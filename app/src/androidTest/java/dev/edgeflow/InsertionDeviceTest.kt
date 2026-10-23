package dev.edgeflow

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** External, synthetic editor installed only inside the test APK. */
class EditorFixtureActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val edit = EditText(this).apply {
            inputType = if (intent.getBooleanExtra("password", false)) 0x81 else 0x20001
            setText("Hola mundo")
            setSelection(4)
            requestFocus()
        }
        setContentView(edit)
    }
}

@RunWith(AndroidJUnit4::class)
class InsertionDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun await(message: String, predicate: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 8000
        while (!predicate() && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(100)
        assertTrue(message, predicate())
    }
    @Test fun bubbleInsertsWithoutDeletingAndHidesOnPassword() {
        val app = instrumentation.targetContext.applicationContext as EdgeApp
        app.preferences.edit().putBoolean("consent", true).commit()
        assumeTrue("Accessibility test is opt-in on an emulator", InstrumentationRegistry.getArguments().getString("accessibility") == "true")
        val enabled = android.provider.Settings.Secure.getString(app.contentResolver, "enabled_accessibility_services").orEmpty()
        assumeTrue("Enable accessibility on the test emulator first", enabled.contains("dev.edgeflow.DictationAccessibilityService"))
        // Instrumentation force-stops the target, so rebind the enabled test service.
        val automation = instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val without = enabled.split(':').filterNot { it.contains("dev.edgeflow.DictationAccessibilityService") }.joinToString(":")
        automation.executeShellCommand(if (without.isEmpty()) "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $without").close()
        Thread.sleep(300)
        automation.executeShellCommand("settings put secure enabled_accessibility_services $enabled").close()
        await("Accessibility service reconnects after instrumentation starts") { DictationAccessibilityService.instance != null }
        val service = DictationAccessibilityService.instance!!
        var main: MainActivity? = null
        val callback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { if (activity is MainActivity) main = activity }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callback)
        fun openEditor(password: Boolean = false) {
            instrumentation.context.startActivity(Intent(instrumentation.context, EditorFixtureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("password", password))
        }
        fun bubble() = service.windows.firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }?.root
        try {
            openEditor()
            await("Bubble must appear on editable field") { bubble() != null }
            val button = bubble()!!.findAccessibilityNodeInfosByText("Dictar").first()
            assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await("Bubble opens visible activity with captured target") { service.hasTarget() && main != null && service.rootInActiveWindow?.packageName?.toString() == app.packageName }
            instrumentation.runOnMainSync { val accepted = main!!.returnToTarget("dictado"); assertTrue(service.status, accepted) }
            await("Insertion must preserve both sides of cursor") {
                service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.text?.toString() == "Hola dictado mundo"
            }
            assertEquals("Texto insertado", service.status)
            main = null
            await("Bubble returns after insertion") { bubble() != null }
            assertTrue(bubble()!!.findAccessibilityNodeInfosByText("Dictar").first().performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await("Second session captures original field") { main != null && service.hasTarget() && service.rootInActiveWindow?.packageName?.toString() == app.packageName }
            openEditor()
            await("A different editor is focused") {
                service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.text?.toString() == "Hola mundo"
            }
            instrumentation.runOnMainSync { assertFalse(service.returnWith("no insertar")); main?.finish() }
            assertEquals("Hola mundo", service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.text?.toString())
            openEditor(password = true)
            await("Password editor focused") { service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.isPassword == true }
            await("No bubble on password") { bubble() == null }
            println("SYNTHETIC_INSERTION preserved text and cursor; changed target refused; password bubble hidden; no send action")
        } finally { app.unregisterActivityLifecycleCallbacks(callback); instrumentation.runOnMainSync { service.cancelTarget() } }
    }
}
