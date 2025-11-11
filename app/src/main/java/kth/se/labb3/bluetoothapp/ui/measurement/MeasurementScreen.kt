package kth.se.labb3.bluetoothapp.ui.theme.measurement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kth.se.labb3.bluetoothapp.ui.measurement.MeasurementViewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MeasurementScreen(
    viewModelFactory: MeasurementViewModelFactory // Ta emot fabriken från MainActivity
) {
    // Skapa vår ViewModel med hjälp av fabriken
    val viewModel: MeasurementViewModel = viewModel(factory = viewModelFactory)

    // Hämta det aktuella UI-tillståndet från ViewModel
    val uiState by viewModel.uiState.collectAsState()

    // Skapa en launcher för att be användaren var filen ska sparas
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        // Detta körs när användaren har valt en plats och ett filnamn
        uri?.let {
            viewModel.exportDataToUri(it)
        }
    }

    // Lyssna efter export-händelser från ViewModel
    LaunchedEffect(Unit) {
        viewModel.exportEvent.collect {
            // Skapa ett standardfilnamn
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "arm_elevation_$timestamp.csv"

            // Visa "Spara som"-dialogen
            createDocumentLauncher.launch(fileName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Algoritm 1: ${"%.2f".format(uiState.currentAngleAlgo1)} °",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Algoritm 2: ${"%.2f".format(uiState.currentAngleAlgo2)} °",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = { viewModel.onStartStopClick() }) {
            Text(text = if (uiState.isRecording) "Stop Measurement" else "Start Measurement")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.onExportClick() },
            // Inaktivera knappen om vi spelar in eller om listan är tom
            enabled = !uiState.isRecording
        ) {
            Text(text = "Export Data")
        }
    }
}