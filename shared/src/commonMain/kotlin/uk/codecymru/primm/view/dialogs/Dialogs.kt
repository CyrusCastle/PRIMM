package uk.codecymru.primm.view.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.window.DialogProperties
import uk.codecymru.primm.controller.AppController
import uk.codecymru.primm.model.DialogState
import uk.codecymru.primm.model.DiffKind
import uk.codecymru.primm.model.DiffLine
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.codecymru.primm.model.Program
import uk.codecymru.primm.model.TeacherSettings
import uk.codecymru.primm.view.PrimmAdditionalColors
import uk.codecymru.primm.view.consoleTextStyle
import primm.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import uk.codecymru.primm.view.components.ButtonType
import uk.codecymru.primm.view.components.PrimmButton

@Composable
fun ConfirmReplaceDialog(pending: Program, onReplace: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        shape = RoundedCornerShape(4.dp),
        onDismissRequest = onKeep,
        title = { Text(stringResource(Res.string.DIALOG_CONFIRM_LOAD_TITLE)) },
        text = {
            Text(
                stringResource(Res.string.DIALOG_LOADING_WILL_CLEAR_EDITOR, pending.title),
            )
        },
        confirmButton = {
            PrimmButton(
                ButtonType.PRIMARY,
                true,
                onReplace,
                text = stringResource(Res.string.DIALOG_LOAD),
                icon = null
            )
        },
        dismissButton = {
            PrimmButton(
                ButtonType.TRANSPARENT,
                true,
                onKeep,
                text = stringResource(Res.string.DIALOG_CANCEL),
                icon = null
            )
        },
    )
}

@Composable
fun SettingsDialog(
    settings: TeacherSettings,
    program: Program,
    onProgramExtrasChange: (String) -> Unit,
    onMakeBriefOnlyChange: (Boolean) -> Unit,
    onStopAtInvestigateChange: (Boolean) -> Unit,
    onUnlockEditorChange: (Boolean) -> Unit,
    onGateRunChange: (Boolean) -> Unit,
    onOneRowAtATimeChange: (Boolean) -> Unit,
    onOpenRowsChange: (Boolean) -> Unit,
    onRevealAllChange: (Boolean) -> Unit,
    studentLink: String,
    studentLinkNote: String?,
    onCopyStudentLink: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        shape = RoundedCornerShape(4.dp),
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.DIALOG_TEACHER_SETTINGS)) },
        text = {
            Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingRow(stringResource(Res.string.DIALOG_UNLOCK_CODE_EDITOR), settings.unlockEditor, onUnlockEditorChange)
                SettingRow(stringResource(Res.string.DIALOG_DISABLE_RUN_UNTIL_PREDICT), settings.gateRun, onGateRunChange)
                SettingRow(stringResource(Res.string.DIALOG_ALLOW_ALL_PRIMM_ROWS), settings.openRows, onOpenRowsChange)
//                SettingRow("One row open at a time", settings.oneRowAtATime, onOneRowAtATimeChange)
                SettingRow(stringResource(Res.string.DIALOG_SKIP_REVEAL_STEP_INVESTIGATE), settings.revealAll, onRevealAllChange)
                SettingRow(stringResource(Res.string.DIALOG_MAKE_BRIEF_REFLECTION_ONLY), settings.makeBriefOnly, onMakeBriefOnlyChange)
                SettingRow(stringResource(Res.string.DIALOG_STOP_AT_INVESTIGATE), settings.stopAtInvestigate, onStopAtInvestigateChange)
//                ProgramExtras(program, onProgramExtrasChange)
                HorizontalDivider(color = PrimmAdditionalColors.border)
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(Res.string.DIALOG_STUDENTS_WHO_OPEN_LINK),
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimmAdditionalColors.textQuiet,
                    )
                    SelectionContainer {
                        Text(studentLink, style = consoleTextStyle(12), color = PrimmAdditionalColors.consoleOutput)
                    }
                    PrimmButton(
                        ButtonType.PRIMARY,
                        true,
                        onCopyStudentLink,
                        text = stringResource(Res.string.DIALOG_COPY_LINK_STUDENTS)
                    )
                    studentLinkNote?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = PrimmAdditionalColors.textQuiet)
                    }
                }
            }
        },
        confirmButton = {
            PrimmButton(
                ButtonType.OUTLINE,
                true,
                onClose,
                text = stringResource(Res.string.DIALOG_DONE),
                icon = null
            )
        },
    )
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
fun ConfirmStartModifyingDialog(onStart: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        shape = RoundedCornerShape(4.dp),
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.DIALOG_START_MODIFYING)) },
        text = {
            Text(stringResource(Res.string.DIALOG_COPY_PROGRAM_OPENS_NEW))
        },
        confirmButton = {
            PrimmButton(
                ButtonType.PRIMARY,
                true,
                onStart,
                text = stringResource(Res.string.DIALOG_START),
                icon = null
            )
        },
        dismissButton = {
            PrimmButton(
                ButtonType.TRANSPARENT,
                true,
                onCancel,
                text = stringResource(Res.string.DIALOG_CANCEL),
                icon = null
            )
        },
    )
}

@Composable
fun ConfirmBackToOriginalDialog(onReset: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        shape = RoundedCornerShape(4.dp),
        onDismissRequest = onKeep,
        title = { Text(stringResource(Res.string.DIALOG_BACK_ORIGINAL)) },
        text = { Text(stringResource(Res.string.DIALOG_MODIFIED_TAB_GOES_BACK)) },
        confirmButton = {
            PrimmButton(
                ButtonType.PRIMARY,
                true,
                onReset,
                text = stringResource(Res.string.DIALOG_REVERT),
                icon = null
            )
        },
        dismissButton = {
            PrimmButton(
                ButtonType.TRANSPARENT,
                true,
                onKeep,
                text = stringResource(Res.string.DIALOG_CANCEL),
                icon = null
            )
        },
    )
}

@Composable
fun ConfirmRemoveFileDialog(name: String, onRemove: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        shape = RoundedCornerShape(4.dp),
        onDismissRequest = onKeep,
        title = { Text(stringResource(Res.string.DIALOG_REMOVE_FILE_TITLE, name)) },
        text = { Text(stringResource(Res.string.DIALOG_FILE_EVERYTHING_WILL_DELETED)) },
        confirmButton = {
            PrimmButton(
                ButtonType.PRIMARY,
                true,
                onRemove,
                text = stringResource(Res.string.DIALOG_REMOVE),
                icon = null
            )
        },
        dismissButton = {
            PrimmButton(
                ButtonType.TRANSPARENT,
                true,
                onKeep,
                text = stringResource(Res.string.DIALOG_CANCEL),
                icon = null
            )
        },
    )
}

@Composable
fun CompareDialog(diff: List<DiffLine>, fontSize: Int, onClose: () -> Unit) {
    val style = consoleTextStyle(fontSize - 1)
    AlertDialog(
        shape = RoundedCornerShape(4.dp),
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.9f).widthIn(max = 1100.dp),
        title = { Text(stringResource(Res.string.DIALOG_ORIGINAL_MODIFIED)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                Row {
                    Text(stringResource(Res.string.DIALOG_ORIGINAL), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(440.dp))
                    Text(stringResource(Res.string.DIALOG_MODIFIED), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(440.dp))
                }
                for (line in diff) {
                    val (left, right) = when (line.kind) {
                        DiffKind.Same -> Color.Transparent to Color.Transparent
                        DiffKind.Changed -> PrimmAdditionalColors.diffChanged to PrimmAdditionalColors.diffChanged
                        DiffKind.Added -> Color.Transparent to PrimmAdditionalColors.diffAdded
                        DiffKind.Removed -> PrimmAdditionalColors.diffRemoved to Color.Transparent
                    }
                    Row {
                        DiffCell(line.oldLine, line.oldText, left, style)
                        DiffCell(line.newLine, line.newText, right, style)
                    }
                }
                if (diff.none { it.kind != DiffKind.Same }) {
                    Text(stringResource(Res.string.DIALOG_NO_DIFFERENCES_YET), color = PrimmAdditionalColors.textQuiet, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            PrimmButton(
                ButtonType.PRIMARY,
                true,
                onClose,
                text = stringResource(Res.string.DIALOG_CLOSE),
                icon = null
            )
        },
    )
}

@Composable
private fun DiffCell(number: Int?, text: String?, background: Color, style: TextStyle) {
    Row(Modifier.width(440.dp).background(background).padding(horizontal = 4.dp)) {
        Text(number?.toString() ?: "", style = style, color = PrimmAdditionalColors.textQuiet, modifier = Modifier.width(32.dp))
        Text(text ?: "", style = style, softWrap = false)
    }
}

@Composable
fun SaveDialog(dialog: DialogState.Save, names: String, controller: AppController) {
    AlertDialog(
        shape = RoundedCornerShape(4.dp),
        onDismissRequest = controller::closeDialog,
        title = { Text(stringResource(Res.string.DIALOG_SAVE_WORK)) },
        text = {
            Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = names,
                    onValueChange = controller::setStudentNames,
                    singleLine = true,
                    label = { Text(stringResource(Res.string.DIALOG_NAMES)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SaveOption(stringResource(Res.string.DIALOG_SAVE_PDF), stringResource(Res.string.DIALOG_CHOOSE_SAVE_PDF_PRINT), !dialog.busy, controller::saveAsPdf)
                SaveOption(stringResource(Res.string.DIALOG_SAVE_IMAGE), stringResource(Res.string.DIALOG_SAME_REPORT_ONE_TALL), !dialog.busy, controller::saveAsImage)
                SaveOption(stringResource(Res.string.DIALOG_COPY_IMAGE), stringResource(Res.string.DIALOG_COPIES_PICTURE_PASTE_STRAIGHT), !dialog.busy, controller::copyImage)
                SaveOption(stringResource(Res.string.DIALOG_SAVE_SESSION), stringResource(Res.string.DIALOG_PRIMM_JSON_FILE_CARRY), !dialog.busy, controller::saveSessionFile)
                dialog.message?.let { Text(it, color = PrimmAdditionalColors.accent) }
            }
        },
        confirmButton = {
            PrimmButton(
                ButtonType.OUTLINE,
                true,
                controller::closeDialog,
                text = stringResource(Res.string.DIALOG_CLOSE),
                icon = null
            )
        },
    )
}

@Composable
private fun SaveOption(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimmButton(
            ButtonType.OUTLINE,
            enabled,
            onClick,
            text = label,
            icon = null,
            modifier = Modifier.width(150.dp)
        )
        Text(description, style = MaterialTheme.typography.bodySmall, color = PrimmAdditionalColors.textQuiet, modifier = Modifier.weight(1f))
    }
}

//@Composable
//private fun ProgramExtras(program: Program, onApply: (String) -> Unit) {
//    val current = ProgramDirectives.format(program.challenges, program.brief)
//    var text by remember(current) { mutableStateOf(current) }
//    Column(Modifier.padding(start = 12.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
//        Text("This program's challenges and Make brief", style = MaterialTheme.typography.titleSmall)
//        Text(
//            "One per line: “# primm-challenge: …”, optionally followed by “# primm-check: 3 | 4 => 7” " +
//                "(answers to type, then what it should print), and “# primm-brief: …”. The same lines at the top " +
//                "of a .py file you load from a link do this too. Save a session file to hand these to a class.",
//            style = MaterialTheme.typography.bodySmall,
//            color = PrimmColors.textQuiet,
//        )
//        OutlinedTextField(
//            value = text,
//            onValueChange = { text = it },
//            minLines = 4,
//            textStyle = consoleTextStyle(13),
//            modifier = Modifier.fillMaxWidth(),
//        )
//        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//            OutlinedButton(onClick = { onApply(text) }, enabled = text != current) { Text("Apply") }
//        }
//    }
//}
