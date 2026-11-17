package uk.codecymru.primm.model

enum class RuntimeStatus {
    Loading,
    Ready,
    Running,
    WaitingForInput,
    Paused,
    Stopping,
    Unavailable,
}

data class ProgramError(
    val type: String,
    val line: Int?,
    val message: String,
    val traceback: String,
)

sealed interface RunOutcome {
    data object Completed : RunOutcome

    data class Stopped(val restarted: Boolean) : RunOutcome

    data class Failed(val error: ProgramError) : RunOutcome
}

sealed interface RuntimeEvent {
    data object Loading : RuntimeEvent

    data class Ready(val channel: String) : RuntimeEvent

    data class LoadFailed(val message: String) : RuntimeEvent

    data class Output(val text: String) : RuntimeEvent

    data class ErrorOutput(val text: String) : RuntimeEvent

    data class InputRequested(val prompt: String, val line: Int? = null) : RuntimeEvent

    data object Paused : RuntimeEvent

    data class TraceStart(val index: Int, val line: Int, val frame: Int, val scope: String) : RuntimeEvent

    data class TraceDone(val index: Int, val changes: Map<String, String>, val output: String) : RuntimeEvent

    data class AnalysisReady(val analysis: Analysis) : RuntimeEvent

    data class Finished(
        val outcome: RunOutcome,
        val traceTruncated: Boolean = false,
        val files: List<TextFile>? = null,
        val truncatedFiles: Set<String> = emptySet(),
    ) : RuntimeEvent
}
