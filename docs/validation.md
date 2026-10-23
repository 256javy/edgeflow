# Validación histórica de la v0.1 — 23 de octubre de 2026

Para la iteración con Gemma y las pruebas físicas, consulta [v0.2](validation-v0.2.md).

## Entorno y alcance

SDK 36, JDK 17, NDK 30.0.15729638, CMake 4.1.2. APK debug con arm64-v8a y x86_64. Emulador API 34 x86_64, perfil Pixel 6, 3 GB RAM, GPU SwiftShader. Inferencia siempre CPU, máximo cuatro hilos. El teléfono físico no estuvo conectado: el propietario lo probará después.

## Evidencia ejecutada

| Comprobación | Resultado | Alcance |
| --- | --- | --- |
| assembleDebug y assembleDebugAndroidTest | PASS | APK real y APK de tests generados |
| testDebugUnitTest | PASS | 10 pruebas JVM, sin omisiones |
| lintDebug | PASS | Sin errores; quedan avisos de versiones más nuevas y estilo/API deprecada |
| Firma debug y zipalign -P 16 | PASS | Verificación con apksigner y alineación de APK; no prueba ejecución en kernel físico de 16 KB |
| Tiny, audio español sintético, modo avión | PASS técnico | Inferencia JNI real, sin skip, frío/caliente y salida repetible |
| Base, mismo audio y modo avión | PASS técnico | Inferencia JNI real, sin skip, frío/caliente y salida repetible |
| Burbuja y editor externo sintético | PASS | Apertura de actividad, inserción conservando ambos lados del cursor, rechazo al cambiar de destino y ocultación en contraseña |
| Teclado del emulador | Sin cambios | Gboard `com.google.android.inputmethod.latin/.LatinIME` permanece seleccionado |
| DownloadManager real | PASS | Tiny descargado desde HF sin token, SHA-256 e instalación privada, original público conservado |
| Desinstalar/reinstalar/recuperar original | PASS | El original de 77.691.713 bytes sobrevivió; se seleccionó por SAF y volvió a instalarse con hash verificado sin descargar |
| AudioRecord visible y salida por Inicio | PASS de ciclo de vida | UI mostró Grabando, duración y Detener; al pausar regresó a Cancelado/Grabar. No es prueba de calidad del micrófono físico |
| UI | Revisión visual | Pantalla principal inspeccionada en emulador; captura en images/home.png |

No se ha validado el flujo completo micrófono físico → español humano → inserción en WhatsApp/Telegram. Las pruebas anteriores comprueban componentes y un editor de laboratorio, no sustituyen esa aceptación.

## Inferencia medida: muestra sintética

Generador: espeak-ng 1.51 (`libespeak-ng1 1.51+dfsg-12build1`) y ffmpeg. PCM16 mono 16 kHz, 83.308 muestras, 5,20675 s. SHA-256 WAV: `105714d6fa8cc746996e25b0e4f91a0a2f23252dc068e3c09606b4f042ba64ab`. El WAV no se versiona; scripts/make-smoke-audio.py reproduce el procedimiento, aunque otra versión de sintetizador puede cambiar la forma de onda.

Referencia sintética: «Hola. Esta es una prueba de dictado local. Vamos a revisar el texto antes de enviar.»

| Modelo | Frío, carga incluida | Caliente | RTF frío/caliente | WER | CER |
| --- | ---: | ---: | --- | ---: | ---: |
| Tiny | 3453 ms | 2786 ms | 0,66 / 0,54 | 43,75 % | 28,40 % |
| Base | 7186 ms | 5970 ms | 1,38 / 1,15 | 25,00 % | 17,28 % |

Salidas observadas:

- Tiny: «Esta es una prueba mecata local, vamos a remitar el texto a este señor.»
- Base: «Esta es una prueba nemícta local, vamos a verisare el texto antes de enviar.»

Estos errores son materiales. La muestra confirma ejecución técnica offline, **no calidad aceptada** de dictado. La calidad no forma parte del criterio de éxito del smoke (palabras reconocibles y repetibilidad); WER/CER se informan aparte con la normalización de scripts/error-rate.py. Otras ejecuciones dieron tiempos distintos por contención/caché del host. No extrapolar estas cifras al Snapdragon.

## Límites pendientes

- Xiaomi 12 Pro: Android/HyperOS reales, RAM libre, drivers, compatibilidad, rendimiento, consumo y térmicas.
- Grabaciones humanas paraguayas, inglés técnico, mezcla, silencio/ruido y duraciones 5/15/30/60 s. Sesiones de 180 s exceden el límite v0.
- Apps reales, WebView, cambio de conversación dentro del mismo widget, editores con filtros, límites de longitud y campos sensibles no marcados por Android.
- Revocación de permisos durante sesión, llamadas, Bluetooth, bajo almacenamiento, muerte de proceso durante descarga y ahorro de batería HyperOS.
- Android 15/16 físicos y otras implementaciones OEM. Compilar con SDK 36 no equivale a probar todas las versiones.
- LiteRT/Qwen, GPU/NPU ASR, diccionario, IME, posprocesamiento y ajuste automático de posición al teclado.
- CI remota y publicación no equivalen a validación física ni aprobación Google Play.

## Correcciones verificadas durante validación

La captura cancela en onPause, evitando depender de que termine la transición de salida para llegar a onStop. La preparación de inserción comprueba de forma síncrona que la actividad propia sigue al frente; así no depende exclusivamente de la llegada asíncrona de eventos de accesibilidad. La escritura final vuelve a validar destino/foco/cursor y nunca reintenta en caso ambiguo.

La cancelación del decoder es cooperativa. Durante la carga de pesos puede tardar en liberarse el trabajo; el resultado cancelado se descarta y nunca se inserta. Solo existe un contexto nativo en RAM; cambiar variante libera el anterior.
