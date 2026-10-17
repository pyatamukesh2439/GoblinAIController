package com.goblin.aicontroller.model

enum class ControllerStatus(val displayName: String) {
    STOPPED("Stopped"),
    RUNNING("Running"),
    PERMISSION_REQUIRED("Permission Required")
}

enum class AiSimulationState(val label: String) {
    IDLE("Idle / Waiting"),
    SCANNING("Scanning Screen Matrix"),
    TARGET_IDENTIFIED("Target Goblin Spotted"),
    CALCULATING_TACTIC("Calculating Decision"),
    ACTION_EXECUTED("Action Dispatched (Simulated)")
}

data class CaptureMetrics(
    val frameCount: Long = 0L,
    val resolution: String = "0x0",
    val lastFps: Double = 0.0,
    val isCapturing: Boolean = false
)

data class LogEntry(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: String,
    val message: String
)
