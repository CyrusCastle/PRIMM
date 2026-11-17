package uk.codecymru.primm.model

import org.jetbrains.compose.resources.StringResource
import primm.shared.generated.resources.*

enum class PrimmRow(val letter: StringResource, val smallLetter: StringResource, private val titleRes: StringResource) {
    Predict(Res.string.STAGE_PREDICT_SHORT, Res.string.STAGE_PREDICT_SHORT_ADDITIONAL, Res.string.STAGE_PREDICT),
    Run(Res.string.STAGE_RUN_SHORT, Res.string.STAGE_RUN_SHORT_ADDITIONAL, Res.string.STAGE_RUN),
    Investigate(Res.string.STAGE_INVESTIGATE_SHORT, Res.string.STAGE_INVESTIGATE_SHORT_ADDITIONAL, Res.string.STAGE_INVESTIGATE),
    Modify(Res.string.STAGE_MODIFY_SHORT, Res.string.STAGE_MODIFY_SHORT_ADDITIONAL, Res.string.STAGE_MODIFY),
    Make(Res.string.STAGE_MAKE_SHORT, Res.string.STAGE_MAKE_SHORT_ADDITIONAL, Res.string.STAGE_MAKE);

    val title: String get() = tr(titleRes)

    companion object {
        fun forStage(stage: Stage): PrimmRow = when (stage) {
            Stage.Predict -> Predict
            Stage.Run -> Run
            Stage.Investigate -> Investigate
            Stage.Modify -> Modify
            Stage.Make -> Make
        }
    }
}

enum class RowProgress { NotStarted, InProgress, Done }

object PrimmProgress {

    fun subtitle(row: PrimmRow, state: AppState): String = when (row) {
        PrimmRow.Predict -> {
            val rows = state.prediction.table.filledRows
            val inputs = state.prediction.inputs.items.size
            tr(Res.string.SUBTITLE_PREDICT, plural(rows, Res.string.COUNT_TRACE_ROW_ONE, Res.string.COUNT_TRACE_ROW_OTHER), plural(inputs, Res.string.COUNT_INPUT_ONE, Res.string.COUNT_INPUT_OTHER))
        }
        PrimmRow.Run -> {
            val run = state.run
            if (state.runCount == 0 || run == null) tr(Res.string.SUBTITLE_NOT_RUN)
            else tr(Res.string.SUBTITLE_RAN, state.runCount, plural(run.rows.size, Res.string.COUNT_ROW_ONE, Res.string.COUNT_ROW_OTHER))
        }
        PrimmRow.Investigate -> {
            val comparison = state.comparison
            when {
                comparison == null -> tr(Res.string.SUBTITLE_NOT_COMPARED)
                comparison.rows.isEmpty() -> tr(Res.string.SUBTITLE_NOTHING_TO_COMPARE)
                else -> tr(Res.string.SUBTITLE_ROWS_DIFFER, comparison.differingRows, comparison.rows.size)
            }
        }
        PrimmRow.Modify -> {
            val challenges = state.program.challenges.size
            when {
                challenges > 0 -> tr(Res.string.SUBTITLE_CHALLENGES, state.modify.ticked.count { it < challenges }, plural(challenges, Res.string.COUNT_CHALLENGE_ONE, Res.string.COUNT_CHALLENGE_OTHER))
                state.modifiedCode == null -> tr(Res.string.SUBTITLE_NOT_STARTED)
                else -> tr(Res.string.SUBTITLE_CHANGES_LOGGED, plural(state.modify.log.size, Res.string.COUNT_CHANGE_ONE, Res.string.COUNT_CHANGE_OTHER))
            }
        }
        PrimmRow.Make -> {
            val tests = state.make.tests
            when {
                state.settings.makeBriefOnly -> if (state.make.reflection.isBlank()) tr(Res.string.SUBTITLE_BRIEF_ONLY) else tr(Res.string.SUBTITLE_REFLECTION_WRITTEN)
                tests.isNotEmpty() -> tr(Res.string.SUBTITLE_TESTS_PASS, tests.count { it.passed == true }, plural(tests.size, Res.string.COUNT_TEST_ONE, Res.string.COUNT_TEST_OTHER))
                state.makeCode != null -> tr(Res.string.SUBTITLE_NO_TESTS)
                else -> tr(Res.string.SUBTITLE_NOT_STARTED)
            }
        }
    }

    fun progress(row: PrimmRow, state: AppState): RowProgress = when (row) {
        PrimmRow.Predict -> {
            when {
                Guide.isPredictFinished(state) -> RowProgress.Done
                state.prediction.isStarted -> RowProgress.InProgress
                else -> RowProgress.NotStarted
            }
        }
        PrimmRow.Run -> when {
            state.finishedRuns > 0 -> RowProgress.Done
            state.runCount > 0 -> RowProgress.InProgress
            else -> RowProgress.NotStarted
        }
        PrimmRow.Investigate -> when {
            Guide.isInvestigateFinished(state) -> RowProgress.Done
            state.comparison != null && state.investigation != Investigation() -> RowProgress.InProgress
            else -> RowProgress.NotStarted
        }
        PrimmRow.Modify -> when {
            Guide.isModifyFinished(state) -> RowProgress.Done
            state.modifiedCode != null -> RowProgress.InProgress
            else -> RowProgress.NotStarted
        }
        PrimmRow.Make -> when {
            Guide.isMakeFinished(state) -> RowProgress.Done
            state.makeCode != null || state.make != MakeWork() -> RowProgress.InProgress
            else -> RowProgress.NotStarted
        }
    }

}
