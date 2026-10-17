package com.goblin.aicontroller.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.goblin.aicontroller.MainActivity
import com.goblin.aicontroller.R
import com.goblin.aicontroller.capture.ScreenCaptureManager
import com.goblin.aicontroller.engine.AiSimulationEngine
import com.goblin.aicontroller.model.ControllerStatus
import com.goblin.aicontroller.state.ControllerStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GoblinAccessibilityService : AccessibilityService() {

    companion object {
        var instance: GoblinAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun tap(x: Float, y: Float, durationMs: Long = 50L) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 260L) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }
}

class OverlayService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var screenCaptureManager: ScreenCaptureManager? = null
    private var aiSimulationEngine: AiSimulationEngine? = null
    private var autoPilotJob: Job? = null

    // UI elements with live status updates
    private var btnAiPlay: Button? = null
    private var tvStatusIndicator: TextView? = null

    companion object {
        const val CHANNEL_ID = "goblin_overlay_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA_INTENT = "EXTRA_DATA_INTENT"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        screenCaptureManager = ScreenCaptureManager(this)
        aiSimulationEngine = AiSimulationEngine(serviceScope)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("Goblin Vision Pilot Ready"))
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val dataIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA_INTENT)
                }

                if (resultCode != 0 && dataIntent != null) {
                    val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val projection: MediaProjection = mediaProjectionManager.getMediaProjection(resultCode, dataIntent)
                    screenCaptureManager?.startCapture(projection)
                }

                showFloatingOverlay()
                ControllerStateRepository.setStatus(ControllerStatus.STOPPED)
            }
            ACTION_STOP -> {
                stopController()
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingOverlay() {
        if (overlayView != null) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 140
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(14, 10, 14, 10)
            setBackgroundColor(Color.parseColor("#F0161616"))
            elevation = 16f
        }

        // Live Status Indicator Pill
        val statusIndicator = TextView(this).apply {
            text = "● OFF"
            setTextColor(Color.parseColor("#9E9E9E"))
            textSize = 12f
            setPadding(8, 0, 14, 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        tvStatusIndicator = statusIndicator

        // Dynamic State Button
        val playButton = Button(this).apply {
            text = "AI PLAY"
            setBackgroundColor(Color.parseColor("#00C853"))
            setTextColor(Color.WHITE)
            textSize = 12f
            setOnClickListener {
                startAccurateVisionPilot()
            }
        }
        btnAiPlay = playButton

        val btnStop = Button(this).apply {
            text = "STOP"
            setBackgroundColor(Color.parseColor("#D50000"))
            setTextColor(Color.WHITE)
            textSize = 12f
            setOnClickListener {
                stopVisionPilotOnly()
            }
        }

        val btnSettings = Button(this).apply {
            text = "SETTINGS"
            setBackgroundColor(Color.parseColor("#455A64"))
            setTextColor(Color.WHITE)
            textSize = 12f
            setOnClickListener {
                val intent = Intent(this@OverlayService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("OPEN_SETTINGS", true)
                }
                startActivity(intent)
            }
        }

        container.addView(statusIndicator)
        container.addView(playButton)
        container.addView(btnStop)
        container.addView(btnSettings)

        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - touchStartX).toInt()
                    layoutParams.y = initialY + (event.rawY - touchStartY).toInt()
                    windowManager.updateViewLayout(container, layoutParams)
                    true
                }
                else -> false
            }
        }

        overlayView = container
        windowManager.addView(container, layoutParams)
    }

    private fun startAccurateVisionPilot() {
        val tapService = GoblinAccessibilityService.instance
        if (tapService == null) {
            Toast.makeText(this, "Enable Goblin in Accessibility Settings!", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        }

        if (autoPilotJob?.isActive == true) {
            Toast.makeText(this, "AI is ALREADY ON and active! 🟢", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. UPDATE VISUAL INDICATOR TO "ACTIVE 🟢"
        tvStatusIndicator?.apply {
            text = "● ACTIVE"
            setTextColor(Color.parseColor("#00E676")) // Bright Glowing Green
        }

        // 2. CHANGE BUTTON COLOR & TEXT TO "RUNNING 🟢"
        btnAiPlay?.apply {
            text = "RUNNING 🟢"
            setBackgroundColor(Color.parseColor("#00B0FF")) // Electric Blue
        }

        Toast.makeText(this, "🟢 Goblin AI Vision Pilot is ON!", Toast.LENGTH_SHORT).show()
        aiSimulationEngine?.start()
        ControllerStateRepository.setStatus(ControllerStatus.RUNNING)

        autoPilotJob = serviceScope.launch(Dispatchers.Main) {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val screenW = metrics.widthPixels.toFloat()
            val screenH = metrics.heightPixels.toFloat()
            val sweepY = screenH * 0.65f

            var currentBalloonX = screenW * 0.5f

            while (isActive) {
                // Phase 1: Launch balloon if at landing dock
                tapService.tap(screenW * 0.5f, screenH * 0.55f)
                delay(300L)
                tapService.tap(screenW * 0.5f, screenH * 0.55f)
                delay(500L)

                // Phase 2: Active Flight Phase using Live Vision Coordinates
                for (flightStep in 0 until 24) {
                    if (!isActive) break

                    val vision = ScreenCaptureManager.latestVision
                    val targetX = screenW * vision.targetLaneXFraction

                    // Emergency Evasive Action: Thunderstorm detected
                    if (vision.isDangerAhead) {
                        val escapeX = if (currentBalloonX > screenW * 0.5f) screenW * 0.18f else screenW * 0.82f
                        tapService.swipe(currentBalloonX, sweepY, escapeX, sweepY, 180L)
                        currentBalloonX = escapeX
                        delay(220L)
                        continue
                    }

                    // Steer towards target treasure
                    if (Math.abs(targetX - currentBalloonX) > 40f) {
                        tapService.swipe(currentBalloonX, sweepY, targetX, sweepY, 260L)
                        currentBalloonX = targetX
                        delay(300L)
                    } else {
                        val microX = (currentBalloonX + (if (currentBalloonX < screenW * 0.5f) 70f else -70f)).coerceIn(screenW * 0.15f, screenW * 0.85f)
                        tapService.swipe(currentBalloonX, sweepY, microX, sweepY, 200L)
                        currentBalloonX = microX
                        delay(240L)
                    }
                }

                if (!isActive) break

                // Phase 3: Post-Flight: Claim rewards and upgrade Rope/Basket
                delay(1200L)
                tapService.tap(screenW * 0.5f, screenH * 0.72f) // Claim loot
                delay(500L)

                tapService.tap(screenW * 0.28f, screenH * 0.88f) // Upgrade basket
                delay(400L)
                tapService.tap(screenW * 0.72f, screenH * 0.88f) // Upgrade rope
                delay(500L)
            }
        }
    }

    // Stops the AI loop and resets UI back to IDLE without destroying the overlay window
    private fun stopVisionPilotOnly() {
        autoPilotJob?.cancel()
        autoPilotJob = null
        aiSimulationEngine?.stop()

        // RESET BUTTON & STATUS INDICATOR TO OFF
        tvStatusIndicator?.apply {
            text = "● OFF"
            setTextColor(Color.parseColor("#9E9E9E"))
        }
        btnAiPlay?.apply {
            text = "AI PLAY"
            setBackgroundColor(Color.parseColor("#00C853"))
        }

        ControllerStateRepository.setStatus(ControllerStatus.STOPPED)
        Toast.makeText(this, "🛑 Goblin AI Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    private fun stopController() {
        stopVisionPilotOnly()
        screenCaptureManager?.stopCapture()
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Goblin Controller Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Goblin AI Controller")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopController()
        serviceScope.cancel()
    }
}
