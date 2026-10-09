package com.android.blemonitor.repository

import com.android.blemonitor.data.model.CapturedAccessibilityEvent
import com.android.blemonitor.service.AppAccessibilityService
import kotlinx.coroutines.flow.StateFlow


class AccessibilityRepository {
    val isServiceRunning: StateFlow<Boolean> = AppAccessibilityService.isServiceRunning
    val events: StateFlow<List<CapturedAccessibilityEvent>> = AppAccessibilityService.accessibilityEvents

    fun clearEvents() {
        AppAccessibilityService.clearCapturedEvents()
    }

}