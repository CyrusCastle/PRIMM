package uk.codecymru.primm.controller

import uk.codecymru.primm.model.ChallengeCheck
import uk.codecymru.primm.model.Concept
import uk.codecymru.primm.model.LocalText
import uk.codecymru.primm.model.Snippet
import uk.codecymru.primm.model.SnippetChallenge
import uk.codecymru.primm.model.SnippetLibrary
import uk.codecymru.primm.model.TextFile
import uk.codecymru.primm.model.Topic

class SnippetLibraryException(message: String) : Exception(message)

/**
 * ```
 * { "starter": "hello-times",
 *   "topics": [ { "id": "sequence", "title": { "en": "Sequence", "cy": "Dilyniant" } } ],
 *   "snippets": [ {
 *       "id": "11a", "title": { "en": "11a" }, "topic": "year-11",
 *       "concepts": ["ForLoop", "Selection", "Files"],
 *       "files": ["scores.txt"], "traceColumns": ["score", "highest"],
 *       "challenges": [
 *         { "text": { "en": "Also print the total." }, "answers": ["3"], "expect": "Total: 57" },
 *         { "text": { "en": "What goes wrong with negative scores?" } } ],
 *       "brief": { "en": "Make a file called temps.txt…" } } ] }
 * ```
 */
object SnippetLibraryReader {
    const val INDEX = "index.json"

    private val ID = Regex("[a-z0-9-]+")

    fun neededFiles(index: String): List<String> =
        snippetsOf(parseIndex(index)).flatMap { entry ->
            val id = entry.string("id") ?: return@flatMap emptyList()
            listOf("$id/$id.py") + entry.strings("files").map { "$id/$it" }
        }

    fun read(files: Map<String, String>): SnippetLibrary {
        val index = parseIndex(files[INDEX] ?: fail("There is no $INDEX."))
        val topics = index.objects("topics").map { topic ->
            val id = topic.string("id") ?: fail("A topic has no id.")
            Topic(id, text(topic, "title") ?: fail("Topic $id has no title."))
        }
        if (topics.isEmpty()) fail("There are no topics.")
        topics.groupBy { it.id }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { fail("Topic $it is listed twice.") }

        val snippets = snippetsOf(index).map { entry ->
            val id = entry.string("id") ?: fail("A program has no id.")
            if (!ID.matches(id)) fail("Program id $id may only use a-z, 0-9 and -.")
            fun file(name: String) = files["$id/$name"]?.let(::normalise) ?: fail("Program $id is missing $id/$name.")
            val topicId = entry.string("topic") ?: fail("Program $id has no topic.")
            Snippet(
                id = id,
                titles = text(entry, "title") ?: fail("Program $id has no title."),
                topic = topics.firstOrNull { it.id == topicId } ?: fail("Program $id has the unknown topic $topicId."),
                code = file("$id.py").let { if (it.endsWith("\n")) it else it + "\n" },
                concepts = entry.strings("concepts").map { name ->
                    Concept.entries.firstOrNull { it.name == name } ?: fail("Program $id has the unknown concept $name.")
                }.toSet(),
                challengeTexts = entry.objects("challenges").mapIndexed { i, challenge ->
                    SnippetChallenge(
                        text = text(challenge, "text") ?: fail("Challenge ${i + 1} of $id has no text."),
                        check = challenge.string("expect")?.let { ChallengeCheck(challenge.strings("answers"), it) },
                    )
                },
                brief = text(entry, "brief"),
                files = entry.strings("files").map { TextFile(it, file(it)) },
                traceColumns = entry.strings("traceColumns"),
            )
        }
        if (snippets.isEmpty()) fail("There are no programs.")
        snippets.groupBy { it.id }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { fail("Program $it is listed twice.") }

        val starter = index.string("starter") ?: snippets.first().id
        if (snippets.none { it.id == starter }) fail("The starter program $starter isn't in the library.")
        return SnippetLibrary(topics, snippets, starter)
    }

    private fun parseIndex(text: String): JsonObject =
        try {
            JsonReader.parse(text) as? JsonObject ?: fail("$INDEX isn't a JSON object.")
        } catch (e: JsonParseException) {
            fail("$INDEX isn't valid JSON: ${e.message}")
        }

    private fun snippetsOf(index: JsonObject): List<JsonObject> = index.objects("snippets")

    /** A string (English) or an object of language code to text. */
    private fun text(json: JsonObject, key: String): LocalText? = when (val value = json[key]) {
        is JsonString -> LocalText.english(value.value)
        is JsonObject -> value.fields.mapNotNull { (language, text) -> (text as? JsonString)?.let { language to it.value } }
            .toMap().takeIf { it.isNotEmpty() }?.let(::LocalText)
        else -> null
    }

    private fun normalise(text: String): String = text.replace("\r\n", "\n")

    private fun fail(message: String): Nothing = throw SnippetLibraryException(message)
}
