package com.example.aqpfact.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.aqpfact.ui.MainViewModel
import com.example.aqpfact.utils.PdfGenerator
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val readings by viewModel.allReadings.collectAsState()
    
    var totalBillCost by remember { mutableStateOf("0.0") }
    var fixedCosts by remember { mutableStateOf("0.0") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report e Ripartizione") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val file = PdfGenerator.generateReport(context, readings)
                        if (file != null) {
                            val uri = FileProvider.getUriForFile(context, "com.example.aqpfact.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Condividi Report"))
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Condividi PDF")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            item {
                Text("Parametri Bolletta", style = MaterialTheme.typography.titleLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = totalBillCost,
                        onValueChange = { totalBillCost = it },
                        label = { Text("Totale Bolletta (€)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fixedCosts,
                        onValueChange = { fixedCosts = it },
                        label = { Text("Spese Fisse (€)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Text("Ripartizione Spese", style = MaterialTheme.typography.titleLarge)
                val totalBill = totalBillCost.toDoubleOrNull() ?: 0.0
                val totalFixed = fixedCosts.toDoubleOrNull() ?: 0.0
                val totalVariable = (totalBill - totalFixed).coerceAtLeast(0.0)
                
                val numUsers = 3
                val fixedPerUser = totalFixed / numUsers

                val readingsByMeter = readings.groupBy { it.meterId }
                
                val userConsumptions = (1..numUsers).map { id ->
                    val userReadings = readingsByMeter[id] ?: emptyList()
                    val consumption = if (userReadings.size >= 2) {
                        (userReadings[0].value - userReadings[1].value).coerceAtLeast(0.0)
                    } else if (userReadings.size == 1) {
                        userReadings[0].value
                    } else 0.0
                    id to consumption
                }
                
                val totalConsumption = userConsumptions.sumOf { it.second }
                
                userConsumptions.forEach { (id, consumption) ->
                    val variableCost = if (totalConsumption > 0) {
                        (consumption / totalConsumption) * totalVariable
                    } else 0.0
                    
                    val totalUserCost = variableCost + fixedPerUser

                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Utenza $id", style = MaterialTheme.typography.titleMedium)
                            Text("Consumo: ${String.format("%.2f", consumption)} m³")
                            Text("Quota Variabile: ${String.format("%.2f", variableCost)} €")
                            Text("Quota Fissa: ${String.format("%.2f", fixedPerUser)} €")
                            Text("TOTALE: ${String.format("%.2f", totalUserCost)} €", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Text("Andamento Consumi", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
            }

            val readingsByMeter = readings.groupBy { it.meterId }
            readingsByMeter.forEach { (meterId, meterReadings) ->
                item {
                    Text(if (meterId == 0) "Generale" else "Utenza $meterId", style = MaterialTheme.typography.titleMedium)
                    val chartEntries = meterReadings.take(10).reversed().mapIndexed { index, reading ->
                        index.toFloat() to reading.value.toFloat()
                    }
                    if (chartEntries.isNotEmpty()) {
                        Chart(
                            chart = lineChart(),
                            model = entryModelOf(*chartEntries.map { it.second }.toTypedArray()),
                            startAxis = rememberStartAxis(),
                            bottomAxis = rememberBottomAxis(),
                            modifier = Modifier.height(200.dp).fillMaxWidth()
                        )
                    } else {
                        Text("Dati insufficienti")
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
