package uk.codecymru.primm.model

import primm.shared.generated.resources.*

/**
 * Designed for Python 3.13 (Pyodide 0.29).
 */
object FriendlyErrors {
    /** Returns null if we can't say anything more helpful than Python's own message */
    fun explain(error: ProgramError): String? {
        val m = error.message
        return when (error.type) {
            "NameError" -> nameError(m, error.traceback)
            "TypeError" -> typeError(m)
            "ValueError" -> valueError(m)
            "ZeroDivisionError" -> tr(Res.string.ERROR_DIVIDE_BY_ZERO)
            "IndexError" -> indexError(m)
            "KeyError" -> tr(Res.string.ERROR_NO_SUCH_KEY, m)
            "AttributeError" -> attributeError(m)
            "SyntaxError" -> syntaxError(m)
            "IndentationError" -> indentationError(m)
            "TabError" -> tr(Res.string.ERROR_TABS_AND_SPACES)
            "RecursionError" -> tr(Res.string.ERROR_RECURSION)
            "FileNotFoundError" -> fileNotFound(m)
            "FileExistsError" -> FILE_NAME.find(m)?.let {
                tr(Res.string.ERROR_FILE_EXISTS_NAMED, it.groupValues[1])
            } ?: tr(Res.string.ERROR_FILE_EXISTS)
            "IsADirectoryError" -> tr(Res.string.ERROR_OPENED_A_FOLDER)
            "PermissionError" -> tr(Res.string.ERROR_PERMISSION)
            "UnsupportedOperation" -> unsupportedOperation(m)
            "ModuleNotFoundError" -> NO_MODULE.find(m)?.let {
                tr(Res.string.ERROR_NO_MODULE, it.groupValues[1])
            }
            "ConnectionError" -> when {
                m.startsWith("could not reach the weather service") -> tr(Res.string.ERROR_WEATHER_UNREACHABLE)
                WEATHER_ANSWERED.matches(m) -> tr(Res.string.ERROR_WEATHER_REFUSED, WEATHER_ANSWERED.find(m)!!.groupValues[1])
                else -> null
            }
            else -> null
        }
    }

    private fun nameError(m: String, traceback: String): String? {
        val name = NOT_DEFINED.find(m)?.groupValues?.get(1) ?: return null
        val lower = name.lowercase()
        if (lower == "true" || lower == "false" || lower == "none") {
            val fixed = lower.replaceFirstChar { it.uppercaseChar() }
            return tr(Res.string.ERROR_TRUE_FALSE_NONE_CAPITALS, fixed)
        }
        val suggestion = DID_YOU_MEAN.find(traceback)?.groupValues?.get(1)
        return if (suggestion != null) {
            tr(Res.string.ERROR_UNKNOWN_NAME_SUGGESTION, name, suggestion)
        } else {
            tr(Res.string.ERROR_UNKNOWN_NAME, name)
        }
    }

    private fun typeError(m: String): String? {
        CONCATENATE.find(m)?.let {
            return tr(Res.string.ERROR_JOIN_TEXT_AND, describe(it.groupValues[1]))
        }
        UNSUPPORTED_OPERAND.find(m)?.let {
            val (op, a, b) = it.destructured
            return when {
                isTextAndNumber(a, b) ->
                    tr(Res.string.ERROR_TEXT_AND_NUMBER, op)
                a == "NoneType" || b == "NoneType" ->
                    tr(Res.string.ERROR_NONE_VALUE)
                else -> tr(Res.string.ERROR_OPERATOR_CANT_BE_USED, op, describe(a), describe(b))
            }
        }
        NOT_SUPPORTED_BETWEEN.find(m)?.let {
            val (op, a, b) = it.destructured
            return if (isTextAndNumber(a, b)) {
                tr(Res.string.ERROR_COMPARED_TEXT_AND_NUMBER)
            } else {
                tr(Res.string.ERROR_CANT_COMPARE, describe(a), describe(b), op)
            }
        }
        NOT_CALLABLE.find(m)?.let {
            val type = it.groupValues[1]
            return if (type == "int" || type == "float") {
                tr(Res.string.ERROR_NUMBER_CALLED)
            } else {
                tr(Res.string.ERROR_NOT_A_FUNCTION, describe(type))
            }
        }
        NOT_SUBSCRIPTABLE.find(m)?.let {
            return tr(Res.string.ERROR_NOT_SUBSCRIPTABLE, describe(it.groupValues[1]))
        }
        NOT_ITERABLE.find(m)?.let {
            val type = it.groupValues[1]
            return if (type == "int") {
                tr(Res.string.ERROR_LOOP_OVER_NUMBER)
            } else {
                tr(Res.string.ERROR_LOOP_OVER, describe(type))
            }
        }
        TOO_MANY_ARGUMENTS.find(m)?.let {
            val (function, expected, given) = it.destructured
            val values = if (expected == "1") tr(Res.string.ERROR_ONE_VALUE) else tr(Res.string.ERROR_N_VALUES, expected)
            return tr(Res.string.ERROR_TOO_MANY_ARGUMENTS, function, values, given)
        }
        MISSING_ARGUMENTS.find(m)?.let {
            val (function, names) = it.destructured
            return tr(Res.string.ERROR_MISSING_ARGUMENTS, function, names)
        }
        WRITE_ARGUMENT.find(m)?.let {
            return tr(Res.string.ERROR_WRITE_NEEDS_TEXT, describe(it.groupValues[1]))
        }
        if (m.startsWith("can't multiply sequence by non-int")) {
            return tr(Res.string.ERROR_MULTIPLY_SEQUENCE)
        }
        return null
    }

    private fun fileNotFound(m: String): String {
        val name = FILE_NAME.find(m)?.groupValues?.get(1)
            ?: return tr(Res.string.ERROR_FILE_NOT_FOUND)
        return if ('/' in name || '\\' in name) {
            tr(Res.string.ERROR_FILE_IN_FOLDER, name.substringAfterLast('/').substringAfterLast('\\'))
        } else {
            tr(Res.string.ERROR_FILE_NOT_FOUND_NAMED, name)
        }
    }

    private fun unsupportedOperation(m: String): String? = when {
        m.startsWith("not readable") ->
            tr(Res.string.ERROR_FILE_NOT_READABLE)
        m.startsWith("not writable") ->
            tr(Res.string.ERROR_FILE_NOT_WRITABLE)
        else -> null
    }

    private fun valueError(m: String): String? {
        if (m.startsWith("I/O operation on closed file")) {
            return tr(Res.string.ERROR_FILE_CLOSED)
        }
        if (m.startsWith("invalid mode")) {
            return tr(Res.string.ERROR_FILE_BAD_MODE)
        }
        UNKNOWN_PLACE.find(m)?.let {
            return tr(Res.string.ERROR_UNKNOWN_PLACE, it.groupValues[1])
        }
        INVALID_INT.find(m)?.let {
            val value = it.groupValues[1]
            return when {
                value.isBlank() -> tr(Res.string.ERROR_INT_BLANK)
                DECIMAL.matches(value.trim()) -> tr(Res.string.ERROR_INT_DECIMAL, value)
                else -> tr(Res.string.ERROR_INT_INVALID, value)
            }
        }
        INVALID_FLOAT.find(m)?.let {
            val value = it.groupValues[1]
            return if (value.isBlank()) {
                tr(Res.string.ERROR_FLOAT_BLANK)
            } else {
                tr(Res.string.ERROR_FLOAT_INVALID, value)
            }
        }
        return null
    }

    private fun indexError(m: String): String = when {
        m.startsWith("list index") ->
            tr(Res.string.ERROR_LIST_INDEX)
        m.startsWith("string index") ->
            tr(Res.string.ERROR_STRING_INDEX)
        else -> tr(Res.string.ERROR_INDEX)
    }

    private fun attributeError(m: String): String? {
        val (type, attribute) = NO_ATTRIBUTE.find(m)?.destructured ?: return null
        return tr(Res.string.ERROR_NO_ATTRIBUTE, describe(type).replaceFirstChar { it.uppercaseChar() }, attribute)
    }

    private fun syntaxError(m: String): String = when {
        m.startsWith("cannot assign to") ->
            tr(Res.string.ERROR_CANNOT_ASSIGN)
        m.contains("instead of '='") ->
            tr(Res.string.ERROR_USE_DOUBLE_EQUALS)
        m.startsWith("expected ':'") ->
            tr(Res.string.ERROR_MISSING_COLON)
        m.startsWith("unterminated string") || m.startsWith("unterminated triple-quoted") ->
            tr(Res.string.ERROR_UNTERMINATED_STRING)
        NEVER_CLOSED.containsMatchIn(m) ->
            tr(Res.string.ERROR_BRACKET_NEVER_CLOSED, NEVER_CLOSED.find(m)!!.groupValues[1])
        UNMATCHED.containsMatchIn(m) ->
            tr(Res.string.ERROR_BRACKET_UNMATCHED, UNMATCHED.find(m)!!.groupValues[1])
        m.contains("forgot a comma") -> tr(Res.string.ERROR_MISSING_COMMA)
        else ->
            tr(Res.string.ERROR_SYNTAX)
    }

    private fun indentationError(m: String): String? = when {
        m.startsWith("expected an indented block") ->
            tr(Res.string.ERROR_EXPECTED_INDENT)
        m.startsWith("unexpected indent") ->
            tr(Res.string.ERROR_UNEXPECTED_INDENT)
        m.startsWith("unindent does not match") ->
            tr(Res.string.ERROR_UNINDENT_MISMATCH)
        else -> null
    }

    private fun isTextAndNumber(a: String, b: String): Boolean {
        val numbers = setOf("int", "float")
        return (a == "str" && b in numbers) || (b == "str" && a in numbers)
    }

    fun describe(type: String): String = when (type) {
        "int" -> tr(Res.string.TYPE_INT)
        "float" -> tr(Res.string.TYPE_FLOAT)
        "str" -> tr(Res.string.TYPE_TEXT)
        "bool" -> tr(Res.string.TYPE_BOOL)
        "list" -> tr(Res.string.TYPE_LIST)
        "dict" -> tr(Res.string.TYPE_DICT)
        "tuple" -> tr(Res.string.TYPE_TUPLE)
        "NoneType" -> "None"
        "function", "builtin_function_or_method" -> tr(Res.string.TYPE_FUNCTION)
        else -> tr(Res.string.TYPE_OTHER, type)
    }

    private val NOT_DEFINED = Regex("""name '(\w+)' is not defined""")
    private val DID_YOU_MEAN = Regex("""Did you mean: '(\w+)'\?""")
    private val CONCATENATE = Regex("""can only concatenate str \(not "(\w+)"\) to str""")
    private val UNSUPPORTED_OPERAND = Regex("""unsupported operand type\(s\) for (\S+): '(\w+)' and '(\w+)'""")
    private val NOT_SUPPORTED_BETWEEN = Regex("""'(\S+)' not supported between instances of '(\w+)' and '(\w+)'""")
    private val NOT_CALLABLE = Regex("""'(\w+)' object is not callable""")
    private val NOT_SUBSCRIPTABLE = Regex("""'(\w+)' object is not subscriptable""")
    private val NOT_ITERABLE = Regex("""'(\w+)' object is not iterable""")
    private val TOO_MANY_ARGUMENTS = Regex("""(\w+)\(\) takes (\d+) positional arguments? but (\d+) (?:was|were) given""")
    private val MISSING_ARGUMENTS = Regex("""(\w+)\(\) missing \d+ required positional arguments?: (.+)""")
    private val INVALID_INT = Regex("""invalid literal for int\(\) with base 10: '(.*)'""")
    private val INVALID_FLOAT = Regex("""could not convert string to float: '(.*)'""")
    private val DECIMAL = Regex("""-?\d*\.\d+""")
    private val NO_ATTRIBUTE = Regex("""'(\w+)' object has no attribute '(\w+)'""")
    private val NEVER_CLOSED = Regex("""'(.)' was never closed""")
    private val UNMATCHED = Regex("""unmatched '(.)'""")
    private val FILE_NAME = Regex("""'([^']*)'$""")
    private val WRITE_ARGUMENT = Regex("""write\(\) argument must be str, not (\w+)""")
    private val NO_MODULE = Regex("""No module named '([^']+)'""")
    private val UNKNOWN_PLACE = Regex("""^unknown place: '(.*)'$""")
    private val WEATHER_ANSWERED = Regex("""^the weather service answered (\d+)$""")
}
