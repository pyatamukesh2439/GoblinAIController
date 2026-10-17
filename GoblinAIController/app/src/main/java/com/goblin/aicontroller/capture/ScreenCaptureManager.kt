package com.goblin.aicontroller.capture

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.goblin.aicontroller.model.CaptureMetrics
import com.goblin.aicontroller.state.ControllerStateRepository

data class VisionTarget(
    val targetLaneXFraction: Float = 0.5f,
    val isDangerAhead: Boolean = false,
    val treasureScore: Int = 0
)

class ScreenCaptureManager(private val context: Context) {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    private var frameCount: Long = 0L
    private var startTime: Long = 0L

    companion object {
        @Volatile
        var latestVision: VisionTarget = VisionTarget()
    }

    fun startCapture(projection: MediaProjection) {
        stopCapture()
        this.mediaProjection = projection

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        // Process at efficient downscaled 360x640 for ultra-fast <3ms computer vision
        val width = 360
        val height = 640
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        frameCount = 0L
        startTime = System.currentTimeMillis()

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                stopCapture()
                ControllerStateRepository.addLog("MediaProjection stream revoked.")
            }
        }, handler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "GoblinVisionStream",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    frameCount++
                    analyzeFrame(image, width, height)

                    val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                    val fps = if (elapsedSec > 0) String.format("%.1f", frameCount / elapsedSec).toDouble() else 0.0

                    ControllerStateRepository.updateMetrics(
                        CaptureMetrics(
                            frameCount = frameCount,
                            resolution = "${width}x${height}",
                            lastFps = fps,
                            isCapturing = true
                        )
                    )
                } catch (_: Exception) {
                } finally {
                    image.close()
                }
            }
        }, handler)

        ControllerStateRepository.addLog("Vision Pipeline active: Scanning treasures & storm hazards.")
    }

    /**
     * Real-Time Pixel Vision:
     * Scans incoming sky layer for Coins (Gold), Gems (Cyan/Purple), and Storm Clouds (Dark Grey/Lightning)
     */
    private fun analyzeFrame(image: Image, width: Int, height: Int) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        // Scan detection window (approaching objects in the upper-mid sky: 30% to 65% screen height)
        val startY = (height * 0.30f).toInt()
        val endY = (height * 0.65f).toInt()

        // 5 Vertical Lanes: 0=FarLeft, 1=MidLeft, 2=Center, 3=MidRight, 4=FarRight
        val treasureScores = IntArray(5)
        val stormScores = IntArray(5)

        val stepY = 6 // Sample every 6th pixel for maximum speed
        val stepX = 4

        for (y in startY until endY step stepY) {
            val rowOffset = y * rowStride
            for (x in 0 until width step stepX) {
                val offset = rowOffset + (x * pixelStride)
                if (offset + 3 >= buffer.limit()) continue

                val r = buffer.get(offset).toInt() and 0xFF
                val g = buffer.get(offset + 1).toInt() and 0xFF
                val b = buffer.get(offset + 2).toInt() and 0xFF

                val lane = (x.toFloat() / width * 5).toInt().coerceIn(0, 4)

                // 1. Detect Gold Coins / Treasure Chests (High Red + Green, Low Blue)
                val isGold = (r > 170 && g > 130 && b < 100)
                // 2. Detect Diamonds & Gems (Cyan / Ruby)
                val isGem = (b > 180 && g > 170 && r < 120) || (r > 180 && g < 100 && b > 140)

                if (isGold || isGem) {
                    treasureScores[lane] += if (isGem) 2 else 1
                }

                // 3. Detect Thunderstorm Clouds & Obstacles (Dark purple/grey clouds, distinct from light sky)
                val isStormCloud = (r < 75 && g < 75 && b < 90)
                if (isStormCloud) {
                    stormScores[lane] += 1
                }
            }
        }

        // Find lane with best treasure that has NO thunderstorm
        var bestLane = 2 // default center
        var maxScore = -1

        for (lane in 0 until 5) {
            // If lane has dangerous storm cloud, avoid it!
            if (stormScores[lane] > 25) continue

            if (treasureScores[lane] > maxScore) {
                maxScore = treasureScores[lane]
                bestLane = lane
            }
        }

        // Convert 5 lanes to screen X fraction (0.15, 0.32, 0.50, 0.68, 0.85)
        val targetXFraction = when (bestLane) {
            0 -> 0.15f
            1 -> 0.32f
            2 -> 0.50f
            3 -> 0.68f
            else -> 0.85f
        }

        val isDanger = stormScores[bestLane] > 20

        latestVision = VisionTarget(
            targetLaneXFraction = targetXFraction,
            isDangerAhead = isDanger,
            treasureScore = maxScore
        )
    }

    fun stopCapture() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
        } catch (_: Exception) { }

        ControllerStateRepository.updateMetrics(CaptureMetrics())
    }
}
