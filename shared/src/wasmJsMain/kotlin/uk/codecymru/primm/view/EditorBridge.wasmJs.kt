package uk.codecymru.primm.view

// Global functions defined in resources/primm/bridge.js.
private external fun primmEditorCreate(text: String, onChange: (String) -> Unit)
private external fun primmEditorSetBounds(
    x: Double,
    y: Double,
    width: Double,
    height: Double,
    density: Double,
    visible: Boolean,
)
private external fun primmEditorSetText(text: String)
private external fun primmEditorSetReadOnly(readOnly: Boolean)
private external fun primmEditorFocus()
private external fun primmEditorSetFontSize(px: Double)
private external fun primmEditorMarkError(line: Int, message: String)
private external fun primmEditorSetInteractive(interactive: Boolean)
private external fun primmEditorSetRunMarks(current: Int, ranCsv: String)
private external fun primmEditorSetDiffMarks(addedCsv: String, changedCsv: String, removedCsv: String)
private external fun primmMarksSetPage(key: String)
private external fun primmMarksSetTool(tool: String)
private external fun primmMarksSetVisible(visible: Boolean)
private external fun primmMarksClear()

internal actual fun bridgeEditorCreate(text: String, onChange: (String) -> Unit) {
    primmEditorCreate(text, onChange)
}

internal actual fun bridgeEditorSetBounds(
    x: Double,
    y: Double,
    width: Double,
    height: Double,
    density: Double,
    visible: Boolean,
) {
    primmEditorSetBounds(x, y, width, height, density, visible)
}

internal actual fun bridgeEditorSetText(text: String) {
    primmEditorSetText(text)
}

internal actual fun bridgeEditorSetReadOnly(readOnly: Boolean) {
    primmEditorSetReadOnly(readOnly)
}

internal actual fun bridgeEditorFocus() {
    primmEditorFocus()
}

internal actual fun bridgeEditorSetFontSize(px: Double) {
    primmEditorSetFontSize(px)
}

internal actual fun bridgeEditorMarkError(line: Int, message: String) {
    primmEditorMarkError(line, message)
}

internal actual fun bridgeEditorSetInteractive(interactive: Boolean) {
    primmEditorSetInteractive(interactive)
}

internal actual fun bridgeEditorSetRunMarks(nextLine: Int, ranLinesCsv: String) {
    primmEditorSetRunMarks(nextLine, ranLinesCsv)
}

internal actual fun bridgeEditorSetDiffMarks(addedCsv: String, changedCsv: String, removedCsv: String) {
    primmEditorSetDiffMarks(addedCsv, changedCsv, removedCsv)
}

internal actual fun bridgeMarksSetPage(key: String) {
    primmMarksSetPage(key)
}

internal actual fun bridgeMarksSetTool(tool: String) {
    primmMarksSetTool(tool)
}

internal actual fun bridgeMarksSetVisible(visible: Boolean) {
    primmMarksSetVisible(visible)
}

internal actual fun bridgeMarksClear() {
    primmMarksClear()
}
