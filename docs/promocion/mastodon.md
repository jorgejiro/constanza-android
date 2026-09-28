# Mastodon post — Constanza

A four-toot thread, in English and in Spanish. Every toot fits Mastodon's default
500-character limit. Mastodon counts every link as exactly 23 characters, no matter
its real length ([source: docs.joinmastodon.org/user/posting](https://docs.joinmastodon.org/user/posting/)) —
the character counts below already use that rule, not the raw text length.

## Before publishing

- **Publish once the Play listing is live** — the app is currently in review; the
  Play link in post 1 will 404 until Google approves it.
- **Publish the thread as chained replies**: the first toot is the one people share
  on its own, and the rest reply to it. Post 1 stands alone in case nobody opens the
  thread.
- **Attach screenshots to the first toot, always with alt text.** An image with no
  description gets boosted far less on Mastodon, and some people never boost one
  without it. Screenshots and their alt text are listed at the end of each language
  section.
- **Hashtags in CamelCase** (`#HabitTracker`, not `#habittracker`): screen readers
  parse them as separate words. Mastodon has no algorithm, so hashtags are what lets
  someone who doesn't follow you find the post.
- **Public visibility on the first toot**, and "unlisted" on the replies, so the
  thread doesn't flood local timelines with four toots.
- **Suggested instances/tags**: `fosstodon.org` (FOSS-friendly, good audience for the
  open-source angle) and the `#Android`/`#AndroidDev` local timelines. Post around a
  weekday morning (Europe/Madrid), when FOSS and Android accounts are more active.
- **Pin the thread** to the profile after posting, at least for the first weeks.
- If publishing both languages, do it on different days, or post the Spanish thread
  separately with its language tag set to Spanish in the composer.

---

## English

### 1/4 (264 characters)

```text
I couldn't find a habit tracker on the Play Store that owned up to a missed day instead of just letting it vanish, so I built one.

Constanza: answer reminders Yes, No or Snooze right from the notification, without opening the app.

Get it: https://play.google.com/store/apps/details?id=com.jjrapps.constanza
```

### 2/4 (259 characters)

```text
A day you skip doesn't disappear: at midnight, any unanswered slot becomes "Missed". No hiding it to feel better about it — that record is what the current streak, best streak and last-30-days compliance are built from, per habit.

#HabitTracker #Productivity
```

### 3/4 (282 characters)

```text
Also: six schedule types (daily, several times a day, times a week, specific weekdays, monthly, every N days), 21 colours plus a custom hue/saturation/brightness picker, a nightly review notification, export/import to a file, dark UI only, English and Spanish.

#Android #AndroidDev
```

### 4/4 (289 characters)

```text
It's free, no ads, no account, no cloud, no analytics — it doesn't even declare the internet permission. And it's open source.

Code: https://github.com/jorgejiro/constanza-android

Missing a feature you'd need? Email me at jjrmobileapps@gmail.com and I'll gladly review it.

#OpenSource #FOSS #Privacy #IndieDev
```

### Images for the first toot

| File | Alt text |
| :--- | :--- |
| `fastlane/metadata/android/en-US/images/phoneScreenshots/1.png` | Today screen of Constanza on a dark background, showing September 28, 2026. Under Now, the habit "Walk" in green with Yes and No buttons. Under Later, "Drink water" in blue marked In progress, and "Stretch" in orange with Yes and No buttons. Under Answered, "Read 20 minutes" in yellow with a green checkmark, and "Meditate" in purple with a red cross. |
| `fastlane/metadata/android/en-US/images/phoneScreenshots/2.png` | Android notification shade showing a Constanza reminder for the habit "Drink water", with Yes, No and Snooze buttons that answer it without opening the app. |
| `fastlane/metadata/android/en-US/images/phoneScreenshots/4.png` | Edit habit screen for "Read 20 minutes" on a dark background, showing the full palette of 21 colours plus a rainbow custom colour picker, frequency set to Daily, and a reminder time of 16:59. |
| `fastlane/metadata/android/en-US/images/phoneScreenshots/7.png` | Progress screen for the habit "Read 20 minutes" showing a current streak of 10, a best streak of 10, and 93% compliance over the last 30 days. |

---

## Español

### 1/4 (290 caracteres)

```text
No encontraba en Google Play una app de hábitos que reconociera un día fallado en vez de dejarlo desaparecer sin más, así que hice la mía.

Constanza: respondes los recordatorios con Sí, No o Aplazar directamente desde la notificación, sin abrir la app.

Descárgala: https://play.google.com/store/apps/details?id=com.jjrapps.constanza
```

### 2/4 (292 caracteres)

```text
Un día que no contestas no desaparece: a medianoche, cualquier hueco sin responder pasa a "No hecho". No lo oculta para que te sientas mejor: ese registro es la base de la racha actual, la mejor racha y el cumplimiento de los últimos 30 días de cada hábito.

#HábitosSaludables #Productividad
```

### 3/4 (328 caracteres)

```text
Además: seis tipos de frecuencia (diaria, varias veces al día, veces por semana, días concretos, mensual, cada N días), 21 colores más un selector personalizado de tono, saturación y brillo, aviso de repaso nocturno, exportación e importación a un archivo, interfaz siempre oscura, español e inglés.

#Android #DesarrolloAndroid
```

### 4/4 (297 caracteres)

```text
Es gratis, sin anuncios, sin cuenta, sin nube, sin analítica: ni siquiera declara el permiso de internet. Y es software libre.

Código: https://github.com/jorgejiro/constanza-android

¿Echas en falta alguna función? Escríbeme a jjrmobileapps@gmail.com y la revisaré con gusto.

#SoftwareLibre #FOSS #Privacidad #IndieDev
```

### Imágenes del primer toot

| Fichero | Texto alternativo |
| :--- | :--- |
| `fastlane/metadata/android/en-US/images/phoneScreenshots/1.png` | Pantalla Hoy de Constanza sobre fondo oscuro, con la fecha 28 de septiembre de 2026. En Ahora, el hábito "Walk" en verde con botones Sí y No. En Más tarde, "Drink water" en azul marcado En curso, y "Stretch" en naranja con botones Sí y No. En Contestados, "Read 20 minutes" en amarillo con una marca verde, y "Meditate" en morado con una cruz roja. |
| `fastlane/metadata/android/en-US/images/phoneScreenshots/2.png` | Panel de notificaciones de Android con un recordatorio de Constanza para el hábito "Drink water", con botones Sí, No y Aplazar que lo responden sin abrir la app. |
| `fastlane/metadata/android/en-US/images/phoneScreenshots/4.png` | Pantalla de edición del hábito "Read 20 minutes" sobre fondo oscuro, con la paleta completa de 21 colores más un selector de color personalizado en forma de arcoíris, la frecuencia puesta en Diaria y una hora de recordatorio a las 16:59. |
| `fastlane/metadata/android/en-US/images/phoneScreenshots/7.png` | Pantalla de progreso del hábito "Read 20 minutes" con una racha actual de 10, una mejor racha de 10, y un 93 % de cumplimiento en los últimos 30 días. |
