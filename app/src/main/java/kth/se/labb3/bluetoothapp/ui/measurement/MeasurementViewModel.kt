package kth.se.labb3.bluetoothapp.ui.measurement

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.sdk.api.model.PolarDeviceInfo
import kth.se.labb3.bluetoothapp.data.database.MeasurementDao
import kth.se.labb3.bluetoothapp.data.database.MeasurementEntity
import kth.se.labb3.bluetoothapp.data.export.CsvExporter
import kth.se.labb3.bluetoothapp.data.model.ProcessedData
import kth.se.labb3.bluetoothapp.data.processing.DataProcessor
import kth.se.labb3.bluetoothapp.data.repository.InternalSensorManager
import kth.se.labb3.bluetoothapp.data.repository.PolarSensorManager
import kth.se.labb3.bluetoothapp.data.repository.SensorRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class UiEvent {
    object ExportFile : UiEvent()
    data class ShowError(val message: String) : UiEvent()
    object CloseHistoryDialog : UiEvent() // Nytt event för att stänga dialogen automatiskt
}

class MeasurementViewModel(
    private val internalSensorManager: InternalSensorManager,
    private val polarSensorManager: PolarSensorManager,
    private val dataProcessor: DataProcessor,
    private val csvExporter: CsvExporter,
    private val measurementDao: MeasurementDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeasurementState())
    val uiState: StateFlow<MeasurementState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<UiEvent>()
    val events = _eventChannel.receiveAsFlow()

    val measurementHistory: Flow<List<MeasurementEntity>> = measurementDao.getAllMeasurements()

    // Lista för pågående mätning
    private val currentSessionData = mutableListOf<ProcessedData>()

    // Variabel som håller reda på vilken data vi ska exportera (Nuvarande eller Historisk)
    private var listToExport: List<ProcessedData> = emptyList()

    private var dataCollectionJob: Job? = null

    init {
        polarSensorManager.scannedDevices
            .onEach { devices -> _uiState.update { it.copy(scannedDevices = devices) } }
            .launchIn(viewModelScope)

        polarSensorManager.isConnected
            .onEach { connected -> _uiState.update { it.copy(isBluetoothConnected = connected) } }
            .launchIn(viewModelScope)

        polarSensorManager.connectionError
            .onEach { errorMsg -> _eventChannel.send(UiEvent.ShowError(errorMsg)) }
            .launchIn(viewModelScope)
    }

    // --- HISTORIK HANTERING ---

    // 1. Konvertera CSV-sträng tillbaka till objekt
    private fun parseCsvToData(csv: String): List<ProcessedData> {
        if (csv.isBlank()) return emptyList()
        return try {
            csv.split(";").mapNotNull { line ->
                val parts = line.split(",")
                if (parts.size == 3) {
                    ProcessedData(parts[0].toLong(), parts[1].toFloat(), parts[2].toFloat())
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 2. Ladda en gammal mätning och visa i grafen
    fun loadHistoryToGraph(entity: MeasurementEntity) {
        val loadedData = parseCsvToData(entity.dataPointsCsv)

        if (loadedData.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    recordedData = loadedData,
                    currentAngleAlgo1 = loadedData.last().angleAlgo1, // Visa sista värdet
                    currentAngleAlgo2 = loadedData.last().angleAlgo2
                )
            }
            viewModelScope.launch {
                _eventChannel.send(UiEvent.CloseHistoryDialog) // Stäng dialogen så man ser grafen
                _eventChannel.send(UiEvent.ShowError("Loaded measurement ID: ${entity.id}"))
            }
        }
    }

    // 3. Exportera en specifik historisk mätning
    fun exportHistoryItem(entity: MeasurementEntity) {
        val loadedData = parseCsvToData(entity.dataPointsCsv)
        if (loadedData.isNotEmpty()) {
            listToExport = loadedData // Sätt denna lista som mål för export
            viewModelScope.launch { _eventChannel.send(UiEvent.ExportFile) }
        }
    }

    // --- STANDARD FUNKTIONER ---

    fun toggleSensorSource(useExternal: Boolean) {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(useExternalSensor = useExternal) }
    }

    fun startScanning() = polarSensorManager.startScanning()
    fun stopScanning() = polarSensorManager.stopScanning()

    fun connectToDevice(device: PolarDeviceInfo) {
        polarSensorManager.stopScanning()
        polarSensorManager.connectToDevice(device.deviceId)
    }

    fun disconnectDevice() = polarSensorManager.disconnect()

    fun onStartStopClick() {
        if (_uiState.value.isRecording) stopMeasurement() else startMeasurement()
    }

    private fun startMeasurement() {
        val activeRepository: SensorRepository = if (_uiState.value.useExternalSensor) {
            if (!_uiState.value.isBluetoothConnected) {
                viewModelScope.launch { _eventChannel.send(UiEvent.ShowError("Not connected to sensor!")) }
                return
            }
            polarSensorManager
        } else {
            internalSensorManager
        }

        dataProcessor.reset()
        currentSessionData.clear()
        dataCollectionJob?.cancel()

        activeRepository.startListening()

        _uiState.update { it.copy(isRecording = true, recordedData = emptyList()) }

        dataCollectionJob = activeRepository.sensorDataFlow
            .onEach { sensorData ->
                val processedData = dataProcessor.processData(sensorData)
                currentSessionData.add(processedData)

                _uiState.update {
                    it.copy(
                        currentAngleAlgo1 = processedData.angleAlgo1,
                        currentAngleAlgo2 = processedData.angleAlgo2,
                        recordedData = currentSessionData.toList()
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun stopMeasurement() {
        internalSensorManager.stopListening()
        polarSensorManager.stopListening()
        dataCollectionJob?.cancel()

        if (currentSessionData.isNotEmpty()) {
            saveMeasurementToDatabase()
        }

        _uiState.update { it.copy(isRecording = false) }
    }

    private fun saveMeasurementToDatabase() {
        viewModelScope.launch {
            try {
                val csvData = currentSessionData.joinToString(separator = ";") {
                    "${it.timestamp},${it.angleAlgo1},${it.angleAlgo2}"
                }

                val entity = MeasurementEntity(
                    timestamp = System.currentTimeMillis(),
                    dataPointsCsv = csvData
                )
                measurementDao.insertMeasurement(entity)
                _eventChannel.send(UiEvent.ShowError("Measurement saved to History!"))
            } catch (e: Exception) {
                _eventChannel.send(UiEvent.ShowError("Failed save: ${e.message}"))
            }
        }
    }

    // --- EXPORT ---
    fun onExportCurrentDataClick() {
        if (_uiState.value.isRecording || currentSessionData.isEmpty()) return
        listToExport = currentSessionData // Exportera nuvarande session
        viewModelScope.launch { _eventChannel.send(UiEvent.ExportFile) }
    }

    fun exportDataToUri(uri: Uri) {
        viewModelScope.launch {
            // Här använder vi listan vi sparade i 'listToExport'
            csvExporter.exportDataAsCsv(listToExport, uri)
            _eventChannel.send(UiEvent.ShowError("Export successful!"))
        }
    }

    override fun onCleared() {
        internalSensorManager.stopListening()
        polarSensorManager.stopListening()
        polarSensorManager.onDestroy()
        super.onCleared()
    }
}