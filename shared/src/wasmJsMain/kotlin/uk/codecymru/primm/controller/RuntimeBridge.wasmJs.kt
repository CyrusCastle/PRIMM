package uk.codecymru.primm.controller

// Global functions defined in resources/primm/bridge.js.
private external fun primmRuntimeStart(onEvent: (String) -> Unit)
private external fun primmRuntimeRun(code: String, trace: Boolean, step: Boolean, filesJson: String, runId: Int)
private external fun primmRuntimeSendInput(text: String)
private external fun primmRuntimeStep(action: String)
private external fun primmRuntimeStop()
private external fun primmRuntimeAnalyse(code: String)

internal actual fun bridgeRuntimeStart(onEvent: (String) -> Unit) {
    primmRuntimeStart(onEvent)
}

internal actual fun bridgeRuntimeRun(code: String, trace: Boolean, step: Boolean, filesJson: String, runId: Int) {
    primmRuntimeRun(code, trace, step, filesJson, runId)
}

internal actual fun bridgeRuntimeSendInput(text: String) {
    primmRuntimeSendInput(text)
}

internal actual fun bridgeRuntimeStep(action: String) {
    primmRuntimeStep(action)
}

internal actual fun bridgeRuntimeStop() {
    primmRuntimeStop()
}

internal actual fun bridgeRuntimeAnalyse(code: String) {
    primmRuntimeAnalyse(code)
}
