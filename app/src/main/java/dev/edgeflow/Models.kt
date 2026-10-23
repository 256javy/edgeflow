package dev.edgeflow

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class ModelDescriptor(val id: String, val bytes: Long, val sha256: String) {
    val label get() = "Whisper ${id.replaceFirstChar { it.uppercase() }} multilingüe"
    val url get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-$id.bin?download=true"
    val license = "MIT"
}
object ModelCatalog {
    val all = listOf(
        ModelDescriptor("tiny", 77691713, "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"),
        ModelDescriptor("base", 147951465, "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe")
    )
}
class ModelStore(private val context: Context) {
    private val directory = File(context.noBackupFilesDir, "models").apply { mkdirs(); listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() } }
    fun file(model: ModelDescriptor) = File(directory, "${model.id}.bin")
    fun installed(model: ModelDescriptor) = file(model).length() == model.bytes
    suspend fun import(model: ModelDescriptor, uri: Uri, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        require(directory.usableSpace > model.bytes + 32L * 1024 * 1024) { "No hay espacio suficiente" }
        val temporary = File(directory, "${model.id}.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            context.contentResolver.openInputStream(uri).use { source ->
                requireNotNull(source) { "No se pudo abrir el archivo" }
                temporary.outputStream().use { target ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = source.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= model.bytes) { "Archivo demasiado grande para este modelo" }
                        digest.update(buffer, 0, n)
                        target.write(buffer, 0, n)
                        progress(total.toFloat() / model.bytes)
                    }
                }
            }
            require(total == model.bytes && digest.digest().joinToString("") { "%02x".format(it) } == model.sha256) { "SHA-256 o tamaño incorrecto. Usa el modelo multilingüe indicado." }
            check(temporary.renameTo(file(model))) { "No se pudo instalar el modelo" }
        } finally { temporary.delete() }
    }
}
