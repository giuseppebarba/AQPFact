package com.example.aqpfact.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.aqpfact.ui.MainViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val syncStatus by viewModel.syncStatus.collectAsState()
    val meterNames by viewModel.meterNames.collectAsState()
    
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_FILE))
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Handle result if needed
    }

    var n0 by remember { mutableStateOf("") }
    var n1 by remember { mutableStateOf("") }
    var n2 by remember { mutableStateOf("") }
    var n3 by remember { mutableStateOf("") }

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
        if (syncStatus != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearSyncStatus() },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearSyncStatus() }) {
                        Text("OK")
                    }
                },
                text = { Text(syncStatus!!) }
            )
        }

        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            item {
                Text("Sincronizzazione Google Drive", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { launcher.launch(googleSignInClient.signInIntent) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Accedi con Google")
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.uploadToDrive() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Backup")
                    }
                    Button(
                        onClick = { viewModel.downloadFromDrive() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ripristina")
                    }
                }
            }

            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
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
