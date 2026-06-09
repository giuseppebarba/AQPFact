package com.example.aqpfact.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.aqpfact.ui.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReadingScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) } // 0: Main, 1: User 1, 2: User 2, 3: User 3
    val totalSteps = 4
    
    val sessionReadings = remember { mutableStateListOf<Triple<Int, Double, String?>>() }
    
    var value by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var hasCameraPermission by remember { mutableStateOf(false) }

    val meterNames by viewModel.meterNames.collectAsState()
    val meterName = meterNames[currentStep] ?: (if (currentStep == 0) "Generale" else "Utenza $currentStep")

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Sessione di Lettura ($meterName)") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { (currentStep.coerceAtMost(totalSteps - 1) + 1).toFloat() / totalSteps },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )

            if (currentStep < totalSteps) {
                Text(
                    "Acquisizione: $meterName",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (hasCameraPermission && photoUri == null) {
                    CameraPreview(onImageCaptured = { uri -> photoUri = uri })
                } else if (photoUri != null) {
                    Text("Foto acquisita!")
                    Button(onClick = { photoUri = null }) {
                        Text("Rifare foto")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Valore contatore (m³)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        val v = value.toDoubleOrNull() ?: 0.0
                        sessionReadings.add(Triple(currentStep, v, photoUri?.toString()))
                        
                        if (currentStep < totalSteps - 1) {
                            currentStep++
                            value = ""
                            photoUri = null
                        } else {
                            viewModel.addReadingSession(sessionReadings.toList())
                            currentStep++ 
                        }
                    },
                    enabled = value.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (currentStep < totalSteps - 1) "Prossimo Contatore" else "Concludi e Salva")
                }
            } else {
                // Step di Riepilogo Finale
                val generalValue = sessionReadings.find { it.first == 0 }?.second ?: 0.0
                
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    "Sessione Salvata!",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Valore Contatore Generale da comunicare:", style = MaterialTheme.typography.titleMedium)
                        Text("$generalValue m³", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ricordati di comunicare questo valore al fornitore per l'emissione della prossima fattura.")
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Torna alla Home")
                }
            }
            
            if (currentStep < totalSteps) {
                TextButton(onClick = onBack) {
                    Text("Annulla Sessione (Non verrà salvato nulla)")
                }
            }
        }
    }
}

@Composable
fun CameraPreview(onImageCaptured: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(cameraProviderFuture) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Box(modifier = Modifier.height(300.dp).fillMaxWidth()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Button(
            onClick = {
                val photoFile = File(
                    context.filesDir,
                    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date()) + ".jpg"
                )
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            onImageCaptured(Uri.fromFile(photoFile))
                        }
                        override fun onError(exception: ImageCaptureException) {
                            exception.printStackTrace()
                        }
                    }
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            Text("Scatta Foto")
        }
    }
}
