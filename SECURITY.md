# Seguridad y privacidad

La v0 no está endurecida para distribución pública. No uses el dictado para contraseñas ni secretos.
Para comunicar un fallo de seguridad, contacta de forma privada al mantenedor del repositorio; no adjuntes datos sensibles en issues públicas.
Todavía no hay un canal público de contacto configurado.

## Límites

- `RECORD_AUDIO`: solo tras pulsar Grabar en una actividad visible. Salir o bloquear cancela la captura; no hay foreground service ni captura persistente.
- `INTERNET`: necesario para las descargas explícitas mediante DownloadManager. El motor nativo solo recibe una ruta local y PCM, sin cliente HTTP.
- Accesibilidad: servicio protegido por `BIND_ACCESSIBILITY_SERVICE`, consentimiento independiente, foco editable y acciones limitadas al campo original.
- La superposición es `TYPE_ACCESSIBILITY_OVERLAY`, no pide `SYSTEM_ALERT_WINDOW`. No se considera una actividad visible para grabar.
- No hay envío, pegado por portapapeles automático, analytics, cuentas ni historial. El botón Copiar es explícito y marca el contenido como sensible.
- El texto reconocido permanece solo en memoria mientras viva el proceso, para recuperarlo si falla la inserción. Borrar resultado lo descarta; Android/GC y el runtime nativo no permiten garantizar borrado criptográfico de RAM.
- Audio en memoria acotada a 60 s; buffers borrados al finalizar/cancelar. Los WAV importados pertenecen al usuario y no se borran.
- Modelos privados instalados solo tras tamaño exacto y SHA-256; revisiones fijadas. Las descargas públicas pueden ser modificadas por terceros, por eso nunca se cargan directamente.
- Originales en Descargas/EdgeFlow sobreviven a la desinstalación; copias privadas, consentimiento y ajustes no. Importar vuelve a verificar.
- La inserción `ACTION_SET_TEXT` no es transaccional. Se valida identidad/foco/cursor y se verifica la respuesta; no se reintenta ni se restaura a ciegas si el editor altera o rechaza texto. En esos casos hay que revisar el campo antes de pegar.

## Distribución

`isAccessibilityTool=false`. La divulgación y el consentimiento de la app no equivalen a aprobación de Google Play.
Antes de publicar se necesitan declaración y revisión de la política de accesibilidad, canal de vulnerabilidades y validación en dispositivos reales.

## Revisión local y diccionario (v0.2)

Gemma recibe audio o el texto original y el vocabulario introducido voluntariamente por el usuario. No recibe mensajes de otras apps ni contexto leído del editor. Audio, transcripciones y revisión no salen del teléfono. El diccionario y la preferencia de revisión se guardan en SharedPreferences privadas; no se guarda voz personal para entrenamiento.

La revisión está apagada por defecto y produce una propuesta separada. No se selecciona para copiar/insertar hasta que el usuario elige Usar propuesta. Se rechazan cambios de cifras o negaciones, salidas vacías/desproporcionadas y marcadores internos, pero estos controles no verifican el significado ni impiden todas las alucinaciones o instrucciones adversarias. El original permanece recuperable en memoria.

Las correcciones de diccionario son explícitas y de palabra/frase completa, sin cascadas. Alias con destinos contradictorios no se aplican. El vocabulario se trata como datos; no se ejecutan herramientas ni instrucciones extraídas del dictado. Ningún modo pulsa Enviar.

Gemma usa una conversación efímera por fragmento/revisión, sin historial entre sesiones. Un único modelo Gemma permanece cargado para reducir latencia, y se libera Whisper antes de cargarlo. Cancelación durante carga/inferencia puede demorar en liberar recursos; los resultados tardíos quedan invalidados.
