package com.android.blemonitor.data.model


data class CapturedAccessibilityEvent(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val eventType: String,
    val visibleText: String,
    val contentDescription: String,
    val className: String
)