# Compilar y publicar una versión de Constanza

Todo lo necesario para sacar una versión nueva desde cualquier Mac: preparar la máquina una vez,
verificar, subir la versión, compilar, y publicar en GitHub, Google Play y F-Droid.

Ninguna clave vive en el repositorio: todas están en Bitwarden Secrets Manager (BWS) y `con-claves`
las inyecta como variables de entorno solo durante el comando que las necesita, sin escribirlas en
disco.

---

## 1. Preparar un Mac (una sola vez)

| Qué | Cómo | Para qué |
|---|---|---|
| Homebrew | <https://brew.sh> | Instalar el resto |
| Android Studio | Descarga oficial, en `/Applications` | Su JDK (`jbr`) lo usa `fastlane subir`; trae el SDK |
| Android SDK | Lo instala Android Studio en `~/Library/Android/sdk` | Compilar y emuladores |
| `ANDROID_HOME` | `export ANDROID_HOME=~/Library/Android/sdk` en `~/.zshrc`, o `sdk.dir=…` en `local.properties` (ignorado por git) | Sin él Gradle falla con *SDK location not found* |
| BWS + `con-claves` | `~/dev/homelab/config/mac/instalar-bws.sh` (guarda el token en el Llavero) | Acceso a las claves |
| GitHub CLI | `brew install gh` y `gh auth login` | Crear PRs y releases |
| fastlane | `brew install fastlane` | Publicar en Google Play |

Comprobar que todo está listo:

```bash
con-claves | rg -i constanza          # deben salir las cinco claves de la tabla siguiente
con-claves 'fastlane validar'         # valida la ficha de Play contra la API; no publica nada
```

### Claves en BWS

| Nombre | Contenido | Lo usa |
|---|---|---|
| `CONSTANZA_KEYSTORE_B64` | El `.jks` de firma, en base64 | `app/build.gradle.kts` |
| `CONSTANZA_STORE_PASSWORD` | Contraseña del almacén | `app/build.gradle.kts` |
| `CONSTANZA_KEY_ALIAS` | Alias de la clave | `app/build.gradle.kts` |
| `CONSTANZA_KEY_PASSWORD` | Contraseña de la clave | `app/build.gradle.kts` |
| `ANDROID_CONSTANZA_JSON_KEY` | JSON de la cuenta de servicio de Play Console | `fastlane/Appfile` |

Si falta `ANDROID_CONSTANZA_JSON_KEY`, fastlane busca el JSON en un archivo: `SUPPLY_JSON_KEY` si está
definida, o `~/.config/play/jjrmobileapps.json`. Con la clave de BWS, dejar `SUPPLY_JSON_KEY` sin
definir: si están las dos, fastlane usa el archivo sin avisar.

---

## 2. Verificar antes de publicar

```bash
./gradlew check                                    # tests unitarios, lint y detekt (también de los tests)
./gradlew :app:emulatorMatrixGroupDebugAndroidTest # suite instrumentada en emuladores API 31 y API 37
```

- `detektMain` solo revisa el código de producción; `check` revisa también los tests. Usar `check`.
- La matriz tarda unos 20 minutos y provisiona los emuladores sola, sin móvil.
- En un MacBook Air M1 con 8 GB, el emulador de API 37 (imagen completa de Google, 2 GB de RAM) va
  saturado: las apps del sistema dejan de responder y algún test puede fallar de forma intermitente.
  Antes de culpar al cambio, repetir el test o pasarlo en una máquina con más memoria.

---

## 3. Subir la versión

En una rama `chore/bump-X-Y-Z`:

1. En `app/build.gradle.kts`, sumar 1 a `versionCode` y cambiar `versionName`.
2. Crear `fastlane/metadata/android/es-ES/changelogs/<versionCode>.txt` y el de `en-US`, con
   **500 caracteres como máximo** cada uno. Es el texto de «Novedades» de Play y el changelog de F-Droid.
3. `./gradlew check`, PR y fusionar con **merge commit** (no squash; F-Droid compila commits concretos).

---

## 4. Compilar y comprobar

Desde `main` actualizado:

```bash
con-claves './gradlew :app:assembleRelease :app:bundleRelease'
```

Salen `app/build/outputs/apk/release/app-release.apk` y `app/build/outputs/bundle/release/app-release.aab`.
Comprobar el APK con las herramientas del SDK (`BT=~/Library/Android/sdk/build-tools/<versión>`):

```bash
$BT/aapt2 dump badging app/build/outputs/apk/release/app-release.apk | head -1   # versionCode y versionName
$BT/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk # certificado
$BT/aapt2 dump permissions app/build/outputs/apk/release/app-release.apk         # permisos
```

- El certificado tiene que ser siempre el mismo: `CN=Constanza`, SHA-256 `59659af6…64c912d0`. Si
  cambia, los móviles no podrán actualizar.
- Si los permisos cambian respecto a la versión anterior, decirlo en las notas de la release.

---

## 5. Publicar

### GitHub Release (y con ella, F-Droid)

```bash
cp app/build/outputs/apk/release/app-release.apk constanza-X.Y.Z.apk   # fuera del repo
gh release create vX.Y.Z constanza-X.Y.Z.apk \
  --target "$(git rev-parse HEAD)" --title "Constanza X.Y.Z" --notes-file notas.md
```

- `--target` necesita el **SHA completo**; con uno abreviado GitHub responde *HTTP 422*.
- Las notas siguen la estructura de las releases anteriores: Install, What's new, What is NOT
  verified y Verification.
- Comprobar que el APK se descarga sin sesión (código 200):
  `curl -sL -o /dev/null -w "%{http_code}\n" https://github.com/jorgejiro/constanza-android/releases/download/vX.Y.Z/constanza-X.Y.Z.apk`

**F-Droid** no necesita nada más: detecta cada tag `vX.Y.Z` (`UpdateCheckMode: Tags`), lee la versión
de `app/build.gradle.kts` y compila por su cuenta. Tarda unos días. Detalles y limitaciones en
[`fdroid/LEEME.md`](fdroid/LEEME.md).

### Google Play

```bash
con-claves 'fastlane subir'
```

Compila el AAB firmado y lo publica en **producción** con estado `completed`: sale directamente, sin
borrador. Sube también la ficha y el changelog de este `versionCode`.

- Para dejarlo preparado sin publicar: `con-claves 'fastlane subir track:internal release_status:draft'`.
- Para resubir también capturas e icono: añadir `images:true`.
- Si no hay acceso a fastlane, el AAB del paso 4 se puede subir a mano en Play Console; las notas
  con etiquetas de idioma están en [`play-release-notes.md`](play-release-notes.md).

---

## 6. Dejar constancia

Añadir una entrada al `## Log` del documento de la funcionalidad en `odd/tasks/` con la versión, el
PR de la subida, el commit del tag, el certificado y lo que queda sin comprobar en un móvil real.
