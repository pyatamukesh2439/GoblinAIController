package com.goblin.aicontroller.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.goblin.aicontroller.model.AiSimulationState
import com.goblin.aicontroller.model.CaptureMetrics

@Composable
fun SimulationSection(
    simulationState: AiSimulationState,
    decisionsCount: Int,
    elapsedSeconds: Long,
    metrics: CaptureMetrics
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "AI State Machine Demo (Isolated Test)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "This mode tests decision loops safely without injecting touches into other applications.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Current State:", color = Color.Gray)
                Text(
                    text = simulationState.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Decisions Made:", color = Color.Gray)
                Text(text = "$decisionsCount", color = Color.White)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Elapsed Test Time:", color = Color.Gray)
                Text(text = "${elapsedSeconds}s", color = Color.White)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Capture Rate / FPS:", color = Color.Gray)
                Text(text = "${metrics.frameCount} frames (${metrics.lastFps} fps)", color = Color.White)
            }
        }
    }
}
