package uk.codecymru.primm.view.panes.primm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.codecymru.primm.controller.AppController
import uk.codecymru.primm.model.AppState
import uk.codecymru.primm.model.EditorTab
import uk.codecymru.primm.model.GuideStep
import uk.codecymru.primm.model.LineDiff
import uk.codecymru.primm.model.Verdict
import uk.codecymru.primm.view.components.CellField
import uk.codecymru.primm.view.PrimmAdditionalColors
import uk.codecymru.primm.view.components.SmallButton
import uk.codecymru.primm.view.components.NextTag
import uk.codecymru.primm.view.components.ButtonType
import uk.codecymru.primm.view.components.PrimmButton
import uk.codecymru.primm.view.consoleTextStyle
import uk.codecymru.primm.view.components.PrimmSegmentedButton
import uk.codecymru.primm.view.components.Segment
import uk.codecymru.primm.view.components.icon
import primm.shared.generated.resources.*
import uk.codecymru.primm.model.plural
import org.jetbrains.compose.resources.stringResource

@Composable
fun ModifyRow(state: AppState, controller: AppController) {
    val active = state.activeStep
    val work = state.modify
    val challenges = state.program.challenges
    val cellStyle = consoleTextStyle(state.preferences.fontSize - 1)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.modifiedCode == null) {
            Section(stringResource(Res.string.MODIFY_START_MODIFYING), active = active == GuideStep.Modify) {
                Text(
                    stringResource(Res.string.MODIFY_MODIFICATIONS_WILL_NEW_TAB),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PrimmButton(
                    ButtonType.TRANSPARENT,
                    true,
                    controller::askToStartModifying,
                    text = stringResource(Res.string.MODIFY_START_MODIFYING),
                    icon = null
                )
            }
            if (challenges.isNotEmpty()) {
                Section(stringResource(Res.string.MODIFY_CHALLENGES)) {
                    challenges.forEachIndexed { i, challenge -> Text("${i + 1}. ${challenge.text}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            return@Column
        }

        if (state.activeTab != EditorTab.Modified) {
            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint(stringResource(Res.string.MODIFY_EDITOR_IS_ON, state.activeTab.title))
                SmallButton(stringResource(Res.string.MODIFY_SHOW_MODIFIED), onClick = { controller.selectTab(EditorTab.Modified) })
            }
        }

        Section(stringResource(Res.string.MODIFY_WHAT_DO_EXPECT_DIFFERENT), active = active == GuideStep.LogChange) {
            if (work.awaitingVerdict) Hint(stringResource(Res.string.MODIFY_FIRST_SAY_HOW_LAST))
            OutlinedTextField(
                value = work.expectation,
                onValueChange = controller::setExpectation,
                enabled = !work.awaitingVerdict,
                minLines = 2,
                placeholder = { Text(stringResource(Res.string.MODIFY_BEFORE_EACH_RUN_WHAT)) },
                colors = paneFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            val canRunModified = state.canRun && work.expectation.isNotBlank() && !work.awaitingVerdict
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimmButton(
                    ButtonType.TRANSPARENT,
                    canRunModified,
                    {
                        controller.selectTab(EditorTab.Modified)
                        controller.run()
                    },
                    text = stringResource(Res.string.MODIFY_RUN_MODIFIED_CODE),
                    icon = null,
                )
                if (active == GuideStep.LogChange && canRunModified) NextTag()
            }
        }

        Section(stringResource(Res.string.MODIFY_CHANGE_LOG)) {
            if (work.log.isEmpty()) Hint(stringResource(Res.string.MODIFY_EACH_RUN_MODIFIED_CODE))
            for (entry in work.log) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(PrimmAdditionalColors.pane, RoundedCornerShape(6.dp))
                        .border(1.dp, if (entry.verdict == null) PrimmAdditionalColors.guideEdge else PrimmAdditionalColors.border, RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("${entry.number}.  ${entry.time}  ·  ${entry.outcome}", style = MaterialTheme.typography.labelMedium, color = PrimmAdditionalColors.textQuiet)
                    Text(stringResource(Res.string.MODIFY_I_EXPECTED, entry.expectation), style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(Res.string.MODIFY_DID_DO_WHAT_EXPECTED), style = MaterialTheme.typography.bodySmall)
                        PrimmSegmentedButton(
                            segments = Verdict.entries.map { Segment(it, it.title, it.icon) },
                            selected = entry.verdict,
                            onSelect = { controller.setVerdict(entry.number, it) },
                        )
                    }
                    CellField(
                        value = entry.note,
                        onValueChange = { controller.setLogNote(entry.number, it) },
                        textStyle = cellStyle,
                        placeholder = stringResource(Res.string.MODIFY_NOTE_OPTIONAL),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Section(if (challenges.isEmpty()) stringResource(Res.string.MODIFY_MY_GOAL) else stringResource(Res.string.MODIFY_CHALLENGES_HEADING), active = active == GuideStep.Challenges) {
            if (challenges.isEmpty()) {
                Hint(stringResource(Res.string.MODIFY_THERE_NO_CHALLENGES_PROGRAM))
                OutlinedTextField(
                    value = work.goal,
                    onValueChange = controller::setGoal,
                    minLines = 2,
                    colors = paneFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                challenges.forEachIndexed { i, challenge ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = i in work.ticked, onCheckedChange = { controller.setChallengeTicked(i, it) })
                        Text(challenge.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (challenge.check != null) {
                            SmallButton(
                                stringResource(Res.string.MODIFY_CHECK),
                                onClick = { controller.checkChallenge(i) },
                                enabled = state.canRun && state.batch == null
                            )
                        }
                    }
                    work.checks[i]?.let { result ->
                        Text(
                            (if (result.passed) "✓ " else "✗ ") + result.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.passed) PrimmAdditionalColors.matches else PrimmAdditionalColors.notRun,
                            modifier = Modifier.padding(start = 48.dp),
                        )
                    }
                }
                Hint(stringResource(Res.string.MODIFY_TICK_CHALLENGE_WHEN_YOUVE))
            }
            state.batch?.let { Hint("${it.label}…") }
        }

        Section(stringResource(Res.string.MODIFY_COMPARE_ORIGINAL)) {
            val changes = LineDiff.changeCount(state.modifiedDiff)
            Text(
                if (changes == 0) stringResource(Res.string.MODIFY_NO_LINES_DIFFER_FROM) else stringResource(Res.string.MODIFY_LINES_DIFFER, plural(changes, Res.string.COUNT_LINE_DIFFERS_ONE, Res.string.COUNT_LINE_DIFFERS_OTHER)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimmButton(
                    ButtonType.OUTLINE,
                    true,
                    controller::openCompareWithOriginal,
                    text = stringResource(Res.string.MODIFY_COMPARE),
                    icon = null
                )
                PrimmButton(
                    ButtonType.OUTLINE,
                    changes > 0,
                    controller::askBackToOriginal,
                    text = stringResource(Res.string.MODIFY_REVERT),
                    icon = null
                )
            }
        }

        Section(stringResource(Res.string.MODIFY_REFLECTION), active = active == GuideStep.ModifyReflection) {
            OutlinedTextField(
                value = work.reflection,
                onValueChange = controller::setModifyReflection,
                minLines = 2,
                colors = paneFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Hint(if (challenges.isEmpty()) stringResource(Res.string.MODIFY_MAKE_UNLOCKS_WHEN_WRITTEN) else stringResource(Res.string.MODIFY_MAKE_UNLOCKS_WHEN_EVERY))
        }
    }
}

@Composable
internal fun ResultMark(passed: Boolean?) {
    Text(
        when (passed) {
            true -> "✓"
            false -> "✗"
            null -> ""
        },
        fontWeight = FontWeight.Bold,
        color = if (passed == true) PrimmAdditionalColors.matches else PrimmAdditionalColors.notRun,
    )
}
