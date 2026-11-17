package uk.codecymru.primm.controller

import uk.codecymru.primm.model.FileUse
import uk.codecymru.primm.model.TextFile
import uk.codecymru.primm.model.Analysis
import uk.codecymru.primm.model.ConditionKind
import uk.codecymru.primm.model.ConditionLine
import uk.codecymru.primm.model.IfChain
import uk.codecymru.primm.model.InputCall
import uk.codecymru.primm.model.Literal
import uk.codecymru.primm.model.LoopInfo
import uk.codecymru.primm.model.LoopKind
import uk.codecymru.primm.model.ProgramError
import uk.codecymru.primm.model.RunOutcome
import uk.codecymru.primm.model.RuntimeEvent
import primm.shared.generated.resources.*
import uk.codecymru.primm.model.tr

data class TaggedEvent(val event: RuntimeEvent, val run: Int?)

object RuntimeEventDecoder {
    fun decode(json: String): RuntimeEvent? = decodeTagged(json)?.event

    fun decodeTagged(json: String): TaggedEvent? {
        val parsed = try {
            JsonReader.parse(json)
        } catch (e: JsonParseException) {
            return null
        }
        val event = parsed as? JsonObject ?: return null
        return decodeEvent(event)?.let { TaggedEvent(it, event.int("run")) }
    }

    private fun decodeEvent(event: JsonObject): RuntimeEvent? {
        return when (event.string("type")) {
            "loading" -> RuntimeEvent.Loading
            "ready" -> RuntimeEvent.Ready(event.string("channel") ?: "unknown")
            "loadFailed" -> RuntimeEvent.LoadFailed(event.string("message") ?: tr(Res.string.PYTHON_UNKNOWN_PROBLEM))
            "stdout" -> RuntimeEvent.Output(event.string("text") ?: "")
            "stderr" -> RuntimeEvent.ErrorOutput(event.string("text") ?: "")
            "inputRequest" -> RuntimeEvent.InputRequested(event.string("prompt") ?: "", event.int("line"))
            "paused" -> RuntimeEvent.Paused
            "traceStart" -> decodeTraceStart(event)
            "traceDone" -> decodeTraceDone(event)
            "analysis" -> (event["analysis"] as? JsonObject)?.let { RuntimeEvent.AnalysisReady(decodeAnalysis(it)) }
            "finished" -> decodeOutcome(event)?.let { outcome ->
                val files = (event["files"] as? JsonArray)?.items?.filterIsInstance<JsonObject>()
                RuntimeEvent.Finished(
                    outcome,
                    traceTruncated = event.boolean("traceTruncated") ?: false,
                    files = files?.mapNotNull { file ->
                        val name = file.string("name") ?: return@mapNotNull null
                        TextFile(name, file.string("text") ?: "")
                    },
                    truncatedFiles = files?.filter { it.boolean("truncated") == true }?.mapNotNull { it.string("name") }?.toSet()
                        ?: emptySet(),
                )
            }
            else -> null
        }
    }

    private fun decodeTraceStart(event: JsonObject): RuntimeEvent? {
        val index = event.int("index") ?: return null
        val line = event.int("line") ?: return null
        return RuntimeEvent.TraceStart(index, line, event.int("frame") ?: 0, event.string("scope") ?: "")
    }

    private fun decodeTraceDone(event: JsonObject): RuntimeEvent? {
        val index = event.int("index") ?: return null
        val changes = (event["changes"] as? JsonObject)?.fields
            ?.mapNotNull { (name, value) -> (value as? JsonString)?.let { name to it.value } }
            ?.toMap()
            ?: emptyMap()
        return RuntimeEvent.TraceDone(index, changes, event.string("output") ?: "")
    }

    fun decodeAnalysis(json: JsonObject): Analysis {
        if (json.boolean("ok") != true) return Analysis.NotParsed
        return Analysis(
            parsed = true,
            variables = json.strings("variables"),
            inputs = json.objects("inputs").mapNotNull { input ->
                val line = input.int("line") ?: return@mapNotNull null
                InputCall(line, input.string("prompt") ?: "", input.boolean("inLoop") ?: false)
            },
            conditions = json.objects("conditions").mapNotNull { condition ->
                val line = condition.int("line") ?: return@mapNotNull null
                val kind = when (condition.string("kind")) {
                    "if" -> ConditionKind.If
                    "elif" -> ConditionKind.Elif
                    "while" -> ConditionKind.While
                    else -> return@mapNotNull null
                }
                ConditionLine(line, kind, condition.int("bodyLine") ?: line, condition.string("scope") ?: "")
            },
            loops = json.objects("loops").mapNotNull { loop ->
                val line = loop.int("line") ?: return@mapNotNull null
                val kind = when (loop.string("kind")) {
                    "for" -> LoopKind.For
                    "while" -> LoopKind.While
                    else -> return@mapNotNull null
                }
                LoopInfo(
                    line = line,
                    kind = kind,
                    bodyLine = loop.int("bodyLine") ?: line,
                    endLine = loop.int("endLine") ?: line,
                    names = loop.strings("names"),
                    scope = loop.string("scope") ?: "",
                    overFile = loop.boolean("overFile") ?: false,
                )
            },
            printLines = (json["prints"] as? JsonArray)?.items?.mapNotNull { (it as? JsonNumber)?.value?.toInt() }
                ?: emptyList(),
            functions = json.strings("functions"),
            usesLists = json.boolean("lists") ?: false,
            fileUses = json.objects("files").mapNotNull { use ->
                val line = use.int("line") ?: return@mapNotNull null
                FileUse(line, use.string("name"), use.string("mode") ?: "?")
            },
            fileVariables = json.strings("fileVariables"),
            assignments = (json["assigned"] as? JsonObject)?.fields?.mapNotNull { (name, lines) ->
                (lines as? JsonArray)?.let { array -> name to array.items.mapNotNull { (it as? JsonNumber)?.value?.toInt() } }
            }?.toMap() ?: emptyMap(),
            literals = json.objects("literals").mapNotNull { literal ->
                val line = literal.int("line") ?: return@mapNotNull null
                val name = literal.string("name") ?: return@mapNotNull null
                Literal(line, name, literal.string("text") ?: return@mapNotNull null)
            },
            chains = json.objects("chains").mapNotNull { chain ->
                val head = chain.int("head") ?: return@mapNotNull null
                val branches = (chain["branches"] as? JsonArray)?.items?.mapNotNull { (it as? JsonNumber)?.value?.toInt() }
                    ?.takeIf { it.isNotEmpty() } ?: listOf(head)
                IfChain(head, branches, chain.boolean("hasElse") ?: false, chain.string("scope") ?: "")
            },
        )
    }

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)?.items?.mapNotNull { (it as? JsonString)?.value } ?: emptyList()

    private fun JsonObject.objects(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.items?.mapNotNull { it as? JsonObject } ?: emptyList()

    private fun decodeOutcome(event: JsonObject): RunOutcome? = when (event.string("outcome")) {
        "completed" -> RunOutcome.Completed
        "stopped" -> RunOutcome.Stopped(restarted = event.boolean("restarted") ?: false)
        "error" -> RunOutcome.Failed(
            ProgramError(
                type = event.string("errorType") ?: "Error",
                line = event.int("line"),
                message = event.string("message") ?: "",
                traceback = event.string("traceback") ?: "",
            ),
        )
        else -> null
    }
}
