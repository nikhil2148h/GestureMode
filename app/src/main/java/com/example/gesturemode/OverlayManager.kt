package com.example.gesturemode

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var isAdded = false

    // Views
    private var cameraPreviewView: HandOverlayView? = null
    private var cursorView: CursorView? = null

    fun showOverlays() {
        if (isAdded) return

        // 1. Camera Preview & Skeleton Overlay
        val cameraParams = WindowManager.LayoutParams(
            300, 400, // Small window
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        cameraParams.gravity = Gravity.BOTTOM or Gravity.END
        cameraParams.x = 20
        cameraParams.y = 20

        cameraPreviewView = HandOverlayView(context)
        windowManager.addView(cameraPreviewView, cameraParams)

        // 2. Cursor Overlay
        val cursorParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT, // Full screen size, transparent background
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        cursorParams.gravity = Gravity.TOP or Gravity.START

        cursorView = CursorView(context)
        windowManager.addView(cursorView, cursorParams)

        isAdded = true
    }

    fun updateHandLandmarks(landmarks: List<NormalizedLandmark>) {
        cameraPreviewView?.updateLandmarks(landmarks)
    }

    fun updateCursorPosition(x: Float, y: Float) {
        cursorView?.updatePosition(x, y)
    }

    fun hideOverlays() {
        if (!isAdded) return
        cameraPreviewView?.let { windowManager.removeView(it) }
        cursorView?.let { windowManager.removeView(it) }
        cameraPreviewView = null
        cursorView = null
        isAdded = false
    }
}

// Inner custom views for overlays
class HandOverlayView(context: Context) : View(context) {
    private var landmarks: List<NormalizedLandmark> = emptyList()
    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 5f
    }
    private val linePaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    fun updateLandmarks(newLandmarks: List<NormalizedLandmark>) {
        landmarks = newLandmarks
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.argb(100, 0, 0, 0)) // Semi-transparent black background

        if (landmarks.isEmpty()) return

        // Simple drawing of points
        for (landmark in landmarks) {
            val cx = landmark.x() * width
            val cy = landmark.y() * height
            canvas.drawCircle(cx, cy, 6f, paint)
        }
    }
}

class CursorView(context: Context) : View(context) {
    private var cursorPos = PointF(-1f, -1f)
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    fun updatePosition(x: Float, y: Float) {
        cursorPos.set(x, y)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cursorPos.x >= 0 && cursorPos.y >= 0) {
            canvas.drawCircle(cursorPos.x, cursorPos.y, 20f, paint)
            canvas.drawCircle(cursorPos.x, cursorPos.y, 20f, strokePaint)
        }
    }
}
