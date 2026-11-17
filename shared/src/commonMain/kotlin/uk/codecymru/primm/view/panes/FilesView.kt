package uk.codecymru.primm.view.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.codecymru.primm.controller.AppController
import uk.codecymru.primm.model.AppState
import uk.codecymru.primm.model.FileRules
import uk.codecymru.primm.model.TextFile
import uk.codecymru.primm.view.PrimmAdditionalColors
import uk.codecymru.primm.view.components.ButtonType
import uk.codecymru.primm.view.components.PrimmButton
import uk.codecymru.primm.view.consoleTextStyle
import uk.codecymru.primm.view.panes.primm.Hint
import primm.shared.generated.resources.*
import uk.codecymru.primm.model.plural
import org.jetbrains.compose.resources.stringResource
import uk.codecymru.primm.model.tr

@Composable
fun FilesView(state: AppState, controller: AppController, modifier: Modifier = Modifier) {
    val files = state.files
    val running = state.isProgramRunning
    Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (files.beforeLastRun != null) {
            Row(
                Modifier.fillMaxWidth()
                    .background(PrimmAdditionalColors.nextLine, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(lastRunSummary(files.changedByLastRun, files.deletedByLastRun), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                PrimmButton(
                    ButtonType.OUTLINE,
                    !running,
                    controller::undoRunFileChanges,
                    text = stringResource(Res.string.FILES_UNDO),
                    icon = null
                )
            }
        }
        files.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = PrimmAdditionalColors.differs)
        }
        if (running) Hint(stringResource(Res.string.FILES_LOCKED_WHILE_RUNNING))

        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.width(170.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(files.files, key = { it.name }) { file ->
                        FileListItem(
                            file = file,
                            selected = file.name == files.selectedFile?.name,
                            changed = file.name in files.changedByLastRun,
                            onClick = { controller.selectFile(file.name) },
                        )
                    }
                }
                val full = files.files.size >= FileRules.MAX_FILES
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrimmButton(
                        ButtonType.OUTLINE,
                        !running && !full,
                        controller::newFile,
                        icon = Icons.Default.Add
                    )
                    PrimmButton(
                        ButtonType.OUTLINE,
                        !running && !full,
                        controller::uploadFile,
                        icon = Icons.Default.UploadFile
                    )
                }
            }
            VerticalDivider(color = PrimmAdditionalColors.border)
            val file = files.selectedFile
            if (file == null) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Hint(stringResource(Res.string.FILES_NO_FILES_YET))
                }
            } else {
                FileEditor(state, file, running, controller, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

private val SmallPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)

private fun lastRunSummary(changed: Set<String>, deleted: Set<String>): String {
    val changedNames = changed.sorted().joinToString(", ")
    val deletedNames = deleted.sorted().joinToString(", ")
    return when {
        deleted.isEmpty() -> tr(Res.string.FILES_LAST_RUN_CHANGED, changedNames)
        changed.isEmpty() -> tr(Res.string.FILES_LAST_RUN_DELETED, deletedNames)
        else -> tr(Res.string.FILES_LAST_RUN_CHANGED_AND_DELETED, changedNames, deletedNames)
    }
}

@Composable
private fun FileListItem(file: TextFile, selected: Boolean, changed: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) PrimmAdditionalColors.selected else PrimmAdditionalColors.pane, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            file.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (changed) Text(stringResource(Res.string.FILES_CHANGED_LABEL), style = MaterialTheme.typography.labelSmall, color = PrimmAdditionalColors.differs)
    }
}

@Composable
private fun FileEditor(state: AppState, file: TextFile, running: Boolean, controller: AppController, modifier: Modifier) {
    val textStyle = consoleTextStyle(state.preferences.fontSize)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NameField(file.name, enabled = !running, onRename = { controller.renameFile(file.name, it) }, modifier = Modifier.weight(1f))
            PrimmButton(
                ButtonType.OUTLINE,
                true,
                { controller.downloadFile(file.name) },
                text = stringResource(Res.string.FILES_DOWNLOAD),
                icon = null
            )
            PrimmButton(
                ButtonType.OUTLINE,
                !running,
                { controller.askToRemoveFile(file.name) },
                text = stringResource(Res.string.FILES_DELETE),
                icon = null
            )
        }
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .border(1.dp, PrimmAdditionalColors.border, RoundedCornerShape(6.dp))
                .background(if (running) PrimmAdditionalColors.lockedTint else PrimmAdditionalColors.pane, RoundedCornerShape(6.dp))
                .padding(8.dp),
        ) {
            if (file.text.isEmpty()) Hint(stringResource(Res.string.FILES_EMPTY))
            BasicTextField(
                value = file.text,
                onValueChange = { controller.setFileText(file.name, it) },
                readOnly = running,
                textStyle = textStyle,
                cursorBrush = SolidColor(PrimmAdditionalColors.consoleInput),
                modifier = Modifier.fillMaxSize(),
            )
        }
        val lines = if (file.text.isEmpty()) 0 else file.text.trimEnd('\n').count { it == '\n' } + 1
        Hint(stringResource(Res.string.FILES_SIZE, plural(lines, Res.string.COUNT_LINE_ONE, Res.string.COUNT_LINE_OTHER), file.text.length, FileRules.MAX_CHARS))
    }
}

@Composable
private fun NameField(name: String, enabled: Boolean, onRename: (String) -> Unit, modifier: Modifier) {
    var draft by remember(name) { mutableStateOf(name) }
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = draft,
        onValueChange = { draft = it.replace("\n", "") },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleSmall,
        cursorBrush = SolidColor(PrimmAdditionalColors.accent),
        modifier = modifier
            .border(1.dp, if (focused) PrimmAdditionalColors.accent else PrimmAdditionalColors.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .onFocusChanged {
                if (focused && !it.isFocused && draft != name) onRename(draft)
                focused = it.isFocused
            }
            .onPreviewKeyEvent { event ->
                val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
                if (isEnter && event.type == KeyEventType.KeyDown) {
                    if (draft != name) onRename(draft)
                    true
                } else {
                    false
                }
            },
    )
}
