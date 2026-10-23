# Investigación — 23 de octubre de 2026

El documento de propuesta fue una referencia de producto. La v0 prioriza una ruta pequeña con ASR real; no declara completadas todas sus fases.

## Documentación primaria consultada

| Fuente | Hallazgo | Consecuencia |
| --- | --- | --- |
| [Xiaomi 12 Pro](https://www.mi.com/pk/product/xiaomi-12-pro/specs/) | Snapdragon 8 Gen 1, variante 12 GB LPDDR5/256 GB UFS 3.1 | Especificaciones de fábrica; Android actual, memoria libre y driver requieren ADB |
| [LiteRT samples ASR](https://github.com/google-ai-edge/litert-samples/tree/main/samples/litert/speech_recognition) | Pipeline Android con features, decodificadores y tokenizer JNI | No basta cargar un archivo; revisar componentes antes de adoptar |
| [Qwen3-ASR](https://github.com/QwenLM/Qwen3-ASR), [checkpoint](https://huggingface.co/Qwen/Qwen3-ASR-0.6B), [conversión](https://huggingface.co/litert-community/Qwen3-ASR-0.6B) | Modelo oficial y artefactos LiteRT separados; conversión Apache-2.0 con model card vacía al consultar | Candidato siguiente; no validado ni declarado incompatible |
| [Whisper.cpp Android](https://github.com/ggml-org/whisper.cpp/tree/v1.8.7/examples/whisper.android) | Ejemplo mantenido recomienda Tiny/Base; procesa audio con código nativo existente | Motor de la v0; se reutiliza runtime completo, no se reimplementa mel/tokenización |
| [Whisper pesos convertidos](https://huggingface.co/ggerganov/whisper.cpp) | Tiny/Base multilingües disponibles públicamente | Descargar por revisión y verificar hashes LFS |
| [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) | Runtime de modelos generativos compatibles | Separado del ASR; no se cargan .tflite con una API .litertlm |
| [eg2-transcribe](https://github.com/john-rocky/eg2-transcribe) | Encoder congelado + reader CTC; vocabulario de 200.000 palabras inglesas y entrenamiento LibriSpeech | No es ASR español listo; requiere trabajo adicional. Fuera de v0 |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Alternativa offline con Android | No se añade otro runtime en la primera prueba |
| [Accesibilidad Android](https://developer.android.com/guide/topics/ui/accessibility/service) | Inspección de nodos y acciones; permiso de alto alcance | Consentimiento, foco limitado, sin dumps ni lecturas globales |
| [Restricciones de FGS](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [tipos Android 14](https://developer.android.com/about/versions/14/changes/fgs-types-required) | Micrófono while-in-use y arranques desde background restringidos | Grabación solo desde actividad visible, sin FGS en v0 |
| [Política AccessibilityService](https://support.google.com/googleplay/android-developer/answer/10964491) | Divulgación, consentimiento y declaración para servicios no especializados en discapacidad | isAccessibilityTool=false; publicación en Play pendiente de revisión |
| [DownloadManager.Request](https://developer.android.com/reference/android/app/DownloadManager.Request) | Destino público Downloads con scoped storage | Descarga voluntaria en carpeta pública; sin permiso de almacenamiento amplio |
| [Archivos específicos de app](https://developer.android.com/training/data-storage/app-specific) | Android elimina almacenamiento específico al desinstalar | Original público conservado; copia privada verificada para inferencia |
| [Wispr Android](https://wisprflow.ai/android) | Referencia de interacción entre apps | No se reutiliza ningún código ni material gráfico propietario |

## Hallazgos en código

- `litert-samples` inspeccionado en `367816dac824a1984648582060424a817dd5d69d`: `ModelMetadataManager`, `LiteRtRunner`, `model_metadata.json`, Gradle y JNI. Usa LiteRT 2.3.0, JNI de tokenizadores y JLibrosa descargado como JAR SNAPSHOT. Qwen usa ventana de 5 s, log-mel 128, FFT 400, tokenizador Qwen2.5 y firmas encode/decode. Añadirlo exige cerrar integridad/licencias de artefactos y segmentación antes de distribuir.
- La tabla del ejemplo confirma aceleradores en ciertos modelos/teléfonos; no demuestra Snapdragon 8 Gen 1. No se incluyen bibliotecas NPU Qualcomm.
- `whisper.cpp` integrado por submódulo sin cambios, tag v1.8.7. JNI propio pequeño; CPU con máximo cuatro hilos, sin GPU y sin red. Solo un contexto nativo cargado; al cambiar modelo se libera el anterior.
- Proyecto local `android-gemma-showcase`: se inspeccionaron ModelDownloadManager, ModelVariant, LlmEngineManager y Gradle. Usa LiteRT-LM 0.13.1, Gemma 4 E2B/E4B, GPU con fallback CPU y audioBackend CPU. DownloadManager descarga a external app-specific y mueve a filesDir. Ambas ubicaciones se pierden al desinstalar; no son almacenamiento público persistente. No se copió código porque no se encontró licencia de raíz.
- El propietario informa que Gemma/LiteRT-LM GPU funciona en su Xiaomi. Se registra como experiencia del propietario, sin medición repetida aquí.

## Catálogo reproducible

HF repo `ggerganov/whisper.cpp`, revisión `5359861c739e955e79d9a303bcbc70fb988958b1`.

| Archivo | Bytes | SHA-256 | Licencia |
| --- | ---: | --- | --- |
| ggml-tiny.bin | 77691713 | be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21 | MIT |
| ggml-base.bin | 147951465 | 60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe | MIT |

## Compatible con Xiaomi (probado)

Ninguna variante de EdgeFlow probada aún en el teléfono. El usuario lo probará después. ADB no tenía dispositivos físicos conectados al iniciar el trabajo.

| Variante/runtime | En v0 | Español | Xiaomi 12 Pro | Acelerador |
| --- | --- | --- | --- | --- |
| Whisper Tiny / whisper.cpp | Sí | Multilingüe | Pendiente | CPU, sin benchmark físico |
| Whisper Base / whisper.cpp | Sí | Multilingüe | Pendiente | CPU, sin benchmark físico |
| Qwen3-ASR / LiteRT | No | Checkpoint multilingüe | Pendiente | CPU/GPU/NPU sin medir |
| Whisper / LiteRT | No | Depende del checkpoint | Pendiente | Sin medir |
| Parakeet TDT/CTC / LiteRT | No | Confirmar checkpoint concreto; no todos son multilingües | Pendiente | Sin medir |
| EmbeddingGemma 2 + reader | No | Reader inspeccionado inglés | No validado para español | Sin medir |
| Gemma / LiteRT-LM | No | Posprocesamiento opcional futuro | Reportado por propietario en otra app | GPU reportada, no ASR |

## Pendiente

Obtener versión real Android/HyperOS, ABI, RAM libre, driver, audio y térmicas; medir grabaciones humanas y Gboard/apps; comparar Tiny/Base; integrar LiteRT si mejora calidad/latencia con esfuerzo razonable. No usar el emulador para extrapolar velocidad ni compatibilidad Qualcomm.
