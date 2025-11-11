package kth.se.labb3.bluetoothapp.ui.measurement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kth.se.labb3.bluetoothapp.ui.components.AngleChart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MeasurementScreen(
    viewModelFactory: MeasurementViewModelFactory
) {

    val viewModel: MeasurementViewModel = viewModel(factory = viewModelFactory)

    val uiState by viewModel.uiState.collectAsState()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            viewModel.exportDataToUri(it)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportEvent.collect {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "arm_elevation_$timestamp.csv"

            createDocumentLauncher.launch(fileName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Arm Elevation Measurement",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Current Angles:",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Algorithm 1:",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${"%.1f".format(uiState.currentAngleAlgo1)}°",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Blue
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Algorithm 2:",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${"%.1f".format(uiState.currentAngleAlgo2)}°",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Red
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.recordedData.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (uiState.isRecording) "Recording..." else "Measurement Result",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    AngleChart(
                        data = uiState.recordedData,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "—",
                                color = Color.Blue,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = " Algorithm 1",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.padding(horizontal = 16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "—",
                                color = Color.Red,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = " Algorithm 2",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Data points: ${uiState.recordedData.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Button(
            onClick = { viewModel.onStartStopClick() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (uiState.isRecording) "Stop Measurement" else "Start Measurement")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.onExportClick() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isRecording && uiState.recordedData.isNotEmpty()
        ) {
            Text(text = "Export Data to CSV")
        }

        if (uiState.recordedData.isEmpty() && !uiState.isRecording) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Press 'Start Measurement' to begin recording arm elevation data.\n\n" +
                        "Attach your phone to your arm and slowly raise it from 0° to 90°.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}