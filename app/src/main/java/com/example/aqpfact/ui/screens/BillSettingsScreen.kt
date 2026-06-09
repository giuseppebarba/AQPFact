package com.example.aqpfact.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.aqpfact.ui.MainViewModel
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val totalSaved by viewModel.lastBillTotal.collectAsState()
    val fixedSaved by viewModel.lastBillFixed.collectAsState()
    val nextDateSaved by viewModel.nextReadingDate.collectAsState()

    var totalInput by remember { mutableStateOf("") }
    var fixedInput by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = nextDateSaved ?: System.currentTimeMillis()
    )

    LaunchedEffect(totalSaved) { totalInput = totalSaved }
    LaunchedEffect(fixedSaved) { fixedInput = fixedSaved }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val date = datePickerState.selectedDateMillis
                    viewModel.saveNextReadingDate(date)
                    showDatePicker = false
                    
                    date?.let {
                        val intent = Intent(Intent.ACTION_INSERT)
                            .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                            .putExtra(android.provider.CalendarContract.Events.TITLE, "Lettura Contatori AQP")
                            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, it)
                            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, it + 3600000)
                            .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "Eseguire lettura di tutti i contatori (Generale e Utenze)")
                            .putExtra(android.provider.CalendarContract.Events.ALL_DAY, true)
                        context.startActivity(intent)
                    }
                }) { Text("Imposta promemoria") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annulla") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parametri Bolletta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.saveBillSettings(totalInput, fixedInput)
                        onBack()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Salva")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Inserisci i dati dell'ultima fattura ricevuta.", style = MaterialTheme.typography.bodyMedium)
            
            OutlinedTextField(
                value = totalInput,
                onValueChange = { totalInput = it },
                label = { Text("Totale Bolletta (€)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = fixedInput,
                onValueChange = { fixedInput = it },
                label = { Text("Spese Fisse (€)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Pianificazione Prossima Lettura", style = MaterialTheme.typography.titleLarge)
            
            Button(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (nextDateSaved == null) "Pianifica Lettura su Calendario" else "Modifica Data Lettura")
            }

            if (nextDateSaved != null) {
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                Text("Prossima lettura prevista per il: ${sdf.format(Date(nextDateSaved!!))}")
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.saveBillSettings(totalInput, fixedInput)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Conferma e Torna alla Home")
            }
        }
    }
}
