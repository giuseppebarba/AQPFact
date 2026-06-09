package com.example.aqpfact.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.aqpfact.ui.MainViewModel
import com.example.aqpfact.utils.PdfGenerator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val readings by viewModel.allReadings.collectAsState()
    val meterNames by viewModel.meterNames.collectAsState()
    
    val totalBillCost by viewModel.lastBillTotal.collectAsState()
    val fixedCosts by viewModel.lastBillFixed.collectAsState()

    val numUsers = 3
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
    val totalConsumptionAll = userConsumptions.sumOf { it.second }

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
                        val file = PdfGenerator.generateReport(
                            context,
                            readings,
                            meterNames,
                            totalBillCost,
                            fixedCosts
                        )
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
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Parametri Fatturazione", style = MaterialTheme.typography.titleMedium)
                        Text("Totale Bolletta: $totalBillCost €")
                        Text("Spese Fisse: $fixedCosts €")
                        Text(
                            "Puoi modificare questi valori nella schermata dei parametri in Home.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Text("Ripartizione Spese", style = MaterialTheme.typography.titleLarge)
                val totalBill = totalBillCost.toDoubleOrNull() ?: 0.0
                val totalFixed = fixedCosts.toDoubleOrNull() ?: 0.0
                val totalVariable = (totalBill - totalFixed).coerceAtLeast(0.0)
                
                val fixedPerUser = totalFixed / numUsers

                userConsumptions.forEach { (id, consumption) ->
                    val variableCost = if (totalConsumptionAll > 0) {
                        (consumption / totalConsumptionAll) * totalVariable
                    } else 0.0
                    
                    val totalUserCost = variableCost + fixedPerUser

                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val lastReading = readingsByMeter[id]?.firstOrNull()?.value ?: 0.0
                            Text(meterNames[id] ?: "Utenza $id", style = MaterialTheme.typography.titleMedium)
                            Text("Ultima Lettura: $lastReading m³")
                            Text("Consumo Periodo: ${String.format(Locale.getDefault(), "%.2f", consumption)} m³")
                            Text("Quota Variabile: ${String.format(Locale.getDefault(), "%.2f", variableCost)} €")
                            Text("Quota Fissa: ${String.format(Locale.getDefault(), "%.2f", fixedPerUser)} €")
                            Text("TOTALE: ${String.format(Locale.getDefault(), "%.2f", totalUserCost)} €", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Bilancio Consumi in UI
                val genReadings = readingsByMeter[0] ?: emptyList()
                val genCons = if (genReadings.size >= 2) {
                    (genReadings[0].value - genReadings[1].value).coerceAtLeast(0.0)
                } else genReadings.firstOrNull()?.value ?: 0.0
                
                val userConsSum = userConsumptions.sumOf { it.second }
                val gap = genCons - userConsSum

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Bilancio Consumi (Sfrido)", style = MaterialTheme.typography.titleSmall)
                        Text("Differenza tra Generale e Somma Utenze: ${String.format(Locale.getDefault(), "%.2f", gap)} m³")
                        if (gap > 0.5) {
                            Text("Nota: È presente una differenza significativa. Possibile perdita o prelievo non tracciato.", 
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Text("Distribuzione Consumi", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                if (totalConsumptionAll > 0) {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Distribuzione Consumi Utenze", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            val colors = listOf(Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFFC107))
                            
                            Box(modifier = Modifier.size(200.dp)) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    var startAngle = -90f
                                    userConsumptions.forEachIndexed { index, pair ->
                                        val sweepAngle = (pair.second / totalConsumptionAll).toFloat() * 360f
                                        drawArc(
                                            color = colors[index % colors.size],
                                            startAngle = startAngle,
                                            sweepAngle = sweepAngle,
                                            useCenter = true,
                                            style = Fill
                                        )
                                        startAngle += sweepAngle
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            userConsumptions.forEachIndexed { index, pair ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Box(modifier = Modifier.size(12.dp).background(colors[index % colors.size]))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val percentage = (pair.second / totalConsumptionAll * 100).toInt()
                                    Text("${meterNames[pair.first] ?: "Utenza ${pair.first}"}: $percentage%")
                                }
                            }
                        }
                    }
                } else {
                    Text("Dati insufficienti per il grafico", modifier = Modifier.padding(16.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
