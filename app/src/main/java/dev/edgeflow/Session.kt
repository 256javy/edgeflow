package dev.edgeflow

import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class EdgeApp : Application() {
    val models by lazy { ModelStore(this) }
    val downloads by lazy { ModelDownloads(this, models) }
    var lastResult = SessionState()
    val engine by lazy { WhisperEngine(models) }
    val preferences by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
}
enum class Phase { READY, RECORDING, PROCESSING, ERROR }
data class SessionState(
    val phase: Phase = Phase.READY, val result: String = "", val message: String = "",
    val seconds: Int = 0, val level: Float = 0f, val latencyMs: Long = 0,
    val importing: Boolean = false, val importProgress: Float = 0f, val revision: Int = 0
)
class SessionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as EdgeApp
    private val mutable = MutableStateFlow(app.lastResult)
    val state = mutable.asStateFlow()
    private var recorder: AudioRecord? = null
    private var capture: Job? = null
    private var inference: Job? = null
    private var importJob: Job? = null
    private var cleanup: Job? = null
    private var pcm = ShortArray(0)
    private var count = 0
    @Volatile private var capturing = false
    private var generation = 0
    var model = ModelCatalog.all.first { it.id == app.preferences.getString("model", "tiny") }
        private set
    var language: String = app.preferences.getString("language", "es") ?: "es"
        private set
    fun selectModel(value: ModelDescriptor) {
        if (busy()) return
        model = value
        app.preferences.edit().putString("model", value.id).apply()
        mutable.value = mutable.value.copy(revision = mutable.value.revision + 1)
    }
    fun selectLanguage(value: String) {
        if (busy()) return
        language = value
        app.preferences.edit().putString("language", value).apply()
        mutable.value = mutable.value.copy(revision = mutable.value.revision + 1)
    }
    fun busy() = capture?.isActive == true || inference?.isActive == true || importJob?.isActive == true || cleanup?.isActive == true
    fun importModel(uri: android.net.Uri) {
        if (busy()) return
        importJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(importing = true, message = "Verificando modelo…")
            try {
                app.models.import(model, uri) { progress -> mutable.value = mutable.value.copy(importProgress = progress) }
                mutable.value = mutable.value.copy(message = "Modelo verificado e instalado", revision = mutable.value.revision + 1)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(message = e.message ?: "Error de importación") }
            finally { mutable.value = mutable.value.copy(importing = false) }
        }
    }
    fun start() {
        if (busy()) return
        if (app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) { fail("Permiso de micrófono denegado"); return }
        if (!app.models.installed(model)) { fail("Importa primero el modelo multilingüe"); return }
        try {
            val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { "Este dispositivo no admite PCM mono a 16 kHz" }
            val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 8192))
            if (audio.state != AudioRecord.STATE_INITIALIZED) { audio.release(); error("No se pudo iniciar el micrófono") }
            recorder = audio
            pcm = ShortArray(16000 * 60)
            count = 0
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Micrófono no disponible" }
            capturing = true
            generation++
            mutable.value = SessionState(phase = Phase.RECORDING, message = "Grabando · límite de 60 segundos")
            capture = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val block = ShortArray(2048)
                    while (capturing && count < pcm.size) {
                        val n = audio.read(block, 0, minOf(block.size, pcm.size - count))
                        check(n > 0) { "Captura interrumpida. Revisa micrófono y permisos." }
                        if (!capturing) break
                        block.copyInto(pcm, count, 0, n)
                        count += n
                        val rms = sqrt(block.take(n).sumOf { it.toDouble() * it } / n) / 32768
                        mutable.value = mutable.value.copy(seconds = count / 16000, level = rms.toFloat())
                    }
                    block.fill(0)
                } catch (e: Exception) {
                    if (capturing) withContext(Dispatchers.Main) { cancel(); fail(e.message ?: "Error de audio") }
                }
                if (capturing) withContext(Dispatchers.Main) { stop() }
            }
        } catch (e: Exception) { releaseRecorder(); pcm.fill(0); pcm = ShortArray(0); fail(e.message ?: "Permiso de micrófono no disponible") }
    }
    fun stop() {
        if (!capturing) return
        capturing = false
        runCatching { recorder?.stop() }
        val captureJob = capture
        val token = generation
        mutable.value = mutable.value.copy(phase = Phase.PROCESSING, message = "Procesando localmente…", level = 0f)
        inference = viewModelScope.launch {
            captureJob?.join()
            releaseRecorder()
            val audio = FloatArray(count) { pcm[it] / 32768f }
            pcm.fill(0); pcm = ShortArray(0)
            val start = android.os.SystemClock.elapsedRealtime()
            try {
                check(audio.size >= 8000 && audio.any { kotlin.math.abs(it) > .005f }) { "No se detectó audio suficiente" }
                val result = app.engine.transcribe(model, audio, language)
                if (token == generation) {
                    mutable.value = mutable.value.copy(phase = Phase.READY, result = result, latencyMs = android.os.SystemClock.elapsedRealtime() - start, message = if (result.isBlank()) "No se reconoció voz" else "Transcripción local lista")
                    app.lastResult = mutable.value
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token == generation) fail(e.message ?: "Error de inferencia local") }
            finally { audio.fill(0f) }
        }
    }
    fun transcribeFile(uri: android.net.Uri) {
        if (busy()) return
        generation++
        val token = generation
        mutable.value = SessionState(phase = Phase.PROCESSING, message = "Leyendo WAV local…")
        inference = viewModelScope.launch {
            var samples = FloatArray(0)
            try {
                samples = withContext(Dispatchers.IO) {
                    val bytes = app.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "No se pudo abrir el audio" }
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            require(output.size() + n <= 4_000_000) { "WAV demasiado grande" }
                            output.write(buffer, 0, n)
                        }
                        buffer.fill(0)
                        output.toByteArray()
                    }
                    try { WavReader.read(bytes) } finally { bytes.fill(0) }
                }
                val start = android.os.SystemClock.elapsedRealtime()
                val result = app.engine.transcribe(model, samples, language)
                if (token == generation) {
                    mutable.value = SessionState(result = result, seconds = samples.size / 16000, latencyMs = android.os.SystemClock.elapsedRealtime() - start, message = "Transcripción de WAV local lista")
                    app.lastResult = mutable.value
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token == generation) fail(e.message ?: "Error al transcribir WAV") }
            finally { samples.fill(0f) }
        }
    }
    fun cancel() {
        generation++
        capturing = false
        runCatching { recorder?.stop() }
        app.engine.cancel()
        importJob?.cancel()
        // Native work owns its buffers until it returns; discard its result through generation.
        cleanup = viewModelScope.launch { capture?.join(); releaseRecorder(); pcm.fill(0); pcm = ShortArray(0) }
        mutable.value = SessionState(message = "Cancelado; no se insertará texto")
        app.lastResult = SessionState()
        DictationAccessibilityService.instance?.cancelTarget()
    }
    fun clear() { if (!busy()) { mutable.value = SessionState(); app.lastResult = SessionState() } }
    private fun releaseRecorder() { recorder?.release(); recorder = null }
    private fun fail(message: String) { mutable.value = mutable.value.copy(phase = Phase.ERROR, message = message) }
    override fun onCleared() { capturing = false; runCatching { recorder?.stop() }; app.engine.cancel(); releaseRecorder(); pcm.fill(0) }
}
