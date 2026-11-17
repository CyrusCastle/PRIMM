package uk.codecymru.primm.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.codecymru.primm.model.Analysis
import uk.codecymru.primm.model.LoopKind
import uk.codecymru.primm.model.PassTrace
import uk.codecymru.primm.model.RunInsights
import uk.codecymru.primm.model.RunRecord
import uk.codecymru.primm.model.StageKind
import uk.codecymru.primm.model.TraceLabels
import uk.codecymru.primm.model.TraceMode
import uk.codecymru.primm.model.TraceStyle
import uk.codecymru.primm.model.TracePlan
import uk.codecymru.primm.model.TraceTable
import uk.codecymru.primm.model.plural
import uk.codecymru.primm.view.PrimmAdditionalColors
import primm.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal val LineWidth = 60.dp
internal val ValueWidth = 96.dp
internal val ConditionWidth = 72.dp
internal val OutputWidth = 160.dp
internal val CellGap = 6.dp
internal val StageWidth = 90.dp
internal val BranchWidth = 160.dp
internal val AgainWidth = 84.dp

internal fun decisionWidth(single: Boolean): Dp = if (single) 130.dp else BranchWidth

class TraceTableActions(
    val addRow: () -> Unit,
    val setLine: (rowId: Int, line: Int?) -> Unit,
    val setValue: (rowId: Int, variable: String, value: String) -> Unit,
    val setCondition: (rowId: Int, condition: Boolean?) -> Unit,
    val setOutput: (rowId: Int, output: String) -> Unit,
    val remove: (rowId: Int) -> Unit,
    val move: (rowId: Int, by: Int) -> Unit,
    val addVariable: (name: String) -> Unit,
    val toggleVariable: (name: String) -> Unit,
    val addPass: (loopLine: Int) -> Unit = {},
    val setDecision: (rowId: Int, head: Int, value: String?) -> Unit = { _, _, _ -> },
    val setAgain: (rowId: Int, again: Boolean?) -> Unit = { _, _ -> },
)

@Composable
fun TraceTableEditor(
    table: TraceTable,
    plan: TracePlan,
    codeLines: List<String>,
    conditionLines: Set<Int>,
    textStyle: TextStyle,
    actions: TraceTableActions,
) {
    if (table.byPass) {
        PassTableEditor(table, plan, codeLines, textStyle, actions)
        return
    }
    val columns = table.visibleVariables
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            Modifier.horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(CellGap), verticalAlignment = Alignment.CenterVertically) {
                HeaderCell(stringResource(Res.string.TRACE_LINE), LineWidth, textStyle)
                for (variable in columns) {
                    Row(Modifier.width(ValueWidth), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            variable,
                            style = textStyle,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        SmallButton(
                            "×",
                            onClick = { actions.toggleVariable(variable) })
                    }
                }
                HeaderCell(stringResource(Res.string.TRACE_CONDITION), ConditionWidth, textStyle)
                HeaderCell(stringResource(Res.string.TRACE_OUTPUT), OutputWidth, textStyle)
            }
            for ((index, row) in table.rows.withIndex()) {
                Row(horizontalArrangement = Arrangement.spacedBy(CellGap), verticalAlignment = Alignment.CenterVertically) {
                    LineChooser(
                        line = row.line,
                        codeLines = codeLines,
                        textStyle = textStyle,
                        onChoose = { actions.setLine(row.id, it) },
                        modifier = Modifier.width(LineWidth),
                    )
                    for (variable in columns) {
                        CellField(
                            value = row.values[variable] ?: "",
                            onValueChange = { actions.setValue(row.id, variable, it) },
                            textStyle = textStyle,
                            modifier = Modifier.width(ValueWidth),
                        )
                    }
                    ConditionToggle(
                        value = row.condition,
                        enabled = row.line != null && row.line in conditionLines,
                        textStyle = textStyle,
                        onChange = { actions.setCondition(row.id, it) },
                        modifier = Modifier.width(ConditionWidth),
                    )
                    CellField(
                        value = row.output,
                        onValueChange = { actions.setOutput(row.id, it) },
                        textStyle = textStyle,
                        modifier = Modifier.width(OutputWidth),
                        onEnter = if (index == table.rows.lastIndex) actions.addRow else null,
                    )
                    SmallButton(
                        "↑",
                        onClick = { actions.move(row.id, -1) },
                        enabled = index > 0
                    )
                    SmallButton(
                        "↓",
                        onClick = { actions.move(row.id, 1) },
                        enabled = index < table.rows.lastIndex
                    )
                    SmallButton(
                        "✕",
                        onClick = { actions.remove(row.id) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallButton(
                stringResource(Res.string.TRACE_ADD_ROW),
                onClick = actions.addRow,
                enabled = !table.isFull
            )
            AddVariable(textStyle, actions.addVariable)
            if (table.isFull) {
                Text(stringResource(Res.string.TRACE_TABLE_FULL_ROWS, TraceTable.MAX_ROWS), style = MaterialTheme.typography.bodySmall, color = PrimmAdditionalColors.textQuiet)
            }
        }
        HiddenVariables(table, actions)
    }
}

@Composable
private fun PassTableEditor(
    table: TraceTable,
    plan: TracePlan,
    codeLines: List<String>,
    textStyle: TextStyle,
    actions: TraceTableActions,
) {
    val columns = table.visibleVariables
    val chains = plan.chains
    val whileLoops = plan.loops.filter { it.kind == LoopKind.While }.map { it.line }.toSet()
    val loops = (plan.loops.map { it.line } + table.rows.mapNotNull { it.stage?.loop?.takeIf { l -> l > 0 } }).distinct().sorted()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            Modifier.horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(CellGap), verticalAlignment = Alignment.CenterVertically) {
                HeaderCell(stringResource(Res.string.TRACE_PASS_HEADER), StageWidth, textStyle)
                for (variable in columns) VariableHeader(variable, textStyle) { actions.toggleVariable(variable) }
                for (chain in chains) HeaderCell(TraceLabels.decisionHeader(chain, codeLines), decisionWidth(chain.isSingle), textStyle)
                if (whileLoops.isNotEmpty()) HeaderCell(stringResource(Res.string.TRACE_LOOP_AGAIN), AgainWidth, textStyle)
                HeaderCell(stringResource(Res.string.TRACE_OUTPUT), OutputWidth, textStyle)
            }
            for (row in table.rows) {
                val stage = row.stage ?: continue
                val isPass = stage.kind == StageKind.Pass
                Row(horizontalArrangement = Arrangement.spacedBy(CellGap), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        TraceLabels.stage(stage, table.passNumber(row), plan),
                        style = textStyle,
                        fontWeight = if (isPass) null else FontWeight.SemiBold,
                        color = PrimmAdditionalColors.textQuiet,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(StageWidth),
                    )
                    for (variable in columns) {
                        CellField(
                            value = row.values[variable] ?: "",
                            onValueChange = { actions.setValue(row.id, variable, it) },
                            textStyle = textStyle,
                            modifier = Modifier.width(ValueWidth),
                        )
                    }
                    for (chain in chains) {
                        val value = row.decisions[chain.head]
                        if (chain.isSingle) {
                            ConditionToggle(
                                value = value?.let { it == PassTrace.TRUE },
                                enabled = true,
                                textStyle = textStyle,
                                onChange = { choice -> actions.setDecision(row.id, chain.head, choice?.let { if (it) PassTrace.TRUE else PassTrace.FALSE }) },
                                modifier = Modifier.width(decisionWidth(true)),
                            )
                        } else {
                            ChoiceChooser(
                                value = value,
                                choices = TraceLabels.decisionChoices(chain, codeLines),
                                textStyle = textStyle,
                                onChoose = { actions.setDecision(row.id, chain.head, it) },
                                modifier = Modifier.width(decisionWidth(false)),
                            )
                        }
                    }
                    if (whileLoops.isNotEmpty()) {
                        ConditionToggle(
                            value = row.again,
                            enabled = isPass && stage.loop in whileLoops,
                            textStyle = textStyle,
                            onChange = { actions.setAgain(row.id, it) },
                            modifier = Modifier.width(AgainWidth),
                        )
                    }
                    CellField(
                        value = row.output,
                        onValueChange = { actions.setOutput(row.id, it) },
                        textStyle = textStyle,
                        modifier = Modifier.width(OutputWidth),
                    )
                    if (isPass) SmallButton("✕", onClick = { actions.remove(row.id) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (loop in loops) {
                SmallButton(
                    if (loops.size > 1) stringResource(Res.string.TRACE_ADD_PASS_OF_LOOP, loop) else stringResource(Res.string.TRACE_ADD_PASS),
                    onClick = { actions.addPass(loop) },
                    enabled = !table.isFull,
                )
            }
            AddVariable(textStyle, actions.addVariable)
            if (table.isFull) {
                Text(stringResource(Res.string.TRACE_TABLE_FULL_ROWS, TraceTable.MAX_ROWS), style = MaterialTheme.typography.bodySmall, color = PrimmAdditionalColors.textQuiet)
            }
        }
        HiddenVariables(table, actions)
    }
}

@Composable
private fun VariableHeader(variable: String, textStyle: TextStyle, onHide: () -> Unit) {
    Row(Modifier.width(ValueWidth), verticalAlignment = Alignment.CenterVertically) {
        Text(
            variable,
            style = textStyle,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SmallButton("×", onClick = onHide)
    }
}

@Composable
private fun HiddenVariables(table: TraceTable, actions: TraceTableActions) {
    val hidden = table.variables.filter { it in table.hiddenVariables }
    if (hidden.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.TRACE_HIDDEN), style = MaterialTheme.typography.bodySmall, color = PrimmAdditionalColors.textQuiet)
        for (variable in hidden) SmallButton(
            stringResource(Res.string.TRACE_SHOW, variable),
            onClick = { actions.toggleVariable(variable) })
    }
}

@Composable
private fun AddVariable(textStyle: TextStyle, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val add = {
        if (TraceTable.isValidName(name.trim())) {
            onAdd(name.trim())
            name = ""
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CellField(
            value = name,
            onValueChange = { name = it },
            textStyle = textStyle,
            placeholder = stringResource(Res.string.TRACE_VARIABLE_PLACEHOLDER),
            modifier = Modifier.width(110.dp),
            onEnter = add,
        )
        SmallButton(
            stringResource(Res.string.TRACE_ADD_VARIABLE),
            onClick = add,
            enabled = TraceTable.isValidName(name.trim())
        )
    }
}

@Composable
fun RunTraceTable(
    record: RunRecord,
    analysis: Analysis?,
    plan: TracePlan,
    columns: List<String>,
    codeLines: List<String>,
    paused: Boolean,
    textStyle: TextStyle,
    style: TraceStyle? = null,
) {
    when (style) {
        TraceStyle.Verbose -> {
            EveryLineTable(record, analysis, paused, textStyle, null, null)
            return
        }
        TraceStyle.Simplified -> {
            if (record.stepping) EveryLineTable(record, analysis, paused, textStyle, null, null)
            else RunPassTable(record, analysis, plan, columns, codeLines, textStyle)
            return
        }
        null -> Unit
    }
    var everyLine by remember(record.stepping) { mutableStateOf(record.stepping) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.clickable { everyLine = !everyLine }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = everyLine, onCheckedChange = { everyLine = it })
            Text(stringResource(Res.string.TRACE_SHOW_EVERY_LINE), style = MaterialTheme.typography.bodySmall)
        }
        when {
            everyLine -> EveryLineTable(record, analysis, paused, textStyle, null, null)
            plan.mode == TraceMode.Passes -> RunPassTable(record, analysis, plan, columns, codeLines, textStyle)
            else -> {
                val shown = RunInsights.notableRows(record, analysis, columns)
                val leftOut = record.rows.count { it.done } - record.rows.indices.count { it in shown && record.rows[it].done }
                if (leftOut > 0) {
                    Text(
                        plural(leftOut, Res.string.TRACE_LINES_LEFT_OUT_ONE, Res.string.TRACE_LINES_LEFT_OUT_OTHER),
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimmAdditionalColors.textQuiet,
                    )
                }
                EveryLineTable(record, analysis, paused, textStyle, shown, columns)
            }
        }
    }
}

@Composable
private fun RunPassTable(
    record: RunRecord,
    analysis: Analysis?,
    plan: TracePlan,
    columns: List<String>,
    codeLines: List<String>,
    textStyle: TextStyle,
) {
    val stages = PassTrace.stages(record, analysis, plan)
    val whileLoops = plan.loops.filter { it.kind == LoopKind.While }.map { it.line }.toSet()
    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
            HeaderCell(stringResource(Res.string.TRACE_PASS_HEADER), StageWidth, textStyle)
            for (variable in columns) HeaderCell(variable, ValueWidth, textStyle)
            for (chain in plan.chains) HeaderCell(TraceLabels.decisionHeader(chain, codeLines), decisionWidth(chain.isSingle), textStyle)
            if (whileLoops.isNotEmpty()) HeaderCell(stringResource(Res.string.TRACE_LOOP_AGAIN), AgainWidth, textStyle)
            HeaderCell(stringResource(Res.string.TRACE_OUTPUT), OutputWidth, textStyle)
        }
        HorizontalDivider(color = PrimmAdditionalColors.border)
        for (stage in stages) {
            Row(horizontalArrangement = Arrangement.spacedBy(CellGap), verticalAlignment = Alignment.CenterVertically) {
                ValueCell(TraceLabels.stage(stage.stage, stage.pass, plan), StageWidth, textStyle, PrimmAdditionalColors.textQuiet)
                for (variable in columns) {
                    val changed = variable in stage.changed
                    ValueCell(
                        if (changed) stage.values[variable] ?: "" else "",
                        ValueWidth,
                        textStyle,
                    )
                }
                for (chain in plan.chains) {
                    val value = stage.decisions[chain.head]
                    ValueCell(
                        TraceLabels.decisionText(chain, value, codeLines),
                        decisionWidth(chain.isSingle),
                        textStyle,
                        if (value == PassTrace.FALSE) PrimmAdditionalColors.notRun else PrimmAdditionalColors.matches,
                    )
                }
                if (whileLoops.isNotEmpty()) {
                    ValueCell(
                        TraceLabels.again(stage.again),
                        AgainWidth,
                        textStyle,
                        if (stage.again == true) PrimmAdditionalColors.matches else PrimmAdditionalColors.notRun,
                    )
                }
                ValueCell(stage.output.trimEnd('\n').replace("\n", "↵"), OutputWidth, textStyle)
            }
        }
    }
}

@Composable
private fun EveryLineTable(
    record: RunRecord,
    analysis: Analysis?,
    paused: Boolean,
    textStyle: TextStyle,
    only: Set<Int>?,
    shownColumns: List<String>?,
) {
    val columns = shownColumns ?: RunInsights.columns(record, analysis)
    val conditions = RunInsights.conditions(record, analysis)
    val conditionLines = analysis?.conditionLines ?: emptySet()
    val passes = RunInsights.passes(record, analysis)
    val passStarts = passes.associateBy { it.first }
    var folded by remember(record.rows.firstOrNull()) { mutableStateOf(setOf<Int>()) }
    val hiddenRows = passes.filter { it.first in folded }.flatMap { ((it.first + 1)..it.last).toList() }.toSet()
    val nextRow = if (paused) record.rows.indexOfLast { !it.done } else -1

    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
            HeaderCell(stringResource(Res.string.TRACE_LINE), LineWidth, textStyle)
            for (variable in columns) HeaderCell(variable, ValueWidth, textStyle)
            HeaderCell(stringResource(Res.string.TRACE_CONDITION), ConditionWidth, textStyle)
            HeaderCell(stringResource(Res.string.TRACE_OUTPUT), OutputWidth, textStyle)
        }
        HorizontalDivider(color = PrimmAdditionalColors.border)
        for ((i, row) in record.rows.withIndex()) {
            passStarts[i]?.let { pass ->
                val isFolded = i in folded
                Text(
                    (if (isFolded) "▸ " else "▾ ") + stringResource(Res.string.TRACE_PASS_LOOP_LINE, pass.pass, pass.loopLine),
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimmAdditionalColors.textQuiet,
                    modifier = Modifier
                        .clickable { folded = if (isFolded) folded - i else folded + i }
                        .padding(top = 4.dp, bottom = 2.dp),
                )
            }
            if (i !in hiddenRows && (only == null || i in only || i == nextRow)) {
                val background = if (i == nextRow) PrimmAdditionalColors.nextLine else Color.Transparent
                Row(
                    Modifier.background(background),
                    horizontalArrangement = Arrangement.spacedBy(CellGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val where = if (row.scope.isEmpty()) "${row.line}" else "${row.line} (${row.scope})"
                    ValueCell(where, LineWidth, textStyle, PrimmAdditionalColors.textQuiet)
                    for (variable in columns) ValueCell(row.changes[variable] ?: "", ValueWidth, textStyle)
                    val condition = if (row.line in conditionLines) conditions[i] else null
                    ValueCell(
                        when (condition) {
                            true -> "True"
                            false -> "False"
                            null -> ""
                        },
                        ConditionWidth,
                        textStyle,
                        if (condition == true) PrimmAdditionalColors.matches else PrimmAdditionalColors.notRun,
                    )
                    ValueCell(row.output.replace("\n", "↵"), OutputWidth, textStyle)
                }
            }
        }
    }
}

@Composable
internal fun HeaderCell(text: String, width: Dp, textStyle: TextStyle) {
    Text(
        text,
        style = textStyle,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun ValueCell(text: String, width: Dp, textStyle: TextStyle, colour: Color = PrimmAdditionalColors.consoleOutput) {
    Text(
        text,
        style = textStyle,
        color = colour,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(vertical = 3.dp),
    )
}
