package kth.se.labb3.bluetoothapp.ui.measurement

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kth.se.labb3.bluetoothapp.data.export.CsvExporter
import kth.se.labb3.bluetoothapp.data.model.ProcessedData
import kth.se.labb3.bluetoothapp.data.processing.DataProcessor
import kth.se.labb3.bluetoothapp.data.repository.SensorRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MeasurementViewModel(
    private val sensorRepository: SensorRepository,
    private val dataProcessor: DataProcessor,
    private val csvExporter: CsvExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeasurementState())
    val uiState: StateFlow<MeasurementState> = _uiState.asStateFlow()

    private val _exportEvent = MutableSharedFlow<Unit>()
    val exportEvent = _exportEvent.asSharedFlow()

    private val processedDataList = mutableListOf<ProcessedData>()

    private var dataCollectionJob: Job? = null

    fun onStartStopClick() {
        val isCurrentlyRecording = _uiState.value.isRecording
        if (isCurrentlyRecording) {
            stopMeasurement()
        } else {
            startMeasurement()
        }
    }

    private fun startMeasurement() {
        dataProcessor.reset()
        processedDataList.clear()
        dataCollectionJob?.cancel()

        sensorRepository.startListening()

        _uiState.update { it.copy(isRecording = true, recordedData = emptyList()) }

        dataCollectionJob = sensorRepository.sensorDataFlow
            .onEach { sensorData ->
                android.util.Log.d("ViewModel", "DATA MOTTAGEN: Y=${sensorData.linearAccel[1]}")
                val processedData = dataProcessor.processData(sensorData)

                processedDataList.add(processedData)

                _uiState.update {
                    it.copy(
                        currentAngleAlgo1 = processedData.angleAlgo1,
                        currentAngleAlgo2 = processedData.angleAlgo2,
                       // recordedData = processedDataList.toList()
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun stopMeasurement() {
        sensorRepository.stopListening()

        dataCollectionJob?.cancel()

        _uiState.update {
            it.copy(
                isRecording = false,
                recordedData = processedDataList.toList()
            )
        }
    }

    fun onExportClick() {
        if (_uiState.value.isRecording) {
            return
        }

        if (processedDataList.isEmpty()) {
            return
        }

        viewModelScope.launch {
            _exportEvent.emit(Unit)
        }
    }

    fun exportDataToUri(uri: Uri) {
        viewModelScope.launch {
            csvExporter.exportDataAsCsv(processedDataList, uri)
        }
    }

    override fun onCleared() {
        sensorRepository.stopListening()
        super.onCleared()
    }
}