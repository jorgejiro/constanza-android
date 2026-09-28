# -*- coding: utf-8 -*-
"""Genera los textos de la ficha de Play y las notas de la primera publicacion, con
los conteos de caracteres calculados, no estimados. Los limites de Play son duros: un
texto que se pasa se rechaza al pegarlo, y contarlos a mano es como se cuela un error.

Puerto del script homonimo de sleep-noise-android (docs/play-store-publication-texts.md,
scripts/generar-textos-ficha.py), adaptado a Constanza: raiz de repo distinta, un solo
idioma de nombre fijo por ficha (sin variante "con cola de busqueda"/"limpio", porque el
nombre de Constanza ya esta decidido), y una unica entrada de notas de version porque esta
es la primera publicacion en Play (la app solo ha distribuido un APK por GitHub hasta ahora).
"""
import pathlib
import re
import sys

ROOT = pathlib.Path("/Users/jorge/dev/constanza-android")

# Nombre de ficha fijo (distinto del nombre del lanzador, que sigue siendo "Constanza",
# translatable="false" en app/src/main/res/values/strings.xml).
NAME_ES = "Constanza - Buenos hábitos"
NAME_EN = "Constanza - Good habits"

SHORT_ES = "Hábitos con recordatorios que respondes sin abrir la app. Sin nube ni anuncios."
SHORT_EN = "Habit reminders you answer without opening the app. No cloud, no ads."

FULL_ES = """Constanza es una app de hábitos que se abre y se usa en segundos. No pide cuenta, no sincroniza nada en la nube y no tiene anuncios ni analítica: no declara siquiera el permiso de internet, así que no podría conectarse aunque quisiera.

Cada hábito tiene su propio color -de una paleta de 21, o uno personalizado con su propio selector de tono, saturación y brillo- y su propia frecuencia: todos los días, varias veces al día, un número de veces por semana, días concretos de la semana, un día del mes, o cada N días. Cualquier frecuencia puede llevar una o varias horas de recordatorio.

La pantalla Hoy agrupa lo que toca ahora, lo que toca más tarde y lo que ya has contestado, y puedes navegar a cualquier día pasado para ver qué ocurrió. Responder es un toque: Sí o No, sin abrir ningún diálogo. Y cuando llega el recordatorio, puedes responder directamente desde la notificación -Sí, No o Aplazar- sin ni siquiera desbloquear el teléfono.

Un día que no contestas no desaparece: a medianoche, cualquier hueco sin responder pasa a "No hecho". Constanza no oculta lo que dejaste pasar para hacerte sentir mejor; lo registra, y ese registro es la base de la racha y del cumplimiento. Progreso muestra la racha actual, la mejor racha y el porcentaje de cumplimiento de los últimos 30 días de cada hábito.

Los recordatorios sobreviven a un reinicio del teléfono y a un cambio de hora o de zona horaria: no hace falta volver a abrir la app para que sigan sonando. Si tu teléfono no concede el permiso de alarmas exactas por defecto, Constanza te avisa sin bloquear nada y te lleva al ajuste; si prefieres no concedérselo, los recordatorios igualmente siguen llegando, solo que pueden hacerlo con algunos minutos de margen.

Cada noche, a las 23:00 por defecto, un aviso de repaso te recuerda lo que quedó pendiente ese día -o solo si de verdad quedó algo, si así lo prefieres-. Y en Ajustes puedes cambiar cuánto dura un aplazamiento, elegir el idioma (siguiendo al sistema, español o inglés), y exportar o importar todos tus datos como un archivo, con el selector de archivos del propio sistema: es tu copia, no la nuestra.

La interfaz es oscura siempre, sin modo claro. No es un olvido: el fondo es deliberadamente neutro para que el único color de la pantalla sea el que tú le has puesto a cada hábito.

Qué incluye:

• Hábitos con seis tipos de frecuencia y una o varias horas de recordatorio.
• 21 colores más un selector personalizado de tono, saturación y brillo.
• Recordatorios con respuesta Sí/No/Aplazar directamente desde la notificación.
• Un día sin contestar se registra como "No hecho" a medianoche, nunca se oculta.
• Racha actual, mejor racha y cumplimiento de los últimos 30 días por hábito.
• Aviso de repaso nocturno, configurable, o solo si queda algo pendiente.
• Exportación e importación de datos a un archivo, con el selector del sistema.
• Recordatorios que sobreviven a reinicios y a cambios de hora.
• Español e inglés.
• Sin cuentas, sin nube, sin anuncios, sin seguimiento, sin permiso de internet."""

FULL_EN = """Constanza is a habit-tracking app that opens and works in seconds. No account, nothing synced to any cloud, no ads and no analytics: it does not even declare the internet permission, so it could not connect even if it wanted to.

Each habit gets its own colour -from a palette of 21, or a custom one from its own hue/saturation/brightness picker- and its own schedule: every day, several times a day, a number of times a week, specific days of the week, a day of the month, or every N days. Any schedule can carry one or several reminder times.

The Today screen groups what is due now, what is due later, and what you have already answered, and you can step back to any past day to see what happened. Answering is one tap: Yes or No, no dialog to open. And when a reminder arrives, you can answer it right from the notification -Yes, No or Snooze- without even unlocking the phone.

A day you do not answer does not disappear: at midnight, any slot left unanswered becomes "Missed". Constanza does not hide what you let slip to make you feel better about it; it records it, and that record is what the streak and the compliance number are built from. Progress shows the current streak, the best streak, and the last-30-days compliance percentage for each habit.

Reminders survive a phone reboot and a time or timezone change: you do not need to reopen the app to keep them firing. If your phone does not grant the exact-alarm permission by default, Constanza tells you without blocking anything and takes you to the setting; if you would rather not grant it, reminders still arrive, just with a few minutes of slack.

Every night, at 23:00 by default, a review notification reminds you what is still open that day -or only when something actually is, if you would rather set it that way. And in Settings you can change how long a snooze lasts, pick the language (following the system, English or Spanish), and export or import all your data as a file, through the system's own file picker: it is your copy, not ours.

The interface is dark, always, with no light mode. That is not an oversight: the background is deliberately neutral so the only colour on screen is the one you gave each habit.

What you get:

• Habits with six schedule types and one or more reminder times.
• 21 colours plus a custom hue/saturation/brightness picker.
• Reminders answered Yes/No/Snooze right from the notification.
• An unanswered day is recorded as "Missed" at midnight, never hidden.
• Current streak, best streak, and last-30-days compliance per habit.
• A nightly review notification, configurable, or only when something is pending.
• Data export and import to a file, through the system picker.
• Reminders that survive reboots and time changes.
• English and Spanish.
• No accounts, no cloud, no ads, no tracking, no internet permission."""

PROMO_ES = "Hábitos con recordatorios de Sí/No/Aplazar desde la notificación. Sin cuentas, sin nube, sin anuncios, sin seguimiento."
PROMO_EN = "Habit reminders answered Yes/No/Snooze from the notification. No accounts, no cloud, no ads, no tracking."

NOTES_0_1_16_ES = """Primera versión de Constanza en Google Play.

• Hábitos con seis tipos de frecuencia y recordatorios con respuesta Sí/No/Aplazar desde la notificación.
• Racha actual, mejor racha y cumplimiento de los últimos 30 días por hábito.
• Aviso de repaso nocturno de lo pendiente, y copia de tus datos a un archivo.

Sin cuentas, sin nube, sin anuncios, sin seguimiento."""

NOTES_0_1_16_EN = """The first version of Constanza on Google Play.

• Habits with six schedule types and reminders answered Yes/No/Snooze from the notification.
• Current streak, best streak, and last-30-days compliance per habit.
• A nightly review notification for what is still open, and a file backup of your data.

No accounts, no cloud, no ads, no tracking."""

# Las versiones publicadas en Play, la más reciente primero. Esta es la primera, así que la
# lista tiene una sola entrada; una publicación futura añade la suya arriba y esta se queda
# como historial, igual que en sleep-noise.
RELEASES = [
    ("0.1.16", 17, "pendiente de publicar en Play (primera publicación)", NOTES_0_1_16_ES, NOTES_0_1_16_EN),
]

LIMITS = [
    ("Nombre ES", NAME_ES, 30), ("Nombre EN", NAME_EN, 30),
    ("Short ES", SHORT_ES, 80), ("Short EN", SHORT_EN, 80),
    ("Full ES", FULL_ES, 4000), ("Full EN", FULL_EN, 4000),
    ("Promo ES", PROMO_ES, 170), ("Promo EN", PROMO_EN, 170),
] + [
    item
    for version, _code, _status, es, en in RELEASES
    for item in (("Notas %s ES" % version, es, 500), ("Notas %s EN" % version, en, 500))
]
fail = False
for label, text, limit in LIMITS:
    n = len(text)
    flag = "OK  " if n <= limit else "PASA"
    if n > limit:
        fail = True
    print("%-14s %5d / %-5d %s" % (label, n, limit, flag))
if fail:
    sys.exit("Hay textos que pasan del límite de Play; hay que recortarlos antes de generar el documento.")


def block(es, en):
    return ("### es-ES (" + str(len(es)) + " caracteres)\n\n```text\n" + es +
            "\n```\n\n### en-US (" + str(len(en)) + " caracteres)\n\n```text\n" + en + "\n```\n")


def fill(template, mapping):
    for key, value in mapping.items():
        template = template.replace("@@" + key + "@@", str(value))
    leftover = re.findall(r"@@\w+@@", template)
    assert not leftover, leftover
    return template


texts_tpl = """# Textos para Google Play — Constanza

Documento de trabajo para rellenar la ficha de Play Console y llevar Constanza de "solo APK en
GitHub" a su primera publicación. Los conteos de caracteres de este fichero **están calculados,
no estimados**: los genera `scripts/generar-textos-ficha.py` a partir de los textos de origen. Si
editas un texto aquí, vuelve a pasar el script para que el conteo no mienta.

> La app no tiene cuentas, nube, anuncios, analítica ni seguimiento, y **no declara el permiso
> `INTERNET`**. No hay servicio en primer plano real (ver más abajo). Eso hace que los
> cuestionarios de Play sean triviales y, sobre todo, verificables por quien revise.

---

## Checklist para publicar

1. `./gradlew testDebugUnitTest detektMain` en verde.
2. `./gradlew :app:compileDebugAndroidTestKotlin` en verde — compilar el androidTest **no** es
   ejecutarlo; ver el punto siguiente.
3. `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` en verde. Corre la suite completa en los
   emuladores API 31 y API 37 que Gradle aprovisiona él mismo — sin teléfono. `CoreFlowE2ETest`
   cubre el flujo principal de punta a punta: diálogo real de permiso, añadir hábito, notificación
   de recordatorio, contestarla, eliminar el hábito.
4. Recorrido manual con la app instalada en un emulador (o, si Jorge quiere, en el S25 con acuerdo
   previo — nunca por defecto):
   - primer arranque: onboarding, permiso de notificaciones concedido y **denegado**;
   - alarma exacta concedida y denegada, y el banner de "puede llegar tarde" en Hoy;
   - crear un hábito de cada tipo de frecuencia, con y sin hora de recordatorio;
   - responder Sí/No desde la pantalla Hoy y desde la notificación (Sí/No/Aplazar);
   - navegar a un día pasado y comprobar que lo no respondido pasa a "No hecho" tras medianoche;
   - Progreso: racha, mejor racha, cumplimiento;
   - repaso nocturno en los dos modos (cada noche / solo si queda algo pendiente);
   - exportar datos, desinstalar y reinstalar (o borrar datos), importar, comprobar que todo vuelve;
   - cambio de idioma del sistema y desde el selector interno (System/English/Español);
   - reinicio del emulador con recordatorios programados, y cambio manual de hora del sistema.
5. `./gradlew :app:bundleRelease` firmado con la upload key vigente (o el `.apk` de release si
   la distribución sigue siendo GitHub además de Play).
6. Subir a **Internal testing** primero, validar, y promover — nunca directo a producción.
7. Pegar las notas de la versión desde [`play-release-notes.md`](play-release-notes.md), con el
   bloque de etiquetas de idioma para hacerlo de una sola pegada.
8. Revisar en Play Console: ficha principal · países · categoría · seguridad de los datos ·
   clasificación de contenido (IARC) · precio gratis · política de privacidad.
9. Enviar a revisión con despliegue por fases: por ejemplo 20 % durante 48 h, luego 100 %.

---

## Subir a Google Play con fastlane

Desde `odd/tasks/fastlane-supply.md`: en vez de pegar 42 imágenes y los textos a mano en Play
Console, `fastlane supply` (action `upload_to_play_store`, ver `fastlane/Fastfile`) los sube con
un comando, leyendo `fastlane/metadata/android/` y `fastlane/Appfile`. Nada de esto sustituye el
checklist de arriba: las comprobaciones y el recorrido manual siguen siendo el mismo paso 1-4
antes de generar el AAB.

### Configuración inicial (una sola vez, la hace el dueño de la cuenta)

La API de Google Play no puede hacer nada de esto por ti; son pasos manuales previos a la primera
subida por `fastlane`:

1. Crear la app en Play Console (nombre de paquete `com.jjrapps.constanza`).
2. Subir el primer AAB **a mano**, desde Play Console. La API de Android Publisher no puede crear
   la primera versión de una app nueva; solo puede actuar sobre una app que ya tiene al menos una
   subida.
3. Crear una cuenta de servicio en Google Cloud (mismo proyecto o uno nuevo) y habilitar la
   **Google Play Android Developer API** en ese proyecto.
4. Generar una clave JSON para esa cuenta de servicio y descargarla.
5. En Play Console → **Usuarios y permisos**, invitar la cuenta de servicio (su email
   `...@....iam.gserviceaccount.com`) con permisos de **Gestión de versiones** (release) y
   **Ficha de Play Store** (store listing) sobre esta app.
6. Guardar la clave JSON **fuera del repositorio**, en `~/.config/play/jjrmobileapps.json` (la
   ruta por defecto de `fastlane/Appfile`) o en cualquier otra ruta, exportándola como
   `SUPPLY_JSON_KEY` antes de invocar `fastlane`. La clave nunca debe entrar en el repositorio ni
   subirse a ningún sitio.

Hasta que estos seis pasos estén hechos, ningún comando de `fastlane` de más abajo tiene
credenciales con las que autenticarse.

### Comandos

- `fastlane validar` — valida toda la ficha (textos, capturas, icono, gráfico de función) contra
  la API sin publicar nada; no sube ningún AAB. Útil para comprobar que la clave y los permisos
  funcionan antes de tocar nada real.
- `fastlane ficha` — sube solo la ficha (textos + capturas + icono + gráfico de función), sin
  ningún binario.
- `fastlane subir track:internal` — compila `:app:bundleRelease` y sube el AAB firmado a la pista
  indicada (`internal` por defecto) con estado `draft` (por defecto no publica, solo lo deja listo
  para revisar en Play Console). Admite también `release_status:` y `images:true` si además hay
  que resubir capturas o icono junto con el binario.

### Lo que sigue siendo manual

- **Los cuestionarios de "Contenido de la app"** (clasificación de contenido/IARC, público
  objetivo, anuncios, acceso a la app): la API de Android Publisher no los expone; se rellenan
  siempre desde Play Console, ver la sección "Contenido de la app" más abajo en este documento.
- **Seguridad de los datos**: el endpoint de la API espera el CSV que exporta la propia Play
  Console para esta sección, no texto libre; para Constanza el formulario entero es un único
  "No" (ver la sección "Seguridad de los datos" más abajo), así que rellenarlo a mano en Play
  Console sigue siendo más simple que fabricar ese CSV.

### Referencias

- Acciones y opciones de `supply`: <https://docs.fastlane.tools/actions/supply/>.
- Configuración de la Google Play Android Developer API (proyecto de Google Cloud, cuenta de
  servicio, habilitar la API): <https://developers.google.com/android-publisher/getting_started>.

---

## Ficha principal — App name

Límite: 30 caracteres. El nombre de la ficha está decidido (`Constanza - Buenos hábitos`); lo que
se propone y cuenta aquí es su equivalente en inglés para la ficha en-US.

- **es-ES** (@@name_es_n@@ caracteres): `@@name_es@@`
- **en-US** (@@name_en_n@@ caracteres): `@@name_en@@`

El nombre en el lanzador (`app_name` en `strings.xml`, `translatable="false"`) es **siempre**
"Constanza", sin el subtítulo, en los dos idiomas. El subtítulo es de la ficha, no del icono.

---

## Ficha principal — Short description

Límite: 80 caracteres.

@@short@@
---

## Ficha principal — Full description

Límite: 4000 caracteres.

Las tres cosas que este texto dice **a propósito**: que un día sin contestar se **registra como
"No hecho", nunca se oculta**; que los recordatorios se contestan **desde la propia notificación**
sin abrir la app; y que la interfaz es **oscura siempre**, porque el color de cada hábito es el
único color que lleva la pantalla (spec `Habit Colour Is The Only Chroma`).

### es-ES (@@full_es_n@@ caracteres)

```text
@@full_es@@
```

### en-US (@@full_en_n@@ caracteres)

```text
@@full_en@@
```

---

## Ficha principal — Categoría y etiquetas

- **Categoría: Productividad.** Constanza no mide ni registra nada de salud física — no hay pasos,
  peso, sueño ni datos médicos — construye rutinas y hábitos genéricos con recordatorios, que es
  el terreno de Productividad, no de Salud y bienestar. Es también donde se clasifican apps
  comparables de seguimiento de hábitos (p. ej. Loop Habit Tracker). Salud y bienestar encajaría
  si Constanza tratara hábitos específicamente saludables (ejercicio, agua, sueño), pero la app es
  deliberadamente neutra sobre qué hábito sigues.
- **Etiquetas** (hasta cinco, en orden de preferencia): Hábitos · Recordatorios · Productividad ·
  Rutinas · Rachas.
- **Precio**: gratis, sin compras integradas.
- **Países**: todos.

---

## Ficha principal — Texto promocional

Límite: 170 caracteres.

@@promo@@
---

## Capturas — Orden recomendado

El orden importa: en Play se ven las dos primeras sin desplazarse.

| # | Escena | Por qué está aquí |
|---|---|---|
| 1 | Hoy, con huecos contestados y pendientes a la vista | Es la app entera en una imagen: qué toca y qué ya se hizo |
| 2 | Notificación de recordatorio en la sombra, con Sí/No/Aplazar | La razón de instalar: contestar sin abrir la app |
| 3 | Editor de hábito con las opciones de frecuencia | La segunda razón: hábitos con la forma que tú quieras, no una plantilla |
| 4 | Selector de color, paleta y personalizado | Cada hábito es su color; lo personalizable se ve, no se cuenta |
| 5 | Lista de hábitos | Cómo se gestionan varios hábitos a la vez |
| 6 | Ajustes: repaso del día y copia de seguridad | Que existen el idioma, el repaso nocturno y la copia, y que no hay nada raro dentro |
| 7 | Progreso: racha, mejor racha, cumplimiento del 30 % | La recompensa de usar la app más de un día |

Los ficheros van en `docs/store-assets/capturas/<idioma>/<formato>/`, con el idioma primero
porque así es como Play Console las pide: una ficha por idioma, y dentro sus formatos. Ver la
tarea T3 de `odd/tasks/play-store-listing.md` para la generación automatizada (semilla de datos
de demostración + pipeline de captura); no se hacen a mano.

---

## Feature graphic — Texto sugerido

1024 × 500. Fondo oscuro cálido, coherente con la app (spec `Dark-Only Rendering`).

- **es-ES**: «Hábitos que se contestan desde la notificación.»
- **en-US**: «Habits you answer from the notification.»

Es la característica que más distingue a Constanza de un simple checklist: no hace falta abrir
la app para marcar un hábito como hecho.

---

## Seguridad de los datos — Respuestas

| Pregunta de Play Console | Respuesta |
|---|---|
| ¿La app recopila o comparte alguno de los tipos de datos requeridos? | **No** |
| ¿Todos los datos se cifran en tránsito? | No aplica: no hay tránsito. La app no declara `INTERNET` |
| ¿Ofrece una forma de solicitar la eliminación de datos? | No aplica: no hay datos en ningún servidor. Desinstalar borra la base de datos local (`allowBackup="false"`, sin copia automática) |
| Resumen de privacidad | Constanza no recopila ni comparte datos de usuario. Hábitos, horarios y respuestas se guardan solo en el dispositivo; el usuario decide si los exporta a un archivo propio |

---

## Contenido de la app

| Sección | Respuesta |
|---|---|
| Clasificación de contenido (IARC) | Todo «no» → clasificación para todos los públicos |
| Público objetivo | Mayores de 13 años. No dirigida a menores |
| Anuncios | No contiene |
| Acceso a la app | Todo el contenido está disponible sin restricciones ni credenciales |
| Política de privacidad | **https://jorgejiro.es/apps/constanza/privacidad/** |

---

## Permiso de alarmas exactas — verificación de política

Verificado contra fuentes primarias de Android/Play (septiembre de 2026), no asumido:

- Android 14+ deniega `SCHEDULE_EXACT_ALARM` por defecto en instalaciones nuevas que apuntan a
  API 33+ y no son apps de alarma/calendario ni están exentas — Constanza cae en ese caso, y por
  eso existe el banner "puede llegar tarde", el flujo de onboarding que explica el permiso, y
  `ExactAlarmPermissionReceiver`, que escucha
  `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` para reprogramar si el usuario lo concede o
  revoca más tarde.
  Fuente: [Schedule exact alarms are denied by default](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms).
- **`USE_EXACT_ALARM` es un permiso restringido con formulario de declaración obligatorio en Play
  Console** (`goo.gle/play-permission-decl-form`), reservado a apps cuya función principal sea
  precisamente alarmas/despertadores o calendario con avisos de eventos. Constanza no es ninguna
  de las dos cosas — es una app de hábitos — así que **no debe declarar `USE_EXACT_ALARM`**, y de
  hecho no lo hace: el manifest solo declara `SCHEDULE_EXACT_ALARM`.
  Fuente: [Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/9888170?hl=en).
- **`SCHEDULE_EXACT_ALARM` en sí NO tiene formulario de declaración en Play Console** — el
  formulario de declaración citado arriba es específicamente para `USE_EXACT_ALARM`. La guía de
  Android sobre justificar el uso de alarmas exactas ("¿de verdad tu app lo necesita?") es una
  guía de ingeniería para que el propio desarrollador evalúe su caso de uso, no un trámite que
  Play pida rellenar para este permiso.
  Fuente: [Schedule alarms — Background work](https://developer.android.com/develop/background-work/services/alarms/schedule).
- **Conclusión, no asumida**: Constanza no necesita presentar ninguna declaración de permiso
  restringido para `SCHEDULE_EXACT_ALARM` en Play Console. Si Play Console mostrase en el futuro
  una pregunta de «Permiso de alarma exacta» distinta del formulario de `USE_EXACT_ALARM`
  (algunas cuentas la ven como una sección de App content con una casilla + explicación breve, no
  verificable sin acceso a la consola), la respuesta breve para rellenar esa casilla, si aparece:

  **en-US**

  ```text
  Constanza reminds the user about self-defined habits at times they scheduled themselves. Reminders
  must fire within a few minutes of the scheduled time to be useful (a reminder for a 7 AM habit that
  arrives at 9 AM defeats its purpose), so scheduling uses AlarmManager.setExactAndAllowWhileIdle.
  The app checks canScheduleExactAlarms() before scheduling, shows a non-blocking banner and an
  onboarding explanation when the permission is not granted, and still delivers reminders on an
  inexact schedule in that case.
  ```

  **es-ES**

  ```text
  Constanza recuerda al usuario hábitos que él mismo definió, a las horas que él mismo programó. El
  recordatorio debe llegar dentro de pocos minutos de la hora programada para tener sentido (un
  recordatorio de un hábito de las 7:00 que llega a las 9:00 pierde su propósito), así que la
  programación usa AlarmManager.setExactAndAllowWhileIdle. La app comprueba
  canScheduleExactAlarms() antes de programar, muestra un banner no bloqueante y una explicación en
  el onboarding cuando el permiso no está concedido, y de todos modos entrega los recordatorios con
  una programación inexacta en ese caso.
  ```

### Servicio en primer plano — no aplica

El manifest fusionado (`aapt2`/`processReleaseMainManifest`) añade el permiso `FOREGROUND_SERVICE`
y un `<service>` `androidx.work.impl.foreground.SystemForegroundService`, ambos de WorkManager, y
ninguno de los dos entra en juego: `rg -n "setForeground|ForegroundInfo"` sobre el código de la
app no encuentra ningún resultado, así que ningún `Worker` de Constanza pide primer plano en
ningún momento. No hay ninguna notificación permanente ni servicio en primer plano real, así que
la declaración de tipo de servicio en primer plano de Play (`FOREGROUND_SERVICE_*`) no debería
aplicar. Si Play Console la pidiera de todos modos por ver el permiso en el binario, la respuesta
es "no se usa": el permiso lo declara una librería, no la app.

### Permisos declarados en el manifest de la app

| Permiso | Para qué |
|---|---|
| `SCHEDULE_EXACT_ALARM` | Que un recordatorio de hábito y el aviso de repaso nocturno suenen a la hora exacta programada, no con margen de minutos u horas (ver verificación de política arriba) |
| `POST_NOTIFICATIONS` | Mostrar la notificación de recordatorio (con Sí/No/Aplazar) y la de repaso nocturno |
| `RECEIVE_BOOT_COMPLETED` | Volver a programar todos los recordatorios tras un reinicio del teléfono, para que no se pierdan silenciosamente |

**No** se declaran en el manifest de la app: `INTERNET`, ningún permiso de almacenamiento,
`USE_EXACT_ALARM`, ni ningún SDK de anuncios o analítica.

Y el mismo matiz que sleep-noise ya documentó para su propio manifest fusionado: el
**manifest fusionado** de Constanza añade permisos que no pide el código de la app, todos de
WorkManager — `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` (no usado, ver arriba) y
el permiso de firma propio `com.jjrapps.constanza.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, que
protege los propios receptores dinámicos internos de WorkManager y no es visible al usuario ni
está en la lista de permisos sensibles de Play. **Son los que Play enseña**, porque Play mira el
binario y no el fichero del repositorio. Verificado con
`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`
para versionCode 17 / 0.1.16 (regenerar con
`./gradlew :app:processReleaseMainManifest` si el intermedio no existe, con
`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`).

---

## Declaración corta para soporte o revisión

**en-US**

```text
Constanza tracks habits offline: no accounts, no cloud sync, no ads, no analytics, and it never
requests the internet permission. Each habit has its own colour and schedule, and its reminders are
answered Yes/No/Snooze right from the notification. An unanswered day is recorded as missed at
midnight rather than hidden, which is what the streak and 30-day compliance numbers are built from.
Data lives only on the device; the user can export or import it as a file at any time.
```

**es-ES**

```text
Constanza sigue hábitos sin conexión: sin cuentas, sin sincronización en la nube, sin anuncios, sin
analítica, y nunca pide el permiso de internet. Cada hábito tiene su propio color y su propia
frecuencia, y sus recordatorios se contestan con Sí/No/Aplazar directamente desde la notificación.
Un día sin contestar se registra como perdido a medianoche en vez de ocultarse, que es lo que
alimenta la racha y el cumplimiento de los últimos 30 días. Los datos viven solo en el dispositivo;
el usuario puede exportarlos o importarlos como un archivo cuando quiera.
```
"""
texts = fill(texts_tpl, {
    "name_es": NAME_ES, "name_es_n": len(NAME_ES),
    "name_en": NAME_EN, "name_en_n": len(NAME_EN),
    "short": block(SHORT_ES, SHORT_EN),
    "full_es": FULL_ES, "full_es_n": len(FULL_ES),
    "full_en": FULL_EN, "full_en_n": len(FULL_EN),
    "promo": block(PROMO_ES, PROMO_EN),
})
(ROOT / "docs/play-store-publication-texts.md").write_text(texts)

notes_tpl = """# Novedades para Google Play — Constanza

Textos de **«Novedades»** («What's new») listos para pegar en Play Console al crear la release:
*Producción → Crear nueva versión → Notas de la versión*.

- **Límite de Google Play: 500 caracteres por idioma.** Cada bloque indica los que ocupa, calculados
  por `scripts/generar-textos-ficha.py`.
- La ficha permanente (nombre, descripciones, capturas, cuestionarios) está en
  [`play-store-publication-texts.md`](play-store-publication-texts.md). Este fichero es solo el texto
  que cambia en cada publicación.
- Esta es la **primera publicación en Play**: Constanza solo ha distribuido un `.apk` por GitHub
  hasta ahora (versionCode 17 / 0.1.16), así que el primer bloque resume el estado actual de la app
  en vez de listar solo lo último que cambió, igual que hizo sleep-noise en su propia 1.0.
- Al publicar una versión nueva, su bloque va arriba y los anteriores se quedan como historial.
- **Cada bloque lleva SIEMPRE tres subsecciones**, en este orden: `es-ES`, `en-US` y **`Formato con
  etiquetas de idioma`**. La tercera repite los dos textos envueltos en `<es-ES>` y `<en-US>` en un
  único bloque, que es lo que Play Console acepta de una sola pegada. Sin ella hay que copiar idioma
  por idioma, así que un bloque con solo las dos primeras está incompleto.

---

@@releases@@"""


def release_block(version, code, status, es, en):
    """Un bloque de novedades con sus tres subsecciones obligatorias."""
    return (
        "## %s (versionCode %d) — %s\n\n" % (version, code, status)
        + "### es-ES (%d caracteres)\n\n```text\n%s\n```\n\n" % (len(es), es)
        + "### en-US (%d caracteres)\n\n```text\n%s\n```\n\n" % (len(en), en)
        + "### Formato con etiquetas de idioma\n\n```xml\n<es-ES>\n%s\n</es-ES>\n<en-US>\n%s\n</en-US>\n```\n"
        % (es, en)
    )


notes = fill(notes_tpl, {"releases": "\n---\n\n".join(release_block(*r) for r in RELEASES)})
(ROOT / "docs/play-release-notes.md").write_text(notes)

# fastlane/metadata/android/<locale>/{title,short_description,full_description}.txt y
# changelogs/<versionCode>.txt — lo que Play Console (y `fastlane supply`) esperan encontrar.
# Play trata el contenido de estos ficheros de texto tal cual, sin salto de línea final añadido
# por convención; escribimos exactamente el texto contado arriba, sin newline de cierre, para que
# `wc -m` sobre el fichero coincida con el conteo impreso por este script.
FASTLANE = ROOT / "fastlane/metadata/android"
LOCALES = {
    "es-ES": {"title": NAME_ES, "short_description": SHORT_ES, "full_description": FULL_ES},
    "en-US": {"title": NAME_EN, "short_description": SHORT_EN, "full_description": FULL_EN},
}
CHANGELOGS = {"es-ES": NOTES_0_1_16_ES, "en-US": NOTES_0_1_16_EN}
VERSION_CODE = RELEASES[0][1]

for locale, fields in LOCALES.items():
    locale_dir = FASTLANE / locale
    locale_dir.mkdir(parents=True, exist_ok=True)
    for name, content in fields.items():
        (locale_dir / (name + ".txt")).write_text(content)
    changelogs_dir = locale_dir / "changelogs"
    changelogs_dir.mkdir(parents=True, exist_ok=True)
    (changelogs_dir / ("%d.txt" % VERSION_CODE)).write_text(CHANGELOGS[locale])

print("\nescritos:")
print(" docs/play-store-publication-texts.md", len(texts), "bytes")
print(" docs/play-release-notes.md", len(notes), "bytes")
for locale in LOCALES:
    for name in ("title", "short_description", "full_description"):
        p = FASTLANE / locale / (name + ".txt")
        print(" fastlane/metadata/android/%s/%s.txt" % (locale, name), len(p.read_text()), "chars")
    p = FASTLANE / locale / "changelogs" / ("%d.txt" % VERSION_CODE)
    print(" fastlane/metadata/android/%s/changelogs/%d.txt" % (locale, VERSION_CODE), len(p.read_text()), "chars")
