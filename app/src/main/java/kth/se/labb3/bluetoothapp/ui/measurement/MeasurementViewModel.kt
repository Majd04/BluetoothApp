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

// Events för engångshändelser som ska visas i UI (t.ex. Toast/Snackbar eller öppna filväljare)
sealed class UiEvent {
    object ExportFile : UiEvent()
    data class ShowError(val message: String) : UiEvent()
}

class MeasurementViewModel(
    private val internalSensorManager: InternalSensorManager,
    private val polarSensorManager: PolarSensorManager,
    private val dataProcessor: DataProcessor,
    private val csvExporter: CsvExporter,
    private val measurementDao: MeasurementDao // Databas-koppling
) : ViewModel() {

    // UI State - Håller all data som visas på skärmen
    private val _uiState = MutableStateFlow(MeasurementState())
    val uiState: StateFlow<MeasurementState> = _uiState.asStateFlow()

    // Kanal för att skicka events till UI (Felmeddelanden, Export)
    private val _eventChannel = Channel<UiEvent>()
    val events = _eventChannel.receiveAsFlow()

    // Hämta all historik från databasen (Flow uppdateras automatiskt om db ändras)
    val measurementHistory: Flow<List<MeasurementEntity>> = measurementDao.getAllMeasurements()

    // Intern lista för pågående mätning
    private val processedDataList = mutableListOf<ProcessedData>()
    private var dataCollectionJob: Job? = null

    init {
        // Lyssna på hittade Bluetooth-enheter
        polarSensorManager.scannedDevices
            .onEach { devices -> _uiState.update { it.copy(scannedDevices = devices) } }
            .launchIn(viewModelScope)

        // Lyssna på anslutningsstatus (Connected/Disconnected)
        polarSensorManager.isConnected
            .onEach { connected -> _uiState.update { it.copy(isBluetoothConnected = connected) } }
            .launchIn(viewModelScope)

        // Lyssna på fel från PolarSensorManager (t.ex. "Connection failed")
        polarSensorManager.connectionError
            .onEach { errorMsg -> _eventChannel.send(UiEvent.ShowError(errorMsg)) }
            .launchIn(viewModelScope)
    }

    // --- Sensor Inställningar ---
    fun toggleSensorSource(useExternal: Boolean) {
        // Låt inte användaren byta sensor mitt i en inspelning
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(useExternalSensor = useExternal) }
    }

    // --- Bluetooth Actions ---
    fun startScanning() = polarSensorManager.startScanning()

    fun stopScanning() = polarSensorManager.stopScanning()

    fun connectToDevice(device: PolarDeviceInfo) {
        polarSensorManager.stopScanning() // Sluta scanna när vi försöker ansluta
        polarSensorManager.connectToDevice(device.deviceId)
    }

    fun disconnectDevice() = polarSensorManager.disconnect()

    // --- Mätning Start/Stopp ---
    fun onStartStopClick() {
        if (_uiState.value.isRecording) {
            stopMeasurement()
        } else {
            startMeasurement()
        }
    }

    private fun startMeasurement() {
        // Välj vilken sensor vi ska lyssna på
        val activeRepository: SensorRepository = if (_uiState.value.useExternalSensor) {
            // Om extern sensor är vald men inte ansluten -> Visa fel
            if (!_uiState.value.isBluetoothConnected) {
                viewModelScope.launch { _eventChannel.send(UiEvent.ShowError("Not connected to any Polar sensor!")) }
                return
            }
            polarSensorManager
        } else {
            internalSensorManager
        }

        // Återställ data och processor
        dataProcessor.reset()
        processedDataList.clear()
        dataCollectionJob?.cancel()

        // Starta sensorn
        activeRepository.startListening()

        _uiState.update { it.copy(isRecording = true, recordedData = emptyList()) }

        // Samla in data
        dataCollectionJob = activeRepository.sensorDataFlow
            .onEach { sensorData ->
                // 1. Kör algoritmerna
                val processedData = dataProcessor.processData(sensorData)
                processedDataList.add(processedData)

                // 2. Uppdatera UI i realtid (Graf + Värden)
                _uiState.update {
                    it.copy(
                        currentAngleAlgo1 = processedData.angleAlgo1,
                        currentAngleAlgo2 = processedData.angleAlgo2,
                        recordedData = processedDataList.toList() // Uppdaterar grafen
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun stopMeasurement() {
        // Stoppa sensorer
        internalSensorManager.stopListening()
        polarSensorManager.stopListening()
        dataCollectionJob?.cancel()

        // Spara till Databas om vi har data
        if (processedDataList.isNotEmpty()) {
            saveMeasurementToDatabase()
        }

        _uiState.update { it.copy(isRecording = false) }
    }

    private fun saveMeasurementToDatabase() {
        viewModelScope.launch {
            try {
                // Konvertera listan till en enkel sträng för lagring (t.ex. CSV-format i en cell)
                // Detta sparar tid jämfört med att skapa en separat tabell för datapunkter
                val csvData = processedDataList.joinToString(separator = ";") {
                    "${it.timestamp},${it.angleAlgo1},${it.angleAlgo2}"
                }

                val entity = MeasurementEntity(
                    timestamp = System.currentTimeMillis(),
                    dataPointsCsv = csvData
                )

                measurementDao.insertMeasurement(entity)
                _eventChannel.send(UiEvent.ShowError("Measurement saved to History!"))
            } catch (e: Exception) {
                _eventChannel.send(UiEvent.ShowError("Failed to save history: ${e.message}"))
            }
        }
    }

    // --- Export ---
    fun onExportClick() {
        if (_uiState.value.isRecording || processedDataList.isEmpty()) return
        // Triggar UI att öppna filväljaren
        viewModelScope.launch { _eventChannel.send(UiEvent.ExportFile) }
    }

    fun exportDataToUri(uri: Uri) {
        viewModelScope.launch {
            csvExporter.exportDataAsCsv(processedDataList, uri)
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