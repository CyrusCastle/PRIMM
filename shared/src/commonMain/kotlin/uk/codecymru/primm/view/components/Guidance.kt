package uk.codecymru.primm.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.codecymru.primm.view.PrimmAdditionalColors
import primm.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun GuideBox(active: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().guideHighlight(active, RectangleShape).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun Modifier.guideHighlight(active: Boolean, shape: Shape): Modifier =
    if (active) background(PrimmAdditionalColors.guide, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape).leftBorder(3.dp, PrimmAdditionalColors.guideEdge)
    else background(Color.Transparent, shape)


fun Modifier.leftBorder(strokeWidth: Dp, color: Color) = composed(
    factory = {
        val density = LocalDensity.current
        val strokeWidthPx = density.run { strokeWidth.toPx() }

        Modifier.drawBehind {
            val height = size.height

            drawLine(
                color = color,
                start = Offset(x = 0f, y = 0f),
                end = Offset(x = 0f , y = height),
                strokeWidth = strokeWidthPx
            )
        }
    }
)

@Composable
fun NextTag() {
    Text(
        stringResource(Res.string.GUIDE_NEXT),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = PrimmAdditionalColors.guideText,
        modifier = Modifier.Companion
            .background(PrimmAdditionalColors.guideEdge, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
