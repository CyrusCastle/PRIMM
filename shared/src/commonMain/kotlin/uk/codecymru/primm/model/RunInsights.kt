package uk.codecymru.primm.model

import primm.shared.generated.resources.*

data class PassGroup(val loopLine: Int, val pass: Int, val first: Int, val last: Int)

data class LoopSummary(
    val loop: LoopInfo,
    val iterations: Int,
    val timesStarted: Int,
    val finished: Boolean,
    val exitValues: Map<String, String>,
) {
    val reached: Boolean get() = timesStarted > 0

    val text: String
        get() {
            val where = tr(Res.string.LOOP_ON_LINE, loop.line)
            if (!reached) return tr(Res.string.LOOP_NEVER_RAN, where)
            val times = plural(iterations, Res.string.COUNT_TIME_ONE, Res.string.COUNT_TIME_OTHER)
            val total = if (timesStarted > 1) tr(Res.string.LOOP_TIMES_IN_TOTAL, times, timesStarted) else times
            if (!finished) return tr(Res.string.LOOP_DIDNT_FINISH, where, total)
            val values = exitValues.entries.joinToString(", ") { "${it.key} = ${it.value}" }
            return if (values.isEmpty()) tr(Res.string.LOOP_RAN, where, total) else tr(Res.string.LOOP_RAN_AND_STOPPED, where, total, values)
        }
}

object RunInsights {
    fun conditions(record: RunRecord, analysis: Analysis?): Map<Int, Boolean> {
        if (analysis == null || !analysis.parsed) return emptyMap()
        val bodyLines = analysis.conditions.associate { it.line to it.bodyLine } +
            analysis.loops.associate { it.line to it.bodyLine }
        val rows = record.rows
        val next = nextInFrame(rows)
        val runEndedNormally = record.outcome == RunOutcome.Completed && !record.traceTruncated
        val result = HashMap<Int, Boolean>()
        for (i in rows.indices) {
            val body = bodyLines[rows[i].line] ?: continue
            if (body == rows[i].line) continue // body on the same line: can't tell
            val value = when {
                next[i] >= 0 -> rows[next[i]].line == body
                runEndedNormally -> false
                else -> null
            }
            if (value != null) result[i] = value
        }
        return result
    }

    fun passes(record: RunRecord, analysis: Analysis?): List<PassGroup> {
        if (analysis == null || !analysis.parsed) return emptyList()
        val conditions = conditions(record, analysis)
        val outermost = analysis.loops.filter { loop ->
            analysis.loops.none { other ->
                other != loop && other.scope == loop.scope && other.line < loop.line && other.contains(loop.line)
            }
        }
        val rows = record.rows
        val previous = previousInFrame(rows)
        val groups = ArrayList<PassGroup>()
        val open = HashMap<Int, PassGroup>() // frame -> pass being collected
        val passCounts = HashMap<Pair<Int, Int>, Int>() // (frame, loop line) -> passes so far

        fun close(frame: Int, lastIndex: Int) {
            open.remove(frame)?.let { groups.add(it.copy(last = lastIndex)) }
        }

        for (i in rows.indices) {
            val row = rows[i]
            val loop = outermost.firstOrNull { it.line == row.line && it.scope == row.scope }
            val current = open[row.frame]
            if (loop != null) {
                close(row.frame, i - 1)
                val p = previous[i]
                val key = row.frame to loop.line
                if (p < 0 || !loop.contains(rows[p].line)) passCounts[key] = 0
                if (conditions[i] == true) {
                    val pass = (passCounts[key] ?: 0) + 1
                    passCounts[key] = pass
                    open[row.frame] = PassGroup(loop.line, pass, i, i)
                }
            } else if (current != null) {
                val currentLoop = outermost.firstOrNull { it.line == current.loopLine && it.scope == row.scope }
                if (currentLoop != null && !currentLoop.contains(row.line)) close(row.frame, i - 1) // left with break
            }
        }
        for (frame in open.keys.toList()) close(frame, rows.lastIndex)
        return groups.sortedBy { it.first }
    }

    fun loopSummaries(record: RunRecord, analysis: Analysis?): List<LoopSummary> {
        if (analysis == null || !analysis.parsed) return emptyList()
        val conditions = conditions(record, analysis)
        val rows = record.rows
        val previous = previousInFrame(rows)
        return analysis.loops.map { loop ->
            val headers = rows.indices.filter { rows[it].line == loop.line && rows[it].scope == loop.scope }
            if (headers.isEmpty()) return@map LoopSummary(loop, 0, 0, false, emptyMap())
            val iterations = headers.count { conditions[it] == true }
            val started = headers.count { val p = previous[it]; p < 0 || !loop.contains(rows[p].line) }
            val last = headers.last()
            val exitIndex: Int? = when (conditions[last]) {
                false -> last
                true -> ((last + 1) until rows.size)
                    .firstOrNull { rows[it].frame == rows[last].frame && !loop.contains(rows[it].line) }
                    ?.let { it - 1 }
                null -> null
            }
            val values = if (exitIndex == null) {
                emptyMap()
            } else {
                val state = stateAfter(record, exitIndex)
                loop.names.mapNotNull { name -> state[name]?.let { name to it } }.toMap()
            }
            LoopSummary(loop, iterations, started, exitIndex != null, values)
        }
    }

    fun stateAfter(record: RunRecord, index: Int): Map<String, String> {
        val state = LinkedHashMap<String, String>()
        for (i in 0..index.coerceAtMost(record.rows.lastIndex)) {
            val row = record.rows[i]
            if (row.done) state.putAll(row.changes)
        }
        return state
    }

    fun columns(record: RunRecord, analysis: Analysis?): List<String> {
        val columns = LinkedHashSet(analysis?.variables ?: emptyList())
        for (row in record.rows) columns.addAll(row.changes.keys)
        val files = analysis?.fileVariables?.toSet() ?: emptySet()
        return columns.filter { it !in files }
    }

    fun notableRows(record: RunRecord, analysis: Analysis?, columns: List<String>): Set<Int> {
        val conditionLines = analysis?.conditionLines ?: emptySet()
        val inputLines = analysis?.inputs?.map { it.line }?.toSet() ?: emptySet()
        return record.rows.indices.filter { i ->
            val row = record.rows[i]
            row.changes.keys.any { it in columns } || row.line in conditionLines || (row.output.isNotEmpty() && row.line !in inputLines)
        }.toSet()
    }

    fun ranLines(record: RunRecord): Set<Int> = record.rows.filter { it.done }.map { it.line }.toSet()

    fun nextLine(record: RunRecord): Int? = record.rows.lastOrNull { !it.done }?.line

    private fun nextInFrame(rows: List<RunTraceRow>): IntArray {
        val next = IntArray(rows.size) { -1 }
        val seen = HashMap<Int, Int>()
        for (i in rows.indices.reversed()) {
            next[i] = seen[rows[i].frame] ?: -1
            seen[rows[i].frame] = i
        }
        return next
    }

    private fun previousInFrame(rows: List<RunTraceRow>): IntArray {
        val previous = IntArray(rows.size) { -1 }
        val seen = HashMap<Int, Int>()
        for (i in rows.indices) {
            previous[i] = seen[rows[i].frame] ?: -1
            seen[rows[i].frame] = i
        }
        return previous
    }
}
