package com.example.gesturemode

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log

object SystemController {
    var accessibilityService: AccessibilityService? = null
    var screenWidth: Int = 1080
    var screenHeight: Int = 1920

    fun performGlobalHome() {
        accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun scroll(swipeUp: Boolean) {
        val service = accessibilityService ?: return
        
        val scrollDistance = screenHeight / 4f
        val startY = screenHeight / 2f
        val endY = if (swipeUp) startY - scrollDistance else startY + scrollDistance
        
        val path = Path()
        path.moveTo(screenWidth / 2f, startY)
        path.lineTo(screenWidth / 2f, endY)
        
        val stroke = GestureDescription.StrokeDescription(path, 0, 200)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        
        val result = service.dispatchGesture(gesture, null, null)
        Log.d("SystemController", "Scrolled ${if (swipeUp) "Up" else "Down"}, result: $result")
    }
}
