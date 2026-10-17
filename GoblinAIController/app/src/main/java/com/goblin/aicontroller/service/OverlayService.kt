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
            y = 150
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.parseColor("#EE181818"))
            elevation = 16f
        }

        // Dedicated Grab-and-Drag Handle
        val dragHandle = TextView(this).apply {
            text = "✥ MOVE"
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 12f
            setPadding(12, 10, 12, 10)
            setBackgroundColor(Color.parseColor("#2C2C2C"))
            gravity = Gravity.CENTER
        }

        // Live Status Text
        val statusIndicator = TextView(this).apply {
            text = "● OFF"
            setTextColor(Color.parseColor("#9E9E9E"))
            textSize = 12f
            setPadding(10, 0, 10, 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        tvStatusIndicator = statusIndicator

        // AI PLAY / RUNNING Button
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

        // CLOSE BUTTON: Completely removes the floating window and stops everything
        val btnClose = Button(this).apply {
            text = "CLOSE ✕"
            setBackgroundColor(Color.parseColor("#D50000"))
            setTextColor(Color.WHITE)
            textSize = 12f
            setOnClickListener {
                stopController() // Fully destroys and removes overlay from screen!
                Toast.makeText(this@OverlayService, "Goblin Controller Closed ✕", Toast.LENGTH_SHORT).show()
            }
        }

        // SETTINGS Button
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

        container.addView(dragHandle)
        container.addView(statusIndicator)
        container.addView(playButton)
        container.addView(btnClose)
        container.addView(btnSettings)

        // Smooth Drag Listener attached to the Drag Handle, Status text, and Container
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        val dragTouchListener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = (initialX + (event.rawX - touchStartX)).toInt()
                    layoutParams.y = (initialY + (event.rawY - touchStartY)).toInt()
                    try {
                        windowManager.updateViewLayout(container, layoutParams)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        dragHandle.setOnTouchListener(dragTouchListener)
        statusIndicator.setOnTouchListener(dragTouchListener)
        container.setOnTouchListener(dragTouchListener)

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
            Toast.makeText(this, "AI is ALREADY ON! 🟢", Toast.LENGTH_SHORT).show()
            return
        }

        tvStatusIndicator?.apply {
            text = "● ACTIVE"
            setTextColor(Color.parseColor("#00E676"))
        }

        btnAiPlay?.apply {
            text = "RUNNING 🟢"
            setBackgroundColor(Color.parseColor("#00B0FF"))
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
                // 1. Launch balloon if at landing dock
                tapService.tap(screenW * 0.5f, screenH * 0.55f)
                delay(300L)
                tapService.tap(screenW * 0.5f, screenH * 0.55f)
                delay(500L)

                // 2. Active Flight: Steer dynamically using live vision
                for (flightStep in 0 until 24) {
                    if (!isActive) break

                    val vision = ScreenCaptureManager.latestVision
                    val targetX = screenW * vision.targetLaneXFraction

                    // Emergency Evasive Action if storm cloud ahead
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

                // 3. Post-Flight: Claim rewards and upgrade
                delay(1200L)
                tapService.tap(screenW * 0.5f, screenH * 0.72f) // Claim
                delay(500L)

                tapService.tap(screenW * 0.28f, screenH * 0.88f) // Upgrade basket
                delay(400L)
                tapService.tap(screenW * 0.72f, screenH * 0.88f) // Upgrade rope
                delay(500L)
            }
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    // Completely stops AI, removes overlay view, and shuts down service
    private fun stopController() {
        autoPilotJob?.cancel()
        autoPilotJob = null
        aiSimulationEngine?.stop()
        screenCaptureManager?.stopCapture()
        removeOverlay()
        ControllerStateRepository.setStatus(ControllerStatus.STOPPED)
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
