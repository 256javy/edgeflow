package dev.edgeflow

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

enum class Runtime { WHISPER, GEMMA }
data class ModelDescriptor(
    val id: String, val bytes: Long, val sha256: String,
    val runtime: Runtime = Runtime.WHISPER,
    val label: String = "Whisper ${id.replaceFirstChar { it.uppercase() }} multilingüe",
    val revision: String = "5359861c739e955e79d9a303bcbc70fb988958b1",
    val repository: String = "ggerganov/whisper.cpp"
) {
    val filename get() = when (id) {
        "gemma-e2b" -> "gemma-4-E2B-it.litertlm"
        "gemma-e4b" -> "gemma-4-E4B-it.litertlm"
        else -> "ggml-$id.bin"
    }
    val url get() = "https://huggingface.co/$repository/resolve/$revision/$filename?download=true"
    val license get() = if (runtime == Runtime.GEMMA) "Apache-2.0" else "MIT"
    val privateFilename get() = if (runtime == Runtime.GEMMA) "$id.litertlm" else "$id.bin"
}
object ModelCatalog {
    val all = listOf(
        ModelDescriptor("tiny", 77691713, "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"),
        ModelDescriptor("base", 147951465, "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe"),
        ModelDescriptor("gemma-e2b", 2588147712L, "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", Runtime.GEMMA,
            "Gemma 4 E2B · audio y revisión", "a790a852d433991c0c0a41c1154cf9b3850300fa", "litert-community/gemma-4-E2B-it-litert-lm"),
        ModelDescriptor("gemma-e4b", 3659530240L, "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0", Runtime.GEMMA,
            "Gemma 4 E4B · audio y revisión", "e6a3892a8c22cf87ff99583996149cf3cb32452b", "litert-community/gemma-4-E4B-it-litert-lm")
    )
    val reviewers get() = all.filter { it.runtime == Runtime.GEMMA }
    fun find(id: String?) = all.find { it.id == id } ?: all.first()
}

class ModelStore(private val context: Context) {
    private val directory = File(context.noBackupFilesDir, "models").apply { mkdirs(); listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() } }
    fun file(model: ModelDescriptor) = File(directory, model.privateFilename)
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
