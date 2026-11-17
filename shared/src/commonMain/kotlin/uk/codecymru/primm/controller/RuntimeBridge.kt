package uk.codecymru.primm.controller

// The Python runtime functions in webApp/src/webMain/resources/primm/bridge.js.

internal expect fun bridgeRuntimeStart(onEvent: (String) -> Unit)

internal expect fun bridgeRuntimeRun(code: String, trace: Boolean, step: Boolean, filesJson: String, runId: Int)

internal expect fun bridgeRuntimeSendInput(text: String)

internal expect fun bridgeRuntimeStep(action: String)

internal expect fun bridgeRuntimeStop()

internal expect fun bridgeRuntimeAnalyse(code: String)
