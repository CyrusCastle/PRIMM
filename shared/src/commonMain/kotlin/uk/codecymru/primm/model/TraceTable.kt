package uk.codecymru.primm.model

enum class StageKind { Start, Pass, After }

data class TraceStage(val kind: StageKind, val loop: Int = 0) {
    companion object {
        val Start = TraceStage(StageKind.Start)

        fun pass(loop: Int) = TraceStage(StageKind.Pass, loop)

        fun after(loop: Int) = TraceStage(StageKind.After, loop)
    }
}

data class TraceRow(
    val id: Int,
    val line: Int? = null,
    val values: Map<String, String> = emptyMap(),
    val condition: Boolean? = null,
    val output: String = "",
    val stage: TraceStage? = null,
    val decisions: Map<Int, String> = emptyMap(),
    val again: Boolean? = null,
) {
    fun hasWork(given: Map<String, String> = emptyMap()): Boolean =
        values.any { (name, value) -> given[name] != value } || condition != null || output.isNotEmpty() ||
            decisions.isNotEmpty() || again != null
}

data class TraceTable(
    val variables: List<String> = emptyList(),
    val hiddenVariables: Set<String> = emptySet(),
    val rows: List<TraceRow> = emptyList(),
    val nextId: Int = 1,
    val mode: TraceMode = TraceMode.Lines,
    val excluded: Set<String> = emptySet(),
    val given: Map<String, String> = emptyMap(),
) {
    val visibleVariables: List<String> get() = variables.filter { it !in hiddenVariables }

    val filledRows: Int get() = rows.count { it.line != null || it.stage?.kind == StageKind.Pass }

    val isFull: Boolean get() = rows.size >= MAX_ROWS

    val byPass: Boolean get() = mode == TraceMode.Passes

    val isStarted: Boolean get() = rows.any { it.hasWork(if (it.stage?.kind == StageKind.Start) given else emptyMap()) }

    fun passNumber(row: TraceRow): Int {
        val stage = row.stage ?: return 0
        if (stage.kind != StageKind.Pass) return 0
        return rows.takeWhile { it.id != row.id }.count { it.stage == stage } + 1
    }

    fun addPass(loop: Int): TraceTable {
        if (!byPass || isFull) return this
        val pass = TraceStage.pass(loop)
        val lastPass = rows.indexOfLast { it.stage == pass }
        val after = rows.indexOfFirst { it.stage == TraceStage.after(loop) }
        val at = when {
            lastPass >= 0 -> lastPass + 1
            after >= 0 -> after
            else -> rows.size
        }
        val list = rows.toMutableList()
        list.add(at, TraceRow(nextId, stage = pass))
        return copy(rows = list, nextId = nextId + 1)
    }

    fun setDecision(id: Int, head: Int, value: String?): TraceTable = update(id) { row ->
        row.copy(decisions = if (value.isNullOrBlank()) row.decisions - head else row.decisions + (head to value))
    }

    fun setAgain(id: Int, again: Boolean?): TraceTable = update(id) { it.copy(again = again) }

    fun addRow(firstLine: Int, lineCount: Int): TraceTable {
        if (isFull || byPass) return this
        val previous = rows.lastOrNull()?.line
        val line = if (previous == null) firstLine else (previous + 1).coerceAtMost(lineCount.coerceAtLeast(1))
        return copy(rows = rows + TraceRow(nextId, line), nextId = nextId + 1)
    }

    fun setLine(id: Int, line: Int?): TraceTable = update(id) { it.copy(line = line) }

    fun setValue(id: Int, variable: String, value: String): TraceTable = update(id) { row ->
        row.copy(values = if (value.isBlank()) row.values - variable else row.values + (variable to value))
    }

    fun setCondition(id: Int, condition: Boolean?): TraceTable = update(id) { it.copy(condition = condition) }

    fun setOutput(id: Int, output: String): TraceTable = update(id) { it.copy(output = output) }

    fun removeRow(id: Int): TraceTable {
        if (byPass && rows.firstOrNull { it.id == id }?.stage?.kind != StageKind.Pass) return this
        return copy(rows = rows.filterNot { it.id == id })
    }

    fun moveRow(id: Int, by: Int): TraceTable = if (byPass) this else copy(rows = rows.moved(id, by) { it.id })

    fun addVariable(name: String): TraceTable {
        val clean = name.trim()
        if (!isValidName(clean) || clean in variables || clean in excluded) return this
        return copy(variables = variables + clean)
    }

    fun toggleHidden(variable: String): TraceTable = copy(
        hiddenVariables = if (variable in hiddenVariables) hiddenVariables - variable else hiddenVariables + variable,
    )

    fun withVariables(names: List<String>): TraceTable =
        copy(variables = variables + names.filter { it !in variables && it !in excluded })

    private inline fun update(id: Int, transform: (TraceRow) -> TraceRow): TraceTable =
        copy(rows = rows.map { if (it.id == id) transform(it) else it })

    companion object {
        const val MAX_ROWS = 200

        /** A fresh table in the shape [plan] describes. */
        fun forPlan(plan: TracePlan, code: String): TraceTable {
            val base = TraceTable(variables = plan.columns, hiddenVariables = plan.hidden, excluded = plan.excluded)
            if (plan.mode == TraceMode.Lines) return base.addRow(firstCodeLine(code), countLines(code))
            val stages = buildList {
                add(TraceStage.Start)
                for (loop in plan.loops) {
                    add(TraceStage.pass(loop.line))
                    add(TraceStage.after(loop.line))
                }
            }
            val rows = stages.mapIndexed { i, stage ->
                TraceRow(i + 1, values = if (stage == TraceStage.Start) plan.startValues else emptyMap(), stage = stage)
            }
            return base.copy(mode = TraceMode.Passes, rows = rows, nextId = rows.size + 1, given = plan.startValues)
        }

        private val NAME = Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)?""")

        fun isValidName(name: String): Boolean = NAME.matches(name)
    }
}

internal fun <T, K> List<T>.moved(key: K, by: Int, keyOf: (T) -> K): List<T> {
    val from = indexOfFirst { keyOf(it) == key }
    if (from < 0 || by == 0) return this
    val to = (from + by).coerceIn(0, lastIndex)
    if (to == from) return this
    val result = toMutableList()
    result.add(to, result.removeAt(from))
    return result
}

fun firstCodeLine(code: String): Int =
    code.split('\n').indexOfFirst { it.isNotBlank() }.let { if (it < 0) 1 else it + 1 }

fun countLines(code: String): Int = code.removeSuffix("\n").split('\n').size
