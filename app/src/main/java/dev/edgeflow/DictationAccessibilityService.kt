package dev.edgeflow

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

class DictationAccessibilityService : AccessibilityService() {
    companion object {
        private var reference = java.lang.ref.WeakReference<DictationAccessibilityService>(null)
        val instance: DictationAccessibilityService? get() = reference.get()
    }
    private val screenOff = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) { hide(); cancelTarget() }
    }
    private val prefs get() = (application as EdgeApp).preferences
    private val wm by lazy { getSystemService(WindowManager::class.java) }
    private var bubble: TextView? = null
    private var target: AccessibilityNodeInfo? = null
    private var sessionTarget: AccessibilityNodeInfo? = null
    private var selection = -1
    private var sessionStarted = 0L
    private var pendingText: String? = null
    private var pendingAt = 0L
    private var invalidated = false
    private var sessionActive = false
    var status: String = "Sin destino"
        private set

    override fun onServiceConnected() {
        reference = java.lang.ref.WeakReference(this)
        val filter = android.content.IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenOff, filter)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!prefs.getBoolean("consent", false) || getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            cancelTarget(); hide(); return
        }
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg == packageName) { hide(); return }
        if (sessionActive && event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED && event.source == sessionTarget) invalidated = true
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (!safe(focused)) {
            hide()
            // A different app window permanently invalidates a captured destination.
            if (sessionActive && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg.isNotEmpty()) {
                val eventWindow = windows.firstOrNull { it.id == event.windowId }
                if (eventWindow?.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION && event.windowId != sessionTarget?.windowId) invalidated = true
            }
            return
        }
        val node = focused!!
        if (sessionActive) {
            if (node != sessionTarget || node.textSelectionStart != selection || node.textSelectionEnd != selection) invalidated = true
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED && event.source == sessionTarget) invalidated = true
            if (pendingText != null) attemptInsertion(node)
        }
        target = AccessibilityNodeInfo.obtain(node)
        if (!sessionActive) showBubble()
    }
    private fun safe(node: AccessibilityNodeInfo?): Boolean {
        if (node == null || !node.isEditable || !node.isFocused || !node.isVisibleToUser || !node.isEnabled) return false
        if (node.packageName?.toString() == packageName) return false
        if (InsertionPolicy.sensitive(node.inputType, node.isPassword)) return false
        val excluded = prefs.getString("excluded", "").orEmpty().split('\n').map { it.trim() }
        return node.packageName?.toString() !in excluded
    }
    private fun showBubble() {
        if (bubble != null) return
        val size = (prefs.getInt("size", 56) * resources.displayMetrics.density).toInt()
        val layout = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.RIGHT
            x = prefs.getInt("x", 16); y = prefs.getInt("y", (300 * resources.displayMetrics.density).toInt())
        }
        val view = TextView(this).apply {
            text = "Dictar"; contentDescription = "Abrir dictado local"; gravity = Gravity.CENTER; textSize = 13f
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(26, 83, 77)); cornerRadius = size.toFloat() }
            alpha = prefs.getFloat("opacity", .9f)
        }
        view.setOnClickListener {
                        val node = target
                        if (safe(node) && node!!.refresh() && safe(node)) {
                            sessionTarget = AccessibilityNodeInfo.obtain(node)
                            selection = node.textSelectionStart
                            sessionStarted = SystemClock.elapsedRealtime()
                            invalidated = selection < 0 || node.textSelectionEnd != selection
                            sessionActive = true; status = "Destino seleccionado"; hide()
                            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("dictation", true))
                        }
        }
        var downX = 0f; var downY = 0f; var initialX = 0; var initialY = 0; var dragged = false
        view.setOnTouchListener { _, motion ->
            when (motion.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = motion.rawX; downY = motion.rawY; initialX = layout.x; initialY = layout.y; dragged = false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(motion.rawX - downX) + kotlin.math.abs(motion.rawY - downY) > 16) dragged = true
                    if (dragged) {
                        val bounds = wm.currentWindowMetrics.bounds
                        layout.x = (initialX - (motion.rawX - downX).toInt()).coerceIn(0, maxOf(0, bounds.width() - size))
                        layout.y = (initialY - (motion.rawY - downY).toInt()).coerceIn(0, maxOf(0, bounds.height() - size))
                        runCatching { wm.updateViewLayout(view, layout) }
                    }; true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) prefs.edit().putInt("x", layout.x).putInt("y", layout.y).apply() else {
                        view.performClick()
                    }; true
                }
                else -> false
            }
        }
        runCatching { wm.addView(view, layout); bubble = view }.onFailure { status = "No se pudo mostrar la burbuja" }
    }
    fun hasTarget() = sessionActive
    fun returnWith(text: String): Boolean {
        if (!sessionActive || invalidated || rootInActiveWindow?.packageName?.toString() != packageName || SystemClock.elapsedRealtime() - sessionStarted > 180000) { status = "Destino no válido; copia el resultado"; cancelTarget(); return false }
        pendingText = text; pendingAt = SystemClock.elapsedRealtime()
        val stamp = pendingAt
        // Focus after finishing the visible activity is validated in the next accessibility event.
        mainExecutor.execute {
            android.os.Handler(mainLooper).postDelayed({
                if (pendingText != null && pendingAt == stamp) { status = "No se pudo comprobar el destino; copia el resultado"; cancelTarget() }
            }, 5000)
        }
        return true
    }
    private fun attemptInsertion(node: AccessibilityNodeInfo) {
        val text = pendingText ?: return
        if (invalidated || SystemClock.elapsedRealtime() - pendingAt > 5000 || !node.refresh() || !safe(node) || node != sessionTarget) {
            status = "Destino cambió; copia el resultado"; cancelTarget(); return
        }
        val old = node.text?.toString() ?: ""
        val edit = InsertionPolicy.insert(old, node.textSelectionStart, node.textSelectionEnd, text)
        if (edit == null || !node.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT)) {
            status = "Editor no compatible; copia el resultado"; cancelTarget(); return
        }
        pendingText = null
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, edit.text) }
        val accepted = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        val verified = accepted && node.refresh() && node.text?.toString() == edit.text
        if (verified) node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, edit.cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, edit.cursor)
        })
        status = if (verified) "Texto insertado" else "Inserción sin verificar; revisa el campo antes de pegar"
        // Never retry SET_TEXT or restore blindly after an ambiguous action.
        cancelTarget()
    }
    fun cancelTarget() { sessionActive = false; sessionTarget = null; pendingText = null; invalidated = false }
    private fun hide() { bubble?.let { runCatching { wm.removeView(it) } }; bubble = null }
    override fun onInterrupt() { hide(); cancelTarget() }
    override fun onDestroy() { hide(); cancelTarget(); runCatching { unregisterReceiver(screenOff) }; reference.clear(); super.onDestroy() }
}
