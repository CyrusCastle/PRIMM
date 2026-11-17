package uk.codecymru.primm.model

data class RunTraceRow(
    val index: Int,
    val line: Int,
    val frame: Int,
    val scope: String,
    val changes: Map<String, String> = emptyMap(),
    val output: String = "",
    val done: Boolean = false,
)

data class UsedInput(val line: Int?, val value: String, val automatic: Boolean)

data class RunRecord(
    val rows: List<RunTraceRow> = emptyList(),
    val inputs: List<UsedInput> = emptyList(),
    val outcome: RunOutcome? = null,
    val traceTruncated: Boolean = false,
    val stepping: Boolean = false,
    val filesBefore: List<TextFile>? = null,
    val filesAfter: List<TextFile>? = null,
) {
    val isFinished: Boolean get() = outcome != null

    fun started(index: Int, line: Int, frame: Int, scope: String): RunRecord {
        val row = RunTraceRow(index, line, frame, scope)
        return when {
            index == rows.size -> copy(rows = rows + row)
            index < rows.size -> copy(rows = rows.mapIndexed { i, r -> if (i == index) row else r })
            else -> this
        }
    }

    fun done(index: Int, changes: Map<String, String>, output: String): RunRecord {
        if (index !in rows.indices) return this
        return copy(
            rows = rows.mapIndexed { i, r -> if (i == index) r.copy(changes = changes, output = output, done = true) else r },
        )
    }

    fun withInput(input: UsedInput): RunRecord = copy(inputs = inputs + input)

    fun finished(outcome: RunOutcome, traceTruncated: Boolean): RunRecord =
        copy(outcome = outcome, traceTruncated = traceTruncated, stepping = false)
}
