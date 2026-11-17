package uk.codecymru.primm.model

enum class ConsoleKind {
    Output,
    Error,
    Input,
    AutoInput,
    Info,
}

data class ConsoleSpan(val kind: ConsoleKind, val text: String)

data class ConsoleLine(val spans: List<ConsoleSpan>) {
    val text: String get() = spans.joinToString("") { it.text }
}

data class Console(
    val lines: List<ConsoleLine> = emptyList(),
    val current: List<ConsoleSpan> = emptyList(),
    val droppedLines: Int = 0,
    val maxLines: Int = MAX_LINES,
) {
    val displayLines: List<ConsoleLine>
        get() = if (current.isEmpty()) lines else lines + ConsoleLine(current)

    val isEmpty: Boolean get() = lines.isEmpty() && current.isEmpty()

    fun append(kind: ConsoleKind, text: String): Console {
        if (text.isEmpty()) return this
        val newLines = ArrayList<ConsoleLine>()
        var line = current
        val pieces = text.replace("\r", "").split('\n')
        pieces.forEachIndexed { index, piece ->
            if (piece.isNotEmpty()) line = line.withSpan(ConsoleSpan(kind, piece))
            if (index < pieces.lastIndex) {
                newLines.add(ConsoleLine(line))
                line = emptyList()
            }
        }
        return withLines(newLines, line)
    }

    fun appendLine(kind: ConsoleKind, text: String): Console =
        ensureNewLine().append(kind, text + "\n")

    fun ensureNewLine(): Console =
        if (current.isEmpty()) this else withLines(listOf(ConsoleLine(current)), emptyList())

    fun clear(): Console = Console(maxLines = maxLines)

    private fun withLines(added: List<ConsoleLine>, newCurrent: List<ConsoleSpan>): Console {
        if (added.isEmpty()) return copy(current = newCurrent)
        val all = lines + added
        val excess = (all.size - maxLines).coerceAtLeast(0)
        return copy(
            lines = if (excess == 0) all else all.subList(excess, all.size).toList(),
            current = newCurrent,
            droppedLines = droppedLines + excess,
        )
    }

    companion object {
        const val MAX_LINES = 5000
    }
}

private fun List<ConsoleSpan>.withSpan(span: ConsoleSpan): List<ConsoleSpan> {
    val last = lastOrNull()
    return if (last != null && last.kind == span.kind) {
        dropLast(1) + ConsoleSpan(span.kind, last.text + span.text)
    } else {
        this + span
    }
}
