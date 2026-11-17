package uk.codecymru.primm.model

import org.jetbrains.compose.resources.StringResource

// Unfortunately just to complicate things we need to access our strings outside of composables as well
object Strings {
    private var table: Map<String, String> = englishStrings

    var language: String = "en"
        private set

    fun use(loaded: Map<String, String>, language: String = "en") {
        table = englishStrings + loaded
        this.language = language
    }

    fun get(resource: StringResource, vararg args: Any?): String = format(table[resource.key] ?: resource.key, args)

    fun format(template: String, args: Array<out Any?>): String =
        PLACEHOLDER.replace(template) { match ->
            args.getOrNull(match.groupValues[1].toInt() - 1)?.toString() ?: match.value
        }

    private val PLACEHOLDER = Regex("""%(\d+)\$[ds]""")
}

fun tr(resource: StringResource, vararg args: Any?): String = Strings.get(resource, *args)

// TODO improve this for other locales
fun plural(count: Int, one: StringResource, other: StringResource): String = tr(if (count == 1) one else other, count)
