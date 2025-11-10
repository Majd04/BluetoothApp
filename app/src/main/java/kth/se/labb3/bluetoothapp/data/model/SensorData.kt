package kth.se.labb3.bluetoothapp.data.model

data class SensorData(
    val timestamp: Long,
    val linearAccel: FloatArray,
    val gyroscope: FloatArray
)