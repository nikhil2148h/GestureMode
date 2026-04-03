package com.example.gesturemode

import android.graphics.PointF
import android.media.AudioManager
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.max

class GestureProcessor(
    private val overlayManager: OverlayManager,
    private val audioManager: AudioManager
) {
    private var lastCursorPos = PointF(SystemController.screenWidth / 2f, SystemController.screenHeight / 2f)
    private val smoothingFactor = 0.3f // EMA smoothing
    
    private var lastScrollY = 0f
    private var lastScrollTime = 0L

    private var lastVolumeY = 0f
    private var volumeModeActive = false

    private var lastHomeTime = 0L

    fun processLandmarks(landmarks: List<NormalizedLandmark>) {
        if (landmarks.size < 21) return

        // 1. Identify Hand pose
        val thumbExtended = isThumbExtended(landmarks)
        val indexExtended = isFingerExtended(landmarks, 8, 6, 5)
        val middleExtended = isFingerExtended(landmarks, 12, 10, 9)
        val ringExtended = isFingerExtended(landmarks, 16, 14, 13)
        val pinkyExtended = isFingerExtended(landmarks, 20, 18, 17)

        val extendedCount = listOf(indexExtended, middleExtended, ringExtended, pinkyExtended).count { it }

        // Mappings:
        // Gesture 1: Mouse Cursor (Only Index)
        if (indexExtended && !middleExtended && !ringExtended && !pinkyExtended && !thumbExtended) {
            handleCursor(landmarks[8])
            return
        }

        // Gesture 2: Quick Reel Scroll (4 fingers extended, thumb can be anywhere)
        if (extendedCount == 4) {
            handleScroll(landmarks)
            return
        }

        // Gesture 3: Volume Control (Thumb + Index separated)
        if (thumbExtended && indexExtended && !middleExtended && !ringExtended && !pinkyExtended) {
            val dist = distance(landmarks[4], landmarks[8])
            if (dist > 0.05f) { // separated
                handleVolume(landmarks[8])
                return
            }
        }
        volumeModeActive = false

        // Gesture 4: Minimize/Split Screen (5 Fingers + upward motion)
        if (thumbExtended && extendedCount == 4) {
            handleHome(landmarks)
            return
        }
    }

    private fun handleCursor(indexTip: NormalizedLandmark) {
        val targetX = indexTip.x() * SystemController.screenWidth
        val targetY = indexTip.y() * SystemController.screenHeight
        
        val newX = lastCursorPos.x + smoothingFactor * (targetX - lastCursorPos.x)
        val newY = lastCursorPos.y + smoothingFactor * (targetY - lastCursorPos.y)
        
        lastCursorPos.set(newX, newY)
        overlayManager.updateCursorPosition(newX, newY)
    }

    private fun handleScroll(landmarks: List<NormalizedLandmark>) {
        // Average Y of tips
        val avgY = (landmarks[8].y() + landmarks[12].y() + landmarks[16].y() + landmarks[20].y()) / 4f
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastScrollTime > 500) { // debounce
            if (lastScrollY != 0f) {
                val deltaY = avgY - lastScrollY
                if (deltaY < -0.1f) { 
                    // swipe up -> scroll down
                    SystemController.scroll(false)
                    lastScrollTime = currentTime
                } else if (deltaY > 0.1f) {
                    // swipe down -> scroll up
                    SystemController.scroll(true)
                    lastScrollTime = currentTime
                }
            }
        }
        lastScrollY = avgY
    }

    private fun handleVolume(indexTip: NormalizedLandmark) {
        if (!volumeModeActive) {
            volumeModeActive = true
            lastVolumeY = indexTip.y()
            return
        }
        
        val deltaY = indexTip.y() - lastVolumeY
        if (abs(deltaY) > 0.05f) { // threshold
            if (deltaY < 0) { // moving up -> volume up
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            } else { // moving down -> volume down
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
            lastVolumeY = indexTip.y()
        }
    }

    private fun handleHome(landmarks: List<NormalizedLandmark>) {
        val avgY = (landmarks[4].y() + landmarks[8].y() + landmarks[12].y() + landmarks[16].y() + landmarks[20].y()) / 5f
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastHomeTime > 1000) {
            if (lastScrollY != 0f) {
                val deltaY = avgY - lastScrollY
                if (deltaY < -0.15f) { // rapid upward movement
                    SystemController.performGlobalHome()
                    lastHomeTime = currentTime
                }
            }
        }
        lastScrollY = avgY
    }

    private fun isFingerExtended(landmarks: List<NormalizedLandmark>, tip: Int, pip: Int, mcp: Int): Boolean {
        return landmarks[tip].y() < landmarks[pip].y() && landmarks[pip].y() < landmarks[mcp].y()
    }

    private fun isThumbExtended(landmarks: List<NormalizedLandmark>): Boolean {
        // Simple logic: thumb tip further horizontally than mcm
        val tip = landmarks[4]
        val mcp = landmarks[2]
        return abs(tip.x() - mcp.x()) > 0.05f
    }

    private fun distance(lm1: NormalizedLandmark, lm2: NormalizedLandmark): Float {
        val dx = lm1.x() - lm2.x()
        val dy = lm1.y() - lm2.y()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
