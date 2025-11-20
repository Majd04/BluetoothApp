package kth.se.labb3.bluetoothapp.ui.measurement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
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

    val historyList by viewModel.measurementHistory.collectAsState(initial = emptyList())
    var showHistoryDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { viewModel.exportDataToUri(it) }
    }

    // Hantera Events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is UiEvent.ExportFile -> {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    createDocumentLauncher.launch("export_$timestamp.csv")
                }
                is UiEvent.CloseHistoryDialog -> showHistoryDialog = false
            }
        }
    }

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
            Text("Arm Elevation", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))

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
                            colors = ButtonDefaults.buttonColors(containerColor = if (!uiState.useExternalSensor) MaterialTheme.colorScheme.primary else Color.Gray)
                        ) { Text("Internal") }
                        Button(
                            onClick = { viewModel.toggleSensorSource(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (uiState.useExternalSensor) MaterialTheme.colorScheme.primary else Color.Gray)
                        ) { Text("Polar BLE") }
                    }
                }
            }

            // 2. BLUETOOTH SETTINGS
            if (uiState.useExternalSensor) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Bluetooth Settings", style = MaterialTheme.typography.titleSmall)
                        if (uiState.isBluetoothConnected) {
                            Text("Status: CONNECTED ✅", color = Color(0xFF2E7D32))
                            Button(onClick = { viewModel.disconnectDevice() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Disconnect") }
                        } else {
                            Text("Status: Disconnected", color = Color.Red)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Button(onClick = { viewModel.startScanning() }) { Text("Scan") }
                                Button(onClick = { viewModel.stopScanning() }) { Text("Stop Scan") }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.scannedDevices.forEach { device ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.connectToDevice(device) },
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(device.name)
                                        Text(device.deviceId, style = MaterialTheme.typography.bodySmall)
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Algo 1 (Acc):"); Text("%.1f°".format(uiState.currentAngleAlgo1), color = Color.Blue)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Algo 2 (Fusion):"); Text("%.1f°".format(uiState.currentAngleAlgo2), color = Color.Red)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // 4. GRAPH
            if (uiState.recordedData.isNotEmpty()) {
                Text("Data Visualization", style = MaterialTheme.typography.titleSmall)
                AngleChart(data = uiState.recordedData, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 5. BUTTONS
            Button(
                onClick = { viewModel.onStartStopClick() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !uiState.useExternalSensor || uiState.isBluetoothConnected || uiState.isRecording
            ) {
                Text(if (uiState.isRecording) "STOP MEASUREMENT" else "START MEASUREMENT")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.onExportCurrentDataClick() }, // Ändrat funktionsnamn här
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isRecording && uiState.recordedData.isNotEmpty()
            ) {
                Text("Export Current Data to CSV")
            }
            Spacer(modifier = Modifier.height(16.dp))
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
                modifier = Modifier.fillMaxWidth().height(500.dp).padding(16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("History", style = MaterialTheme.typography.headlineSmall)
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
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                            items(historyList) { measurement ->
                                HistoryItem(
                                    measurement = measurement,
                                    onViewClick = { viewModel.loadHistoryToGraph(measurement) },
                                    onExportClick = { viewModel.exportHistoryItem(measurement) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(
    measurement: MeasurementEntity,
    onViewClick: () -> Unit,
    onExportClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(Date(measurement.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = dateString, style = MaterialTheme.typography.titleSmall, color = Color.Black)
                Text(text = "ID: ${measurement.id}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }

            // ACTION BUTTONS
            Row {
                IconButton(onClick = onViewClick) {
                    Icon(Icons.Default.Info, contentDescription = "View", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onExportClick) {
                    Icon(Icons.Default.Share, contentDescription = "Export", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}