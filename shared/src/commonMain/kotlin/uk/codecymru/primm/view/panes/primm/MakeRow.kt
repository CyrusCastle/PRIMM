package uk.codecymru.primm.view.panes.primm

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.codecymru.primm.controller.AppController
import uk.codecymru.primm.model.AppState
import uk.codecymru.primm.model.Concept
import uk.codecymru.primm.model.Concepts
import uk.codecymru.primm.model.EditorTab
import uk.codecymru.primm.model.GuideStep
import uk.codecymru.primm.model.MakeWork
import uk.codecymru.primm.model.PlanMode
import uk.codecymru.primm.view.components.CellField
import uk.codecymru.primm.view.components.CellGap
import uk.codecymru.primm.view.components.HeaderCell
import uk.codecymru.primm.view.PrimmAdditionalColors
import uk.codecymru.primm.view.components.SmallButton
import uk.codecymru.primm.view.components.NextTag
import uk.codecymru.primm.view.components.ButtonType
import uk.codecymru.primm.view.components.PrimmButton
import uk.codecymru.primm.view.consoleTextStyle
import uk.codecymru.primm.view.components.PrimmSegmentedButton
import uk.codecymru.primm.view.components.Segment
import primm.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun MakeRow(state: AppState, controller: AppController) {
    val active = state.activeStep
    val work = state.make
    val cellStyle = consoleTextStyle(state.preferences.fontSize - 1)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Section(stringResource(Res.string.MAKE_BRIEF)) {
            if (state.program.brief.isNotBlank()) {
                Text(state.program.brief, style = MaterialTheme.typography.bodyLarge)
            } else {
                Hint(stringResource(Res.string.MAKE_THERES_NO_BRIEF_PROGRAM))
                OutlinedTextField(
                    value = work.brief,
                    onValueChange = controller::setMakeBrief,
                    minLines = 2,
                    colors = paneFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.settings.makeBriefOnly) {
            Hint(stringResource(Res.string.MAKE_WRITE_PROGRAM_USUAL_EDITOR), Modifier.padding(horizontal = 10.dp))
            Reflection(state, controller)
            return@Column
        }

        Section(stringResource(Res.string.MAKE_PLAN), active = active == GuideStep.Plan) {
            PrimmSegmentedButton(
                segments = listOf(
                    Segment(PlanMode.ThreeBoxes, stringResource(Res.string.MAKE_INPUTS_PROCESS_OUTPUTS)),
                    Segment(PlanMode.Pseudocode, stringResource(Res.string.MAKE_PSEUDOCODE)),
                ),
                selected = work.planMode,
                onSelect = controller::setPlanMode,
            )
            when (work.planMode) {
                PlanMode.ThreeBoxes -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanBox(stringResource(Res.string.MAKE_INPUTS), work.planInputs, controller::setPlanInputs, Modifier.weight(1f))
                    PlanBox(stringResource(Res.string.MAKE_PROCESS), work.planProcess, controller::setPlanProcess, Modifier.weight(1f))
                    PlanBox(stringResource(Res.string.MAKE_OUTPUTS), work.planOutputs, controller::setPlanOutputs, Modifier.weight(1f))
                }
                PlanMode.Pseudocode -> OutlinedTextField(
                    value = work.pseudocode,
                    onValueChange = controller::setPseudocode,
                    minLines = 4,
                    textStyle = cellStyle,
                    colors = paneFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Section(stringResource(Res.string.MAKE_WRITE), active = active == GuideStep.NewProgram || active == GuideStep.MakeRun) {
            if (state.makeCode == null) {
                PrimmButton(
                    ButtonType.TRANSPARENT,
                    true,
                    controller::newProgram,
                    text = stringResource(Res.string.MAKE_NEW_PROGRAM),
                    icon = null
                )
                Hint(stringResource(Res.string.MAKE_OPENS_BLANK_MAKE_TAB))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.activeTab != EditorTab.Make) {
                        PrimmButton(
                            ButtonType.OUTLINE,
                            true,
                            { controller.selectTab(EditorTab.Make) },
                            text = stringResource(Res.string.MAKE_SHOW_MAKE_TAB)
                        )
                    }
                    PrimmButton(
                        ButtonType.TRANSPARENT,
                        state.canRun && state.makeCode.isNotBlank(),
                        {
                            controller.selectTab(EditorTab.Make)
                            controller.run()
                        },
                        text = stringResource(Res.string.MAKE_RUN_MY_PROGRAM),
                        icon = null,
                    )
                    if (active == GuideStep.MakeRun && state.canRun && state.makeCode.isNotBlank()) NextTag()
                }
                Text(
                    if (work.ranWithoutError) stringResource(Res.string.MAKE_HAS_RUN_WITHOUT_ERRORS) else stringResource(Res.string.MAKE_GET_RUN_WITHOUT_ERRORS),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (work.ranWithoutError) PrimmAdditionalColors.matches else PrimmAdditionalColors.textQuiet,
                )
            }
        }

        Section(stringResource(Res.string.MAKE_TESTS), active = active == GuideStep.MakeTests) {
            Hint(stringResource(Res.string.MAKE_WRITE_WHAT_YOULL_TYPE))
            Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                    HeaderCell(stringResource(Res.string.MAKE_INPUTS), 150.dp, cellStyle)
                    HeaderCell(stringResource(Res.string.MAKE_EXPECTED_OUTPUT), 200.dp, cellStyle)
                    HeaderCell(stringResource(Res.string.MAKE_ACTUAL_OUTPUT), 200.dp, cellStyle)
                    HeaderCell(stringResource(Res.string.MAKE_PASS), 40.dp, cellStyle)
                }
                for (test in work.tests) {
                    Row(horizontalArrangement = Arrangement.spacedBy(CellGap), verticalAlignment = Alignment.CenterVertically) {
                        CellField(
                            test.inputs,
                            { controller.setMakeTestInputs(test.id, it) },
                            cellStyle,
                            Modifier.width(150.dp),
                            placeholder = "3, 4"
                        )
                        CellField(
                            test.expected,
                            { controller.setMakeTestExpected(test.id, it) },
                            cellStyle,
                            Modifier.width(200.dp),
                            placeholder = "7"
                        )
                        Text(
                            test.actual?.replace("\n", "↵") ?: "",
                            style = cellStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(200.dp),
                        )
                        Row(Modifier.width(40.dp)) { ResultMark(test.passed) }
                        SmallButton("✕", onClick = { controller.removeMakeTest(test.id) })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallButton(
                    stringResource(Res.string.MAKE_ADD_TEST),
                    onClick = controller::addMakeTest,
                    enabled = work.tests.size < MakeWork.MAX_TESTS
                )
                PrimmButton(
                    ButtonType.OUTLINE,
                    state.canRun && !state.makeCode.isNullOrBlank() && work.tests.isNotEmpty() && state.batch == null,
                    controller::runMakeTests,
                    text = stringResource(Res.string.MAKE_RUN_TESTS),
                    icon = null,
                )
                state.batch?.let { Hint(stringResource(Res.string.MAKE_BATCH_PROGRESS, it.label, it.done, it.total)) }
            }
        }

        Section(stringResource(Res.string.MAKE_CONCEPTS)) {
            val original = Concepts.of(state.analysis)
            val mine = Concepts.of(state.makeAnalysis)
            Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                HeaderCell("", 190.dp, cellStyle)
                HeaderCell(stringResource(Res.string.MAKE_ORIGINAL), 80.dp, cellStyle)
                HeaderCell(stringResource(Res.string.MAKE_MINE), 80.dp, cellStyle)
            }
            for (concept in Concept.entries) {
                Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                    Text(concept.title, style = cellStyle, modifier = Modifier.width(190.dp))
                    Text(if (concept in original) "✓" else "", style = cellStyle, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                    Text(if (concept in mine) "✓" else "", style = cellStyle, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                }
            }
            if (state.makeCode == null) Hint(stringResource(Res.string.MAKE_PROGRAMS_CONCEPTS_TICKED_WRITE))
        }

        Reflection(state, controller)
    }
}

@Composable
private fun Reflection(state: AppState, controller: AppController) {
    Section(stringResource(Res.string.MAKE_REFLECTION), active = state.activeStep == GuideStep.MakeReflection) {
        OutlinedTextField(
            value = state.make.reflection,
            onValueChange = controller::setMakeReflection,
            minLines = 2,
            colors = paneFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlanBox(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = PrimmAdditionalColors.textQuiet)
        OutlinedTextField(value = value, onValueChange = onChange, minLines = 3, colors = paneFieldColors(), modifier = Modifier.fillMaxWidth())
    }
}
