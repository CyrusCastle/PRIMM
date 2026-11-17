package uk.codecymru.primm.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.codecymru.primm.view.PrimmAdditionalColors
import primm.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun CellField(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    onEnter: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.replace("\n", "").replace("\t", "")) },
        singleLine = true,
        enabled = enabled,
        textStyle = textStyle,
        cursorBrush = SolidColor(PrimmAdditionalColors.accent),
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.Tab -> {
                    focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
                    true
                }
                Key.Enter, Key.NumPadEnter -> {
                    if (onEnter != null) onEnter()
                    onEnter != null
                }
                else -> false
            }
        },
        decorationBox = { inner ->
            Box(
                Modifier.Companion
                    .background(if (enabled) PrimmAdditionalColors.pane else PrimmAdditionalColors.lockedTint, RoundedCornerShape(4.dp))
                    .border(1.dp, PrimmAdditionalColors.border, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = textStyle, color = PrimmAdditionalColors.textQuiet, maxLines = 1)
                }
                inner()
            }
        },
    )
}

@Composable
fun LineChooser(
    line: Int?,
    codeLines: List<String>,
    textStyle: TextStyle,
    onChoose: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = true,
    choices: List<Int>? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Text(
            line?.toString() ?: "–",
            style = textStyle,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimmAdditionalColors.pane, RoundedCornerShape(4.dp))
                .border(1.dp, PrimmAdditionalColors.border, RoundedCornerShape(4.dp))
                .clickable { open = true }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowNone) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.TRACE_NO_LINE)) }, onClick = { open = false; onChoose(null) })
            }
            val numbers = choices ?: codeLines.indices.map { it + 1 }
            for (number in numbers) {
                val text = codeLines.getOrNull(number - 1)?.trim().orEmpty()
                DropdownMenuItem(
                    text = {
                        Text(
                            "$number   ${text.take(48)}",
                            style = textStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = { open = false; onChoose(number) },
                )
            }
        }
    }
}

@Composable
fun ChoiceChooser(
    value: String?,
    choices: List<Pair<String, String>>,
    textStyle: TextStyle,
    onChoose: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Text(
            choices.firstOrNull { it.first == value }?.second ?: value ?: "–",
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimmAdditionalColors.pane, RoundedCornerShape(4.dp))
                .border(1.dp, PrimmAdditionalColors.border, RoundedCornerShape(4.dp))
                .clickable { open = true }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowNone) DropdownMenuItem(text = { Text("–", style = textStyle) }, onClick = { open = false; onChoose(null) })
            for ((stored, shown) in choices) {
                DropdownMenuItem(
                    text = { Text(shown, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { open = false; onChoose(stored) },
                )
            }
        }
    }
}

@Composable
fun ConditionToggle(value: Boolean?, enabled: Boolean, textStyle: TextStyle, onChange: (Boolean?) -> Unit, modifier: Modifier = Modifier) {
    val label = when (value) {
        null -> if (enabled) "–" else ""
        true -> "True"
        false -> "False"
    }
    val colour = when (value) {
        true -> PrimmAdditionalColors.matches
        false -> PrimmAdditionalColors.notRun
        null -> PrimmAdditionalColors.textQuiet
    }
    Text(
        label,
        style = textStyle,
        color = colour,
        maxLines = 1,
        modifier = modifier
            .background(if (enabled) PrimmAdditionalColors.pane else PrimmAdditionalColors.page, RoundedCornerShape(4.dp))
            .border(1.dp, if (enabled) PrimmAdditionalColors.border else PrimmAdditionalColors.page, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) {
                onChange(
                    when (value) {
                        null -> true
                        true -> false
                        false -> null
                    },
                )
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
fun SmallButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Text(
        label,
        color = if (enabled) PrimmAdditionalColors.accent else PrimmAdditionalColors.border,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
