# Publicación en F-Droid

F-Droid no admite subir APK: compila la app desde este repositorio con una **receta** que vive en
[`fdroiddata`](https://gitlab.com/fdroid/fdroiddata), y la firma con su propia clave. Publicar es
abrir un merge request allí con la receta.

| Pieza | Dónde |
|---|---|
| Receta, validada con `fdroid lint` y en el formato de `fdroid rewritemeta` | [`com.jjrapps.constanza.yml`](com.jjrapps.constanza.yml) |
| Ficha: título, descripciones, changelog, icono, gráfico y capturas | `fastlane/metadata/android/{en-US,es-ES}/` — F-Droid la lee del repositorio |

## Enviar la app

1. Fork de [`fdroid/fdroiddata`](https://gitlab.com/fdroid/fdroiddata): ya existe en
   `jorgejiro/fdroiddata`.
2. En el fork, crear una rama `com.jjrapps.constanza` y añadir la receta como
   `metadata/com.jjrapps.constanza.yml`. Con `glab api` o desde la web, sin clonar: el repo pesa
   varios GB.
3. Commit: `New app: Constanza`.
4. Abrir el merge request contra `fdroid/fdroiddata:master` y rellenar la checklist de la plantilla.
   La CI del merge request (corre en el fork) compila la app: si falla, el log dice por qué.
5. Contestar a los revisores. La revisión la hacen voluntarios y puede tardar semanas.

## Lo que hay que saber

- **La firma es la de F-Droid, no la tuya.** Quien instala desde Play no puede actualizar desde
  F-Droid, ni al revés, sin desinstalar antes. Para firmar con la clave propia hacen falta builds
  reproducibles, y ya no se pueden activar después.
- **La receta de la 0.1.16 apunta a un commit, no al tag `v0.1.16`.** El tag es anterior a quitar el
  bloque de dependencias cifrado para Google (`dependenciesInfo`), que F-Droid rechaza. La app es la
  misma: solo cambia la configuración del build. Por eso esta rama se fusiona con merge commit,
  nunca con squash: si no, ese commit desaparece de `main` y F-Droid no puede compilarlo.
- **El commit de la receta tiene que contener `fastlane/`.** F-Droid lee la ficha del mismo commit
  que compila. Comprobarlo con `git ls-tree -r --name-only <sha> | rg ^fastlane/`.
- **Las versiones siguientes se publican solas.** Con `UpdateCheckMode: Tags` y
  `AutoUpdateMode: Version`, F-Droid detecta cada tag `vX.Y.Z` nuevo, lee `versionCode` y
  `versionName` de `app/build.gradle.kts` y añade el build. Basta con seguir etiquetando las releases.
  Si el tag más reciente tiene un `versionCode` menor que `CurrentVersionCode`, `checkupdates` falla.
- **El changelog de cada versión** va en `fastlane/metadata/android/<idioma>/changelogs/<versionCode>.txt`,
  con un máximo de 500 caracteres. Sirve el mismo texto que las notas de Play.
- **Nada en el build puede descargar herramientas por su cuenta.** El escáner de F-Droid rechaza el
  plugin `foojay-resolver` y borra `gradle-daemon-jvm.properties` antes de compilar. Su servidor
  (Debian trixie) ya trae JDK 21.
- **El repositorio tiene que ser público**: la CI clona sin credenciales.
