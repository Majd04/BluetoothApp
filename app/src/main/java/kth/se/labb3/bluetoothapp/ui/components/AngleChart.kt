package kth.se.labb3.bluetoothapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kth.se.labb3.bluetoothapp.data.model.ProcessedData

@Composable
fun AngleChart(
    data: List<ProcessedData>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        return
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
    ) {
        val width = size.width
        val height = size.height
        val padding = 40f

        val minTime = data.first().timestamp
        val maxTime = data.last().timestamp
        val timeRange = (maxTime - minTime).toFloat()
        val minAngle = -10f
        val maxAngle = 100f
        val angleRange = maxAngle - minAngle

        for (angle in 0..90 step 30) {
            val y = height - padding - ((angle - minAngle) / angleRange) * (height - 2 * padding)
            drawLine(
                color = Color.LightGray,
                start = Offset(padding, y),
                end = Offset(width - padding, y),
                strokeWidth = 1f
            )
        }

        // Y-axel
        drawLine(
            color = Color.Black,
            start = Offset(padding, padding),
            end = Offset(padding, height - padding),
            strokeWidth = 2f
        )

        // X-axel
        drawLine(
            color = Color.Black,
            start = Offset(padding, height - padding),
            end = Offset(width - padding, height - padding),
            strokeWidth = 2f
        )

        fun dataToScreen(timestamp: Long, angle: Float): Offset {
            val x = if (timeRange > 0) {
                padding + ((timestamp - minTime).toFloat() / timeRange) * (width - 2 * padding)
            } else {
                padding
            }
            val y = height - padding - ((angle - minAngle) / angleRange) * (height - 2 * padding)
            return Offset(x, y)
        }

        if (data.size > 1) {
            val pathAlgo1 = Path()
            val firstPoint1 = dataToScreen(data[0].timestamp, data[0].angleAlgo1)
            pathAlgo1.moveTo(firstPoint1.x, firstPoint1.y)

            for (i in 1 until data.size) {
                val point = dataToScreen(data[i].timestamp, data[i].angleAlgo1)
                pathAlgo1.lineTo(point.x, point.y)
            }

            drawPath(
                path = pathAlgo1,
                color = Color.Blue,
                style = Stroke(width = 3f)
            )
        }

        if (data.size > 1) {
            val pathAlgo2 = Path()
            val firstPoint2 = dataToScreen(data[0].timestamp, data[0].angleAlgo2)
            pathAlgo2.moveTo(firstPoint2.x, firstPoint2.y)

            for (i in 1 until data.size) {
                val point = dataToScreen(data[i].timestamp, data[i].angleAlgo2)
                pathAlgo2.lineTo(point.x, point.y)
            }

            drawPath(
                path = pathAlgo2,
                color = Color.Red,
                style = Stroke(width = 3f)
            )
        }
    }
}