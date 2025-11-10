package kth.se.labb3.bluetoothapp.data.repository

import android.content.Context
import kth.se.labb3.bluetoothapp.data.model.SensorData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class InternalSensorManager(private val context: Context) : SensorRepository {

    // TODO: Implementera SensorManager här

    override val sensorDataFlow: Flow<SensorData>
        get() = emptyFlow() // TODO: Byt ut mot en riktig Flow

    override fun startListening() {
        // TODO: Registrera sensorlyssnare
    }

    override fun stopListening() {
        // TODO: Avregistrera sensorlyssnare
    }
}