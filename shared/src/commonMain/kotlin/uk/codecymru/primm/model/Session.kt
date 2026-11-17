package uk.codecymru.primm.model

data class Session(
    val program: Program,
    val code: String = program.code,
    val stage: Stage = Stage.Predict,
    val studentNames: String = "",
    val prediction: Prediction = Prediction(),
    val run: RunRecord? = null,
    val runCount: Int = 0,
    val finishedRuns: Int = 0,
    val runSettings: RunSettings = RunSettings(),
    val manualRunTable: TraceTable = TraceTable(),
    val investigation: Investigation = Investigation(),
    val modifiedCode: String? = null,
    val makeCode: String? = null,
    val activeTab: EditorTab = EditorTab.Original,
    val modify: ModifyWork = ModifyWork(),
    val make: MakeWork = MakeWork(),
    val files: List<TextFile> = program.files,
) {
    fun settled(): Session {
        val record = run ?: return this
        if (record.isFinished) return this
        return copy(
            run = record.finished(RunOutcome.Stopped(restarted = false), record.traceTruncated),
            finishedRuns = finishedRuns + 1,
        )
    }
}

fun AppState.toSession(): Session = Session(
    program = program,
    code = code,
    stage = stage,
    studentNames = studentNames,
    prediction = prediction,
    run = run,
    runCount = runCount,
    finishedRuns = finishedRuns,
    runSettings = runSettings,
    manualRunTable = manualRunTable,
    investigation = investigation,
    modifiedCode = modifiedCode,
    makeCode = makeCode,
    activeTab = activeTab,
    modify = modify,
    make = make,
    files = files.files,
)
