# Reddit post — Constanza

Two versions of the same post: English, for international subreddits, and Spanish, for
Spanish-speaking ones. The body is in Reddit Markdown: paste it as-is into the editor in
"Markdown" mode.

**Do not publish yet.** Constanza is submitted to Google Play and currently in review; the
Play link in the post (`https://play.google.com/store/apps/details?id=com.jjrapps.constanza`)
is not live yet. Wait until the listing is live and the link resolves before posting anywhere.

**Suggested flair:** varies per subreddit — see the "Flair / format" column in the
[Subreddits](#subreddits) table below. Where the flair isn't verified, check the sidebar/rules
before picking one.

**Screenshots to attach (2–3):** from
`fastlane/metadata/android/en-US/images/phoneScreenshots/` (the `es-ES` folder has the same
shots in Spanish for the Spanish post):

1. **`1.png` — Today screen.** Shows the three groups (Now / Later / Answered) and, most
   important for the story, a habit already marked as missed (the red ✕ on "Meditate") next to
   one marked done — the honest-streak differentiator, visible at a glance.
2. **`2.png` — Notification with Yes / No / Snooze.** The screenshot that proves "answer without
   opening the app": a real Android notification shade with the three reply actions.
3. **`4.png` — Edit habit screen, colours expanded.** Shows the full 21-colour palette plus the
   custom picker swatch, and the six-option frequency dropdown open (Daily, Multiple times a day,
   Times per week, Specific days of the week, Monthly, Every N days) — covers both the colour and
   the flexible-schedule differentiators in one shot.

Use 1 and 2 as the minimum pair (story + core differentiator); add 4 when the subreddit's culture
rewards more detail (e.g. r/androidapps, r/fossdroid).

Before publishing in each subreddit:

- Read its rules and wiki yourself, close to posting time — subreddit rules change, and this
  document's verification (below) has a timestamp.
- Say you're the developer in the first line. Reddit punishes a hidden promo post far more than
  a disclosed one.
- Don't post the same text in many subreddits the same day — the spam filter notices, and anyone
  who follows several of them sees it repeated. Space them out and adapt the first paragraph to
  each community.
- Stay to answer comments for the first few hours; it's what drives the most visibility.
- Don't cross-post identical text into r/es and r/programacion and r/esApps back to back; treat
  each as a separate, spaced post like the English ones.

---

## English

### Title

```text
I couldn't find a habit tracker that let me answer from the notification and still admit when I missed a day, so I built one
```

Alternative titles:

```text
None of the habit apps I tried let me answer a reminder without opening the app, so I built one — free, open source, no account
```

```text
Built my own habit tracker because I wanted missed days to stay missed, not get hidden by a streak freeze — open source, no ads, no cloud
```

### Body

```markdown
Hi everyone! I'm the developer, so full disclosure up front: this is my own app.

**The problem.** I've gone through a fair number of habit trackers on the Play Store, and none of them worked the way I actually needed:

- **Answering a reminder means opening the app.** Every tracker I tried pops a notification and then makes you unlock the phone, open the app, and tap through a screen just to say "yes, I did it." I wanted to answer from the notification itself, without even unlocking the phone.
- **Missed days get softened or hidden.** Streak freezes, grace periods, "don't worry, we won't count that one" — I understand why apps do it, but it means the streak stops meaning anything. I wanted a day I skip to just show up as missed, not get explained away.
- **Rigid schedules.** Most trackers only offer "every day" or "N times a week." I have habits that repeat every 3 days, one that's a single day a month, others with several check-ins the same day.
- **An account, a cloud, ads, or all three**, for something that's really just a local list of things I tick off.

So I built **Constanza**.

**What it does**

- **Answer reminders straight from the notification** — Yes, No, or Snooze — without opening the app or unlocking the phone.
- **Missed days aren't hidden.** Anything left unanswered becomes "Missed" at midnight. Streak, best streak, and 30-day compliance are built from that honest record, not a softened one.
- **Six schedule types**: daily, several times a day, a number of times a week, specific days of the week, a day of the month, or every N days — each with one or more reminder times.
- **21 colours, or your own** from a hue/saturation/brightness picker, one colour per habit.
- **Today screen** groups what's due now, what's due later, and what you've already answered, and you can step back to any past day to see what happened.
- **Export and import through the system's own file picker** — it's your data, not ours.
- **Dark UI only**, on purpose: the background stays neutral so the only colour on screen is the one you gave each habit.
- English and Spanish.

**Privacy.** Constanza doesn't even declare the internet permission, so it couldn't send anything anywhere if it wanted to. No account, no cloud sync, no ads, no analytics. It's free. Privacy policy, for what it's worth: https://jorgejiro.es/apps/constanza/privacidad/

**It's open source.** Full source code on GitHub:
https://github.com/jorgejiro/constanza-android

**Get it on Google Play:**
https://play.google.com/store/apps/details?id=com.jjrapps.constanza

**Missing something?** If you like the idea but there's a feature you'd need that it doesn't have, email me at **jjrmobileapps@gmail.com** — I'll gladly review every improvement request. Bug reports are just as welcome, here or as a GitHub issue.

Thanks for reading.
```

---

## Español

### Título

```text
No encontraba una app de hábitos que respondiera desde la notificación y a la vez no ocultara los días que fallaba, así que hice la mía
```

### Cuerpo

```markdown
¡Hola a todos! Soy el desarrollador, así que lo digo desde el principio: la app es mía.

**El problema.** He probado bastantes apps de hábitos en Google Play y ninguna funcionaba como yo necesitaba:

- **Para responder un recordatorio hay que abrir la app.** Todas las que probé lanzan una notificación y luego te obligan a desbloquear el teléfono, abrir la app y tocar una pantalla solo para decir «sí, lo hice». Yo quería responder desde la propia notificación, sin ni siquiera desbloquear el teléfono.
- **Los días que fallas se suavizan o se ocultan.** Congelar la racha, días de gracia, «tranquilo, ese no cuenta»... entiendo por qué las apps lo hacen, pero así la racha deja de significar algo. Yo quería que un día que dejo pasar apareciera como no hecho, sin más explicación.
- **Frecuencias rígidas.** La mayoría solo ofrece «todos los días» o «N veces por semana». Tengo hábitos que se repiten cada 3 días, uno que es un único día al mes, y otros con varias veces el mismo día.
- **Cuenta, nube, anuncios, o las tres cosas**, para algo que en el fondo es una lista local de cosas que marcas.

Así que hice **Constanza**.

**Qué hace**

- **Responde a los recordatorios directamente desde la notificación**: Sí, No o Aplazar, sin abrir la app ni desbloquear el teléfono.
- **No oculta los días que fallas.** Lo que queda sin contestar se registra como «No hecho» a medianoche. La racha, la mejor racha y el cumplimiento de los últimos 30 días se calculan sobre ese registro honesto, no sobre uno suavizado.
- **Seis tipos de frecuencia**: todos los días, varias veces al día, un número de veces por semana, días concretos de la semana, un día del mes, o cada N días — cada una con una o varias horas de recordatorio.
- **21 colores, o el tuyo propio** con un selector de tono, saturación y brillo, uno por hábito.
- **La pantalla Hoy** agrupa lo que toca ahora, lo que toca más tarde y lo que ya has contestado, y puedes navegar a cualquier día pasado para ver qué ocurrió.
- **Exporta e importa con el propio selector de archivos del sistema** — es tu copia, no la nuestra.
- **Interfaz oscura siempre**, a propósito: el fondo se mantiene neutro para que el único color en pantalla sea el que le has puesto a cada hábito.
- Español e inglés.

**Privacidad.** Constanza ni siquiera declara el permiso de internet, así que no podría enviar nada a ningún sitio aunque quisiera. Sin cuenta, sin sincronización en la nube, sin anuncios, sin analítica. Es gratis. Política de privacidad, por si te interesa: https://jorgejiro.es/apps/constanza/privacidad/

**Es de código abierto.** Todo el código está en GitHub:
https://github.com/jorgejiro/constanza-android

**Descárgala en Google Play:**
https://play.google.com/store/apps/details?id=com.jjrapps.constanza

**¿Echas algo en falta?** Si te gusta la idea pero necesitas alguna funcionalidad que no tiene, escríbeme a **jjrmobileapps@gmail.com** y revisaré con gusto tu petición de mejora. Los fallos también son bienvenidos, aquí o como issue en GitHub.

Gracias por leer.
```

---

## Subreddits

Checked 2026-09-28. Verification attempt: `reddit.com` (both `www.` and `old.`) is unreachable
from this environment's fetch tool, and the public Redlib/Libreddit mirrors tried
(`redlib.catsarch.com`, `redlib.privacyredirect.com`, `redlib.tiekoetter.com`, `l.opnxng.com`)
either rate-limited, bot-blocked (403/Anubis), or hung. What's marked **verified** below comes
from sources that *were* reachable — either the subreddit's own linked resource off-Reddit, or
two-or-more independent third-party subreddit-rule trackers agreeing on the same specific claim.
Everything else is **unverified**: read as "plausible, not confirmed" and re-check the actual
sidebar/wiki before posting.

| # | Subreddit | Fit | Self-promo allowed? | Flair / format | Verification |
|---|---|---|---|---|---|
| 1 | r/androidapps | High — dedicated Android app discovery sub | Likely yes, with format rules (more permissive than most app subs, per one tracker) | Unverified — check sidebar for required post format | Unverified. [leadsrover.io](https://leadsrover.io/subreddits/r/apps) (general claim, no quoted rule text) |
| 2 | r/fossdroid | High — FOSS Android apps specifically; matches the open-source angle | Yes, for your own new open-source app | **"Application Release"** flair, used "for new apps only, when you created the app." Post the open-source licence (GPL/MIT/Apache…) at the top or bottom. Non-FOSS/EULA apps need mod approval first. | **Verified** — fetched directly from [fossdroid.org/reddit/posting-guidelines.html](https://fossdroid.org/reddit/posting-guidelines.html) |
| 3 | r/SideProject | High — self-promotion is explicitly the point of the sub | Yes | No fixed flair found; post needs real context (what you built, why, what you're looking for), not a bare link | **Verified** (via secondary trackers, Reddit itself unreachable) — [redditmaster.com](https://www.redditmaster.com/subreddit-rules/sideproject), [oneup.today](https://oneup.today/tools/reddit-self-promotion-checker/sideproject), [mediafa.st](https://www.mediafa.st/subreddit/sideproject) agree: self-promo welcome, low-effort/no-context posts get removed |
| 4 | r/habits | High — exact topical match (habit tracking) | Unverified — small/niche sub, no rule text found | Unverified | Unverified — no source found either way |
| 5 | r/opensource | Medium-high — fits the open-source angle | Likely yes, "limited" | Unverified — check for a self-promo/showcase flair | Unverified — one tracker ("limited self-promotion allowed") with no quoted rule |
| 6 | r/degoogle | Medium — fits "no account, no cloud, no tracking" angle | Unverified | Unverified | Unverified — no source found either way |
| 7 | r/privacy | Medium — fits the privacy-first angle, but historically a stricter, higher-scrutiny sub for self-promo | Unverified, treat as cautious | Unverified — expect closer mod scrutiny of the privacy claims specifically | Unverified — no source found either way |
| 8 | r/getdisciplined | Medium — topically adjacent (habit/discipline systems) | Unverified | Unverified | Unverified — no source found either way |
| 9 | r/AndroidDev | Medium — developer audience, but this is a professional/dev-focused sub, not a user-facing showcase one | Only via a designated thread, if one exists — never confirmed a recurring "Show and Tell" thread | Unverified | Unverified — search turned up nothing specific; check the sidebar/wiki for a weekly thread before posting to the main feed |
| 10 | r/productivity | Medium topically, but **not recommended** | **No — self-promotion prohibited in any form**, including when "asked for recommendations" | N/A | **Verified** (via two independent trackers) — [redditgrowthdb.com](https://www.redditgrowthdb.com/database/subreddits/productivity), [oneup.today](https://oneup.today/tools/reddit-self-promotion-checker/productivity) |
| 11 | r/Android | Low fit for a direct self-promo post — huge default sub, general reputation is that dev self-promo posts get removed outside a designated thread | Unverified specifics, but general signal is unfavourable | Unverified | Unverified — no quoted rule text found; treat as skip unless you find and follow a specific weekly/dev thread |
| 12 | r/esApps | High (Spanish) — Spanish-language equivalent of r/androidapps | Unverified | Unverified | Unverified — no source found either way |
| 13 | r/programacion | Medium (Spanish) — fits the open-source/dev angle for Spanish speakers | Unverified | Unverified | Unverified — no source found either way |
| 14 | r/es | Low-medium (Spanish) — general-audience Spanish sub, self-promotion typically restricted on general subs | Unverified, treat as cautious | Unverified | Unverified — no source found either way |

### Posting cadence

- Start with the two verified-friendly, high-fit ones: **r/fossdroid** and **r/SideProject**.
  Space them at least a day apart.
- Try **r/androidapps** and **r/habits** next, each on its own day; re-read their live rules
  immediately before posting since neither is verified here.
- Skip **r/productivity** entirely (confirmed no self-promotion) and skip **r/Android**'s main
  feed unless you find a live, current designated thread for it.
- For the Spanish post, don't fire r/esApps, r/programacion, and r/es the same day — same
  spam-filter and "seen it three times" risk as the English batch. Lead with r/esApps.
- Reply to every comment for the first few hours after each post; that's what carries it further
  than the algorithm alone.
