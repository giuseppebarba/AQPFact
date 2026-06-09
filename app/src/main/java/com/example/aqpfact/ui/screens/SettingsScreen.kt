package com.example.aqpfact.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aqpfact.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val pCloudToken by viewModel.pCloudToken.collectAsState()
    
    var tokenInput by remember { mutableStateOf("") }
    
    // Meter names states
    val meterNames by viewModel.meterNames.collectAsState()

    var n0 by remember { mutableStateOf("") }
    var n1 by remember { mutableStateOf("") }
    var n2 by remember { mutableStateOf("") }
    var n3 by remember { mutableStateOf("") }

    LaunchedEffect(pCloudToken) { tokenInput = pCloudToken ?: "" }
    LaunchedEffect(meterNames) {
        if (meterNames.isNotEmpty()) {
            n0 = meterNames[0] ?: "Generale"
            n1 = meterNames[1] ?: "Utenza 1"
            n2 = meterNames[2] ?: "Utenza 2"
            n3 = meterNames[3] ?: "Utenza 3"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.savePCloudToken(tokenInput)
                        viewModel.saveMeterName(0, n0)
                        viewModel.saveMeterName(1, n1)
                        viewModel.saveMeterName(2, n2)
                        viewModel.saveMeterName(3, n3)
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Salva")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Account pCloud", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Access Token") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Inserisci il token OAuth") }
                )
                Text(
                    "Il token viene usato per la sincronizzazione del database su pCloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { Divider() }

            item {
                Text("Nomi Utenze", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                
                MeterNameField(0, n0) { n0 = it }
                MeterNameField(1, n1) { n1 = it }
                MeterNameField(2, n2) { n2 = it }
                MeterNameField(3, n3) { n3 = it }
            }
        }
    }
}

@Composable
fun MeterNameField(id: Int, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (id == 0) "Contatore Generale" else "Utenza $id") },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true
    )
}
