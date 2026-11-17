package uk.codecymru.primm.view.panes

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import uk.codecymru.primm.model.DiffMarks
import uk.codecymru.primm.model.ErrorMark
import uk.codecymru.primm.model.Marks
import uk.codecymru.primm.model.RunMarks
import uk.codecymru.primm.view.bridgeEditorCreate
import uk.codecymru.primm.view.bridgeEditorMarkError
import uk.codecymru.primm.view.bridgeEditorSetBounds
import uk.codecymru.primm.view.bridgeEditorSetDiffMarks
import uk.codecymru.primm.view.bridgeEditorSetFontSize
import uk.codecymru.primm.view.bridgeEditorSetReadOnly
import uk.codecymru.primm.view.bridgeEditorSetRunMarks
import uk.codecymru.primm.view.bridgeEditorSetText
import uk.codecymru.primm.view.bridgeMarksClear
import uk.codecymru.primm.view.bridgeMarksSetPage
import uk.codecymru.primm.view.bridgeMarksSetTool
import uk.codecymru.primm.view.bridgeMarksSetVisible

@Composable
fun CodeEditorPane(
    code: String,
    readOnly: Boolean,
    visible: Boolean,
    fontSize: Int,
    errorMark: ErrorMark?,
    runMarks: RunMarks?,
    diffMarks: DiffMarks?,
    marks: Marks,
    marksPage: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnCodeChange by rememberUpdatedState(onCodeChange)
    val latestVisible by rememberUpdatedState(visible)
    val editorText = remember { EditorText(code) }
    val bounds = remember { EditorBounds() }
    val density = LocalDensity.current.density.toDouble()

    DisposableEffect(Unit) {
        bridgeEditorCreate(code) { text ->
            editorText.value = text
            latestOnCodeChange(text)
        }
        onDispose {
            bridgeEditorSetBounds(
                0.0,
                0.0,
                0.0,
                0.0,
                1.0,
                false
            )
        }
    }

    LaunchedEffect(code) {
        if (code != editorText.value) {
            editorText.value = code
            bridgeEditorSetText(code)
        }
    }

    LaunchedEffect(readOnly) {
        bridgeEditorSetReadOnly(
            readOnly
        )
    }
    LaunchedEffect(fontSize) {
        bridgeEditorSetFontSize(
            fontSize.toDouble()
        )
    }
    LaunchedEffect(errorMark) {
        bridgeEditorMarkError(
            errorMark?.line ?: 0,
            errorMark?.message ?: ""
        )
    }
    LaunchedEffect(runMarks) {
        bridgeEditorSetRunMarks(
            runMarks?.nextLine ?: 0,
            runMarks?.ranLines?.sorted()?.joinToString(",") ?: ""
        )
    }
    LaunchedEffect(diffMarks, code) {
        fun csv(lines: Set<Int>?) = lines?.sorted()?.joinToString(",") ?: ""
        bridgeEditorSetDiffMarks(
            csv(diffMarks?.added),
            csv(diffMarks?.changed),
            csv(diffMarks?.removedBefore)
        )
    }
    LaunchedEffect(visible) { bounds.send(density, visible) }

    LaunchedEffect(marksPage) { bridgeMarksSetPage(marksPage) }
    LaunchedEffect(marks.tool) { bridgeMarksSetTool(marks.tool?.bridgeName ?: "") }
    LaunchedEffect(marks.visible) { bridgeMarksSetVisible(marks.visible) }
    val clearsSeen = remember { ClearsSeen(marks.clears) }
    LaunchedEffect(marks.clears) {
        if (marks.clears != clearsSeen.value) {
            clearsSeen.value = marks.clears
            bridgeMarksClear()
        }
    }

    Box(
        modifier.onGloballyPositioned { coordinates ->
            bounds.update(coordinates)
            bounds.send(density, latestVisible)
        },
    )
}

private class EditorText(var value: String)

private class ClearsSeen(var value: Int)

private class EditorBounds {
    private var x = 0.0
    private var y = 0.0
    private var width = 0.0
    private var height = 0.0

    fun update(coordinates: LayoutCoordinates) {
        val position = coordinates.positionInWindow()
        x = position.x.toDouble()
        y = position.y.toDouble()
        width = coordinates.size.width.toDouble()
        height = coordinates.size.height.toDouble()
    }

    fun send(density: Double, visible: Boolean) {
        bridgeEditorSetBounds(
            x,
            y,
            width,
            height,
            density,
            visible && width > 0 && height > 0
        )
    }
}
