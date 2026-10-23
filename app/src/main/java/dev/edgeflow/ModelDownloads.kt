package dev.edgeflow

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** System-managed public downloads survive uninstall; verified private copies feed native ASR. */
class ModelDownloads(private val context: Context, private val store: ModelStore) {
    private val manager = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(DownloadState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    init {
        val id = prefs.getLong("id", -1)
        val model = ModelCatalog.all.find { it.id == prefs.getString("model", "") }
        if (id >= 0 && model != null) monitor(id, model)
    }
    data class DownloadState(val active: Boolean = false, val message: String = "", val progress: Float = 0f, val revision: Int = 0)
    fun start(model: ModelDescriptor) {
        if (mutable.value.active) return
        try {
            val free = android.os.StatFs(Environment.getExternalStorageDirectory().path).availableBytes
            require(free > model.bytes * 2 + 32L * 1024 * 1024) { "Espacio insuficiente para original y copia privada" }
            val request = DownloadManager.Request(Uri.parse(model.url))
                .setTitle("EdgeFlow · ${model.label}")
                .setDescription("Modelo ASR local. Se conservará en Descargas/EdgeFlow.")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "EdgeFlow/ggml-${model.id}.bin")
                .setAllowedOverMetered(false)
            val id = manager.enqueue(request)
            prefs.edit().putLong("id", id).putString("model", model.id).apply()
            monitor(id, model)
        } catch (e: Exception) { mutable.value = DownloadState(message = e.message ?: "No se pudo descargar") }
    }
    fun cancel() {
        job?.cancel()
        val id = prefs.getLong("id", -1)
        if (id >= 0) manager.remove(id)
        prefs.edit().clear().apply()
        mutable.value = DownloadState(message = "Descarga cancelada")
    }
    private fun monitor(id: Long, model: ModelDescriptor) {
        mutable.value = DownloadState(active = true, message = "Descargando ${model.id} por Wi-Fi…")
        job = scope.launch {
            try {
                while (isActive) {
                    val status = manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                        check(cursor.moveToFirst()) { "Descarga eliminada por el sistema" }
                        val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        mutable.value = mutable.value.copy(progress = (bytes.toFloat() / model.bytes).coerceIn(0f, 1f))
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    }
                    when (status) {
                        DownloadManager.STATUS_FAILED -> error("Falló la descarga. Si ya existe el archivo en Descargas/EdgeFlow, impórtalo; no se sobrescribe.")
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            mutable.value = mutable.value.copy(message = "Verificando SHA-256 e instalando…")
                            val uri = manager.getUriForDownloadedFile(id) ?: error("No se pudo abrir la descarga")
                            store.import(model, uri) { mutable.value = mutable.value.copy(progress = it) }
                            mutable.value = DownloadState(message = "${model.label} instalado. Original conservado en Descargas/EdgeFlow.", revision = mutable.value.revision + 1)
                            break
                        }
                    }
                    delay(1000)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = DownloadState(message = e.message ?: "Error de descarga o verificación") }
            finally { if (prefs.getLong("id", -1) == id) prefs.edit().clear().apply() }
        }
    }
}
