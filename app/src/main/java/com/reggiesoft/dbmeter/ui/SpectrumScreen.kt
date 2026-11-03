package com.reggiesoft.dbmeter.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reggiesoft.dbmeter.R
import com.reggiesoft.dbmeter.audio.SpectrumPoint
import com.reggiesoft.dbmeter.model.MeterState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun SpectrumScreen(state: MeterState, modifier: Modifier = Modifier) {
    var selection by remember(state.resetVersion) { mutableStateOf<FrequencySelection>(FrequencySelection.Peak) }
    val reading = state.measurement
    val focusedFrequency = when (val focus = selection) {
        FrequencySelection.Peak -> reading.peakFrequency
        is FrequencySelection.Manual -> focus.frequency
    }
    val selected = reading.spectrum.minByOrNull { abs(it.frequency - focusedFrequency) }
    val heldAtSelectedFrequency = selected?.let { selectedPoint ->
        reading.spectrumHold.minByOrNull { abs(it.frequency - selectedPoint.frequency) }
    }
    val selectedFrequencyLabel = selected?.frequency?.frequencyLabel() ?: "— Hz"
    val selectedPeakLevel = if (state.hasReading) {
        heldAtSelectedFrequency?.db?.level() ?: "—"
    } else {
        "—"
    }
    val selectedLiveLevel = if (state.hasReading) {
        selected?.db?.level() ?: "—"
    } else {
        "—"
    }
    Surface(modifier = modifier, color = PANEL, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth().background(INSET, RoundedCornerShape(6.dp)).padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.peak) + "  " + selectedFrequencyLabel, color = CORAL, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "$selectedPeakLevel dB",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.live) + "  " + selectedFrequencyLabel, color = TEAL, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "$selectedLiveLevel dB",
                        color = TEAL,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            SpectrumChart(
                spectrum = reading.spectrum, hold = reading.spectrumHold,
                marker = selected, onFrequency = { selection = FrequencySelection.Manual(it) },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
private fun SpectrumChart(spectrum: List<SpectrumPoint>, hold: List<SpectrumPoint>, marker: SpectrumPoint?, onFrequency: (Double) -> Unit, modifier: Modifier) {
    val density = LocalDensity.current
    val onFrequencyUpdate by rememberUpdatedState(onFrequency)
    val selectFrequency: (Offset, Int) -> Unit = { point, canvasWidth ->
        val left = with(density) { 28.dp.toPx() }
        val right = with(density) { 8.dp.toPx() }
        val fraction = ((point.x - left) / (canvasWidth - left - right)).coerceIn(0f, 1f)
        onFrequencyUpdate(20.0 * 1000.0.pow(fraction.toDouble()))
    }
    val paint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED.toArgb()
            textSize = with(density) { 9.sp.toPx() }
        }
    }
    val badgePaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEAL.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
    }
    val description = stringResource(R.string.spectrum_description)
    val markerDescription = if (marker != null) {
        "${marker.frequency.frequencyLabel()}, ${marker.db.level()} dB"
    } else {
        stringResource(R.string.ready)
    }
    Canvas(modifier.testTag("spectrum-chart").semantics {
        contentDescription = description
        stateDescription = markerDescription
    }.pointerInput(density) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var dragging = false
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!dragging && (change.position - down.position).getDistance() > touchSlop) {
                    dragging = true
                }
                if (dragging) {
                    change.consume()
                    selectFrequency(change.position, size.width)
                }
                if (!change.pressed) {
                    if (!dragging) {
                        selectFrequency(change.position, size.width)
                    }
                    break
                }
            }
        }
    }) {
        val left = 28.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 28.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        val width = right - left
        val height = bottom - top
        fun x(frequency: Double): Float = left + (log10(frequency / 20.0) / 3.0).toFloat() * width
        fun y(db: Double): Float = bottom - ((db - 20.0) / 90.0).coerceIn(0.0, 1.0).toFloat() * height
        val grid = Color(0xFF2A3437)
        for (level in 20..100 step 20) {
            val pos = y(level.toDouble())
            drawLine(grid, Offset(left, pos), Offset(right, pos), strokeWidth = 0.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f)))
            drawContext.canvas.nativeCanvas.drawText(level.toString(), 0f, pos + paint.textSize / 3, paint)
        }
        for (frequency in listOf(20.0, 100.0, 300.0, 1000.0, 3000.0, 10000.0, 20000.0)) {
            val pos = x(frequency)
            drawLine(grid.copy(alpha = 0.45f), Offset(pos, top), Offset(pos, bottom), strokeWidth = 0.5.dp.toPx())
            val label = if (frequency >= 1000) {
                "${(frequency / 1000).toInt()}k"
            } else {
                frequency.toInt().toString()
            }
            drawContext.canvas.nativeCanvas.drawText(label, (pos - paint.measureText(label) / 2).coerceIn(0f, size.width - paint.measureText(label)), bottom + 18.dp.toPx(), paint)
        }
        fun trace(points: List<SpectrumPoint>): Path {
            val path = Path()
            for ((index, point) in points.withIndex()) {
                if (index == 0) {
                    path.moveTo(x(point.frequency), y(point.db))
                } else {
                    path.lineTo(x(point.frequency), y(point.db))
                }
            }
            return path
        }
        if (spectrum.isNotEmpty()) {
            val live = trace(spectrum)
            val fill = Path().apply { addPath(live) }
            fill.lineTo(x(spectrum.last().frequency), bottom)
            fill.lineTo(x(spectrum.first().frequency), bottom)
            fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(TEAL.copy(alpha = 0.20f), TEAL.copy(alpha = 0.01f)), startY = top, endY = bottom))
            drawPath(trace(hold), CORAL.copy(alpha = 0.65f), style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))
            drawPath(live, TEAL, style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (marker != null) {
            val pos = Offset(x(marker.frequency), y(marker.db))
            drawLine(TEAL.copy(alpha = 0.5f), Offset(pos.x, top), Offset(pos.x, bottom), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 5f)))
            drawCircle(TEAL.copy(alpha = 0.15f), radius = 8.dp.toPx(), center = pos)
            drawCircle(TEAL, radius = 3.dp.toPx(), center = pos)
            val label = "${marker.frequency.frequencyLabel()} · ${marker.db.level()} dB"
            val labelWidth = badgePaint.measureText(label)
            drawContext.canvas.nativeCanvas.drawText(label, (pos.x - labelWidth / 2).coerceIn(left, (right - labelWidth).coerceAtLeast(left)), 14.dp.toPx(), badgePaint)
        }
    }
}

private fun Double.frequencyLabel(): String {
    return if (this >= 1000) {
        String.format(Locale.getDefault(), "%.1f kHz", this / 1000)
    } else {
        String.format(Locale.getDefault(), "%.0f Hz", this)
    }
}

private sealed interface FrequencySelection {
    data object Peak : FrequencySelection
    data class Manual(val frequency: Double) : FrequencySelection
}
