package com.waph1.glance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs
import kotlin.math.exp

class WaveSideBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val letters = ('A'..'Z').toList() + '#'
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private var itemHeight = 0f
    private var currentTouchY = -1f
    
    // Animation state
    private var waveIntensity = 0f // 0f = no wave, 1f = full wave

    // Animation constants
    private val maxTranslationX = 150f // Maximum move to the left
    private val maxScale = 2.5f // Maximum scale up
    private val influenceRadius = 200f // Radius of influence for the wave
    private val sigma = influenceRadius / 3f // Standard deviation for Gaussian

    // Haptics
    var isHapticEnabled = true
    private var lastSelectedLetter: String? = null

    // Visuals
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444444") // Dark gray for bubble
        style = Paint.Style.FILL
    }

    // Spring Animation for wave intensity (reset)
    private val intensitySpring = SpringAnimation(androidx.dynamicanimation.animation.FloatValueHolder(0f))

    var onLetterSelected: ((String) -> Unit)? = null
    var onDoubleTap: (() -> Unit)? = null

    private val gestureDetector = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleTap?.invoke()
            return true
        }
    })

    init {
        intensitySpring.spring = SpringForce(0f).apply {
            stiffness = SpringForce.STIFFNESS_LOW
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
        intensitySpring.addUpdateListener { _, value, _ ->
            waveIntensity = value
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        itemHeight = h.toFloat() / letters.size
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val centerX = width - 60f // Base position from right edge

        // Find the index of the letter closest to the touch
        var closestIndex = -1
        if (waveIntensity > 0.01f) {
             closestIndex = (currentTouchY / itemHeight).toInt().coerceIn(0, letters.size - 1)
        }

        letters.forEachIndexed { index, char ->
            val itemCenterY = index * itemHeight + itemHeight / 2
            
            var translationX = 0f
            var scale = 1f

            // Only apply wave if intensity > 0
            if (waveIntensity > 0.01f) {
                val distance = abs(currentTouchY - itemCenterY)
                if (distance < influenceRadius) {
                    // Gaussian function
                    val factor = exp(-(distance * distance) / (2 * sigma * sigma))
                    
                    // Apply intensity to the factor
                    val effectiveFactor = factor * waveIntensity
                    
                    translationX = -maxTranslationX * effectiveFactor
                    scale = 1f + (maxScale - 1f) * effectiveFactor
                }
            }

            canvas.save()
            canvas.translate(centerX + translationX, itemCenterY)
            canvas.scale(scale, scale)
            
            // Draw bubble if this is the closest letter and wave is active
            if (index == closestIndex && waveIntensity > 0.5f) {
                // Draw circle behind text
                // Radius should be slightly larger than text size
                val radius = 50f 
                canvas.drawCircle(0f, 0f, radius, bubblePaint)
            }

            // Draw text centered
            val textHeight = paint.descent() - paint.ascent()
            val textOffset = (textHeight / 2) - paint.descent()
            
            canvas.drawText(char.toString(), 0f, textOffset, paint)
            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Only accept touches on the right side (e.g., last 200dp)
                // Since width is now 200dp to allow drawing, we must not block touches on the left.
                if (event.action == MotionEvent.ACTION_DOWN && event.x < width - 200f) {
                    return false
                }
                
                // Cancel any ongoing spring reset
                intensitySpring.cancel()
                
                // Set intensity to 1 immediately for responsiveness
                waveIntensity = 1f
                currentTouchY = event.y
                
                // Calculate selected letter
                val index = (currentTouchY / itemHeight).toInt().coerceIn(0, letters.size - 1)
                val selectedLetter = letters[index].toString()
                
                if (selectedLetter != lastSelectedLetter) {
                    if (isHapticEnabled) {
                        // Use CLOCK_TICK for a nice scrolling tick effect (API 21+)
                        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    }
                    onLetterSelected?.invoke(selectedLetter)
                    lastSelectedLetter = selectedLetter
                }
                
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Spring back to 0 intensity
                intensitySpring.animateToFinalPosition(0f)
                lastSelectedLetter = null
            }
        }
        return true
    }
}
