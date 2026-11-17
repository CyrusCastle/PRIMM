package uk.codecymru.primm.model

import org.jetbrains.compose.resources.StringResource
import primm.shared.generated.resources.*

enum class EditorTab(private val titleRes: StringResource) {
    Original(Res.string.EDITOR_TAB_ORIGINAL),
    Modified(Res.string.EDITOR_TAB_MODIFIED),
    Make(Res.string.EDITOR_TAB_MAKE);

    val title: String get() = tr(titleRes)
}

enum class DiffKind { Same, Added, Removed, Changed }

data class DiffLine(
    val kind: DiffKind,
    val oldLine: Int?,
    val newLine: Int?,
    val oldText: String?,
    val newText: String?,
)

data class DiffMarks(val added: Set<Int>, val changed: Set<Int>, val removedBefore: Set<Int>) {
    val isEmpty: Boolean get() = added.isEmpty() && changed.isEmpty() && removedBefore.isEmpty()
}

object LineDiff {
    private var cachedOld: String? = null
    private var cachedNew: String? = null
    private var cachedDiff: List<DiffLine> = emptyList()
    private var cachedMarksFor: List<DiffLine>? = null
    private var cachedMarks: DiffMarks? = null

    fun cached(old: String, new: String): List<DiffLine> {
        if (old != cachedOld || new != cachedNew) {
            cachedDiff = compute(old, new)
            cachedOld = old
            cachedNew = new
        }
        return cachedDiff
    }

    fun cachedMarks(diff: List<DiffLine>): DiffMarks {
        val known = cachedMarks
        if (known != null && diff === cachedMarksFor) return known
        return marks(diff).also {
            cachedMarksFor = diff
            cachedMarks = it
        }
    }

    fun compute(old: String, new: String): List<DiffLine> {
        val a = lines(old)
        val b = lines(new)

        var start = 0
        while (start < a.size && start < b.size && a[start] == b[start]){
            start++
        }

        var end = 0
        while (end < a.size - start && end < b.size - start && a[a.size - 1 - end] == b[b.size - 1 - end]){
            end++
        }

        val result = ArrayList<DiffLine>()

        for (k in 0 until start) result.add(DiffLine(DiffKind.Same, k + 1, k + 1, a[k], b[k]))
        result.addAll(middle(a, b, start, a.size - end, b.size - end))
        for (k in end downTo 1) {
            result.add(DiffLine(DiffKind.Same, a.size - k + 1, b.size - k + 1, a[a.size - k], b[b.size - k]))
        }
        return result
    }

    private fun middle(all: List<String>, allNew: List<String>, from: Int, aEnd: Int, bEnd: Int): List<DiffLine> {
        val a = all.subList(from, aEnd)
        val b = allNew.subList(from, bEnd)
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.size - 1 downTo 0) {
            for (j in b.size - 1 downTo 0) {
                lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        val result = ArrayList<DiffLine>()
        val removed = ArrayList<Int>()
        val added = ArrayList<Int>()

        val first = from + 1

        fun flush() {
            val paired = minOf(removed.size, added.size)
            for (k in 0 until paired) {
                result.add(DiffLine(DiffKind.Changed, removed[k] + first, added[k] + first, a[removed[k]], b[added[k]]))
            }
            for (k in paired until removed.size) result.add(DiffLine(DiffKind.Removed, removed[k] + first, null, a[removed[k]], null))
            for (k in paired until added.size) result.add(DiffLine(DiffKind.Added, null, added[k] + first, null, b[added[k]]))
            removed.clear()
            added.clear()
        }

        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && a[i] == b[j] && lcs[i][j] == lcs[i + 1][j + 1] + 1 -> {
                    flush()
                    result.add(DiffLine(DiffKind.Same, i + first, j + first, a[i], b[j]))
                    i++
                    j++
                }
                j == b.size || (i < a.size && lcs[i + 1][j] >= lcs[i][j + 1]) -> removed.add(i++)
                else -> added.add(j++)
            }
        }
        flush()
        return result
    }

    fun marks(diff: List<DiffLine>): DiffMarks {
        val added = HashSet<Int>()
        val changed = HashSet<Int>()
        val removedBefore = HashSet<Int>()
        var nextNewLine = 1
        for (line in diff) {
            when (line.kind) {
                DiffKind.Same -> nextNewLine = line.newLine!! + 1
                DiffKind.Added -> {
                    added.add(line.newLine!!)
                    nextNewLine = line.newLine + 1
                }
                DiffKind.Changed -> {
                    changed.add(line.newLine!!)
                    nextNewLine = line.newLine + 1
                }
                DiffKind.Removed -> removedBefore.add(nextNewLine)
            }
        }
        return DiffMarks(added, changed, removedBefore)
    }

    fun changeCount(diff: List<DiffLine>): Int = diff.count { it.kind != DiffKind.Same }

    private fun lines(code: String): List<String> = if (code.isEmpty()) emptyList() else code.removeSuffix("\n").split('\n')
}
