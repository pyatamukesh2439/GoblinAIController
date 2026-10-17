package com.goblin.aicontroller.capture

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.goblin.aicontroller.model.CaptureMetrics
import com.goblin.aicontroller.state.ControllerStateRepository

class ScreenCaptureManager(private val context: Context) {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    private var frameCount: Long = 0L
    private var startTime: Long = 0L

    fun startCapture(projection: MediaProjection) {
        stopCapture()
        this.mediaProjection = projection

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val width = (metrics.widthPixels / 2).coerceAtLeast(360)
        val height = (metrics.heightPixels / 2).coerceAtLeast(640)
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
            "GoblinCaptureStream",
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
                frameCount++
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
                image.close()
            }
        }, handler)

        ControllerStateRepository.addLog("Capture initialized: ${width}x${height} @ ${density}dpi.")
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
