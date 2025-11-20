package kth.se.labb3.bluetoothapp.ui.measurement

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kth.se.labb3.bluetoothapp.data.database.AppDatabase
import kth.se.labb3.bluetoothapp.data.export.CsvExporter
import kth.se.labb3.bluetoothapp.data.processing.DataProcessor
import kth.se.labb3.bluetoothapp.data.repository.InternalSensorManager
import kth.se.labb3.bluetoothapp.data.repository.PolarSensorManager

class MeasurementViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MeasurementViewModel::class.java)) {
            val internalSensorManager = InternalSensorManager(context.applicationContext)
            val polarSensorManager = PolarSensorManager(context.applicationContext)
            val dataProcessor = DataProcessor()
            val csvExporter = CsvExporter(context.applicationContext)

            // Hämta Databas-DAO
            val database = AppDatabase.getDatabase(context)
            val measurementDao = database.measurementDao()

            @Suppress("UNCHECKED_CAST")
            return MeasurementViewModel(
                internalSensorManager,
                polarSensorManager,
                dataProcessor,
                csvExporter,
                measurementDao
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}