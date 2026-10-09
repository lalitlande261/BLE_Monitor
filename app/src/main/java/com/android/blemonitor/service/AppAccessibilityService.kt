package com.android.blemonitor.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.android.blemonitor.data.model.CapturedAccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceRunning.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.isPassword) return

        val captured = CapturedAccessibilityEvent(
            packageName = event.packageName?.toString() ?: "Unknown",
            eventType = AccessibilityEvent.eventTypeToString(event.eventType),
            visibleText = event.text
                .joinToString(" ")
                .take(200),
            contentDescription = event.contentDescription
                ?.toString()
                ?.take(200)
                .orEmpty(),
            className = event.className?.toString() ?: "Unknown"
        )

        // Keep only the latest 30 events.
        val current = _accessibilityEvents.value

        // Avoid adding consecutive duplicate events.
        if (current.firstOrNull()?.let {
                it.packageName == captured.packageName &&
                        it.eventType == captured.eventType &&
                        it.visibleText == captured.visibleText &&
                        it.className == captured.className
            } == true
        ) {
            return
        }

        _accessibilityEvents.value =
            (listOf(captured) + current).take(50)
    }

    override fun onInterrupt() {

    }

    private fun extractNodeInfo(
        node: AccessibilityNodeInfo
    ): String {
        val descriptions = mutableListOf<String>()

        node.contentDescription?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let { descriptions.add(it) }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue

            try {
                val childDescription = extractNodeInfo(child)
                if (childDescription.isNotBlank()) {
                    descriptions.add(childDescription)
                }
            } finally {
                child.recycle()
            }


            if (descriptions.sumOf { it.length } >= 500) break
        }

        return descriptions.joinToString("; ").take(500)
    }



    override fun onDestroy() {
        _isServiceRunning.value = false
        super.onDestroy()
    }

    companion object {
        private val _accessibilityEvents =
            MutableStateFlow<List<CapturedAccessibilityEvent>>(emptyList())

        val accessibilityEvents: StateFlow<List<CapturedAccessibilityEvent>> =
            _accessibilityEvents.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)

        val isServiceRunning: StateFlow<Boolean> =
            _isServiceRunning.asStateFlow()


        fun clearCapturedEvents() {
            _accessibilityEvents.value = emptyList()
        }
    }
}