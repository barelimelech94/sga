package com.bar.sga.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bar.sga.R
import com.bar.sga.domain.formatKg

/** Simple line chart: [labels] are shown for the first and last point only. */
@Composable
fun LineChart(values: List<Double>, labels: List<String>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val text = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val res = LocalContext.current.resources
    val face = remember { res.getFont(R.font.rubik) }
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = text; textSize = 12.dp.toPx(); typeface = face; fontVariationSettings = "'wght' 400" }
        val left = 44.dp.toPx(); val right = 12.dp.toPx(); val top = 12.dp.toPx(); val bottom = 28.dp.toPx()
        val w = size.width - left - right; val h = size.height - top - bottom
        val lo = values.min(); val hi = values.max()
        val flat = hi == lo
        fun y(v: Double) = if (flat) top + h / 2 else top + h * (1 - ((v - lo) / (hi - lo)).toFloat())
        fun x(i: Int) = if (values.size == 1) left + w / 2 else left + w * i / (values.size - 1)
        // grid: min, max and (when they differ) two in between
        val ticks = if (flat) listOf(lo) else (0..3).map { lo + (hi - lo) * it / 3 }
        for (t in ticks) {
            drawLine(grid, Offset(left, y(t)), Offset(left + w, y(t)))
            drawContext.canvas.nativeCanvas.drawText(formatKg(Math.round(t * 10) / 10.0), 0f, y(t) + paint.textSize / 3, paint)
        }
        val path = Path().apply { values.indices.forEach { if (it == 0) moveTo(x(it), y(values[it])) else lineTo(x(it), y(values[it])) } }
        drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        values.indices.forEach { drawCircle(line, 4.dp.toPx(), Offset(x(it), y(values[it]))) }
        val base = size.height - 6.dp.toPx()
        val c = drawContext.canvas.nativeCanvas
        labels.firstOrNull()?.let { c.drawText(it, left, base, paint) }
        if (labels.size > 1) labels.last().let { c.drawText(it, size.width - right - paint.measureText(it), base, paint) }
    }
}
