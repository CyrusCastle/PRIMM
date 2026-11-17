package uk.codecymru.primm.controller

import uk.codecymru.primm.model.TextFile

import uk.codecymru.primm.model.RuntimeEvent

interface PythonRuntime {
    fun start(onEvent: (event: RuntimeEvent, run: Int?) -> Unit)

    fun run(code: String, runId: Int, step: Boolean = false, trace: Boolean = true, files: List<TextFile>? = null)

    fun sendInput(text: String)

    fun step(toEnd: Boolean)

    fun stop()

    fun analyse(code: String)
}

class BridgePythonRuntime : PythonRuntime {
    override fun start(onEvent: (event: RuntimeEvent, run: Int?) -> Unit) {
        bridgeRuntimeStart { json ->
            val tagged = RuntimeEventDecoder.decodeTagged(json)
            if (tagged != null) {
                onEvent(tagged.event, tagged.run)
            } else {
                println("PRIMM: ignored an unrecognised runtime event: $json")
            }
        }
    }

    override fun run(code: String, runId: Int, step: Boolean, trace: Boolean, files: List<TextFile>?) {
        val filesJson = files?.let { list ->
            JsonWriter.write(jsonArray(list.map { json("name" to jsonOf(it.name), "text" to jsonOf(it.text)) }))
        } ?: ""
        bridgeRuntimeRun(code, trace, step && trace, filesJson, runId)
    }

    override fun sendInput(text: String) {
        bridgeRuntimeSendInput(text)
    }

    override fun step(toEnd: Boolean) {
        bridgeRuntimeStep(if (toEnd) "continue" else "next")
    }

    override fun stop() {
        bridgeRuntimeStop()
    }

    override fun analyse(code: String) {
        bridgeRuntimeAnalyse(code)
    }
}
