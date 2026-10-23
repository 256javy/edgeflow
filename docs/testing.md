# Pruebas reproducibles

## Build y JVM

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Tests JVM: conservación de ambos lados del cursor, selección no destructiva, cursor inválido, UTF-16, texto vacío, contraseñas; WAV válido, frecuencia incorrecta, truncamiento y overflow de bloques.

## Inferencia Android con muestra sintética

Requiere Python 3, `libespeak-ng` con voz española y ffmpeg en el host solo para generar un fixture; la app no depende del host para dictar. El fixture es sintético, no representa español paraguayo ni una medición de calidad humana.

```bash
python3 scripts/make-smoke-audio.py
export ANDROID_SERIAL=emulator-5554 # reemplazar explícitamente si corresponde
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Instala modelos desde la app o, solo en una build debug y con archivos ya verificados, prepara los fixtures privados:

```bash
# Comprueba los SHA-256 de docs/research.md antes de introducir los pesos.
sha256sum /ruta/ggml-tiny.bin /ruta/ggml-base.bin
adb shell run-as dev.edgeflow mkdir -p no_backup/models
adb exec-in run-as dev.edgeflow sh -c 'cat > no_backup/models/tiny.bin' < /ruta/ggml-tiny.bin
adb exec-in run-as dev.edgeflow sh -c 'cat > no_backup/models/base.bin' < /ruta/ggml-base.bin
adb exec-in run-as dev.edgeflow sh -c 'cat > files/smoke-es.wav' < benchmarks/smoke-es.wav
```

Activa modo avión manualmente en el teléfono. En un emulador desechable se puede usar `adb shell cmd connectivity airplane-mode enable`.

```bash
adb shell am instrument -w -e class dev.edgeflow.OfflineEngineTest -e model tiny dev.edgeflow.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class dev.edgeflow.OfflineEngineTest -e model base dev.edgeflow.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s System.out:I
```

La prueba omite ejecución si faltan fixtures: verifica que no haya assumption/skip en los resultados. Comprueba palabras de la muestra y repetibilidad frío/caliente; no usa igualdad con la referencia como criterio de calidad. El texto sintético se imprime solo desde el test. Producción no registra transcripciones.

## Inserción en editor sintético

Solo en emulador desechable. Instala app y test APK. Habilita el servicio en Ajustes de accesibilidad, o usa los siguientes comandos únicamente en ese emulador:

```bash
adb shell settings put secure enabled_accessibility_services dev.edgeflow/dev.edgeflow.DictationAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell am instrument -w -e class dev.edgeflow.InsertionDeviceTest -e accessibility true dev.edgeflow.test/androidx.test.runner.AndroidJUnitRunner
```

El test da consentimiento en preferencias, reconecta el servicio y abre un editor externo dentro del APK de pruebas. Ningún editor sintético se distribuye en el APK normal. Comprueba burbuja, actividad visible, inserción en cursor intermedio conservando texto y ocultación en contraseña. Cambia temporalmente el registro de servicios habilitados para reconectar el servicio bajo instrumentación; no ejecutar en un teléfono de uso normal con servicios de accesibilidad importantes activos.

## Descarga real opcional

Con red disponible, solo en emulador desechable y sin archivo Tiny previo en Descargas/EdgeFlow:

```bash
adb shell am instrument -w -e class dev.edgeflow.ModelDownloadTest -e download true dev.edgeflow.test/androidx.test.runner.AndroidJUnitRunner
```

Descarga 78 MB; espera instalación privada tras verificar hash y comprueba el original público en `Descargas/EdgeFlow`. No usa claves. Las pruebas sin el argumento explícito omiten este test.

Para probar reinstalación: confirmar que el original existe; desinstalar la app (se pierden ajustes y copias privadas); reinstalar; usar Importar en Modelos y seleccionar el original en el selector Android. Repetir dictado en modo avión. No prometer que un limpiador de archivos no borre Descargas.

## Aceptación en Xiaomi 12 Pro

```bash
export ANDROID_SERIAL=<serial-del-telefono>
scripts/device-info.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No se cambian automáticamente ajustes de HyperOS. Tras instalar, probar primero dictado interno y luego aceptar accesibilidad. No hace falta cambiar Gboard. Si Android restringe accesibilidad por instalación manual, revisar el aviso de Ajustes del propio sistema.

Registrar cada escenario como PASS/FAIL/PENDIENTE, modelo/revisión, Android/HyperOS y evidencia sin datos privados:

| Área | Casos |
| --- | --- |
| Audio | español paraguayo, inglés técnico, mezcla, 5/15/30/60 s, silencio y ruido; 180 s queda fuera del límite v0 |
| Inserción | navegador, notas, WhatsApp, Telegram, WebView; vacío, contenido, cursor intermedio, selección, multilínea, contraseña |
| Cambios | perder foco, otra app, otra ventana, rotación, bloqueo, cerrar actividad; nunca escribir en nuevo destino |
| Permisos | denegar/revocar micrófono y accesibilidad; recuperación al regresar sin reactivar permisos |
| Interrupciones | llamada, otro usuario de micrófono, Bluetooth/desconexión, ahorro batería; cancelar sin insertar |
| Modelos | corrupto, incompleto, poco espacio, descarga cancelada, cambio Tiny/Base; no cargar artefacto sin verificar |
| Red | modo avión tras instalar; sin solicitudes salientes durante inferencia |
| Persistencia | muerte de proceso durante descarga, actualización APK, desinstalar/reinstalar/importar original público |

Medir por variante frío/caliente: tiempo Detener→resultado, duración de audio, RTF=segundos inferencia/segundos audio, RAM (`dumpsys meminfo dev.edgeflow`), térmicas y batería antes/después. La latencia de UI incluye carga del modelo; no hay instrumentación separada de carga en v0.

Para WER/CER, guarda una referencia transcrita por una persona y la salida en UTF-8 y ejecuta `python3 scripts/error-rate.py referencia.txt salida.txt`. Normaliza a minúsculas, NFC y sin puntuación; conserva tildes. No subas audio ni mensajes privados al repositorio.
