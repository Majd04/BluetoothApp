package kth.se.labb3.bluetoothapp.data.processing

import kth.se.labb3.bluetoothapp.data.model.ProcessedData
import kth.se.labb3.bluetoothapp.data.model.SensorData
import kotlin.math.atan2
import kotlin.math.sqrt

class DataProcessor {

    // Filterfaktor för Algoritm 1 (EWMA) [cite: 157]
    private val alphaAlgo1: Float = 0.1f // Justera denna (mellan 0 och 1)

    // Filterfaktor för Algoritm 2 (Complementary) [cite: 164]
    private val alphaAlgo2: Float = 0.98f // Justera denna (mellan 0 och 1)

    // Senaste tillstånd för filtren
    private var lastAngleAlgo1: Float = 0f
    private var lastAngleAlgo2: Float = 0f
    private var lastTimestamp: Long = 0L

    /**
     * Nollställer filtertillstånden.
     * Anropas när en ny mätning startar.
     */
    fun reset() {
        lastAngleAlgo1 = 0f
        lastAngleAlgo2 = 0f
        lastTimestamp = 0L
    }

    /**
     * Bearbetar rå sensordata och returnerar processad data med båda algoritmerna.
     */
    fun processData(sensorData: SensorData): ProcessedData {
        // Beräkna tidssteg (dt) i sekunder
        val dt: Float = if (lastTimestamp == 0L) {
            0f // Första mätningen
        } else {
            (sensorData.timestamp - lastTimestamp) / 1_000_000_000.0f // Från nanosekunder till sekunder
        }
        lastTimestamp = sensorData.timestamp

        // --- Steg 1: Beräkna vinkel från accelerometer ---
        // Vi antar att telefonen är fäst på armen så att Y-axeln pekar längs armen (0 grader)
        // och Z-axeln pekar ut från armen (se figur 2 i PDF:en [cite: 159]).
        // När armen lyfts 90 grader, kommer Z-axeln peka nedåt (mot gravitationen).
        // Vinkel (pitch) kan beräknas från ay och az.
        val accelX = sensorData.linearAccel[0]
        val accelY = sensorData.linearAccel[1]
        val accelZ = sensorData.linearAccel[2]

        // Vinkel från accelerometer (endast tillförlitlig vid låg/ingen rörelse)
        // atan2 hanterar alla kvadranter och division med noll.
        // Vi använder Y och Z enligt antagandet ovan.
        // Konvertera från radianer till grader
        val angleFromAccel = atan2(accelY.toDouble(), accelZ.toDouble()).toFloat() * (180.0f / Math.PI.toFloat())

        // --- Steg 2: Hämta vinkelhastighet från gyroskop ---
        // Vi antar att rotationen sker kring X-axeln (se figur 2 [cite: 159]).
        // Gyroskopet ger vinkelhastighet (radianer/sekund).
        val gyroX = sensorData.gyroscope[0]

        // --- Algoritm 1: EWMA-filter (på accelerometer-vinkeln) ---
        // Formel: y(n) = a*x(n) + (1-a)*y(n-1) [cite: 155]
        // x(n) = angleFromAccel
        // y(n-1) = lastAngleAlgo1
        val currentAngleAlgo1 = (alphaAlgo1 * angleFromAccel) + ((1 - alphaAlgo1) * lastAngleAlgo1)
        lastAngleAlgo1 = currentAngleAlgo1 // Spara för nästa iteration

        // --- Algoritm 2: Komplementärfilter (Sensor Fusion) ---
        // Formel: y(n) = a*x1(n) + (1-a)*x2(n) [cite: 162]
        // Här är det lite mer komplicerat.
        // x1(n) är den *integrerade* vinkeln från gyroskopet.
        // x2(n) är vinkeln från accelerometern.
        // 'a' (alpha) är filterfaktorn.

        // x1(n): Integrera gyroskopdata för att få en vinkel
        // Vinkel = Föregående vinkel + (vinkelhastighet * tidsteg)
        // Vi konverterar gyroX från rad/s till grader/s (* 180 / PI)
        val angleFromGyro = lastAngleAlgo2 + (gyroX * (180.0f / Math.PI.toFloat()) * dt)

        // x2(n): Vinkeln från accelerometer (angleFromAccel)

        // Kombinera dem med komplementärfiltret:
        // Vi litar mest på gyroskopet för snabba ändringar (integrerade vinkeln)
        // och "korrigerar" det långsamt med accelerometer-vinkeln (som är stabil men "brusig" vid rörelse).
        // y(n) = alpha * (integrerad gyro-vinkel) + (1 - alpha) * (accel-vinkel)
        val currentAngleAlgo2 = (alphaAlgo2 * angleFromGyro) + ((1 - alphaAlgo2) * angleFromAccel)
        lastAngleAlgo2 = currentAngleAlgo2 // Spara för nästa iteration

        return ProcessedData(
            timestamp = sensorData.timestamp,
            angleAlgo1 = currentAngleAlgo1,
            angleAlgo2 = currentAngleAlgo2
        )
    }
}