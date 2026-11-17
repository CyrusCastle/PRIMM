package uk.codecymru.primm.model

import uk.codecymru.primm.model.web.QueryString
import primm.shared.generated.resources.*

data class WebAddress(
    val scheme: String,
    val host: String,
    val port: Int?,
    val path: String,
    val query: String,
) {
    val segments: List<String> get() = path.split('/').filter { it.isNotEmpty() }

    override fun toString(): String = buildString {
        append(scheme).append("://").append(host)
        if (port != null) append(':').append(port)
        append(path)
        if (query.isNotEmpty()) append('?').append(query)
    }

    companion object {
        private val PATTERN = Regex(
            """^(https?)://([A-Za-z0-9.-]+)(?::(\d{1,5}))?(/[^?#\s]*)?(?:\?([^#\s]*))?(?:#\S*)?$""",
            RegexOption.IGNORE_CASE,
        )

        fun parse(text: String): WebAddress? {
            val match = PATTERN.matchEntire(text.trim()) ?: return null
            val (scheme, host, port, path, query) = match.destructured
            if (host.startsWith('.') || host.endsWith('.') || ".." in host) return null
            return WebAddress(
                scheme = scheme.lowercase(),
                host = host.lowercase(),
                port = port.toIntOrNull(),
                path = path.ifEmpty { "/" },
                query = query,
            )
        }
    }
}

sealed interface LinkCheck {
    data class Fetch(val fetchUrl: String) : LinkCheck

    data class Rejected(val reason: String) : LinkCheck
}

sealed interface ContentCheck {
    data class Accepted(val code: String) : ContentCheck

    data class Rejected(val reason: String) : ContentCheck
}

object ProgramUrls {
    const val MAX_BYTES = 50 * 1024

    val EMPTY_LINK: String get() = tr(Res.string.LINK_EMPTY_LINK)
    val NOT_A_LINK: String get() = tr(Res.string.LINK_NOT_A_LINK)
    val GOOGLE_DRIVE: String get() = tr(Res.string.LINK_GOOGLE_DRIVE)
    val ONEDRIVE: String get() = tr(Res.string.LINK_ONEDRIVE)
    val GITHUB_NOT_A_FILE: String get() = tr(Res.string.LINK_GITHUB_NOT_A_FILE)
    val GIST_NEEDS_RAW: String get() = tr(Res.string.LINK_GIST_NEEDS_RAW)
    val BLOCKED: String get() = tr(Res.string.LINK_BLOCKED)
    val TOO_LARGE: String get() = tr(Res.string.LINK_TOO_LARGE)
    val WEB_PAGE: String get() = tr(Res.string.LINK_WEB_PAGE)
    val NOT_TEXT: String get() = tr(Res.string.LINK_NOT_TEXT)
    val EMPTY_FILE: String get() = tr(Res.string.LINK_EMPTY_FILE)

    fun check(link: String): LinkCheck {
        val text = link.trim()
        if (text.isEmpty()) return LinkCheck.Rejected(EMPTY_LINK)
        val withScheme = if (HAS_SCHEME.containsMatchIn(text)) text else "https://$text"
        val address = WebAddress.parse(withScheme) ?: return LinkCheck.Rejected(NOT_A_LINK)
        val host = address.host.removePrefix("www.")
        val parts = address.segments
        return when {
            host == "drive.google.com" || host == "docs.google.com" -> LinkCheck.Rejected(GOOGLE_DRIVE)
            host == "onedrive.live.com" || host == "1drv.ms" || host == "sharepoint.com" ||
                host.endsWith(".sharepoint.com") -> LinkCheck.Rejected(ONEDRIVE)
            host == "github.com" -> githubFile(parts)
            host == "gist.github.com" -> gistFile(parts)
            else -> LinkCheck.Fetch(address.toString())
        }
    }

    private fun githubFile(parts: List<String>): LinkCheck {
        if (parts.size >= 5 && (parts[2] == "blob" || parts[2] == "raw")) {
            val rest = (listOf(parts[0], parts[1]) + parts.drop(3)).joinToString("/")
            return LinkCheck.Fetch("https://raw.githubusercontent.com/$rest")
        }
        return LinkCheck.Rejected(GITHUB_NOT_A_FILE)
    }

    private fun gistFile(parts: List<String>): LinkCheck = when {
        parts.size == 2 -> LinkCheck.Fetch("https://gist.githubusercontent.com/${parts[0]}/${parts[1]}/raw")
        parts.size >= 3 && parts[2] == "raw" ->
            LinkCheck.Fetch("https://gist.githubusercontent.com/${parts.joinToString("/")}")
        else -> LinkCheck.Rejected(GIST_NEEDS_RAW)
    }

    fun checkContent(text: String, contentType: String): ContentCheck {
        val code = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val start = code.trimStart().take(20).lowercase()
        return when {
            contentType.lowercase().contains("text/html") ||
                start.startsWith("<!doctype") || start.startsWith("<html") ->
                ContentCheck.Rejected(WEB_PAGE)
            code.contains('\u0000') -> ContentCheck.Rejected(NOT_TEXT)
            code.isBlank() -> ContentCheck.Rejected(EMPTY_FILE)
            else -> ContentCheck.Accepted(if (code.endsWith("\n")) code else code + "\n")
        }
    }

    fun failureMessage(failure: FetchFailure): String = when (failure) {
        FetchFailure.Blocked -> BLOCKED
        FetchFailure.TooLarge -> TOO_LARGE
        is FetchFailure.Http -> when (failure.status) {
            404 -> tr(Res.string.LINK_NOT_FOUND)
            401, 403 -> tr(Res.string.LINK_PRIVATE, failure.status)
            else -> tr(Res.string.LINK_SERVER_ERROR, failure.status)
        }
    }

    fun titleFor(link: String): String {
        val address = WebAddress.parse(link.trim()) ?: WebAddress.parse("https://${link.trim()}")
        val name = address?.segments?.lastOrNull()?.let { QueryString.decode(it) }
        return if (name.isNullOrBlank() || name == "raw") tr(Res.string.LINK_PROGRAM_TITLE) else name
    }

    private val HAS_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
}

sealed interface FetchFailure {
    data object Blocked : FetchFailure

    data object TooLarge : FetchFailure

    data class Http(val status: Int) : FetchFailure
}
