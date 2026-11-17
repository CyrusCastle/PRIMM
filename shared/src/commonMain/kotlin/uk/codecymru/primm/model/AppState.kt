package uk.codecymru.primm.model

enum class LoadTab { Library, Link, Session }

sealed interface DialogState {
    data class LoadProgram(
        val tab: LoadTab = LoadTab.Library,
        val search: String = "",
        val selectedSnippetId: String? = null,
        val link: String = "",
        val fetching: Boolean = false,
        val linkError: String? = null,
        val sessionError: String? = null,
    ) : DialogState

    data class ConfirmReplace(val pending: Program) : DialogState

    data object Settings : DialogState

    data object ConfirmStartModifying : DialogState

    data class ConfirmRemoveFile(val name: String) : DialogState

    data object ConfirmBackToOriginal : DialogState

    data object CompareWithOriginal : DialogState

    data class Save(val message: String? = null, val busy: Boolean = false) : DialogState
}

data class BatchProgress(val label: String, val done: Int, val total: Int)

data class ErrorMark(val line: Int, val message: String)

data class RunMarks(val nextLine: Int, val ranLines: Set<Int>)

enum class TableTarget { Prediction, ManualRun }

data class AppState(
    val program: Program,
    val code: String = program.code,
    val stage: Stage = Stage.Predict,
    val status: RuntimeStatus = RuntimeStatus.Loading,
    val console: Console = Console(),
    val lastOutcome: RunOutcome? = null,
    val lastRunCode: String? = null,
    val runtimeProblem: String? = null,
    val studentNames: String = "",
    val settings: TeacherSettings = TeacherSettings(),
    val studentLinkNote: String? = null,
    val preferences: Preferences = Preferences(),
    val dialog: DialogState? = null,
    val loadingFromLink: Boolean = false,
    val analysis: Analysis? = null,
    val prediction: Prediction = Prediction.initial(program.code),
    val run: RunRecord? = null,
    val runCount: Int = 0,
    val finishedRuns: Int = 0,
    val runSettings: RunSettings = RunSettings(),
    val manualRunTable: TraceTable = TraceTable(),
    val expandedRows: Set<PrimmRow> = setOf(PrimmRow.Predict),
    val investigation: Investigation = Investigation(),
    val modifiedCode: String? = null,
    val makeCode: String? = null,
    val activeTab: EditorTab = EditorTab.Original,
    val modify: ModifyWork = ModifyWork(),
    val make: MakeWork = MakeWork(),
    val makeAnalysis: Analysis? = null,
    val lastRunTab: EditorTab = EditorTab.Original,
    val lastRunTraced: Boolean = true,
    val batch: BatchProgress? = null,
    val files: FilesState = FilesState(),
    val consoleTab: ConsoleTab = ConsoleTab.Console,
    val marks: Marks = Marks(),
) {
    val tracePlan: TracePlan by lazy(LazyThreadSafetyMode.NONE) { TracePlan.of(analysis, program.traceColumns) }

    val canChooseTraceStyle: Boolean get() = tracePlan.mode == TraceMode.Passes

    val traceStyle: TraceStyle get() = if (prediction.table.byPass) TraceStyle.Simplified else TraceStyle.Verbose

    val showsEveryLine: Boolean get() = canChooseTraceStyle && traceStyle == TraceStyle.Verbose

    val shownTracePlan: TracePlan
        get() = if (prediction.table.byPass) tracePlan else tracePlan.copy(mode = TraceMode.Lines)

    val comparison: Comparison? by lazy(LazyThreadSafetyMode.NONE) { Compare.of(this) }

    fun isRevealed(key: String): Boolean = settings.revealAll || key in investigation.revealed

    val canRun: Boolean get() = status == RuntimeStatus.Ready && !loadingFromLink
    val canStop: Boolean
        get() = status == RuntimeStatus.Running || status == RuntimeStatus.WaitingForInput ||
            status == RuntimeStatus.Paused
    val isWaitingForInput: Boolean get() = status == RuntimeStatus.WaitingForInput
    val isPaused: Boolean get() = status == RuntimeStatus.Paused
    val isProgramRunning: Boolean
        get() = status == RuntimeStatus.Running || status == RuntimeStatus.WaitingForInput ||
            status == RuntimeStatus.Paused || status == RuntimeStatus.Stopping

    val isRunGated: Boolean get() = !isRowUnlocked(PrimmRow.Run)

    val runAllowed: Boolean
        get() = canRun && when (activeTab) {
            EditorTab.Original -> !isRunGated
            EditorTab.Modified -> modify.expectation.isNotBlank() && !modify.awaitingVerdict
            EditorTab.Make -> !makeCode.isNullOrBlank()
        }

    val runsAreTraced: Boolean get() = activeTab == EditorTab.Original && modifiedCode == null

    val canStep: Boolean get() = runsAreTraced && runAllowed

    val editorText: String
        get() = when (activeTab) {
            EditorTab.Original -> code
            EditorTab.Modified -> modifiedCode ?: code
            EditorTab.Make -> makeCode ?: ""
        }

    val isEditorReadOnly: Boolean
        get() = when (activeTab) {
            EditorTab.Original -> !settings.unlockEditor && (isCodeLocked || modifiedCode != null || stage.ordinal >= Stage.Modify.ordinal)
            else -> false
        }

    val availableTabs: List<EditorTab>
        get() = buildList {
            add(EditorTab.Original)
            if (settings.stopAtInvestigate) return@buildList
            if (modifiedCode != null) add(EditorTab.Modified)
            if (makeCode != null && !settings.makeBriefOnly) add(EditorTab.Make)
        }

    val visibleRows: List<PrimmRow>
        get() = if (settings.stopAtInvestigate) PrimmRow.entries.filter { it <= PrimmRow.Investigate } else PrimmRow.entries

    val modifiedDiff: List<DiffLine> get() = LineDiff.cached(code, modifiedCode ?: code)

    val diffMarks: DiffMarks?
        get() = if (activeTab == EditorTab.Modified && modifiedCode != null) LineDiff.cachedMarks(modifiedDiff) else null

    val activeStep: GuideStep get() = Guide.activeStep(this)

    val isRunPrompted: Boolean
        get() = !isProgramRunning && when (activeStep) {
            GuideStep.RunProgram -> true
            GuideStep.LogChange -> activeTab == EditorTab.Modified && runAllowed
            GuideStep.MakeRun -> activeTab == EditorTab.Make && runAllowed
            else -> false
        }

    fun isRowUnlocked(row: PrimmRow): Boolean = Guide.isUnlocked(row, this)

    val openRows: Set<PrimmRow> get() = expandedRows.filterTo(LinkedHashSet<PrimmRow>()) { isRowUnlocked(it) }

    val lineCount: Int get() = countLines(code)

    val runMarks: RunMarks?
        get() {
            val record = run ?: return null
            if (activeTab != EditorTab.Original || lastRunTab != EditorTab.Original || !lastRunTraced) return null
            if (code != lastRunCode) return null
            val next = if (isPaused) RunInsights.nextLine(record) ?: 0 else 0
            return RunMarks(next, RunInsights.ranLines(record))
        }

    fun table(target: TableTarget): TraceTable = when (target) {
        TableTarget.Prediction -> prediction.table
        TableTarget.ManualRun -> manualRunTable
    }

    val isCodeLocked: Boolean get() = stage.locksCode && !settings.unlockEditor

    val hasEdits: Boolean get() = code != program.code

    private val lastError: ProgramError? get() = (lastOutcome as? RunOutcome.Failed)?.error

    val errorHint: String? get() = lastError?.let { FriendlyErrors.explain(it) }

    val errorMark: ErrorMark?
        get() {
            val error = lastError ?: return null
            val line = error.line ?: return null
            if (activeTab != lastRunTab || editorText != lastRunCode) return null
            return ErrorMark(line, errorHint ?: "${error.type}: ${error.message}")
        }
}
