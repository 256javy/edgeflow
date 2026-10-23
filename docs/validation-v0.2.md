# Validación v0.2 — 23 de octubre de 2026

La v0.1 no superó la aceptación del propietario: Tiny/Base producían texto poco inteligible. Esta iteración añade un reconocedor distinto con audio directo, revisión local y diccionario. No se considera aceptada con voz real hasta que el propietario vuelva a probarla.

## Dispositivo físico

Xiaomi 12 Pro, modelo 2201122G, SoC SM8450 (Snapdragon 8 Gen 1), Android 15 / SDK 35, propiedad de versión HyperOS OS3.0, páginas de 4096 bytes. Se ejecutó LiteRT-LM Android 0.13.1 con Gemma 4 E4B y configuración GPU para decoder, CPU para audio. La inicialización GPU completó sin pasar por el respaldo CPU.

Pesos E4B reutilizados del archivo público ya presente en otra app del propietario: SHA-256 `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`. No se copió ningún otro dato de esa app. Se conservó el origen y se creó un original adicional en Descargas/EdgeFlow junto con la copia privada de inferencia. No se descargaron nuevamente 3,66 GB.

Las pruebas importaron exclusivamente WAV sintéticos. No se capturó micrófono, cambió modo avión, modificó accesibilidad ni recogieron grabaciones personales en el teléfono. La instalación del paquete de pruebas requirió el modo ADB `--no-streaming`; el modo streaming fue rechazado por Xiaomi mientras el teléfono estaba bloqueado.

## Resultados ejecutados

| Prueba | Resultado | Límite de la evidencia |
| --- | --- | --- |
| Compilación debug y APK de instrumentación | PASS | arm64-v8a y x86_64 |
| JVM | PASS | 20 pruebas de inserción, WAV, diccionario, solapamiento y guardas |
| Lint | PASS | Sin errores; avisos de versiones/API deprecada permanecen |
| Gemma E4B en Xiaomi | PASS técnico | Audio y revisión reales, GPU inicializada; sin skip |
| Flujo de sesión en Xiaomi | PASS | ASR → propuesta, original seleccionado por defecto, selección explícita y recuperación del original |
| Cancelar revisión en Xiaomi | PASS | Conserva original y descarta propuesta tardía; no inserta ni copia |
| Whisper Base en Xiaomi | PASS de regresión | Nueva firma JNI y ejecución CPU con fixture; no aceptación de calidad |
| Inserción y contraseña, emulador API 34 | PASS | Se preserva texto y cursor, rechaza nuevo destino y oculta burbuja en contraseña; no se ejecutó este test que altera ajustes en el teléfono |

## Audio español sintético

Mismo fixture espeak-ng 1.51 de v0.1: 83.308 muestras PCM16 mono 16 kHz, 5,20675 s. SHA-256 `105714d6fa8cc746996e25b0e4f91a0a2f23252dc068e3c09606b4f042ba64ab`.

Referencia: «Hola. Esta es una prueba de dictado local. Vamos a revisar el texto antes de enviar.»

Gemma: «Hola. Esta es una prueba médica local. Vamos a revisar el texto antes de enviar.»

- Primera transcripción, carga incluida: **31.443 ms** en la segunda ejecución (34.178 ms en la primera).
- Transcripción con el mismo modelo cargado: **5.588 ms**.
- WER normalizado sobre este único fixture: **12,5 %**. Sigue confundiendo «de dictado» con «médica».
- La misma entrada de v0.1 daba 25 % con Base y 43,75 % con Tiny en emulador. Es comparación de salida del mismo audio; no es una comparación de rendimiento entre dispositivos ni validación de voz humana.

La primera carga de E4B cuesta decenas de segundos. El modelo permanece en memoria para siguientes dictados; si Android mata el proceso, habrá otra carga. El tiempo no depende solo de GPU: el audio se codifica en CPU y la generación escala con la longitud.

## PoC, siglas y dictado largo

Fixture técnico: 115.783 muestras, 7,2364375 s. SHA-256 `f0689ca5f9e2e61639a28b4c481265c8ce418c71d138b94cf6a8b0d843e466ed`. Vocabulario de referencia de laboratorio: PoC, PR, GitHub, Android. El sintetizador pronunció «poc» y «pe erre».

Salida en **6.395 ms**: «Vamos a desarrollar una PoC funcional para Android. Después vamos a revisar el PR en GitHub. No ejecutar el deploy.»

Acertó PoC y PR, pero omitió «hoy» y cambió «de GitHub» por «en GitHub». No es transcripción perfecta ni prueba de la pronunciación del propietario. Se añadió solo **PoC** a su diccionario; no una regla global «beso → PoC», que cambiaría una palabra legítima.

Fixture largo: el audio original repetido siete veces, 583.156 muestras / **36,44725 s**, procesado en dos fragmentos con solapamiento. Tardó **24.719 ms** y devolvió las siete frases completas sin duplicación adicional en el corte. Esto supera el límite de una sola entrada Gemma, pero **no valida todavía 60 ni 180 s**.

## Revisión de texto técnico

Entrada sintética: «vamos a revisar el PR de GitHub y actualizar PostgreSQL 17 no ejecutar el deploy hoy».

Propuesta: «Vamos a revisar el PR de GitHub y actualizar PostgreSQL 17; no ejecutar el deploy hoy.»

Tiempo: **5.775 ms**. Conservó cifra, negación y términos. Una prueba adicional ejecutó el pipeline real en SessionViewModel, confirmó que copiar/insertar conserva la elección original/propuesta y canceló una segunda revisión conservando el ASR original. La prueba de selección no escribió en otra app del teléfono.

Las guardas de cifras/negaciones/extensión no verifican significado ni garantizan que un LLM no alucine. La propuesta requiere selección explícita. Un LLM no puede reconstruir con certeza lo que el ASR oyó mal.

## Pendiente

- Aceptación con voz real del propietario: PoC y su vocabulario, español paraguayo, ruido y velocidad habitual.
- Micrófono → ASR Gemma → inserción en WhatsApp/Telegram/navegador, con Gboard y restricciones HyperOS reales.
- Grabaciones de 60/180 s, cortes de palabra, silencio, Bluetooth, llamadas y presión térmica/batería.
- Gemma E2B en el teléfono y respaldo CPU de Gemma. Se ofrecen como configuraciones, sin afirmar que fueron medidos.
- Revisión de corpus amplio y métricas con referencias humanas. No se ha entrenado ni adaptado ningún modelo con voz del propietario.

Comandos, preparación y política de datos: docs/testing.md. Resultados estructurados: benchmarks/xiaomi-gemma-2026-10-23.json. La CI remota compila y ejecuta JVM/Lint; no sustituye los tests físicos anteriores.
