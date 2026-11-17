package uk.codecymru.primm.model

import org.jetbrains.compose.resources.StringResource
import primm.shared.generated.resources.*

enum class Language(val iso: String, private val nameRes: StringResource) {
    ENGLISH("en", Res.string.LANGUAGE_ENGLISH),
    WELSH("cy", Res.string.LANGUAGE_WELSH);

    val title: String get() = tr(nameRes)

    val other: Language get() = if (this == ENGLISH) WELSH else ENGLISH

    companion object {
        fun fromIso(iso: String?): Language? = entries.firstOrNull { it.iso == iso?.trim()?.lowercase() }

        fun preferred(chosen: String?, browserLanguages: List<String>): Language =
            fromIso(chosen)
                ?: browserLanguages.firstNotNullOfOrNull { fromIso(it.substringBefore('-').substringBefore('_')) }
                ?: ENGLISH
    }
}
