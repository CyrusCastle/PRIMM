package uk.codecymru.primm.view.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Eraser
import compose.icons.fontawesomeicons.solid.Highlighter
import compose.icons.fontawesomeicons.solid.PencilAlt
import uk.codecymru.primm.model.EditorTab
import uk.codecymru.primm.model.ExpectedErrorKind
import uk.codecymru.primm.model.MarkTool
import uk.codecymru.primm.model.PlanMode
import uk.codecymru.primm.model.SelfMark
import uk.codecymru.primm.model.Verdict

val EditorTab.icon: ImageVector
    get() = when (this) {
        EditorTab.Original -> Icons.Filled.Description
        EditorTab.Modified -> Icons.Filled.Edit
        EditorTab.Make -> Icons.Filled.Build
    }

val MarkTool.icon: ImageVector
    get() = when (this) {
        MarkTool.Pen -> FontAwesomeIcons.Solid.PencilAlt
        MarkTool.Highlighter -> FontAwesomeIcons.Solid.Highlighter
        MarkTool.Eraser -> FontAwesomeIcons.Solid.Eraser
    }

//val ExpectedErrorKind.icon: ImageVector
//    get() = when (this) {
//        ExpectedErrorKind.None -> Icons.Filled.CheckCircle
//        ExpectedErrorKind.Syntax -> Icons.Filled.Spellcheck
//        ExpectedErrorKind.Runtime -> Icons.Filled.Error
//        ExpectedErrorKind.Logic -> Icons.Filled.BugReport
//    }

//val PlanMode.icon: ImageVector
//    get() = when (this) {
//        PlanMode.ThreeBoxes -> Icons.Filled.ViewColumn
//        PlanMode.Pseudocode -> Icons.Filled.Code
//    }

val SelfMark.icon: ImageVector
    get() = when (this) {
        SelfMark.Right -> Icons.Filled.Check
        SelfMark.Partly -> Icons.Filled.Contrast
        SelfMark.Wrong -> Icons.Filled.Close
    }

val Verdict.icon: ImageVector
    get() = when (this) {
        Verdict.Yes -> Icons.Filled.Check
        Verdict.Partly -> Icons.Filled.Contrast
        Verdict.No -> Icons.Filled.Close
    }
