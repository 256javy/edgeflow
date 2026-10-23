package dev.edgeflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OfflineEngineTest {
    @Test fun recognizesSpanishFixtureWithoutNetwork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = (context.applicationContext as EdgeApp).models
        val variant = InstrumentationRegistry.getArguments().getString("model", "tiny")
        val model = ModelCatalog.all.first { it.id == variant }
        val wav = File(context.filesDir, "smoke-es.wav")
        assumeTrue("Supply verified Tiny model and synthetic smoke-es.wav; see docs/testing.md", store.installed(model) && wav.exists())
        val bytes = wav.readBytes()
        val samples = try { WavReader.read(bytes) } finally { bytes.fill(0) }
        val start = android.os.SystemClock.elapsedRealtime()
        val engine = WhisperEngine(store)
        try {
            val result = engine.transcribe(model, samples, "es")
            val elapsed = android.os.SystemClock.elapsedRealtime() - start
            // This explicit synthetic fixture has no personal data; production never logs transcripts.
            println("SYNTHETIC_ASR model=$variant cold_ms=$elapsed audio_samples=${samples.size} result=$result")
            assertTrue(result, result.contains("prueba", ignoreCase = true))
            assertTrue(result, result.contains("local", ignoreCase = true))
            val warmStart = android.os.SystemClock.elapsedRealtime()
            val warm = engine.transcribe(model, samples, "es")
            println("SYNTHETIC_ASR warm_ms=${android.os.SystemClock.elapsedRealtime() - warmStart}")
            assertEquals(result, warm)
        } finally { samples.fill(0f); engine.unload() }
    }
}
