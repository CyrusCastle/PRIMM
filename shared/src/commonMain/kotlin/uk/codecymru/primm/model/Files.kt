package uk.codecymru.primm.model

import primm.shared.generated.resources.*

data class TextFile(val name: String, val text: String)

data class FileUse(val line: Int, val name: String?, val mode: String) {
    val writes: Boolean get() = mode == "?" || mode.any { it in "wax+" }

    val verb: String
        get() = when {
            mode == "?" -> tr(Res.string.FILE_VERB_OPENS)
            'a' in mode -> tr(Res.string.FILE_VERB_ADDS_TO)
            'w' in mode || 'x' in mode -> tr(Res.string.FILE_VERB_WRITES)
            '+' in mode -> tr(Res.string.FILE_VERB_READS_AND_WRITES)
            else -> tr(Res.string.FILE_VERB_READS)
        }
}

enum class ConsoleTab { Console, Files }

object FileRules {
    const val MAX_FILES = 20
    const val MAX_CHARS = 100_000
    const val MAX_NAME_LENGTH = 40
    private const val FORBIDDEN = "/\\:*?\"<>|"

    fun nameProblem(name: String, others: Collection<String>): String? = when {
        name.isBlank() -> tr(Res.string.FILE_NAME_BLANK)
        name != name.trim() -> tr(Res.string.FILE_NAME_EDGE_SPACE)
        name.any { it in FORBIDDEN } -> tr(Res.string.FILE_NAME_FORBIDDEN)
        name.startsWith(".") -> tr(Res.string.FILE_NAME_DOT)
        name.length > MAX_NAME_LENGTH -> tr(Res.string.FILE_NAME_TOO_LONG, MAX_NAME_LENGTH)
        others.any { it.equals(name, ignoreCase = true) } -> tr(Res.string.FILE_NAME_TAKEN, name)
        else -> null
    }

    fun newName(others: Collection<String>): String =
        generateSequence(1) { it + 1 }
            .map { if (it == 1) "data.txt" else "data$it.txt" }
            .first { candidate -> others.none { it.equals(candidate, ignoreCase = true) } }

    fun mimeType(name: String): String = if (name.lowercase().endsWith(".csv")) "text/csv" else "text/plain"

    fun sameContents(a: String, b: String): Boolean = lines(a) == lines(b)

    private fun lines(text: String): List<String> =
        text.replace("\r", "").split('\n').map { it.trimEnd() }.dropLastWhile { it.isEmpty() }
}

data class FilesState(
    val files: List<TextFile> = emptyList(),
    val selected: String? = null,
    val beforeLastRun: List<TextFile>? = null,
    val changedByLastRun: Set<String> = emptySet(),
    val deletedByLastRun: Set<String> = emptySet(),
    val message: String? = null,
) {
    val names: List<String> get() = files.map { it.name }

    fun file(name: String?): TextFile? = files.firstOrNull { it.name == name }

    val selectedFile: TextFile? get() = file(selected) ?: files.firstOrNull()

    fun afterRun(after: List<TextFile>): FilesState {
        val byName = after.associateBy { it.name }
        val kept = files.mapNotNull { byName[it.name] }
        val added = after.filter { file(it.name) == null }
        val updated = kept + added
        val changed = after.filter { file(it.name)?.text != it.text }.map { it.name }.toSet()
        val deleted = files.map { it.name }.filter { it !in byName }.toSet()
        val anything = changed.isNotEmpty() || deleted.isNotEmpty()
        return copy(
            files = updated,
            selected = selected?.takeIf { name -> updated.any { it.name == name } },
            beforeLastRun = if (anything) files else null,
            changedByLastRun = changed,
            deletedByLastRun = deleted,
            message = null,
        )
    }

    fun undoLastRun(): FilesState {
        val before = beforeLastRun ?: return this
        return copy(
            files = before,
            selected = selected?.takeIf { name -> before.any { it.name == name } },
            beforeLastRun = null,
            changedByLastRun = emptySet(),
            deletedByLastRun = emptySet(),
            message = null,
        )
    }

    private fun edited(files: List<TextFile>, selected: String? = this.selected, message: String? = null) =
        copy(files = files, selected = selected, beforeLastRun = null, message = message)

    fun add(name: String, text: String = ""): FilesState {
        if (files.size >= FileRules.MAX_FILES) return copy(message = tr(Res.string.FILE_TOO_MANY, FileRules.MAX_FILES))
        FileRules.nameProblem(name, names)?.let { return copy(message = it) }
        return edited(files + TextFile(name, text.take(FileRules.MAX_CHARS)), selected = name)
    }

    fun setText(name: String, text: String): FilesState =
        edited(files.map { if (it.name == name) it.copy(text = text.take(FileRules.MAX_CHARS)) else it })

    fun rename(from: String, to: String): FilesState {
        if (from == to) return copy(message = null)
        FileRules.nameProblem(to, names - from)?.let { return copy(message = it) }
        return edited(files.map { if (it.name == from) it.copy(name = to) else it }, selected = to)
    }

    fun remove(name: String): FilesState {
        val remaining = files.filterNot { it.name == name }
        return edited(remaining, selected = if (selected == name) remaining.firstOrNull()?.name else selected)
    }

    fun upload(name: String, text: String): FilesState {
        val existing = files.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            return edited(
                files.map { if (it === existing) TextFile(name, text.take(FileRules.MAX_CHARS)) else it },
                selected = name,
                message = tr(Res.string.FILE_REPLACED, name),
            )
        }
        return add(name, text)
    }

    fun select(name: String): FilesState = copy(selected = name, message = null)

    companion object {
        fun of(files: List<TextFile>) = FilesState(files = files, selected = files.firstOrNull()?.name)
    }
}

data class FileComparison(val name: String, val before: String?, val predicted: String, val actual: String?) {
    val matches: Boolean
        get() = if (actual == null) predicted.isBlank() else FileRules.sameContents(predicted, actual)
}
