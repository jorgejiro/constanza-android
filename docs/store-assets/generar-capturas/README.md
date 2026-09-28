# Generador de capturas para Google Play — Constanza

Genera las capturas de la ficha de Play para Constanza: 7 escenas × 2 idiomas (es, en) × 3 formatos
(phone, tablet7, tablet10) = 42 imágenes, en el orden 1 Hoy · 2 Notificación · 3 Editor de hábito ·
4 Selector de color · 5 Lista de hábitos · 6 Ajustes · 7 Progreso (Progreso va último a propósito:
es UI real y honesta —tres líneas de texto—, no una escena vistosa, así que no compite por los dos
huecos que Play enseña sin desplazarse), más el juego de teléfono copiado a
`fastlane/metadata/android/{es-ES,en-US}/images/phoneScreenshots/`. Puerto del pipeline que
`sleep-noise-android` ya resolvió (`docs/decisions/003-capturas-de-la-ficha-automatizadas.md` en
ese repo), adaptado a que Constanza tiene una base de datos Room real: aquí no basta con abrir la
app, hace falta *sembrar* datos de demostración primero.

## Requisitos

- Un emulador arrancado (AVD `Medium_Phone`, API 37 — cualquiera de `Medium_Phone`, `Tablet7`,
  `Tablet10` o `Pixel_6` sirve igual, todos son API 37; se usa uno solo y se cambia `wm size`/
  `wm density` para los tres formatos, nunca tres emuladores). Nunca un teléfono físico.
- `build **debug**`, no release: la siembra de datos corre por instrumentación
  (`am instrument`/`run-as`), y un APK de release no se puede instrumentar.
- Python 3 con `Pillow` y `numpy` (`pip install pillow numpy` si `revisar.py` los echa en falta).

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:installDebug :app:installDebugAndroidTest
```

## Ejecutar todo

```bash
cd docs/store-assets/generar-capturas
./todos.sh emulator-5554
```

Esto: concede permisos, entra en modo demo, siembra y captura las 3 formatos en español, repite en
inglés, copia el juego de teléfono a fastlane, y termina corriendo `revisar.py`. Una pasada completa
tarda del orden de 30-45 minutos — la mayor parte es la espera real de la notificación de
recordatorio (ver más abajo), multiplicada por 2 idiomas × 3 formatos.

Para un solo idioma: `python3 tanda.py emulator-5554 es`. Para solo verificar lo ya generado:
`python3 revisar.py`.

## Piezas

- **`ui.py`** — helpers de bajo nivel sobre `adb`/`uiautomator`: todo elemento se localiza **por
  texto** (o `content-description`), nunca por coordenada fija; toda espera es "esperar a que
  aparezca el contenido", nunca un `sleep` fijo (salvo el `_settle()` corto de `capturar.py`, que
  solo cubre el último frame de una animación de Compose después de que la espera por texto ya
  tuvo éxito).
- **`capturar.py`** — la navegación de cada una de las 7 escenas (mismo orden que
  `docs/play-store-publication-texts.md`), más los textos es/en que cada escena busca.
- **`tanda.py`** — orquesta un idioma completo: por cada formato, cambia `wm size`/`wm density`,
  siembra de nuevo (`ListingScreenshotSeed`), lanza la app, captura las 7 escenas y copia el
  formato `phone` a fastlane.
- **`revisar.py`** — verifica el resultado completo; sale con código 1 si algo falla.
- **`todos.sh`** — los dos idiomas y `revisar.py`, en ese orden.

## La semilla de datos: `ListingScreenshotSeed`

`app/src/androidTest/kotlin/com/jjrapps/constanza/seed/ListingScreenshotSeed.kt`. Sigue el patrón
ya establecido por `ImminentReminderSeed`: escribe en la base de datos **real** del dispositivo, a
través de los DAO y mappers de producción — nunca una base de datos en memoria ni datos inventados
a mano —, y está anotada `@SeedOnly` para que `:app:connectedDebugAndroidTest` la excluya siempre
(esa tarea desinstala los dos APK al terminar, lo que destruiría los datos sembrados).

Siembra 5 hábitos, elegidos para que las TRES secciones de Hoy (`groupTodayRows` — Ahora/Más
tarde/Contestados) salgan siempre pobladas, de forma determinista, sin importar la hora real a la
que corra el pipeline:

1. **Beber agua** (varias veces al día) — el ancla de la escena de notificación. Sus tres horas
   fijas históricas se resuelven también como contestadas HOY, a través del mismo
   `EntryWriter.answerInApp` que usa la propia pantalla Hoy, y solo el cuarto hueco —armado 2
   minutos vista— se deja pendiente. Su hora siempre queda en el futuro en el momento de capturar
   Hoy, así que esta fila cae siempre en "Más tarde" mostrando "En progreso", nunca en "Ahora": la
   notificación real (Sí/No/Aplazar) llega un par de minutos después de esa captura.
2. **Leer 20 minutos** (diaria) — resuelto Sí (COMPLETED) para hoy por el mismo camino de
   producción, así que cae en "Contestados" con el visto verde. También la racha más cuidada,
   reservada para la captura de Progreso, y el hábito EXISTENTE cuyo editor abren las escenas 3 y 4
   (ver más abajo).
3. **Caminar** (N veces por semana) — deliberadamente SIN hora de recordatorio: la propia regla de
   `TodayModel` ("un hueco sin hora y sin contestar siempre es 'Ahora'") es lo que coloca esta fila,
   nunca el reloj. También lleva una semana por debajo de cuota en su historial, para que se vea una
   racha que se rompió una vez.
4. **Meditar** (días concretos de la semana) — resuelto No (MISSED) para hoy, la pareja del "Sí" de
   Leer, para que "Contestados" enseñe los dos glifos de respuesta, no solo visto buenos.
   Configurado alrededor de HOY, para que siempre esté pendiente sin importar qué día real se
   ejecute el pipeline.
5. **Estirar** (cada N días) — anclado en HOY por la misma razón, sin contestar, con su
   recordatorio en el futuro: cae siempre en "Más tarde" junto a Beber agua.

Se ejecuta una vez por idioma (instrumentación con `-e language es` o `-e language en`), y es
idempotente: cada ejecución borra cualquier hábito sembrado antes (cascada sobre programaciones,
horas y respuestas) y cancela sus alarmas reales antes de insertar el juego nuevo.

**Por qué el reloj del emulador no se congela.** Este AVD (`google_apis_playstore`, como todos los
que usan la etiqueta Play Store) es un build de producción: `adb root` responde "adbd cannot run as
root in production builds", y sin root `adb shell date -s ...` falla con "Operation not permitted"
— comprobado en vivo, no asumido. La alternativa que sí funciona sin root es la que se usa aquí:
sembrar SIEMPRE en relativo a la hora real del dispositivo (desplazamientos `now + N minutos`,
nunca una hora de reloj fija — ver la lección de más abajo), no un reloj de referencia congelado.

```bash
adb -s emulator-5554 shell pm grant com.jjrapps.constanza android.permission.POST_NOTIFICATIONS
adb -s emulator-5554 shell appops set com.jjrapps.constanza SCHEDULE_EXACT_ALARM allow
adb -s emulator-5554 shell am instrument -w -r \
  -e class com.jjrapps.constanza.seed.ListingScreenshotSeed -e language es \
  com.jjrapps.constanza.test/androidx.test.runner.AndroidJUnitRunner
```

Por qué faltan MISSED explícitos y no huecos: `EntryResolution` resuelve una fecha sin fila como
`UNKNOWN`, que ni rompe ni extiende una racha — ese es el trabajo del barrido de medianoche real
(`OccurrenceResolver`, `source = "SWEEP"`). Un "par de fallos" en el historial solo se ve como tal
si se escribe como filas `MISSED` reales, igual que las dejaría ese barrido.

## Lecciones aprendidas aquí (más allá de las de sleep-noise-android)

- **`am instrument` puede reportar "Process crashed" en una siembra que en realidad terminó bien.**
  El runner mata el proceso instrumentado nada más terminar el test (comportamiento normal,
  visible en logcat como `Force stopping ... due to finished inst`), y en algunas versiones eso se
  refleja como "Process crashed" en la salida humana de `am instrument` aunque
  `finished: <nombreDelTest>` ya haya aparecido. `tanda.py` no confía en ese texto: solo falla si
  ve `FAILURES!!!` explícito.
- **Un `@Test fun x() = runBlocking { ... }` puede compilar bien y aun así fallar en tiempo de
  ejecución con "should be void".** Si la última expresión del bloque no es literalmente `Unit`
  (por ejemplo, la última línea es un `Log.i(...)`, que en Android devuelve `Int`), Kotlin infiere
  ese tipo como el de la función, y JUnit lo rechaza al validar la clase. La firma correcta es
  `fun x(): Unit = runBlocking { ... }`, con el `Unit` explícito.
- **`service call notification 1` no limpia nada en las versiones de Android usadas aquí** (a
  diferencia de lo que "el truco clásico de adb" promete): las notificaciones de "Sin bloqueo de
  pantalla", batería o teclado físico del propio emulador lo sobrevivían intactas. Lo que sí
  funciona es aplazar cada notificación ajena con `cmd notification snooze --for <ms> '<key>'`, una
  por una, dejando solo la de Constanza. La *key* que da `cmd notification list` contiene `|`, así
  que hay que pasarla entre comillas simples: sin comillas, el shell del dispositivo la trocea como
  una tubería y responde "inaccessible or not found".
- **Un elemento de Compose que solo aparece "al final de la lista" no está en el volcado de
  `uiautomator` si la lista lo compone perezosamente fuera de la pantalla.** El botón "Añadir
  hábito" de la propia pantalla Hoy es así cuando ya hay hábitos: la fila vive al final, bajo el
  último hábito, y con 5 hábitos sembrados normalmente no entra en el viewport. Esto dejó de importar
  en la práctica una vez que las escenas 3/4 pasaron a abrir el editor de un hábito EXISTENTE
  tocando su propia fila en la Lista de hábitos (`HabitRow`'s `Modifier.clickable {
  onEditHabit(habit.id) }`) en vez de crear uno nuevo — pero la lección en sí sigue siendo válida
  para cualquier otro elemento fuera de pantalla: localízalo por un punto de entrada que SÍ esté
  siempre en el viewport, no confíes en que scroll_until_text lo alcance.
- **Un `input tap` puede acertar contenido de la persiana de notificaciones en vez del de la app**
  si una notificación con aviso emergente (heads-up) está en pantalla en ese instante — no hace
  falta haber deslizado nada. Limpiar notificaciones ajenas ANTES de cada escena (no solo antes de
  la escena de notificación) evita esta interferencia con el resto del recorrido.
- **Un campo desplegable ya relleno con un valor visible (p. ej. "Frecuencia" mostrando ya
  "Diaria") hace que esperar por ese mismo texto no pruebe que el menú se abrió.** El campo hay que
  tocarlo por su VALOR actual, y la comprobación de que el menú está realmente desplegado debe
  esperar por una opción que solo existe dentro del menú (p. ej. "Mensual"), no por el valor que ya
  estaba visible en el campo cerrado.
- **Mientras un `DropdownMenu` de Compose está abierto, `uiautomator dump` solo devuelve el árbol
  de accesibilidad de ESE popup, no el de la pantalla que hay debajo.** No es que el resto esté
  oculto: no está en absoluto en el volcado, aunque un `screencap` normal sí lo pinte (compone
  todas las ventanas). Buscar por texto cualquier elemento de la pantalla de fondo — incluida su
  propia etiqueta de sección, como "Color" — para cerrar el desplegable siempre agota el tiempo de
  espera. Lo que sí es localizable es la propia lista de opciones del desplegable, así que volver a
  tocar el valor ya seleccionado ("Diaria") lo cierra sin cambiar nada y sin arriesgar la
  navegación — cerrarlo con el botón Atrás del sistema, en cambio, cerró la pantalla entera del
  editor en la primera pasada completa, no solo el menú.
- **Un `wait_for_text` que ya tuvo éxito no prueba que la persiana esté realmente abierta**: el
  texto de una notificación puede seguir "presente" en el árbol de accesibilidad un instante
  después de que la persiana ya se haya vuelto a cerrar sola. La comprobación robusta espera
  además por un elemento que SOLO existe dentro de la persiana desplegada (el botón "Aplazar" de
  la propia notificación) y confirma con `dumpsys window` que la ventana con foco es
  `NotificationShade`, reintentando una vez si no lo es.
- **El modo demo de SystemUI no oculta, por sí solo, el símbolo de "sin internet" del icono de
  wifi** en algunas versiones: hace falta el extra `-e fully true` en el comando `network` para que
  el wifi se muestre como una conexión completa y no con el signo de exclamación.
- **Una hora de recordatorio fija (p. ej. las 7:30) puede disparar una notificación real casi al
  instante de sembrarla, si esa hora ya pasó hoy.** `OccurrencePlanner` arma la alarma para la
  franja de HOY tal cual, sin comprobar si ya quedó atrás, y `AlarmManager` dispara de inmediato un
  disparo ya vencido. La primera pasada completa de este pipeline lo sufrió literalmente: Meditar
  (7:30) y Estirar (9:00) dispararon sus propias notificaciones sin avisar mientras el pipeline
  corría a media tarde, colándose en la escena de la notificación de Beber agua. La única hora de
  recordatorio que puede ser un reloj fijo es la de Beber agua, calculada a propósito para ser
  inminente; el resto de hábitos con recordatorio en vivo usan un desplazamiento sobre la hora
  actual (`ListingScreenshotSeed.laterToday`), nunca una hora fija — ver su propio KDoc.
- **Un hábito con dos filas en `dumpsys notification` (la real y el resumen automático
  `GROUP_SUMMARY`/`AUTOGROUP_SUMMARY` de Android) no son dos notificaciones para quien mira la
  persiana** — Android oculta ese resumen en solitario cuando solo hay una notificación real detrás.
  Contar líneas `pkg=<paquete>` sin filtrar ese resumen hace que una única notificación real parezca
  dos. También importa leer ambos recuentos (propias y totales) de UN solo volcado de `dumpsys`: dos
  llamadas separadas pueden ver estados distintos si el resumen aparece y desaparece entre medias
  (se observó en vivo: `own=2, total=1`, imposible bajo una lectura consistente).
- **La navegación Atrás no siempre vuelve un solo nivel en la pila esperada, y no es solo un caso
  aislado.** Se midió en tres pantallas distintas alcanzadas a través del menú de la Lista de
  hábitos: desde Progreso, una única pulsación Atrás no vuelve a la Lista, sale directamente a Hoy
  (y una segunda pulsación sale del todo al lanzador); desde Ajustes, ni una ni dos pulsaciones
  volvían a Hoy de forma fiable; y el editor de un hábito EXISTENTE se comportó igual de raro tras
  tocar el desplegable de Frecuencia. En vez de adivinar la profundidad de pila de cada pantalla —ya
  falló dos veces seguidas con hipótesis distintas—, la solución robusta y uniforme adoptada en
  todas partes es relanzar la app entera (`force-stop` + arranque en frío, que siempre abre en Hoy
  porque la siembra deja el onboarding hecho) en vez de encadenar pulsaciones Atrás.
- **Las cabeceras de sección de Ajustes se renderizan en VERSALITAS por el propio composable
  (`.uppercase()`), nunca con el `casing` original del recurso de cadena.** Buscar
  "Datos y copia de seguridad" (con mayúscula solo inicial) nunca aparece, así que un scroll que
  busca ese texto se agota siempre, aunque la sección ya esté en pantalla sin necesidad de scroll.
  El texto correcto a buscar es "DATOS Y COPIA DE SEGURIDAD".
- **Un `adb shell input swipe` de magnitud "exacta", calculada para llevar un elemento localizado
  a una posición de píxel concreta, no converge.** `input swipe` siempre termina como un fling: la
  duración pedida solo cambia la velocidad que alimenta la MISMA curva de deceleración que usaría un
  golpe real, así que es la física de scroll de la propia `LazyColumn` de Compose —no una
  traslación 1:1— la que decide dónde acaba aterrizando. Se probó en vivo: tres correcciones
  "exactas" sucesivas oscilaron sin asentarse nunca dentro de un margen de 40 px. Lo que sí converge
  son pasos pequeños de magnitud FIJA (la misma forma de gesto que ya usaba `scroll_down`),
  remedidos tras cada uno y parando en cuanto el elemento buscado cae dentro de una tolerancia
  amplia (150 px) — más intentos, pasos más pequeños, en vez de un gesto grande "perfecto".
- **A veces el contenido sencillamente no cabe en una pantalla de teléfono con todas las cabeceras
  intactas, sin importar a qué posición se haga scroll.** En Ajustes, llevar la cabecera de "Repaso
  del día" al borde superior deja siempre la cola de "Duración de aplazamiento" (unas opciones "2 h
  / 3 h / 4 h" huérfanas, sin cabecera encima) en el marco — medido en vivo: la posición de scroll
  máxima posible ya deja esa cabecera a 1732 px del borde, muy lejos del objetivo, y scrolls
  adicionales no mueven nada más porque no queda más contenido por revelar. La densidad más alta del
  formato `phone` (@420, frente a @288 de las tabletas) es justo lo que hace que cinco secciones NO
  quepan juntas aquí aunque sí quepan en tableta. La solución fue no perseguir una posición de scroll
  intermedia imposible, sino usar la parte de arriba SIN NINGÚN scroll (Duración + Datos y copia +
  Idioma, las tres con su cabecera intacta y nada huérfano) en vez de perseguir Repaso del día.
- **Sembrar la respuesta de HOY con el mismo slotId "0" que usa el historial no basta para que
  Hoy la reconozca como contestada, si ese hábito SÍ tiene una hora de recordatorio configurada.**
  `TodayModel.toTodaySlot` empareja cada entrada por su `slotId` REAL (el id de
  `ReminderSlotEntity`, nunca el sentinel `0`) contra la fila de hoy que construye para ESE hueco
  concreto — el sentinel `0` solo es válido para hábitos sin ninguna hora configurada. Sin pasar el
  id real, la fila se queda "sin contestar" a ojos de la pantalla Hoy aunque la base de datos tenga
  la entrada. La única forma correcta, y la que usa `ListingScreenshotSeed`, es contestar a través
  de `EntryWriter.answerInApp` con el id real del hueco y de su ocurrencia armada — el mismo camino
  que usa `TodayViewModel.answer` en producción, nunca un `INSERT` a mano.
- **Un bucle de "historial" que construye fechas por semana puede escribir sin querer una fila para
  HOY mismo.** El bucle de Caminar generaba sus fechas completadas con `week.with(dayOfWeek)`
  filtrado por `!date.isAfter(today)` — que incluye HOY cuando hoy mismo cae en uno de los días de
  la semana elegidos (lunes, miércoles o viernes). Se detectó en vivo un lunes: Caminar aparecía ya
  "Contestado" en vez de en "Ahora", porque el propio bucle de historial le había escrito una
  entrada COMPLETED para hoy sin que ninguna llamada explícita lo pidiera. El filtro correcto es
  estrictamente `date.isBefore(today)` — nunca `!isAfter`— en cualquier bucle de historial que
  construya fechas hacia adelante en vez de retroceder desde "hoy menos N días".
- **Una diferencia media de píxel de toda la imagen es un mal indicador de "mismo idioma
  capturado dos veces" en una app con fondo mayoritariamente uniforme.** El tema oscuro de
  Constanza (`Dark-Only Rendering`) hace que el texto traducido —la única diferencia real entre
  es/en— ocupe una fracción pequeña de los 2.6+ millones de píxeles de un teléfono, así que la
  media por canal de toda la imagen puede quedar por debajo de 1.0 incluso entre dos capturas
  genuinamente distintas (medido en vivo en cinco pares reales). La señal robusta es la PROPORCIÓN
  de píxeles que difieren de forma apreciable (`revisar.py`'s `PIXEL_DIFF_THRESHOLD`), no la media.

- **`cmd locale set-app-locales` solo cambia el idioma de LA APP; la persiana y la barra de estado
  siguen el idioma del SISTEMA, y ese no cambia en caliente con un simple `settings put`.** La
  escena de notificación mezcla las dos cosas en una sola captura: el texto de Constanza (app) junto
  al "Clear all"/"Borrar todo" de la persiana (sistema). Escribir
  `settings put system system_locales es-ES` y volcar la persiana de inmediato seguía mostrando
  inglés — comprobado en vivo, con y sin *broadcast* `LOCALE_CHANGED`, con y sin gesto nuevo. Lo que
  sí funciona, medido igual de directamente, es reiniciar el dispositivo (`adb reboot`) después de
  escribir el ajuste — la misma técnica que usan las herramientas de test de idioma instrumentadas.
  Cuesta un reinicio completo por PASADA DE IDIOMA (no por formato), y todo el estado que depende de
  *broadcasts* en tiempo de ejecución —modo demo, `wm size`/`wm density`— hay que volver a aplicarlo
  después, porque el reinicio los resetea.

## `revisar.py`

Comprueba, y falla con código 1 en cualquier incumplimiento:

1. Que existan los 42 ficheros esperados.
2. Dimensiones exactas por formato (phone 1080×2400, tablet7 1080×1920, tablet10 1440×2560).
3. Proporción 9:16 en los dos formatos de tableta.
4. Que es y en difieran de verdad en cada escena (un par idéntico es un idioma que se filtró o se
   capturó dos veces en el mismo idioma).
5. Que ninguna imagen esté en blanco o a medio dibujar (umbral de varianza de píxel).
6. Que la escena de notificación no tenga ninguna notificación ajena colada, cruzando con
   `manifest.json` (que `capturar.py` escribe en el momento de la captura, con el recuento real de
   notificaciones activas — un PNG ya terminado no puede probar esto por sí solo).
7. Que no se haya colado ningún texto del idioma equivocado en la propia persiana del sistema en la
   escena de notificación (`system_locale_leak`, también cruzado contra `manifest.json`) — la propia
   `capturar.py` ya corta el lote de inmediato si lo detecta en el momento de capturar, así que esta
   comprobación es sobre todo para un manifiesto de una ejecución previa que quedó a medias.
