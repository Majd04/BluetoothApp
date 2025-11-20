package kth.se.labb3.bluetoothapp.data.repository

import android.content.Context
import android.util.Log
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarSensorSetting
import kth.se.labb3.bluetoothapp.data.model.SensorData
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.UUID

class PolarSensorManager(
    context: Context
) : SensorRepository {

    private val api: PolarBleApi = PolarBleApiDefaultImpl.defaultImplementation(
        context,
        setOf(
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
            PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
            PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
        )
    )

    private val _sensorDataFlow = MutableSharedFlow<SensorData>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val sensorDataFlow: Flow<SensorData> = _sensorDataFlow.asSharedFlow()

    // För scanning
    private val _scannedDevices = MutableStateFlow<List<PolarDeviceInfo>>(emptyList())
    val scannedDevices = _scannedDevices.asStateFlow()

    // Anslutningsstatus
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _connectionError = MutableSharedFlow<String>()
    val connectionError = _connectionError.asSharedFlow()

    private var connectedDeviceId: String = ""
    private val disposables = CompositeDisposable()

    // Spara senaste värdena för att kunna kombinera Acc + Gyro
    private var lastAccel = FloatArray(3)
    private var lastGyro = FloatArray(3)

    init {
        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d("Polar", "BLE Power: $powered")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d("Polar", "CONNECTED: ${polarDeviceInfo.deviceId}")
                connectedDeviceId = polarDeviceInfo.deviceId
                _isConnected.value = true
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d("Polar", "CONNECTING: ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d("Polar", "DISCONNECTED: ${polarDeviceInfo.deviceId}")
                _isConnected.value = false
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {}
            override fun batteryLevelReceived(identifier: String, level: Int) {}
        })
    }

    // --- Scanning ---
    fun startScanning() {
        _scannedDevices.value = emptyList()
        val stream = api.searchForDevice()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { foundDevice: PolarDeviceInfo ->
                    // Krav: "Only Polar devices should display"
                    if (foundDevice.name.contains("Polar")) {
                        val currentList = _scannedDevices.value.toMutableList()
                        if (currentList.none { it.deviceId == foundDevice.deviceId }) {
                            currentList.add(foundDevice)
                            _scannedDevices.value = currentList
                        }
                    }
                },
                { error -> Log.e("Polar", "Scan error: $error") }
            )
        disposables.add(stream)
    }

    fun stopScanning() {
        // searchForDevice stängs ofta av sig själv, men vi kan rensa disposables om vi vill avbryta
    }

    // --- Connection ---
    fun connectToDevice(deviceId: String) {
        try {
            api.connectToDevice(deviceId)
        } catch (e: PolarInvalidArgument) {
            Log.e("Polar", "Failed to connect: $e")
            _connectionError.tryEmit("Kunde inte ansluta till $deviceId")
        }
    }

    fun disconnect() {
        if (connectedDeviceId.isNotEmpty()) {
            api.disconnectFromDevice(connectedDeviceId)
        }
    }

    // --- Data Streaming (Krav: Start recording) ---
    override fun startListening() {
        if (connectedDeviceId.isEmpty() || !_isConnected.value) {
            _connectionError.tryEmit("Ingen sensor ansluten!")
            return
        }

        // Starta Accelerometer
        val accDisposable = api.requestStreamSettings(connectedDeviceId, PolarBleApi.PolarDeviceDataType.ACC)
            .toFlowable()
            .flatMap { settings: com.polar.sdk.api.model.PolarSensorSetting ->
                api.startAccStreaming(connectedDeviceId, settings.maxSettings())
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { polarAccelData: com.polar.sdk.api.model.PolarAccelerometerData ->
                    // Ta första samplet i paketet
                    for (sample in polarAccelData.samples) {
                        // Polar ger mG (milli-G), vi vill ha m/s^2 eller G beroende på din algoritm.
                        // Androids interna sensor ger m/s^2.
                        // 1000 mG = 1 G ≈ 9.81 m/s^2.
                        // Låt oss konvertera till m/s^2 för att matcha intern sensor:
                        val x = (sample.x / 1000f) * 9.81f
                        val y = (sample.y / 1000f) * 9.81f
                        val z = (sample.z / 1000f) * 9.81f

                        lastAccel[0] = x
                        lastAccel[1] = y
                        lastAccel[2] = z

                        emitData(sample.timeStamp)
                    }
                },
                { error: Throwable -> Log.e("Polar", "ACC error: $error") }
            )
        disposables.add(accDisposable)

        // Starta Gyroskop (om tillgängligt, Verity Sense har det)
        val gyroDisposable = api.requestStreamSettings(connectedDeviceId, PolarBleApi.PolarDeviceDataType.GYRO)
            .toFlowable()
            .flatMap { settings: com.polar.sdk.api.model.PolarSensorSetting ->
                api.startGyroStreaming(connectedDeviceId, settings.maxSettings())
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { polarGyroData: com.polar.sdk.api.model.PolarGyroData ->
                    for (sample in polarGyroData.samples) {
                        // Polar ger deg/s. Androids interna ger rad/s.
                        // Konvertera deg/s till rad/s för att matcha din DataProcessor
                        val radScale = (Math.PI.toFloat() / 180.0f)
                        lastGyro[0] = sample.x * radScale
                        lastGyro[1] = sample.y * radScale
                        lastGyro[2] = sample.z * radScale

                        emitData(sample.timeStamp)
                    }
                },
                { error: Throwable -> Log.e("Polar", "GYRO error: $error") }
            )
        disposables.add(gyroDisposable)
    }

    private fun emitData(timestamp: Long) {
        // Timestamp från Polar är nanosekunder från uppstart eller liknande.
        // Vi skickar det vidare.
        _sensorDataFlow.tryEmit(
            SensorData(
                timestamp = timestamp, // Eller System.nanoTime() om du vill synka med telefonens klocka
                linearAccel = lastAccel.clone(),
                gyroscope = lastGyro.clone()
            )
        )
    }

    override fun stopListening() {
        disposables.clear()
    }

    // Cleanup
    fun onDestroy() {
        api.shutDown()
        disposables.dispose()
    }
}