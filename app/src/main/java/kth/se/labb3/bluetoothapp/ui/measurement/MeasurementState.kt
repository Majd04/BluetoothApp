package kth.se.labb3.bluetoothapp.ui.measurement

import kth.se.labb3.bluetoothapp.data.model.ProcessedData

data class MeasurementState(
    val isRecording: Boolean = false,
    val currentAngleAlgo1: Float = 0f,
    val currentAngleAlgo2: Float = 0f,
    val recordedData: List<ProcessedData> = emptyList()
)