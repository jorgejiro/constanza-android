#!/usr/bin/env python3
"""Batch driver: for ONE language, seeds and captures all THREE Play Store formats on a single
already-booted emulator, switching `wm size`/`wm density` between them rather than booting a
separate AVD per format (the technique sleep-noise-android's own pipeline settled on).

Usage:
    python3 tanda.py <serial> <es|en>

Requires the debug build and its test APK already installed (seeding uses instrumentation, which a
release build cannot run — see the README):

    JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \\
        ./gradlew :app:installDebug :app:installDebugAndroidTest
"""

from __future__ import annotations

import sys
import time
from pathlib import Path

import capturar
import ui

REPO_ROOT = Path(__file__).resolve().parents[3]
CAPTURAS_DIR = REPO_ROOT / "docs" / "store-assets" / "capturas"
FASTLANE_DIR = REPO_ROOT / "fastlane" / "metadata" / "android"

LOCALE_DIR = {"es": "es", "en": "en"}
FASTLANE_LOCALE = {"es": "es-ES", "en": "en-US"}

# width, height, density — from the sibling app's README, reused verbatim.
FORMATS: dict[str, tuple[int, int, int]] = {
    "phone": (1080, 2400, 420),
    "tablet7": (1080, 1920, 288),
    "tablet10": (1440, 2560, 288),
}

SEED_CLASS = "com.jjrapps.constanza.seed.ListingScreenshotSeed"
POST_SEED_LAUNCH_DELAY = 2.0  # lets the just-armed alarms register before the app cold-starts


def prepare_device(serial: str) -> None:
    ui.grant_permissions(serial)
    ui.force_24_hour_clock(serial)
    ui.disable_animations(serial)
    # The app is dark-only, but the notification shade follows the system theme: without this,
    # scene 02 is a white panel around a dark app.
    ui.shell(serial, "cmd", "uimode", "night", "yes")
    ui.enter_demo_mode(serial)


def seed_and_launch(serial: str, lang: str) -> None:
    ui.force_stop(serial)
    result = ui.run_seed(serial, SEED_CLASS, language=lang)
    if "OK (1 test)" not in result and "FAILURES!!!" in result:
        raise RuntimeError(f"ListingScreenshotSeed failed for language={lang}:\n{result}")
    time.sleep(POST_SEED_LAUNCH_DELAY)
    ui.start_app(serial)


def copy_phone_to_fastlane(lang: str, phone_dir: Path) -> None:
    fastlane_images = FASTLANE_DIR / FASTLANE_LOCALE[lang] / "images" / "phoneScreenshots"
    fastlane_images.mkdir(parents=True, exist_ok=True)
    for index, scene in enumerate(capturar.SCENES, start=1):
        src = phone_dir / f"{scene}.png"
        dest = fastlane_images / f"{index}.png"
        dest.write_bytes(src.read_bytes())


def run_language(serial: str, lang: str) -> None:
    if lang not in LOCALE_DIR:
        raise SystemExit(f"Unknown language '{lang}', expected 'es' or 'en'")

    prepare_device(serial)
    ui.set_app_locale(serial, lang)

    manifest_path = CAPTURAS_DIR / "manifest.json"
    for formato, (width, height, density) in FORMATS.items():
        print(f"== {lang} / {formato} ({width}x{height}@{density}) ==", flush=True)
        ui.set_wm(serial, width, height, density)
        seed_and_launch(serial, lang)
        out_dir = CAPTURAS_DIR / LOCALE_DIR[lang] / formato
        results = capturar.capture_all(serial, lang, out_dir)
        capturar.write_manifest_entries(manifest_path, lang, formato, results)
        ui.force_stop(serial)
        if formato == "phone":
            copy_phone_to_fastlane(lang, out_dir)

    ui.reset_wm(serial)
    ui.exit_demo_mode(serial)


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    serial, lang = sys.argv[1], sys.argv[2]
    run_language(serial, lang)


if __name__ == "__main__":
    main()
