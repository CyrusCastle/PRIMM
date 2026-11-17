package uk.codecymru.primm.controller

import uk.codecymru.primm.model.ChangeLogEntry
import uk.codecymru.primm.model.Challenge
import uk.codecymru.primm.model.ChallengeCheck
import uk.codecymru.primm.model.CheckResult
import uk.codecymru.primm.model.EditorTab
import uk.codecymru.primm.model.ExpectedError
import uk.codecymru.primm.model.MakeTest
import uk.codecymru.primm.model.MakeWork
import uk.codecymru.primm.model.ModifyWork
import uk.codecymru.primm.model.PlanMode
import uk.codecymru.primm.model.Verdict
import uk.codecymru.primm.model.ExpectedErrorKind
import uk.codecymru.primm.model.InputPlan
import uk.codecymru.primm.model.Investigation
import uk.codecymru.primm.model.PlannedInput
import uk.codecymru.primm.model.Prediction
import uk.codecymru.primm.model.Program
import uk.codecymru.primm.model.ProgramError
import uk.codecymru.primm.model.ProgramSource
import uk.codecymru.primm.model.RunOutcome
import uk.codecymru.primm.model.RunRecord
import uk.codecymru.primm.model.RunSettings
import uk.codecymru.primm.model.RunTraceRow
import uk.codecymru.primm.model.SelfMark
import uk.codecymru.primm.model.Session
import uk.codecymru.primm.model.Stage
import uk.codecymru.primm.model.TextFile
import uk.codecymru.primm.model.StageKind
import uk.codecymru.primm.model.TraceMode
import uk.codecymru.primm.model.TraceRow
import uk.codecymru.primm.model.TraceStage
import uk.codecymru.primm.model.TraceTable
import uk.codecymru.primm.model.UsedInput
import primm.shared.generated.resources.*
import uk.codecymru.primm.model.tr

sealed interface SessionDecode {
    data class Success(val session: Session) : SessionDecode

    data class Failure(val message: String) : SessionDecode
}

object SessionCodec {
    const val FORMAT = "primm-session"
    const val VERSION = 1
    const val FILE_SUFFIX = ".primm.json"

    private val NOT_A_SESSION: String get() = tr(Res.string.SESSION_NOT_A_SESSION)

    fun encode(session: Session): String = JsonWriter.write(
        json(
            "format" to jsonOf(FORMAT),
            "version" to jsonOf(VERSION),
            "program" to json(
                "title" to jsonOf(session.program.title),
                "code" to jsonOf(session.program.code),
                "source" to source(session.program.source),
                "challenges" to jsonArray(session.program.challenges.map { challenge(it) }),
                "brief" to jsonOf(session.program.brief),
                "files" to files(session.program.files),
                "traceColumns" to jsonArray(session.program.traceColumns.map { jsonOf(it) }),
            ),
            "code" to jsonOf(session.code),
            "stage" to jsonOf(session.stage.name),
            "studentNames" to jsonOf(session.studentNames),
            "prediction" to prediction(session.prediction),
            "run" to (session.run?.let { run(it) } ?: JsonNull),
            "runCount" to jsonOf(session.runCount),
            "finishedRuns" to jsonOf(session.finishedRuns),
            "runSettings" to json(
                "autoFill" to jsonOf(session.runSettings.autoFill),
                "usePredictedInputs" to jsonOf(session.runSettings.usePredictedInputs),
            ),
            "manualRunTable" to table(session.manualRunTable),
            "investigation" to investigation(session.investigation),
            "modifiedCode" to (session.modifiedCode?.let { jsonOf(it) } ?: JsonNull),
            "makeCode" to (session.makeCode?.let { jsonOf(it) } ?: JsonNull),
            "activeTab" to jsonOf(session.activeTab.name),
            "modify" to modify(session.modify),
            "make" to make(session.make),
            "files" to files(session.files),
        ),
    )

    fun decode(text: String): SessionDecode {
        val parsed: JsonValue? = try {
            JsonReader.parse(text)
        } catch (e: JsonParseException) {
            null
        }
        val root = parsed as? JsonObject ?: return SessionDecode.Failure(NOT_A_SESSION)
        if (root.string("format") != FORMAT) return SessionDecode.Failure(NOT_A_SESSION)
        val version = root.int("version") ?: return SessionDecode.Failure(NOT_A_SESSION)
        if (version > VERSION) {
            return SessionDecode.Failure(tr(Res.string.SESSION_TOO_NEW))
        }
        val program = root.obj("program")?.let { readProgram(it) } ?: return SessionDecode.Failure(NOT_A_SESSION)
        val runSettings = root.obj("runSettings")
        return SessionDecode.Success(
            Session(
                program = program,
                code = root.string("code") ?: program.code,
                stage = Stage.entries.firstOrNull { it.name == root.string("stage") } ?: Stage.Predict,
                studentNames = root.string("studentNames") ?: "",
                prediction = root.obj("prediction")?.let { readPrediction(it) } ?: Prediction(),
                run = root.obj("run")?.let { readRun(it) },
                runCount = root.int("runCount") ?: 0,
                finishedRuns = root.int("finishedRuns") ?: 0,
                runSettings = RunSettings(
                    autoFill = runSettings?.boolean("autoFill") ?: true,
                    usePredictedInputs = runSettings?.boolean("usePredictedInputs") ?: true,
                ),
                manualRunTable = root.obj("manualRunTable")?.let { readTable(it) } ?: TraceTable(),
                investigation = root.obj("investigation")?.let { readInvestigation(it) } ?: Investigation(),
                modifiedCode = root.string("modifiedCode"),
                makeCode = root.string("makeCode"),
                activeTab = EditorTab.entries.firstOrNull { it.name == root.string("activeTab") } ?: EditorTab.Original,
                modify = root.obj("modify")?.let { readModify(it) } ?: ModifyWork(),
                make = root.obj("make")?.let { readMake(it) } ?: MakeWork(),
                files = readFiles(root["files"]) ?: program.files,
            ),
        )
    }

    fun fileName(title: String): String = slug(title) + FILE_SUFFIX

    fun slug(title: String): String = title.lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .split('-')
        .filter { it.isNotEmpty() }
        .joinToString("-")
        .ifEmpty { "primm-session" }

    /////////////
    // WRITING //
    /////////////

    private fun source(source: ProgramSource): JsonObject = when (source) {
        is ProgramSource.Library -> json("type" to jsonOf("library"), "id" to jsonOf(source.snippetId))
        is ProgramSource.Url -> json("type" to jsonOf("url"), "href" to jsonOf(source.href))
    }

    private fun files(files: List<TextFile>): JsonValue =
        jsonArray(files.map { json("name" to jsonOf(it.name), "text" to jsonOf(it.text)) })

    private fun readFiles(value: JsonValue?): List<TextFile>? = (value as? JsonArray)?.items
        ?.mapNotNull { it as? JsonObject }
        ?.mapNotNull { f -> f.string("name")?.let { TextFile(it, f.string("text") ?: "") } }
        ?.distinctBy { it.name.lowercase() }

    private fun prediction(prediction: Prediction): JsonObject = json(
        "summary" to jsonOf(prediction.summary),
        "inputs" to json(
            "items" to jsonArray(
                prediction.inputs.items.map {
                    json("id" to jsonOf(it.id), "line" to jsonOf(it.inputLine), "value" to jsonOf(it.value))
                },
            ),
            "nextId" to jsonOf(prediction.inputs.nextId),
        ),
        "table" to table(prediction.table),
        "loopEnds" to jsonStringMap(prediction.loopEnds.mapKeys { it.key.toString() }),
        "expectedError" to json(
            "kind" to (prediction.expectedError.kind?.let { jsonOf(it.name) } ?: JsonNull),
            "line" to jsonOf(prediction.expectedError.line),
            "reason" to jsonOf(prediction.expectedError.reason),
        ),
        "files" to jsonStringMap(prediction.files),
        "otherTable" to (prediction.otherTable?.let { table(it) } ?: JsonNull),
    )

    private fun table(table: TraceTable): JsonObject = json(
        "variables" to jsonArray(table.variables.map { jsonOf(it) }),
        "hidden" to jsonArray(table.hiddenVariables.map { jsonOf(it) }),
        "rows" to jsonArray(
            table.rows.map {
                json(
                    "id" to jsonOf(it.id),
                    "line" to jsonOf(it.line),
                    "values" to jsonStringMap(it.values),
                    "condition" to jsonOf(it.condition),
                    "output" to jsonOf(it.output),
                    "stage" to (it.stage?.let { stage -> json("kind" to jsonOf(stage.kind.name), "loop" to jsonOf(stage.loop)) } ?: JsonNull),
                    "decisions" to jsonStringMap(it.decisions.mapKeys { (head, _) -> head.toString() }),
                    "again" to jsonOf(it.again),
                )
            },
        ),
        "nextId" to jsonOf(table.nextId),
        "mode" to jsonOf(table.mode.name),
        "excluded" to jsonArray(table.excluded.map { jsonOf(it) }),
        "given" to jsonStringMap(table.given),
    )

    private fun run(run: RunRecord): JsonObject = json(
        "rows" to jsonArray(
            run.rows.map {
                json(
                    "index" to jsonOf(it.index),
                    "line" to jsonOf(it.line),
                    "frame" to jsonOf(it.frame),
                    "scope" to jsonOf(it.scope),
                    "changes" to jsonStringMap(it.changes),
                    "output" to jsonOf(it.output),
                    "done" to jsonOf(it.done),
                )
            },
        ),
        "inputs" to jsonArray(
            run.inputs.map { json("line" to jsonOf(it.line), "value" to jsonOf(it.value), "automatic" to jsonOf(it.automatic)) },
        ),
        "outcome" to (run.outcome?.let { outcome(it) } ?: JsonNull),
        "traceTruncated" to jsonOf(run.traceTruncated),
        "filesBefore" to (run.filesBefore?.let { files(it) } ?: JsonNull),
        "filesAfter" to (run.filesAfter?.let { files(it) } ?: JsonNull),
    )

    private fun outcome(outcome: RunOutcome): JsonObject = when (outcome) {
        RunOutcome.Completed -> json("type" to jsonOf("completed"))
        is RunOutcome.Stopped -> json("type" to jsonOf("stopped"), "restarted" to jsonOf(outcome.restarted))
        is RunOutcome.Failed -> json(
            "type" to jsonOf("failed"),
            "error" to json(
                "type" to jsonOf(outcome.error.type),
                "line" to jsonOf(outcome.error.line),
                "message" to jsonOf(outcome.error.message),
                "traceback" to jsonOf(outcome.error.traceback),
            ),
        )
    }

    private fun investigation(investigation: Investigation): JsonObject = json(
        "reflections" to jsonStringMap(investigation.reflections),
        "revealed" to jsonArray(investigation.revealed.map { jsonOf(it) }),
        "summaryMark" to mark(investigation.summaryMark),
        "loopMarks" to JsonObject(investigation.loopMarks.entries.associate { (line, value) -> line.toString() to mark(value) }),
        "errorMark" to mark(investigation.errorMark),
        "rewrittenSummary" to jsonOf(investigation.rewrittenSummary),
        "learned" to jsonOf(investigation.learned),
    )

    private fun mark(mark: SelfMark?): JsonValue = mark?.let { jsonOf(it.name) } ?: JsonNull

    private fun challenge(challenge: Challenge): JsonObject = json(
        "text" to jsonOf(challenge.text),
        "check" to (
            challenge.check?.let {
                json("inputs" to jsonArray(it.inputs.map { v -> jsonOf(v) }), "output" to jsonOf(it.output))
            } ?: JsonNull
            ),
    )

    private fun modify(work: ModifyWork): JsonObject = json(
        "ticked" to jsonArray(work.ticked.sorted().map { jsonOf(it) }),
        "checks" to JsonObject(
            work.checks.entries.associate { (index, result) ->
                index.toString() to json("passed" to jsonOf(result.passed), "message" to jsonOf(result.message))
            },
        ),
        "goal" to jsonOf(work.goal),
        "expectation" to jsonOf(work.expectation),
        "log" to jsonArray(
            work.log.map {
                json(
                    "number" to jsonOf(it.number),
                    "time" to jsonOf(it.time),
                    "expectation" to jsonOf(it.expectation),
                    "outcome" to jsonOf(it.outcome),
                    "verdict" to (it.verdict?.let { v -> jsonOf(v.name) } ?: JsonNull),
                    "note" to jsonOf(it.note),
                )
            },
        ),
        "reflection" to jsonOf(work.reflection),
    )

    private fun make(work: MakeWork): JsonObject = json(
        "brief" to jsonOf(work.brief),
        "planMode" to jsonOf(work.planMode.name),
        "planInputs" to jsonOf(work.planInputs),
        "planProcess" to jsonOf(work.planProcess),
        "planOutputs" to jsonOf(work.planOutputs),
        "pseudocode" to jsonOf(work.pseudocode),
        "tests" to jsonArray(
            work.tests.map {
                json(
                    "id" to jsonOf(it.id),
                    "inputs" to jsonOf(it.inputs),
                    "expected" to jsonOf(it.expected),
                    "actual" to (it.actual?.let { a -> jsonOf(a) } ?: JsonNull),
                    "passed" to jsonOf(it.passed),
                )
            },
        ),
        "nextTestId" to jsonOf(work.nextTestId),
        "ranWithoutError" to jsonOf(work.ranWithoutError),
        "reflection" to jsonOf(work.reflection),
    )

    private fun readChallenge(json: JsonObject): Challenge? {
        val text = json.string("text") ?: return null
        val check = json.obj("check")?.let { ChallengeCheck(it.strings("inputs"), it.string("output") ?: "") }
        return Challenge(text, check)
    }

    private fun readModify(json: JsonObject): ModifyWork = ModifyWork(
        ticked = json.array("ticked").mapNotNull { (it as? JsonNumber)?.value?.toInt() }.toSet(),
        checks = json.obj("checks")?.fields?.mapNotNull { (k, v) ->
            val index = k.toIntOrNull() ?: return@mapNotNull null
            val result = v as? JsonObject ?: return@mapNotNull null
            index to CheckResult(result.boolean("passed") ?: false, result.string("message") ?: "")
        }?.toMap() ?: emptyMap(),
        goal = json.string("goal") ?: "",
        expectation = json.string("expectation") ?: "",
        log = json.objects("log").mapIndexed { i, entry ->
            ChangeLogEntry(
                number = entry.int("number") ?: (i + 1),
                time = entry.string("time") ?: "",
                expectation = entry.string("expectation") ?: "",
                outcome = entry.string("outcome") ?: "",
                verdict = Verdict.entries.firstOrNull { it.name == entry.string("verdict") },
                note = entry.string("note") ?: "",
            )
        },
        reflection = json.string("reflection") ?: "",
    )

    private fun readMake(json: JsonObject): MakeWork {
        val tests = json.objects("tests").mapNotNull { test ->
            val id = test.int("id") ?: return@mapNotNull null
            MakeTest(id, test.string("inputs") ?: "", test.string("expected") ?: "", test.string("actual"), test.boolean("passed"))
        }
        return MakeWork(
            brief = json.string("brief") ?: "",
            planMode = PlanMode.entries.firstOrNull { it.name == json.string("planMode") } ?: PlanMode.ThreeBoxes,
            planInputs = json.string("planInputs") ?: "",
            planProcess = json.string("planProcess") ?: "",
            planOutputs = json.string("planOutputs") ?: "",
            pseudocode = json.string("pseudocode") ?: "",
            tests = tests,
            nextTestId = json.int("nextTestId") ?: ((tests.maxOfOrNull { it.id } ?: 0) + 1),
            ranWithoutError = json.boolean("ranWithoutError") ?: false,
            reflection = json.string("reflection") ?: "",
        )
    }

    /////////////
    // READING //
    /////////////

    private fun readProgram(json: JsonObject): Program? {
        val title = json.string("title") ?: return null
        val code = json.string("code") ?: return null
        val source = json.obj("source") ?: return null
        val programSource = when (source.string("type")) {
            "library" -> ProgramSource.Library(source.string("id") ?: return null)
            "url" -> ProgramSource.Url(source.string("href") ?: return null)
            else -> return null
        }
        return Program(
            title,
            code,
            programSource,
            challenges = json.objects("challenges").mapNotNull { readChallenge(it) },
            brief = json.string("brief") ?: "",
            files = readFiles(json["files"]) ?: emptyList(),
            traceColumns = json.strings("traceColumns"),
        )
    }

    private fun readPrediction(json: JsonObject): Prediction {
        val inputs = json.obj("inputs")
        val items = inputs?.objects("items")?.mapNotNull { item ->
            val id = item.int("id") ?: return@mapNotNull null
            PlannedInput(id, item.int("line"), item.string("value") ?: "")
        } ?: emptyList()
        val error = json.obj("expectedError")
        return Prediction(
            summary = json.string("summary") ?: "",
            inputs = InputPlan(items, inputs?.int("nextId") ?: ((items.maxOfOrNull { it.id } ?: 0) + 1)),
            table = json.obj("table")?.let { readTable(it) } ?: TraceTable(),
            loopEnds = json.stringMap("loopEnds").mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.toMap(),
            expectedError = ExpectedError(
                kind = ExpectedErrorKind.entries.firstOrNull { it.name == error?.string("kind") },
                line = error?.int("line"),
                reason = error?.string("reason") ?: "",
            ),
            files = json.stringMap("files"),
            otherTable = json.obj("otherTable")?.let { readTable(it) },
        )
    }

    private fun readTable(json: JsonObject): TraceTable {
        val rows = json.objects("rows").mapNotNull { row ->
            val id = row.int("id") ?: return@mapNotNull null
            TraceRow(
                id = id,
                line = row.int("line"),
                values = row.stringMap("values"),
                condition = row.boolean("condition"),
                output = row.string("output") ?: "",
                stage = row.obj("stage")?.let { stage ->
                    StageKind.entries.firstOrNull { it.name == stage.string("kind") }?.let { TraceStage(it, stage.int("loop") ?: 0) }
                },
                decisions = row.stringMap("decisions").mapNotNull { (head, value) -> head.toIntOrNull()?.let { it to value } }.toMap(),
                again = row.boolean("again"),
            )
        }
        val mode = TraceMode.entries.firstOrNull { it.name == json.string("mode") } ?: TraceMode.Lines
        return TraceTable(
            variables = json.strings("variables"),
            hiddenVariables = json.strings("hidden").toSet(),
            rows = if (mode == TraceMode.Passes) rows.filter { it.stage != null } else rows,
            nextId = json.int("nextId") ?: ((rows.maxOfOrNull { it.id } ?: 0) + 1),
            mode = mode,
            excluded = json.strings("excluded").toSet(),
            given = json.stringMap("given"),
        )
    }

    private fun readRun(json: JsonObject): RunRecord {
        val rows = json.objects("rows").mapIndexedNotNull { i, row ->
            RunTraceRow(
                index = row.int("index") ?: i,
                line = row.int("line") ?: return@mapIndexedNotNull null,
                frame = row.int("frame") ?: 1,
                scope = row.string("scope") ?: "",
                changes = row.stringMap("changes"),
                output = row.string("output") ?: "",
                done = row.boolean("done") ?: true,
            )
        }
        return RunRecord(
            rows = rows,
            inputs = json.objects("inputs").map {
                UsedInput(it.int("line"), it.string("value") ?: "", it.boolean("automatic") ?: false)
            },
            outcome = json.obj("outcome")?.let { readOutcome(it) },
            traceTruncated = json.boolean("traceTruncated") ?: false,
            filesBefore = readFiles(json["filesBefore"]),
            filesAfter = readFiles(json["filesAfter"]),
        )
    }

    private fun readOutcome(json: JsonObject): RunOutcome? = when (json.string("type")) {
        "completed" -> RunOutcome.Completed
        "stopped" -> RunOutcome.Stopped(json.boolean("restarted") ?: false)
        "failed" -> {
            val error = json.obj("error")
            RunOutcome.Failed(
                ProgramError(
                    type = error?.string("type") ?: "Error",
                    line = error?.int("line"),
                    message = error?.string("message") ?: "",
                    traceback = error?.string("traceback") ?: "",
                ),
            )
        }
        else -> null
    }

    private fun readInvestigation(json: JsonObject): Investigation = Investigation(
        reflections = json.stringMap("reflections"),
        revealed = json.strings("revealed").toSet(),
        summaryMark = readMark(json.string("summaryMark")),
        loopMarks = json.obj("loopMarks")?.fields
            ?.mapNotNull { (k, v) -> k.toIntOrNull()?.let { line -> readMark((v as? JsonString)?.value)?.let { line to it } } }
            ?.toMap() ?: emptyMap(),
        errorMark = readMark(json.string("errorMark")),
        rewrittenSummary = json.string("rewrittenSummary") ?: "",
        learned = json.string("learned") ?: "",
    )

    private fun readMark(name: String?): SelfMark? = SelfMark.entries.firstOrNull { it.name == name }
}
