# Dependencias y atribución

| Componente | Versión/revisión | Licencia | Uso |
| --- | --- | --- | --- |
| whisper.cpp + ggml | v1.8.7, `48f628a84833905ee4a0658ee6d4a5c915ce1997` | MIT | ASR, log-mel y decodificación locales; submódulo sin modificaciones |
| Whisper Tiny/Base multilingües | conversión ggerganov/whisper.cpp `5359861c739e955e79d9a303bcbc70fb988958b1` | MIT | Pesos opcionales, no incluidos en APK |
| LiteRT-LM Android | 0.13.1 | Apache-2.0 | ASR multimodal Gemma y revisión local; decoder GPU o CPU |
| Gemma 4 E2B/E4B LiteRT-LM | Revisiones y hashes en docs/research.md | Apache-2.0 | Pesos opcionales, sin incluir en APK |
| Android Gradle Plugin | 9.2.1 | Apache-2.0 | Build |
| Gradle + wrapper | 9.4.1 | Apache-2.0 | Build; wrapper procedente del ejemplo oficial de Google |
| Kotlin Compose compiler | 2.3.10 | Apache-2.0 | Compilador |
| AndroidX Compose | BOM 2026.02.01 | Apache-2.0 | UI nativa Material 3 |
| AndroidX Activity | 1.12.4 | Apache-2.0 | Actividad y contratos de permisos/SAF |
| AndroidX Lifecycle | 2.10.0 | Apache-2.0 | ViewModel y observación de estado |
| kotlinx.coroutines | 1.10.2 | Apache-2.0 | Trabajo fuera del hilo principal |
| JUnit | 4.13.2 | EPL-1.0 | Tests JVM |
| AndroidX Test runner / junit | 1.6.2 / 1.2.1 | Apache-2.0 | Prueba Android |

Las licencias MIT nativas se incluyen también en `app/src/main/assets/licenses/`.
AndroidX/Kotlin conservan sus avisos en los artefactos distribuidos. Gradle: https://github.com/gradle/gradle/blob/v9.4.1/LICENSE.
Whisper: https://github.com/openai/whisper/blob/main/LICENSE.

Se inspeccionó android-gemma-showcase (Gemma Field) como referencia arquitectónica de DownloadManager y ciclo de vida de motores. No se copió código de ese repositorio, que no expone una licencia en su raíz.
Se investigaron litert-samples (Apache-2.0), Qwen3-ASR (Apache-2.0), sherpa-onnx (Apache-2.0) y eg2-transcribe (Apache-2.0 con condiciones adicionales para artefactos Gemma); no se incorporan sus runtimes ni pesos. LiteRT-LM/Gemma se añaden en v0.2 con sus avisos Apache-2.0.
