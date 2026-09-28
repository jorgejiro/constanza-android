#!/usr/bin/env python3
"""Verifies the full Play Store screenshot set. Exit 0 means every check below passed; any
failure prints every problem found (not just the first) and exits 1.

Checks:
  1. Every expected file is present (2 languages x 3 formats x 7 scenes = 42 files).
  2. Exact pixel dimensions per format.
  3. Tablet formats are 9:16 portrait.
  4. es and en differ for every scene (a byte-identical pair would mean a leaked/unswapped
     language — the seed's own text never repeats verbatim between languages).
  5. No blank or half-drawn image (a pixel-variance floor).
  6. No stray system notification in the notification scene, cross-checked against the
     capture-time manifest.json capturar.py writes (own_notifications/total_notifications), since
     a finished PNG alone cannot prove what else was in the shade at capture time.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

REPO_ROOT = Path(__file__).resolve().parents[3]
CAPTURAS_DIR = REPO_ROOT / "docs" / "store-assets" / "capturas"

LANGS = ["es", "en"]
SCENES = [
    "01-hoy",
    "02-notificacion",
    "03-editor-habito",
    "04-selector-color",
    "05-progreso",
    "06-lista-habitos",
    "07-ajustes",
]
FORMATS: dict[str, tuple[int, int]] = {
    "phone": (1080, 2400),
    "tablet7": (1080, 1920),
    "tablet10": (1440, 2560),
}
TABLET_FORMATS = {"tablet7", "tablet10"}

BLANK_STD_FLOOR = 8.0  # a real screen, even Constanza's mostly-dark theme, varies more than this
PIXEL_DIFF_THRESHOLD = 20  # per-channel abs diff above this counts a pixel as "meaningfully different"
LEAK_DIFFERING_PIXELS_FLOOR = 0.001  # under 0.1% of pixels differing meaningfully == suspiciously identical


def fail(problems: list[str], message: str) -> None:
    problems.append(message)


def check_files_present(problems: list[str]) -> dict[tuple[str, str, str], Path]:
    paths: dict[tuple[str, str, str], Path] = {}
    for lang in LANGS:
        for formato in FORMATS:
            for scene in SCENES:
                path = CAPTURAS_DIR / lang / formato / f"{scene}.png"
                if not path.exists():
                    fail(problems, f"MISSING: {path.relative_to(REPO_ROOT)}")
                else:
                    paths[(lang, formato, scene)] = path
    return paths


def check_dimensions(problems: list[str], paths: dict[tuple[str, str, str], Path]) -> None:
    for (lang, formato, scene), path in paths.items():
        expected = FORMATS[formato]
        with Image.open(path) as image:
            actual = image.size
        if actual != expected:
            fail(problems, f"WRONG SIZE {path.relative_to(REPO_ROOT)}: expected {expected}, got {actual}")
        if formato in TABLET_FORMATS:
            width, height = actual
            ratio = width / height
            expected_ratio = 9 / 16
            if abs(ratio - expected_ratio) > 0.01:
                fail(problems, f"NOT 9:16 {path.relative_to(REPO_ROOT)}: ratio {ratio:.4f}, expected {expected_ratio:.4f}")


def check_not_blank(problems: list[str], paths: dict[tuple[str, str, str], Path]) -> None:
    for key, path in paths.items():
        with Image.open(path) as image:
            arr = np.asarray(image.convert("RGB"), dtype=np.float64)
        std = float(arr.std())
        if std < BLANK_STD_FLOOR:
            fail(problems, f"LOOKS BLANK {path.relative_to(REPO_ROOT)}: pixel std={std:.2f} (floor {BLANK_STD_FLOOR})")


def check_languages_differ(problems: list[str], paths: dict[tuple[str, str, str], Path]) -> None:
    for formato in FORMATS:
        for scene in SCENES:
            es_path = paths.get(("es", formato, scene))
            en_path = paths.get(("en", formato, scene))
            if es_path is None or en_path is None:
                continue  # already reported as missing
            with Image.open(es_path) as es_img, Image.open(en_path) as en_img:
                if es_img.size != en_img.size:
                    continue  # already reported as wrong size
                es_arr = np.asarray(es_img.convert("RGB"), dtype=np.float64)
                en_arr = np.asarray(en_img.convert("RGB"), dtype=np.float64)
            # A whole-image MEAN per-channel diff is diluted to near-zero by this app's own
            # mostly-uniform dark background (design's own "Dark-Only Rendering", "Habit Colour Is
            # The Only Chroma") — translated text is a small fraction of 2.6M+ phone pixels, so a
            # real es/en difference can average under 1.0 even when the two are genuinely distinct
            # (measured live: five real, correctly-different scene pairs). The proportion of
            # pixels that differ MEANINGFULLY at all is the robust signal instead: two screens
            # showing different text always disagree on a non-trivial share of pixels; two
            # captures of the same language (a leak) disagree on essentially none beyond
            # antialiasing/clock-tick noise.
            differing_pixels = float(np.any(np.abs(es_arr - en_arr) > PIXEL_DIFF_THRESHOLD, axis=-1).mean())
            if differing_pixels < LEAK_DIFFERING_PIXELS_FLOOR:
                fail(
                    problems,
                    f"LANGUAGE LEAK {formato}/{scene}: only {differing_pixels:.4%} of pixels differ "
                    f"meaningfully between es and en — looks like the same language was captured twice.",
                )


def check_notification_scene(problems: list[str]) -> None:
    manifest_path = CAPTURAS_DIR / "manifest.json"
    if not manifest_path.exists():
        fail(problems, f"MISSING manifest: {manifest_path.relative_to(REPO_ROOT)} (needed for the stray-notification check)")
        return
    manifest = json.loads(manifest_path.read_text())
    for lang in LANGS:
        for formato in FORMATS:
            entry = manifest.get(lang, {}).get(formato, {}).get("notification")
            if entry is None:
                fail(problems, f"MISSING manifest entry for {lang}/{formato}/notification")
                continue
            own = entry.get("own_notifications")
            total = entry.get("total_notifications")
            if own != 1:
                fail(problems, f"NOTIFICATION SCENE {lang}/{formato}: expected exactly 1 Constanza notification, found {own}")
            if total != own:
                fail(
                    problems,
                    f"STRAY NOTIFICATION {lang}/{formato}: {total} notifications total but only "
                    f"{own} from Constanza — something else was in the shade.",
                )


def main() -> int:
    problems: list[str] = []
    paths = check_files_present(problems)
    check_dimensions(problems, paths)
    check_not_blank(problems, paths)
    check_languages_differ(problems, paths)
    check_notification_scene(problems)

    if problems:
        print(f"revisar.py: {len(problems)} problem(s) found:\n")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    total = len(LANGS) * len(FORMATS) * len(SCENES)
    print(f"revisar.py: all {total} screenshots present and valid.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
