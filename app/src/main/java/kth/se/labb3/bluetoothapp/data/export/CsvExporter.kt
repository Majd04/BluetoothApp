package kth.se.labb3.bluetoothapp.data.export

import android.content.Context
import android.net.Uri
import kth.se.labb3.bluetoothapp.data.model.ProcessedData
import java.io.IOException

class CsvExporter(private val context: Context) {

    /**
     * Exporterar en lista av ProcessedData till en CSV-fil vid den angivna Uri.
     */
    fun exportDataAsCsv(data: List<ProcessedData>, uri: Uri) {
        try {
            // Använd ContentResolver för att öppna en output stream till den valda filen
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                // Skriv till strömmen med en buffrad skrivare för effektivitet
                outputStream.bufferedWriter().use { writer ->
                    // Skriv CSV-huvudraden
                    writer.write("Timestamp (ns),AngleAlgo1 (degrees),AngleAlgo2 (degrees)")
                    writer.newLine()

                    // Iterera över datan och skriv varje rad
                    data.forEach { processedData ->
                        val line = "${processedData.timestamp},${processedData.angleAlgo1},${processedData.angleAlgo2}"
                        writer.write(line)
                        writer.newLine()
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            // TODO: Hantera felet, kanske skicka tillbaka ett felmeddelande
        }
    }
}