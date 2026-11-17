package uk.codecymru.primm.model

import primm.shared.generated.resources.*

enum class GuideStep(val row: PrimmRow) {
    // Predict
    Summary(PrimmRow.Predict),
    Inputs(PrimmRow.Predict),
    TraceTable(PrimmRow.Predict),
    LoopEnds(PrimmRow.Predict),
    ExpectedError(PrimmRow.Predict),
    // Run
    RunProgram(PrimmRow.Run),
    // Investigate
    Investigate(PrimmRow.Investigate),
    MarkAnswers(PrimmRow.Investigate),
    Reflect(PrimmRow.Investigate),
    // Modify
    Modify(PrimmRow.Modify),
    LogChange(PrimmRow.Modify),
    Challenges(PrimmRow.Modify),
    ModifyReflection(PrimmRow.Modify),
    // Make
    Plan(PrimmRow.Make),
    NewProgram(PrimmRow.Make),
    MakeRun(PrimmRow.Make),
    MakeTests(PrimmRow.Make),
    MakeReflection(PrimmRow.Make),
    Finished(PrimmRow.Make),
}

object Guide {

    fun predictSteps(state: AppState): List<GuideStep> {
        val analysis = state.analysis
        return buildList {
            add(GuideStep.Summary)
            if (analysis == null || analysis.inputs.isNotEmpty()) add(GuideStep.Inputs)
            add(GuideStep.TraceTable)
            if (analysis != null && analysis.loops.isNotEmpty() && !state.prediction.table.byPass) add(GuideStep.LoopEnds)
            add(GuideStep.ExpectedError)
        }
    }

    fun isDone(step: GuideStep, state: AppState): Boolean {
        val prediction = state.prediction
        val analysis = state.analysis
        return when (step) {
            GuideStep.Summary -> prediction.summary.isNotBlank()
            GuideStep.Inputs -> analysis != null && analysis.inputs.all { call ->
                prediction.inputs.items.any { it.inputLine == call.line && it.value.isNotEmpty() }
            }
            GuideStep.TraceTable -> prediction.table.isStarted
            GuideStep.LoopEnds -> analysis != null && analysis.loops.all { !prediction.loopEnds[it.line].isNullOrBlank() }
            GuideStep.ExpectedError -> prediction.expectedError.kind != null
            GuideStep.RunProgram -> state.finishedRuns > 0
            GuideStep.Investigate -> state.comparison != null && nextToExplain(state) == null
            GuideStep.MarkAnswers -> {
                val comparison = state.comparison ?: return false
                val investigation = state.investigation
                investigation.summaryMark != null &&
                    comparison.loops.all { it.loop.line in investigation.loopMarks } &&
                    (comparison.error.verdict != ErrorVerdict.SelfMark || investigation.errorMark != null)
            }
            GuideStep.Reflect -> {
                val investigation = state.investigation
                state.comparison != null && investigation.learned.isNotBlank() &&
                    (investigation.summaryMark == SelfMark.Right || investigation.rewrittenSummary.isNotBlank())
            }
            GuideStep.Modify -> state.modifiedCode != null
            GuideStep.LogChange -> state.modify.log.any { it.verdict != null }
            GuideStep.Challenges ->
                if (state.program.challenges.isEmpty()) state.modify.goal.isNotBlank() else state.modify.ticked.isNotEmpty()
            GuideStep.ModifyReflection -> allChallengesTicked(state) || state.modify.reflection.isNotBlank()
            GuideStep.Plan -> (state.program.brief.isNotBlank() || state.make.brief.isNotBlank()) && state.make.isPlanned
            GuideStep.NewProgram -> state.makeCode != null
            GuideStep.MakeRun -> state.make.ranWithoutError
            GuideStep.MakeTests -> state.make.tests.isNotEmpty() && state.make.tests.all { it.actual != null }
            GuideStep.MakeReflection -> state.make.reflection.isNotBlank()
            GuideStep.Finished -> false
        }
    }

    private fun allChallengesTicked(state: AppState): Boolean {
        val count = state.program.challenges.size
        return count > 0 && (0 until count).all { it in state.modify.ticked }
    }

    private val modifySteps = listOf(GuideStep.Modify, GuideStep.LogChange, GuideStep.Challenges, GuideStep.ModifyReflection)

    fun makeSteps(state: AppState): List<GuideStep> =
        if (state.settings.makeBriefOnly) listOf(GuideStep.MakeReflection)
        else listOf(GuideStep.Plan, GuideStep.NewProgram, GuideStep.MakeRun, GuideStep.MakeTests, GuideStep.MakeReflection)

    fun isModifyFinished(state: AppState): Boolean =
        state.modifiedCode != null && isDone(GuideStep.ModifyReflection, state)

    fun isMakeFinished(state: AppState): Boolean =
        isDone(GuideStep.MakeReflection, state) && (state.settings.makeBriefOnly || state.make.ranWithoutError)

    fun nextToExplain(state: AppState): Mismatch? =
        state.comparison?.mismatches?.firstOrNull { !state.investigation.isExplained(it.key) }

    private val investigateSteps = listOf(GuideStep.Investigate, GuideStep.MarkAnswers, GuideStep.Reflect)

    fun isInvestigateFinished(state: AppState): Boolean = investigateSteps.all { isDone(it, state) }

    fun predictCount(state: AppState): Pair<Int, Int> {
        val steps = predictSteps(state)
        return steps.count { isDone(it, state) } to steps.size
    }

    fun isPredictFinished(state: AppState): Boolean = predictSteps(state).all { isDone(it, state) }

    fun activeStep(state: AppState): GuideStep {
        val later = if (state.settings.stopAtInvestigate) emptyList() else modifySteps + makeSteps(state)
        return (predictSteps(state) + GuideStep.RunProgram + investigateSteps + later)
            .firstOrNull { !isDone(it, state) } ?: GuideStep.Finished
    }

    fun isUnlocked(row: PrimmRow, state: AppState): Boolean {
        if (row !in state.visibleRows) return false
        if (state.settings.openRows) return true
        return when (row) {
            PrimmRow.Predict -> true
            PrimmRow.Run -> !state.settings.gateRun || state.runCount > 0 || isPredictFinished(state)
            PrimmRow.Investigate -> state.finishedRuns > 0
            PrimmRow.Modify -> isInvestigateFinished(state)
            PrimmRow.Make -> isModifyFinished(state)
        }
    }

    fun lockReason(row: PrimmRow, state: AppState): String? {
        if (isUnlocked(row, state)) return null
        return when (row) {
            PrimmRow.Predict -> null
            PrimmRow.Run -> tr(Res.string.LOCKED_UNTIL_PREDICT)
            PrimmRow.Investigate -> tr(Res.string.LOCKED_UNTIL_RUN)
            PrimmRow.Modify -> tr(Res.string.LOCKED_UNTIL_INVESTIGATE)
            PrimmRow.Make -> tr(Res.string.LOCKED_UNTIL_MODIFY)
        }
    }
}
