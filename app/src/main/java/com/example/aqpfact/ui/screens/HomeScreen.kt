package com.example.aqpfact.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.aqpfact.data.Reading
import com.example.aqpfact.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToAddReading: () -> Unit,
    onNavigateToReport: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBillSettings: () -> Unit
) {
    val context = LocalContext.current
    val readings by viewModel.allReadings.collectAsState()
    var showSyncDialog by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    
    val pCloudToken by viewModel.pCloudToken.collectAsState()
    val meterNames by viewModel.meterNames.collectAsState()
    
    val totalBillCost by viewModel.lastBillTotal.collectAsState()
    val fixedCosts by viewModel.lastBillFixed.collectAsState()
    val nextReadingDate by viewModel.nextReadingDate.collectAsState()

    LaunchedEffect(pCloudToken) {
        if (pCloudToken != null) token = pCloudToken!!
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AQPFact") },
                actions = {
                    IconButton(onClick = { onNavigateToSettings() }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
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
            FloatingActionButton(onClick = { onNavigateToAddReading() }) {
                Icon(Icons.Default.Add, contentDescription = "Nuova Sessione di Lettura")
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Statistiche Bolletta", style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = onNavigateToBillSettings) {
                            Icon(androidx.compose.material.icons.Icons.Default.Edit, contentDescription = "Modifica Costi")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
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
                        val meterName = meterNames[id] ?: "Utenza $id"
                        val varCost = if (totalConsumption > 0) (consumption / totalConsumption) * totalVariable else 0.0
                        val fixedCost = totalFixed / 3.0
                        val total = varCost + fixedCost
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$meterName:", style = MaterialTheme.typography.bodyMedium)
                            Text("${String.format("%.2f", total)} € (${String.format("%.1f", consumption)} m³)", style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column {
                            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                            Text("Prossima Lettura:", style = MaterialTheme.typography.titleSmall)
                            Text(
                                nextReadingDate?.let { sdf.format(Date(it)) } ?: "Non impostata",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Button(onClick = onNavigateToBillSettings) {
                            Text(if (nextReadingDate == null) "Pianifica" else "Modifica")
                        }
                    }
                }
            }

            Text("Cronologia Sessioni", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))

            val groupedReadings = readings.groupBy { it.groupId ?: it.date.toString() }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(groupedReadings.values.toList()) { sessionReadings ->
                    SessionItem(sessionReadings, meterNames = meterNames, onDelete = { 
                        val groupId = sessionReadings.first().groupId
                        if (groupId != null) {
                            viewModel.deleteSession(groupId)
                        } else {
                            viewModel.deleteReading(sessionReadings.first().id)
                        }
                    })
                }
            }
        }
    }
}

@Composable
fun SessionItem(sessionReadings: List<Reading>, meterNames: Map<Int, String>, onDelete: () -> Unit) {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val date = sessionReadings.first().date
    
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sessione: ${sdf.format(Date(date))}", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina Sessione", tint = MaterialTheme.colorScheme.error)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            sessionReadings.sortedBy { it.meterId }.forEach { reading ->
                val meterName = meterNames[reading.meterId] ?: "Utenza ${reading.meterId}"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$meterName:", style = MaterialTheme.typography.bodySmall)
                    Text("${reading.value} m³", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
