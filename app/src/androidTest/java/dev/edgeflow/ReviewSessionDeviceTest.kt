package dev.edgeflow

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReviewSessionDeviceTest {
    @Test fun originalProposalSelectionAndCancellation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Pipeline test uses only synthetic WAV", InstrumentationRegistry.getArguments().getString("pipeline") == "true")
        val app = instrumentation.targetContext.applicationContext as EdgeApp
        check(app.preferences.getString("model", "") == "gemma-e4b" && app.preferences.getBoolean("review", false)) { "Select installed Gemma E4B and enable review first" }
        lateinit var vm: SessionViewModel
        instrumentation.runOnMainSync { vm = SessionViewModel(app) }
        fun await(message: String, predicate: () -> Boolean) {
            val end = android.os.SystemClock.elapsedRealtime() + 120000
            while (!predicate() && android.os.SystemClock.elapsedRealtime() < end) {
                check(vm.state.value.phase != Phase.ERROR) { vm.state.value.message }
                Thread.sleep(100)
            }
            assertTrue(message + ": " + vm.state.value.message, predicate())
        }
        val audio = Uri.fromFile(File(app.filesDir, "smoke-es.wav"))
        try {
            instrumentation.runOnMainSync { vm.transcribeFile(audio) }
            await("Original and proposal ready") { !vm.busy() && vm.state.value.result.isNotBlank() && vm.state.value.reviewed.isNotBlank() }
            val original = vm.state.value.result
            assertFalse(vm.state.value.useReview)
            assertEquals(original, vm.selectedText())
            instrumentation.runOnMainSync { vm.chooseReview(true) }
            assertEquals(vm.state.value.reviewed, vm.selectedText())
            instrumentation.runOnMainSync { vm.chooseReview(false) }
            assertEquals(original, vm.selectedText())
            instrumentation.runOnMainSync { vm.transcribeFile(audio) }
            await("Reviewing second dictation") { vm.state.value.phase == Phase.REVIEWING }
            val retained = vm.state.value.result
            instrumentation.runOnMainSync { vm.cancel() }
            await("Cancellation settled") { !vm.busy() }
            assertEquals(retained, vm.state.value.result)
            assertEquals(retained, vm.selectedText())
            assertEquals(Phase.READY, vm.state.value.phase)
            assertFalse(vm.state.value.useReview)
            println("SYNTHETIC_PIPELINE original preserved; proposal explicitly selected; review cancellation retains original")
        } finally {
            instrumentation.runOnMainSync { vm.cancel(); vm.clear() }
            runBlocking { app.engine.unload() }
        }
    }
}
