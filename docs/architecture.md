# Arquitectura de la v0

- `MainActivity`: Compose, permisos contextuales, consentimiento, selección/importación, dictado interno y retorno explícito al editor. Nunca graba por recibir un Intent.
- `SessionViewModel`: sesión observable READY/RECORDING/PROCESSING/ERROR. Generaciones descartan resultados cancelados; una inferencia por ViewModel y exclusión nativa global. El ViewModel conserva estado en rotación, pero la captura se cancela al pausar la actividad (incluida una rotación) para no mantener micrófono fuera de interacción visible.
- `AudioRecord`: mono PCM16 16 kHz, buffer máximo 60 s, loop solo durante grabación. Audio convertido a FloatArray tras detener. No se escribe audio capturado a disco.
- `SpeechEngine` / `WhisperEngine`: runtime intercambiable; modelo y lenguaje explícitos. JNI se ocupa solo de carga/inferencia/descarga/cancelación; Whisper.cpp realiza features, tokenizer y decodificación. Un único contexto, CPU hasta cuatro hilos, sin LLM.
- `ModelCatalog` / `ModelStore`: catálogo inmutable por revisión, tamaño y hash. Importación limitada, espacio libre, archivo .part, rename tras verificar. `.part` no se usa como modelo.
- `ModelDownloads`: DownloadManager del sistema; fuente pública y descarga consentida por Wi-Fi. Polling de 1 s solo durante descarga activa. ID persistido; verificación e instalación al completar. Original conservado en carpeta pública.
- `DictationAccessibilityService`: eventos de foco/texto/selección/ventanas; busca solo FOCUS_INPUT, sin recorrer mensajes. Overlay de accesibilidad no enfocable y arrastrable. Oculta en campos no editables, protegidos, bloqueados, propios y paquetes excluidos.
- `InsertionPolicy`: política pura con tests. Rechaza selección desconocida/no vacía y cortes de pares UTF-16. Reconstruye solo al insertar en el nodo activo, preservando ambos lados del cursor.

## Destino y fallos

La burbuja captura referencia del nodo y cursor durante hasta tres minutos, sin persistir texto de la otra app. Ir a otro editor/ventana externa o cambiar selección invalida el destino. La actividad propia se permite para grabar, detener y revisar.

Volver e insertar arma un resultado pendiente por cinco segundos, cierra la actividad y espera el foco del mismo nodo. Valida de nuevo `refresh`, visibilidad, contraseña, exclusiones y selección; lee contenido solo para construir la inserción. `ACTION_SET_TEXT` se envía una vez y se verifica. Si la acción no confirma el resultado, no se restaura ni se reintenta automáticamente: se conserva el dictado en memoria y se invita a revisar/copiar.

El nodo Android puede quedar obsoleto o el editor puede transformar el texto. Esto requiere pruebas por app; no existe garantía universal de inserción a través de accesibilidad. El respaldo es parte de la v0.

## Diferencias deliberadas respecto del producto completo

No hay FGS ni permiso FOREGROUND_SERVICE_MICROPHONE porque la captura termina al salir de la pantalla visible. No se usan las posibles excepciones de background-start para iniciar micrófono desde overlay. No se solicitan POST_NOTIFICATIONS (notificaciones propias inexistentes) ni SYSTEM_ALERT_WINDOW (overlay de accesibilidad).

Historial, diccionario, IME, posprocesamiento, ajuste al área real de teclado, sesiones de 180 s, routing Bluetooth y backends LiteRT siguen pendientes. No hay botones para motores no integrados.
