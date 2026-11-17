package uk.codecymru.primm.model

import org.jetbrains.compose.resources.StringResource
import primm.shared.generated.resources.*

enum class MarkTool(private val labelRes: StringResource, val bridgeName: String) {
    Pen(Res.string.MARK_TOOL_PEN, "pen"),
    Highlighter(Res.string.MARK_TOOL_HIGHLIGHTER, "highlighter"),
    Eraser(Res.string.MARK_TOOL_ERASER, "eraser");

    val label: String get() = tr(labelRes)
}

data class Marks(
    val tool: MarkTool? = null,
    val visible: Boolean = true,
    val program: Int = 0,
    val clears: Int = 0,
) {
    fun page(tab: EditorTab): String = "$program:${tab.name}"

    fun choose(tool: MarkTool): Marks = if (this.tool == tool) copy(tool = null) else copy(tool = tool, visible = true)

    fun toggleVisible(): Marks = if (visible) copy(visible = false, tool = null) else copy(visible = true)

    fun clear(): Marks = copy(clears = clears + 1)

    fun newProgram(): Marks = copy(tool = null, program = program + 1)
}