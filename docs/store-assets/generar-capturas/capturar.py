"""Per-scene navigation for Constanza's Play Store screenshots.

Scene order and numbering follow docs/play-store-publication-texts.md's "Capturas — Orden
recomendado" table exactly — 1 Today, 2 notification, 3 habit editor, 4 colour picker, 5 progress,
6 habit list, 7 settings. Every scene function receives an already-seeded, already-launched app on
the Today screen and returns the app on the Today screen too, so scenes can run back-to-back in a
single pass over one (language, format) combination.
"""

from __future__ import annotations

import json
import time
from dataclasses import dataclass
from pathlib import Path

import ui

STRINGS = {
    "es": {
        "today_title": "Hoy",
        "add_habit": "Añadir hábito",
        "settings": "Ajustes",
        "manage_habits": "Hábitos",
        "more_options": "Más opciones",
        "progress_menu": "Progreso",
        "schedule_label": "Frecuencia",
        "schedule_daily": "Diaria",
        "schedule_monthly": "Mensual",
        "color_label": "Color",
        "color_more": "Más",
        "notification_snooze": "Aplazar",
        "notification_title": "Buenos hábitos",
        # Section headers are rendered upper-cased by SettingsSection's own composable
        # (`.uppercase()`), never the string resource's own mixed case — confirmed live: scrolling
        # for the resource's exact casing looped through all 8 attempts while the upper-cased text
        # sat on screen the whole time, unscrolled.
        "portability_section": "DATOS Y COPIA DE SEGURIDAD",
        "day_review_time_label": "Hora del repaso",
        "progress_title": "Progreso",
        "habit_list_title": "Hábitos",
        "habit_editor_title": "Nuevo hábito",
        "habit_progress_target": "Leer 20 minutos",
        "discard_confirm": "Descartar",
    },
    "en": {
        "today_title": "Today",
        "add_habit": "Add habit",
        "settings": "Settings",
        "manage_habits": "Habits",
        "more_options": "More options",
        "progress_menu": "Progress",
        "schedule_label": "Frequency",
        "schedule_daily": "Daily",
        "schedule_monthly": "Monthly",
        "color_label": "Colour",
        "color_more": "More",
        "notification_snooze": "Snooze",
        "notification_title": "Good habits",
        "portability_section": "DATA & BACKUP",
        "day_review_time_label": "Review time",
        "progress_title": "Progress",
        "habit_list_title": "Habits",
        "habit_editor_title": "New habit",
        "habit_progress_target": "Read 20 minutes",
        "discard_confirm": "Discard",
    },
}

# Order matters: it is both the capture order and the Play Store listing order (T1 doc).
SCENES = [
    "01-hoy",
    "02-notificacion",
    "03-editor-habito",
    "04-selector-color",
    "05-progreso",
    "06-lista-habitos",
    "07-ajustes",
]

SETTLE_SECONDS = 0.6  # a short, fixed pause AFTER a text-based wait already succeeded, for the
# one frame of recomposition/ripple animation Compose can still be mid-way through — never used
# as a substitute for wait_for_text itself.

NOTIFICATION_WAIT_SECONDS = 240  # SEED_LEAD_MINUTES=2 in ListingScreenshotSeed.kt, plus headroom
# for a slow emulator and for AlarmManager's own dispatch jitter.


@dataclass
class SceneResult:
    scene: str
    path: Path
    notes: dict


def _settle() -> None:
    time.sleep(SETTLE_SECONDS)


def capture_today(serial: str, lang: str, out: Path) -> SceneResult:
    s = STRINGS[lang]
    # Before the first shot, not only before the notification scene: every foreign notification
    # also puts an icon in the status bar, which demo mode does not hide on this API level.
    ui.clear_all_notifications(serial)
    ui.wait_for_text(serial, s["today_title"])
    _settle()
    ui.screenshot(serial, out)
    return SceneResult("today", out, {})


def capture_notification(serial: str, lang: str, out: Path) -> SceneResult:
    s = STRINGS[lang]
    ui.clear_all_notifications(serial)
    ui.demo_notification_icons(serial, visible=True)
    ui.wait_for_own_notification(serial, timeout=NOTIFICATION_WAIT_SECONDS)
    _open_shade_on_own_notification(serial, s)
    own, total = ui.notification_counts(serial)  # one shared dump — see its own KDoc for why
    ui.screenshot(serial, out)
    ui.collapse_shade(serial)
    ui.demo_notification_icons(serial, visible=False)
    return SceneResult("notification", out, {"own_notifications": own, "total_notifications": total})


def _shade_is_open(serial: str) -> bool:
    focus = ui.shell(serial, "dumpsys", "window", check=False)
    return any("mCurrentFocus" in line and "NotificationShade" in line for line in focus.splitlines())


def _open_shade_on_own_notification(serial: str, s: dict) -> None:
    """Opens the shade and proves it is open before the shot. The first full run took scene 02
    with the shade already collapsed again — the text wait had passed, the screenshot showed
    Today. So this waits for the reminder's own "Snooze" action (only ever drawn inside the
    shade, never in the app) and then checks the focused window is the shade itself, re-opening
    once if it is not."""
    for _ in range(2):
        ui.expand_shade(serial)
        ui.wait_for_text(serial, s["notification_title"])
        ui.wait_for_text(serial, s["notification_snooze"])
        _settle()
        if _shade_is_open(serial):
            return
    raise ui.UiTimeout("the notification shade would not stay open")


def capture_habit_editor(serial: str, lang: str, out: Path) -> SceneResult:
    s = STRINGS[lang]
    ui.tap_text(serial, s["add_habit"])
    ui.wait_for_text(serial, s["habit_editor_title"])
    # Tap the field by its current VALUE ("Daily"), not by the "Frequency" heading above it:
    # the heading is plain text, and the first run tapped it, opened nothing, and still passed a
    # wait for "Daily" — which is the closed field's own value. "Monthly" only exists in the menu.
    ui.tap_text(serial, s["schedule_daily"])
    ui.wait_for_text(serial, s["schedule_monthly"])
    _settle()
    ui.screenshot(serial, out)
    return SceneResult("habit_editor", out, {})


def capture_color_picker(serial: str, lang: str, out: Path) -> SceneResult:
    s = STRINGS[lang]
    # Dismiss the still-open schedule dropdown by RE-TAPPING its own already-selected item
    # ("Daily"), not by tapping "Colour": while a Compose DropdownMenu's Popup is showing,
    # `uiautomator dump` only returns THAT popup window's own accessibility tree — every node from
    # the screen underneath, "Colour" included, is simply absent from the dump, not merely
    # obscured, so waiting for it there always times out. The popup's own item list, on the other
    # hand, is exactly what the dump DOES contain, and "Daily" is already one of its rows (the
    # current selection), so tapping it re-confirms the same value and closes the menu — a
    # text-located dismissal, never a raw coordinate. Not back: a back press closed the whole
    # editor outright in the first full run, not just the dropdown.
    ui.tap_text(serial, s["schedule_daily"])
    ui.wait_absent(serial, s["schedule_monthly"])
    ui.tap_text(serial, s["color_more"])
    _settle()
    ui.screenshot(serial, out)
    return SceneResult("color_picker", out, {})


def discard_habit_editor(serial: str, lang: str) -> None:
    s = STRINGS[lang]
    ui.press_back(serial)  # leaves the editor; a discard-changes dialog is expected (name is empty
    # only if untouched, but the schedule/colour taps above count as "changes" too)
    try:
        ui.tap_text(serial, s["discard_confirm"], timeout=5)
    except ui.UiTimeout:
        pass  # nothing was actually dirty (e.g. re-run) — already back on Today.
    ui.wait_for_text(serial, s["today_title"])


def capture_habit_list(serial: str, lang: str, out: Path) -> SceneResult:
    s = STRINGS[lang]
    ui.tap_text(serial, s["manage_habits"])
    ui.wait_for_text(serial, s["habit_list_title"])
    _settle()
    ui.screenshot(serial, out)
    return SceneResult("habit_list", out, {})


def capture_progress(serial: str, lang: str, out: Path) -> SceneResult:
    s = STRINGS[lang]
    ui.tap_nearest(serial, s["habit_progress_target"], s["more_options"])
    ui.tap_text(serial, s["progress_menu"])
    # ProgressScreen's own top bar shows the HABIT'S NAME, not the literal "Progress" string —
    # `state.habitName.ifEmpty { stringResource(progress_title) }`, and habitName is never empty
    # here — so that is what this waits for, not s["progress_title"].
    ui.wait_for_text(serial, s["habit_progress_target"])
    _settle()
    ui.screenshot(serial, out)
    # Neither one back press nor two reliably lands on Today from here — the first full run showed
    # a SINGLE back press from Progress exits straight past Today to the launcher, not to Habit
    # list. Rather than guess a stack depth that has already proven unreliable twice, relaunch the
    # app outright: force-stop then cold-start always opens on Today (the seed leaves onboarding
    # done), the same recovery tanda.py already uses between formats.
    ui.force_stop(serial)
    ui.start_app(serial)
    ui.wait_for_text(serial, s["today_title"])
    return SceneResult("progress", out, {})


def capture_settings(serial: str, lang: str, out: Path) -> SceneResult:
    s = STRINGS[lang]
    ui.tap_text(serial, s["settings"])
    # Scrolls to the DAY REVIEW section specifically, not Data & backup: Settings' own section
    # order is snooze, backup, language, day review — scrolling only until backup first appears
    # stops immediately (it is already on screen unscrolled) and leaves day review, further down,
    # cut off. Scrolling to day review's own time label reliably brings both into the same frame,
    # since backup does not scroll far enough off the top to leave frame by the time day review
    # enters it.
    ui.scroll_until_text(serial, s["day_review_time_label"])
    _settle()
    ui.screenshot(serial, out)
    # No return-to-Today here: `capture_settings` is the last scene of a pass, and `tanda.py`
    # force-stops the app right after `capture_all` returns regardless. A trailing back press here
    # was exiting straight to the launcher — same broken back-stack `capture_progress` hit — for no
    # benefit, since nothing after this reads screen state.
    return SceneResult("settings", out, {})


def capture_all(serial: str, lang: str, out_dir: Path) -> list[SceneResult]:
    """Captures the full ordered scene set into out_dir/01-hoy.png .. 07-ajustes.png, returning a
    per-scene manifest entry revisar.py cross-checks (notification counts in particular, since
    those cannot be recovered from the finished PNG alone)."""
    results: list[SceneResult] = []
    results.append(capture_today(serial, lang, out_dir / f"{SCENES[0]}.png"))
    results.append(capture_notification(serial, lang, out_dir / f"{SCENES[1]}.png"))
    results.append(capture_habit_editor(serial, lang, out_dir / f"{SCENES[2]}.png"))
    results.append(capture_color_picker(serial, lang, out_dir / f"{SCENES[3]}.png"))
    discard_habit_editor(serial, lang)
    results.append(capture_habit_list(serial, lang, out_dir / f"{SCENES[5]}.png"))
    results.append(capture_progress(serial, lang, out_dir / f"{SCENES[4]}.png"))
    results.append(capture_settings(serial, lang, out_dir / f"{SCENES[6]}.png"))
    return results


def write_manifest_entries(manifest_path: Path, lang: str, formato: str, results: list[SceneResult]) -> None:
    manifest: dict = {}
    if manifest_path.exists():
        manifest = json.loads(manifest_path.read_text())
    manifest.setdefault(lang, {})[formato] = {
        r.scene: {"path": str(r.path.relative_to(manifest_path.parent)), **r.notes} for r in results
    }
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False, sort_keys=True))
