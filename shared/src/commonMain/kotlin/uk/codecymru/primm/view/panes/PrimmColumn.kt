package uk.codecymru.primm.view.panes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import uk.codecymru.primm.controller.AppController
import uk.codecymru.primm.model.AppState
import uk.codecymru.primm.model.Guide
import uk.codecymru.primm.model.GuideStep
import uk.codecymru.primm.model.PrimmProgress
import uk.codecymru.primm.model.PrimmRow
import uk.codecymru.primm.model.RowProgress
import uk.codecymru.primm.view.PaneCard
import uk.codecymru.primm.view.PrimmAdditionalColors
import uk.codecymru.primm.view.components.NextTag
import uk.codecymru.primm.view.components.Padlock
import uk.codecymru.primm.view.panes.primm.InvestigateRow
import uk.codecymru.primm.view.panes.primm.MakeRow
import uk.codecymru.primm.view.panes.primm.ModifyRow
import uk.codecymru.primm.view.panes.primm.PredictRow
import uk.codecymru.primm.view.panes.primm.RunRow

@Composable
fun PrimmColumn(state: AppState, controller: AppController, modifier: Modifier = Modifier) {
    val active = state.activeStep
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in state.visibleRows) {
            val unlocked = state.isRowUnlocked(row)
            val expanded = row in state.openRows
            val prompt = unlocked && !expanded && active.row == row && active != GuideStep.Finished
            PaneCard(
                if (expanded) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()
            ) {
                Column(if (expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
                    RowHeader(
                        row,
                        state,
                        expanded,
                        unlocked,
                        prompt,
                        onClick = { controller.toggleRow(row) })
                    if (expanded) {
                        HorizontalDivider(color = PrimmAdditionalColors.border)
                        val scroll = rememberScrollState()
                        Box(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                        ) {
                            when (row) {
                                PrimmRow.Predict -> PredictRow(
                                    state,
                                    controller
                                )

                                PrimmRow.Run -> RunRow(
                                    state,
                                    controller,
                                    scroll
                                )

                                PrimmRow.Investigate -> InvestigateRow(
                                    state,
                                    controller
                                )

                                PrimmRow.Modify -> ModifyRow(
                                    state,
                                    controller
                                )

                                PrimmRow.Make -> MakeRow(
                                    state,
                                    controller
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowHeader(
    row: PrimmRow,
    state: AppState,
    expanded: Boolean,
    unlocked: Boolean,
    prompt: Boolean,
    onClick: () -> Unit,
) {
    val headerColour = when {
        prompt -> PrimmAdditionalColors.guide
        !unlocked -> PrimmAdditionalColors.lockedRow
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (unlocked) Modifier.clickable(onClick = onClick) else Modifier)
            .background(headerColour)
            .alpha(if (unlocked) 1f else 0.45f)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(row.letter), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (stringResource(row.smallLetter).isNotEmpty()) {
                Text(
                    stringResource(row.smallLetter),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.titleSmall)
            Text(
                Guide.lockReason(row, state) ?: PrimmProgress.subtitle(row, state),
                style = MaterialTheme.typography.bodySmall,
                color = PrimmAdditionalColors.textQuiet,
            )
        }
        if (unlocked) {
            if (prompt) NextTag()
            ProgressDot(PrimmProgress.progress(row, state))
            Text(if (expanded) "▾" else "▸", color = PrimmAdditionalColors.textQuiet)
        } else {
            Padlock(PrimmAdditionalColors.textQuiet, Modifier.padding(end = 2.dp))
        }
    }
}

@Composable
private fun ProgressDot(progress: RowProgress) {
    Canvas(Modifier.size(12.dp)) {
        val radius = size.minDimension / 2
        val colour = PrimmAdditionalColors.accent
        when (progress) {
            RowProgress.NotStarted -> drawCircle(colour, radius - 1f, style = Stroke(width = 2f))
            RowProgress.InProgress -> {
                drawCircle(colour, radius - 1f, style = Stroke(width = 2f))
                drawArc(colour, startAngle = 90f, sweepAngle = 180f, useCenter = true, topLeft = Offset.Zero, size = size)
            }
            RowProgress.Done -> drawCircle(colour, radius)
        }
    }
}


