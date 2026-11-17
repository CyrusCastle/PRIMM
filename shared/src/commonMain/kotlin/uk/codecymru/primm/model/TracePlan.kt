package uk.codecymru.primm.model

import org.jetbrains.compose.resources.StringResource
import primm.shared.generated.resources.*

enum class TraceMode { Lines, Passes }

enum class TraceStyle(private val titleRes: StringResource) {
    Simplified(Res.string.TRACE_STYLE_SIMPLIFIED),
    Verbose(Res.string.TRACE_STYLE_VERBOSE);

    val title: String get() = tr(titleRes)
}

data class TracePlan(
    val mode: TraceMode = TraceMode.Lines,
    val loops: List<LoopInfo> = emptyList(),
    val columns: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val excluded: Set<String> = emptySet(),
    val chains: List<IfChain> = emptyList(),
    val startValues: Map<String, String> = emptyMap(),
) {
    val hasWhileLoop: Boolean get() = loops.any { it.kind == LoopKind.While }

    fun loopAt(line: Int): LoopInfo? = loops.firstOrNull { it.line == line }

    fun chainAt(head: Int): IfChain? = chains.firstOrNull { it.head == head }

    companion object {
        fun of(analysis: Analysis?, chosen: List<String> = emptyList()): TracePlan {
            if (analysis == null) return TracePlan()
            val excluded = analysis.fileVariables.toSet()
            val offered = analysis.variables.filter { it !in excluded }
            if (!analysis.parsed) return TracePlan(columns = offered, excluded = excluded)

            val byPass = canTraceByPass(analysis)
            val loops = if (byPass) analysis.loops.sortedBy { it.line } else emptyList()
            val firstLoop = loops.firstOrNull()?.line

            val wanted = chosen.filter { it in offered }.distinct()
            val columns: List<String>
            val hidden: Set<String>
            if (wanted.isNotEmpty()) {
                columns = wanted + offered.filter { it !in wanted }
                hidden = offered.filter { it !in wanted }.toSet()
            } else {
                columns = offered
                val fileText = analysis.loops.filter { it.overFile }.flatMap { it.names }
                val setUpOnly = if (firstLoop == null) emptyList() else offered.filter { name ->
                    val lines = analysis.assignments[name].orEmpty()
                    lines.isNotEmpty() && lines.all { it < firstLoop }
                }
                hidden = (fileText + setUpOnly).filter { it in offered }.toSet()
            }

            val start = if (firstLoop == null) emptyMap() else analysis.literals
                .filter { literal ->
                    val lastBeforeLoop = analysis.assignments[literal.name].orEmpty().filter { it < firstLoop }.maxOrNull()
                    literal.line < firstLoop && literal.line == lastBeforeLoop &&
                        literal.name in columns && literal.name !in hidden
                }
                .associate { it.name to it.text }

            return TracePlan(
                mode = if (byPass) TraceMode.Passes else TraceMode.Lines,
                loops = loops,
                columns = columns,
                hidden = hidden,
                excluded = excluded,
                chains = if (byPass) analysis.chains.sortedBy { it.head } else emptyList(),
                startValues = start,
            )
        }

        private fun canTraceByPass(analysis: Analysis): Boolean {
            val loops = analysis.loops
            if (loops.isEmpty() || analysis.functions.isNotEmpty()) return false
            if (loops.any { it.scope.isNotEmpty() }) return false
            return loops.none { outer -> loops.any { inner -> inner.line != outer.line && outer.contains(inner.line) } }
        }
    }
}
