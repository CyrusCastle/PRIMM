package uk.codecymru.primm.model.web

import uk.codecymru.primm.model.ProgramSource

data class LaunchOptions(
    val snippetId: String? = null,
    val sourceUrl: String? = null,
    val unlockEditor: Boolean = false,
    val gateRun: Boolean? = null,
    val openRows: Boolean = false,
    val revealAll: Boolean = false,
    val makeBriefOnly: Boolean = false,
    val stopAtInvestigate: Boolean = false,
) {
    companion object {
        fun parse(query: String): LaunchOptions {
            val params = QueryString.parse(query)
            return LaunchOptions(
                snippetId = params["snippet"]?.trim()?.takeIf { it.isNotEmpty() },
                sourceUrl = params["src"]?.trim()?.takeIf { it.isNotEmpty() },
                unlockEditor = params["unlock"]?.let { isTrue(it) } ?: false,
                gateRun = params["gate"]?.let { !isOff(it) },
                openRows = params["rows"]?.let { it.equals("open", ignoreCase = true) || isTrue(it) } ?: false,
                revealAll = params["reveal"]?.let { it.equals("all", ignoreCase = true) || isTrue(it) } ?: false,
                makeBriefOnly = params["make"]?.equals("brief", ignoreCase = true) ?: false,
                stopAtInvestigate = params["stop"]?.equals("investigate", ignoreCase = true) ?: false,
            )
        }

        fun queryFor(
            source: ProgramSource,
            unlockEditor: Boolean,
            gateOff: Boolean = false,
            openRows: Boolean = false,
            revealAll: Boolean = false,
            makeBriefOnly: Boolean = false,
            stopAtInvestigate: Boolean = false,
        ): String {
            val params = buildList {
                when (source) {
                    is ProgramSource.Library -> add("snippet" to source.snippetId)
                    is ProgramSource.Url -> add("src" to source.href)
                }
                if (unlockEditor) add("unlock" to "1")
                if (gateOff) add("gate" to "off")
                if (openRows) add("rows" to "open")
                if (revealAll) add("reveal" to "all")
                if (makeBriefOnly) add("make" to "brief")
                if (stopAtInvestigate) add("stop" to "investigate")
            }
            return QueryString.build(params)
        }

        private fun isOff(value: String) =
            value == "0" || value.equals("off", ignoreCase = true) || value.equals("false", ignoreCase = true)

        private fun isTrue(value: String) = value.isEmpty() || value == "1" || value.equals("true", ignoreCase = true)
    }
}

object QueryString {
    fun parse(query: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (pair in query.removePrefix("?").split('&')) {
            if (pair.isEmpty()) continue
            val equals = pair.indexOf('=')
            val name = decode(if (equals < 0) pair else pair.substring(0, equals))
            val value = if (equals < 0) "" else decode(pair.substring(equals + 1))
            if (name !in result) result[name] = value
        }
        return result
    }

    fun build(params: List<Pair<String, String>>): String =
        params.joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }

    fun decode(text: String): String {
        val bytes = ArrayList<Byte>(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '+' -> {
                    bytes.add(' '.code.toByte())
                    i++
                }
                c == '%' && isHex(text, i + 1) && isHex(text, i + 2) -> {
                    bytes.add(text.substring(i + 1, i + 3).toInt(16).toByte())
                    i += 3
                }
                else -> {
                    val end = if (c.isHighSurrogate() && i + 1 < text.length) i + 2 else i + 1
                    text.substring(i, end).encodeToByteArray().forEach { bytes.add(it) }
                    i = end
                }
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    fun encode(text: String): String = buildString {
        for (byte in text.encodeToByteArray()) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            if (b < 0x80 && (c.isLetterOrDigit() || c in "-_.~:/@")) {
                append(c)
            } else {
                append('%')
                append(HEX[b shr 4])
                append(HEX[b and 0x0F])
            }
        }
    }

    private fun isHex(text: String, index: Int): Boolean =
        index < text.length && text[index].let { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    private const val HEX = "0123456789ABCDEF"
}
