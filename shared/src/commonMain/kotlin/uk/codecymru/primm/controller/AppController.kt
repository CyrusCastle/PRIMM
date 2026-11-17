package uk.codecymru.primm.controller

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.codecymru.primm.model.AppState
import uk.codecymru.primm.model.ConsoleKind
import uk.codecymru.primm.model.ContentCheck
import uk.codecymru.primm.model.DialogState
import uk.codecymru.primm.model.ExpectedErrorKind
import uk.codecymru.primm.model.Investigation
import uk.codecymru.primm.model.ConsoleTab
import uk.codecymru.primm.model.MarkTool
import uk.codecymru.primm.model.FileRules
import uk.codecymru.primm.model.FilesState
import uk.codecymru.primm.model.TextFile
import uk.codecymru.primm.model.BatchProgress
import uk.codecymru.primm.model.ChangeLogEntry
import uk.codecymru.primm.model.CheckResult
import uk.codecymru.primm.model.EditorTab
import uk.codecymru.primm.model.MakeWork
import uk.codecymru.primm.model.ModifyWork
import uk.codecymru.primm.model.OutputMatch
import uk.codecymru.primm.model.PlanMode
import uk.codecymru.primm.model.ProgramDirectives
import uk.codecymru.primm.model.Report
import uk.codecymru.primm.model.Verdict
import uk.codecymru.primm.model.SelfMark
import uk.codecymru.primm.model.Session
import uk.codecymru.primm.model.toSession
import uk.codecymru.primm.model.web.LaunchOptions
import uk.codecymru.primm.model.LinkCheck
import uk.codecymru.primm.model.LoadTab
import uk.codecymru.primm.model.Prediction
import uk.codecymru.primm.model.Preferences
import uk.codecymru.primm.model.PrimmRow
import uk.codecymru.primm.model.Program
import uk.codecymru.primm.model.ProgramSource
import uk.codecymru.primm.model.ProgramUrls
import uk.codecymru.primm.model.RunOutcome
import uk.codecymru.primm.model.RunRecord
import uk.codecymru.primm.model.RuntimeEvent
import uk.codecymru.primm.model.RuntimeStatus
import uk.codecymru.primm.model.Stage
import uk.codecymru.primm.model.TableTarget
import uk.codecymru.primm.model.TraceMode
import uk.codecymru.primm.model.TracePlan
import uk.codecymru.primm.model.TraceStyle
import uk.codecymru.primm.model.TeacherSettings
import uk.codecymru.primm.model.TraceTable
import uk.codecymru.primm.model.UsedInput
import uk.codecymru.primm.model.firstCodeLine
import primm.shared.generated.resources.*
import uk.codecymru.primm.model.tr


class AppController(
    private val runtime: PythonRuntime,
    private val browser: BrowserServices,
    initialProgram: Program = Program.fromSnippet(Snippets.starter),
) {
    private val mutableState = MutableStateFlow(
        AppState(
            program = initialProgram,
            files = FilesState.of(initialProgram.files),
            preferences = loadPreferences(),
            settings = TeacherSettings(
                unlockEditor = browser.readSetting(KEY_UNLOCK) == "1",
                gateRun = browser.readSetting(KEY_GATE) != "0",
                oneRowAtATime = browser.readSetting(KEY_ONE_ROW) != "0",
                openRows = browser.readSetting(KEY_OPEN_ROWS) == "1",
                revealAll = browser.readSetting(KEY_REVEAL_ALL) == "1",
                makeBriefOnly = browser.readSetting(KEY_MAKE_BRIEF_ONLY) == "1",
                stopAtInvestigate = browser.readSetting(KEY_STOP_AT_INVESTIGATE) == "1",
            ),
        ),
    )
    val state: StateFlow<AppState> = mutableState.asStateFlow()

    private val current: AppState get() = mutableState.value

    private var started = false

    private var linkUnlocksEditor = false
    private var linkGateOff = false
    private var linkOpensRows = false
    private var linkRevealsAll = false
    private var linkBriefOnly = false
    private var linkStopsAtInvestigate = false
    private var lastAutosave: String? = null
    private var autoInputs = ArrayDeque<String>()
    private var job: Job? = null

    private var batchQueue = ArrayDeque<BatchItem>()
    private var batchLabel = ""
    private var batchTotal = 0

    private var analysisTarget = EditorTab.Original
    private var pendingInputLine: Int? = null

    // Python runs
    private var lastRunId = 0
    private var activeRunId: Int? = null

    private var downloadGeneration = 0

    //////////////
    // START UP //
    //////////////

    fun start() {
        if (started) return
        started = true
        runtime.start(this::onRuntimeEvent)
        runtime.analyse(current.program.code)

        val launch = LaunchOptions.parse(browser.queryString())
        if (launch.unlockEditor) {
            linkUnlocksEditor = true
            change { it.copy(settings = it.settings.copy(unlockEditor = true)) }
        }
        if (launch.gateRun == false) {
            linkGateOff = true
            change { it.copy(settings = it.settings.copy(gateRun = false)) }
        }
        if (launch.openRows) {
            linkOpensRows = true
            change { it.copy(settings = it.settings.copy(openRows = true)) }
        }
        if (launch.revealAll) {
            linkRevealsAll = true
            change { it.copy(settings = it.settings.copy(revealAll = true)) }
        }
        if (launch.makeBriefOnly) {
            linkBriefOnly = true
            change { it.copy(settings = it.settings.copy(makeBriefOnly = true)) }
        }
        if (launch.stopAtInvestigate) {
            linkStopsAtInvestigate = true
            change { withStopAtInvestigate(it, true) }
        }
        val sourceUrl = launch.sourceUrl
        val snippetId = launch.snippetId

        // After a refresh, carry on with the autosaved work, unless the link names a different program.
        val autosaved = if (!current.preferences.autosave) {
            null
        } else {
            (browser.readSetting(KEY_SESSION)?.let { SessionCodec.decode(it) } as? SessionDecode.Success)?.session
        }
        val linked: ProgramSource? = when {
            sourceUrl != null -> ProgramSource.Url(sourceUrl.trim())
            snippetId != null -> ProgramSource.Library(snippetId)
            else -> null
        }
        if (autosaved != null && (linked == null || linked == autosaved.program.source)) {
            applySession(autosaved, tr(Res.string.CONSOLE_RESTORED, autosaved.program.title))
            return
        }
        when {
            sourceUrl != null -> loadFromPageLink(sourceUrl)
            snippetId != null -> {
                val snippet = Snippets.byId(snippetId)
                if (snippet != null) {
                    applyLoad(Program.fromSnippet(snippet), announce = false)
                } else {
                    change {
                        it.copy(
                            console = it.console.appendLine(
                                ConsoleKind.Error,
                                tr(Res.string.CONSOLE_NO_SUCH_SNIPPET, snippetId),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun loadFromPageLink(link: String) {
        when (val check = ProgramUrls.check(link)) {
            is LinkCheck.Rejected -> reportPageLinkProblem(check.reason)
            is LinkCheck.Fetch -> {
                val programBefore = current.program
                change { it.copy(loadingFromLink = true) }
                browser.fetchText(check.fetchUrl) { result ->
                    change { it.copy(loadingFromLink = false) }

                    if (current.program != programBefore) return@fetchText

                    when (val outcome = downloaded(result)) {
                        is ContentCheck.Accepted -> applyLoad(
                            ProgramDirectives.apply(
                                Program(ProgramUrls.titleFor(check.fetchUrl), outcome.code, ProgramSource.Url(link.trim())),
                            ),
                            announce = false,
                        )
                        is ContentCheck.Rejected -> reportPageLinkProblem(outcome.reason)
                    }
                }
            }
        }
    }

    private fun reportPageLinkProblem(reason: String) {
        change {
            it.copy(
                console = it.console.appendLine(ConsoleKind.Error, tr(Res.string.CONSOLE_LINK_FAILED, reason)),
            )
        }
    }


    /////////////
    // EDITING //
    /////////////

    fun updateCode(code: String) {
        when (current.activeTab) {
            EditorTab.Original -> change { it.copy(code = code) }
            EditorTab.Modified -> change { it.copy(modifiedCode = code) }
            EditorTab.Make -> {
                change { it.copy(makeCode = code) }
                requestMakeAnalysis()
            }
        }
    }

    fun selectTab(tab: EditorTab) {
        change { if (tab in it.availableTabs) it.copy(activeTab = tab) else it }
    }

    fun setStudentNames(names: String) {
        change { it.copy(studentNames = names) }
    }

    /////////////
    // RUNNING //
    /////////////

    fun run() {
        if (current.runsAreTraced) startRun(step = false) else startPlainRun()
    }

    fun step() {
        if (current.runsAreTraced) startRun(step = true)
    }

    fun runOriginal() = runOriginal(step = false)

    fun stepOriginal() = runOriginal(step = true)

    private fun runOriginal(step: Boolean) {
        if (current.modifiedCode != null) return
        change { it.copy(activeTab = EditorTab.Original) }
        startRun(step)
    }

    private fun startRun(step: Boolean) {
        val state = current
        if (!state.canRun) return
        job = Job.Traced
        autoInputs = if (state.runSettings.usePredictedInputs) ArrayDeque(state.prediction.inputs.values) else ArrayDeque()
        pendingInputLine = null
        change {
            val stage = if (it.stage == Stage.Predict) Stage.Run else it.stage
            it.copy(
                status = RuntimeStatus.Running,
                lastOutcome = null,
                lastRunCode = state.code,
                console = it.console.appendLine(
                    ConsoleKind.Info,
                    if (step) tr(Res.string.CONSOLE_RUNNING_STEP) else tr(Res.string.CONSOLE_RUNNING),
                ),
                run = RunRecord(stepping = step, filesBefore = it.files.files),
                consoleTab = ConsoleTab.Console,
                runCount = it.runCount + 1,
                lastRunTab = EditorTab.Original,
                lastRunTraced = true,
                stage = stage,
                expandedRows = if (stage != it.stage) opened(it, PrimmRow.forStage(stage)) else it.expandedRows,
            )
        }
        runtime.run(state.code, newRunId(), step, files = state.files.files)
    }

    private fun startPlainRun() {
        val state = current
        if (!state.canRun) return
        val tab = state.activeTab
        val code = state.editorText
        autoInputs = ArrayDeque()
        pendingInputLine = null
        job = Job.Plain(tab, if (tab == EditorTab.Modified) state.modify.expectation else "")
        change {
            it.copy(
                status = RuntimeStatus.Running,
                lastOutcome = null,
                lastRunCode = code,
                lastRunTab = tab,
                lastRunTraced = false,
                console = it.console.appendLine(ConsoleKind.Info, tr(Res.string.CONSOLE_RUNNING_TAB, tab.title.lowercase())),
                consoleTab = ConsoleTab.Console,
            )
        }
        runtime.run(code, newRunId(), step = false, trace = false, files = state.files.files)
    }

    fun next() {
        if (!current.isPaused) return
        change { it.copy(status = RuntimeStatus.Running) }
        runtime.step(toEnd = false)
    }

    fun continueRun() {
        if (!current.isPaused) return
        change { it.copy(status = RuntimeStatus.Running, run = it.run?.copy(stepping = false)) }
        runtime.step(toEnd = true)
    }

    fun stop() {
        if (!current.canStop) return
        batchQueue.clear()
        change { it.copy(status = RuntimeStatus.Stopping) }
        runtime.stop()
    }

    fun submitInput(text: String) {
        if (!current.isWaitingForInput) return
        val line = pendingInputLine
        pendingInputLine = null
        change {
            it.copy(
                status = RuntimeStatus.Running,
                console = it.console.append(ConsoleKind.Input, text + "\n"),
                run = it.run?.withInput(UsedInput(line, text, automatic = false)),
            )
        }
        runtime.sendInput(text)
    }

    fun clearConsole() {
        change { it.copy(console = it.console.clear()) }
    }

    private fun newRunId(): Int {
        lastRunId++
        activeRunId = lastRunId
        return lastRunId
    }

    private fun abandonRun() {
        activeRunId = null
        job = null
        autoInputs = ArrayDeque()
        pendingInputLine = null
        if (current.status != RuntimeStatus.Stopping) runtime.stop()
    }

    private fun isStale(run: Int?): Boolean = run != null && run != activeRunId

    fun onRuntimeEvent(event: RuntimeEvent, run: Int? = null) {
        val stale = isStale(run)
        when (event) {
            RuntimeEvent.Loading -> change { it.copy(status = RuntimeStatus.Loading) }

            is RuntimeEvent.Ready -> {
                change { it.copy(status = RuntimeStatus.Ready, runtimeProblem = null) }
                if (job == null && batchQueue.isNotEmpty()) startNextBatchItem()
            }

            is RuntimeEvent.LoadFailed -> change {
                it.copy(
                    status = RuntimeStatus.Unavailable,
                    runtimeProblem = event.message,
                    console = it.console.appendLine(
                        ConsoleKind.Error,
                        tr(Res.string.CONSOLE_PYTHON_FAILED, event.message),
                    ),
                )
            }

            is RuntimeEvent.Output -> if (!stale) {
                (job as? Job.Batch)?.output?.append(event.text)
                change { it.copy(console = it.console.append(ConsoleKind.Output, event.text)) }
            }

            is RuntimeEvent.ErrorOutput -> if (!stale) change {
                it.copy(console = it.console.append(ConsoleKind.Error, event.text))
            }

            is RuntimeEvent.InputRequested -> if (!stale) onInputRequested(event)

            RuntimeEvent.Paused -> change {
                if (it.status == RuntimeStatus.Stopping || stale) it
                else it.copy(status = RuntimeStatus.Paused)
            }

            is RuntimeEvent.TraceStart -> if (!stale) change {
                it.copy(run = it.run?.started(event.index, event.line, event.frame, event.scope))
            }

            is RuntimeEvent.TraceDone -> if (!stale) change {
                it.copy(run = it.run?.done(event.index, event.changes, event.output))
            }

            is RuntimeEvent.AnalysisReady -> if (analysisTarget == EditorTab.Make) {
                change { it.copy(makeAnalysis = event.analysis) }
            } else {
                change {
                    val plan = TracePlan.of(event.analysis, it.program.traceColumns)
                    it.copy(
                        analysis = event.analysis,
                        prediction = it.prediction.withAnalysis(event.analysis, plan, it.program.code),
                        manualRunTable = it.manualRunTable.withVariables(plan.columns),
                    )
                }
                if (current.makeCode != null) requestMakeAnalysis()
            }

            is RuntimeEvent.Finished -> {
                if (stale) {
                    if (activeRunId == null) {
                        change { finish(it, event.outcome, event.traceTruncated, discard = true, finished = null, time = "") }
                    }
                    return
                }
                val discard = false
                activeRunId = null
                autoInputs = ArrayDeque()
                pendingInputLine = null
                val finished = job
                job = null
                val time = if (finished is Job.Plain && finished.tab == EditorTab.Modified) browser.clockTime() else ""
                change { finish(it, event.outcome, event.traceTruncated, discard, finished, time) }
                val files = event.files
                if (!discard && finished !is Job.Batch && files != null) {
                    takeRunFiles(files, event.truncatedFiles, traced = finished == null || finished == Job.Traced)
                }
                if (!discard && finished is Job.Batch) startNextBatchItem()
            }
        }
    }

    private fun onInputRequested(event: RuntimeEvent.InputRequested) {
        val state = current
        if (state.status == RuntimeStatus.Stopping) return
        val batch = job as? Job.Batch
        if (batch != null) {
            if (event.prompt.isNotEmpty() && batch.output.endsWith(event.prompt)) {
                batch.output.setLength(batch.output.length - event.prompt.length)
            }
            val answer = batch.inputs.removeFirstOrNull()
            if (answer == null) {
                batch.ranOutOfInput = true
                change { it.copy(status = RuntimeStatus.Stopping) }
                runtime.stop()
            } else {
                change { it.copy(console = it.console.append(ConsoleKind.AutoInput, answer + "\n")) }
                runtime.sendInput(answer)
            }
            return
        }
        val predicted = autoInputs.removeFirstOrNull()
        if (predicted == null) {
            pendingInputLine = event.line
            change { it.copy(status = RuntimeStatus.WaitingForInput) }
            return
        }
        change {
            it.copy(
                console = it.console.append(ConsoleKind.AutoInput, predicted + "\n"),
                run = it.run?.withInput(UsedInput(event.line, predicted, automatic = true)),
            )
        }
        runtime.sendInput(predicted)
    }

    private fun finish(
        state: AppState,
        outcome: RunOutcome,
        traceTruncated: Boolean,
        discard: Boolean,
        finished: Job?,
        time: String,
    ): AppState {
        val restarted = outcome is RunOutcome.Stopped && outcome.restarted
        val status = if (restarted) RuntimeStatus.Loading else RuntimeStatus.Ready
        if (discard) return state.copy(status = status)
        val console = when (outcome) {
            RunOutcome.Completed -> state.console.appendLine(ConsoleKind.Info, tr(Res.string.CONSOLE_FINISHED))
            is RunOutcome.Stopped -> state.console.appendLine(ConsoleKind.Info, tr(Res.string.CONSOLE_STOPPED))
            is RunOutcome.Failed -> {
                val error = outcome.error
                val report = error.traceback.ifBlank { "${error.type}: ${error.message}" }
                state.console.ensureNewLine().append(ConsoleKind.Error, report.trimEnd() + "\n")
            }
        }
        val base = state.copy(status = status, lastOutcome = outcome, console = console)
        return when (finished) {
            null, Job.Traced -> base.copy(
                run = state.run?.finished(outcome, traceTruncated),
                finishedRuns = if (state.run != null) state.finishedRuns + 1 else state.finishedRuns,
            )
            is Job.Plain -> when (finished.tab) {
                EditorTab.Modified -> base.copy(
                    modify = state.modify.copy(
                        expectation = "",
                        log = state.modify.log + ChangeLogEntry(
                            number = state.modify.log.size + 1,
                            time = time,
                            expectation = finished.expectation,
                            outcome = describeOutcome(outcome),
                        ),
                    ),
                )
                EditorTab.Make -> base.copy(
                    make = state.make.copy(ranWithoutError = state.make.ranWithoutError || outcome == RunOutcome.Completed),
                )
                EditorTab.Original -> base
            }
            is Job.Batch -> {
                val item = finished.item
                val output = finished.output.toString()
                val completed = outcome == RunOutcome.Completed && !finished.ranOutOfInput
                val progress = state.batch?.let { it.copy(done = it.done + 1) }
                when (item.kind) {
                    BatchKind.Check -> {
                        val passed = completed && OutputMatch.containsInOrder(item.expected, output)
                        val message = when {
                            passed -> tr(Res.string.CHECK_PASSED)
                            finished.ranOutOfInput -> tr(Res.string.CHECK_RAN_OUT_OF_INPUT)
                            !completed -> describeOutcome(outcome)
                            else -> tr(Res.string.CHECK_MISSING, OutputMatch.lines(item.expected).joinToString(" / "))
                        }
                        base.copy(
                            batch = progress,
                            modify = state.modify.copy(
                                checks = state.modify.checks + (item.id to CheckResult(passed, message)),
                                ticked = if (passed) state.modify.ticked + item.id else state.modify.ticked,
                            ),
                        )
                    }
                    BatchKind.Test -> {
                        val shown = if (finished.ranOutOfInput) tr(Res.string.TEST_RAN_OUT_OF_INPUT) else output.trimEnd()
                        val passed = completed && OutputMatch.matches(item.expected, output)
                        base.copy(
                            batch = progress,
                            make = state.make.withResult(item.id, shown, passed).copy(
                                ranWithoutError = state.make.ranWithoutError || outcome == RunOutcome.Completed,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun takeRunFiles(files: List<TextFile>, truncated: Set<String>, traced: Boolean) {
        change {
            val updated = it.files.afterRun(files)
            var console = it.console
            val changed = updated.changedByLastRun + updated.deletedByLastRun
            if (changed.isNotEmpty()) {
                console = console.appendLine(ConsoleKind.Info, tr(Res.string.CONSOLE_FILES_CHANGED, changed.sorted().joinToString(", ")))
            }
            for (name in truncated.sorted()) {
                console = console.appendLine(
                    ConsoleKind.Info,
                    tr(Res.string.CONSOLE_FILE_TRUNCATED, name, FileRules.MAX_CHARS),
                )
            }
            it.copy(
                files = updated,
                console = console,
                run = if (traced) it.run?.copy(filesAfter = files) else it.run,
            )
        }
    }

    ///////////
    // FILES //
    ///////////

    fun chooseMarkTool(tool: MarkTool) {
        change { it.copy(marks = it.marks.choose(tool)) }
    }

    fun toggleMarksVisible() {
        change { it.copy(marks = it.marks.toggleVisible()) }
    }

    fun clearMarks() {
        change { it.copy(marks = it.marks.clear()) }
    }

    fun showConsoleTab(tab: ConsoleTab) {
        change { it.copy(consoleTab = tab) }
    }

    fun selectFile(name: String) = changeFiles { it.select(name) }

    fun newFile() = changeFiles { it.add(FileRules.newName(it.names)) }

    fun setFileText(name: String, text: String) = changeFiles { it.setText(name, text) }

    fun renameFile(from: String, to: String) = changeFiles { it.rename(from, to.trim()) }

    fun askToRemoveFile(name: String) {
        if (current.files.file(name) == null) return
        change { it.copy(dialog = DialogState.ConfirmRemoveFile(name)) }
    }

    fun removeFile(name: String) {
        change { it.copy(files = it.files.remove(name), dialog = null) }
    }

    fun undoRunFileChanges() = changeFiles { it.undoLastRun() }

    fun uploadFile() {
        browser.pickTextFile(".txt,.csv,text/plain,text/csv") { pick ->
            when (pick) {
                FilePick.Cancelled -> Unit
                is FilePick.Failed -> changeFiles {
                    val reason = if ("too big" in pick.message) tr(Res.string.FILE_UPLOAD_TOO_BIG) else pick.message
                    it.copy(message = reason)
                }
                is FilePick.Picked -> changeFiles { files ->
                    when {
                        pick.text.length > FileRules.MAX_CHARS ->
                            files.copy(message = tr(Res.string.FILE_UPLOAD_TOO_LONG, pick.name, FileRules.MAX_CHARS))
                        else -> files.upload(pick.name.substringAfterLast('/').substringAfterLast('\\'), pick.text)
                    }
                }
            }
        }
    }

    fun downloadFile(name: String) {
        val file = current.files.file(name) ?: return
        browser.downloadText(file.name, file.text, FileRules.mimeType(file.name))
    }

    fun setPredictingFile(name: String, predicting: Boolean) {
        change {
            val files = if (predicting) {
                it.prediction.files + (name to (it.files.file(name)?.text ?: ""))
            } else {
                it.prediction.files - name
            }
            it.copy(prediction = it.prediction.copy(files = files))
        }
    }

    fun setPredictedFile(name: String, text: String) {
        change { it.copy(prediction = it.prediction.copy(files = it.prediction.files + (name to text))) }
    }

    private inline fun changeFiles(transform: (FilesState) -> FilesState) {
        change { it.copy(files = transform(it.files)) }
    }

    private fun describeOutcome(outcome: RunOutcome): String = when (outcome) {
        RunOutcome.Completed -> tr(Res.string.OUTCOME_RAN_TO_END)
        is RunOutcome.Stopped -> tr(Res.string.OUTCOME_STOPPED)
        is RunOutcome.Failed -> {
            val error = outcome.error
            "${error.type}${error.line?.let { " on line $it" } ?: ""}: ${error.message}"
        }
    }

    /////////////
    // LOADING //
    /////////////

    fun openLoadDialog() {
        val currentId = (current.program.source as? ProgramSource.Library)?.snippetId
        change { it.copy(dialog = DialogState.LoadProgram(selectedSnippetId = currentId ?: Snippets.all.first().id)) }
    }

    fun setLoadTab(tab: LoadTab) {
        changeLoadDialog { it.copy(tab = tab) }
    }

    fun setLibrarySearch(search: String) {
        changeLoadDialog { dialog ->
            val matches = Snippets.search(search)
            val keep = matches.any { it.id == dialog.selectedSnippetId }
            dialog.copy(
                search = search,
                selectedSnippetId = if (keep) dialog.selectedSnippetId else matches.firstOrNull()?.id,
            )
        }
    }

    fun selectSnippet(id: String) {
        changeLoadDialog { it.copy(selectedSnippetId = id) }
    }

    fun loadSelectedSnippet() {
        val dialog = current.dialog as? DialogState.LoadProgram ?: return
        val snippet = dialog.selectedSnippetId?.let { Snippets.byId(it) } ?: return
        requestLoad(Program.fromSnippet(snippet))
    }

    fun setLinkText(link: String) {
        changeLoadDialog { it.copy(link = link, linkError = null) }
    }

    fun loadFromLink() {
        val dialog = current.dialog as? DialogState.LoadProgram ?: return
        if (dialog.fetching) return
        val link = dialog.link.trim()
        when (val check = ProgramUrls.check(link)) {
            is LinkCheck.Rejected -> changeLoadDialog { it.copy(linkError = check.reason) }
            is LinkCheck.Fetch -> {
                val generation = ++downloadGeneration
                changeLoadDialog { it.copy(fetching = true, linkError = null) }
                browser.fetchText(check.fetchUrl) { result ->
                    if (generation != downloadGeneration || current.dialog !is DialogState.LoadProgram) {
                        return@fetchText
                    }
                    when (val outcome = downloaded(result)) {
                        is ContentCheck.Accepted -> {
                            changeLoadDialog { it.copy(fetching = false) }
                            requestLoad(
                                ProgramDirectives.apply(
                                    Program(ProgramUrls.titleFor(check.fetchUrl), outcome.code, ProgramSource.Url(link)),
                                ),
                            )
                        }
                        is ContentCheck.Rejected ->
                            changeLoadDialog { it.copy(fetching = false, linkError = outcome.reason) }
                    }
                }
            }
        }
    }

    private fun requestLoad(program: Program) {
        val state = current
        if (state.hasEdits || state.modifiedCode != null || state.makeCode != null) {
            change { it.copy(dialog = DialogState.ConfirmReplace(program)) }
        } else {
            applyLoad(program)
        }
    }

    fun confirmReplace() {
        val pending = (current.dialog as? DialogState.ConfirmReplace)?.pending ?: return
        applyLoad(pending)
    }

    fun closeDialog() {
        downloadGeneration++
        change { it.copy(dialog = null) }
    }

    private fun applyLoad(program: Program, announce: Boolean = true) {
        downloadGeneration++
        val wasRunning = current.isProgramRunning
        if (wasRunning) abandonRun()
        change {
            val console = it.console.clear()
            it.copy(
                program = program,
                code = program.code,
                stage = Stage.Predict,
                status = if (wasRunning) RuntimeStatus.Stopping else it.status,
                lastOutcome = null,
                lastRunCode = null,
                console = if (announce) console.appendLine(ConsoleKind.Info, tr(Res.string.CONSOLE_LOADED, program.title)) else console,
                dialog = null,
                loadingFromLink = false,
                analysis = null,
                prediction = Prediction.initial(program.code),
                run = null,
                runCount = 0,
                finishedRuns = 0,
                manualRunTable = TraceTable(),
                expandedRows = setOf(PrimmRow.Predict),
                investigation = Investigation(),
                modifiedCode = null,
                makeCode = null,
                activeTab = EditorTab.Original,
                modify = ModifyWork(),
                make = MakeWork(),
                makeAnalysis = null,
                batch = null,
                files = FilesState.of(program.files),
                marks = it.marks.newProgram(),
            )
        }
        batchQueue.clear()
        analysisTarget = EditorTab.Original
        runtime.analyse(program.code)
        browser.replaceQueryString(linkQuery(program.source))
    }

    private fun linkQuery(source: ProgramSource): String =
        LaunchOptions.queryFor(source, linkUnlocksEditor, linkGateOff, linkOpensRows, linkRevealsAll, linkBriefOnly, linkStopsAtInvestigate)

    private fun downloaded(result: FetchResult): ContentCheck = when (result) {
        is FetchResult.Success -> ProgramUrls.checkContent(result.text, result.contentType)
        is FetchResult.Failure -> ContentCheck.Rejected(ProgramUrls.failureMessage(result.failure))
    }

    private inline fun changeLoadDialog(transform: (DialogState.LoadProgram) -> DialogState.LoadProgram) {
        val dialog = current.dialog as? DialogState.LoadProgram ?: return
        change { it.copy(dialog = transform(dialog)) }
    }

    /////////////////
    // PREDICTIONS //
    /////////////////

    fun setSummary(summary: String) {
        changePrediction { it.copy(summary = summary) }
    }

    fun addPlannedInput(inputLine: Int?) {
        changePrediction { it.copy(inputs = it.inputs.add(inputLine)) }
    }

    fun setPlannedInputValue(id: Int, value: String) {
        changePrediction { it.copy(inputs = it.inputs.setValue(id, value)) }
    }

    fun setPlannedInputLine(id: Int, inputLine: Int?) {
        changePrediction { it.copy(inputs = it.inputs.setLine(id, inputLine)) }
    }

    fun movePlannedInput(id: Int, by: Int) {
        changePrediction { it.copy(inputs = it.inputs.move(id, by)) }
    }

    fun removePlannedInput(id: Int) {
        changePrediction { it.copy(inputs = it.inputs.remove(id)) }
    }

    fun setLoopEnd(loopLine: Int, text: String) {
        changePrediction { it.copy(loopEnds = it.loopEnds + (loopLine to text)) }
    }

    fun setExpectedErrorKind(kind: ExpectedErrorKind) {
        changePrediction { it.copy(expectedError = it.expectedError.copy(kind = kind)) }
    }

    fun setExpectedErrorLine(line: Int?) {
        changePrediction { it.copy(expectedError = it.expectedError.copy(line = line)) }
    }

    fun setExpectedErrorReason(reason: String) {
        changePrediction { it.copy(expectedError = it.expectedError.copy(reason = reason)) }
    }

    private inline fun changePrediction(transform: (Prediction) -> Prediction) {
        change { it.copy(prediction = transform(it.prediction)) }
    }

    //////////////////
    // TRACE TABLES //
    //////////////////

    fun addTraceRow(target: TableTarget) {
        val state = current
        changeTable(target) { it.addRow(firstCodeLine(state.code), state.lineCount) }
    }

    fun setTraceLine(target: TableTarget, rowId: Int, line: Int?) {
        changeTable(target) { it.setLine(rowId, line) }
    }

    fun setTraceValue(target: TableTarget, rowId: Int, variable: String, value: String) {
        changeTable(target) { it.setValue(rowId, variable, value) }
    }

    fun setTraceCondition(target: TableTarget, rowId: Int, condition: Boolean?) {
        changeTable(target) { it.setCondition(rowId, condition) }
    }

    fun setTraceOutput(target: TableTarget, rowId: Int, output: String) {
        changeTable(target) { it.setOutput(rowId, output) }
    }

    fun setTraceStyle(style: TraceStyle) {
        change { state ->
            val prediction = state.prediction.withTraceStyle(style, state.tracePlan, state.program.code)
            val manual = state.manualRunTable.takeIf { it.isStarted }
                ?: if (state.manualRunTable.rows.isEmpty()) state.manualRunTable
                else TraceTable.forPlan(if (prediction.table.byPass) state.tracePlan else state.tracePlan.copy(mode = TraceMode.Lines), state.code)
                    .withVariables(prediction.table.variables).copy(hiddenVariables = prediction.table.hiddenVariables)
            state.copy(prediction = prediction, manualRunTable = manual)
        }
    }

    fun addTracePass(target: TableTarget, loopLine: Int) {
        changeTable(target) { it.addPass(loopLine) }
    }

    fun setTraceDecision(target: TableTarget, rowId: Int, head: Int, value: String?) {
        changeTable(target) { it.setDecision(rowId, head, value) }
    }

    fun setTraceAgain(target: TableTarget, rowId: Int, again: Boolean?) {
        changeTable(target) { it.setAgain(rowId, again) }
    }

    fun removeTraceRow(target: TableTarget, rowId: Int) {
        changeTable(target) { it.removeRow(rowId) }
    }

    fun moveTraceRow(target: TableTarget, rowId: Int, by: Int) {
        changeTable(target) { it.moveRow(rowId, by) }
    }

    fun addTraceVariable(target: TableTarget, name: String) {
        changeTable(target) { it.addVariable(name) }
    }

    fun toggleTraceVariable(target: TableTarget, variable: String) {
        changeTable(target) { it.toggleHidden(variable) }
    }

    private inline fun changeTable(target: TableTarget, transform: (TraceTable) -> TraceTable) {
        change { state ->
            when (target) {
                TableTarget.Prediction ->
                    state.copy(prediction = state.prediction.copy(table = transform(state.prediction.table)))
                TableTarget.ManualRun -> state.copy(manualRunTable = transform(state.manualRunTable))
            }
        }
    }

    /////////
    // RUN //
    /////////

    fun setAutoFill(autoFill: Boolean) {
        change { state ->
            val manual = if (!autoFill && state.manualRunTable.rows.isEmpty()) {
                TraceTable.forPlan(state.shownTracePlan, state.code)
                    .withVariables(state.prediction.table.variables)
                    .copy(hiddenVariables = state.prediction.table.hiddenVariables)
            } else {
                state.manualRunTable
            }
            state.copy(runSettings = state.runSettings.copy(autoFill = autoFill), manualRunTable = manual)
        }
    }

    fun setUsePredictedInputs(use: Boolean) {
        change { it.copy(runSettings = it.runSettings.copy(usePredictedInputs = use)) }
    }

    /////////////////////////////
    // CONTROLLING PRIMM CYCLE //
    /////////////////////////////

    fun toggleRow(row: PrimmRow) {
        change {
            if (!it.isRowUnlocked(row)) return@change it.copy(expandedRows = it.expandedRows - row)
            val opening = row !in it.openRows
            val expanded = if (opening) opened(it, row) else it.expandedRows - row
            val stage = if (opening && row == PrimmRow.Investigate && it.stage.ordinal < Stage.Investigate.ordinal) {
                Stage.Investigate
            } else {
                it.stage
            }
            it.copy(expandedRows = expanded, stage = stage)
        }
    }

    private fun opened(state: AppState, row: PrimmRow): Set<PrimmRow> =
        if (state.settings.oneRowAtATime) setOf(row) else state.openRows + row

    ////////////
    // MODIFY //
    ////////////

    fun askToStartModifying() {
        if (current.modifiedCode != null || current.settings.stopAtInvestigate) return

        startModifying()
//        change { it.copy(dialog = DialogState.ConfirmStartModifying) }
    }

    fun startModifying() {
        change {
            if (it.modifiedCode != null || it.settings.stopAtInvestigate) return@change it.copy(dialog = null)
            val next = it.copy(modifiedCode = it.code, activeTab = EditorTab.Modified, stage = Stage.Modify, dialog = null)
            next.copy(expandedRows = opened(next, PrimmRow.Modify))
        }
    }

    fun askBackToOriginal() {
        if (current.modifiedCode == null) return
        change { it.copy(dialog = DialogState.ConfirmBackToOriginal) }
    }

    fun backToOriginal() {
        change { if (it.modifiedCode == null) it.copy(dialog = null) else it.copy(modifiedCode = it.code, dialog = null) }
    }

    fun openCompareWithOriginal() {
        if (current.modifiedCode == null) return
        change { it.copy(dialog = DialogState.CompareWithOriginal) }
    }

    fun setChallengeTicked(index: Int, ticked: Boolean) {
        changeModify { it.copy(ticked = if (ticked) it.ticked + index else it.ticked - index) }
    }

    fun checkChallenge(index: Int) {
        val state = current
        val check = state.program.challenges.getOrNull(index)?.check ?: return
        val code = state.modifiedCode ?: return
        startBatch(tr(Res.string.BATCH_CHECKING_CHALLENGE, index + 1), listOf(BatchItem(BatchKind.Check, index, code, check.inputs, check.output)))
    }

    fun setGoal(goal: String) {
        changeModify { it.copy(goal = goal) }
    }

    fun setExpectation(text: String) {
        changeModify { it.copy(expectation = text) }
    }

    fun setVerdict(number: Int, verdict: Verdict) {
        changeModify { work -> work.copy(log = work.log.map { if (it.number == number) it.copy(verdict = verdict) else it }) }
    }

    fun setLogNote(number: Int, note: String) {
        changeModify { work -> work.copy(log = work.log.map { if (it.number == number) it.copy(note = note) else it }) }
    }

    fun setModifyReflection(text: String) {
        changeModify { it.copy(reflection = text) }
    }

    private inline fun changeModify(transform: (ModifyWork) -> ModifyWork) {
        change { it.copy(modify = transform(it.modify)) }
    }

    //////////
    // MAKE //
    //////////

    fun setMakeBrief(text: String) = changeMake { it.copy(brief = text) }

    fun setPlanMode(mode: PlanMode) = changeMake { it.copy(planMode = mode) }

    fun setPlanInputs(text: String) = changeMake { it.copy(planInputs = text) }

    fun setPlanProcess(text: String) = changeMake { it.copy(planProcess = text) }

    fun setPlanOutputs(text: String) = changeMake { it.copy(planOutputs = text) }

    fun setPseudocode(text: String) = changeMake { it.copy(pseudocode = text) }

    fun newProgram() {
        if (current.settings.stopAtInvestigate) return
        change {
            val next = it.copy(makeCode = it.makeCode ?: "", activeTab = EditorTab.Make, stage = Stage.Make)
            next.copy(expandedRows = opened(next, PrimmRow.Make))
        }
    }

    fun addMakeTest() = changeMake { it.addTest() }

    fun setMakeTestInputs(id: Int, inputs: String) = changeMake { work -> work.updateTest(id) { it.copy(inputs = inputs) } }

    fun setMakeTestExpected(id: Int, expected: String) = changeMake { work -> work.updateTest(id) { it.copy(expected = expected) } }

    fun removeMakeTest(id: Int) = changeMake { it.removeTest(id) }

    fun runMakeTests() {
        val state = current
        val code = state.makeCode?.takeIf { it.isNotBlank() } ?: return
        val items = state.make.tests.map { BatchItem(BatchKind.Test, it.id, code, it.inputValues, OutputMatch.fromCell(it.expected)) }
        startBatch(tr(Res.string.BATCH_RUNNING_TESTS), items)
    }

    fun setMakeReflection(text: String) = changeMake { it.copy(reflection = text) }

    private fun changeMake(transform: (MakeWork) -> MakeWork) {
        change { it.copy(make = transform(it.make)) }
    }

    private fun requestMakeAnalysis() {
        val state = current
        val code = state.makeCode ?: return
        if (state.analysis == null) return
        analysisTarget = EditorTab.Make
        runtime.analyse(code)
    }

    //////////////////////
    // CHECKS AND TESTS //
    //////////////////////

    private fun startBatch(label: String, items: List<BatchItem>) {
        if (!current.canRun || items.isEmpty() || job != null) return
        batchQueue = ArrayDeque(items)
        batchLabel = label
        batchTotal = items.size
        change { it.copy(batch = BatchProgress(label, 0, items.size), consoleTab = ConsoleTab.Console) }
        startNextBatchItem()
    }

    private fun startNextBatchItem() {
        val item = batchQueue.removeFirstOrNull()
        if (item == null) {
            change { it.copy(batch = null) }
            return
        }

        if (!current.canRun) {
            batchQueue.addFirst(item)
            return
        }
        job = Job.Batch(item, ArrayDeque(item.inputs))
        val tab = if (item.kind == BatchKind.Check) EditorTab.Modified else EditorTab.Make
        val what = if (item.kind == BatchKind.Check) tr(Res.string.BATCH_CHECKING_CHALLENGE, item.id + 1) else tr(Res.string.BATCH_TEST_N_OF, batchTotal - batchQueue.size, batchTotal)
        change {
            it.copy(
                status = RuntimeStatus.Running,
                lastOutcome = null,
                lastRunCode = item.code,
                lastRunTab = tab,
                lastRunTraced = false,
                console = it.console.appendLine(ConsoleKind.Info, "▶ $what"),
            )
        }
        runtime.run(item.code, newRunId(), step = false, trace = false, files = current.files.files)
    }

    /////////////////
    // INVESTIGATE //
    /////////////////

    fun setReflection(key: String, text: String) {
        changeInvestigation { it.withReflection(key, text) }
    }

    fun reveal(key: String) {
        changeInvestigation { it.reveal(key) }
    }

    fun setSummaryMark(mark: SelfMark) {
        changeInvestigation { it.copy(summaryMark = mark) }
    }

    fun setLoopMark(loopLine: Int, mark: SelfMark) {
        changeInvestigation { it.copy(loopMarks = it.loopMarks + (loopLine to mark)) }
    }

    fun setErrorMark(mark: SelfMark) {
        changeInvestigation { it.copy(errorMark = mark) }
    }

    fun setRewrittenSummary(text: String) {
        changeInvestigation { it.copy(rewrittenSummary = text) }
    }

    fun setLearned(text: String) {
        changeInvestigation { it.copy(learned = text) }
    }

    private inline fun changeInvestigation(transform: (Investigation) -> Investigation) {
        change { it.copy(investigation = transform(it.investigation)) }
    }

    /////////////////////
    // SESSION CONTROL //
    /////////////////////

    fun autosave() {
        val state = current
        if (!started || state.loadingFromLink || !state.preferences.autosave) return
        val text = SessionCodec.encode(state.toSession())
        if (text == lastAutosave) return
        browser.writeSetting(KEY_SESSION, text)
        lastAutosave = text
    }

    fun setAutosave(on: Boolean) {
        change { it.copy(preferences = it.preferences.copy(autosave = on)) }
        browser.writeSetting(KEY_AUTOSAVE, if (on) "1" else "0")
        lastAutosave = null
        if (on) autosave() else browser.writeSetting(KEY_SESSION, "")
    }

    fun saveSessionFile() {
        val state = current
        val name = SessionCodec.fileName(state.program.title)
        browser.downloadText(name, SessionCodec.encode(state.toSession()), "application/json")
        changeSaveDialog { it.copy(message = tr(Res.string.SAVE_SESSION_SAVED, name)) }
    }

    fun openSaveDialog() {
        change { it.copy(dialog = DialogState.Save()) }
    }

    private fun report(): String = Report.html(current, browser.today())

    fun saveAsPdf() {
        browser.printReport(report())
        changeSaveDialog { it.copy(message = tr(Res.string.SAVE_PDF_HINT)) }
    }

    fun saveAsImage() = reportImage(copy = false)

    fun copyImage() = reportImage(copy = true)

    private fun reportImage(copy: Boolean) {
        val dialog = current.dialog as? DialogState.Save ?: return
        if (dialog.busy) return
        val name = SessionCodec.slug(current.program.title) + ".png"
        changeSaveDialog { it.copy(busy = true, message = if (copy) tr(Res.string.SAVE_IMAGE_COPYING) else tr(Res.string.SAVE_IMAGE_DRAWING)) }
        browser.saveReportImage(report(), name, copy) { result ->
            val message = when (result) {
                ImageResult.Done -> if (copy) tr(Res.string.SAVE_IMAGE_COPIED) else tr(Res.string.SAVE_IMAGE_SAVED, name)
                is ImageResult.Failed -> result.message
            }
            changeSaveDialog { it.copy(busy = false, message = message) }
        }
    }

    private inline fun changeSaveDialog(transform: (DialogState.Save) -> DialogState.Save) {
        val dialog = current.dialog as? DialogState.Save ?: return
        change { it.copy(dialog = transform(dialog)) }
    }

    fun chooseSessionFile() {
        if (current.dialog !is DialogState.LoadProgram) return
        changeLoadDialog { it.copy(sessionError = null) }
        browser.pickTextFile(".json,.primm.json") { pick ->
            if (current.dialog !is DialogState.LoadProgram) return@pickTextFile
            when (pick) {
                FilePick.Cancelled -> Unit
                is FilePick.Failed -> changeLoadDialog { it.copy(sessionError = pick.message) }
                is FilePick.Picked -> when (val decoded = SessionCodec.decode(pick.text)) {
                    is SessionDecode.Failure -> changeLoadDialog { it.copy(sessionError = decoded.message) }
                    is SessionDecode.Success ->
                        applySession(decoded.session, tr(Res.string.CONSOLE_OPENED_SESSION, decoded.session.program.title))
                }
            }
        }
    }

    private fun applySession(saved: Session, announcement: String) {
        val session = saved.settled()
        downloadGeneration++
        val wasRunning = current.isProgramRunning
        if (wasRunning) abandonRun()
        change {
            it.copy(
                program = session.program,
                code = session.code,
                stage = session.stage,
                status = if (wasRunning) RuntimeStatus.Stopping else it.status,
                lastOutcome = session.run?.outcome,
                lastRunCode = if (session.run != null) session.code else null,
                console = it.console.clear().appendLine(ConsoleKind.Info, announcement),
                dialog = null,
                loadingFromLink = false,
                analysis = null,
                studentNames = session.studentNames,
                prediction = session.prediction,
                run = session.run,
                runCount = session.runCount,
                finishedRuns = session.finishedRuns,
                runSettings = session.runSettings,
                manualRunTable = session.manualRunTable,
                investigation = session.investigation,
                modifiedCode = session.modifiedCode,
                makeCode = session.makeCode,
                activeTab = session.activeTab.takeIf { tab -> tab in it.copy(modifiedCode = session.modifiedCode, makeCode = session.makeCode).availableTabs }
                    ?: EditorTab.Original,
                modify = session.modify,
                make = session.make,
                files = FilesState.of(session.files),
                marks = it.marks.newProgram(),
                makeAnalysis = null,
                batch = null,
                lastRunTab = EditorTab.Original,
                lastRunTraced = true,
                expandedRows = setOf(PrimmRow.forStage(session.stage)),
            )
        }
        batchQueue.clear()
        analysisTarget = EditorTab.Original
        runtime.analyse(session.program.code)
        browser.replaceQueryString(linkQuery(session.program.source))
    }

    /////////////////
    // PREFERENCES //
    /////////////////

    fun increaseFontSize() = setFontSize(current.preferences.fontSize + Preferences.FONT_STEP)

    fun decreaseFontSize() = setFontSize(current.preferences.fontSize - Preferences.FONT_STEP)

    private fun setFontSize(size: Int) {
        val preferences = current.preferences.withFontSize(size)
        change { it.copy(preferences = preferences) }
        browser.writeSetting(KEY_FONT_SIZE, preferences.fontSize.toString())
    }

    fun setCodeFraction(fraction: Float) {
        val preferences = current.preferences.withCodeFraction(fraction)
        change { it.copy(preferences = preferences) }
        browser.writeSetting(KEY_CODE_FRACTION, preferences.codeFraction.toString())
    }

    fun setEditorFraction(fraction: Float) {
        val preferences = current.preferences.withEditorFraction(fraction)
        change { it.copy(preferences = preferences) }
        browser.writeSetting(KEY_EDITOR_FRACTION, preferences.editorFraction.toString())
    }

    fun openSettings() {
        change { it.copy(dialog = DialogState.Settings, studentLinkNote = null) }
    }

    fun studentLink(): String {
        val state = current
        val settings = state.settings
        val query = LaunchOptions.queryFor(
            state.program.source,
            unlockEditor = settings.unlockEditor,
            gateOff = !settings.gateRun,
            openRows = settings.openRows,
            revealAll = settings.revealAll,
            makeBriefOnly = settings.makeBriefOnly,
            stopAtInvestigate = settings.stopAtInvestigate,
        )
        return browser.pageAddress() + "?" + query
    }

    fun copyStudentLink() {
        val link = studentLink()
        browser.copyText(link) { copied ->
            change {
                it.copy(
                    studentLinkNote = if (copied) {
                        tr(Res.string.STUDENT_LINK_COPIED)
                    } else {
                        tr(Res.string.STUDENT_LINK_NOT_COPIED, link)
                    },
                )
            }
        }
    }

    fun setUnlockEditor(unlock: Boolean) {
        change { it.copy(settings = it.settings.copy(unlockEditor = unlock)) }
        browser.writeSetting(KEY_UNLOCK, if (unlock) "1" else "0")
    }

    fun setGateRun(gate: Boolean) {
        change { it.copy(settings = it.settings.copy(gateRun = gate)) }
        browser.writeSetting(KEY_GATE, if (gate) "1" else "0")
    }

    fun setMakeBriefOnly(briefOnly: Boolean) {
        change {
            val tab = if (briefOnly && it.activeTab == EditorTab.Make) EditorTab.Original else it.activeTab
            it.copy(settings = it.settings.copy(makeBriefOnly = briefOnly), activeTab = tab)
        }
        browser.writeSetting(KEY_MAKE_BRIEF_ONLY, if (briefOnly) "1" else "0")
    }

    fun setStopAtInvestigate(stop: Boolean) {
        change { withStopAtInvestigate(it, stop) }
        browser.writeSetting(KEY_STOP_AT_INVESTIGATE, if (stop) "1" else "0")
    }

    private fun withStopAtInvestigate(state: AppState, stop: Boolean): AppState {
        val next = state.copy(settings = state.settings.copy(stopAtInvestigate = stop))
        if (!stop) return next
        val hidden = setOf(PrimmRow.Modify, PrimmRow.Make)
        val expanded = (next.expandedRows - hidden).ifEmpty { setOf(PrimmRow.Investigate) }
        val tab = if (next.activeTab in next.availableTabs) next.activeTab else EditorTab.Original
        return next.copy(expandedRows = expanded, activeTab = tab, dialog = next.dialog.takeUnless { it == DialogState.ConfirmStartModifying })
    }

    fun setProgramExtras(text: String) {
        val extras = ProgramDirectives.parse(text)
        change { it.copy(program = it.program.copy(challenges = extras.challenges, brief = extras.brief)) }
    }

    fun setRevealAll(revealAll: Boolean) {
        change { it.copy(settings = it.settings.copy(revealAll = revealAll)) }
        browser.writeSetting(KEY_REVEAL_ALL, if (revealAll) "1" else "0")
    }

    fun setOpenRows(open: Boolean) {
        change {
            val next = it.copy(settings = it.settings.copy(openRows = open))
            next.copy(expandedRows = next.openRows)
        }
        browser.writeSetting(KEY_OPEN_ROWS, if (open) "1" else "0")
    }

    fun setOneRowAtATime(oneRow: Boolean) {
        change {
            val expanded = if (oneRow && it.expandedRows.size > 1) setOf(PrimmRow.forStage(it.stage)) else it.expandedRows
            it.copy(settings = it.settings.copy(oneRowAtATime = oneRow), expandedRows = expanded)
        }
        browser.writeSetting(KEY_ONE_ROW, if (oneRow) "1" else "0")
    }

    private fun loadPreferences(): Preferences {
        val defaults = Preferences()
        return defaults
            .withFontSize(browser.readSetting(KEY_FONT_SIZE)?.toIntOrNull() ?: defaults.fontSize)
            .withCodeFraction(browser.readSetting(KEY_CODE_FRACTION)?.toFloatOrNull() ?: defaults.codeFraction)
            .withEditorFraction(browser.readSetting(KEY_EDITOR_FRACTION)?.toFloatOrNull() ?: defaults.editorFraction)
            .copy(autosave = browser.readSetting(KEY_AUTOSAVE) == "1")
    }

    private inline fun change(transform: (AppState) -> AppState) {
        mutableState.value = transform(mutableState.value)
    }

    companion object {
        const val KEY_FONT_SIZE = "fontSize"
        const val KEY_CODE_FRACTION = "codeFraction"
        const val KEY_EDITOR_FRACTION = "editorFraction"
        const val KEY_UNLOCK = "unlockEditor"
        const val KEY_GATE = "gateRun"
        const val KEY_ONE_ROW = "oneRowAtATime"
        const val KEY_OPEN_ROWS = "openRows"
        const val KEY_REVEAL_ALL = "revealAll"
        const val KEY_MAKE_BRIEF_ONLY = "makeBriefOnly"
        const val KEY_STOP_AT_INVESTIGATE = "stopAtInvestigate"

        // Autosave
        const val KEY_SESSION = "session"
        const val KEY_AUTOSAVE = "autosave"
    }
}

private sealed interface Job {
    data object Traced : Job

    data class Plain(val tab: EditorTab, val expectation: String) : Job

    class Batch(val item: BatchItem, val inputs: ArrayDeque<String>) : Job {
        val output = StringBuilder()
        var ranOutOfInput = false
    }
}

private enum class BatchKind { Check, Test }

private class BatchItem(val kind: BatchKind, val id: Int, val code: String, val inputs: List<String>, val expected: String)
