package com.goblin.aicontroller.state

import com.goblin.aicontroller.model.AiSimulationState
import com.goblin.aicontroller.model.CaptureMetrics
import com.goblin.aicontroller.model.ControllerStatus
import com.goblin.aicontroller.model.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ControllerStateRepository {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _status = MutableStateFlow(ControllerStatus.STOPPED)
    val status: StateFlow<ControllerStatus> = _status.asStateFlow()

    private val _captureMetrics = MutableStateFlow(CaptureMetrics())
    val captureMetrics: StateFlow<CaptureMetrics> = _captureMetrics.asStateFlow()

    private val _simulationState = MutableStateFlow(AiSimulationState.IDLE)
    val simulationState: StateFlow<AiSimulationState> = _simulationState.asStateFlow()

    private val _decisionCount = MutableStateFlow(0)
    val decisionCount: StateFlow<Int> = _decisionCount.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(
        listOf(LogEntry(timestamp = timeFormat.format(Date()), message = "System initialized and ready."))
    )
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun setStatus(newStatus: ControllerStatus) {
        _status.value = newStatus
        addLog("Status changed to ${newStatus.displayName}")
    }

    fun updateMetrics(metrics: CaptureMetrics) {
        _captureMetrics.value = metrics
    }

    fun updateSimulation(state: AiSimulationState, decisions: Int, elapsed: Long) {
        _simulationState.value = state
        _decisionCount.value = decisions
        _elapsedSeconds.value = elapsed
    }

    fun addLog(message: String) {
        val entry = LogEntry(timestamp = timeFormat.format(Date()), message = message)
        _logs.value = (_logs.value + entry).takeLast(40)
    }

    fun resetSimulation() {
        _simulationState.value = AiSimulationState.IDLE
        _decisionCount.value = 0
        _elapsedSeconds.value = 0L
    }
}
