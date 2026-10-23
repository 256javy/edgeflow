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
    external fun transcribe(path: String, audio: FloatArray, language: String, vocabulary: String = ""): String
    external fun cancel()
    external fun unload()
}
class WhisperEngine(private val store: ModelStore) : SpeechEngine {
    var vocabulary: String = ""
    override suspend fun transcribe(model: ModelDescriptor, samples: FloatArray, language: String): String {
        require(model.runtime == Runtime.WHISPER) { "El artefacto no es Whisper" }
        check(store.installed(model)) { "Primero importa un modelo verificado" }
        NativeWhisper.prepare()
        return withContext(Dispatchers.IO) { NativeWhisper.transcribe(store.file(model).absolutePath, samples, language, vocabulary).trim() }
    }
    override fun cancel() = NativeWhisper.cancel()
    override suspend fun unload() = withContext(Dispatchers.IO) { NativeWhisper.unload() }
}

/** Release the other runtime before loading a model to bound RAM use. */
class RoutingSpeechEngine(private val whisper: WhisperEngine, private val gemma: GemmaEngine) : SpeechEngine {
    override suspend fun transcribe(model: ModelDescriptor, samples: FloatArray, language: String): String {
        return if (model.runtime == Runtime.GEMMA) {
            whisper.unload()
            gemma.transcribe(model, samples, language)
        } else {
            gemma.unload()
            whisper.transcribe(model, samples, language)
        }
    }
    override fun cancel() { whisper.cancel(); gemma.cancel() }
    override suspend fun unload() { whisper.unload(); gemma.unload() }
}
