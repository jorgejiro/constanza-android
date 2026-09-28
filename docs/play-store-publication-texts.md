# Textos para Google Play — Constanza

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

## Ficha principal — App name

Límite: 30 caracteres. El nombre de la ficha está decidido (`Constanza - Buenos hábitos`); lo que
se propone y cuenta aquí es su equivalente en inglés para la ficha en-US.

- **es-ES** (26 caracteres): `Constanza - Buenos hábitos`
- **en-US** (23 caracteres): `Constanza - Good habits`

El nombre en el lanzador (`app_name` en `strings.xml`, `translatable="false"`) es **siempre**
"Constanza", sin el subtítulo, en los dos idiomas. El subtítulo es de la ficha, no del icono.

---

## Ficha principal — Short description

Límite: 80 caracteres.

### es-ES (79 caracteres)

```text
Hábitos con recordatorios que respondes sin abrir la app. Sin nube ni anuncios.
```

### en-US (69 caracteres)

```text
Habit reminders you answer without opening the app. No cloud, no ads.
```

---

## Ficha principal — Full description

Límite: 4000 caracteres.

Las tres cosas que este texto dice **a propósito**: que un día sin contestar se **registra como
"No hecho", nunca se oculta**; que los recordatorios se contestan **desde la propia notificación**
sin abrir la app; y que la interfaz es **oscura siempre**, porque el color de cada hábito es el
único color que lleva la pantalla (spec `Habit Colour Is The Only Chroma`).

### es-ES (3031 caracteres)

```text
Constanza es una app de hábitos que se abre y se usa en segundos. No pide cuenta, no sincroniza nada en la nube y no tiene anuncios ni analítica: no declara siquiera el permiso de internet, así que no podría conectarse aunque quisiera.

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
• Sin cuentas, sin nube, sin anuncios, sin seguimiento, sin permiso de internet.
```

### en-US (2832 caracteres)

```text
Constanza is a habit-tracking app that opens and works in seconds. No account, nothing synced to any cloud, no ads and no analytics: it does not even declare the internet permission, so it could not connect even if it wanted to.

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
• No accounts, no cloud, no ads, no tracking, no internet permission.
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

### es-ES (119 caracteres)

```text
Hábitos con recordatorios de Sí/No/Aplazar desde la notificación. Sin cuentas, sin nube, sin anuncios, sin seguimiento.
```

### en-US (105 caracteres)

```text
Habit reminders answered Yes/No/Snooze from the notification. No accounts, no cloud, no ads, no tracking.
```

---

## Capturas — Orden recomendado

El orden importa: en Play se ven las dos primeras sin desplazarse.

| # | Escena | Por qué está aquí |
|---|---|---|
| 1 | Hoy, con huecos contestados y pendientes a la vista | Es la app entera en una imagen: qué toca y qué ya se hizo |
| 2 | Notificación de recordatorio en la sombra, con Sí/No/Aplazar | La razón de instalar: contestar sin abrir la app |
| 3 | Editor de hábito con las opciones de frecuencia | La segunda razón: hábitos con la forma que tú quieras, no una plantilla |
| 4 | Selector de color, paleta y personalizado | Cada hábito es su color; lo personalizable se ve, no se cuenta |
| 5 | Progreso: racha, mejor racha, cumplimiento del 30 % | La recompensa de usar la app más de un día |
| 6 | Lista de hábitos | Cómo se gestionan varios hábitos a la vez |
| 7 | Ajustes: repaso del día y copia de seguridad | Que existen el idioma, el repaso nocturno y la copia, y que no hay nada raro dentro |

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
