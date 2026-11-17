package uk.codecymru.primm.model

import org.jetbrains.compose.resources.StringResource
import primm.shared.generated.resources.*

data class ChallengeCheck(val inputs: List<String>, val output: String)

enum class Verdict(private val titleRes: StringResource) {
    Yes(Res.string.VERDICT_YES),
    Partly(Res.string.VERDICT_PARTLY),
    No(Res.string.VERDICT_NO);

    val title: String get() = tr(titleRes)
}

data class ChangeLogEntry(
    val number: Int,
    val time: String,
    val expectation: String,
    val outcome: String,
    val verdict: Verdict? = null,
    val note: String = "",
)

data class CheckResult(val passed: Boolean, val message: String)

data class ModifyWork(
    val ticked: Set<Int> = emptySet(),
    val checks: Map<Int, CheckResult> = emptyMap(),
    val goal: String = "",
    val expectation: String = "",
    val log: List<ChangeLogEntry> = emptyList(),
    val reflection: String = "",
) {
    val awaitingVerdict: Boolean get() = log.lastOrNull()?.let { it.verdict == null } ?: false
}

object OutputMatch {
    fun lines(text: String): List<String> =
        text.replace("\r", "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun matches(expected: String, actual: String): Boolean = lines(expected) == lines(actual)

    fun containsInOrder(expected: String, actual: String): Boolean {
        val output = lines(actual)
        var from = 0
        for (line in lines(expected)) {
            val at = (from until output.size).firstOrNull { line in output[it] } ?: return false
            from = at + 1
        }
        return true
    }

    fun fromCell(text: String): String = text.replace("\\n", "\n").replace("↵", "\n")
}

object ProgramDirectives {
    private val DIRECTIVE = Regex("""^\s*#\s*primm-(challenge|check|brief)\s*:\s?(.*)$""", RegexOption.IGNORE_CASE)

    data class Extras(val challenges: List<Challenge>, val brief: String)

    fun parse(text: String): Extras {
        val challenges = ArrayList<Challenge>()
        val brief = ArrayList<String>()
        for (line in text.replace("\r", "").split('\n')) {
            val match = DIRECTIVE.find(line) ?: continue
            val value = match.groupValues[2].trim()
            when (match.groupValues[1].lowercase()) {
                "challenge" -> if (value.isNotEmpty()) challenges.add(Challenge(value))
                "check" -> if (challenges.isNotEmpty()) {
                    challenges[challenges.lastIndex] = challenges.last().copy(check = parseCheck(value))
                }
                "brief" -> if (value.isNotEmpty()) brief.add(value)
            }
        }
        return Extras(challenges, brief.joinToString(" "))
    }

    fun apply(program: Program): Program {
        val lines = program.code.split('\n')
        if (lines.none { DIRECTIVE.containsMatchIn(it) }) return program
        val extras = parse(program.code)
        val code = lines.filterNot { DIRECTIVE.containsMatchIn(it) }.dropWhile { it.isBlank() }.joinToString("\n")
        return program.copy(code = code, challenges = extras.challenges, brief = extras.brief)
    }

    fun format(challenges: List<Challenge>, brief: String): String = buildList {
        for (challenge in challenges) {
            add("# primm-challenge: ${challenge.text}")
            challenge.check?.let { check ->
                val inputs = check.inputs.joinToString(" | ")
                add("# primm-check: ${if (inputs.isEmpty()) "" else "$inputs "}=> ${check.output.replace("\n", "\\n")}")
            }
        }
        if (brief.isNotBlank()) add("# primm-brief: $brief")
    }.joinToString("\n")

    private fun parseCheck(value: String): ChallengeCheck? {
        val arrow = value.indexOf("=>")
        if (arrow < 0) return null
        val inputs = value.substring(0, arrow).split('|').map { it.trim() }.filter { it.isNotEmpty() }
        val output = value.substring(arrow + 2).trim().replace("\\n", "\n")
        return ChallengeCheck(inputs, output)
    }
}
