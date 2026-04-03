package com.example.gesturemode

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class GestureAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("GestureAccessService", "Accessibility Service Connected")
        SystemController.accessibilityService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We do not need to process window changes strictly, 
        // we just need the service to be alive to perform actions.
    }

    override fun onInterrupt() {
        Log.d("GestureAccessService", "Accessibility Service Interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        SystemController.accessibilityService = null
        return super.onUnbind(intent)
    }
}
