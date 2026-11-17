package uk.codecymru.primm.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.codecymru.primm.view.ColumnResizeIcon
import uk.codecymru.primm.view.PrimmAdditionalColors
import uk.codecymru.primm.view.RowResizeIcon

private val HandleThickness = 12.dp

@Composable
fun SplitPane(
    orientation: Orientation,
    fraction: Float,
    onFractionCommitted: (Float) -> Unit,
    minFirst: Dp,
    minSecond: Dp,
    modifier: Modifier = Modifier,
    onDragActiveChange: (Boolean) -> Unit = {},
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val horizontal = orientation == Orientation.Horizontal
        val available = (if (horizontal) maxWidth else maxHeight) - HandleThickness
        val availablePx = with(density) { available.toPx() }.coerceAtLeast(1f)
        val minFraction = with(density) { minFirst.toPx() } / availablePx
        val maxFraction = 1f - with(density) { minSecond.toPx() } / availablePx

        var live by remember { mutableStateOf(fraction) }
        var dragging by remember { mutableStateOf(false) }
        LaunchedEffect(fraction) {
            if (!dragging) live = fraction
        }

        val shown = if (minFraction <= maxFraction) live.coerceIn(minFraction, maxFraction) else 0.5f
        val firstSize = available * shown

        val dragState = rememberDraggableState { delta ->
            val next = (live * availablePx + delta) / availablePx
            live = if (minFraction <= maxFraction) next.coerceIn(minFraction, maxFraction) else 0.5f
        }
        val handle = Modifier
            .draggable(
                state = dragState,
                orientation = orientation,
                onDragStarted = {
                    dragging = true
                    onDragActiveChange(true)
                },
                onDragStopped = {
                    dragging = false
                    onDragActiveChange(false)
                    onFractionCommitted(live)
                },
            )
            .pointerHoverIcon(if (horizontal) ColumnResizeIcon else RowResizeIcon)

        if (horizontal) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(firstSize).fillMaxHeight()) { first() }
                Box(handle.width(HandleThickness).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Grip(horizontal = true)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { second() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.height(firstSize).fillMaxWidth()) { first() }
                Box(handle.height(HandleThickness).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Grip(horizontal = false)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { second() }
            }
        }
    }
}

@Composable
private fun Grip(horizontal: Boolean) {
    val size = if (horizontal) Modifier.size(width = 4.dp, height = 36.dp) else Modifier.size(width = 36.dp, height = 4.dp)
    Box(size.background(PrimmAdditionalColors.border, RoundedCornerShape(2.dp)))
}
