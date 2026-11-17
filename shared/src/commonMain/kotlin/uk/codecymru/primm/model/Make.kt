package uk.codecymru.primm.model

enum class PlanMode { ThreeBoxes, Pseudocode }

data class MakeTest(
    val id: Int,
    val inputs: String = "",
    val expected: String = "",
    val actual: String? = null,
    val passed: Boolean? = null,
) {
    val inputValues: List<String> get() = inputs.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

data class MakeWork(
    val brief: String = "",
    val planMode: PlanMode = PlanMode.ThreeBoxes,
    val planInputs: String = "",
    val planProcess: String = "",
    val planOutputs: String = "",
    val pseudocode: String = "",
    val tests: List<MakeTest> = emptyList(),
    val nextTestId: Int = 1,
    val ranWithoutError: Boolean = false,
    val reflection: String = "",
) {
    val isPlanned: Boolean
        get() = when (planMode) {
            PlanMode.ThreeBoxes -> planInputs.isNotBlank() && planProcess.isNotBlank() && planOutputs.isNotBlank()
            PlanMode.Pseudocode -> pseudocode.isNotBlank()
        }

    fun addTest(): MakeWork = if (tests.size >= MAX_TESTS) this else copy(tests = tests + MakeTest(nextTestId), nextTestId = nextTestId + 1)

    fun updateTest(id: Int, transform: (MakeTest) -> MakeTest): MakeWork =
        copy(tests = tests.map { if (it.id == id) transform(it).copy(actual = null, passed = null) else it })

    fun removeTest(id: Int): MakeWork = copy(tests = tests.filterNot { it.id == id })

    fun withResult(id: Int, actual: String, passed: Boolean): MakeWork =
        copy(tests = tests.map { if (it.id == id) it.copy(actual = actual, passed = passed) else it })

    companion object {
        const val MAX_TESTS = 20
    }
}

object Concepts {
    fun of(analysis: Analysis?): Set<Concept> {
        if (analysis == null || !analysis.parsed) return emptySet()
        return buildSet {
            if (analysis.inputs.isNotEmpty()) add(Concept.Input)
            if (analysis.conditions.any { it.kind == ConditionKind.If || it.kind == ConditionKind.Elif }) add(Concept.Selection)
            if (analysis.loops.any { it.kind == LoopKind.For }) add(Concept.ForLoop)
            if (analysis.loops.any { it.kind == LoopKind.While }) add(Concept.WhileLoop)
            if (analysis.usesLists) add(Concept.Lists)
            if (analysis.functions.isNotEmpty()) add(Concept.Functions)
            if (analysis.fileUses.isNotEmpty()) add(Concept.Files)
        }
    }
}
