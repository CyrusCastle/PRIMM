package uk.codecymru.primm.model

import org.jetbrains.compose.resources.StringResource
import primm.shared.generated.resources.*

sealed interface ProgramSource {
    data class Library(val snippetId: String) : ProgramSource

    data class Url(val href: String) : ProgramSource
}

data class Program(
    val title: String,
    val code: String,
    val source: ProgramSource,
    val challenges: List<Challenge> = emptyList(),
    val brief: String = "",
    val files: List<TextFile> = emptyList(),
    val traceColumns: List<String> = emptyList(),
) {
    companion object {
        fun fromSnippet(snippet: Snippet) = Program(
            snippet.title,
            snippet.code,
            ProgramSource.Library(snippet.id),
            challenges = snippet.challenges,
            brief = snippet.makeBrief ?: "",
            files = snippet.files,
            traceColumns = snippet.traceColumns,
        )
    }
}

enum class Stage(private val titleRes: StringResource) {
    Predict(Res.string.STAGE_PREDICT),
    Run(Res.string.STAGE_RUN),
    Investigate(Res.string.STAGE_INVESTIGATE),
    Modify(Res.string.STAGE_MODIFY),
    Make(Res.string.STAGE_MAKE);

    val title: String get() = tr(titleRes)

    val locksCode: Boolean get() = this == Predict || this == Run || this == Investigate
}

data class TeacherSettings(
    val unlockEditor: Boolean = false,
    val gateRun: Boolean = true,
    val openRows: Boolean = false,
    val oneRowAtATime: Boolean = true,
    val revealAll: Boolean = false,
    val makeBriefOnly: Boolean = false,
    val stopAtInvestigate: Boolean = false,
)

data class RunSettings(
    val autoFill: Boolean = true,
    val usePredictedInputs: Boolean = true,
)

data class Preferences(
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val codeFraction: Float = DEFAULT_CODE_FRACTION,
    val editorFraction: Float = DEFAULT_EDITOR_FRACTION,
    val autosave: Boolean = false,
) {
    fun withFontSize(size: Int) = copy(fontSize = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE))

    fun withCodeFraction(fraction: Float) = copy(codeFraction = fraction.coerceIn(0.3f, 0.8f))

    fun withEditorFraction(fraction: Float) = copy(editorFraction = fraction.coerceIn(0.2f, 0.85f))

    companion object {
        const val DEFAULT_FONT_SIZE = 15
        const val MIN_FONT_SIZE = 11
        const val MAX_FONT_SIZE = 32
        const val FONT_STEP = 2
        const val DEFAULT_CODE_FRACTION = 0.6f
        const val DEFAULT_EDITOR_FRACTION = 0.65f
    }
}
