package uk.codecymru.primm.model

import primm.shared.generated.resources.*

object Report {

    fun html(state: AppState, date: String): String = buildString {
        append("<!DOCTYPE html><html lang=\"").append(tr(Res.string.LANGUAGE_CODE)).append("\"><head><meta charset=\"utf-8\">")
        append("<title>").append(esc(tr(Res.string.REPORT_PAGE_TITLE, state.program.title))).append("</title>")
        append("<style>").append(STYLE).append("</style></head><body>")
        header(state, date)
        code(state)
        predict(state)
        run(state)
        investigate(state)
        if (!state.settings.stopAtInvestigate) {
            modify(state)
            make(state)
        }
        append("</body></html>")
//        append("<footer>Made with primm.codecymru.uk</footer></body></html>")
    }

    //////////////
    // SECTIONS //
    //////////////

    private fun StringBuilder.header(state: AppState, date: String) {
        append("<header><h1>").append(esc(state.program.title)).append("</h1><p class=\"meta\">")
        append(esc(date))
        if (state.studentNames.isNotBlank()) append(" ").append(esc(tr(Res.string.REPORT_NAMES, state.studentNames.trim())))
        append("</p></header>")
    }

    private fun StringBuilder.code(state: AppState) {
        section(tr(Res.string.REPORT_CODE)) {
            h3(tr(Res.string.EDITOR_TAB_ORIGINAL))
            codeBlock(state.code)
            state.modifiedCode?.let {
                h3(tr(Res.string.EDITOR_TAB_MODIFIED))
                val diff = LineDiff.cached(state.code, it)
                val changes = LineDiff.changeCount(diff)
                append(para(if (changes == 0) tr(Res.string.REPORT_NO_CHANGES) else tr(Res.string.REPORT_LINES_DIFFER, plural(changes, Res.string.COUNT_LINE_ONE, Res.string.COUNT_LINE_OTHER))))
                diffBlock(diff)
            }
            state.makeCode?.let {
                h3(tr(Res.string.EDITOR_TAB_MAKE))
                if (it.isBlank()) append(para(tr(Res.string.REPORT_EMPTY))) else codeBlock(it)
            }
            if (state.files.files.isNotEmpty()) {
                h3(tr(Res.string.REPORT_FILES))
                for (file in state.files.files) fileBlock(file.name, file.text)
            }
        }
    }

    private fun StringBuilder.predict(state: AppState) {
        val p = state.prediction
        section(tr(Res.string.REPORT_PREDICT)) {
            field(tr(Res.string.REPORT_WHAT_DOES_IT_DO), p.summary)
            val inputs = p.inputs.items.filter { it.value.isNotEmpty() }
            if (inputs.isNotEmpty()) {
                h3(tr(Res.string.REPORT_INPUTS_I_WILL_TYPE))
                append("<ol>")
                for (item in inputs) {
                    append("<li>").append(esc(item.value))
                    item.inputLine?.let { append(" <span class=\"quiet\">").append(esc(tr(Res.string.REPORT_ON_LINE, it))).append("</span>") }
                    append("</li>")
                }
                append("</ol>")
            }
            h3(tr(Res.string.REPORT_TRACE_TABLE))
            val columns = p.table.visibleVariables
            if (p.table.byPass) {
                val shape = PassShape(state)
                table(shape.headers(columns)) {
                    for (row in p.table.rows) {
                        val stage = row.stage ?: continue
                        tableRow(
                            listOf(TraceLabels.stage(stage, p.table.passNumber(row), shape.plan)) + columns.map { row.values[it] ?: "" } +
                                shape.plan.chains.map { TraceLabels.decisionText(it, row.decisions[it.head], shape.codeLines) } +
                                (if (shape.hasWhile) listOf(TraceLabels.again(row.again)) else emptyList()) + listOf(row.output),
                        )
                    }
                }
            } else {
                table(listOf(tr(Res.string.TRACE_LINE)) + columns + listOf(tr(Res.string.TRACE_CONDITION), tr(Res.string.TRACE_OUTPUT))) {
                    for (row in p.table.rows) {
                        tableRow(listOf(row.line?.toString() ?: "") + columns.map { row.values[it] ?: "" } + listOf(condition(row.condition), row.output))
                    }
                }
            }
            if (p.loopEnds.values.any { it.isNotBlank() }) {
                h3(tr(Res.string.REPORT_LOOP_WILL_END))
                append("<ul>")
                for ((line, text) in p.loopEnds.entries.sortedBy { it.key }) {
                    if (text.isNotBlank()) append("<li>").append(esc(tr(Res.string.REPORT_LOOP_ON_LINE, line))).append(" ").append(esc(text)).append("</li>")
                }
                append("</ul>")
            }
            field(tr(Res.string.REPORT_EXPECTED_ERRORS), describe(p.expectedError))
            if (p.files.isNotEmpty()) {
                h3(tr(Res.string.REPORT_FILES_AFTERWARDS))
                for ((name, text) in p.files.entries.sortedBy { it.key }) fileBlock(name, text)
            }
        }
    }

    private fun StringBuilder.run(state: AppState) {
        section(tr(Res.string.REPORT_RUN)) {
            val run = state.run
            if (run == null) {
                append(para(tr(Res.string.REPORT_NOT_RUN)))
                return@section
            }
            append(para(tr(Res.string.REPORT_RUNS, state.runCount, outcome(run.outcome)) + if (run.traceTruncated) " " + tr(Res.string.REPORT_TRACE_STOPPED) else ""))
            if (run.inputs.isNotEmpty()) {
                h3(tr(Res.string.REPORT_INPUTS_USED))
                append("<ol>")
                for (input in run.inputs) {
                    append("<li>").append(esc(input.value))
                    if (input.automatic) append(" <span class=\"quiet\">").append(esc(tr(Res.string.REPORT_FROM_PREDICT))).append("</span>")
                    append("</li>")
                }
                append("</ol>")
            }
            val loops = RunInsights.loopSummaries(run, state.analysis)
            if (loops.isNotEmpty()) {
                h3(tr(Res.string.REPORT_LOOPS))
                append("<ul>")
                for (loop in loops) append("<li>").append(esc(loop.text)).append("</li>")
                append("</ul>")
            }
            h3(tr(Res.string.REPORT_TRACE_TABLE))
            val shape = PassShape(state)
            if (shape.plan.mode == TraceMode.Passes) {
                val columns = state.prediction.table.visibleVariables
                table(shape.headers(columns)) {
                    for (stage in PassTrace.stages(run, state.analysis, shape.plan)) {
                        tableRow(
                            listOf(TraceLabels.stage(stage.stage, stage.pass, shape.plan)) +
                                columns.map { if (it in stage.changed) stage.values[it] ?: "" else "" } +
                                shape.plan.chains.map { TraceLabels.decisionText(it, stage.decisions[it.head], shape.codeLines) } +
                                (if (shape.hasWhile) listOf(TraceLabels.again(stage.again)) else emptyList()) +
                                listOf(stage.output.trimEnd('\n').replace("\n", " ↵ ")),
                        )
                    }
                }
                return@section
            }
            val columns = RunInsights.columns(run, state.analysis)
            val conditions = RunInsights.conditions(run, state.analysis)
            val conditionLines = state.analysis?.conditionLines ?: emptySet()
            val shown = if (state.showsEveryLine) run.rows.indices.toSet()
            else RunInsights.notableRows(run, state.analysis, state.prediction.table.visibleVariables)
            val leftOut = run.rows.indices.count { it !in shown }
            if (leftOut > 0) append(para(plural(leftOut, Res.string.TRACE_LINES_LEFT_OUT_ONE, Res.string.TRACE_LINES_LEFT_OUT_OTHER)))
            table(listOf(tr(Res.string.TRACE_LINE)) + columns + listOf(tr(Res.string.TRACE_CONDITION), tr(Res.string.TRACE_OUTPUT))) {
                for ((i, row) in run.rows.withIndex()) {
                    if (i !in shown) continue
                    val where = if (row.scope.isEmpty()) "${row.line}" else "${row.line} (${row.scope})"
                    val cond = if (row.line in conditionLines) condition(conditions[i]) else ""
                    tableRow(listOf(where) + columns.map { row.changes[it] ?: "" } + listOf(cond, row.output.trimEnd('\n').replace("\n", " ↵ ")))
                }
            }
        }
    }

    private fun StringBuilder.investigate(state: AppState) {
        section(tr(Res.string.REPORT_INVESTIGATE)) {
            val comparison = state.comparison
            if (comparison == null) {
                append(para(tr(Res.string.REPORT_NOT_COMPARED)))
                return@section
            }
            val inv = state.investigation
            append(
                para(
                    tr(
                        Res.string.REPORT_DIFFER_EXPLAINED,
                        comparison.differingRows,
                        plural(comparison.rows.size, Res.string.COUNT_ROW_ONE, Res.string.COUNT_ROW_OTHER),
                        comparison.mismatches.count { inv.isExplained(it.key) },
                        plural(comparison.mismatches.size, Res.string.COUNT_DIFFERENCE_ONE, Res.string.COUNT_DIFFERENCE_OTHER),
                    ),
                ),
            )
            h3(tr(Res.string.REPORT_PREDICTION_AGAINST_RUN))
            val columns = comparison.columns
            if (comparison.byPass) passComparison(state, comparison) else table(listOf("", tr(Res.string.TRACE_LINE)) + columns + listOf(tr(Res.string.TRACE_CONDITION), tr(Res.string.TRACE_OUTPUT))) {
                for ((index, row) in comparison.rows.withIndex()) {
                    val mark = when (row.match) {
                        RowMatch.Match -> "✓"
                        RowMatch.Different -> "≠"
                        RowMatch.NotRun -> tr(Res.string.REPORT_DIDNT_RUN)
                        RowMatch.NotPredicted -> tr(Res.string.REPORT_NOT_PREDICTED)
                    }
                    val p = row.predicted
                    if (p != null) {
                        tableRow(
                            listOf(mark, p.line?.toString() ?: "?") + columns.map { p.values[it] ?: "" } +
                                listOf(condition(p.condition), p.output),
                            differing = columns.indices.filter { columns[it] in row.different }.map { it + 2 }.toSet() +
                                setOfNotNull(
                                    (columns.size + 2).takeIf { AlignedRow.CONDITION in row.different },
                                    (columns.size + 3).takeIf { AlignedRow.OUTPUT in row.different },
                                ),
                            cssClass = if (row.match == RowMatch.NotRun) "notrun" else null,
                        )
                    }
                    val a = row.actual
                    if (a != null && row.match != RowMatch.Match) {
                        tableRow(
                            listOf(if (p == null) mark else tr(Res.string.REPORT_RAN), a.line.toString()) + columns.map { row.actualValues[it] ?: "" } +
                                listOf(condition(row.actualCondition), a.output.trimEnd('\n').replace("\n", " ↵ ")),
                            cssClass = "actual",
                        )
                    }
                    comparison.mismatchEndingAt(index)?.let { m -> reflectionRow(columns.size + 4, inv.reflection(m.key)) }
                }
            }
            if (comparison.inputs.isNotEmpty()) {
                h3(tr(Res.string.REPORT_INPUTS))
                append("<ul>")
                for (input in comparison.inputs) {
                    append("<li>").append(if (input.matches) "✓ " else "≠ ")
                    append(esc(tr(Res.string.REPORT_INPUT_PLANNED, input.predicted?.value ?: tr(Res.string.REPORT_NOTHING), input.actual?.value ?: tr(Res.string.REPORT_NOTHING_NOT_ASKED))))
                    comparison.mismatches.firstOrNull { it.kind == MismatchKind.Input && it.inputPosition == input.position }?.let {
                        append("<br><span class=\"reflection\">").append(esc(inv.reflection(it.key).ifBlank { tr(Res.string.REPORT_NO_EXPLANATION) })).append("</span>")
                    }
                    append("</li>")
                }
                append("</ul>")
            }
            if (comparison.files.isNotEmpty()) {
                h3(tr(Res.string.REPORT_FILES))
                for (file in comparison.files) {
                    append(para((if (file.matches) "✓ " else "≠ ") + file.name))
                    fileBlock(tr(Res.string.REPORT_I_PREDICTED), file.predicted)
                    fileBlock(tr(Res.string.REPORT_IT_HELD), file.actual)
                    comparison.mismatches.firstOrNull { it.kind == MismatchKind.File && it.fileName == file.name }?.let {
                        append("<p class=\"reflection\">").append(esc(inv.reflection(it.key).ifBlank { tr(Res.string.REPORT_NO_EXPLANATION) })).append("</p>")
                    }
                }
            }
            h3(tr(Res.string.REPORT_ERRORS))
            append(para(tr(Res.string.REPORT_ERRORS_PREDICTED, describe(comparison.error.predicted), comparison.error.actualText)))
            comparison.mismatches.firstOrNull { it.kind == MismatchKind.Error }?.let {
                append("<p class=\"reflection\">").append(esc(inv.reflection(it.key).ifBlank { tr(Res.string.REPORT_NO_EXPLANATION) })).append("</p>")
            }
            h3(tr(Res.string.REPORT_MARKED_BY_STUDENT))
            append("<ul><li>").append(esc(tr(Res.string.REPORT_SUMMARY_MARK, inv.summaryMark?.title ?: tr(Res.string.REPORT_NOT_MARKED)))).append("</li>")
            for (check in comparison.loops) {
                append("<li>").append(esc(tr(Res.string.REPORT_LOOP_ON_LINE, check.loop.line))).append(" ").append(esc(tr(Res.string.REPORT_LOOP_SAID, check.predicted, check.actual)))
                append(" <strong>").append(esc(inv.loopMarks[check.loop.line]?.title ?: tr(Res.string.REPORT_NOT_MARKED))).append("</strong></li>")
            }
            if (comparison.error.verdict == ErrorVerdict.SelfMark) {
                append("<li>").append(esc(tr(Res.string.REPORT_LOGIC_ERROR_MARK, inv.errorMark?.title ?: tr(Res.string.REPORT_NOT_MARKED)))).append("</li>")
            }
            append("</ul>")
            field(tr(Res.string.INVESTIGATE_REWRITE_SUMMARY), inv.rewrittenSummary)
            field(tr(Res.string.INVESTIGATE_LEARNED), inv.learned)
        }
    }

    private fun StringBuilder.modify(state: AppState) {
        section(tr(Res.string.REPORT_MODIFY)) {
            if (state.modifiedCode == null) {
                append(para(tr(Res.string.REPORT_NOT_STARTED)))
                return@section
            }
            val work = state.modify
            val challenges = state.program.challenges
            if (challenges.isEmpty()) {
                field(tr(Res.string.REPORT_MY_GOAL), work.goal)
            } else {
                h3(tr(Res.string.REPORT_CHALLENGES))
                append("<ul class=\"ticks\">")
                for ((i, challenge) in challenges.withIndex()) {
                    append("<li>").append(if (i in work.ticked) "☑ " else "☐ ").append(esc(challenge.text))
                    work.checks[i]?.let { append(" <span class=\"quiet\">").append(esc(tr(Res.string.REPORT_CHECK, it.message))).append("</span>") }
                    append("</li>")
                }
                append("</ul>")
            }
            h3(tr(Res.string.REPORT_CHANGE_LOG))
            if (work.log.isEmpty()) {
                append(para(tr(Res.string.REPORT_NO_MODIFIED_RUNS)))
            } else {
                table(listOf("#", tr(Res.string.LOG_TIME), tr(Res.string.LOG_I_EXPECTED), tr(Res.string.LOG_WHAT_HAPPENED), tr(Res.string.LOG_AS_EXPECTED), tr(Res.string.LOG_NOTE))) {
                    for (entry in work.log) {
                        tableRow(listOf(entry.number.toString(), entry.time, entry.expectation, entry.outcome, entry.verdict?.title ?: "", entry.note))
                    }
                }
            }
            field(tr(Res.string.MODIFY_REFLECTION), work.reflection)
        }
    }

    private fun StringBuilder.make(state: AppState) {
        section(tr(Res.string.REPORT_MAKE)) {
            val work = state.make
            val brief = state.program.brief.ifBlank { work.brief }
            val started = state.makeCode != null || work != MakeWork()
            if (!started && !Guide.isModifyFinished(state)) {
                append(para(tr(Res.string.REPORT_NOT_STARTED)))
                return@section
            }
            field(tr(Res.string.REPORT_BRIEF), brief)
            if (!state.settings.makeBriefOnly) {
                when (work.planMode) {
                    PlanMode.ThreeBoxes -> {
                        h3(tr(Res.string.REPORT_PLAN))
                        table(listOf(tr(Res.string.PLAN_INPUTS), tr(Res.string.PLAN_PROCESS), tr(Res.string.PLAN_OUTPUTS))) { tableRow(listOf(work.planInputs, work.planProcess, work.planOutputs)) }
                    }
                    PlanMode.Pseudocode -> {
                        h3(tr(Res.string.REPORT_PLAN_PSEUDOCODE))
                        codeBlock(work.pseudocode, numbered = false)
                    }
                }
                h3(tr(Res.string.REPORT_TESTS))
                if (work.tests.isEmpty()) {
                    append(para(tr(Res.string.REPORT_NO_TESTS)))
                } else {
                    table(listOf(tr(Res.string.TEST_INPUTS), tr(Res.string.TEST_EXPECTED_OUTPUT), tr(Res.string.TEST_ACTUAL_OUTPUT), tr(Res.string.TEST_PASS))) {
                        for (test in work.tests) {
                            val pass = when (test.passed) {
                                true -> "✓"
                                false -> "✗"
                                null -> ""
                            }
                            tableRow(listOf(test.inputs, OutputMatch.fromCell(test.expected).replace("\n", " ↵ "), (test.actual ?: tr(Res.string.TEST_NOT_RUN)).replace("\n", " ↵ "), pass))
                        }
                    }
                }
                h3(tr(Res.string.REPORT_CONCEPTS))
                val original = Concepts.of(state.analysis)
                val made = Concepts.of(state.makeAnalysis)
                table(listOf(tr(Res.string.CONCEPTS_CONCEPT), tr(Res.string.EDITOR_TAB_ORIGINAL), tr(Res.string.CONCEPTS_MY_PROGRAM))) {
                    for (concept in Concept.entries) {
                        tableRow(listOf(concept.title, if (concept in original) "✓" else "", if (concept in made) "✓" else ""))
                    }
                }
            }
            field(tr(Res.string.MAKE_REFLECTION), work.reflection)
        }
    }

    /////////////
    // HELPERS //
    /////////////

    private inline fun StringBuilder.section(title: String, body: StringBuilder.() -> Unit) {
        append("<section><h2>").append(esc(title)).append("</h2>")
        body()
        append("</section>")
    }

    private fun StringBuilder.h3(text: String) {
        append("<h3>").append(esc(text)).append("</h3>")
    }

    private fun StringBuilder.field(label: String, value: String) {
        h3(label)
        append(if (value.isBlank()) "<p class=\"quiet\">${esc(tr(Res.string.REPORT_NOT_WRITTEN))}</p>" else "<p class=\"answer\">${esc(value).replace("\n", "<br>")}</p>")
    }

    private fun StringBuilder.codeBlock(code: String, numbered: Boolean = true) {
        append("<pre class=\"code\">")
        val lines = code.removeSuffix("\n").split('\n')
        for ((i, line) in lines.withIndex()) {
            if (numbered) append("<span class=\"ln\">${i + 1}</span>")
            append(esc(line)).append('\n')
        }
        append("</pre>")
    }

    private fun StringBuilder.fileBlock(name: String, text: String?) {
        append("<p class=\"quiet\">").append(esc(name)).append("</p>")
        when {
            text == null -> append(para(tr(Res.string.REPORT_NO_SUCH_FILE)))
            text.isEmpty() -> append(para(tr(Res.string.REPORT_EMPTY)))
            else -> {
                val lines = text.removeSuffix("\n").split('\n')
                val shown = lines.take(REPORT_FILE_LINES).joinToString("\n")
                codeBlock(shown, numbered = false)
                if (lines.size > REPORT_FILE_LINES) append(para(tr(Res.string.REPORT_MORE_LINES, plural(lines.size - REPORT_FILE_LINES, Res.string.COUNT_MORE_LINE_ONE, Res.string.COUNT_MORE_LINE_OTHER))))
            }
        }
    }

    private const val REPORT_FILE_LINES = 40

    private fun StringBuilder.diffBlock(diff: List<DiffLine>) {
        append("<pre class=\"code\">")
        for (line in diff) {
            when (line.kind) {
                DiffKind.Same -> append("<span class=\"ln\">${line.newLine}</span>").append(esc(line.newText ?: "")).append('\n')
                DiffKind.Added -> append("<span class=\"added\"><span class=\"ln\">+${line.newLine}</span>").append(esc(line.newText ?: "")).append("</span>")
                DiffKind.Changed -> append("<span class=\"changed\"><span class=\"ln\">~${line.newLine}</span>").append(esc(line.newText ?: "")).append("</span>")
                DiffKind.Removed -> append("<span class=\"removed\"><span class=\"ln\">−</span>").append(esc(line.oldText ?: "")).append("</span>")
            }
        }
        append("</pre>")
    }

    private class PassShape(state: AppState) {
        val plan = state.shownTracePlan
        val codeLines = state.code.removeSuffix("\n").split('\n')
        val hasWhile = plan.loops.any { it.kind == LoopKind.While }

        fun headers(columns: List<String>): List<String> =
            listOf(tr(Res.string.TRACE_PASS_HEADER)) + columns + plan.chains.map { TraceLabels.decisionHeader(it, codeLines) } +
                (if (hasWhile) listOf(tr(Res.string.TRACE_LOOP_AGAIN)) else emptyList()) + listOf(tr(Res.string.TRACE_OUTPUT))

        fun decisionKeys(): List<String> = plan.chains.map { AlignedRow.decision(it.head) } +
            (if (hasWhile) listOf(AlignedRow.AGAIN) else emptyList()) + listOf(AlignedRow.OUTPUT)
    }

    private fun StringBuilder.passComparison(state: AppState, comparison: Comparison) {
        val shape = PassShape(state)
        val table = state.prediction.table
        val inv = state.investigation
        val columns = comparison.columns
        val keys = columns + shape.decisionKeys()
        table(listOf("") + shape.headers(columns)) {
            for ((index, row) in comparison.rows.withIndex()) {
                val mark = when (row.match) {
                    RowMatch.Match -> "✓"
                    RowMatch.Different -> "≠"
                    RowMatch.NotRun -> tr(Res.string.REPORT_DIDNT_RUN)
                    RowMatch.NotPredicted -> tr(Res.string.REPORT_NOT_PREDICTED)
                }
                val p = row.predicted
                if (p != null) {
                    tableRow(
                        listOf(mark, p.stage?.let { TraceLabels.stage(it, table.passNumber(p), shape.plan) } ?: "") +
                            columns.map { p.values[it] ?: "" } +
                            shape.plan.chains.map { TraceLabels.decisionText(it, p.decisions[it.head], shape.codeLines) } +
                            (if (shape.hasWhile) listOf(TraceLabels.again(p.again)) else emptyList()) + listOf(p.output),
                        differing = keys.indices.filter { keys[it] in row.different }.map { it + 2 }.toSet(),
                        cssClass = if (row.match == RowMatch.NotRun) "notrun" else null,
                    )
                }
                val a = row.stage
                if (a != null && row.match != RowMatch.Match) {
                    tableRow(
                        listOf(if (p == null) mark else tr(Res.string.REPORT_RAN), TraceLabels.stage(a.stage, a.pass, shape.plan)) +
                            columns.map { if (it in a.changed || it in row.different) row.actualValues[it] ?: "" else "" } +
                            shape.plan.chains.map { TraceLabels.decisionText(it, row.actualDecisions[it.head], shape.codeLines) } +
                            (if (shape.hasWhile) listOf(TraceLabels.again(row.actualAgain)) else emptyList()) +
                            listOf(row.actualOutput.trimEnd('\n').replace("\n", " ↵ ")),
                        cssClass = "actual",
                    )
                }
                comparison.mismatchEndingAt(index)?.let { m -> reflectionRow(keys.size + 2, inv.reflection(m.key)) }
            }
        }
    }

    private class TableBuilder(val out: StringBuilder) {
        fun tableRow(cells: List<String>, differing: Set<Int> = emptySet(), cssClass: String? = null) {
            out.append(if (cssClass != null) "<tr class=\"$cssClass\">" else "<tr>")
            for ((i, cell) in cells.withIndex()) {
                out.append(if (i in differing) "<td class=\"diff\">" else "<td>").append(esc(cell)).append("</td>")
            }
            out.append("</tr>")
        }

        fun reflectionRow(span: Int, text: String) {
            out.append("<tr class=\"reflection\"><td colspan=\"$span\">")
                .append(esc(tr(Res.string.REPORT_EXPLANATION, text.ifBlank { tr(Res.string.REPORT_NOT_WRITTEN_YET) }))).append("</td></tr>")
        }
    }

    private inline fun StringBuilder.table(headers: List<String>, rows: TableBuilder.() -> Unit) {
        append("<table><thead><tr>")
        for (h in headers) append("<th>").append(esc(h)).append("</th>")
        append("</tr></thead><tbody>")
        TableBuilder(this).rows()
        append("</tbody></table>")
    }

    private fun para(text: String) = "<p>${esc(text)}</p>"

    private fun condition(value: Boolean?) = when (value) {
        true -> "True"
        false -> "False"
        null -> ""
    }

    private fun outcome(outcome: RunOutcome?) = when (outcome) {
        null -> tr(Res.string.REPORT_STILL_RUNNING)
        RunOutcome.Completed -> tr(Res.string.REPORT_RAN_TO_END)
        is RunOutcome.Stopped -> tr(Res.string.REPORT_STOPPED_BEFORE_END)
        is RunOutcome.Failed -> outcome.error.line?.let { tr(Res.string.ERROR_ON_LINE, outcome.error.type, it, outcome.error.message) }
            ?: tr(Res.string.ERROR_NO_LINE, outcome.error.type, outcome.error.message)
    }

    private fun describe(error: ExpectedError): String {
        val kind = error.kind ?: return tr(Res.string.REPORT_NOT_CHOSEN)
        if (kind == ExpectedErrorKind.None) return kind.title
        val described = error.line?.let { tr(Res.string.REPORT_KIND_ON_LINE, kind.title, it) } ?: kind.title
        return error.reason.takeIf { it.isNotBlank() }?.let { tr(Res.string.REPORT_WITH_REASON, described, it) } ?: described
    }


    fun esc(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }

    private val STYLE = """
        * {
          -webkit-print-color-adjust: exact;
          print-color-adjust: exact;
        }
        @page { size: A4; margin: 14mm; }
        * { box-sizing: border-box; }
        body { font-family: "Segoe UI", Roboto, Helvetica, Arial, sans-serif; font-size: 13px; color: #1f261e;
               background: #ffffff; margin: 0; padding: 24px; line-height: 1.4; }
        header { border-bottom: 3px solid #1e4c21; margin-bottom: 12px; }
        h1 { font-size: 24px; margin: 0 0 4px; color: #002907; }
        h2 { font-size: 17px; margin: 22px 0 6px; padding: 4px 8px; background: #1e4c21; color: #ffffff; }
        h3 { font-size: 13px; margin: 12px 0 4px; color: #002907; }
        p { margin: 4px 0; }
        .meta, .quiet, footer { color: #3e453c; }
        footer { margin-top: 24px; font-size: 11px; text-align: right; }
        .answer { padding: 6px 8px; background: #f1f5ec; border-radius: 4px; white-space: pre-wrap; }
        pre.code { font-family: Consolas, "Courier New", monospace; font-size: 12px; background: #f1f5ec;
                   padding: 8px; border-radius: 4px; white-space: pre-wrap; margin: 4px 0; }
        .ln { display: inline-block; width: 3em; color: #6b7368; user-select: none; }
        .added { background: #c4fabe; display: block; }
        .changed { background: #f3e2c4; display: block; }
        .removed { background: #f1d9da; text-decoration: line-through; display: block; }
        table { border-collapse: collapse; margin: 4px 0; width: auto; max-width: 100%; }
        th, td { border: 1px solid #d8dbd2; padding: 3px 6px; text-align: left; vertical-align: top;
                 font-family: Consolas, "Courier New", monospace; font-size: 12px; }
        th { background: #ecefe6; font-family: inherit; }
        tr { break-inside: avoid; page-break-inside: avoid; }
        td.diff { outline: 2px solid #b86e00; outline-offset: -2px; }
        tr.actual td { color: #1a494f; background: #f1f5ec; }
        tr.notrun td { text-decoration: line-through; color: #6b7368; }
        tr.reflection td, .reflection { font-family: inherit; font-style: italic; background: #e5fbe2; }
        ul.ticks { list-style: none; padding-left: 4px; }
        section { break-inside: auto; }
    """.trimIndent().replace("\n", " ")
}
