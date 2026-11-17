package uk.codecymru.primm.controller

// TODO is this code dupe?
sealed interface JsonValue

data class JsonObject(val fields: Map<String, JsonValue>) : JsonValue {
    operator fun get(key: String): JsonValue? = fields[key]

    fun string(key: String): String? = (fields[key] as? JsonString)?.value

    fun int(key: String): Int? = (fields[key] as? JsonNumber)?.value?.toInt()

    fun boolean(key: String): Boolean? = (fields[key] as? JsonBoolean)?.value
}

data class JsonArray(val items: List<JsonValue>) : JsonValue

data class JsonString(val value: String) : JsonValue

data class JsonNumber(val value: Double) : JsonValue

data class JsonBoolean(val value: Boolean) : JsonValue

data object JsonNull : JsonValue

class JsonParseException(message: String) : Exception(message)

object JsonReader {
    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        parser.skipWhitespace()
        val value = parser.readValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) parser.fail("Unexpected text after the JSON value")
        return value
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun atEnd() = pos >= text.length

        fun fail(message: String): Nothing = throw JsonParseException("$message at position $pos")

        fun skipWhitespace() {
            while (pos < text.length && text[pos] in " \t\r\n") pos++
        }

        fun readValue(): JsonValue {
            if (atEnd()) fail("Unexpected end of JSON")
            return when (val c = text[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> JsonString(readString())
                't' -> readKeyword("true", JsonBoolean(true))
                'f' -> readKeyword("false", JsonBoolean(false))
                'n' -> readKeyword("null", JsonNull)
                else -> if (c == '-' || c in '0'..'9') readNumber() else fail("Unexpected character '$c'")
            }
        }

        private fun readKeyword(word: String, value: JsonValue): JsonValue {
            if (!text.startsWith(word, pos)) fail("Expected $word")
            pos += word.length
            return value
        }

        private fun readObject(): JsonObject {
            pos++ // {
            val fields = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return JsonObject(fields)
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') fail("Expected a field name")
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                fields[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return JsonObject(fields)
                    }
                    else -> fail("Expected ',' or '}'")
                }
            }
        }

        private fun readArray(): JsonArray {
            pos++ // [
            val items = ArrayList<JsonValue>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return JsonArray(items)
            }
            while (true) {
                skipWhitespace()
                items.add(readValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return JsonArray(items)
                    }
                    else -> fail("Expected ',' or ']'")
                }
            }
        }

        private fun readString(): String {
            pos++ // opening quote
            val out = StringBuilder()
            while (true) {
                if (atEnd()) fail("Unterminated string")
                val c = text[pos++]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> {
                        if (atEnd()) fail("Unterminated escape")
                        when (val e = text[pos++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) fail("Short unicode escape")
                                val hex = text.substring(pos, pos + 4)
                                if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                                    fail("Bad unicode escape '$hex'")
                                }
                                out.append(Char(hex.toInt(16)))
                                pos += 4
                            }
                            else -> fail("Unknown escape '\\$e'")
                        }
                    }
                    c < ' ' -> fail("Control character in string")
                    else -> out.append(c)
                }
            }
        }

        private fun readNumber(): JsonNumber {
            val start = pos
            if (peek() == '-') pos++
            if (peek() == '0') {
                pos++
            } else if (peek() in '1'..'9') {
                while (peek() in '0'..'9') pos++
            } else {
                fail("Bad number")
            }
            if (peek() == '.') {
                pos++
                if (peek() !in '0'..'9') fail("Bad number")
                while (peek() in '0'..'9') pos++
            }
            if (peek() == 'e' || peek() == 'E') {
                pos++
                if (peek() == '+' || peek() == '-') pos++
                if (peek() !in '0'..'9') fail("Bad number")
                while (peek() in '0'..'9') pos++
            }
            val number = text.substring(start, pos).toDoubleOrNull() ?: fail("Bad number")
            return JsonNumber(number)
        }

        private fun peek(): Char = if (pos < text.length) text[pos] else '\u0000'

        private fun expect(c: Char) {
            if (peek() != c) fail("Expected '$c'")
            pos++
        }
    }
}
