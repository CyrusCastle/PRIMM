package uk.codecymru.primm.model

import primm.shared.generated.resources.*

object TraceLabels {
    fun stage(stage: TraceStage, pass: Int, plan: TracePlan): String {
        val several = plan.loops.size > 1
        return when (stage.kind) {
            StageKind.Start -> tr(Res.string.TRACE_START)
            StageKind.Pass -> if (several) tr(Res.string.TRACE_PASS_OF_LOOP, pass, stage.loop) else pass.toString()
            StageKind.After -> if (several) tr(Res.string.TRACE_AFTER_LOOP, stage.loop) else tr(Res.string.TRACE_END)
        }
    }

    fun decisionHeader(chain: IfChain, codeLines: List<String>): String =
        if (chain.isSingle) condition(codeLines.getOrNull(chain.head - 1).orEmpty()).ifEmpty { tr(Res.string.TRACE_CONDITION) }
        else tr(Res.string.TRACE_WHICH_BRANCH, chain.head)

    fun decisionChoices(chain: IfChain, codeLines: List<String>): List<Pair<String, String>> {
        if (chain.isSingle) return listOf(PassTrace.TRUE to PassTrace.TRUE, PassTrace.FALSE to PassTrace.FALSE)
        val branches = chain.branches.map { it.toString() to branch(codeLines.getOrNull(it - 1).orEmpty()) }
        val last = if (chain.hasElse) PassTrace.ELSE to tr(Res.string.TRACE_BRANCH_ELSE) else PassTrace.NONE to tr(Res.string.TRACE_BRANCH_NONE)
        return branches + last
    }

    fun decisionText(chain: IfChain, value: String?, codeLines: List<String>): String {
        if (value == null) return ""
        return decisionChoices(chain, codeLines).firstOrNull { it.first == value }?.second ?: value
    }

    fun again(value: Boolean?): String = when (value) {
        true -> PassTrace.TRUE
        false -> PassTrace.FALSE
        null -> ""
    }

    private fun condition(line: String): String =
        branch(line).removePrefix("elif ").removePrefix("if ").trim()

    private fun branch(line: String): String = line.trim().substringBeforeLast(':').trim()
}
