package com.example.aqpfact.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aqpfact.ui.MainViewModel
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val billDetails by viewModel.billDetails.collectAsState()
    val nextDateSaved by viewModel.nextReadingDate.collectAsState()

    var totalInput by remember { mutableStateOf("") }
    var fixedInput by remember { mutableStateOf("") }
    var aqueductInput by remember { mutableStateOf("") }
    var sewerageInput by remember { mutableStateOf("") }
    var purificationInput by remember { mutableStateOf("") }
    var chargesInput by remember { mutableStateOf("") }
    var bonusInput by remember { mutableStateOf("") }
    var advancesInput by remember { mutableStateOf("") }
    var otherInput by remember { mutableStateOf("") }
    var taxableInput by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = nextDateSaved ?: System.currentTimeMillis()
    )

    LaunchedEffect(billDetails) {
        totalInput = billDetails.total
        fixedInput = billDetails.fixed
        aqueductInput = billDetails.aqueduct
        sewerageInput = billDetails.sewerage
        purificationInput = billDetails.purification
        chargesInput = billDetails.charges
        bonusInput = billDetails.bonus
        advancesInput = billDetails.advances
        otherInput = billDetails.other
        taxableInput = billDetails.taxable
    }

    fun parseVal(s: String): Double = s.replace(',', '.').trim().toDoubleOrNull() ?: 0.0

    val pFixed = parseVal(fixedInput)
    val pAqueduct = parseVal(aqueductInput)
    val pSewerage = parseVal(sewerageInput)
    val pPurification = parseVal(purificationInput)
    val pCharges = parseVal(chargesInput)
    val pBonus = parseVal(bonusInput)
    val pAdvances = parseVal(advancesInput)
    val pOther = parseVal(otherInput)
    val pTaxable = parseVal(taxableInput)

    // Costo IVA 10% calcolato direttamente dal Totale Imponibile inserito dall'utente
    val vat10 = pTaxable * 0.10

    // Costi Variabili = Acquedotto + Fognatura + Depurazione + Oneri + Addebiti/Accrediti Diversi + IVA 10%
    val variablePart = pAqueduct + pSewerage + pPurification + pCharges + pOther + vat10
    // Costo Totale = Costi Fissi + Costi Variabili
    val calcTotal = pFixed + variablePart
    val advancesPct = if (calcTotal > 0) kotlin.math.abs(pAdvances) / calcTotal else 0.0

    fun saveAll() {
        val finalTotal = if (totalInput.isBlank()) String.format(Locale.US, "%.2f", calcTotal) else totalInput
        viewModel.saveBillSettings(
            total = finalTotal,
            fixed = fixedInput,
            aqueduct = aqueductInput,
            sewerage = sewerageInput,
            purification = purificationInput,
            charges = chargesInput,
            bonus = bonusInput,
            advances = advancesInput,
            other = otherInput,
            taxable = taxableInput
        )
        onBack()
    }

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
                    IconButton(onClick = { saveAll() }) {
                        Icon(Icons.Default.Save, contentDescription = "Salva")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Inserisci le singole voci della fattura ricevuta. I costi e accrediti variabili verranno ripartiti proporzionalmente ai consumi.",
                style = MaterialTheme.typography.bodyMedium
            )

            // --- SEZIONE Dettaglio Componenti ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Voci di Spesa e Accrediti", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = fixedInput,
                        onValueChange = { fixedInput = it },
                        label = { Text("Quota Fissa (€)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = aqueductInput,
                        onValueChange = { aqueductInput = it },
                        label = { Text("Servizio Acquedotto (€)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = sewerageInput,
                        onValueChange = { sewerageInput = it },
                        label = { Text("Servizio Fognatura (€)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = purificationInput,
                        onValueChange = { purificationInput = it },
                        label = { Text("Servizio Depurazione (€)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = chargesInput,
                        onValueChange = { chargesInput = it },
                        label = { Text("Oneri (€)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = bonusInput,
                        onValueChange = { bonusInput = it },
                        label = { Text("Bonus (€) (es. -10.00 se accredito)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = advancesInput,
                        onValueChange = { advancesInput = it },
                        label = { Text("Acconti Precedenti e Rettifiche (€) (es. -15.00 se accredito)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = otherInput,
                        onValueChange = { otherInput = it },
                        label = { Text("Addebiti/Accrediti Diversi (€) (es. -5.00 se accredito)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // --- SEZIONE Imponibile e IVA ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Totale Imponibile e IVA (10%)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = taxableInput,
                        onValueChange = { taxableInput = it },
                        label = { Text("Totale Imponibili (€)") },
                        placeholder = { Text("es. 100.00") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Costo IVA (10% Imponibile):", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${String.format(Locale.getDefault(), "%.2f", vat10)} €",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // --- SEZIONE Totale e Verifica Ripartizione ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Totale Fattura e Verifica", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = totalInput,
                        onValueChange = { totalInput = it },
                        label = { Text("Totale Bolletta (€)") },
                        placeholder = { Text(String.format(Locale.getDefault(), "%.2f (Calcolato)", calcTotal)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Costi Fissi (Quota Fissa):", style = MaterialTheme.typography.bodyMedium)
                        Text("${String.format(Locale.getDefault(), "%.2f", pFixed)} €", style = MaterialTheme.typography.bodyLarge)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Costi Variabili:", style = MaterialTheme.typography.bodyMedium)
                        Text("${String.format(Locale.getDefault(), "%.2f", variablePart)} €", style = MaterialTheme.typography.bodyLarge)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Costo Totale Calcolato:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${String.format(Locale.getDefault(), "%.2f", calcTotal)} €", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }

                    if (pAdvances != 0.0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Acconti Precedenti:", style = MaterialTheme.typography.bodyMedium)
                            Text("${String.format(Locale.getDefault(), "%.2f", pAdvances)} € (${String.format(Locale.getDefault(), "%.2f", advancesPct * 100)}%)", style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            totalInput = String.format(Locale.US, "%.2f", calcTotal)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = "Calcola Totale")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Imposta Totale Calcolato")
                    }
                }
            }

            HorizontalDivider()

            // --- SEZIONE Promemoria Prossima Lettura ---
            Text("Pianificazione Prossima Lettura", style = MaterialTheme.typography.titleLarge)

            Button(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (nextDateSaved == null) "Pianifica Lettura su Calendario" else "Modifica Data Lettura")
            }

            if (nextDateSaved != null) {
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                Text("Prossima lettura prevista per il: ${sdf.format(Date(nextDateSaved!!))}")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { saveAll() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Conferma e Salva")
            }
        }
    }
}
