package uk.codecymru.primm.model

import primm.shared.generated.resources.*

enum class RowMatch { Match, Different, NotRun, NotPredicted }

data class AlignedRow(
    val predicted: TraceRow?,
    val actual: RunTraceRow?,
    val match: RowMatch,
    val actualCondition: Boolean? = null,
    val different: Set<String> = emptySet(),
    val actualValues: Map<String, String> = emptyMap(),
    val stage: ActualStage? = null,
    val actualDecisions: Map<Int, String> = emptyMap(),
    val actualAgain: Boolean? = null,
) {
    val line: Int? get() = actual?.line ?: predicted?.line

    val actualOutput: String get() = actual?.output ?: stage?.output ?: ""

    internal val actualKey: String? get() = actual?.index?.toString() ?: stage?.key

    companion object {
        const val CONDITION = "#condition"
        const val OUTPUT = "#output"
        const val AGAIN = "#again"

        fun decision(head: Int) = "#decision:$head"
    }
}

data class InputComparison(val position: Int, val predicted: PlannedInput?, val actual: UsedInput?) {
    val matches: Boolean
        get() = predicted != null && actual != null && predicted.value.trim() == actual.value.trim()
}

enum class ErrorVerdict {
    Match,
    Different,
    SelfMark,
    NotComparable,
}

data class ErrorComparison(
    val predicted: ExpectedError,
    val actualKind: ExpectedErrorKind?,
    val actualLine: Int?,
    val actualText: String,
    val verdict: ErrorVerdict,
)

enum class MismatchKind { Row, NotRun, NotPredicted, Input, File, Error }

data class Mismatch(
    val key: String,
    val kind: MismatchKind,
    val rows: IntRange = IntRange.EMPTY,
    val inputPosition: Int = -1,
    val fileName: String? = null,
)

data class LoopCheck(val loop: LoopInfo, val predicted: String, val actual: String)

data class Comparison(
    val columns: List<String>,
    val rows: List<AlignedRow>,
    val inputs: List<InputComparison>,
    val files: List<FileComparison>,
    val error: ErrorComparison,
    val mismatches: List<Mismatch>,
    val loops: List<LoopCheck>,
    val output: String,
    val outcome: RunOutcome,
    val traceTruncated: Boolean,
    val mode: TraceMode = TraceMode.Lines,
) {
    val byPass: Boolean get() = mode == TraceMode.Passes

    val differingRows: Int get() = rows.count { it.match != RowMatch.Match }

    fun mismatchEndingAt(index: Int): Mismatch? = mismatches.firstOrNull { it.rows.last == index && !it.rows.isEmpty() }

    fun mismatchFor(index: Int): Mismatch? = mismatches.firstOrNull { index in it.rows }
}

object Compare {
    private val SYNTAX_ERRORS = setOf("SyntaxError", "IndentationError", "TabError")
    private val SPACE_AROUND_PUNCTUATION = Regex("""\s*([,:\[\](){}])\s*""")

    fun of(state: AppState): Comparison? {
        val run = state.run ?: return null
        val outcome = run.outcome ?: return null
        val prediction = state.prediction
        val analysis = state.analysis
        val columns = prediction.table.visibleVariables
        val plan = state.tracePlan
        val byPass = prediction.table.byPass && plan.mode == TraceMode.Passes
        val rows = if (byPass) passRows(prediction.table, run, analysis, plan, columns) else lineRows(prediction.table, run, analysis, columns)
        val inputs = compareInputs(prediction.inputs, run.inputs)
        val files = compareFiles(prediction.files, run)
        val error = compareError(prediction.expectedError, outcome)
        return Comparison(
            columns = columns,
            rows = rows,
            inputs = inputs,
            files = files,
            error = error,
            mismatches = mismatches(rows, inputs, files, error),
            loops = if (byPass) emptyList() else loopChecks(prediction, run, analysis),
            output = run.rows.joinToString("") { it.output },
            outcome = outcome,
            traceTruncated = run.traceTruncated,
            mode = if (byPass) TraceMode.Passes else TraceMode.Lines,
        )
    }

    private fun lineRows(table: TraceTable, run: RunRecord, analysis: Analysis?, columns: List<String>): List<AlignedRow> {
        val conditions = RunInsights.conditions(run, analysis)
        val conditionLines = analysis?.conditionLines ?: emptySet()
        val inputLines = analysis?.inputs?.map { it.line }?.toSet() ?: emptySet()
        val notable = RunInsights.notableRows(run, analysis, columns)
        return align(table.rows, run.rows).mapNotNull { (predicted, actual) ->
            when {
                predicted == null -> if (actual!!.index in notable) AlignedRow(null, actual, RowMatch.NotPredicted, actualValues = actual.changes) else null
                actual == null -> AlignedRow(predicted, null, RowMatch.NotRun)
                else -> compareRow(predicted, actual, run, columns, conditions[actual.index]
                    .takeIf { actual.line in conditionLines }, actual.line in inputLines)
            }
        }
    }

    private fun passRows(table: TraceTable, run: RunRecord, analysis: Analysis?, plan: TracePlan, columns: List<String>): List<AlignedRow> {
        val stages = PassTrace.stages(run, analysis, plan)
        val loops = (plan.loops.map { it.line } + table.rows.mapNotNull { it.stage?.loop?.takeIf { l -> l > 0 } }).distinct().sorted()
        val result = ArrayList<AlignedRow>()
        fun pair(predicted: TraceRow?, actual: ActualStage?) {
            when {
                predicted == null && actual == null -> Unit
                predicted == null -> result.add(
                    AlignedRow(
                        null, null, RowMatch.NotPredicted,
                        actualValues = actual!!.values.filterKeys { it in columns },
                        stage = actual, actualDecisions = actual.decisions, actualAgain = actual.again,
                    ),
                )
                actual == null -> result.add(AlignedRow(predicted, null, RowMatch.NotRun))
                else -> result.add(compareStage(predicted, actual, plan, columns))
            }
        }
        pair(table.rows.firstOrNull { it.stage == TraceStage.Start }, stages.firstOrNull { it.stage == TraceStage.Start })
        for (loop in loops) {
            val predicted = table.rows.filter { it.stage == TraceStage.pass(loop) }
            val actual = stages.filter { it.stage == TraceStage.pass(loop) }
            for (i in 0 until maxOf(predicted.size, actual.size)) pair(predicted.getOrNull(i), actual.getOrNull(i))
            pair(table.rows.firstOrNull { it.stage == TraceStage.after(loop) }, stages.firstOrNull { it.stage == TraceStage.after(loop) })
        }
        return result
    }

    private fun compareStage(predicted: TraceRow, actual: ActualStage, plan: TracePlan, columns: List<String>): AlignedRow {
        val different = LinkedHashSet<String>()
        val shown = LinkedHashMap<String, String>()
        for (variable in columns) {
            val guess = predicted.values[variable]?.takeIf { it.isNotBlank() }
            val value = actual.values[variable]
            if (value != null) shown[variable] = value
            when {
                guess == null -> if (variable in actual.changed) different.add(variable)
                value == null || !sameValue(guess, value) -> different.add(variable)
            }
        }
        for (chain in plan.chains) {
            if (predicted.decisions[chain.head] != actual.decisions[chain.head]) different.add(AlignedRow.decision(chain.head))
        }
        if (actual.again != null && predicted.again != actual.again) different.add(AlignedRow.AGAIN)
        if (normaliseOutput(predicted.output) != normaliseOutput(actual.output)) different.add(AlignedRow.OUTPUT)
        return AlignedRow(
            predicted = predicted,
            actual = null,
            match = if (different.isEmpty()) RowMatch.Match else RowMatch.Different,
            different = different,
            actualValues = shown,
            stage = actual,
            actualDecisions = actual.decisions,
            actualAgain = actual.again,
        )
    }

    fun align(predicted: List<TraceRow>, actual: List<RunTraceRow>): List<Pair<TraceRow?, RunTraceRow?>> {
        val n = predicted.size
        val m = actual.size
        // lcs[i][j]: longest common subsequence of predicted[i..] and actual[j..].
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lcs[i][j] = if (sameLine(predicted[i], actual[j])) lcs[i + 1][j + 1] + 1
                else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        val result = ArrayList<Pair<TraceRow?, RunTraceRow?>>(maxOf(n, m))
        var i = 0
        var j = 0
        while (i < n || j < m) {
            when {
                i == n -> result.add(null to actual[j++])
                j == m -> result.add(predicted[i++] to null)
                sameLine(predicted[i], actual[j]) && lcs[i][j] == lcs[i + 1][j + 1] + 1 ->
                    result.add(predicted[i++] to actual[j++])
                lcs[i + 1][j] >= lcs[i][j + 1] -> result.add(predicted[i++] to null)
                else -> result.add(null to actual[j++])
            }
        }
        return result
    }

    fun sameValue(predicted: String, actual: String): Boolean = normalise(predicted) == normalise(actual)

    private fun normalise(value: String): String {
        var text = value.trim()
        if (text.length >= 2 && (text[0] == '\'' || text[0] == '"') && text.last() == text[0]) {
            text = text.substring(1, text.length - 1)
        }
        return SPACE_AROUND_PUNCTUATION.replace(text, "$1")
    }

    private fun normaliseOutput(text: String): List<String> =
        text.replace("↵", "\n").replace("\r", "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    private fun sameLine(predicted: TraceRow, actual: RunTraceRow) = predicted.line == actual.line

    private fun compareRow(
        predicted: TraceRow,
        actual: RunTraceRow,
        run: RunRecord,
        columns: List<String>,
        actualCondition: Boolean?,
        isInputLine: Boolean,
    ): AlignedRow {
        val different = LinkedHashSet<String>()
        val shown = LinkedHashMap<String, String>()
        val stateAfter by lazy(LazyThreadSafetyMode.NONE) { RunInsights.stateAfter(run, actual.index) }
        for (variable in columns) {
            val guess = predicted.values[variable]?.takeIf { it.isNotBlank() }
            val changed = actual.changes[variable]
            when {
                changed != null -> {
                    shown[variable] = changed
                    if (guess == null || !sameValue(guess, changed)) different.add(variable)
                }
                guess != null -> {
                    val current = stateAfter[variable]
                    if (current != null) shown[variable] = current
                    if (current == null || !sameValue(guess, current)) different.add(variable)
                }
            }
        }
        if (actualCondition != null && predicted.condition != actualCondition) different.add(AlignedRow.CONDITION)
        val guessedOutput = normaliseOutput(predicted.output)
        val skipOutput = isInputLine && guessedOutput.isEmpty()
        if (!skipOutput && guessedOutput != normaliseOutput(actual.output)) different.add(AlignedRow.OUTPUT)
        return AlignedRow(
            predicted = predicted,
            actual = actual,
            match = if (different.isEmpty()) RowMatch.Match else RowMatch.Different,
            actualCondition = actualCondition,
            different = different,
            actualValues = shown,
        )
    }

    private fun compareInputs(plan: InputPlan, used: List<UsedInput>): List<InputComparison> {
        val planned = plan.items.filter { it.value.isNotEmpty() }
        return (0 until maxOf(planned.size, used.size)).map { i ->
            InputComparison(i, planned.getOrNull(i), used.getOrNull(i))
        }
    }

    private fun compareError(predicted: ExpectedError, outcome: RunOutcome): ErrorComparison {
        val kind: ExpectedErrorKind
        val line: Int?
        val text: String
        when (outcome) {
            is RunOutcome.Stopped -> return ErrorComparison(
                predicted, null, null,
                tr(Res.string.ERRORS_NONE_UNFINISHED),
                ErrorVerdict.NotComparable,
            )
            RunOutcome.Completed -> {
                kind = ExpectedErrorKind.None
                line = null
                text = tr(Res.string.ERRORS_NONE)
            }
            is RunOutcome.Failed -> {
                val error = outcome.error
                kind = if (error.type in SYNTAX_ERRORS) ExpectedErrorKind.Syntax else ExpectedErrorKind.Runtime
                line = error.line
                text = "${error.type}${error.line?.let { " on line $it" } ?: ""}: ${error.message}"
            }
        }
        val verdict = when (predicted.kind) {
            null -> ErrorVerdict.Different
            ExpectedErrorKind.Logic -> if (kind == ExpectedErrorKind.None) ErrorVerdict.SelfMark else ErrorVerdict.Different
            ExpectedErrorKind.None -> if (kind == ExpectedErrorKind.None) ErrorVerdict.Match else ErrorVerdict.Different
            ExpectedErrorKind.Syntax, ExpectedErrorKind.Runtime -> when {
                predicted.kind != kind -> ErrorVerdict.Different
                predicted.line != null && line != null && predicted.line != line -> ErrorVerdict.Different
                else -> ErrorVerdict.Match
            }
        }
        return ErrorComparison(predicted, kind, line, text, verdict)
    }

    private fun compareFiles(predicted: Map<String, String>, run: RunRecord): List<FileComparison> {
        val before = run.filesBefore ?: return emptyList()
        val after = run.filesAfter ?: return emptyList()
        return predicted.entries
            .sortedBy { it.key }
            .mapNotNull { (name, text) ->
                val start = before.firstOrNull { it.name == name }?.text
                if (FileRules.sameContents(text, start ?: "")) return@mapNotNull null
                FileComparison(name, start, text, after.firstOrNull { it.name == name }?.text)
            }
    }

    private fun mismatches(
        rows: List<AlignedRow>,
        inputs: List<InputComparison>,
        files: List<FileComparison>,
        error: ErrorComparison,
    ): List<Mismatch> {
        val result = ArrayList<Mismatch>()
        var i = 0
        while (i < rows.size) {
            val row = rows[i]
            when (row.match) {
                RowMatch.Match -> i++
                RowMatch.Different -> {
                    result.add(Mismatch("row:${row.predicted!!.id}:${row.actualKey}", MismatchKind.Row, i..i))
                    i++
                }
                RowMatch.NotRun, RowMatch.NotPredicted -> {
                    var end = i
                    while (end + 1 < rows.size && rows[end + 1].match == row.match) end++
                    val key = if (row.match == RowMatch.NotRun) {
                        "missing:${row.predicted!!.id}:${rows[end].predicted!!.id}"
                    } else {
                        "extra:${row.actualKey}:${rows[end].actualKey}"
                    }
                    val kind = if (row.match == RowMatch.NotRun) MismatchKind.NotRun else MismatchKind.NotPredicted
                    result.add(Mismatch(key, kind, i..end))
                    i = end + 1
                }
            }
        }
        for (input in inputs) {
            if (!input.matches) result.add(Mismatch("input:${input.position}", MismatchKind.Input, inputPosition = input.position))
        }
        for (file in files) {
            if (!file.matches) result.add(Mismatch("file:${file.name}", MismatchKind.File, fileName = file.name))
        }
        if (error.verdict == ErrorVerdict.Different) result.add(Mismatch("error", MismatchKind.Error))
        return result
    }

    private fun loopChecks(prediction: Prediction, run: RunRecord, analysis: Analysis?): List<LoopCheck> =
        RunInsights.loopSummaries(run, analysis).map { summary ->
            LoopCheck(summary.loop, prediction.loopEnds[summary.loop.line] ?: "", summary.text)
        }
}
