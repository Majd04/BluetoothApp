package kth.se.labb3.bluetoothapp.ui.theme.measurement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
// import androidx.lifecycle.viewmodel.compose.viewModel // Behövs sen

@Composable
fun MeasurementScreen(
    // viewModel: MeasurementViewModel = viewModel() // Avkommentera när ViewModel är redo
) {
    // val uiState by viewModel.uiState.collectAsState() // Avkommentera sen

    Column(modifier = Modifier.padding(16.dp)) {

        Text(text = "Algoritm 1: 0.0 °") // TODO: Byt ut mot uiState.currentAngleAlgo1
        Text(text = "Algoritm 2: 0.0 °") // TODO: Byt ut mot uiState.currentAngleAlgo2

        Button(onClick = { /* TODO: viewModel.onStartStopClick() */ }) {
            Text(text = "Start Measurement") // TODO: Ändra text baserat på uiState.isRecording
        }

        Button(onClick = { /* TODO: viewModel.onExportClick() */ }) {
            Text(text = "Export Data")
        }
    }
}