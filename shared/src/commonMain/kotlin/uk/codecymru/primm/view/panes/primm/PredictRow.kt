package uk.codecymru.primm.view.panes.primm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.codecymru.primm.controller.AppController
import uk.codecymru.primm.model.AppState
import uk.codecymru.primm.model.ExpectedErrorKind
import uk.codecymru.primm.model.Guide
import uk.codecymru.primm.model.GuideStep
import uk.codecymru.primm.model.TableTarget
import uk.codecymru.primm.model.TraceStyle
import uk.codecymru.primm.view.components.ChoiceChooser
import uk.codecymru.primm.view.components.CellField
import uk.codecymru.primm.view.components.LineChooser
import uk.codecymru.primm.view.PrimmAdditionalColors
import uk.codecymru.primm.view.components.SmallButton
import uk.codecymru.primm.view.components.TraceTableActions
import uk.codecymru.primm.view.components.TraceTableEditor
import uk.codecymru.primm.view.components.GuideBox
import uk.codecymru.primm.view.components.NextTag
import uk.codecymru.primm.view.consoleTextStyle
import uk.codecymru.primm.view.components.PrimmSegmentedButton
import uk.codecymru.primm.view.components.Segment
import primm.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun PredictRow(state: AppState, controller: AppController) {
    val prediction = state.prediction
    val analysis = state.analysis
    val codeLines = state.code.removeSuffix("\n").split('\n')
    val cellStyle = consoleTextStyle(state.preferences.fontSize - 1)
    val active = state.activeStep

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.isRunGated) {
            val (done, total) = Guide.predictCount(state)
            Hint(stringResource(Res.string.PREDICT_FILL_EACH_PART_UNLOCK, done, total), Modifier.padding(horizontal = 10.dp))
        }

        Section(stringResource(Res.string.PREDICT_WHAT_DOES_PROGRAM_DO), active = active == GuideStep.Summary) {
            OutlinedTextField(
                value = prediction.summary,
                onValueChange = controller::setSummary,
                minLines = 2,
                placeholder = { Text(stringResource(Res.string.PREDICT_TWO_THREE_SENTENCES_WHAT)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PrimmAdditionalColors.pane,
                    unfocusedContainerColor = PrimmAdditionalColors.pane,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (analysis != null && !analysis.parsed) {
            Text(
                stringResource(Res.string.PREDICT_SOME_HELPERS_ARENT_AVAILABLE),
                color = PrimmAdditionalColors.consoleHint,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimmAdditionalColors.lockedTint, RoundedCornerShape(6.dp))
                    .padding(10.dp),
            )
        }

        Section(stringResource(Res.string.PREDICT_INPUTS_I_WILL_TYPE), active = active == GuideStep.Inputs) {
            val calls = analysis?.inputs ?: emptyList()
            when {
                analysis == null -> Hint(stringResource(Res.string.PREDICT_LOOKING_INPUT_CALLS))
                calls.isEmpty() && prediction.inputs.items.isEmpty() -> Hint(stringResource(Res.string.PREDICT_PROGRAM_DOESNT_ASK_INPUT))
            }
            for (call in calls) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.PREDICT_INPUT_CALL, call.line, call.label), style = cellStyle)
                    if (call.inLoop) {
                        Badge(stringResource(Res.string.PREDICT_IN_LOOP))
                        SmallButton(
                            stringResource(Res.string.PREDICT_ANOTHER_ANSWER),
                            onClick = { controller.addPlannedInput(call.line) })
                    }
                }
            }
            val callLines = calls.map { it.line }
            for ((index, item) in prediction.inputs.items.withIndex()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${index + 1}.", style = cellStyle, color = PrimmAdditionalColors.textQuiet, modifier = Modifier.width(28.dp))
                    LineChooser(
                        line = item.inputLine,
                        codeLines = codeLines,
                        textStyle = cellStyle,
                        onChoose = { controller.setPlannedInputLine(item.id, it) },
                        modifier = Modifier.width(60.dp),
                        choices = callLines.ifEmpty { null },
                    )
                    CellField(
                        value = item.value,
                        onValueChange = { controller.setPlannedInputValue(item.id, it) },
                        textStyle = cellStyle,
                        placeholder = stringResource(Res.string.PREDICT_WHAT_YOULL_TYPE),
                        modifier = Modifier.width(180.dp),
                    )
                    SmallButton(
                        "↑",
                        onClick = { controller.movePlannedInput(item.id, -1) },
                        enabled = index > 0
                    )
                    SmallButton(
                        "↓",
                        onClick = { controller.movePlannedInput(item.id, 1) },
                        enabled = index < prediction.inputs.items.lastIndex
                    )
                    SmallButton("✕", onClick = { controller.removePlannedInput(item.id) })
                }
            }
            if (calls.isEmpty() && analysis != null && analysis.parsed.not()) {
                SmallButton(stringResource(Res.string.PREDICT_ADD_INPUT), onClick = { controller.addPlannedInput(null) })
            }
        }

        Section(stringResource(Res.string.PREDICT_TRACE_TABLE), active = active == GuideStep.TraceTable) {
            if (state.canChooseTraceStyle) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChooser(
                        value = state.traceStyle.name,
                        choices = TraceStyle.entries.map { it.name to it.title },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        onChoose = { name -> TraceStyle.entries.firstOrNull { it.name == name }?.let(controller::setTraceStyle) },
                        modifier = Modifier.width(140.dp),
                        allowNone = false,
                    )
                }
            }
            Hint(stringResource(if (prediction.table.byPass) Res.string.PREDICT_ONE_ROW_PER_PASS else Res.string.PREDICT_ONE_ROW_PER_LINE))
            TraceTableEditor(
                table = prediction.table,
                plan = state.shownTracePlan,
                codeLines = codeLines,
                conditionLines = analysis?.conditionLines ?: emptySet(),
                textStyle = cellStyle,
                actions = tableActions(controller, TableTarget.Prediction),
            )
        }

        val loops = if (prediction.table.byPass) emptyList() else analysis?.loops ?: emptyList()
        if (loops.isNotEmpty()) {
            Section(stringResource(Res.string.PREDICT_LOOP_WILL_END_WHEN), active = active == GuideStep.LoopEnds) {
                for (loop in loops) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.PREDICT_LOOP_LINE_WILL_END, loop.line), style = MaterialTheme.typography.bodyMedium)
                        CellField(
                            value = prediction.loopEnds[loop.line] ?: "",
                            onValueChange = { controller.setLoopEnd(loop.line, it) },
                            textStyle = cellStyle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (analysis != null && analysis.fileUses.isNotEmpty()) {
            Section(stringResource(Res.string.PREDICT_FILES)) {
                FilePredictions(state, controller, cellStyle)
            }
        }

        Section(stringResource(Res.string.PREDICT_EXPECTED_BUGS_ERRORS), active = active == GuideStep.ExpectedError) {
            PrimmSegmentedButton(
                segments = ExpectedErrorKind.entries.map { Segment(it, it.title) },
                selected = prediction.expectedError.kind,
                onSelect = controller::setExpectedErrorKind,
            )
            val kind = prediction.expectedError.kind
            if (kind != null && kind != ExpectedErrorKind.None) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.PREDICT_LINE_LABEL), style = MaterialTheme.typography.bodyMedium)
                    LineChooser(
                        line = prediction.expectedError.line,
                        codeLines = codeLines,
                        textStyle = cellStyle,
                        onChoose = controller::setExpectedErrorLine,
                        modifier = Modifier.width(60.dp),
                    )
                    CellField(
                        value = prediction.expectedError.reason,
                        onValueChange = controller::setExpectedErrorReason,
                        textStyle = cellStyle,
                        placeholder = stringResource(Res.string.PREDICT_WHY),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilePredictions(state: AppState, controller: AppController, cellStyle: TextStyle) {
    val analysis = state.analysis ?: return
    val prediction = state.prediction
    for (use in analysis.fileUses) {
        val what = use.name?.let { "${use.verb} $it" } ?: stringResource(Res.string.PREDICT_FILE_WHOSE_NAME_WORKED, use.verb)
        Text(stringResource(Res.string.PREDICT_INPUT_CALL, use.line, what), style = cellStyle)
    }
    val unnamedWrites = analysis.fileUses.any { it.name == null && it.writes }
    val candidates = (analysis.writtenFiles + (if (unnamedWrites) state.files.names else emptyList()) + prediction.files.keys).distinct()
    if (candidates.isEmpty()) {
        Hint(stringResource(Res.string.PREDICT_PROGRAM_ONLY_READS_FILES))
        return
    }
    Hint(stringResource(Res.string.PREDICT_TICK_FILE_IF_THINK))
    for (name in candidates) {
        val predicted = prediction.files[name]
        val exists = state.files.file(name) != null
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = predicted != null, onCheckedChange = { controller.setPredictingFile(name, it) })
            Text(
                if (exists) stringResource(Res.string.PREDICT_I_THINK_WILL_CHANGE, name) else stringResource(Res.string.PREDICT_I_THINK_NEW_FILE, name),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (predicted != null) {
            OutlinedTextField(
                value = predicted,
                onValueChange = { controller.setPredictedFile(name, it) },
                minLines = 3,
                textStyle = cellStyle,
                placeholder = { Text(stringResource(Res.string.PREDICT_WHAT_WILL_HOLD_AFTER, name), style = cellStyle) },
                colors = paneFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
            )
        }
    }
}

fun tableActions(controller: AppController, target: TableTarget) = TraceTableActions(
    addRow = { controller.addTraceRow(target) },
    setLine = { id, line -> controller.setTraceLine(target, id, line) },
    setValue = { id, variable, value -> controller.setTraceValue(target, id, variable, value) },
    setCondition = { id, condition -> controller.setTraceCondition(target, id, condition) },
    setOutput = { id, output -> controller.setTraceOutput(target, id, output) },
    remove = { id -> controller.removeTraceRow(target, id) },
    move = { id, by -> controller.moveTraceRow(target, id, by) },
    addVariable = { name -> controller.addTraceVariable(target, name) },
    toggleVariable = { name -> controller.toggleTraceVariable(target, name) },
    addPass = { loop -> controller.addTracePass(target, loop) },
    setDecision = { id, head, value -> controller.setTraceDecision(target, id, head, value) },
    setAgain = { id, again -> controller.setTraceAgain(target, id, again) },
)

@Composable
fun Section(title: String, active: Boolean = false, content: @Composable () -> Unit) {
    GuideBox(active) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (active) NextTag()
        }
        content()
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = PrimmAdditionalColors.textQuiet, modifier = modifier)
}

@Composable
fun Badge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = PrimmAdditionalColors.consoleHint,
        modifier = Modifier
            .background(PrimmAdditionalColors.nextLine, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

