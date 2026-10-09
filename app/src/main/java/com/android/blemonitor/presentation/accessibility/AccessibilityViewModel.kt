package com.android.blemonitor.presentation.accessibility


import androidx.lifecycle.ViewModel
import com.android.blemonitor.data.model.CapturedAccessibilityEvent
import com.android.blemonitor.repository.AccessibilityRepository

import kotlinx.coroutines.flow.StateFlow

class AccessibilityViewModel(private val repository: AccessibilityRepository) : ViewModel() {
    val isServiceRunning: StateFlow<Boolean> = repository.isServiceRunning
    val events: StateFlow<List<CapturedAccessibilityEvent>> = repository.events

    fun clearEvents() {
        repository.clearEvents()
    }
}