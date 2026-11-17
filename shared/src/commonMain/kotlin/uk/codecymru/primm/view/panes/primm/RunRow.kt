package uk.codecymru.primm.view.panes.primm

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.codecymru.primm.controller.AppController
import uk.codecymru.primm.model.AppState
import uk.codecymru.primm.model.GuideStep
import uk.codecymru.primm.model.RunInsights
import uk.codecymru.primm.model.RunOutcome
import uk.codecymru.primm.model.RunRecord
import uk.codecymru.primm.model.TableTarget
import uk.codecymru.primm.view.components.RunTraceTable
import uk.codecymru.primm.view.components.TraceTableEditor
import uk.codecymru.primm.view.components.GuideBox
import uk.codecymru.primm.view.components.NextTag
import uk.codecymru.primm.view.components.ButtonType
import uk.codecymru.primm.view.components.PrimmButton
import uk.codecymru.primm.view.consoleTextStyle
import primm.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import uk.codecymru.primm.model.tr

@Composable
fun RunRow(state: AppState, controller: AppController, scroll: ScrollState) {
    val run = state.run
    val cellStyle = consoleTextStyle(state.preferences.fontSize - 1)
    val codeLines = state.code.removeSuffix("\n").split('\n')

    LaunchedEffect(run?.rows?.size) {
        if (run != null && !run.isFinished) scroll.animateScrollTo(scroll.maxValue)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val prompted = state.isRunPrompted && state.activeStep == GuideStep.RunProgram
        val allowed = state.canRun && !state.isRunGated && state.modifiedCode == null
        GuideBox(active = prompted) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrimmButton(
                    ButtonType.TRANSPARENT,
                    allowed,
                    controller::runOriginal,
                    text = stringResource(Res.string.RUN_BUTTON),
                    icon = null
                )
                PrimmButton(
                    ButtonType.OUTLINE,
                    allowed,
                    controller::stepOriginal,
                    text = stringResource(Res.string.RUN_LINE_BY_LINE),
                    icon = null
                )
                if (prompted) NextTag()
            }
            if (state.modifiedCode != null) {
                Hint(stringResource(Res.string.RUN_RUN_KEPT_INVESTIGATE_RUN))
            }
        }
        Column {
            Option(stringResource(Res.string.RUN_FILL_AUTOMATICALLY), state.runSettings.autoFill, controller::setAutoFill)
            Option(stringResource(Res.string.RUN_USE_MY_PREDICTED_INPUTS), state.runSettings.usePredictedInputs, controller::setUsePredictedInputs)
        }

        if (run == null) {
            Hint(stringResource(Res.string.RUN_RUN_PROGRAM_FILL), Modifier.padding(horizontal = 10.dp))
        } else {
            Section(stringResource(Res.string.RUN_WHAT_HAPPENED)) {
                Text(outcomeText(state, run))
                if (run.traceTruncated) {
                    Hint(stringResource(Res.string.RUN_TRACE_STOPPED_AFTER_500))
                }
            }
            if (run.inputs.isNotEmpty()) {
                Section(stringResource(Res.string.RUN_INPUTS_USED)) {
                    for (input in run.inputs) {
                        val where = input.line?.let { stringResource(Res.string.RUN_LINE, it) } ?: stringResource(Res.string.RUN_INPUT)
                        val from = if (input.automatic) "  " + stringResource(Res.string.REPORT_FROM_PREDICT) else ""
                        Text("$where: ${input.value}$from", style = cellStyle)
                    }
                }
            }
            val loops = RunInsights.loopSummaries(run, state.analysis)
            if (loops.isNotEmpty()) {
                Section(stringResource(Res.string.RUN_LOOPS)) {
                    for (loop in loops) Text(loop.text)
                }
            }
            Section(stringResource(Res.string.RUN_TRACE_TABLE)) {
                if (state.runSettings.autoFill) {
                    if (run.rows.isEmpty()) {
                        Hint(if (run.isFinished) stringResource(Res.string.RUN_NO_LINES_RAN) else stringResource(Res.string.RUN_WAITING_FIRST_LINE))
                    } else {
                        RunTraceTable(
                            record = run,
                            analysis = state.analysis,
                            plan = state.shownTracePlan,
                            columns = state.prediction.table.visibleVariables,
                            codeLines = codeLines,
                            paused = state.isPaused,
                            textStyle = cellStyle,
                            style = if (state.canChooseTraceStyle) state.traceStyle else null,
                        )
                    }
                } else {
                    Hint(stringResource(Res.string.RUN_FILL_YOURSELF_STEP_THROUGH))
                    TraceTableEditor(
                        table = state.manualRunTable,
                        plan = if (state.manualRunTable.byPass) state.tracePlan else state.shownTracePlan,
                        codeLines = codeLines,
                        conditionLines = state.analysis?.conditionLines ?: emptySet(),
                        textStyle = cellStyle,
                        actions = tableActions(controller, TableTarget.ManualRun),
                    )
                }
            }
        }
    }
}

@Composable
private fun Option(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun outcomeText(state: AppState, run: RunRecord): String = when (val outcome = run.outcome) {
    null -> when {
        state.isPaused -> tr(Res.string.RUN_PAUSED_BEFORE_HIGHLIGHTED_LINE)
        state.isWaitingForInput -> tr(Res.string.RUN_WAITING_INPUT_CONSOLE)
        else -> tr(Res.string.RUN_RUNNING)
    }
    RunOutcome.Completed -> tr(Res.string.RUN_NO_ERRORS_PROGRAM_RAN)
    is RunOutcome.Stopped -> tr(Res.string.RUN_STOPPED_BEFORE_END)
    is RunOutcome.Failed -> {
        val error = outcome.error
        error.line?.let { tr(Res.string.ERROR_ON_LINE, error.type, it, error.message) }
            ?: tr(Res.string.ERROR_NO_LINE, error.type, error.message)
    }
}
