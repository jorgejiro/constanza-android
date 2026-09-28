# Gráficos de la ficha de Google Play

Icono y gráfico de cabecera de la ficha, generados por `scripts/generar-graficos.py`:

- `icono-512.png` — 512×512, PNG de 32 bits (con alfa). Icono cuadrado a sangre completa —
  Play aplica su propia máscara, así que este fichero no lleva esquinas redondeadas ni sombra.
- `cabecera-1024x500-es.png` / `cabecera-1024x500-en.png` — gráfico de cabecera, 1024×500, PNG
  de 24 bits sin canal alfa (Play rechaza transparencia en este gráfico).

El script también copia ambos ficheros a
`fastlane/metadata/android/{es-ES,en-US}/images/{icon.png,featureGraphic.png}`.

## Regenerar

```bash
python3 scripts/generar-graficos.py
```

## De dónde sale cada cosa

- **El icono no está dibujado a mano.** El script lee el `pathData` real de
  `app/src/main/res/drawable/ic_launcher_foreground.xml` y el color real de
  `app/src/main/res/values/ic_launcher_background.xml`, arma un SVG mínimo (el viewport de 108 dp
  del icono adaptativo, mapeado directamente a los 512 px del PNG — así es como Play espera este
  fichero, sin recortar a la zona segura de 72 dp que sí aplica la máscara del lanzador) y lo
  rasteriza con `rsvg-convert` (librsvg, `brew install librsvg` si falta). Si el vector del icono
  cambia alguna vez, este script no hay que tocarlo: solo volver a ejecutarlo.
- **El gráfico de cabecera** reutiliza ese mismo icono a menor tamaño, el texto exacto de la
  sección «Feature graphic» de `docs/play-store-publication-texts.md` (uno por idioma) y una
  captura real de la pantalla «Hoy» (`docs/store-assets/capturas/<idioma>/phone/01-hoy.png`),
  recortada a la franja con contenido y enmarcada — nunca una maqueta de interfaz inventada.
  Compuesto con Pillow sobre la paleta fija de `ConstanzaColors.kt` (la app no tiene tema claro).
- **Tipografía**: este Mac no tiene Roboto ni Inter instaladas (ninguna aparece en `fc-list`), así
  que el gráfico de cabecera usa Helvetica Neue, la sans del sistema más cercana en forma a la
  Roboto que dibuja la propia app.

## Verificación de fidelidad del icono

`generar-graficos.py` no es la única fuente que se comprobó: el `pathData` extraído de
`ic_launcher_foreground.xml` se comparó carácter a carácter contra el SVG ya existente en
`vps/services/web/src/assets/iconos/constanza.svg` (idéntico en las dos rutas y en el color de
fondo), y ese SVG de origen independiente se rasterizó también con `rsvg-convert` para comparar
píxel a píxel contra la salida de este script — la única diferencia es antialiasing residual de la
reducción de escala interna (máximo ~27/255 por canal, en el contorno del trazo), no una forma
distinta.
