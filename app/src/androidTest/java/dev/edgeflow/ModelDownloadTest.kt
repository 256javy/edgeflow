package dev.edgeflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelDownloadTest {
    @Test fun downloadsAndVerifiesPublicTinyModel() {
        assumeTrue("Network test is opt-in", InstrumentationRegistry.getArguments().getString("download") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as EdgeApp
        val model = ModelCatalog.all.first()
        app.downloads.start(model)
        val end = android.os.SystemClock.elapsedRealtime() + 120000
        while (app.downloads.state.value.active && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(250)
        assertFalse("Download did not finish", app.downloads.state.value.active)
        assertTrue(app.downloads.state.value.message, app.downloads.state.value.message.contains("instalado"))
        assertTrue(app.models.installed(model))
        println("MODEL_DOWNLOAD verified public Tiny source and installed private copy")
    }
}
