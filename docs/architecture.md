# Arquitectura de la v0

- `MainActivity`: Compose, permisos contextuales, consentimiento, selección/importación, dictado interno y retorno explícito al editor. Nunca graba por recibir un Intent.
- `SessionViewModel`: sesión observable READY/RECORDING/PROCESSING/REVIEWING/ERROR. Generaciones descartan resultados cancelados; una inferencia por ViewModel y exclusión nativa global. El ViewModel conserva estado en rotación, pero la captura se cancela al pausar la actividad (incluida una rotación) para no mantener micrófono fuera de interacción visible.
- `AudioRecord`: mono PCM16 16 kHz, buffer máximo 180 s, loop solo durante grabación. Audio convertido a FloatArray tras detener. No se escribe audio capturado a disco.
- `SpeechEngine` / `WhisperEngine`: runtime intercambiable; modelo y lenguaje explícitos. JNI se ocupa solo de carga/inferencia/descarga/cancelación; Whisper.cpp realiza features, tokenizer y decodificación. Un único contexto, CPU hasta cuatro hilos, sin LLM.
- `ModelCatalog` / `ModelStore`: catálogo inmutable por revisión, tamaño y hash. Importación limitada, espacio libre, archivo .part, rename tras verificar. `.part` no se usa como modelo.
- `ModelDownloads`: DownloadManager del sistema; fuente pública y descarga consentida por Wi-Fi. Polling de 1 s solo durante descarga activa. ID persistido; verificación e instalación al completar. Original conservado en carpeta pública.
- `DictationAccessibilityService`: eventos de foco/texto/selección/ventanas; busca solo FOCUS_INPUT, sin recorrer mensajes. Overlay de accesibilidad no enfocable y arrastrable. Oculta en campos no editables, protegidos, bloqueados, propios y paquetes excluidos.
- `InsertionPolicy`: política pura con tests. Rechaza selección desconocida/no vacía y cortes de pares UTF-16. Reconstruye solo al insertar en el nodo activo, preservando ambos lados del cursor.

## Destino y fallos

La burbuja captura referencia del nodo y cursor durante hasta seis minutos, sin persistir texto de la otra app. Ir a otro editor/ventana externa o cambiar selección invalida el destino. La actividad propia se permite para grabar, detener y revisar.

Volver e insertar arma un resultado pendiente por cinco segundos, cierra la actividad y espera el foco del mismo nodo. Valida de nuevo `refresh`, visibilidad, contraseña, exclusiones y selección; lee contenido solo para construir la inserción. `ACTION_SET_TEXT` se envía una vez y se verifica. Si la acción no confirma el resultado, no se restaura ni se reintenta automáticamente: se conserva el dictado en memoria y se invita a revisar/copiar.

El nodo Android puede quedar obsoleto o el editor puede transformar el texto. Esto requiere pruebas por app; no existe garantía universal de inserción a través de accesibilidad. El respaldo es parte de la v0.

## Diferencias deliberadas respecto del producto completo

No hay FGS ni permiso FOREGROUND_SERVICE_MICROPHONE porque la captura termina al salir de la pantalla visible. No se usan las posibles excepciones de background-start para iniciar micrófono desde overlay. No se solicitan POST_NOTIFICATIONS (notificaciones propias inexistentes) ni SYSTEM_ALERT_WINDOW (overlay de accesibilidad).

Historial, IME, ajuste al área real de teclado, routing Bluetooth y ASR especializado con LiteRT siguen pendientes. Diccionario, revisión y límite de 180 s se incorporan en v0.2, descrita abajo. No hay botones para motores no integrados.

## Cambios v0.2

`RoutingSpeechEngine` elige Whisper.cpp o Gemma según descriptor y descarga el otro runtime antes de cargarlo. `GemmaEngine` usa LiteRT-LM 0.13.1 sobre un dispatcher de un solo hilo y un mutex para mantener serializado el contexto GPU. No se copió implementación de la otra aplicación del propietario; se reutilizan runtime y convenciones documentadas de API.

Gemma transcribe audio desde WAV PCM16 en memoria en fragmentos de 25 s con 1 s de solapamiento. Cada fragmento tiene conversación efímera; un prefijo/sufijo compartido de al menos dos palabras elimina duplicados exactos. El límite total pasa a 180 s, el de cada entrada permanece por debajo de 30 s. El texto de borde puede requerir revisión; no se garantiza continuidad perfecta.

La sesión usa PROCESSING para ASR y REVIEWING para revisión opcional. Un mutex a nivel de aplicación evita dos pipelines simultáneos aunque se recree una pantalla. Guarda el original en RAM antes de revisar; fallos, cancelación o rechazo del revisor preservan ese original. Nunca se selecciona automáticamente la propuesta. La UI usa `selectedText()` para que Copiar e Insertar operen sobre la misma versión elegida.

`PersonalDictionary` produce términos canónicos y reglas explícitas de palabra completa, en una pasada sin cascadas. Los términos guían el ASR (Whisper con initial_prompt limitado; Gemma mediante contexto). Las reglas generan una propuesta separada sin modificar el original. `ReviewPolicy` rechaza cifras/negaciones cambiadas, salidas desproporcionadas y marcadores internos; no es un verificador semántico ni acústico.

El diccionario se persiste de forma privada; transcripción, propuesta y audio no se guardan en historial. No se recopilan ejemplos para fine-tuning ni se ejecuta entrenamiento en la PC. El catálogo separa runtime, artefacto, revisión, hash y licencia para evitar cargar `.tflite` en LiteRT-LM.
