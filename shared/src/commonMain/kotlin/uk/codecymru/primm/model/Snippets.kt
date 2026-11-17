package uk.codecymru.primm.model

import org.jetbrains.compose.resources.StringResource
import primm.shared.generated.resources.*

data class LocalText(val byLanguage: Map<String, String>) {
    val text: String
        get() = byLanguage[Strings.language] ?: byLanguage["en"] ?: byLanguage.values.firstOrNull().orEmpty()

    override fun toString(): String = text

    companion object {
        fun english(text: String) = LocalText(mapOf("en" to text))
    }
}

data class Topic(val id: String, val titles: LocalText) {
    val title: String get() = titles.text
}

enum class Concept(private val titleRes: StringResource) {
    Input(Res.string.CONCEPT_INPUT),
    Selection(Res.string.CONCEPT_SELECTION),
    ForLoop(Res.string.CONCEPT_FOR_LOOP),
    WhileLoop(Res.string.CONCEPT_WHILE_LOOP),
    Lists(Res.string.CONCEPT_LIST),
    Functions(Res.string.CONCEPT_FUNCTION),
    Files(Res.string.CONCEPT_FILE);

    val title: String get() = tr(titleRes)
}

data class Challenge(val text: String, val check: ChallengeCheck? = null)

data class SnippetChallenge(val text: LocalText, val check: ChallengeCheck? = null) {
    val challenge: Challenge get() = Challenge(text.text, check)
}

data class Snippet(
    val id: String,
    val titles: LocalText,
    val topic: Topic,
    val code: String,
    val concepts: Set<Concept>,
    val challengeTexts: List<SnippetChallenge> = emptyList(),
    val brief: LocalText? = null,
    val files: List<TextFile> = emptyList(),
    val traceColumns: List<String> = emptyList(),
) {
    val title: String get() = titles.text

    val challenges: List<Challenge> get() = challengeTexts.map { it.challenge }

    val makeBrief: String? get() = brief?.text
}

data class SnippetLibrary(val topics: List<Topic>, val snippets: List<Snippet>, val starterId: String) {
    val starter: Snippet get() = byId(starterId) ?: snippets.first()

    fun topic(id: String): Topic? = topics.firstOrNull { it.id == id }

    fun byId(id: String): Snippet? = snippets.firstOrNull { it.id == id.trim().lowercase() }

    fun search(query: String): List<Snippet> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return snippets
        return snippets.filter { snippet ->
            val haystack = "${snippet.title} ${snippet.topic.title} ${snippet.code}".lowercase()
            words.all { it in haystack }
        }
    }

    fun grouped(among: List<Snippet> = snippets): List<Pair<Topic, List<Snippet>>> =
        topics.mapNotNull { topic ->
            among.filter { it.topic == topic }.takeIf { it.isNotEmpty() }?.let { topic to it }
        }
}
