package kth.se.labb3.bluetoothapp.data.processing

import kth.se.labb3.bluetoothapp.data.model.ProcessedData
import kth.se.labb3.bluetoothapp.data.model.SensorData

class DataProcessor {

    // TODO: Lägg till logik för Algoritm 1 & 2 här

    fun processNewData(data: SensorData): ProcessedData {
        // TODO: Implementera algoritmerna
        return ProcessedData(data.timestamp, 0f, 0f)
    }

    fun getRecordedDataForExport(): List<ProcessedData> {
        // TODO: Returnera en lista med sparad data
        return emptyList()
    }

    fun clearData() {
        // TODO: Rensa sparad data
    }
}