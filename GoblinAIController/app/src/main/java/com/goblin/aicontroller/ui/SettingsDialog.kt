package com.goblin.aicontroller.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var fpsLimit by remember { mutableStateOf(15f) }
    var autoDecision by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Controller Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Target Frame Rate: ${fpsLimit.toInt()} FPS")
                Slider(
                    value = fpsLimit,
                    onValueChange = { fpsLimit = it },
                    valueRange = 5f..30f,
                    steps = 5
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Auto-Decision Simulator")
                    Switch(
                        checked = autoDecision,
                        onCheckedChange = { autoDecision = it }
                    )
                }

                Text(
                    text = "Resolution and downscaling are handled automatically in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
