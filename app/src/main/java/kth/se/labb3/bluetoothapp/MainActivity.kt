package kth.se.labb3.bluetoothapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kth.se.labb3.bluetoothapp.ui.measurement.MeasurementViewModelFactory
import kth.se.labb3.bluetoothapp.ui.theme.BluetoothAppTheme
import kth.se.labb3.bluetoothapp.ui.theme.measurement.MeasurementScreen

class MainActivity : ComponentActivity() {

    // Launcher för att fråga om behörigheter
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Här kan du hantera om användaren nekade behörigheter,
            // men för labben antar vi att de godkänner dem.
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                // Behörighet given
            } else {
                // Behörighet nekad, appen kommer inte att kunna skanna efter BLE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Be om behörigheter
        requestBluetoothPermissions()

        setContent {
            BluetoothAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Skapa vår ViewModel Factory och skicka in applikationskontexten
                    val context = LocalContext.current
                    val viewModelFactory = MeasurementViewModelFactory(context.applicationContext)

                    // Visa vår huvudskärm
                    MeasurementScreen(viewModelFactory = viewModelFactory)
                }
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Från Android 12 (API 31) behövs BLUETOOTH_SCAN och BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        // Före Android 12 (API < 31) behövs ACCESS_FINE_LOCATION för BLE-skanning
        else {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}