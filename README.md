# EdgeFlow Android

Dictado local para Android 12 o posterior: voz → Whisper en el teléfono → texto en el campo original o copia manual. Mantiene el teclado actual.

**Estado: prueba de concepto, sin validación todavía en Xiaomi 12 Pro.** No es un producto listo para Google Play. La UI y el motor son reales; no hay transcripciones simuladas.

<img src="docs/images/home.png" alt="Pantalla de dictado de EdgeFlow en un emulador Android" width="300" />

## Probar

1. Instala el APK de depuración construido desde este repositorio.
2. En **Modelos**, descarga Tiny o Base multilingüe desde Hugging Face, sin cuenta ni token. Solo Wi-Fi; descarga e importación son voluntarias.
3. La app verifica tamaño exacto y SHA-256 e instala una copia privada. Conserva el original en `Descargas/EdgeFlow` para recuperarlo mediante **Importar** tras reinstalar.
4. En **Dictar**, pulsa **Grabar**, acepta micrófono, habla y pulsa **Detener y transcribir**. También puedes importar un WAV PCM16 mono a 16 kHz de hasta 60 s.
5. Para otras apps, lee y acepta la divulgación en **Ajustes** y activa EdgeFlow en Accesibilidad. Enfoca un campo: aparece una burbuja arrastrable. Ábrela, graba y usa **Volver e insertar**. Si el destino no puede comprobarse, copia y pega manualmente.
6. Activa modo avión después de instalar los modelos y repite el dictado.

No se pulsa Enviar. Una selección de texto no vacía utiliza respaldo manual para evitar borrarla. No hay acceso a contraseñas, historial, nube ni telemetría. La grabación ocurre en pantalla visible y se cancela al salir; la inferencia empieza después de detenerla.

## Compilar

Requisitos: JDK 17, SDK 36, NDK `30.0.15729638`, CMake `4.1.2`. Android Studio puede instalar estos componentes. Se generan bibliotecas `arm64-v8a` para el Xiaomi y `x86_64` para emulador, con soporte de páginas de 16 KB mediante NDK moderno.

```bash
git clone --recurse-submodules https://github.com/256javy/edgeflow.git
cd edgeflow
# Si ya lo clonaste sin submódulos:
git submodule update --init --recursive
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

El wrapper fija Gradle 9.4.1 con checksum. AGP 9.2.1 integra Kotlin; Compose compiler 2.3.10. Las versiones están declaradas en Gradle. El submódulo fija Whisper.cpp v1.8.7 por SHA.

## Qué incluye la v0

- Compose Material 3, modelos Tiny/Base multilingües, idioma español/inglés/automático.
- AudioRecord PCM16 mono 16 kHz, límite de 60 s, nivel y duración; inferencia CPU fuera del hilo principal, cancelación cooperativa y latencia local.
- DownloadManager con progreso, cancelación y recuperación de descarga tras muerte del proceso; verificación SHA-256 y SAF para recuperar archivos.
- Burbuja de accesibilidad con tamaño, opacidad, posición persistente y exclusión por paquete. No requiere cambiar Gboard ni permiso SYSTEM_ALERT_WINDOW.
- Inserción conservadora validando nodo, foco y cursor; resultado conservado solo en RAM para respaldo.
- Tests de inserción, contraseñas, WAV corrupto y prueba nativa Android con audio sintético.

## Límites y siguientes pasos

La burbuja abre una actividad visible y pide pulsar Grabar: aún no es el flujo de grabación desde overlay del producto final. La posición inicial es fija y arrastrable, sin ajuste automático al teclado. La captura no continúa con pantalla apagada ni otra aplicación delante. No hay IME alternativo, diccionario, posprocesamiento, LiteRT o LiteRT-LM integrados. Son trabajo posterior, no opciones ficticias del catálogo.

Solo Tiny/Base Whisper.cpp se ofrecen. No se afirma incompatibilidad del Xiaomi con Qwen/LiteRT: falta integrar y medir esa ruta. Gemma con LiteRT-LM/GPU funcionando en otro proyecto del propietario es evidencia útil para ese runtime/modelo, no una medición de ASR.

La calidad y latencia de español paraguayo, apps reales, Bluetooth, consumo y comportamiento HyperOS están pendientes. Los resultados de un emulador no describen el rendimiento del teléfono. Consulta [investigación](docs/research.md), [pruebas](docs/testing.md), [arquitectura](docs/architecture.md) y [validación](docs/validation.md).

## Open source

Código propio bajo [MIT](LICENSE). Revisa [contribución](CONTRIBUTING.md), [seguridad](SECURITY.md) y [atribuciones](THIRD_PARTY_NOTICES.md). Los pesos se descargan por separado y conservan su propia licencia.
