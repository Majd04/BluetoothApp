package kth.se.labb3.bluetoothapp.data.processing

import kth.se.labb3.bluetoothapp.data.model.ProcessedData
import kth.se.labb3.bluetoothapp.data.model.SensorData
import kotlin.math.atan2
import kotlin.math.sqrt

class DataProcessor {
    private val alphaAlgo1: Float = 0.1f

    private val alphaAlgo2: Float = 0.98f


    private var lastAngleAlgo1: Float = 0f
    private var lastAngleAlgo2: Float = 0f
    private var lastTimestamp: Long = 0L


    fun reset() {
        lastAngleAlgo1 = 0f
        lastAngleAlgo2 = 0f
        lastTimestamp = 0L
    }


    fun processData(sensorData: SensorData): ProcessedData {

        val dt: Float = if (lastTimestamp == 0L) {
            0f
        } else {
            (sensorData.timestamp - lastTimestamp) / 1_000_000_000.0f // Från nanosekunder till sekunder
        }
        lastTimestamp = sensorData.timestamp

        val accelX = sensorData.linearAccel[0]
        val accelY = sensorData.linearAccel[1]
        val accelZ = sensorData.linearAccel[2]

        val angleFromAccel = atan2(accelY.toDouble(), accelZ.toDouble()).toFloat() * (180.0f / Math.PI.toFloat())

        val gyroX = sensorData.gyroscope[0]

        val currentAngleAlgo1 = (alphaAlgo1 * angleFromAccel) + ((1 - alphaAlgo1) * lastAngleAlgo1)
        lastAngleAlgo1 = currentAngleAlgo1

        val angleFromGyro = lastAngleAlgo2 + (gyroX * (180.0f / Math.PI.toFloat()) * dt)

        val currentAngleAlgo2 = (alphaAlgo2 * angleFromGyro) + ((1 - alphaAlgo2) * angleFromAccel)
        lastAngleAlgo2 = currentAngleAlgo2

        return ProcessedData(
            timestamp = sensorData.timestamp,
            angleAlgo1 = currentAngleAlgo1,
            angleAlgo2 = currentAngleAlgo2
        )
    }
}