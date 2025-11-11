package kth.se.labb3.bluetoothapp.ui.theme.measurement

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

// Injicera dina klasser. Vi använder SensorRepository-interfacet för flexibilitet
class MeasurementViewModel(
    private val sensorRepository: SensorRepository, // Nu är det InternalSensorManager
    private val dataProcessor: DataProcessor,
    private val csvExporter: CsvExporter
) : ViewModel() {

    // Privat StateFlow som vi kan ändra
    private val _uiState = MutableStateFlow(MeasurementState())
    // Publik, oföränderlig StateFlow som UI:t kan observera
    val uiState: StateFlow<MeasurementState> = _uiState.asStateFlow()

    // En "Event"-flöde för att skicka engångshändelser till UI:t, som "visa filspara-dialog"
    private val _exportEvent = MutableSharedFlow<Unit>()
    val exportEvent = _exportEvent.asSharedFlow()

    // Lista för att spara all processad data under en mätning
    private val processedDataList = mutableListOf<ProcessedData>()

    // Håller koll på vår insamlings-job
    private var dataCollectionJob: Job? = null

    fun onStartStopClick() {
        val isCurrentlyRecording = _uiState.value.isRecording
        if (isCurrentlyRecording) {
            // Stoppa mätningen
            stopMeasurement()
        } else {
            // Starta mätningen
            startMeasurement()
        }
    }

    private fun startMeasurement() {
        // Nollställ allt
        dataProcessor.reset()
        processedDataList.clear()
        dataCollectionJob?.cancel() // Avbryt tidigare jobb om det finns

        // Starta sensorlyssnaren
        sensorRepository.startListening()

        // Uppdatera UI-tillståndet
        _uiState.update { it.copy(isRecording = true) }

        // Starta insamling av data i en ny coroutine
        dataCollectionJob = sensorRepository.sensorDataFlow
            .onEach { sensorData ->
                // Bearbeta rådata
                val processedData = dataProcessor.processData(sensorData)

                // Spara data till listan
                processedDataList.add(processedData)

                // Uppdatera UI med de senaste vinklarna
                _uiState.update {
                    it.copy(
                        currentAngleAlgo1 = processedData.angleAlgo1,
                        currentAngleAlgo2 = processedData.angleAlgo2
                    )
                }
            }
            .launchIn(viewModelScope) // Körs inom ViewModel:s livscykel
    }

    private fun stopMeasurement() {
        // Stoppa sensorlyssnaren
        sensorRepository.stopListening()

        // Avbryt datainsamlingsjobbet
        dataCollectionJob?.cancel()

        // Uppdatera UI-tillståndet
        _uiState.update {
            it.copy(
                isRecording = false,
                currentAngleAlgo1 = 0f, // Nollställ vinklar i UI
                currentAngleAlgo2 = 0f
            )
        }
    }

    fun onExportClick() {
        if (_uiState.value.isRecording) {
            // Kan inte exportera under mätning
            // TODO: Visa kanske ett meddelande till användaren?
            return
        }

        if (processedDataList.isEmpty()) {
            // Ingen data att exportera
            // TODO: Visa meddelande
            return
        }

        // Skicka en händelse till UI:t för att öppna fildialogen
        viewModelScope.launch {
            _exportEvent.emit(Unit)
        }
    }

    /**
     * Kallas från UI:t när användaren har valt en fil-URI.
     */
    fun exportDataToUri(uri: Uri) {
        viewModelScope.launch {
            csvExporter.exportDataAsCsv(processedDataList, uri)
            // TODO: Visa "Export klar!"-meddelande
        }
    }

    override fun onCleared() {
        // Städa upp när ViewModel förstörs
        sensorRepository.stopListening()
        super.onCleared()
    }
}