package uk.codecymru.primm.model

import org.jetbrains.compose.resources.StringResource
import primm.shared.generated.resources.*

data class PlannedInput(val id: Int, val inputLine: Int?, val value: String = "")

data class InputPlan(
    val items: List<PlannedInput> = emptyList(),
    val nextId: Int = 1,
) {
    val values: List<String> get() = items.map { it.value }.filter { it.isNotEmpty() }

    fun add(inputLine: Int?): InputPlan {
        val item = PlannedInput(nextId, inputLine)
        val lastForLine = items.indexOfLast { it.inputLine == inputLine }
        val list = items.toMutableList()
        if (lastForLine < 0) list.add(item) else list.add(lastForLine + 1, item)
        return copy(items = list, nextId = nextId + 1)
    }

    fun setValue(id: Int, value: String): InputPlan = update(id) { it.copy(value = value.replace("\n", "")) }

    fun setLine(id: Int, inputLine: Int?): InputPlan = update(id) { it.copy(inputLine = inputLine) }

    fun remove(id: Int): InputPlan = copy(items = items.filterNot { it.id == id })

    fun move(id: Int, by: Int): InputPlan = copy(items = items.moved(id, by) { it.id })

    private inline fun update(id: Int, transform: (PlannedInput) -> PlannedInput): InputPlan =
        copy(items = items.map { if (it.id == id) transform(it) else it })

    companion object {
        fun forCalls(calls: List<InputCall>): InputPlan =
            calls.fold(InputPlan()) { plan, call -> plan.add(call.line) }
    }
}

enum class ExpectedErrorKind(private val titleRes: StringResource) {
    None(Res.string.EXPECTED_ERROR_NONE),
    Syntax(Res.string.EXPECTED_ERROR_SYNTAX),
    Runtime(Res.string.EXPECTED_ERROR_RUNTIME),
    Logic(Res.string.EXPECTED_ERROR_LOGIC);

    val title: String get() = tr(titleRes)
}

data class ExpectedError(
    val kind: ExpectedErrorKind? = null,
    val line: Int? = null,
    val reason: String = "",
)

data class Prediction(
    val summary: String = "",
    val inputs: InputPlan = InputPlan(),
    val table: TraceTable = TraceTable(),
    val loopEnds: Map<Int, String> = emptyMap(),
    val expectedError: ExpectedError = ExpectedError(),
    val files: Map<String, String> = emptyMap(),
    val otherTable: TraceTable? = null,
) {
    val isStarted: Boolean
        get() = summary.isNotBlank() || inputs.items.any { it.value.isNotEmpty() } || files.isNotEmpty() ||
            table.isStarted || loopEnds.values.any { it.isNotBlank() } || expectedError != ExpectedError()

    fun withAnalysis(analysis: Analysis): Prediction = copy(
        inputs = if (inputs.items.isEmpty()) InputPlan.forCalls(analysis.inputs) else inputs,
        table = TraceTable(variables = analysis.variables, excluded = table.excluded).withVariables(table.variables)
            .copy(hiddenVariables = table.hiddenVariables, rows = table.rows, nextId = table.nextId, mode = table.mode, given = table.given),
    )

    fun withAnalysis(analysis: Analysis, plan: TracePlan, code: String): Prediction = copy(
        inputs = if (inputs.items.isEmpty()) InputPlan.forCalls(analysis.inputs) else inputs,
        table = if (!table.isStarted) {
            TraceTable.forPlan(plan, code)
        } else {
            TraceTable(variables = plan.columns, excluded = table.excluded + plan.excluded)
                .withVariables(table.variables)
                .copy(
                    hiddenVariables = table.hiddenVariables,
                    rows = table.rows,
                    nextId = table.nextId,
                    mode = table.mode,
                    given = table.given,
                )
        },
    )

    fun withTraceStyle(style: TraceStyle, plan: TracePlan, code: String): Prediction {
        val mode = if (style == TraceStyle.Simplified) TraceMode.Passes else TraceMode.Lines
        if (table.mode == mode || plan.mode != TraceMode.Passes) return this
        val next = otherTable?.takeIf { it.mode == mode }
            ?: TraceTable.forPlan(plan.copy(mode = mode), code).copy(hiddenVariables = table.hiddenVariables)
                .withVariables(table.variables)
        return copy(table = next, otherTable = table)
    }

    companion object {
        fun initial(code: String): Prediction =
            Prediction(table = TraceTable().addRow(firstCodeLine(code), countLines(code)))
    }
}
