package uk.codecymru.primm.view.components

import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector

enum class ButtonType {
    PRIMARY,
    OUTLINE,
    TRANSPARENT
}

@Composable
fun PrimmButton(
    type: ButtonType,
    enabled: Boolean,
    onClick: () -> Unit,
    color: Color = Color.Unspecified,
    text: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    when (type){
        ButtonType.PRIMARY -> {
            Button(
                enabled = enabled,
                onClick = onClick,
                modifier = modifier,
                shape = RectangleShape
            ){
                text?.let { Text(it, color = color) }
                icon?.let { Icon(icon, contentDescription = text, tint = color) }
            }
        }

        ButtonType.OUTLINE -> {
            OutlinedButton(
                enabled = enabled,
                onClick = onClick,
                modifier = modifier,
                shape = RectangleShape
            ){
                text?.let { Text(it, color = color) }
                icon?.let { Icon(icon, contentDescription = text, tint = color) }
            }
        }

        ButtonType.TRANSPARENT -> {
            TextButton(
                enabled = enabled,
                onClick = onClick,
                modifier = modifier,
                shape = RectangleShape
            ){
                text?.let { Text(it, color = color) }
                icon?.let { Icon(icon, contentDescription = text, tint = color) }
            }
        }
    }
}