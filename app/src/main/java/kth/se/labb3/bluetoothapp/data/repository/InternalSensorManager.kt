package kth.se.labb3.bluetoothapp.data.repository

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kth.se.labb3.bluetoothapp.data.model.SensorData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class InternalSensorManager(
    private val context: Context
) : SensorRepository, SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val linearAccelSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gyroscopeSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val _sensorDataFlow = MutableSharedFlow<SensorData>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    override val sensorDataFlow: Flow<SensorData> = _sensorDataFlow.asSharedFlow()
    private var lastAccelData = FloatArray(3)
    private var lastGyroData = FloatArray(3)

    init {
        lastAccelData.fill(0f)
        lastGyroData.fill(0f)
    }

    override fun startListening() {
        Log.d("InternalSensorManager", "Startar sensorlyssnare")
        sensorManager.registerListener(this, linearAccelSensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun stopListening() {
        Log.d("InternalSensorManager", "Stoppar sensorlyssnare")
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, lastAccelData, 0, 3)
            }
            Sensor.TYPE_GYROSCOPE -> {
                System.arraycopy(event.values, 0, lastGyroData, 0, 3)
            }
            else -> return
        }

        _sensorDataFlow.tryEmit(
            SensorData(
                timestamp = event.timestamp,
                linearAccel = lastAccelData.clone(),
                gyroscope = lastGyroData.clone()
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}