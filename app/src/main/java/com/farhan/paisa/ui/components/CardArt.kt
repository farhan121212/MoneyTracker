package com.farhan.paisa.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.farhan.paisa.ui.theme.Pop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

const val CARD_PATTERNS = 8

/**
 * Artwork behind a card's text, drawn in a faint tone of near-black. It stays faint on purpose:
 * the balance sits on top of it and has to remain easy to read.
 */
fun DrawScope.drawCardArt(pattern: Int, ink: Color = Pop.OnPop.copy(alpha = 0.085f)) {
    clipRect {
        when (pattern.mod(CARD_PATTERNS)) {
            0 -> rings(ink)
            1 -> waves(ink)
            2 -> dots(ink)
            3 -> stripes(ink)
            4 -> blocks(ink)
            5 -> flower(ink)
            6 -> arches(ink)
            else -> rays(ink)
        }
    }
}

private fun DrawScope.rings(ink: Color) {
    val center = Offset(size.width * 0.92f, size.height * 0.1f)
    listOf(0.5f, 0.82f, 1.14f).forEach { factor ->
        drawCircle(ink, size.height * factor, center, style = Stroke(size.height * 0.11f))
    }
}

private fun DrawScope.waves(ink: Color) {
    val stroke = size.height * 0.1f
    repeat(4) { row ->
        val baseline = size.height * (0.22f + row * 0.22f)
        val rise = size.height * 0.16f
        val path = Path().apply {
            moveTo(0f, baseline)
            var x = 0f
            val step = size.width / 2
            var up = true
            while (x < size.width) {
                val bend = if (up) -rise else rise
                cubicTo(x + step * 0.4f, baseline + bend, x + step * 0.6f, baseline + bend, x + step, baseline)
                x += step
                up = !up
            }
        }
        drawPath(path, ink, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

private fun DrawScope.dots(ink: Color) {
    val gap = size.height * 0.15f
    val startX = size.width * 0.42f
    var row = 0
    var y = gap / 2
    while (y < size.height) {
        var x = startX + (if (row % 2 == 0) 0f else gap / 2)
        while (x < size.width + gap) {
            // The dots grow towards the corner, so the pattern fades in behind the text.
            val weight = ((x - startX) / (size.width - startX)).coerceIn(0.15f, 1f)
            drawCircle(ink, gap * 0.36f * weight, Offset(x, y))
            x += gap
        }
        y += gap
        row += 1
    }
}

private fun DrawScope.stripes(ink: Color) {
    val stroke = size.height * 0.12f
    val gap = stroke * 2.2f
    var x = size.width * 0.3f
    while (x < size.width + size.height) {
        drawLine(ink, Offset(x, size.height), Offset(x + size.height, 0f), stroke)
        x += gap
    }
}

private fun DrawScope.blocks(ink: Color) {
    val unit = size.height * 0.5f
    val left = size.width - unit * 2
    drawCircle(ink, unit / 2, Offset(left + unit / 2, unit / 2))
    drawRect(ink, Offset(left + unit, 0f), Size(unit, unit))
    drawArc(ink, 180f, 90f, true, Offset(left, unit), Size(unit * 2, unit * 2))
    drawArc(ink, 90f, 180f, true, Offset(left + unit * 0.5f, unit), Size(unit, unit))
}

private fun DrawScope.flower(ink: Color) {
    val center = Offset(size.width * 0.84f, size.height * 0.34f)
    val petal = min(size.width, size.height) * 0.27f
    repeat(6) { index ->
        val angle = index * PI / 3
        val at = Offset(center.x + (cos(angle) * petal).toFloat(), center.y + (sin(angle) * petal).toFloat())
        drawCircle(ink, petal * 0.74f, at)
    }
}

private fun DrawScope.arches(ink: Color) {
    val stroke = size.height * 0.1f
    repeat(3) { column ->
        val centerX = size.width * (0.5f + column * 0.25f)
        listOf(0.26f, 0.5f).forEach { factor ->
            val radius = size.height * factor
            drawArc(
                ink,
                180f,
                180f,
                false,
                Offset(centerX - radius, size.height - radius),
                Size(radius * 2, radius * 2),
                style = Stroke(stroke),
            )
        }
    }
}

private fun DrawScope.rays(ink: Color) {
    val origin = Offset(size.width, 0f)
    val length = size.width
    repeat(9) { index ->
        val angle = (90.0 + index * 11.25) * PI / 180
        val end = Offset(origin.x + (cos(angle) * length).toFloat(), origin.y + (sin(angle) * length).toFloat())
        drawLine(ink, origin, end, 8.dp.toPx(), StrokeCap.Round)
    }
    drawCircle(ink, size.height * 0.3f, origin)
}
