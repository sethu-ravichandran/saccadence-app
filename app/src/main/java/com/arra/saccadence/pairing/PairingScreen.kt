package com.arra.saccadence.pairing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arra.saccadence.voice.SpokenLanguage

data class PairingSettings(val host: String, val port: Int, val sessionCode: String, val language: SpokenLanguage)

/**
 * The rig prints its LAN IPs to its own console and shows a session code +
 * QR on its start screen (see saccadence-rig/public/js/startScreen.js).
 * Manual entry here, not a QR scanner — adding camera-based barcode
 * scanning is a reasonable next step but pulls in another dependency
 * (ML Kit) not exercised in this pass.
 */
@Composable
fun PairingScreen(
    statusText: String,
    onConnect: (PairingSettings) -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8765") }
    var sessionCode by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(SpokenLanguage.ENGLISH) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Pair with rig", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Enter the address and session code shown on the clinic laptop's start screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = host, onValueChange = { host = it },
            label = { Text("Rig IP address") },
            placeholder = { Text("192.168.1.42") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = port, onValueChange = { port = it.filter(Char::isDigit) },
            label = { Text("Port") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = sessionCode, onValueChange = { sessionCode = it.uppercase() },
            label = { Text("Session code") },
            placeholder = { Text("ABC123") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Text("Spoken instructions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            SpokenLanguage.entries.forEachIndexed { index, option ->
                if (index > 0) Spacer(Modifier.width(8.dp))
                val label = option.name.lowercase().replaceFirstChar { it.uppercase() }
                if (option == language) {
                    Button(onClick = { language = option }, modifier = Modifier.weight(1f)) { Text(label) }
                } else {
                    OutlinedButton(onClick = { language = option }, modifier = Modifier.weight(1f)) { Text(label) }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(statusText, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                val portNumber = port.toIntOrNull() ?: 8765
                onConnect(PairingSettings(host.trim(), portNumber, sessionCode.trim(), language))
            },
            enabled = host.isNotBlank() && sessionCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Connect") }
    }
}
