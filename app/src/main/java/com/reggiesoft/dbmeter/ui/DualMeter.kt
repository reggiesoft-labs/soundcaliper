package com.reggiesoft.dbmeter.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reggiesoft.dbmeter.R
import com.reggiesoft.dbmeter.model.MeterState
import java.util.Locale

@Composable
fun DualMeter(state: MeterState, modifier: Modifier = Modifier) {
    val reading = state.measurement
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = PANEL) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReadingCard(
                    label = "dBA",
                    value = reading.aDb,
                    peak = reading.aPeak,
                    average = reading.aAverage,
                    hasReading = state.hasReading,
                    color = TEAL,
                    tag = "a",
                    modifier = Modifier.weight(1f)
                )
                ReadingCard(
                    label = "dBC",
                    value = reading.cDb,
                    peak = reading.cPeak,
                    average = reading.cAverage,
                    hasReading = state.hasReading,
                    color = Color(0xFFFFE0A1),
                    tag = "c",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(20.dp))
            Surface(color = INSET, shape = RoundedCornerShape(50)) {
                Text(
                    text = if (state.hasReading) {
                        "Δ ${signedLevel(reading.cDb - reading.aDb)} dB"
                    } else {
                        "Δ — dB"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 10.sp, color = Color(0xFFFFE0A1), fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            LevelBars(state, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun ReadingCard(label: String, value: Double, peak: Double, average: Double, hasReading: Boolean, color: Color, tag: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Column(Modifier.fillMaxWidth().background(INSET, RoundedCornerShape(6.dp)).padding(top = 10.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = color.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (hasReading) {
                    value.level()
                } else {
                    "—"
                },
                modifier = Modifier.testTag("meter-reading-$tag"),
                color = color,
                fontSize = 46.sp,
                lineHeight = 55.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2).sp,
                maxLines = 1
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Statistic(
                label = stringResource(R.string.peak),
                value = if (hasReading) { peak.level() } else { "—" },
                color = CORAL,
                modifier = Modifier.weight(1f)
            )
            Statistic(
                label = stringResource(R.string.average),
                value = if (hasReading) { average.level() } else { "—" },
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Statistic(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.background(INSET, RoundedCornerShape(4.dp)).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = MUTED)
        Text(value, fontSize = 14.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LevelBars(state: MeterState, modifier: Modifier) {
    val density = LocalDensity.current
    val paint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED.toArgb()
            textSize = with(density) { 11.sp.toPx() }
        }
    }
    val description = stringResource(R.string.dual_bar_description)
    Canvas(modifier.semantics { contentDescription = description }) {
        val top = 8.dp.toPx()
        val bottom = size.height - 16.dp.toPx()
        val height = bottom - top
        val width = 30.dp.toPx()
        val gap = 16.dp.toPx()
        val first = size.width / 2 - gap / 2 - width
        val second = size.width / 2 + gap / 2
        fun y(db: Double): Float = bottom - ((db - 30.0) / 80.0).coerceIn(0.0, 1.0).toFloat() * height
        for (db in 30..110 step 20) {
            val label = db.toString()
            val textWidth = paint.measureText(label)
            drawContext.canvas.nativeCanvas.drawText(label, first - textWidth - 16.dp.toPx(), y(db.toDouble()) + paint.textSize / 3, paint)
            drawContext.canvas.nativeCanvas.drawText(label, second + width + 16.dp.toPx(), y(db.toDouble()) + paint.textSize / 3, paint)
        }
        val levels = listOf(state.measurement.aDb to state.measurement.aPeak, state.measurement.cDb to state.measurement.cPeak)
        for ((index, pair) in levels.withIndex()) {
            val x = if (index == 0) { first } else { second }
            val color = if (index == 0) { TEAL } else { AMBER }
            drawRoundRect(color = INSET, topLeft = Offset(x, top), size = Size(width, height), cornerRadius = CornerRadius(5.dp.toPx()))
            if (state.hasReading) {
                val levelTop = y(pair.first)
                drawRoundRect(color = color, topLeft = Offset(x + 3.dp.toPx(), levelTop), size = Size(width - 6.dp.toPx(), bottom - levelTop), cornerRadius = CornerRadius(2.dp.toPx()))
                drawLine(CORAL, Offset(x + 4.dp.toPx(), y(pair.second)), Offset(x + width - 4.dp.toPx(), y(pair.second)), strokeWidth = 2.dp.toPx())
            }
        }
    }
}

internal fun Double.level(): String = String.format(Locale.getDefault(), "%.1f", this)
private fun signedLevel(value: Double): String = String.format(Locale.getDefault(), "%+.1f", value)
