"""Low-level adb / uiautomator helpers for the Constanza screenshot pipeline.

Every navigation step in capturar.py locates elements BY TEXT (or content-description), never by
fixed coordinates: a uiautomator XML dump is pulled from the device, parsed, and searched for an
exact string match — the same lesson sleep-noise-android's own pipeline paid for first (see
docs/decisions/003-capturas-de-la-ficha-automatizadas.md over there). Waiting is always "wait for
content", never a fixed sleep: every wait_* function polls the dump on a bounded interval until the
expected text appears or a timeout is hit. Poll intervals are deliberately not too tight — a tight
loop keeps `uiautomator dump` contending with the emulator's own UI thread and makes the wait
slower, not faster, the same lesson the sibling app's own README records.
"""

from __future__ import annotations

import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.jjrapps.constanza"
INSTRUMENTATION_TARGET = f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"

DEFAULT_TIMEOUT = 15.0
POLL_INTERVAL = 0.4


class UiTimeout(TimeoutError):
    pass


def adb(serial: str, *args: str, check: bool = True) -> str:
    result = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True)
    if check and result.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed (exit {result.returncode}): {result.stderr.strip()}")
    return result.stdout


def shell(serial: str, *args: str, check: bool = True) -> str:
    return adb(serial, "shell", *args, check=check)


# --------------------------------------------------------------------------------------------
# uiautomator dump / text search — the ONLY way elements are located in this pipeline.
# --------------------------------------------------------------------------------------------

def dump_ui(serial: str) -> ET.Element:
    shell(serial, "uiautomator", "dump", "/sdcard/window_dump.xml")
    xml_text = shell(serial, "cat", "/sdcard/window_dump.xml")
    return ET.fromstring(xml_text)


def _matches(node: ET.Element, text: str) -> bool:
    return node.get("text") == text or node.get("content-desc") == text


def find_all(root: ET.Element, text: str) -> list[ET.Element]:
    return [n for n in root.iter("node") if _matches(n, text)]


def find_bounds(node: ET.Element) -> tuple[int, int]:
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not match:
        raise ValueError(f"Node has no parseable bounds: {node.attrib}")
    x1, y1, x2, y2 = (int(v) for v in match.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def wait_for_text(serial: str, text: str, timeout: float = DEFAULT_TIMEOUT) -> ET.Element:
    """Polls a fresh uiautomator dump until an EXACT text or content-description match appears."""
    deadline = time.time() + timeout
    last_dump_summary = ""
    while time.time() < deadline:
        root = dump_ui(serial)
        matches = find_all(root, text)
        if matches:
            return matches[0]
        last_dump_summary = ", ".join(sorted({n.get("text") for n in root.iter("node") if n.get("text")})[:20])
        time.sleep(POLL_INTERVAL)
    raise UiTimeout(f"'{text}' never appeared within {timeout}s. Visible text sample: {last_dump_summary}")


def wait_absent(serial: str, text: str, timeout: float = DEFAULT_TIMEOUT) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        root = dump_ui(serial)
        if not find_all(root, text):
            return
        time.sleep(POLL_INTERVAL)
    raise UiTimeout(f"'{text}' was still present after {timeout}s")


def find_nearest(root: ET.Element, anchor_text: str, target_text: str) -> ET.Element:
    """Disambiguates a repeated control (e.g. every habit row's own "More options" button) by
    picking the match whose vertical centre is closest to the named anchor's — the anchor (e.g. a
    habit's own name) is unique, the target is not. Still text-located, never a raw coordinate."""
    anchors = find_all(root, anchor_text)
    if not anchors:
        raise LookupError(f"Anchor '{anchor_text}' not found")
    _, anchor_y = find_bounds(anchors[0])
    candidates = find_all(root, target_text)
    if not candidates:
        raise LookupError(f"Target '{target_text}' not found")
    return min(candidates, key=lambda n: abs(find_bounds(n)[1] - anchor_y))


def tap_node(serial: str, node: ET.Element) -> None:
    x, y = find_bounds(node)
    shell(serial, "input", "tap", str(x), str(y))


def tap_text(serial: str, text: str, timeout: float = DEFAULT_TIMEOUT) -> None:
    tap_node(serial, wait_for_text(serial, text, timeout))


def tap_nearest(serial: str, anchor_text: str, target_text: str) -> None:
    root = dump_ui(serial)
    tap_node(serial, find_nearest(root, anchor_text, target_text))


def scroll_down(serial: str, amount: int = 700) -> None:
    shell(serial, "input", "swipe", "540", "1700", "540", str(1700 - amount), "300")


def scroll_until_text(serial: str, text: str, max_scrolls: int = 8) -> ET.Element:
    for _ in range(max_scrolls):
        root = dump_ui(serial)
        matches = find_all(root, text)
        if matches:
            return matches[0]
        scroll_down(serial)
        time.sleep(0.3)
    raise UiTimeout(f"'{text}' never appeared after {max_scrolls} scroll attempts")


# --------------------------------------------------------------------------------------------
# Navigation primitives
# --------------------------------------------------------------------------------------------

def press_back(serial: str) -> None:
    shell(serial, "input", "keyevent", "KEYCODE_BACK")


def press_home(serial: str) -> None:
    shell(serial, "input", "keyevent", "KEYCODE_HOME")


def screenshot(serial: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(["adb", "-s", serial, "exec-out", "screencap", "-p"], capture_output=True)
    if result.returncode != 0 or not result.stdout:
        raise RuntimeError(f"screencap failed for {dest}: {result.stderr.decode(errors='replace')}")
    dest.write_bytes(result.stdout)


# --------------------------------------------------------------------------------------------
# Device state: demo mode, wm size/density, locale, permissions, clean notifications.
# --------------------------------------------------------------------------------------------

def _demo_broadcast(serial: str, command: str, *extras: str) -> None:
    shell(serial, "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", command, *extras, check=False)


def enter_demo_mode(serial: str) -> None:
    """SystemUI demo mode: a fixed clock, full battery, full wifi, no carrier signal, and hidden
    per-app notification icons — the standard, publicly documented technique for a clean,
    reproducible status bar (`adb shell am broadcast -a com.android.systemui.demo ...`)."""
    shell(serial, "settings", "put", "global", "sysui_demo_allowed", "1")
    _demo_broadcast(serial, "enter")
    _demo_broadcast(serial, "clock", "-e", "hhmm", "0930")
    _demo_broadcast(serial, "battery", "-e", "level", "100", "-e", "plugged", "false")
    # `fully true` drops the "no internet" exclamation mark the emulator's wifi otherwise shows.
    _demo_broadcast(serial, "network", "-e", "wifi", "show", "-e", "level", "4", "-e", "fully", "true")
    _demo_broadcast(serial, "network", "-e", "mobile", "hide")
    _demo_broadcast(serial, "notifications", "-e", "visible", "false")


def demo_notification_icons(serial: str, visible: bool) -> None:
    """The notification-shade scene needs the shade's own content, not the status bar's small
    per-app dot — demo mode's `notifications` component only ever hides the latter. Toggled around
    that one scene defensively, in case a future platform version ties the two together."""
    _demo_broadcast(serial, "notifications", "-e", "visible", "true" if visible else "false")


def exit_demo_mode(serial: str) -> None:
    _demo_broadcast(serial, "exit")


def force_24_hour_clock(serial: str) -> None:
    shell(serial, "settings", "put", "system", "time_12_24", "24")


def disable_animations(serial: str) -> None:
    for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        shell(serial, "settings", "put", "global", key, "0")


def set_wm(serial: str, width: int, height: int, density: int) -> None:
    shell(serial, "wm", "size", f"{width}x{height}")
    shell(serial, "wm", "density", str(density))


def reset_wm(serial: str) -> None:
    shell(serial, "wm", "size", "reset", check=False)
    shell(serial, "wm", "density", "reset", check=False)


def set_app_locale(serial: str, language: str) -> None:
    """API 33+ only (all local AVDs used here are API 37) — below that, the app's own in-app
    picker (Settings, `language_tag` DataStore key) would be the fallback, but is not needed here."""
    shell(serial, "cmd", "locale", "set-app-locales", PACKAGE, "--user", "0", "--locales", language)


def grant_permissions(serial: str) -> None:
    shell(serial, "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS", check=False)
    shell(serial, "appops", "set", PACKAGE, "SCHEDULE_EXACT_ALARM", "allow", check=False)


def clear_all_notifications(serial: str) -> None:
    """Moves every notification that is not Constanza's out of the shade with
    `cmd notification snooze`, the only thing that actually works — sleep-noise-android measured
    that `service call notification 1` clears nothing on modern Android, and it did clear nothing
    here either (the emulator's "No screen lock", "Theft protection" and physical-keyboard
    notifications survived it). Snoozed for an hour, which outlasts a full pass, and nothing is
    deleted from the device. The key contains `|`, so it is single-quoted: unquoted, the device
    shell splits it into a pipeline and answers "inaccessible or not found" for every piece.
    Also clears the status-bar notification icons those notifications would otherwise show."""
    for key in shell(serial, "cmd", "notification", "list", check=False).splitlines():
        key = key.strip()
        if key and f"|{PACKAGE}|" not in key:
            shell(serial, f"cmd notification snooze --for 3600000 '{key}'", check=False)


def collapse_shade(serial: str) -> None:
    shell(serial, "cmd", "statusbar", "collapse", check=False)


def expand_shade(serial: str) -> None:
    shell(serial, "cmd", "statusbar", "expand-notifications", check=False)


def notification_counts(serial: str, package: str = PACKAGE) -> tuple[int, int]:
    """One dumpsys call, not two: `active_notification_count`/`total_notification_count` used to
    each run their own separate `adb shell dumpsys` at slightly different instants, and a
    notification changing state between those two calls (observed live: the OS auto-added, then
    almost immediately dropped, a `GROUP_SUMMARY`/`AUTOGROUP_SUMMARY` bookkeeping record for a
    single real notification) could make `own` and `total` disagree in impossible ways. A single
    shared dump removes that race. The summary record itself is EXCLUDED from `own`: it carries
    the same `pkg=` as the real notification but draws no separate card in the shade — counting it
    made a single real notification look like two."""
    dump = shell(serial, "dumpsys", "notification", "--noredact", check=False)
    records = re.findall(r"NotificationRecord\([^\n]*", dump)
    total = len(records)
    own = sum(
        1
        for record in records
        if f"pkg={package}" in record and "GROUP_SUMMARY" not in record
    )
    return own, total


def active_notification_count(serial: str, package: str = PACKAGE) -> int:
    own, _ = notification_counts(serial, package)
    return own


def total_notification_count(serial: str) -> int:
    _, total = notification_counts(serial)
    return total


def wait_for_own_notification(serial: str, timeout: float) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if active_notification_count(serial) >= 1:
            return
        time.sleep(2.0)
    raise UiTimeout(f"No Constanza notification appeared within {timeout}s")


def force_stop(serial: str) -> None:
    shell(serial, "am", "force-stop", PACKAGE, check=False)


def start_app(serial: str) -> None:
    shell(serial, "monkey", "-p", PACKAGE, "-c", "android.intent.category.LAUNCHER", "1", check=False)


def run_seed(serial: str, class_name: str, **instrumentation_args: str) -> str:
    extras: list[str] = []
    for key, value in instrumentation_args.items():
        extras += ["-e", key, value]
    return shell(serial, "am", "instrument", "-w", "-r", *extras, "-e", "class", class_name, INSTRUMENTATION_TARGET)
