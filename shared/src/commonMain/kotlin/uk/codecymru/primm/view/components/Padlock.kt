package uk.codecymru.primm.view.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import primm.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun Padlock(colour: Color, modifier: Modifier = Modifier.Companion) {
    Icon(
        Icons.Filled.Lock,
        contentDescription = stringResource(Res.string.EDITOR_LOCKED_RIGHT_NOW),
        tint = colour,
        modifier = modifier.size(width = 10.dp, height = 12.dp)
    )
}