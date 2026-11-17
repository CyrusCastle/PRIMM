package uk.codecymru.primm.view.panes.primm

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.codecymru.primm.controller.AppController
import uk.codecymru.primm.model.AlignedRow
import uk.codecymru.primm.model.AppState
import uk.codecymru.primm.model.Comparison
import uk.codecymru.primm.model.ErrorVerdict
import uk.codecymru.primm.model.ExpectedError
import uk.codecymru.primm.model.ExpectedErrorKind
import uk.codecymru.primm.model.Guide
import uk.codecymru.primm.model.LoopKind
import uk.codecymru.primm.model.GuideStep
import uk.codecymru.primm.model.Mismatch
import uk.codecymru.primm.model.MismatchKind
import uk.codecymru.primm.model.RowMatch
import uk.codecymru.primm.model.RunOutcome
import uk.codecymru.primm.model.SelfMark
import uk.codecymru.primm.model.TraceLabels
import uk.codecymru.primm.model.TracePlan
import uk.codecymru.primm.view.components.CellGap
import uk.codecymru.primm.view.components.ConditionWidth
import uk.codecymru.primm.view.components.HeaderCell
import uk.codecymru.primm.view.components.LineWidth
import uk.codecymru.primm.view.components.OutputWidth
import uk.codecymru.primm.view.PrimmAdditionalColors
import uk.codecymru.primm.view.components.SmallButton
import uk.codecymru.primm.view.components.ValueWidth
import uk.codecymru.primm.view.components.AgainWidth
import uk.codecymru.primm.view.components.StageWidth
import uk.codecymru.primm.view.components.decisionWidth
import uk.codecymru.primm.view.components.GuideBox
import uk.codecymru.primm.view.components.NextTag
import uk.codecymru.primm.view.consoleTextStyle
import uk.codecymru.primm.view.components.PrimmSegmentedButton
import uk.codecymru.primm.view.components.Segment
import uk.codecymru.primm.view.components.icon
import primm.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import uk.codecymru.primm.model.tr
import uk.codecymru.primm.model.plural

private val MarkWidth = 22.dp
private val ReflectionWidth = 460.dp

@Composable
fun InvestigateRow(state: AppState, controller: AppController) {
    val comparison = state.comparison
    if (comparison == null) {
        val text = if (state.isProgramRunning) stringResource(Res.string.INVESTIGATE_WAITING_RUN_FINISH) else stringResource(Res.string.INVESTIGATE_RUN_PROGRAM_COMPARE_PREDICTION)
        Hint(text, Modifier.padding(horizontal = 10.dp))
        return
    }
    val active = state.activeStep
    val next = if (active == GuideStep.Investigate) Guide.nextToExplain(state)?.key else null
    val cellStyle = consoleTextStyle(state.preferences.fontSize - 1)
    val boxes = ReflectionBoxes(state, controller, next)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Hint(introText(state, comparison))
            if (comparison.outcome is RunOutcome.Stopped) {
                Hint(stringResource(Res.string.INVESTIGATE_RUN_STOPPED_BEFORE_END))
            }
            if (comparison.traceTruncated) {
                Hint(stringResource(Res.string.INVESTIGATE_ONLY_FIRST_500_LINES))
            }
        }

        Section(stringResource(Res.string.INVESTIGATE_TRACE_TABLE)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend("✓", stringResource(Res.string.INVESTIGATE_LEGEND_MATCHES), PrimmAdditionalColors.matches)
                Legend("≠", stringResource(Res.string.INVESTIGATE_LEGEND_DIFFERENT), PrimmAdditionalColors.differs)
                Legend("−", stringResource(Res.string.INVESTIGATE_DIDNT_RUN), PrimmAdditionalColors.notRun)
                Legend("+", stringResource(Res.string.INVESTIGATE_RAN_NOT_PREDICTED), PrimmAdditionalColors.differs)
            }
            if (comparison.byPass) PassComparedTable(state, comparison, cellStyle, boxes)
            else ComparedTable(state, comparison, cellStyle, boxes)
        }

        if (comparison.inputs.isNotEmpty()) {
            Section(stringResource(Res.string.INVESTIGATE_INPUTS)) {
                for (input in comparison.inputs) {
                    val mismatch = comparison.mismatches.firstOrNull { it.kind == MismatchKind.Input && it.inputPosition == input.position }
                    val revealed = mismatch == null || state.isRevealed(mismatch.key)
                    val got = when {
                        !revealed -> "?"
                        input.actual == null -> stringResource(Res.string.INVESTIGATE_NEVER_ASKED)
                        else -> input.actual.value
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Mark(if (input.matches) "✓" else "≠", if (input.matches) PrimmAdditionalColors.matches else PrimmAdditionalColors.differs)
                        Text(
                            stringResource(Res.string.INVESTIGATE_INPUT_ROW, input.position + 1, input.predicted?.value ?: stringResource(Res.string.REPORT_NOTHING), got),
                            style = cellStyle,
                        )
                    }
                    if (mismatch != null) boxes.Box(mismatch)
                }
            }
        }

        if (comparison.files.isNotEmpty()) {
            Section(stringResource(Res.string.INVESTIGATE_FILES)) {
                Hint(stringResource(Res.string.INVESTIGATE_ONLY_FILES_PREDICTED_WOULD))
                for (file in comparison.files) {
                    val mismatch = comparison.mismatches.firstOrNull { it.kind == MismatchKind.File && it.fileName == file.name }
                    val revealed = mismatch == null || state.isRevealed(mismatch.key)
                    val actual = when {
                        !revealed -> "?"
                        file.actual == null -> stringResource(Res.string.INVESTIGATE_FILE_DOESNT_EXIST)
                        else -> file.actual.ifEmpty { stringResource(Res.string.INVESTIGATE_EMPTY) }
                    }
                    MarkedText(if (file.matches) "✓" else "≠", if (file.matches) PrimmAdditionalColors.matches else PrimmAdditionalColors.differs, file.name)
                    SideBySide(stringResource(Res.string.INVESTIGATE_PREDICTED_WOULD_HOLD), file.predicted.ifEmpty { stringResource(Res.string.INVESTIGATE_EMPTY) }, stringResource(Res.string.INVESTIGATE_AFTER_RUN_HELD), actual, cellStyle)
                    if (mismatch != null) boxes.Box(mismatch)
                }
            }
        }

        Section(stringResource(Res.string.INVESTIGATE_ERRORS)) {
            val error = comparison.error
            Text(stringResource(Res.string.INVESTIGATE_PREDICTED, describe(error.predicted)), style = MaterialTheme.typography.bodyMedium)
            when (error.verdict) {
                ErrorVerdict.Match -> MarkedText("✓", PrimmAdditionalColors.matches, error.actualText)
                ErrorVerdict.Different -> {
                    val mismatch = comparison.mismatches.first { it.kind == MismatchKind.Error }
                    val text = if (state.isRevealed(mismatch.key)) error.actualText else stringResource(Res.string.INVESTIGATE_SOMETHING_DIFFERENT_HAPPENED)
                    MarkedText("≠", PrimmAdditionalColors.differs, text)
                    boxes.Box(mismatch)
                }
                ErrorVerdict.SelfMark -> {
                    Text(error.actualText, style = MaterialTheme.typography.bodyMedium)
                    Hint(stringResource(Res.string.INVESTIGATE_THERE_LOGIC_ERROR_CAUSED))
                }
                ErrorVerdict.NotComparable -> Hint(error.actualText)
            }
        }

        Section(stringResource(Res.string.INVESTIGATE_MARK_ANSWERS), active = active == GuideStep.MarkAnswers) {
            Hint(stringResource(Res.string.INVESTIGATE_THESE_CANT_CHECKED_AUTOMATICALLY))
            SideBySide(stringResource(Res.string.INVESTIGATE_SUMMARY), state.prediction.summary.ifBlank { stringResource(Res.string.INVESTIGATE_BLANK) }, stringResource(Res.string.INVESTIGATE_WHAT_PROGRAM_PRINTED), comparison.output.ifBlank { stringResource(Res.string.INVESTIGATE_NOTHING) }, cellStyle)
            MarkChips(state.investigation.summaryMark, controller::setSummaryMark)
            for (check in comparison.loops) {
                SideBySide(
                    stringResource(Res.string.INVESTIGATE_SAID_LOOP_LINE_WOULD, check.loop.line),
                    check.predicted.ifBlank { stringResource(Res.string.INVESTIGATE_BLANK) },
                    stringResource(Res.string.INVESTIGATE_WHAT_DID),
                    check.actual,
                    null,
                )
                MarkChips(state.investigation.loopMarks[check.loop.line]) { controller.setLoopMark(check.loop.line, it) }
            }
            if (comparison.error.verdict == ErrorVerdict.SelfMark) {
                SideBySide(stringResource(Res.string.INVESTIGATE_YOU_PREDICTED), describe(comparison.error.predicted), stringResource(Res.string.INVESTIGATE_WHAT_HAPPENED), comparison.error.actualText, null)
                MarkChips(state.investigation.errorMark, controller::setErrorMark)
            }
        }

        Section(stringResource(Res.string.INVESTIGATE_CLOSING_REFLECTION), active = active == GuideStep.Reflect) {
            Text(stringResource(Res.string.INVESTIGATE_REWRITE_SUMMARY), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.investigation.rewrittenSummary,
                onValueChange = controller::setRewrittenSummary,
                minLines = 2,
                placeholder = {
                    Text(if (state.investigation.summaryMark == SelfMark.Right) stringResource(Res.string.INVESTIGATE_RIGHT_NOTHING_CHANGE) else stringResource(Res.string.INVESTIGATE_SUMMARY_WHAT_HAPPENED))
                },
                colors = paneFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(Res.string.INVESTIGATE_LEARNED), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.investigation.learned,
                onValueChange = controller::setLearned,
                minLines = 2,
                colors = paneFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun introText(state: AppState, comparison: Comparison): String {
    val total = comparison.mismatches.size
    if (total == 0) return tr(Res.string.INVESTIGATE_ALL_MATCHED)
    val explained = comparison.mismatches.count { state.investigation.isExplained(it.key) }
    val count = plural(total, Res.string.COUNT_DIFFERENCE_ONE, Res.string.COUNT_DIFFERENCE_OTHER)
    return if (state.settings.revealAll) {
        tr(Res.string.INVESTIGATE_INTRO, explained, count)
    } else {
        tr(Res.string.INVESTIGATE_INTRO_REVEAL, explained, count)
    }
}

private fun describe(error: ExpectedError): String {
    val kind = error.kind ?: return tr(Res.string.REPORT_NOTHING)
    if (kind == ExpectedErrorKind.None) return kind.title
    val described = error.line?.let { tr(Res.string.REPORT_KIND_ON_LINE, kind.title, it) } ?: kind.title
    return error.reason.takeIf { it.isNotBlank() }?.let { tr(Res.string.REPORT_WITH_REASON, described, it) } ?: described
}

private class ReflectionBoxes(val state: AppState, val controller: AppController, val next: String?) {
    @Composable
    fun Box(mismatch: Mismatch) {
        val key = mismatch.key
        val text = state.investigation.reflection(key)
        val active = key == next
        val byPass = state.comparison?.byPass == true
        val question = when (mismatch.kind) {
            MismatchKind.NotRun -> stringResource(if (byPass) Res.string.INVESTIGATE_WHY_NO_PASS else Res.string.INVESTIGATE_WHY_NOT_RUN)
            MismatchKind.NotPredicted -> stringResource(if (byPass) Res.string.INVESTIGATE_WHY_EXTRA_PASS else Res.string.INVESTIGATE_WHY_RAN)
            else -> stringResource(Res.string.INVESTIGATE_WHY_DIFFERENT)
        }
        val canReveal = mismatch.kind != MismatchKind.NotRun && !state.settings.revealAll
        GuideBox(active, Modifier.width(ReflectionWidth).padding(vertical = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    question,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (active) NextTag()
            }
            OutlinedTextField(
                value = text,
                onValueChange = { controller.setReflection(key, it) },
                minLines = 2,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = paneFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (canReveal) {
                if (state.isRevealed(key)) {
                    Hint(stringResource(Res.string.INVESTIGATE_REVEALED))
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmallButton(
                            stringResource(Res.string.INVESTIGATE_REVEAL_WHAT_HAPPENED),
                            onClick = { controller.reveal(key) },
                            enabled = text.isNotBlank()
                        )
                        if (text.isBlank()) Hint(stringResource(Res.string.INVESTIGATE_WRITE_EXPLANATION_FIRST))
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparedTable(state: AppState, comparison: Comparison, textStyle: TextStyle, boxes: ReflectionBoxes) {
    val columns = comparison.columns
    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
            HeaderCell("", MarkWidth, textStyle)
            HeaderCell(stringResource(Res.string.INVESTIGATE_LINE), LineWidth, textStyle)
            for (variable in columns) HeaderCell(variable, ValueWidth, textStyle)
            HeaderCell(stringResource(Res.string.INVESTIGATE_CONDITION), ConditionWidth, textStyle)
            HeaderCell(stringResource(Res.string.INVESTIGATE_OUTPUT), OutputWidth, textStyle)
        }
        HorizontalDivider(color = PrimmAdditionalColors.border)
        for ((index, row) in comparison.rows.withIndex()) {
            val mismatch = comparison.mismatchFor(index)
            val revealed = mismatch == null || state.isRevealed(mismatch.key)
            when (row.match) {
                RowMatch.Match -> PredictedRow(row, columns, textStyle, "✓", PrimmAdditionalColors.matches)
                RowMatch.Different -> {
                    PredictedRow(row, columns, textStyle, "≠", PrimmAdditionalColors.differs)
                    if (revealed) ActualRow(row, columns, textStyle, "↳", PrimmAdditionalColors.textQuiet)
                }
                RowMatch.NotRun -> PredictedRow(row, columns, textStyle, "−", PrimmAdditionalColors.notRun, struck = true)
                RowMatch.NotPredicted -> when {
                    revealed -> ActualRow(row, columns, textStyle, "+", PrimmAdditionalColors.differs)
                    index == mismatch.rows.first -> {
                        val count = mismatch.rows.last - mismatch.rows.first + 1
                        Text(
                            if (count == 1) stringResource(Res.string.INVESTIGATE_ONE_LINE_NOT_PREDICTED) else stringResource(Res.string.INVESTIGATE_LINES_NOT_PREDICTED, count),
                            style = textStyle,
                            color = PrimmAdditionalColors.differs,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
            comparison.mismatchEndingAt(index)?.let { boxes.Box(it) }
        }
    }
}

@Composable
private fun PassComparedTable(state: AppState, comparison: Comparison, textStyle: TextStyle, boxes: ReflectionBoxes) {
    val plan = state.shownTracePlan
    val table = state.prediction.table
    val codeLines = state.code.removeSuffix("\n").split('\n')
    val columns = comparison.columns
    val hasWhile = plan.loops.any { it.kind == LoopKind.While }
    val layout = PassLayout(plan, columns, codeLines, hasWhile)
    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
            HeaderCell("", MarkWidth, textStyle)
            HeaderCell(stringResource(Res.string.TRACE_PASS_HEADER), StageWidth, textStyle)
            for (variable in columns) HeaderCell(variable, ValueWidth, textStyle)
            for (chain in plan.chains) HeaderCell(TraceLabels.decisionHeader(chain, codeLines), decisionWidth(chain.isSingle), textStyle)
            if (hasWhile) HeaderCell(stringResource(Res.string.TRACE_LOOP_AGAIN), AgainWidth, textStyle)
            HeaderCell(stringResource(Res.string.INVESTIGATE_OUTPUT), OutputWidth, textStyle)
        }
        HorizontalDivider(color = PrimmAdditionalColors.border)
        for ((index, row) in comparison.rows.withIndex()) {
            val mismatch = comparison.mismatchFor(index)
            val revealed = mismatch == null || state.isRevealed(mismatch.key)
            val predicted = row.predicted
            val label = predicted?.stage?.let { TraceLabels.stage(it, table.passNumber(predicted), plan) } ?: ""
            when (row.match) {
                RowMatch.Match -> PredictedPassRow(row, label, layout, textStyle, "✓", PrimmAdditionalColors.matches)
                RowMatch.Different -> {
                    PredictedPassRow(row, label, layout, textStyle, "≠", PrimmAdditionalColors.differs)
                    if (revealed) ActualPassRow(row, layout, textStyle, "↳", PrimmAdditionalColors.textQuiet)
                }
                RowMatch.NotRun -> PredictedPassRow(row, label, layout, textStyle, "−", PrimmAdditionalColors.notRun, struck = true)
                RowMatch.NotPredicted -> when {
                    revealed -> ActualPassRow(row, layout, textStyle, "+", PrimmAdditionalColors.differs)
                    index == mismatch.rows.first -> {
                        val count = mismatch.rows.last - mismatch.rows.first + 1
                        Text(
                            if (count == 1) stringResource(Res.string.INVESTIGATE_ONE_PASS_NOT_PREDICTED) else stringResource(Res.string.INVESTIGATE_PASSES_NOT_PREDICTED, count),
                            style = textStyle,
                            color = PrimmAdditionalColors.differs,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
            comparison.mismatchEndingAt(index)?.let { boxes.Box(it) }
        }
    }
}

private class PassLayout(val plan: TracePlan, val columns: List<String>, val codeLines: List<String>, val hasWhile: Boolean)

@Composable
private fun PredictedPassRow(
    row: AlignedRow,
    label: String,
    layout: PassLayout,
    textStyle: TextStyle,
    mark: String,
    markColour: Color,
    struck: Boolean = false,
) {
    val predicted = row.predicted ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(CellGap), verticalAlignment = Alignment.CenterVertically) {
        Cell(mark, MarkWidth, textStyle, markColour, bold = true)
        Cell(label, StageWidth, textStyle, PrimmAdditionalColors.textQuiet, struck = struck)
        for (variable in layout.columns) {
            Cell(predicted.values[variable] ?: "", ValueWidth, textStyle, outlined = variable in row.different, struck = struck)
        }
        for (chain in layout.plan.chains) {
            Cell(
                TraceLabels.decisionText(chain, predicted.decisions[chain.head], layout.codeLines),
                decisionWidth(chain.isSingle), textStyle,
                outlined = AlignedRow.decision(chain.head) in row.different, struck = struck,
            )
        }
        if (layout.hasWhile) {
            Cell(TraceLabels.again(predicted.again), AgainWidth, textStyle, outlined = AlignedRow.AGAIN in row.different, struck = struck)
        }
        Cell(predicted.output, OutputWidth, textStyle, outlined = AlignedRow.OUTPUT in row.different, struck = struck)
    }
}

@Composable
private fun ActualPassRow(row: AlignedRow, layout: PassLayout, textStyle: TextStyle, mark: String, markColour: Color) {
    val stage = row.stage ?: return
    val emphasise = row.match == RowMatch.Different
    fun colour(key: String) = if (emphasise && key in row.different) PrimmAdditionalColors.differs else PrimmAdditionalColors.consoleOutput
    fun bold(key: String) = emphasise && key in row.different
    Row(horizontalArrangement = Arrangement.spacedBy(CellGap), verticalAlignment = Alignment.CenterVertically) {
        Cell(mark, MarkWidth, textStyle, markColour, bold = true)
        Cell(TraceLabels.stage(stage.stage, stage.pass, layout.plan), StageWidth, textStyle, PrimmAdditionalColors.textQuiet)
        for (variable in layout.columns) {
            val show = variable in stage.changed || variable in row.different
            Cell(if (show) row.actualValues[variable] ?: "" else "", ValueWidth, textStyle, colour(variable), bold = bold(variable))
        }
        for (chain in layout.plan.chains) {
            val key = AlignedRow.decision(chain.head)
            Cell(TraceLabels.decisionText(chain, row.actualDecisions[chain.head], layout.codeLines), decisionWidth(chain.isSingle), textStyle, colour(key), bold = bold(key))
        }
        if (layout.hasWhile) {
            Cell(TraceLabels.again(row.actualAgain), AgainWidth, textStyle, colour(AlignedRow.AGAIN), bold = bold(AlignedRow.AGAIN))
        }
        Cell(row.actualOutput.trimEnd('\n').replace("\n", "↵"), OutputWidth, textStyle, colour(AlignedRow.OUTPUT), bold = bold(AlignedRow.OUTPUT))
    }
}

@Composable
private fun PredictedRow(row: AlignedRow, columns: List<String>, textStyle: TextStyle, mark: String, markColour: Color, struck: Boolean = false) {
    val predicted = row.predicted ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(CellGap), verticalAlignment = Alignment.CenterVertically) {
        Cell(mark, MarkWidth, textStyle, markColour, bold = true)
        Cell(predicted.line?.toString() ?: "?",
            LineWidth, textStyle, PrimmAdditionalColors.textQuiet, struck = struck)
        for (variable in columns) {
            Cell(predicted.values[variable] ?: "",
                ValueWidth, textStyle, outlined = variable in row.different, struck = struck)
        }
        Cell(conditionText(predicted.condition),
            ConditionWidth, textStyle, outlined = AlignedRow.CONDITION in row.different, struck = struck)
        Cell(predicted.output,
            OutputWidth, textStyle, outlined = AlignedRow.OUTPUT in row.different, struck = struck)
    }
}

@Composable
private fun ActualRow(row: AlignedRow, columns: List<String>, textStyle: TextStyle, mark: String, markColour: Color) {
    val actual = row.actual ?: return
    val emphasise = row.match == RowMatch.Different
    Row(horizontalArrangement = Arrangement.spacedBy(CellGap), verticalAlignment = Alignment.CenterVertically) {
        Cell(mark, MarkWidth, textStyle, markColour, bold = true)
        val where = if (actual.scope.isEmpty()) "${actual.line}" else "${actual.line} (${actual.scope})"
        Cell(where, LineWidth, textStyle, PrimmAdditionalColors.textQuiet)
        for (variable in columns) {
            val differs = emphasise && variable in row.different
            Cell(row.actualValues[variable] ?: "",
                ValueWidth, textStyle, if (differs) PrimmAdditionalColors.differs else PrimmAdditionalColors.consoleOutput, bold = differs)
        }
        val conditionDiffers = emphasise && AlignedRow.CONDITION in row.different
        Cell(conditionText(row.actualCondition),
            ConditionWidth, textStyle, if (conditionDiffers) PrimmAdditionalColors.differs else PrimmAdditionalColors.consoleOutput, bold = conditionDiffers)
        val outputDiffers = emphasise && AlignedRow.OUTPUT in row.different
        Cell(actual.output.replace("\n", "↵"),
            OutputWidth, textStyle, if (outputDiffers) PrimmAdditionalColors.differs else PrimmAdditionalColors.consoleOutput, bold = outputDiffers)
    }
}

private fun conditionText(condition: Boolean?): String = when (condition) {
    true -> "True"
    false -> "False"
    null -> ""
}

@Composable
private fun Cell(
    text: String,
    width: Dp,
    textStyle: TextStyle,
    colour: Color = PrimmAdditionalColors.consoleOutput,
    outlined: Boolean = false,
    bold: Boolean = false,
    struck: Boolean = false,
) {
    val outline = if (outlined) Modifier.Companion.border(1.5.dp, PrimmAdditionalColors.differs, RoundedCornerShape(3.dp)) else Modifier
    Text(
        text,
        style = if (struck) textStyle.copy(textDecoration = TextDecoration.LineThrough) else textStyle,
        color = if (struck) PrimmAdditionalColors.textQuiet else colour,
        fontWeight = if (bold) FontWeight.Bold else null,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).then(outline).padding(horizontal = 3.dp, vertical = 3.dp),
    )
}

@Composable
private fun Mark(symbol: String, colour: Color) {
    Text(symbol, color = colour, fontWeight = FontWeight.Bold)
}

@Composable
private fun MarkedText(symbol: String, colour: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Mark(symbol, colour)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Legend(symbol: String, label: String, colour: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(symbol, color = colour, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        Text(label, color = PrimmAdditionalColors.textQuiet, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SideBySide(leftLabel: String, left: String, rightLabel: String, right: String, rightStyle: TextStyle?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(leftLabel, style = MaterialTheme.typography.labelMedium, color = PrimmAdditionalColors.textQuiet)
            Text(left, style = MaterialTheme.typography.bodyMedium)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(rightLabel, style = MaterialTheme.typography.labelMedium, color = PrimmAdditionalColors.textQuiet)
            Text(
                right.trimEnd(),
                style = rightStyle ?: MaterialTheme.typography.bodyMedium,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MarkChips(selected: SelfMark?, onChoose: (SelfMark) -> Unit) {
    PrimmSegmentedButton(
        segments = SelfMark.entries.map { Segment(it, it.title, it.icon) },
        selected = selected,
        onSelect = onChoose,
    )
}

@Composable
fun paneFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = PrimmAdditionalColors.pane,
    unfocusedContainerColor = PrimmAdditionalColors.pane,
)
