package dev.edgeflow

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GemmaDeviceTest {
    @Test fun audioAndTextReviewOnExplicitDevice() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Gemma test is explicit and uses only synthetic fixtures", arguments.getString("gemma") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as EdgeApp
        val model = ModelCatalog.find(arguments.getString("model", "gemma-e4b"))
        check(model.runtime == Runtime.GEMMA && app.models.installed(model)) { "Supply verified Gemma model" }
        val bytes = File(app.filesDir, "smoke-es.wav").readBytes()
        val samples = try { WavReader.read(bytes) } finally { bytes.fill(0) }
        val engine = app.gemma
        try {
            val start = android.os.SystemClock.elapsedRealtime()
            val original = engine.transcribe(model, samples, "es")
            val asrMs = android.os.SystemClock.elapsedRealtime() - start
            println("SYNTHETIC_GEMMA device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} soc=${Build.SOC_MODEL} model=${model.id} backend=${engine.backend} asr_ms=$asrMs result=$original")
            assertTrue(original, original.contains("prueba", true))
            assertTrue(original, original.contains("local", true))
            val warmStart = android.os.SystemClock.elapsedRealtime()
            val warm = engine.transcribe(model, samples, "es")
            println("SYNTHETIC_GEMMA_WARM ms=${android.os.SystemClock.elapsedRealtime() - warmStart} result=$warm")
            assertEquals(original, warm)
            val technical = File(app.filesDir, "technical-es.wav")
            if (technical.exists()) {
                val technicalBytes = technical.readBytes()
                val technicalAudio = try { WavReader.read(technicalBytes) } finally { technicalBytes.fill(0) }
                engine.vocabulary = "PoC\nPR\nGitHub\nAndroid"
                try {
                    val technicalStart = android.os.SystemClock.elapsedRealtime()
                    val text = engine.transcribe(model, technicalAudio, "es")
                    println("SYNTHETIC_GEMMA_TECH ms=${android.os.SystemClock.elapsedRealtime() - technicalStart} result=$text")
                    assertTrue(text, text.contains("PoC", true))
                    assertTrue(text, text.contains("GitHub", true))
                } finally { technicalAudio.fill(0f) }
            }
            if (arguments.getString("long") == "true") {
                val longer = FloatArray(samples.size * 7) { samples[it % samples.size] }
                try {
                    val longStart = android.os.SystemClock.elapsedRealtime()
                    val text = engine.transcribe(model, longer, "es")
                    println("SYNTHETIC_GEMMA_LONG samples=${longer.size} ms=${android.os.SystemClock.elapsedRealtime() - longStart} result=$text")
                    assertTrue(text, text.contains("prueba", true))
                    assertTrue(text, text.length > original.length)
                } finally { longer.fill(0f) }
            }
            val raw = "vamos a revisar el PR de GitHub y actualizar PostgreSQL 17 no ejecutar el deploy hoy"
            val reviewStart = android.os.SystemClock.elapsedRealtime()
            val reviewed = engine.review(model, raw, "PR\nGitHub\nPostgreSQL")
            println("SYNTHETIC_REVIEW ms=${android.os.SystemClock.elapsedRealtime() - reviewStart} original=$raw proposal=$reviewed")
            assertNull(ReviewPolicy.rejection(raw, reviewed))
            assertTrue(reviewed, reviewed.contains("PostgreSQL", true))
            assertTrue(reviewed, reviewed.contains("GitHub", true))
            assertNotEquals(raw, reviewed)
            if (arguments.getString("configure") == "true") {
                val dictionary = app.preferences.getString("vocabulary", "").orEmpty()
                val withPoc = if (DictationText.terms(dictionary).any { it.equals("PoC", true) }) dictionary else (dictionary + "\nPoC").trim()
                app.preferences.edit().putString("vocabulary", withPoc).putString("model", model.id).putString("review_model", model.id).putBoolean("review", true).commit()
            }
        } finally { samples.fill(0f); engine.unload() }
    }
}
