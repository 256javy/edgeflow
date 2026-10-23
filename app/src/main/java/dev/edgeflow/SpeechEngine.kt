package dev.edgeflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SpeechEngine {
    suspend fun transcribe(model: ModelDescriptor, samples: FloatArray, language: String): String
    fun cancel()
    suspend fun unload()
}
object NativeWhisper {
    init { System.loadLibrary("edgeflow") }
    external fun prepare()
    external fun transcribe(path: String, audio: FloatArray, language: String): String
    external fun cancel()
    external fun unload()
}
class WhisperEngine(private val store: ModelStore) : SpeechEngine {
    override suspend fun transcribe(model: ModelDescriptor, samples: FloatArray, language: String): String {
        check(store.installed(model)) { "Primero importa un modelo verificado" }
        NativeWhisper.prepare()
        return withContext(Dispatchers.IO) { NativeWhisper.transcribe(store.file(model).absolutePath, samples, language).trim() }
    }
    override fun cancel() = NativeWhisper.cancel()
    override suspend fun unload() = withContext(Dispatchers.IO) { NativeWhisper.unload() }
}
