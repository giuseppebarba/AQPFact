package com.example.aqpfact.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                            Icon(Icons.Default.Edit, contentDescription = "Modifica Costi")
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
                            Text("${String.format(Locale.getDefault(), "%.2f", total)} € (${String.format(Locale.getDefault(), "%.1f", consumption)} m³)", style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
            Spacer(modifier = Modifier.height(8.dp))

            val groupedReadings = readings.groupBy { it.groupId ?: it.date.toString() }
                .toList().sortedByDescending { it.first }

            Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp).horizontalScroll(rememberScrollState())) {
                Column {
                    // Header Tabella
                    Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
                        TableHeaderItem("Data", 80)
                        TableHeaderItem(meterNames[0]?.take(8) ?: "Gen", 70)
                        TableHeaderItem(meterNames[1]?.take(8) ?: "Ut 1", 70)
                        TableHeaderItem(meterNames[2]?.take(8) ?: "Ut 2", 70)
                        TableHeaderItem(meterNames[3]?.take(8) ?: "Ut 3", 70)
                        TableHeaderItem("Sfrido", 70)
                        TableHeaderItem("Azione", 60)
                    }
                    
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(groupedReadings) { (_, sessionReadings) ->
                            SessionRow(
                                date = sessionReadings.first().date,
                                readings = sessionReadings,
                                allReadings = readings,
                                onDelete = {
                                    val id = sessionReadings.first().groupId
                                    if (id != null) viewModel.deleteSession(id) 
                                    else viewModel.deleteReading(sessionReadings.first().id)
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TableHeaderItem(text: String, width: Int) {
    Text(
        text = text,
        modifier = Modifier.width(width.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
}

@Composable
fun SessionRow(
    date: Long,
    readings: List<Reading>,
    allReadings: List<Reading>,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
    
    // Calcolo sfrido per la riga
    val readingsByMeter = allReadings.groupBy { it.meterId }.mapValues { it.value.sortedByDescending { r -> r.date } }
    var genCons = 0.0
    var userConsSum = 0.0
    
    (0..3).forEach { mId ->
        val current = readings.find { it.meterId == mId }
        if (current != null) {
            val prev = readingsByMeter[mId]?.find { it.date < date }
            val cons = if (prev != null) (current.value - prev.value).coerceAtLeast(0.0) else current.value
            if (mId == 0) genCons = cons else userConsSum += cons
        }
    }
    val sfrido = genCons - userConsSum

    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TableCell(sdf.format(Date(date)), 80)
        TableCell(readings.find { it.meterId == 0 }?.value?.toString() ?: "-", 70)
        TableCell(readings.find { it.meterId == 1 }?.value?.toString() ?: "-", 70)
        TableCell(readings.find { it.meterId == 2 }?.value?.toString() ?: "-", 70)
        TableCell(readings.find { it.meterId == 3 }?.value?.toString() ?: "-", 70)
        TableCell(String.format(Locale.getDefault(), "%.2f", sfrido), 70)
        
        Box(modifier = Modifier.width(60.dp), contentAlignment = Alignment.Center) {
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun TableCell(text: String, width: Int) {
    Text(
        text = text,
        modifier = Modifier.width(width.dp),
        style = MaterialTheme.typography.bodySmall,
        fontSize = 12.sp,
        maxLines = 1
    )
}
