package uk.codecymru.primm.model

import org.jetbrains.compose.resources.StringResource
import primm.shared.generated.resources.*

enum class SelfMark(private val titleRes: StringResource) {
    Right(Res.string.SELF_MARK_RIGHT),
    Partly(Res.string.SELF_MARK_PARTLY),
    Wrong(Res.string.SELF_MARK_WRONG);

    val title: String get() = tr(titleRes)
}

data class Investigation(
    val reflections: Map<String, String> = emptyMap(),
    val revealed: Set<String> = emptySet(),
    val summaryMark: SelfMark? = null,
    val loopMarks: Map<Int, SelfMark> = emptyMap(),
    val errorMark: SelfMark? = null,
    val rewrittenSummary: String = "",
    val learned: String = "",
) {
    fun reflection(key: String): String = reflections[key] ?: ""

    fun isExplained(key: String): Boolean = reflection(key).isNotBlank()

    fun withReflection(key: String, text: String): Investigation = copy(reflections = reflections + (key to text))

    fun reveal(key: String): Investigation = if (isExplained(key)) copy(revealed = revealed + key) else this
}
