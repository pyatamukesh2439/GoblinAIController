package com.goblin.aicontroller.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goblin.aicontroller.model.AiSimulationState
import com.goblin.aicontroller.model.CaptureMetrics
import com.goblin.aicontroller.model.ControllerStatus
import com.goblin.aicontroller.model.LogEntry

@Composable
fun MainScreen(
    status: ControllerStatus,
    metrics: CaptureMetrics,
    simState: AiSimulationState,
    decisionsCount: Int,
    elapsedSeconds: Long,
    logs: List<LogEntry>,
    hasOverlayPermission: Boolean,
    onGrantOverlayClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Goblin AI Controller",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Native Automation & Vision Testing",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = when (status) {
                        ControllerStatus.RUNNING -> Color(0xFF1B5E20)
                        ControllerStatus.STOPPED -> Color(0xFF424242)
                        ControllerStatus.PERMISSION_REQUIRED -> Color(0xFFE65100)
                    }
                ) {
                    Text(
                        text = status.displayName,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!hasOverlayPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF332020))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Permission Required",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Display Over Other Apps is needed to show the movable floating HUD.",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onGrantOverlayClick,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartClick,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                    enabled = status != ControllerStatus.RUNNING
                ) {
                    Text("START", fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = onStopClick,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                    enabled = status == ControllerStatus.RUNNING
                ) {
                    Text("STOP", fontWeight = FontWeight.Bold, color = Color.White)
                }

                OutlinedButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("SETTINGS")
                }
            }

            SimulationSection(
                simulationState = simState,
                decisionsCount = decisionsCount,
                elapsedSeconds = elapsedSeconds,
                metrics = metrics
            )

            Text("Activity Log", style = MaterialTheme.typography.titleMedium, color = Color.White)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                shape = RoundedCornerShape(8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    reverseLayout = true
                ) {
                    items(logs.reversed(), key = { it.id }) { log ->
                        Text(
                            text = "[${log.timestamp}] ${log.message}",
                            fontSize = 11.sp,
                            color = Color(0xFFB0BEC5),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
