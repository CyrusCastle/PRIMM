package uk.codecymru.primm.model

data class ActualStage(
    val stage: TraceStage,
    val pass: Int,
    val first: Int,
    val last: Int,
    val values: Map<String, String>,
    val changed: Set<String>,
    val decisions: Map<Int, String>,
    val again: Boolean?,
    val output: String,
) {
    val isEmpty: Boolean get() = last < first

    val key: String get() = "${stage.kind.name.lowercase()}${stage.loop}.$pass"
}

object PassTrace {
    const val TRUE = "True"
    const val FALSE = "False"
    const val ELSE = "else"
    const val NONE = "none"

    fun stages(record: RunRecord, analysis: Analysis?, plan: TracePlan): List<ActualStage> {
        if (plan.mode != TraceMode.Passes || analysis == null || !analysis.parsed) return emptyList()
        val rows = record.rows
        val conditions = RunInsights.conditions(record, analysis)
        val inputLines = analysis.inputs.map { it.line }.toSet()

        val spans = ArrayList<Span>()
        var current = Span(TraceStage.Start, 0, 0)
        val passCounts = HashMap<Int, Int>()
        fun switchTo(stage: TraceStage, pass: Int, at: Int) {
            current.last = at - 1
            spans.add(current)
            current = Span(stage, pass, at)
        }

        for (i in rows.indices) {
            val row = rows[i]
            if (row.scope.isNotEmpty()) continue
            val loop = plan.loops.firstOrNull { it.contains(row.line) }
            when {
                loop == null -> {
                    val before = plan.loops.lastOrNull { it.endLine < row.line }
                    val stage = before?.let { TraceStage.after(it.line) } ?: TraceStage.Start
                    if (current.stage != stage) switchTo(stage, 0, i)
                }
                row.line == loop.line -> when (conditions[i]) {
                    true -> {
                        val pass = (passCounts[loop.line] ?: 0) + 1
                        passCounts[loop.line] = pass
                        switchTo(TraceStage.pass(loop.line), pass, i)
                    }
                    false -> switchTo(TraceStage.after(loop.line), 0, i + 1)
                    null -> Unit
                }
            }
        }
        current.last = rows.lastIndex
        spans.add(current)

        val states = runningStates(record)
        return spans.mapIndexed { index, span ->
            val before = if (span.first == 0) emptyMap() else states.getOrElse(span.first - 1) { states.lastOrNull() ?: emptyMap() }
            val after = if (span.last < span.first) before else states[span.last]
            val range = span.first..span.last
            ActualStage(
                stage = span.stage,
                pass = span.pass,
                first = span.first,
                last = span.last,
                values = after,
                changed = (after.keys + before.keys).filter { after[it] != before[it] }.toSet(),
                decisions = plan.chains.mapNotNull { chain ->
                    decision(chain, range.filter { rows[it].scope.isEmpty() && rows[it].line in chain.branches }, rows, conditions)
                        ?.let { chain.head to it }
                }.toMap(),
                again = again(span, spans.getOrNull(index + 1), plan, range, rows, conditions),
                output = range.filter { rows[it].line !in inputLines }.joinToString("") { rows[it].output },
            )
        }
    }

    private fun decision(chain: IfChain, at: List<Int>, rows: List<RunTraceRow>, conditions: Map<Int, Boolean>): String? {
        if (at.isEmpty()) return null
        if (chain.isSingle) return conditions[at.first()]?.let { if (it) TRUE else FALSE }
        for (i in at) {
            val line = rows[i].line
            when (conditions[i]) {
                true -> return line.toString()
                null -> return null
                false -> if (line == chain.branches.last()) return if (chain.hasElse) ELSE else NONE
            }
        }
        return null
    }

    private fun again(
        span: Span,
        next: Span?,
        plan: TracePlan,
        range: IntRange,
        rows: List<RunTraceRow>,
        conditions: Map<Int, Boolean>,
    ): Boolean? {
        val stage = span.stage
        if (stage.kind != StageKind.Pass || plan.loopAt(stage.loop)?.kind != LoopKind.While) return null
        if (next?.stage == stage) return true
        if (range.any { rows[it].line == stage.loop && conditions[it] == false }) return false
        return if (next?.stage == TraceStage.after(stage.loop)) false else null
    }

    private fun runningStates(record: RunRecord): List<Map<String, String>> {
        val state = LinkedHashMap<String, String>()
        return record.rows.map { row ->
            if (row.done) state.putAll(row.changes)
            LinkedHashMap(state)
        }
    }
}

private class Span(val stage: TraceStage, val pass: Int, val first: Int, var last: Int = first - 1)
