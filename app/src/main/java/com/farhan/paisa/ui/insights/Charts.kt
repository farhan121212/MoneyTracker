package com.farhan.paisa.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farhan.paisa.domain.Money
import com.farhan.paisa.ui.components.LegendDot
import com.farhan.paisa.ui.theme.Jakarta
import com.farhan.paisa.ui.theme.Pop
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

private val AxisText = TextStyle(fontFamily = Jakarta, fontSize = 11.sp, color = Pop.TextMuted)
private val BubbleText = TextStyle(fontFamily = Jakarta, fontSize = 13.sp, color = Pop.OnPop, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)

private fun niceCeiling(value: Long): Long {
    var step = 100_00L
    while (step * 10 < value) step *= 10
    return listOf(1, 2, 3, 4, 5, 6, 8, 10).first { step * it >= value } * step
}

/**
 * Money spent so far this month against last month and the limit, on one rupee axis.
 * Drag across it to read the exact total for any day.
 */
@Composable
fun SpendCurve(
    current: LongArray,
    previous: LongArray,
    budget: Long,
    /** How many days of [current] have happened. The line stops there. */
    daysElapsed: Int,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    var scrub by remember(current, previous) { mutableStateOf<Int?>(null) }
    val days = max(current.size, 28)
    val top = niceCeiling(maxOf(current.maxOrNull() ?: 0, previous.maxOrNull() ?: 0, budget, 1_000_00L))

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .pointerInput(days, daysElapsed) {
                    fun pick(x: Float) {
                        val plot = size.width - 44.dp.toPx()
                        scrub = ((x / plot) * (days - 1)).roundToInt().coerceIn(0, max(daysElapsed - 1, 0))
                    }
                    detectDragGestures(
                        onDragStart = { pick(it.x) },
                        onDrag = { change, _ -> pick(change.position.x) },
                        onDragEnd = { scrub = null },
                    )
                }
                .pointerInput(days, daysElapsed) {
                    detectTapGestures { offset ->
                        val plot = size.width - 44.dp.toPx()
                        val day = ((offset.x / plot) * (days - 1)).roundToInt().coerceIn(0, max(daysElapsed - 1, 0))
                        scrub = if (scrub == day) null else day
                    }
                },
        ) {
            val axisWidth = 44.dp.toPx()
            val plotWidth = size.width - axisWidth
            val axisHeight = 20.dp.toPx()
            val bubbleSpace = 34.dp.toPx()
            val plotHeight = size.height - axisHeight - bubbleSpace
            val x = { day: Int -> plotWidth * day / (days - 1) }
            val y = { amount: Long -> bubbleSpace + plotHeight * (1f - amount.toFloat() / top) }

            for (step in 0..2) {
                val value = top * step / 2
                drawLine(Pop.Hairline, Offset(0f, y(value)), Offset(plotWidth, y(value)), 1.dp.toPx())
                val label = measurer.measure(Money.compact(value), AxisText)
                drawText(label, topLeft = Offset(size.width - label.size.width, y(value) - label.size.height / 2))
            }
            listOf(1, 8, 15, 22, days).distinct().forEach { day ->
                val label = measurer.measure(day.toString(), AxisText)
                val left = (x(day - 1) - label.size.width / 2).coerceIn(0f, plotWidth - label.size.width)
                drawText(label, topLeft = Offset(left, size.height - label.size.height))
            }

            if (budget > 0) {
                drawLine(Pop.Tangerine, Offset(0f, y(budget)), Offset(plotWidth, y(budget)), 1.dp.toPx())
            }
            if (previous.isNotEmpty()) {
                val path = smooth(previous.indices.map { Offset(x(it), y(previous[it])) })
                drawPath(path, Pop.TextMuted, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            val count = daysElapsed.coerceIn(0, current.size)
            if (count > 0) {
                val points = (0 until count).map { Offset(x(it), y(current[it])) }
                val line = smooth(points)
                val area = Path().apply {
                    addPath(line)
                    lineTo(points.last().x, y(0))
                    lineTo(points.first().x, y(0))
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(Pop.Lime.copy(alpha = 0.22f), Pop.Lime.copy(alpha = 0f)), startY = bubbleSpace, endY = y(0)))
                drawPath(line, Pop.Lime, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

                val active = scrub ?: (count - 1)
                val point = points[active]
                drawRect(
                    Brush.verticalGradient(listOf(Pop.Lime.copy(alpha = 0f), Pop.Lime.copy(alpha = 0.16f))),
                    Offset(point.x - 14.dp.toPx(), bubbleSpace),
                    Size(28.dp.toPx(), plotHeight),
                )
                drawCircle(Pop.Surface, 7.dp.toPx(), point)
                drawCircle(Pop.Lime, 5.dp.toPx(), point)
                bubble(measurer, "Day ${active + 1}  ${Money.compact(current[active])}", point, size.width, axisWidth)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(Pop.Lime, "This month")
            LegendDot(Pop.TextMuted, "Last month")
            if (budget > 0) LegendDot(Pop.Tangerine, "Limit")
        }
    }
}

private fun DrawScope.bubble(measurer: TextMeasurer, text: String, anchor: Offset, width: Float, rightInset: Float) {
    val label = measurer.measure(text, BubbleText)
    val padX = 10.dp.toPx()
    val padY = 5.dp.toPx()
    val boxWidth = label.size.width + padX * 2
    val boxHeight = label.size.height + padY * 2
    val left = (anchor.x - boxWidth / 2).coerceIn(0f, width - rightInset - boxWidth)
    val top = (anchor.y - boxHeight - 12.dp.toPx()).coerceAtLeast(0f)
    drawRoundRect(Pop.Lime, Offset(left, top), Size(boxWidth, boxHeight), CornerRadius(boxHeight / 2))
    drawText(label, topLeft = Offset(left + padX, top + padY))
}

/** Curve through points with flat tangents at each one, so it never dips below real values. */
private fun smooth(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    for (index in 1 until points.size) {
        val from = points[index - 1]
        val to = points[index]
        val middle = (from.x + to.x) / 2
        cubicTo(middle, from.y, middle, to.y, to.x, to.y)
    }
}

data class DonutSlice(val key: Long, val label: String, val amount: Long, val color: Color)

/** Share of spending by category. Tap a slice to pick it out, tap again to go back to the total. */
@Composable
fun SpendDonut(slices: List<DonutSlice>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.amount }.coerceAtLeast(1)
    var selected by remember(slices) { mutableStateOf<Long?>(null) }
    val active = slices.firstOrNull { it.key == selected }
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(slices) {
                    detectTapGestures { tap ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val distance = hypot(tap.x - center.x, tap.y - center.y)
                        if (distance < size.width * 0.3f || distance > size.width * 0.5f) {
                            selected = null
                            return@detectTapGestures
                        }
                        val angle = (Math.toDegrees(atan2(tap.y - center.y, tap.x - center.x).toDouble()) + 450) % 360
                        var start = 0.0
                        for (slice in slices) {
                            val sweep = slice.amount.toDouble() / total * 360
                            if (angle in start..(start + sweep)) {
                                selected = if (selected == slice.key) null else slice.key
                                break
                            }
                            start += sweep
                        }
                    }
                },
        ) {
            val stroke = 20.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            val gap = if (slices.size > 1) 360f * (3.dp.toPx() / (Math.PI.toFloat() * arc.width)) else 0f
            var start = -90f
            slices.forEach { slice ->
                val sweep = slice.amount.toFloat() / total * 360f
                val visible = (sweep - gap).coerceAtLeast(0.5f)
                val dimmed = selected != null && selected != slice.key
                drawArc(
                    color = if (dimmed) slice.color.copy(alpha = 0.25f) else slice.color,
                    startAngle = start + gap / 2,
                    sweepAngle = visible,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arc,
                    style = Stroke(stroke),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(36.dp)) {
            Text(
                active?.label ?: "Your spending",
                style = MaterialTheme.typography.bodySmall,
                color = Pop.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(Money.whole(active?.amount ?: total), style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            if (active != null) {
                Text(
                    "${(active.amount * 100 / total)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = Pop.TextMuted,
                )
            }
        }
    }
}

data class FlowNode(val key: Long, val label: String, val amount: Long, val color: Color)
data class FlowEdge(val from: Long, val to: Long, val amount: Long)

/**
 * Sankey diagram from accounts on the left to where the money ended up on the right.
 * Ribbon width is proportional to the amount. Tap a node to pick out its ribbons.
 */
@Composable
fun MoneyFlowChart(
    sources: List<FlowNode>,
    targets: List<FlowNode>,
    edges: List<FlowEdge>,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val rows = max(sources.size, targets.size)
    val chartHeight = max(260, rows * 54).dp
    var selected by remember(sources, targets, edges) { mutableStateOf<Long?>(null) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(chartHeight)
            .pointerInput(sources, targets, edges) {
                detectTapGestures { tap ->
                    val layout = layoutFlow(sources, targets, size.width.toFloat(), size.height.toFloat(), density)
                    val hit = (layout.left + layout.right).firstOrNull { tap.y in it.slotTop..it.slotBottom && abs(tap.x - it.anchorX) < size.width * 0.32f }
                    selected = if (hit == null || selected == hit.node.key) null else hit.node.key
                }
            },
    ) {
        val layout = layoutFlow(sources, targets, size.width, size.height, density)
        val leftUsed = HashMap<Long, Float>()
        val rightUsed = HashMap<Long, Float>()
        val byKey = (layout.left + layout.right).associateBy { it.node.key }
        val targetRank = targets.withIndex().associate { it.value.key to it.index }
        val sourceRank = sources.withIndex().associate { it.value.key to it.index }
        val ordered = edges.sortedWith(compareBy({ sourceRank[it.from] ?: 0 }, { targetRank[it.to] ?: 0 }))

        ordered.forEach { edge ->
            val from = byKey[edge.from] ?: return@forEach
            val to = byKey[edge.to] ?: return@forEach
            val thickness = edge.amount * layout.scale
            val leftTop = from.top + (leftUsed[edge.from] ?: 0f)
            val rightTop = to.top + (rightUsed[edge.to] ?: 0f)
            leftUsed[edge.from] = (leftUsed[edge.from] ?: 0f) + thickness
            rightUsed[edge.to] = (rightUsed[edge.to] ?: 0f) + thickness

            val startX = from.anchorX
            val endX = to.anchorX
            val middle = (startX + endX) / 2
            val ribbon = Path().apply {
                moveTo(startX, leftTop)
                cubicTo(middle, leftTop, middle, rightTop, endX, rightTop)
                lineTo(endX, rightTop + thickness)
                cubicTo(middle, rightTop + thickness, middle, leftTop + thickness, startX, leftTop + thickness)
                close()
            }
            val lit = selected == null || selected == edge.from || selected == edge.to
            drawPath(ribbon, to.node.color.copy(alpha = if (lit) 0.5f else 0.08f))
        }

        layout.left.forEach { drawNode(it, measurer, leftSide = true, dimmed = selected != null && selected != it.node.key && edges.none { e -> e.from == it.node.key && e.to == selected }) }
        layout.right.forEach { drawNode(it, measurer, leftSide = false, dimmed = selected != null && selected != it.node.key && edges.none { e -> e.to == it.node.key && e.from == selected }) }
    }
}

private class PlacedNode(
    val node: FlowNode,
    val top: Float,
    val height: Float,
    val slotTop: Float,
    val slotBottom: Float,
    val anchorX: Float,
)

private class FlowLayout(val left: List<PlacedNode>, val right: List<PlacedNode>, val scale: Float)

private fun layoutFlow(sources: List<FlowNode>, targets: List<FlowNode>, width: Float, height: Float, density: Float): FlowLayout {
    val gutter = width * 0.27f
    val gap = 6 * density
    val minSlot = 34 * density
    val bar = 5 * density

    fun place(nodes: List<FlowNode>, anchor: Float, scale: Float): List<PlacedNode> {
        val slots = nodes.map { max(it.amount * scale, minSlot) }
        val used = slots.sum() + gap * (nodes.size - 1)
        var y = ((height - used) / 2).coerceAtLeast(0f)
        return nodes.mapIndexed { index, node ->
            val slot = slots[index]
            val barHeight = node.amount * scale
            val placed = PlacedNode(node, y + (slot - barHeight) / 2, barHeight, y, y + slot, anchor)
            y += slot + gap
            placed
        }
    }

    // Shrink the scale until both columns, with their minimum label rows, fit the height.
    val total = max(sources.sumOf { it.amount }, targets.sumOf { it.amount }).coerceAtLeast(1)
    var scale = height / total
    repeat(24) {
        val needed = listOf(sources, targets).maxOf { nodes ->
            nodes.sumOf { max(it.amount * scale, minSlot).toDouble() }.toFloat() + gap * (nodes.size - 1)
        }
        if (needed > height) scale *= 0.95f
    }
    return FlowLayout(place(sources, gutter + bar, scale), place(targets, width - gutter - bar, scale), scale)
}

private fun DrawScope.drawNode(placed: PlacedNode, measurer: TextMeasurer, leftSide: Boolean, dimmed: Boolean) {
    val bar = 5.dp.toPx()
    val alpha = if (dimmed) 0.3f else 1f
    val x = if (leftSide) placed.anchorX - bar else placed.anchorX
    drawRoundRect(
        placed.node.color.copy(alpha = alpha),
        Offset(x, placed.top),
        Size(bar, max(placed.height, 2.dp.toPx())),
        CornerRadius(bar / 2),
    )

    val gutter = (if (leftSide) x else size.width - x - bar) - 8.dp.toPx()
    val constraints = Constraints(maxWidth = gutter.toInt().coerceAtLeast(1))
    val name = measurer.measure(
        placed.node.label,
        TextStyle(fontFamily = Jakarta, fontSize = 11.sp, color = Pop.TextSecondary.copy(alpha = alpha)),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = constraints,
    )
    val value = measurer.measure(
        Money.compact(placed.node.amount),
        TextStyle(
            fontFamily = Jakarta,
            fontSize = 13.sp,
            color = Pop.Text.copy(alpha = alpha),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        ),
        constraints = constraints,
    )
    val middle = (placed.slotTop + placed.slotBottom) / 2
    val blockHeight = name.size.height + value.size.height
    val top = middle - blockHeight / 2f
    val nameX = if (leftSide) x - 8.dp.toPx() - name.size.width else x + bar + 8.dp.toPx()
    val valueX = if (leftSide) x - 8.dp.toPx() - value.size.width else x + bar + 8.dp.toPx()
    drawText(name, topLeft = Offset(nameX, top))
    drawText(value, topLeft = Offset(valueX, top + name.size.height))
}

/** One column per month, saved amount only. The goal is drawn as a thin reference line. */
@Composable
fun SavedBars(
    months: List<Pair<String, Long>>,
    target: Long,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val top = niceCeiling(maxOf(months.maxOfOrNull { it.second } ?: 0, target, 1_000_00L))
    Canvas(modifier.fillMaxWidth().height(170.dp)) {
        val labelHeight = 20.dp.toPx()
        val valueHeight = 18.dp.toPx()
        val plotHeight = size.height - labelHeight - valueHeight
        val slot = size.width / months.size
        val barWidth = 24.dp.toPx().coerceAtMost(slot * 0.6f)
        val baseline = valueHeight + plotHeight

        if (target > 0) {
            val y = baseline - plotHeight * target / top
            drawLine(Pop.Tangerine, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        months.forEachIndexed { index, (label, saved) ->
            val center = slot * index + slot / 2
            val positive = saved.coerceAtLeast(0)
            val barHeight = plotHeight * positive / top
            if (barHeight > 0) {
                val rect = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = center - barWidth / 2,
                            top = baseline - barHeight,
                            right = center + barWidth / 2,
                            bottom = baseline,
                            topLeftCornerRadius = CornerRadius(4.dp.toPx()),
                            topRightCornerRadius = CornerRadius(4.dp.toPx()),
                        ),
                    )
                }
                drawPath(rect, Pop.Lavender)
            }
            val value = measurer.measure(Money.compact(saved), AxisText.copy(color = Pop.TextSecondary))
            val valueTop = Offset(center - value.size.width / 2, baseline - barHeight - value.size.height - 3.dp.toPx())
            // A surface patch keeps the label readable where it crosses the goal line.
            drawRect(Pop.Surface, valueTop, Size(value.size.width.toFloat(), value.size.height.toFloat()))
            drawText(value, topLeft = valueTop)
            val name = measurer.measure(label, AxisText)
            drawText(name, topLeft = Offset(center - name.size.width / 2, size.height - name.size.height))
        }
        drawLine(Pop.Hairline, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
    }
}

/** Calendar with one hue from dark to bright by amount. Tap a day to read its total. */
@Composable
fun SpendCalendar(
    daily: LongArray,
    /** Empty cells before the 1st, with Monday as the first column. */
    leadingBlanks: Int,
    modifier: Modifier = Modifier,
) {
    var selected by remember(daily) { mutableStateOf<Int?>(null) }
    val peak = (daily.maxOrNull() ?: 0).coerceAtLeast(1)
    val cells = List(leadingBlanks) { -1 } + daily.indices.toList()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Pop.TextMuted,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1.25f)) {
                        if (day >= 0) {
                            val level = daily[day].toFloat() / peak
                            val fill = when {
                                daily[day] == 0L -> Pop.Raised
                                else -> Pop.Lime.copy(alpha = 0.22f + 0.78f * level)
                            }
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(fill)
                                    .pointerInput(day) {
                                        detectTapGestures { selected = if (selected == day) null else day }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    (day + 1).toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (level > 0.5f) Pop.OnPop else Pop.TextSecondary,
                                )
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).size(0.dp)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        val day = selected
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (day == null) "Tap a day to see its total" else "Day ${day + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = Pop.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (day != null) {
                Text(Money.format(daily[day]), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.width(0.dp))
        }
    }
}

/** One bar split by payment method, with a 2dp gap between parts. */
@Composable
fun StackedBar(parts: List<Pair<Long, Color>>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.first }.coerceAtLeast(1)
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val gap = 2.dp.toPx()
        val usable = size.width - gap * (parts.size - 1)
        var x = 0f
        parts.forEach { (amount, color) ->
            val width = usable * amount / total
            drawRoundRect(color, Offset(x, 0f), Size(width, size.height), CornerRadius(4.dp.toPx()))
            x += width + gap
        }
    }
}
