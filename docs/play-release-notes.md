# Novedades para Google Play — Constanza

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

## 0.1.16 (versionCode 17) — pendiente de publicar en Play (primera publicación)

### es-ES (363 caracteres)

```text
Primera versión de Constanza en Google Play.

• Hábitos con seis tipos de frecuencia y recordatorios con respuesta Sí/No/Aplazar desde la notificación.
• Racha actual, mejor racha y cumplimiento de los últimos 30 días por hábito.
• Aviso de repaso nocturno de lo pendiente, y copia de tus datos a un archivo.

Sin cuentas, sin nube, sin anuncios, sin seguimiento.
```

### en-US (343 caracteres)

```text
The first version of Constanza on Google Play.

• Habits with six schedule types and reminders answered Yes/No/Snooze from the notification.
• Current streak, best streak, and last-30-days compliance per habit.
• A nightly review notification for what is still open, and a file backup of your data.

No accounts, no cloud, no ads, no tracking.
```

### Formato con etiquetas de idioma

```xml
<es-ES>
Primera versión de Constanza en Google Play.

• Hábitos con seis tipos de frecuencia y recordatorios con respuesta Sí/No/Aplazar desde la notificación.
• Racha actual, mejor racha y cumplimiento de los últimos 30 días por hábito.
• Aviso de repaso nocturno de lo pendiente, y copia de tus datos a un archivo.

Sin cuentas, sin nube, sin anuncios, sin seguimiento.
</es-ES>
<en-US>
The first version of Constanza on Google Play.

• Habits with six schedule types and reminders answered Yes/No/Snooze from the notification.
• Current streak, best streak, and last-30-days compliance per habit.
• A nightly review notification for what is still open, and a file backup of your data.

No accounts, no cloud, no ads, no tracking.
</en-US>
```
