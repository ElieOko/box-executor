package com.appbox.runtime.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
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

        private val SEND_VIEW_IDS = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp:id/conversation_entry_action_button",
            "com.whatsapp.w4b:id/send",
            "com.whatsapp.w4b:id/conversation_entry_action_button",
        )

        suspend fun sendWhatsAppMessage(timeoutMs: Long = 20_000): Result<Unit> = runCatching {
            val service = instance ?: throw IllegalStateException(
                "Activez HOSHI dans Paramètres → Accessibilité pour l'envoi automatique WhatsApp",
            )
            val deadline = System.currentTimeMillis() + timeoutMs
            var lastError = "Bouton envoyer introuvable"
            while (System.currentTimeMillis() < deadline) {
                val result = service.tryTapSendButton()
                if (result.isSuccess) return@runCatching
                lastError = result.exceptionOrNull()?.message ?: lastError
                delay(600)
            }
            throw IllegalStateException(lastError)
        }
    }

    private fun tryTapSendButton(): Result<Unit> = runCatching {
        val root = rootInActiveWindow ?: throw IllegalStateException("Fenêtre WhatsApp introuvable")
        try {
            val sendNode = findSendButton(root)
                ?: throw IllegalStateException("Bouton envoyer introuvable")

            val clicked = sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (!clicked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val rect = Rect()
                sendNode.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    tap(rect.centerX().toFloat(), rect.centerY().toFloat())
                } else {
                    throw IllegalStateException("Zone envoyer invalide")
                }
            } else if (!clicked) {
                throw IllegalStateException("Impossible de cliquer sur envoyer")
            }
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (viewId in SEND_VIEW_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                val enabled = nodes.firstOrNull { it.isEnabled && it.isVisibleToUser }
                if (enabled != null) return enabled
                return nodes.first()
            }
        }

        listOf("Envoyer", "Send", "Send message").forEach { label ->
            val nodes = root.findAccessibilityNodeInfosByText(label)
            val match = nodes?.firstOrNull { it.isClickable && it.isEnabled }
            if (match != null) return match
        }

        return findNodeRecursive(root) { node ->
            val id = node.viewIdResourceName?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            node.isClickable && node.isEnabled && (
                id.contains("send") || desc.contains("send") || desc.contains("envoyer")
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
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
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
