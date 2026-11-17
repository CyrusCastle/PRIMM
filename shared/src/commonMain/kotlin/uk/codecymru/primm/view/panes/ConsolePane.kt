package uk.codecymru.primm.view.panes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import uk.codecymru.primm.model.AppState
import uk.codecymru.primm.model.ConsoleKind
import uk.codecymru.primm.model.ConsoleSpan
import uk.codecymru.primm.model.ConsoleTab
import uk.codecymru.primm.model.EditorTab
import uk.codecymru.primm.view.PaneCard
import uk.codecymru.primm.view.PrimmAdditionalColors
import uk.codecymru.primm.view.components.guideHighlight
import uk.codecymru.primm.view.components.ButtonType
import uk.codecymru.primm.view.components.PrimmButton
import uk.codecymru.primm.view.consoleTextStyle
import primm.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ConsolePane(
    state: AppState,
    onRun: () -> Unit,
    onStep: () -> Unit,
    onNext: () -> Unit,
    onContinue: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onSubmitInput: (String) -> Unit,
    onShowTab: (ConsoleTab) -> Unit,
    files: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    PaneCard(modifier) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaneTab(
                    stringResource(Res.string.CONSOLE_TAB),
                    state.consoleTab == ConsoleTab.Console
                ) { onShowTab(ConsoleTab.Console) }
                val changed =
                    state.files.beforeLastRun != null && state.consoleTab != ConsoleTab.Files
                val count = state.files.files.size
                PaneTab(
                    stringResource(Res.string.CONSOLE_FILES_TAB) + (if (count > 0) " ($count)" else "") + (if (changed) " •" else ""),
                    state.consoleTab == ConsoleTab.Files,
                ) { onShowTab(ConsoleTab.Files) }
                Spacer(Modifier.weight(1f))
                if (state.isPaused) {
                    PrimmButton(
                        ButtonType.PRIMARY,
                        true,
                        onNext,
                        text = stringResource(Res.string.CONSOLE_NEXT)
                    )
                    PrimmButton(
                        ButtonType.OUTLINE,
                        true,
                        onContinue,
                        text = stringResource(Res.string.CONSOLE_CONTINUE)
                    )
                } else {
                    Row(
                        Modifier.guideHighlight(state.isRunPrompted, RectangleShape).padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PrimmButton(
                            ButtonType.PRIMARY,
                            state.runAllowed,
                            onRun,
                            text = stringResource(Res.string.CONSOLE_RUN)
                        )
                        if (state.runsAreTraced) {
                            PrimmButton(
                                ButtonType.OUTLINE,
                                state.canStep,
                                onStep,
                                text = stringResource(Res.string.CONSOLE_STEP)
                            )
                        }
                    }
                }
                PrimmButton(
                    ButtonType.OUTLINE,
                    state.canStop,
                    onStop,
                    text = stringResource(Res.string.CONSOLE_STOP),
                    icon = null
                )
                if (state.consoleTab == ConsoleTab.Console) {
                    PrimmButton(
                        ButtonType.TRANSPARENT,
                        true,
                        onClear,
                        text = stringResource(Res.string.CONSOLE_CLEAR)
                    )
                }
            }
            HorizontalDivider(color = PrimmAdditionalColors.border)
            when (state.consoleTab) {
                ConsoleTab.Console -> ConsoleOutput(
                    state = state,
                    onSubmitInput = onSubmitInput,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )

                ConsoleTab.Files -> files(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

private val CompactPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)

@Composable
private fun PaneTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) PrimmAdditionalColors.accent else PrimmAdditionalColors.textQuiet,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .drawBehind {
                if (selected) {
                    val y = size.height - 1.dp.toPx()
                    drawLine(PrimmAdditionalColors.accent, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                }
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
private fun ConsoleOutput(
    state: AppState,
    onSubmitInput: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val console = state.console
    val waiting = state.isWaitingForInput
    val style = consoleTextStyle(state.preferences.fontSize)
    val hint = state.errorHint?.takeIf { !waiting && !console.isEmpty }
    val lines = if (waiting) console.lines else console.displayLines
    val itemCount = lines.size + (if (waiting) 1 else 0) + (if (hint != null) 1 else 0)
    val listState = rememberLazyListState()

    LaunchedEffect(itemCount, waiting, console.current) {
        if (itemCount > 0) listState.scrollToItem(itemCount - 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (console.droppedLines > 0) {
            item(key = "dropped") {
                Text(
                    stringResource(Res.string.CONSOLE_EARLIER_LINES_NOT_SHOWN, console.droppedLines),
                    style = style,
                    color = PrimmAdditionalColors.consoleInfo,
                )
            }
        }
        itemsIndexed(lines) { _, line ->
            Text(
                text = styled(line.spans),
                style = style,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (waiting) {
            item(key = "input") {
                InputLine(prompt = console.current, style = style, onSubmit = onSubmitInput)
            }
        }
        if (hint != null) {
            item(key = "hint") {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(Res.string.CONSOLE_HINT)) }
                        append(" ")
                        append(hint)
                    },
                    style = style.copy(fontFamily = FontFamily.Default),
                    color = PrimmAdditionalColors.consoleHint,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
        if (itemCount == 0) {
            item(key = "empty") {
                Text(
                    when (state.activeTab) {
                        EditorTab.Modified if !state.runAllowed -> stringResource(Res.string.CONSOLE_NOTE_DOWN_EXPECT_DIFFERENT)
                        EditorTab.Make if !state.runAllowed -> stringResource(Res.string.CONSOLE_WRITE_PROGRAM_THEN_PRESS)
                        EditorTab.Original if state.isRunGated -> stringResource(Res.string.CONSOLE_FILL_EVERY_PART_PREDICT)
                        else -> stringResource(Res.string.CONSOLE_PRESS_RUN_RUN_PROGRAM)
                    },
                    style = style,
                    color = PrimmAdditionalColors.consoleInfo,
                )
            }
        }
    }
}

@Composable
private fun InputLine(prompt: List<ConsoleSpan>, style: TextStyle, onSubmit: (String) -> Unit) {
    var answer by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (prompt.isNotEmpty()) {
            Text(styled(prompt), style = style)
        }
        BasicTextField(
            value = answer,
            onValueChange = { answer = it.replace("\n", "") },
            singleLine = true,
            textStyle = style.copy(color = PrimmAdditionalColors.consoleInput),
            cursorBrush = SolidColor(PrimmAdditionalColors.consoleInput),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (isEnter && event.type == KeyEventType.KeyDown) {
                        onSubmit(answer)
                        answer = ""
                        true
                    } else {
                        false
                    }
                },
        )
    }
}

private fun styled(spans: List<ConsoleSpan>): AnnotatedString = buildAnnotatedString {
    for (span in spans) {
        withStyle(spanStyle(span.kind)) { append(span.text) }
    }
}

private fun spanStyle(kind: ConsoleKind): SpanStyle = when (kind) {
    ConsoleKind.Output -> SpanStyle(color = PrimmAdditionalColors.consoleOutput)
    ConsoleKind.Error -> SpanStyle(color = PrimmAdditionalColors.consoleError)
    ConsoleKind.Input -> SpanStyle(color = PrimmAdditionalColors.consoleInput, fontWeight = FontWeight.SemiBold)
    ConsoleKind.AutoInput -> SpanStyle(color = PrimmAdditionalColors.consoleAutoInput, fontWeight = FontWeight.SemiBold)
    ConsoleKind.Info -> SpanStyle(color = PrimmAdditionalColors.consoleInfo, fontStyle = FontStyle.Italic)
}
