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
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val gyroscopeSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // En SharedFlow är bra för "events" som detta.
    // Vi behöver inte veta om någon lyssnar, vi bara skickar data.
    private val _sensorDataFlow = MutableSharedFlow<SensorData>()
    override val sensorDataFlow: Flow<SensorData> = _sensorDataFlow.asSharedFlow()

    // Vi behöver spara de senaste värdena från båda sensorerna,
    // eftersom de levererar data oberoende av varandra.
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
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                System.arraycopy(event.values, 0, lastAccelData, 0, 3)
            }
            Sensor.TYPE_GYROSCOPE -> {
                System.arraycopy(event.values, 0, lastGyroData, 0, 3)
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            _sensorDataFlow.tryEmit(
                SensorData(
                    timestamp = event.timestamp,
                    linearAccel = lastAccelData.clone(),
                    gyroscope = lastGyroData.clone()
                )
            )
        }
    }

    //*override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Behövs inte för denna lab, men måste implementeras
    }*//
}