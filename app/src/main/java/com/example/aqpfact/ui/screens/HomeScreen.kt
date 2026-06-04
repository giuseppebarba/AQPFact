package com.example.aqpfact.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aqpfact.data.Reading
import com.example.aqpfact.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToAddReading: (Int) -> Unit,
    onNavigateToReport: () -> Unit
) {
    val readings by viewModel.allReadings.collectAsState()
    var showSyncDialog by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    
    var totalBillCost by remember { mutableStateOf("0.0") }
    var fixedCosts by remember { mutableStateOf("0.0") }
    
    var showAddOptions by remember { mutableStateOf(false) }

    if (showSyncDialog) {
        AlertDialog(
            onDismissRequest = { showSyncDialog = false },
            title = { Text("Sincronizza su pCloud") },
            text = {
                Column {
                    Text("Inserisci il tuo Access Token di pCloud:")
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Token") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.syncToPCloud(token)
                    showSyncDialog = false
                }) {
                    Text("Sincronizza")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }

    if (showAddOptions) {
        AlertDialog(
            onDismissRequest = { showAddOptions = false },
            title = { Text("Seleziona Utenza") },
            text = {
                Column {
                    (0..3).forEach { id ->
                        TextButton(
                            onClick = {
                                onNavigateToAddReading(id)
                                showAddOptions = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (id == 0) "Contatore Generale" else "Utenza $id")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddOptions = false }) {
                    Text("Annulla")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AQPFact") },
                actions = {
                    IconButton(onClick = { onNavigateToReport() }) {
                        Icon(Icons.Default.Share, contentDescription = "Report")
                    }
                    IconButton(onClick = { showSyncDialog = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync pCloud")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddOptions = true }) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi Lettura")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Sezione Statistiche
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Statistiche Ultima Bolletta", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = totalBillCost,
                            onValueChange = { totalBillCost = it },
                            label = { Text("Totale (€)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = fixedCosts,
                            onValueChange = { fixedCosts = it },
                            label = { Text("Fisse (€)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val totalBill = totalBillCost.toDoubleOrNull() ?: 0.0
                    val totalFixed = fixedCosts.toDoubleOrNull() ?: 0.0
                    val totalVariable = (totalBill - totalFixed).coerceAtLeast(0.0)
                    
                    val readingsByMeter = readings.groupBy { it.meterId }
                    val userConsumptions = (1..3).map { id ->
                        val userReadings = readingsByMeter[id] ?: emptyList()
                        val consumption = if (userReadings.size >= 2) {
                            (userReadings[0].value - userReadings[1].value).coerceAtLeast(0.0)
                        } else if (userReadings.size == 1) {
                            userReadings[0].value
                        } else 0.0
                        consumption
                    }
                    val totalConsumption = userConsumptions.sum()
                    
                    userConsumptions.forEachIndexed { index, consumption ->
                        val id = index + 1
                        val varCost = if (totalConsumption > 0) (consumption / totalConsumption) * totalVariable else 0.0
                        val fixedCost = totalFixed / 3.0
                        val total = varCost + fixedCost
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Utenza $id:", style = MaterialTheme.typography.bodyMedium)
                            Text("${String.format("%.2f", total)} € (${String.format("%.1f", consumption)} m³)", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Text("Cronologia Letture", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(readings) { reading ->
                    ReadingItem(reading, onDelete = { viewModel.deleteReading(reading.id) })
                }
            }
        }
    }
}

@Composable
fun ReadingItem(reading: Reading, onDelete: () -> Unit) {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Meter: ${if (reading.meterId == 0) "Generale" else "Utenza ${reading.meterId}"}")
            Text("Valore: ${reading.value} m³")
            Text("Data: ${sdf.format(Date(reading.date))}")
            if (reading.photoPath != null) {
                Text("Foto allegata")
            }
            IconButton(onClick = onDelete) {
                Text("Elimina", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
