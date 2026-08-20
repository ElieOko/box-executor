package com.appbox.runtime.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HoshiAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        _isConnected.value = false
        super.onDestroy()
    }

    companion object {
        private var instance: HoshiAccessibilityService? = null
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        fun isEnabled(): Boolean = instance != null

        suspend fun sendWhatsAppMessage(timeoutMs: Long = 8000): Result<Unit> = runCatching {
            val service = instance ?: throw IllegalStateException(
                "Service Accessibilité HOSHI non activé — Paramètres → Accessibilité → HOSHI",
            )
            service.performWhatsAppSend(timeoutMs)
        }
    }

    private fun performWhatsAppSend(timeoutMs: Long) {
        val root = rootInActiveWindow ?: throw IllegalStateException("Fenêtre WhatsApp introuvable")
        try {
            val sendNode = findSendButton(root)
                ?: throw IllegalStateException("Bouton envoyer WhatsApp introuvable")

            if (!sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val rect = android.graphics.Rect()
                    sendNode.getBoundsInScreen(rect)
                    tap(rect.centerX().toFloat(), rect.centerY().toFloat())
                } else {
                    throw IllegalStateException("Impossible de cliquer sur envoyer")
                }
            }
            Thread.sleep(500)
        } finally {
            root.recycle()
        }
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byId = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        if (!byId.isNullOrEmpty()) return byId.first()

        val byDesc = root.findAccessibilityNodeInfosByText("Envoyer")
        if (!byDesc.isNullOrEmpty()) return byDesc.first()

        val byDescEn = root.findAccessibilityNodeInfosByText("Send")
        if (!byDescEn.isNullOrEmpty()) return byDescEn.first()

        return findNodeRecursive(root) { node ->
            val cls = node.className?.toString() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            node.isClickable && (
                cls.contains("ImageButton") || cls.contains("Button")
                ) && (
                desc.contains("send") || desc.contains("envoyer") ||
                    node.viewIdResourceName?.contains("send") == true
                )
        }
    }

    private fun findNodeRecursive(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeRecursive(child, predicate)
            if (found != null) return found
        }
        return null
    }

    private fun tap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }
}

object AccessibilitySettingsHelper {
    fun openSettings(context: android.content.Context) {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
