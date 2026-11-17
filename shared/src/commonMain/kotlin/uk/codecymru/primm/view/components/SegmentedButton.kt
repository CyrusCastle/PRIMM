package uk.codecymru.primm.view.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import uk.codecymru.primm.view.PrimmAdditionalColors

data class Segment<T>(val value: T, val label: String, val icon: ImageVector? = null)

@Composable
fun <T> PrimmSegmentedButton(
    segments: List<Segment<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    iconsOnly: Boolean = false,
) {
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = PrimmAdditionalColors.accent,
        activeContentColor = PrimmAdditionalColors.pane,
        activeBorderColor = PrimmAdditionalColors.accent,
        inactiveContainerColor = PrimmAdditionalColors.pane,
        inactiveContentColor = PrimmAdditionalColors.consoleOutput,
        inactiveBorderColor = PrimmAdditionalColors.border,
    )
    SingleChoiceSegmentedButtonRow(modifier) {
        for (segment in segments) {
            SegmentedButton(
                selected = segment.value == selected,
                onClick = { onSelect(segment.value) },
                shape = RectangleShape,
                colors = colors,
                icon = {
                    if (segment.icon != null && !iconsOnly) {
                        Icon(segment.icon, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                    }
                },
                label = {
                    if (segment.icon != null && iconsOnly) {
                        Icon(segment.icon, contentDescription = segment.label, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                    } else {
                        Text(segment.label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                },
            )
        }
    }
}
