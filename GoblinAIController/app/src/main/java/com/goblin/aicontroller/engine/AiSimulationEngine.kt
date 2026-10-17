package com.goblin.aicontroller.engine

import com.goblin.aicontroller.model.AiSimulationState
import com.goblin.aicontroller.state.ControllerStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AiSimulationEngine(private val scope: CoroutineScope) {
    private var job: Job? = null
    private var decisions = 0
    private var elapsedSeconds = 0L

    fun start() {
        if (job?.isActive == true) return
        decisions = 0
        elapsedSeconds = 0L
        ControllerStateRepository.addLog("AI Simulation Engine active. (Simulated test mode)")

        job = scope.launch(Dispatchers.Default) {
            val ticker = launch {
                while (isActive) {
                    delay(1000L)
                    elapsedSeconds++
                    ControllerStateRepository.updateSimulation(
                        state = ControllerStateRepository.simulationState.value,
                        decisions = decisions,
                        elapsed = elapsedSeconds
                    )
                }
            }

            var currentState = AiSimulationState.SCANNING
            while (isActive) {
                ControllerStateRepository.updateSimulation(currentState, decisions, elapsedSeconds)
                when (currentState) {
                    AiSimulationState.IDLE -> {
                        delay(500)
                        currentState = AiSimulationState.SCANNING
                    }
                    AiSimulationState.SCANNING -> {
                        delay(1200)
                        currentState = AiSimulationState.TARGET_IDENTIFIED
                    }
                    AiSimulationState.TARGET_IDENTIFIED -> {
                        delay(800)
                        currentState = AiSimulationState.CALCULATING_TACTIC
                    }
                    AiSimulationState.CALCULATING_TACTIC -> {
                        delay(900)
                        decisions++
                        currentState = AiSimulationState.ACTION_EXECUTED
                    }
                    AiSimulationState.ACTION_EXECUTED -> {
                        delay(1000)
                        currentState = AiSimulationState.SCANNING
                    }
                }
            }
            ticker.cancel()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        ControllerStateRepository.resetSimulation()
        ControllerStateRepository.addLog("AI Simulation Engine stopped.")
    }
}
