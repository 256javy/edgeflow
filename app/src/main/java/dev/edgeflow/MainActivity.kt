package dev.edgeflow

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val session: SessionViewModel by viewModels()
    private var fromBubble by mutableStateOf(false)
    private var refresh by mutableIntStateOf(0)
    private var returning = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fromBubble = intent.getBooleanExtra("dictation", false)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xff1a534d), background = Color(0xfff7f8f2))) { Surface(Modifier.fillMaxSize()) { App() } } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); fromBubble = intent.getBooleanExtra("dictation", false); refresh++ }
    override fun onDestroy() {
        if (!isChangingConfigurations && !returning) DictationAccessibilityService.instance?.cancelTarget()
        super.onDestroy()
    }
    override fun onResume() { super.onResume(); refresh++ }
    override fun onPause() {
        if (session.state.value.phase == Phase.RECORDING) session.cancel()
        super.onPause()
    }
    override fun onStop() {
        if (!isChangingConfigurations && session.state.value.phase == Phase.RECORDING) session.cancel()
        super.onStop()
    }
    fun returnToTarget(text: String): Boolean {
        if (DictationAccessibilityService.instance?.returnWith(text) != true) return false
        returning = true
        fromBubble = false
        finish()
        return true
    }
    @Composable private fun App() {
        val app = application as EdgeApp
        val state by session.state.collectAsStateWithLifecycle()
        val download by app.downloads.state.collectAsStateWithLifecycle()
        var consent by remember { mutableStateOf(app.preferences.getBoolean("consent", false)) }
        var exclusions by remember { mutableStateOf(app.preferences.getString("excluded", "").orEmpty()) }
        var size by remember { mutableFloatStateOf(app.preferences.getInt("size", 56).toFloat()) }
        var opacity by remember { mutableFloatStateOf(app.preferences.getFloat("opacity", .9f)) }
        var phaseSeconds by remember { mutableLongStateOf(0) }
        LaunchedEffect(state.phase) {
            phaseSeconds = 0
            if (state.phase in setOf(Phase.PROCESSING, Phase.REVIEWING)) {
                val started = android.os.SystemClock.elapsedRealtime()
                while (true) { kotlinx.coroutines.delay(1000); phaseSeconds = (android.os.SystemClock.elapsedRealtime() - started) / 1000 }
            }
        }
        var tab by remember { mutableIntStateOf(0) }
        LaunchedEffect(fromBubble) { if (fromBubble) tab = 0 }
        var importTarget by remember { mutableStateOf(session.model) }
        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { session.importModel(it, importTarget) } }
        val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { session.transcribeFile(it) } }
        val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> if (allowed) session.start() }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(16.dp))
            Text("EdgeFlow", style = MaterialTheme.typography.headlineLarge)
            Text("Tu voz, dentro de tu teléfono.", style = MaterialTheme.typography.titleMedium)
            Text("Dictado local · Android 12 o posterior", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Dictar", "Modelos", "Ajustes").forEachIndexed { index, title -> FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(title) }) }
            }
            if (tab == 0) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(when (state.phase) { Phase.READY -> "Listo para dictar"; Phase.RECORDING -> "Grabando · ${state.seconds} s"; Phase.PROCESSING -> "Procesando en el dispositivo"; Phase.REVIEWING -> "Revisando redacción"; Phase.ERROR -> "Revisa el dictado" }, style = MaterialTheme.typography.titleLarge)
                        Text(session.model.label + " · " + session.language)
                        if (state.phase == Phase.RECORDING) LinearProgressIndicator(progress = { (state.level * 8).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        if (state.phase in setOf(Phase.PROCESSING, Phase.REVIEWING)) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (state.phase in setOf(Phase.PROCESSING, Phase.REVIEWING)) Text("${phaseSeconds} s · la primera carga del modelo tarda más; puedes cancelar.")
                        Text("La grabación se detiene al salir de esta pantalla. Máximo: 3 minutos. No hay historial ni envío de audio.")
                        Button(onClick = {
                            if (state.phase == Phase.RECORDING) session.stop()
                            else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) session.start()
                            else microphone.launch(Manifest.permission.RECORD_AUDIO)
                        }, enabled = (state.phase == Phase.RECORDING || !session.busy()) && !state.importing) { Text(if (state.phase == Phase.RECORDING) "Detener y transcribir" else "Grabar") }
                        if (state.phase in setOf(Phase.RECORDING, Phase.PROCESSING, Phase.REVIEWING)) OutlinedButton(onClick = { session.cancel() }) { Text(if (state.phase == Phase.REVIEWING) "Cancelar revisión" else "Cancelar") }
                    }
                }
                OutlinedButton(enabled = !session.busy(), onClick = { audioPicker.launch(arrayOf("audio/*")) }) { Text("Probar WAV local · PCM16 mono 16 kHz") }
                if (state.message.isNotEmpty()) Text(state.message)
                if (state.result.isNotEmpty()) {
                    Text("Transcripción original", style = MaterialTheme.typography.titleMedium)
                    androidx.compose.foundation.text.selection.SelectionContainer { Text(state.result) }
                    Text("${state.sourceModel} · ${state.backend}")
                    Text("ASR: ${state.asrMs} ms · revisión: ${state.reviewMs} ms · total: ${state.latencyMs} ms · ${state.seconds} s de audio")
                    if (state.reviewed.isNotBlank()) {
                        Text(state.proposalKind, style = MaterialTheme.typography.titleMedium)
                        androidx.compose.foundation.text.selection.SelectionContainer { Text(state.reviewed) }
                        Text("Es una propuesta: comprueba nombres, siglas y significado antes de usarla.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !state.useReview, enabled = !session.busy(), onClick = { session.chooseReview(false) }, label = { Text("Usar original") })
                            FilterChip(selected = state.useReview, enabled = !session.busy(), onClick = { session.chooseReview(true) }, label = { Text("Usar propuesta") })
                        }
                    }
                    Button(onClick = {
                        val clip = ClipData.newPlainText("Dictado", session.selectedText())
                        clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                    }, enabled = !session.busy()) { Text(if (state.useReview) "Copiar propuesta" else "Copiar original") }
                    if (fromBubble) OutlinedButton(onClick = {
                        if (!returnToTarget(session.selectedText())) refresh++
                    }, enabled = !session.busy()) { Text("Volver e insertar el texto elegido") }
                    Text("La inserción requiere el mismo campo y cursor. Si no es seguro, copia el texto y pégalo manualmente. Nunca se pulsa Enviar.")
                    TextButton(onClick = { session.clear() }) { Text("Borrar resultado") }
                }
                Text("Burbuja: " + if (DictationAccessibilityService.instance != null) "servicio conectado" else "servicio desactivado")
                DictationAccessibilityService.instance?.status?.let { Text(it) }
                Text("Puedes probar el dictado interno sin activar accesibilidad. Para usarlo en otras aplicaciones, completa Ajustes.")
                Text("Internet se usa solo al descargar modelos con tu autorización. Después, prueba el dictado en modo avión.")
            }
            if (tab == 1) {
                Text("Modelos locales", style = MaterialTheme.typography.titleLarge)
                Text("Descarga por Wi-Fi desde Hugging Face, sin cuenta ni clave. El original queda en Descargas/EdgeFlow y sobrevive a la desinstalación. Una copia privada verificada se usa para inferencia. Tras reinstalar, usa Importar para recuperar el archivo sin volver a descargarlo.")
                ModelCatalog.all.forEach { model ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(model.label, style = MaterialTheme.typography.titleMedium)
                        Text("${model.bytes / 1_000_000} MB · ${model.license} · español/inglés/automático")
                        Text(if (model.runtime == Runtime.GEMMA) "Audio y revisión · GPU preferida, respaldo CPU · fragmentos menores de 30 s" else "ASR pequeño de comparación · CPU")
                        Text(if (app.models.installed(model)) "Instalado y verificado al importar" else "Sin instalar")

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = session.model == model, enabled = !session.busy(), onClick = { session.selectModel(model) }, label = { Text("Usar") })
                            TextButton(enabled = !session.busy() && !download.active, onClick = { app.downloads.start(model) }) { Text("Descargar") }
                        }
                        OutlinedButton(enabled = !session.busy() && !download.active, onClick = { importTarget = model; filePicker.launch(arrayOf("*/*")) }) { Text("Importar ${model.id}") }
                    } }
                }
                if (download.message.isNotEmpty()) Text(download.message)
                if (download.active) { LinearProgressIndicator(progress = { download.progress }, modifier = Modifier.fillMaxWidth()); TextButton(onClick = { app.downloads.cancel() }) { Text("Cancelar descarga") } }
                Button(enabled = !session.busy() && !download.active, onClick = { importTarget = session.model; filePicker.launch(arrayOf("*/*")) }) { Text("Importar archivo para ${session.model.id}") }
                if (state.importing) { LinearProgressIndicator(progress = { state.importProgress }, modifier = Modifier.fillMaxWidth()); TextButton(onClick = { session.cancel() }) { Text("Cancelar importación") } }
                Text(state.message)
                Text("La inferencia es local. Se libera Whisper antes de cargar Gemma y viceversa. Gemma usa LiteRT-LM; la revisión es opcional y conserva el original.")
                TextButton(enabled = !session.busy(), onClick = { session.deleteModel() }) { Text("Eliminar copia privada del modelo") }
            }
            if (tab == 2) {
                Text("Permisos y privacidad", style = MaterialTheme.typography.titleLarge)
                Text("Accesibilidad permite identificar el campo enfocado, mostrar una burbuja y leer su texto únicamente al insertar, para conservarlo. No se almacenan formularios ni contraseñas. El servicio puede inspeccionar ventanas por su permiso de Android; actívalo solo si aceptas este uso.")
                Row {
                    Checkbox(checked = consent, onCheckedChange = { consent = it; app.preferences.edit().putBoolean("consent", it).apply(); if (!it) DictationAccessibilityService.instance?.onInterrupt() })
                    Text("Acepto este uso limitado de accesibilidad", Modifier.padding(top = 12.dp))
                }
                Button(enabled = consent, onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Abrir ajustes de accesibilidad") }
                Text("La burbuja usa TYPE_ACCESSIBILITY_OVERLAY. No se solicita permiso adicional de superposición ni se cambia Gboard.")
                Text("Idioma", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("es", "en", "auto").forEach { lang -> FilterChip(selected = session.language == lang, enabled = !session.busy(), onClick = { session.selectLanguage(lang) }, label = { Text(lang) }) }
                }
                Text("Diccionario personal", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = session.vocabulary, enabled = !session.busy(), onValueChange = { session.setVocabulary(it) },
                    label = { Text("Términos y correcciones") }, supportingText = { Text("Hasta 80 líneas. Ejemplos: PostgreSQL, DNIT o posgres => PostgreSQL. Una entrada por línea. No se entrena el modelo.") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                Text("Revisión local de redacción", style = MaterialTheme.typography.titleMedium)
                Row {
                    Switch(checked = session.reviewEnabled, enabled = !session.busy(), onCheckedChange = { session.configureReview(it) })
                    Text("Proponer revisión después del dictado", Modifier.padding(start = 8.dp, top = 10.dp))
                }
                Text("Conserva el original. Puede equivocarse: debes elegir explícitamente si copias o insertas la propuesta.")
                ModelCatalog.reviewers.forEach { reviewer ->
                    FilterChip(selected = session.reviewModel == reviewer, enabled = !session.busy(), onClick = { session.configureReview(session.reviewEnabled, reviewer) }, label = { Text(reviewer.label) })
                }
                Text("Tamaño de burbuja: ${size.toInt()} dp")
                Slider(value = size, onValueChange = { size = it; app.preferences.edit().putInt("size", it.toInt()).apply() }, valueRange = 48f..80f)
                Text("Opacidad")
                Slider(value = opacity, onValueChange = { opacity = it; app.preferences.edit().putFloat("opacity", it).apply() }, valueRange = .5f..1f)
                Text("Arrastra la burbuja para moverla. La posición se conserva. Reenfoca un campo para aplicar cambios.")
                OutlinedTextField(value = exclusions, onValueChange = { exclusions = it; app.preferences.edit().putString("excluded", it).apply() }, label = { Text("Apps excluidas: un paquete por línea") }, supportingText = { Text("Ejemplo: com.whatsapp. Añade gestores de contraseñas y apps bancarias.") }, modifier = Modifier.fillMaxWidth())
                Text("Xiaomi / HyperOS", style = MaterialTheme.typography.titleMedium)
                Text("Si el servicio deja de funcionar, revisa Accesibilidad, batería y restricciones de actividad en segundo plano en los ajustes de tu versión. Los nombres varían. No se cambian ajustes automáticamente. Android puede pedir habilitar ajustes restringidos para APK instalados manualmente.")
                Text("Diagnóstico: Android ${android.os.Build.VERSION.RELEASE} · SDK ${android.os.Build.VERSION.SDK_INT} · ${android.os.Build.SOC_MODEL} · ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
                Text("La revisión y el diccionario se ejecutan localmente. No hay entrenamiento, historial ni grabación en segundo plano. El texto original siempre queda disponible mientras viva el proceso.")
            }
            // Read a lifecycle refresh token to refresh permission/model diagnostics after returning.
            key(refresh) { Spacer(Modifier.height(24.dp)) }
        }
    }
}
