package uk.codecymru.primm.view.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.codecymru.primm.model.AppState
import uk.codecymru.primm.model.EditorTab
import uk.codecymru.primm.model.MarkTool
import uk.codecymru.primm.model.Marks
import uk.codecymru.primm.model.Preferences
import uk.codecymru.primm.view.PaneCard
import uk.codecymru.primm.view.PrimmAdditionalColors
import uk.codecymru.primm.view.components.ButtonType
import uk.codecymru.primm.view.components.Padlock
import uk.codecymru.primm.view.components.PrimmButton
import uk.codecymru.primm.view.consoleTextStyle
import uk.codecymru.primm.view.components.PrimmSegmentedButton
import uk.codecymru.primm.view.components.Segment
import uk.codecymru.primm.view.components.icon
import primm.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditorPanel(
    state: AppState,
    onCodeChange: (String) -> Unit,
    onSmallerText: () -> Unit,
    onLargerText: () -> Unit,
    onSelectTab: (EditorTab) -> Unit,
    onChooseMarkTool: (MarkTool) -> Unit,
    onToggleMarks: () -> Unit,
    onClearMarks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontSize = state.preferences.fontSize
    val editorVisible = state.dialog == null
    PaneCard(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val tabs = state.availableTabs
                if (tabs.size > 1) {
                    PrimmSegmentedButton(
                        segments = tabs.map { Segment(it, it.title, it.icon) },
                        selected = state.activeTab,
                        onSelect = onSelectTab,
                    )
                } else {
                    Text(
                        state.program.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (state.isEditorReadOnly) {
                    LockBadge(Modifier.weight(1f, fill = false))
                }
                Spacer(Modifier.weight(1f))
                MarkTools(state.marks, onChooseMarkTool)
                Spacer(Modifier.weight(1f))
                MarksCommands(state.marks, onToggleMarks, onClearMarks)
                Spacer(Modifier.weight(1f))
                PrimmButton(
                    ButtonType.TRANSPARENT,
                    fontSize > Preferences.MIN_FONT_SIZE,
                    onSmallerText,
                    text = "T-",
                    icon = null,
                )
                PrimmButton(
                    ButtonType.TRANSPARENT,
                    fontSize < Preferences.MAX_FONT_SIZE,
                    onLargerText,
                    text = "T+",
                    icon = null,
                )
            }
            HorizontalDivider(color = PrimmAdditionalColors.border)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CodeEditorPane(
                    code = state.editorText,
                    readOnly = state.isEditorReadOnly,
                    visible = editorVisible,
                    fontSize = fontSize,
                    errorMark = state.errorMark,
                    runMarks = state.runMarks,
                    diffMarks = state.diffMarks,
                    marks = state.marks,
                    marksPage = state.marks.page(state.activeTab),
                    onCodeChange = onCodeChange,
                    modifier = Modifier.fillMaxSize()
                        .padding(start = 1.dp, end = 1.dp, bottom = 1.dp),
                )
                if (!editorVisible) {
                    StaticCode(state.editorText, fontSize, locked = state.isEditorReadOnly)
                }
            }
        }
    }
}

@Composable
private fun MarkTools(marks: Marks, onChoose: (MarkTool) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        PrimmSegmentedButton(
            segments = MarkTool.entries.map { Segment(it, it.label, it.icon) },
            selected = marks.tool,
            onSelect = onChoose,
            iconsOnly = true,
        )
    }
}

@Composable
private fun MarksCommands(marks: Marks, onToggle: () -> Unit, onClear: () -> Unit){
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        PrimmButton(
            ButtonType.TRANSPARENT,
            true,
            onToggle,
            text = if (marks.visible) stringResource(Res.string.EDITOR_HIDE) else stringResource(Res.string.EDITOR_SHOW),
            icon = null
        )
        PrimmButton(
            ButtonType.TRANSPARENT,
            true,
            onClear,
            text = stringResource(Res.string.EDITOR_CLEAR),
            icon = null
        )
    }
}

@Composable
private fun StaticCode(code: String, fontSize: Int, locked: Boolean) {
    val lines = code.removeSuffix("\n").split('\n')
    val style = consoleTextStyle(fontSize)
    Row(
        Modifier
            .fillMaxSize()
            .background(if (locked) PrimmAdditionalColors.lockedTint else PrimmAdditionalColors.pane)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.End) {
            lines.indices.forEach { Text("${it + 1}", style = style, color = PrimmAdditionalColors.textQuiet) }
        }
        Column(Modifier.padding(start = 8.dp)) {
            lines.forEach { Text(it.ifEmpty { " " }, style = style, softWrap = false) }
        }
    }
}

@Composable
fun LockBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(PrimmAdditionalColors.lockedTint, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Padlock(PrimmAdditionalColors.textQuiet)
    }
}