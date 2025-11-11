package kth.se.labb3.bluetoothapp.data.export

import android.content.Context
import android.net.Uri
import kth.se.labb3.bluetoothapp.data.model.ProcessedData
import java.io.IOException

class CsvExporter(private val context: Context) {

    fun exportDataAsCsv(data: List<ProcessedData>, uri: Uri) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.bufferedWriter().use { writer ->
                    writer.write("Timestamp (ns),AngleAlgo1 (degrees),AngleAlgo2 (degrees)")
                    writer.newLine()

                    data.forEach { processedData ->
                        val line = "${processedData.timestamp},${processedData.angleAlgo1},${processedData.angleAlgo2}"
                        writer.write(line)
                        writer.newLine()
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}