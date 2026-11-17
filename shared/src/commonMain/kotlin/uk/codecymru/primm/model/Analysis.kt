package uk.codecymru.primm.model

data class InputCall(val line: Int, val prompt: String, val inLoop: Boolean) {
    val label: String get() = "input($prompt)"
}

enum class ConditionKind { If, Elif, While }

data class ConditionLine(val line: Int, val kind: ConditionKind, val bodyLine: Int, val scope: String = "")

enum class LoopKind { For, While }

data class LoopInfo(
    val line: Int,
    val kind: LoopKind,
    val bodyLine: Int,
    val endLine: Int,
    val names: List<String>,
    val scope: String = "",
    val overFile: Boolean = false,
) {
    fun contains(line: Int): Boolean = line in this.line..endLine
}

data class IfChain(val head: Int, val branches: List<Int>, val hasElse: Boolean, val scope: String = "") {
    val isSingle: Boolean get() = branches.size == 1
}

data class Literal(val line: Int, val name: String, val text: String)

data class Analysis(
    val parsed: Boolean,
    val variables: List<String> = emptyList(),
    val inputs: List<InputCall> = emptyList(),
    val conditions: List<ConditionLine> = emptyList(),
    val loops: List<LoopInfo> = emptyList(),
    val printLines: List<Int> = emptyList(),
    val functions: List<String> = emptyList(),
    val usesLists: Boolean = false,
    val fileUses: List<FileUse> = emptyList(),
    val fileVariables: List<String> = emptyList(),
    val assignments: Map<String, List<Int>> = emptyMap(),
    val literals: List<Literal> = emptyList(),
    val chains: List<IfChain> = emptyList(),
) {
    val writtenFiles: List<String> get() = fileUses.filter { it.writes }.mapNotNull { it.name }.distinct()

    fun conditionAt(line: Int): ConditionLine? = conditions.firstOrNull { it.line == line }

    fun loopAt(line: Int): LoopInfo? = loops.firstOrNull { it.line == line }

    val conditionLines: Set<Int> get() = conditions.map { it.line }.toSet()

    companion object {
        val NotParsed = Analysis(parsed = false)
    }
}
