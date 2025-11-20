package kth.se.labb3.bluetoothapp.ui.measurement

import com.polar.sdk.api.model.PolarDeviceInfo
import kth.se.labb3.bluetoothapp.data.model.ProcessedData

data class MeasurementState(
    val isRecording: Boolean = false,
    val currentAngleAlgo1: Float = 0f,
    val currentAngleAlgo2: Float = 0f,
    val recordedData: List<ProcessedData> = emptyList(),

    val useExternalSensor: Boolean = false, // True = Polar, False = Intern
    val scannedDevices: List<PolarDeviceInfo> = emptyList(),
    val isBluetoothConnected: Boolean = false
)