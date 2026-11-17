package uk.codecymru.primm.view

// The editor functions in webApp/src/webMain/resources/primm/bridge.js.

internal expect fun bridgeEditorCreate(text: String, onChange: (String) -> Unit)

internal expect fun bridgeEditorSetBounds(
    x: Double,
    y: Double,
    width: Double,
    height: Double,
    density: Double,
    visible: Boolean,
)

internal expect fun bridgeEditorSetText(text: String)

internal expect fun bridgeEditorSetReadOnly(readOnly: Boolean)

internal expect fun bridgeEditorFocus()

internal expect fun bridgeEditorSetFontSize(px: Double)

internal expect fun bridgeEditorMarkError(line: Int, message: String)

internal expect fun bridgeEditorSetInteractive(interactive: Boolean)

internal expect fun bridgeEditorSetRunMarks(nextLine: Int, ranLinesCsv: String)

internal expect fun bridgeEditorSetDiffMarks(addedCsv: String, changedCsv: String, removedCsv: String)

internal expect fun bridgeMarksSetPage(key: String)

internal expect fun bridgeMarksSetTool(tool: String)

internal expect fun bridgeMarksSetVisible(visible: Boolean)

internal expect fun bridgeMarksClear()
