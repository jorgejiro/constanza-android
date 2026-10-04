#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Genera los gráficos de la ficha de Google Play para Constanza:

  docs/store-assets/graficos/icono-512.png              icono de la ficha, 512x512
  docs/store-assets/graficos/cabecera-1024x500-es.png    gráfico de cabecera, es-ES
  docs/store-assets/graficos/cabecera-1024x500-en.png    gráfico de cabecera, en-US
  fastlane/metadata/android/{es-ES,en-US}/images/icon.png
  fastlane/metadata/android/{es-ES,en-US}/images/featureGraphic.png

El icono no se dibuja a mano: se extrae el `pathData` real de
`app/src/main/res/drawable/ic_launcher_foreground.xml` y el color real de
`app/src/main/res/values/ic_launcher_background.xml`, se arma un SVG mínimo y se
rasteriza con `rsvg-convert` (librsvg, `brew install librsvg`). Así el PNG de la
tienda nunca se puede desincronizar del icono que de verdad lleva la app: si el
vector cambia, este script solo hay que volver a ejecutarlo.

El gráfico de cabecera reutiliza ese mismo icono (a menor tamaño) más el texto
exacto de la sección «Feature graphic» de docs/play-store-publication-texts.md,
compuesto con Pillow sobre la paleta fija de la app (ConstanzaColors.kt), y una
captura real de la pantalla «Hoy» recortada y enmarcada — nunca una maqueta de UI
inventada.

Fuente tipográfica: este Mac no tiene Roboto ni Inter instaladas (ninguna de las
dos aparece en `fc-list`), así que se usa Helvetica Neue, la sans del sistema más
cercana en forma a la Roboto que dibuja la app.
"""
from __future__ import annotations

import pathlib
import re
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
OUT = ROOT / "docs/store-assets/graficos"
CAPTURAS = ROOT / "docs/store-assets/capturas"
FASTLANE = ROOT / "fastlane/metadata/android"

FOREGROUND_XML = RES / "drawable/ic_launcher_foreground.xml"
BACKGROUND_XML = RES / "values/ic_launcher_background.xml"

HELVETICA_NEUE = "/System/Library/Fonts/HelveticaNeue.ttc"
FACE_REGULAR = 0
FACE_BOLD = 1

# Paleta fija de la app — ConstanzaColors.kt (no hay tema claro: spec
# "Dark-Only Rendering"). No se recalcula aquí: se copian los mismos ARGB.
BACKGROUND = (0x14, 0x14, 0x16)
ON_BACKGROUND = (0xEC, 0xEC, 0xEE)
ON_BACKGROUND_VARIANT = (0xAD, 0xAD, 0xB5)
CONTROL_STROKE = (0x6C, 0x6C, 0x73)

FEATURE_GRAPHIC_TEXT = {
    "es": "Hábitos que se contestan desde la notificación.",
    "en": "Habits you answer from the notification.",
}
SCREENSHOT_LANG_DIR = {"es": "es", "en": "en"}

ICON_SIZE = 512
FEATURE_W, FEATURE_H = 1024, 500


def _fail(msg: str) -> None:
    print(f"error: {msg}", file=sys.stderr)
    sys.exit(1)


def read_background_color() -> str:
    text = BACKGROUND_XML.read_text(encoding="utf-8")
    m = re.search(r'<color name="ic_launcher_background">(#[0-9A-Fa-f]{6})</color>', text)
    if not m:
        _fail(f"no encuentro ic_launcher_background en {BACKGROUND_XML}")
    return m.group(1)


def read_foreground_paths() -> list[tuple[str, str]]:
    """Extrae (fillColor, pathData) de cada <path> del vector, en orden de dibujo."""
    text = FOREGROUND_XML.read_text(encoding="utf-8")
    paths = []
    for m in re.finditer(
        r'<path\s+android:fillColor="(#[0-9A-Fa-f]{6,8})"\s+android:pathData="([^"]+)"\s*/>',
        text,
        re.DOTALL,
    ):
        fill, data = m.group(1), m.group(2)
        data = re.sub(r"\s+", " ", data).strip()
        paths.append((fill, data))
    if not paths:
        _fail(f"no encuentro ningun <path> en {FOREGROUND_XML}")
    return paths


def icon_svg(size: int, bg: str, paths: list[tuple[str, str]]) -> str:
    body = "\n".join(f'  <path fill="{fill}" d="{data}"/>' for fill, data in paths)
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
        f'viewBox="0 0 108 108">\n'
        f'  <rect width="108" height="108" fill="{bg}"/>\n{body}\n</svg>\n'
    )


def rasterize_svg(svg_text: str, size: int) -> Image.Image:
    with tempfile.TemporaryDirectory() as td:
        svg_path = pathlib.Path(td) / "in.svg"
        png_path = pathlib.Path(td) / "out.png"
        svg_path.write_text(svg_text, encoding="utf-8")
        try:
            subprocess.run(
                [
                    "rsvg-convert",
                    "-w", str(size),
                    "-h", str(size),
                    "-o", str(png_path),
                    str(svg_path),
                ],
                check=True,
                capture_output=True,
            )
        except FileNotFoundError:
            _fail("rsvg-convert no está instalado. `brew install librsvg`.")
        except subprocess.CalledProcessError as e:
            _fail(f"rsvg-convert falló: {e.stderr.decode(errors='replace')}")
        return Image.open(png_path).convert("RGBA")


def build_icon_image(size: int, bg: str, paths: list[tuple[str, str]]) -> Image.Image:
    """Rasteriza a 3x y reduce, para un mejor antialiasing que pedirle a
    rsvg-convert el tamaño final directamente."""
    big = rasterize_svg(icon_svg(size * 3, bg, paths), size * 3)
    return big.resize((size, size), Image.LANCZOS)


def rounded_mask(w: int, h: int, radius: int) -> Image.Image:
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=255)
    return mask


def wrap_text(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.FreeTypeFont, max_width: int) -> list[str]:
    words = text.split(" ")
    lines: list[str] = []
    current = ""
    for word in words:
        trial = f"{current} {word}".strip()
        if not current or draw.textlength(trial, font=font) <= max_width:
            current = trial
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def build_phone_panel(screenshot_path: pathlib.Path, target_h: int, crop_h: int) -> Image.Image:
    """Recorta la captura real 'Hoy' a la franja con contenido (barra de estado,
    Ahora/Más tarde/Contestados con los slots ✓/✗) y descarta el resto en blanco
    de abajo. Esquinas redondeadas para leerse como pantalla, sin inventar un
    bisel de teléfono que no existe en la captura."""
    img = Image.open(screenshot_path).convert("RGBA")
    w, _ = img.size
    img = img.crop((0, 0, w, crop_h))
    scale = target_h / crop_h
    target_w = round(w * scale)
    img = img.resize((target_w, target_h), Image.LANCZOS)
    radius = round(target_w * 0.07)
    img.putalpha(rounded_mask(target_w, target_h, radius))
    return img


def build_feature_graphic(lang: str, out_path: pathlib.Path, bg_hex: str, fg_paths: list[tuple[str, str]]) -> None:
    canvas = Image.new("RGB", (FEATURE_W, FEATURE_H), BACKGROUND)
    draw = ImageDraw.Draw(canvas)

    margin = 40

    screenshot = CAPTURAS / SCREENSHOT_LANG_DIR[lang] / "phone/01-hoy.png"
    if not screenshot.exists():
        _fail(f"no encuentro la captura {screenshot}")
    phone_h = 420
    phone = build_phone_panel(screenshot, phone_h, crop_h=1450)
    phone_x = FEATURE_W - margin - phone.width
    phone_y = (FEATURE_H - phone_h) // 2

    text_x = margin + 24
    text_max_width = phone_x - 30 - text_x

    title_font = ImageFont.truetype(HELVETICA_NEUE, 62, index=FACE_BOLD)
    tagline_font = ImageFont.truetype(HELVETICA_NEUE, 29, index=FACE_REGULAR)

    icon_size = 128
    icon = build_icon_image(icon_size * 2, bg_hex, fg_paths).resize((icon_size, icon_size), Image.LANCZOS)
    icon.putalpha(rounded_mask(icon_size, icon_size, round(icon_size * 0.22)))

    title = "Constanza"
    title_bbox = draw.textbbox((0, 0), title, font=title_font)
    title_h = title_bbox[3] - title_bbox[1]

    tagline_lines = wrap_text(draw, FEATURE_GRAPHIC_TEXT[lang], tagline_font, text_max_width)
    line_bbox = draw.textbbox((0, 0), "Ag", font=tagline_font)
    line_h = round((line_bbox[3] - line_bbox[1]) * 1.45)

    gap_icon_title = 30
    gap_title_tagline = 18
    block_h = icon_size + gap_icon_title + title_h + gap_title_tagline + line_h * len(tagline_lines)
    top_y = (FEATURE_H - block_h) // 2

    icon_y = top_y
    canvas.paste(icon, (text_x, icon_y), icon)

    title_y = icon_y + icon_size + gap_icon_title
    draw.text((text_x, title_y - title_bbox[1]), title, font=title_font, fill=ON_BACKGROUND)

    tagline_y = title_y + title_h + gap_title_tagline
    for i, line in enumerate(tagline_lines):
        draw.text(
            (text_x, tagline_y + i * line_h - line_bbox[1]),
            line,
            font=tagline_font,
            fill=ON_BACKGROUND_VARIANT,
        )

    frame_pad = 3
    frame_radius = round(phone.width * 0.07) + frame_pad
    draw.rounded_rectangle(
        [phone_x - frame_pad, phone_y - frame_pad, phone_x + phone.width + frame_pad, phone_y + phone_h + frame_pad],
        radius=frame_radius,
        outline=CONTROL_STROKE,
        width=2,
    )
    canvas.paste(phone, (phone_x, phone_y), phone)

    canvas.save(out_path)  # RGB: Play rechaza alfa en el feature graphic.


def copy_into_fastlane(icon_path: pathlib.Path, feature_es: pathlib.Path, feature_en: pathlib.Path) -> None:
    for locale, feature in (("es-ES", feature_es), ("en-US", feature_en)):
        images_dir = FASTLANE / locale / "images"
        images_dir.mkdir(parents=True, exist_ok=True)
        (images_dir / "icon.png").write_bytes(icon_path.read_bytes())
        (images_dir / "featureGraphic.png").write_bytes(feature.read_bytes())


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)

    bg_hex = read_background_color()
    fg_paths = read_foreground_paths()

    icon = build_icon_image(ICON_SIZE, bg_hex, fg_paths)
    icon_path = OUT / "icono-512.png"
    icon.save(icon_path)  # RGBA: Play pide PNG de 32 bits con alfa.
    print(f"icono: {icon_path} ({icon.size[0]}x{icon.size[1]}, {icon.mode})")

    feature_es = OUT / "cabecera-1024x500-es.png"
    feature_en = OUT / "cabecera-1024x500-en.png"
    build_feature_graphic("es", feature_es, bg_hex, fg_paths)
    build_feature_graphic("en", feature_en, bg_hex, fg_paths)
    for p in (feature_es, feature_en):
        with Image.open(p) as im:
            print(f"cabecera: {p} ({im.size[0]}x{im.size[1]}, {im.mode})")

    copy_into_fastlane(icon_path, feature_es, feature_en)
    print("copiado a fastlane/metadata/android/{es-ES,en-US}/images/")


if __name__ == "__main__":
    main()
