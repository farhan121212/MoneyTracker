package com.farhan.paisa.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.farhan.paisa.ui.theme.Pop

val Gutter = 20.dp
val CardShape = RoundedCornerShape(24.dp)
val GroupShape = RoundedCornerShape(20.dp)

/** Clickable with a soft spring, so tapped things feel physical. */
@Composable
fun Modifier.press(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Top bar for pushed screens: a round back button, a centred title and an optional action. */
@Composable
fun NavHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack,
    trailing: @Composable () -> Unit = { Spacer(Modifier.size(44.dp)) },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton(icon, "Back", onBack)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        trailing()
    }
}

@Composable
fun CircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Pop.Surface,
    tint: Color = Pop.Text,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(background).press(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.bodyMedium,
                color = Pop.TextSecondary,
                modifier = Modifier.press(onClick = onAction).padding(vertical = 6.dp, horizontal = 4.dp),
            )
        }
    }
}

/** Inset grouped container, the same pattern as iOS settings lists. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth().clip(GroupShape).background(Pop.Surface), content = content)
}

@Composable
fun RowDivider(start: Dp = 16.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = start)
            .height(1.dp)
            .background(Pop.Hairline),
    )
}

@Composable
fun GroupRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Pop.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun Avatar(name: String, seed: Long, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Pop.accent(seed.toInt())),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = Pop.OnPop,
        )
    }
}

@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selectedColor: Color = Pop.Lime,
    dot: Color? = null,
) {
    val background by animateColorAsState(if (selected) selectedColor else Pop.Surface, label = "chip")
    val content by animateColorAsState(if (selected) Pop.OnPop else Pop.Text, label = "chipText")
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .press(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (selected) Pop.OnPop else dot))
        }
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

/**
 * A horizontally scrolling row of chips that runs to the screen edges from inside a padded
 * column, and keeps the selected chip in view.
 */
@Composable
fun <T> ChipRow(
    items: List<T>,
    selected: Int = -1,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) {
        if (selected > 0) state.animateScrollToItem(selected, -state.layoutInfo.viewportSize.width / 4)
    }
    LazyRow(
        state = state,
        modifier = modifier.layout { measurable, constraints ->
            val bleed = Gutter.roundToPx()
            val width = constraints.maxWidth + bleed * 2
            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
            layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
        },
        contentPadding = PaddingValues(horizontal = Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items) { content(it) }
    }
}

/** A row of mutually exclusive options, like an iOS segmented control. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Pop.Surface)
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            val background by animateColorAsState(if (active) Pop.Lime else Color.Transparent, label = "segment")
            val content by animateColorAsState(if (active) Pop.OnPop else Pop.TextSecondary, label = "segmentText")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(value) },
                    )
                    .heightIn(min = 38.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Pop.Lime,
    contentColor: Color = Pop.OnPop,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (enabled) color else Pop.Raised)
            .press(enabled = enabled, onClick = onClick)
            .heightIn(min = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) contentColor else Pop.TextMuted,
        )
    }
}

/** Horizontal meter with an optional tick showing how far through the month we are. */
@Composable
fun Meter(
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
    track: Color = Pop.Raised,
    marker: Float? = null,
    markerColor: Color = Pop.Text,
    height: Dp = 8.dp,
) {
    val animated by animateFloatAsState(value.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "meter")
    Canvas(modifier.fillMaxWidth().height(height + 6.dp)) {
        val barHeight = height.toPx()
        val top = (size.height - barHeight) / 2
        val radius = CornerRadius(barHeight / 2)
        drawRoundRect(track, Offset(0f, top), Size(size.width, barHeight), radius)
        if (animated > 0f) {
            drawRoundRect(color, Offset(0f, top), Size((size.width * animated).coerceAtLeast(barHeight), barHeight), radius)
        }
        if (marker != null) {
            val x = size.width * marker.coerceIn(0f, 1f)
            drawLine(Pop.Ink, Offset(x, 0f), Offset(x, size.height), 5.dp.toPx())
            drawLine(markerColor, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
fun Ring(value: Float, color: Color, track: Color, modifier: Modifier = Modifier, stroke: Dp = 7.dp) {
    val animated by animateFloatAsState(value.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "ring")
    Canvas(modifier) {
        val width = stroke.toPx()
        val inset = width / 2
        val arc = Size(size.width - width, size.height - width)
        drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(width))
        if (animated > 0f) {
            drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arc, style = Stroke(width, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Pop.TextSecondary, textAlign = TextAlign.Center)
    }
}

/** A borderless text input that sits inside a [Group]. */
@Composable
fun GroupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    textAlign: TextAlign = TextAlign.Start,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 12.dp))
        }
        InlineField(value, onValueChange, placeholder, Modifier.weight(1f), keyboardType, capitalization, if (label != null) TextAlign.End else textAlign)
    }
}

@Composable
fun InlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    textAlign: TextAlign = TextAlign.Start,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val textStyle = style.copy(color = Pop.Text, textAlign = textAlign)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(Pop.Lime),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        decorationBox = { inner ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (textAlign == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = textStyle.copy(color = Pop.TextMuted),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Pop.Surface)
            .heightIn(min = 46.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Pop.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        InlineField(value, onValueChange, placeholder, Modifier.weight(1f))
    }
}

val ScreenPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 120.dp)
val PushedPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 40.dp)

@Composable
fun FullScreen(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Pop.Ink), content = content)
}
