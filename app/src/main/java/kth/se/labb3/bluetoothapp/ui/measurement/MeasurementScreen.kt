package kth.se.labb3.bluetoothapp.ui.measurement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import kth.se.labb3.bluetoothapp.data.database.MeasurementEntity
import kth.se.labb3.bluetoothapp.ui.components.AngleChart
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MeasurementScreen(
    viewModelFactory: MeasurementViewModelFactory
) {
    val viewModel: MeasurementViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    // Hämta historik från databasen (Se till att 'measurementHistory' finns i ViewModel)
    val historyList by viewModel.measurementHistory.collectAsState(initial = emptyList())

    // State för att visa historik-dialogen
    var showHistoryDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { viewModel.exportDataToUri(it) }
    }

    // Hantera Events (Felmeddelanden & Export)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is UiEvent.ExportFile -> {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    createDocumentLauncher.launch("arm_elevation_$timestamp.csv")
                }
            }
        }
    }

    // --- UI START ---
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Arm Elevation",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 1. SENSOR SELECTION
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Sensor Source:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(
                            onClick = { viewModel.toggleSensorSource(false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!uiState.useExternalSensor) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        ) {
                            Text("Internal")
                        }
                        Button(
                            onClick = { viewModel.toggleSensorSource(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.useExternalSensor) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        ) {
                            Text("Polar BLE")
                        }
                    }
                }
            }

            // 2. BLUETOOTH SETTINGS (Visas bara om Polar är valt)
            if (uiState.useExternalSensor) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Bluetooth Settings", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (uiState.isBluetoothConnected) {
                            Text("Status: CONNECTED ✅", color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.disconnectDevice() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Disconnect")
                            }
                        } else {
                            Text("Status: Disconnected", color = Color.Red)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Button(onClick = { viewModel.startScanning() }) { Text("Scan") }
                                Button(onClick = { viewModel.stopScanning() }) { Text("Stop Scan") }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Devices Found:", style = MaterialTheme.typography.labelMedium)

                            if (uiState.scannedDevices.isEmpty()) {
                                Text("No devices found...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }

                            uiState.scannedDevices.forEach { device ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { viewModel.connectToDevice(device) },
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(text = device.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(text = "ID: ${device.deviceId}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. CURRENT ANGLES
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Current Angles:", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Algo 1 (Acc):", style = MaterialTheme.typography.bodyLarge)
                        Text("%.1f°".format(uiState.currentAngleAlgo1), color = Color.Blue, style = MaterialTheme.typography.titleLarge)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Algo 2 (Fusion):", style = MaterialTheme.typography.bodyLarge)
                        Text("%.1f°".format(uiState.currentAngleAlgo2), color = Color.Red, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. LIVE GRAPH
            if (uiState.recordedData.isNotEmpty()) {
                Text("Measurement Result", style = MaterialTheme.typography.titleSmall)
                AngleChart(data = uiState.recordedData, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 5. CONTROL BUTTONS
            Button(
                onClick = { viewModel.onStartStopClick() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !uiState.useExternalSensor || uiState.isBluetoothConnected || uiState.isRecording
            ) {
                Text(
                    text = if (uiState.isRecording) "STOP MEASUREMENT" else "START MEASUREMENT",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.onExportClick() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isRecording && uiState.recordedData.isNotEmpty()
            ) {
                Text("Export Data to CSV")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. HISTORY BUTTON (Database)
            OutlinedButton(
                onClick = { showHistoryDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Measurement History")
            }
        }
    }

    // --- HISTORY DIALOG ---
    if (showHistoryDialog) {
        Dialog(onDismissRequest = { showHistoryDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Measurement History", style = MaterialTheme.typography.headlineSmall)
                        IconButton(onClick = { showHistoryDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    HorizontalDivider()

                    if (historyList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No history found.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(top = 8.dp)
                        ) {
                            items(historyList) { measurement ->
                                HistoryItem(measurement)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(measurement: MeasurementEntity) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val dateString = dateFormat.format(Date(measurement.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = dateString, style = MaterialTheme.typography.titleSmall, color = Color.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Data points: ${measurement.dataPointsCsv.split(";").size}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray
            )
            Text(
                text = "ID: ${measurement.id}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}