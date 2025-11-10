package kth.se.labb3.bluetoothapp.data.repository

import kth.se.labb3.bluetoothapp.data.model.SensorData
import kotlinx.coroutines.flow.Flow

interface SensorRepository {
    val sensorDataFlow: Flow<SensorData>
    fun startListening()
    fun stopListening()
}