package com.goblin.aicontroller

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.goblin.aicontroller.model.ControllerStatus
import com.goblin.aicontroller.service.OverlayService
import com.goblin.aicontroller.state.ControllerStateRepository
import com.goblin.aicontroller.ui.MainScreen
import com.goblin.aicontroller.ui.SettingsDialog
import com.goblin.aicontroller.ui.theme.GoblinTheme

class MainActivity : ComponentActivity() {

    private var isSettingsOpen by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ControllerStateRepository.addLog("Screen capture permission granted.")
            startOverlayService(result.resultCode, result.data!!)
        } else {
            ControllerStateRepository.addLog("Screen capture consent denied.")
            ControllerStateRepository.setStatus(ControllerStatus.STOPPED)
            Toast.makeText(this, "Screen capture permission required.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            hasOverlayPermission = Settings.canDrawOverlays(this)
        } catch (_: Exception) {}

        handleIntentExtras(intent)

        setContent {
            GoblinTheme {
                val status by ControllerStateRepository.status.collectAsState()
                val metrics by ControllerStateRepository.captureMetrics.collectAsState()
                val simState by ControllerStateRepository.simulationState.collectAsState()
                val decisionsCount by ControllerStateRepository.decisionCount.collectAsState()
                val elapsedSeconds by ControllerStateRepository.elapsedSeconds.collectAsState()
                val logs by ControllerStateRepository.logs.collectAsState()

                MainScreen(
                    status = status,
                    metrics = metrics,
                    simState = simState,
                    decisionsCount = decisionsCount,
                    elapsedSeconds = elapsedSeconds,
                    logs = logs,
                    hasOverlayPermission = hasOverlayPermission,
                    onGrantOverlayClick = { requestOverlayPermission() },
                    onStartClick = { handleStartFlow() },
                    onStopClick = { handleStopFlow() },
                    onSettingsClick = { isSettingsOpen = true }
                )

                SettingsDialog(
                    isOpen = isSettingsOpen,
                    onDismiss = { isSettingsOpen = false }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent?) {
        if (intent?.getBooleanExtra("OPEN_SETTINGS", false) == true) {
            isSettingsOpen = true
        }
    }

    private fun checkPermissions() {
        try {
            hasOverlayPermission = Settings.canDrawOverlays(this)
            if (!hasOverlayPermission && ControllerStateRepository.status.value != ControllerStatus.RUNNING) {
                ControllerStateRepository.setStatus(ControllerStatus.PERMISSION_REQUIRED)
            } else if (hasOverlayPermission && ControllerStateRepository.status.value == ControllerStatus.PERMISSION_REQUIRED) {
                ControllerStateRepository.setStatus(ControllerStatus.STOPPED)
            }
        } catch (_: Exception) {}
    }

    private fun requestOverlayPermission() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open settings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleStartFlow() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant overlay permission first.", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }

        try {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            screenCaptureLauncher.launch(captureIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Screen capture error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startOverlayService(resultCode: Int, data: Intent) {
        try {
            val serviceIntent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
                putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
                putExtra(OverlayService.EXTRA_DATA_INTENT, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start service: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleStopFlow() {
        try {
            val stopIntent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_STOP
            }
            startService(stopIntent)
        } catch (_: Exception) {}
    }
}
