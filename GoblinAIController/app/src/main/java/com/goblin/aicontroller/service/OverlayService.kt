package com.goblin.aicontroller.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import android.widget.Button
import android.widget.LinearLayout
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

class OverlayService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var screenCaptureManager: ScreenCaptureManager? = null
    private var aiSimulationEngine: AiSimulationEngine? = null
    private var autoTapJob: Job? = null

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
                startForeground(NOTIFICATION_ID, buildNotification("Goblin Overlay Active"))
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
                aiSimulationEngine?.start()
                ControllerStateRepository.setStatus(ControllerStatus.RUNNING)
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
            x = 80
            y = 150
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.parseColor("#EE1B1B1B"))
            elevation = 16f
        }

        val btnAiPlay = Button(this).apply {
            text = "AI PLAY"
            setBackgroundColor(Color.parseColor("#00C853"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                startGoblinHeistPilot()
            }
        }

        val btnStop = Button(this).apply {
            text = "STOP"
            setBackgroundColor(Color.parseColor("#D50000"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                stopController()
            }
        }

        val btnSettings = Button(this).apply {
            text = "SETTINGS"
            setBackgroundColor(Color.parseColor("#455A64"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val intent = Intent(this@OverlayService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("OPEN_SETTINGS", true)
                }
                startActivity(intent)
            }
        }

        container.addView(btnAiPlay)
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

    private fun startGoblinHeistPilot() {
        val tapService = GoblinAccessibilityService.instance
        if (tapService == null) {
            Toast.makeText(this, "Please enable Goblin Controller in Accessibility Settings!", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        }

        if (autoTapJob?.isActive == true) {
            Toast.makeText(this, "Goblin Pilot is already running!", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Goblin Heist Auto-Pilot Started!", Toast.LENGTH_SHORT).show()
        aiSimulationEngine?.start()
        ControllerStateRepository.setStatus(ControllerStatus.RUNNING)

        autoTapJob = serviceScope.launch(Dispatchers.Main) {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val screenW = metrics.widthPixels.toFloat()
            val screenH = metrics.heightPixels.toFloat()

            val sweepY = screenH * 0.65f
            val leftX = screenW * 0.15f
            val midLeftX = screenW * 0.35f
            val midRightX = screenW * 0.65f
            val rightX = screenW * 0.85f

            while (isActive) {
                // Phase 1: Tap to Launch the Balloon into the sky
                tapService.tap(screenW * 0.5f, screenH * 0.55f)
                delay(350L)
                tapService.tap(screenW * 0.5f, screenH * 0.55f)
                delay(600L)

                // Phase 2: Active Flight Looting Loop (Sweeps Left & Right to scoop all gems, coins, chests)
                for (step in 0 until 9) {
                    if (!isActive) break

                    // Sweep Left to Right
                    tapService.swipe(leftX, sweepY, rightX, sweepY, 420L)
                    delay(480L)

                    // Sweep Right to Left
                    tapService.swipe(rightX, sweepY, leftX, sweepY, 420L)
                    delay(480L)

                    // Quick center adjustment sweep
                    tapService.swipe(midLeftX, sweepY, midRightX, sweepY, 280L)
                    delay(320L)
                }

                if (!isActive) break

                // Phase 3: Round Ended — Claim Loot & Auto-Upgrade
                delay(1200L)
                // Tap center / claim button
                tapService.tap(screenW * 0.5f, screenH * 0.72f)
                delay(600L)

                // Tap Upgrade Basket Capacity (Left Upgrade Button)
                tapService.tap(screenW * 0.28f, screenH * 0.88f)
                delay(400L)

                // Tap Upgrade Rope Length (Right Upgrade Button)
                tapService.tap(screenW * 0.72f, screenH * 0.88f)
                delay(600L)
            }
        }
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
        autoTapJob?.cancel()
        autoTapJob = null
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
