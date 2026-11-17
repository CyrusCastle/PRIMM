package uk.codecymru.primm.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import uk.codecymru.primm.controller.AppController
import uk.codecymru.primm.controller.LocaleController
import uk.codecymru.primm.model.AppState
import uk.codecymru.primm.model.DialogState
import kotlinx.coroutines.delay
import uk.codecymru.primm.view.dialogs.CompareDialog
import uk.codecymru.primm.view.dialogs.ConfirmBackToOriginalDialog
import uk.codecymru.primm.view.dialogs.ConfirmRemoveFileDialog
import uk.codecymru.primm.view.dialogs.ConfirmReplaceDialog
import uk.codecymru.primm.view.dialogs.ConfirmStartModifyingDialog
import uk.codecymru.primm.view.dialogs.SaveDialog
import uk.codecymru.primm.view.dialogs.SettingsDialog
import uk.codecymru.primm.view.components.SplitPane
import uk.codecymru.primm.view.dialogs.LoadDialog
import uk.codecymru.primm.view.panes.ConsolePane
import uk.codecymru.primm.view.panes.EditorPanel
import uk.codecymru.primm.view.panes.FilesView
import uk.codecymru.primm.view.panes.PrimmColumn

private const val AutosaveDelayMs = 600L

private val StackBelowWidth = 960.dp

@Composable
fun MainScreen(controller: AppController) {
    val state by controller.state.collectAsState()
    val language by LocaleController.language.collectAsState()

    LaunchedEffect(state) {
        delay(AutosaveDelayMs)
        controller.autosave()
    }

    Column(Modifier.fillMaxSize().background(PrimmAdditionalColors.page)) {
        TopBar(
            state = state,
            onLoad = controller::openLoadDialog,
            onSave = controller::openSaveDialog,
            onAutosaveChange = controller::setAutosave,
            onOpenSettings = controller::openSettings,
            language = language,
            onToggleLanguage = LocaleController::toggle,
        )
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            if (maxWidth < StackBelowWidth) {
                StackedLayout(state, controller)
            } else {
                SideBySideLayout(state, controller)
            }
        }
    }

    when (val dialog = state.dialog) {
        is DialogState.LoadProgram -> LoadDialog(dialog, state.preferences.fontSize, controller)
        is DialogState.ConfirmReplace -> ConfirmReplaceDialog(
            pending = dialog.pending,
            onReplace = controller::confirmReplace,
            onKeep = controller::closeDialog,
        )
        DialogState.Settings -> SettingsDialog(
            settings = state.settings,
            program = state.program,
            onProgramExtrasChange = controller::setProgramExtras,
            onMakeBriefOnlyChange = controller::setMakeBriefOnly,
            onStopAtInvestigateChange = controller::setStopAtInvestigate,
            onUnlockEditorChange = controller::setUnlockEditor,
            onGateRunChange = controller::setGateRun,
            onOneRowAtATimeChange = controller::setOneRowAtATime,
            onOpenRowsChange = controller::setOpenRows,
            onRevealAllChange = controller::setRevealAll,
            studentLink = controller.studentLink(),
            studentLinkNote = state.studentLinkNote,
            onCopyStudentLink = controller::copyStudentLink,
            onClose = controller::closeDialog,
        )
        DialogState.ConfirmStartModifying -> ConfirmStartModifyingDialog(
            onStart = controller::startModifying,
            onCancel = controller::closeDialog,
        )
        is DialogState.ConfirmRemoveFile -> ConfirmRemoveFileDialog(
            name = dialog.name,
            onRemove = { controller.removeFile(dialog.name) },
            onKeep = controller::closeDialog,
        )
        DialogState.ConfirmBackToOriginal -> ConfirmBackToOriginalDialog(
            onReset = controller::backToOriginal,
            onKeep = controller::closeDialog,
        )
        DialogState.CompareWithOriginal -> CompareDialog(
            state.modifiedDiff,
            state.preferences.fontSize,
            controller::closeDialog
        )
        is DialogState.Save -> SaveDialog(dialog, state.studentNames, controller)
        null -> Unit
    }
}

@Composable
private fun SideBySideLayout(state: AppState, controller: AppController) {
    val passThroughEditor: (Boolean) -> Unit = { dragging -> bridgeEditorSetInteractive(!dragging) }
    SplitPane(
        orientation = Orientation.Horizontal,
        fraction = state.preferences.codeFraction,
        onFractionCommitted = controller::setCodeFraction,
        minFirst = 380.dp,
        minSecond = 320.dp,
        onDragActiveChange = passThroughEditor,
        modifier = Modifier.fillMaxSize(),
        first = {
            SplitPane(
                orientation = Orientation.Vertical,
                fraction = state.preferences.editorFraction,
                onFractionCommitted = controller::setEditorFraction,
                minFirst = 140.dp,
                minSecond = 110.dp,
                onDragActiveChange = passThroughEditor,
                modifier = Modifier.fillMaxSize(),
                first = { Editor(state, controller, Modifier.fillMaxSize()) },
                second = { Console(state, controller, Modifier.fillMaxSize()) },
            )
        },
        second = { PrimmColumn(state, controller, Modifier.fillMaxSize()) },
    )
}

@Composable
private fun StackedLayout(state: AppState, controller: AppController) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Editor(state, controller, Modifier.weight(0.45f).fillMaxWidth())
        Console(state, controller, Modifier.weight(0.25f).fillMaxWidth())
        PrimmColumn(state, controller, Modifier.weight(0.30f).fillMaxWidth())
    }
}

@Composable
private fun Editor(state: AppState, controller: AppController, modifier: Modifier) {
    EditorPanel(
        state = state,
        onCodeChange = controller::updateCode,
        onSmallerText = controller::decreaseFontSize,
        onLargerText = controller::increaseFontSize,
        onSelectTab = controller::selectTab,
        onChooseMarkTool = controller::chooseMarkTool,
        onToggleMarks = controller::toggleMarksVisible,
        onClearMarks = controller::clearMarks,
        modifier = modifier,
    )
}

@Composable
private fun Console(state: AppState, controller: AppController, modifier: Modifier) {
    ConsolePane(
        state = state,
        onRun = controller::run,
        onStep = controller::step,
        onNext = controller::next,
        onContinue = controller::continueRun,
        onStop = controller::stop,
        onClear = controller::clearConsole,
        onSubmitInput = controller::submitInput,
        onShowTab = controller::showConsoleTab,
        files = { FilesView(state, controller, it) },
        modifier = modifier,
    )
}

@Composable
fun PaneCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .clip(shape)
            .background(PrimmAdditionalColors.pane)
            .border(1.dp, PrimmAdditionalColors.border, shape)
    ) {
        content()
    }
}
