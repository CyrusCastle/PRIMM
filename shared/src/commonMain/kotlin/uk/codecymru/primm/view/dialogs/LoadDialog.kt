package uk.codecymru.primm.view.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import uk.codecymru.primm.controller.AppController
import uk.codecymru.primm.model.DialogState
import uk.codecymru.primm.model.LoadTab
import uk.codecymru.primm.model.Snippet
import uk.codecymru.primm.controller.Snippets
import uk.codecymru.primm.view.components.ButtonType
import uk.codecymru.primm.view.components.PrimmButton
import primm.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import uk.codecymru.primm.view.PrimmAdditionalColors
import uk.codecymru.primm.view.consoleTextStyle

@Composable
fun LoadDialog(dialog: DialogState.LoadProgram, fontSize: Int, controller: AppController) {
    Dialog(
        onDismissRequest = controller::closeDialog,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = PrimmAdditionalColors.pane,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 1000.dp)
                .fillMaxHeight(0.88f)
                .heightIn(max = 700.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.LOAD_TITLE), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    PrimmButton(
                        ButtonType.OUTLINE,
                        true,
                        controller::closeDialog,
                        text = "X"
                    )
                }
                Row(Modifier.padding(horizontal = 12.dp)) {
                    TabButton(stringResource(Res.string.LOAD_TAB_LIBRARY), dialog.tab == LoadTab.Library) {
                        controller.setLoadTab(LoadTab.Library)
                    }
                    TabButton(stringResource(Res.string.LOAD_TAB_LINK), dialog.tab == LoadTab.Link) {
                        controller.setLoadTab(LoadTab.Link)
                    }
                    TabButton(stringResource(Res.string.LOAD_TAB_SESSION), dialog.tab == LoadTab.Session) {
                        controller.setLoadTab(LoadTab.Session)
                    }
                }
                HorizontalDivider(color = PrimmAdditionalColors.border)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (dialog.tab) {
                        LoadTab.Library -> LibraryTab(dialog, fontSize, controller)
                        LoadTab.Link -> LinkTab(dialog, controller)
                        LoadTab.Session -> SessionTab(dialog, controller)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            color = if (selected) PrimmAdditionalColors.accent else PrimmAdditionalColors.textQuiet,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Box(
            Modifier
                .height(3.dp)
                .width(120.dp)
                .background(if (selected) PrimmAdditionalColors.accent else PrimmAdditionalColors.pane, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun LibraryTab(dialog: DialogState.LoadProgram, fontSize: Int, controller: AppController) {
    val results = Snippets.search(dialog.search)
    val selected = dialog.selectedSnippetId?.let { Snippets.byId(it) }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            OutlinedTextField(
                value = dialog.search,
                onValueChange = controller::setLibrarySearch,
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.LOAD_SEARCH)) },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                for ((topic, snippets) in Snippets.grouped(results)) {
                    item(key = "topic-${topic.id}") {
                        Text(
                            topic.title.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = PrimmAdditionalColors.textQuiet,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(snippets, key = { it.id }) { snippet ->
                        SnippetRow(
                            snippet = snippet,
                            selected = snippet.id == selected?.id,
                            onClick = { controller.selectSnippet(snippet.id) },
                        )
                    }
                }
                if (results.isEmpty()) {
                    item(key = "none") {
                        Text(
                            stringResource(Res.string.LOAD_NO_PROGRAMS_MATCH, dialog.search),
                            color = PrimmAdditionalColors.textQuiet,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
        VerticalDivider(color = PrimmAdditionalColors.border)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (selected != null) {
                SnippetPreview(selected, fontSize, onLoad = controller::loadSelectedSnippet)
            } else {
                Text(
                    stringResource(Res.string.LOAD_CHOOSE_PROGRAM_LEFT),
                    color = PrimmAdditionalColors.textQuiet,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun SnippetRow(snippet: Snippet, selected: Boolean, onClick: () -> Unit) {
    Text(
        snippet.title,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) PrimmAdditionalColors.selected else PrimmAdditionalColors.pane)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun SnippetPreview(snippet: Snippet, fontSize: Int, onLoad: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(snippet.title, style = MaterialTheme.typography.titleMedium)
                val uses = if (snippet.concepts.isEmpty()) stringResource(Res.string.LOAD_VARIABLES_ONLY) else snippet.concepts.joinToString(", ") { it.title }
                Text(
                    stringResource(Res.string.LOAD_USES, snippet.topic.title, uses),
                    style = MaterialTheme.typography.bodySmall,
                    color = PrimmAdditionalColors.textQuiet,
                )
            }
            PrimmButton(
                ButtonType.OUTLINE,
                true,
                onLoad,
                text = stringResource(Res.string.LOAD_THIS_PROGRAM),
                icon = null
            )
        }
        val lines = snippet.code.removeSuffix("\n").split('\n')
        val style = consoleTextStyle(fontSize)
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, PrimmAdditionalColors.border, RoundedCornerShape(4.dp))
                .background(PrimmAdditionalColors.page, RoundedCornerShape(4.dp))
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            Column(Modifier.padding(horizontal = 10.dp), horizontalAlignment = Alignment.End) {
                lines.indices.forEach { Text("${it + 1}", style = style, color = PrimmAdditionalColors.textQuiet) }
            }
            Column(Modifier.horizontalScroll(rememberScrollState()).padding(end = 10.dp)) {
                lines.forEach { Text(it.ifEmpty { " " }, style = style, softWrap = false) }
            }
        }
    }
}

@Composable
private fun LinkTab(dialog: DialogState.LoadProgram, controller: AppController) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(Res.string.LOAD_PASTE_LINK_PYTHON_PY))
        OutlinedTextField(
            value = dialog.link,
            onValueChange = controller::setLinkText,
            singleLine = true,
            enabled = !dialog.fetching,
            isError = dialog.linkError != null,
            placeholder = { Text("https://raw.githubusercontent.com/…/program.py") },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                        controller.loadFromLink()
                        true
                    } else {
                        false
                    }
                },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimmButton(
                ButtonType.TRANSPARENT,
                !dialog.fetching && dialog.link.isNotBlank(),
                controller::loadFromLink,
                text = stringResource(Res.string.LOAD_BUTTON),
                icon = null
            )
            if (dialog.fetching) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(Res.string.LOAD_DOWNLOADING), color = PrimmAdditionalColors.textQuiet)
            }
        }
        dialog.linkError?.let { error ->
            Text(error, color = PrimmAdditionalColors.consoleError)
        }
        HorizontalDivider(color = PrimmAdditionalColors.border)
        Text(stringResource(Res.string.LOAD_LINKS_WORK), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(Res.string.LOAD_LINKS_THAT_WORK_LIST),
            style = MaterialTheme.typography.bodyMedium,
            color = PrimmAdditionalColors.textQuiet,
        )
        Text(
            stringResource(Res.string.LOAD_AFTER_LOADING_ADDRESS_BAR),
            style = MaterialTheme.typography.bodyMedium,
            color = PrimmAdditionalColors.textQuiet,
        )
    }
}

@Composable
private fun SessionTab(dialog: DialogState.LoadProgram, controller: AppController) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(Res.string.LOAD_CARRY_FROM_PREVIOUS_SESSION))
        PrimmButton(
            ButtonType.TRANSPARENT,
            true,
            controller::chooseSessionFile,
            text = stringResource(Res.string.LOAD_CHOOSE_PRIMM_JSON_FILE),
            icon = null
        )
        dialog.sessionError?.let { error ->
            Text(error, color = PrimmAdditionalColors.consoleError)
        }
        HorizontalDivider(color = PrimmAdditionalColors.border)
        Text(
            stringResource(Res.string.LOAD_SESSION_FILE_HOLDS_PROGRAM),//\nYou might want to try using autosave, which keeps it in your browser across sessions. I cannot promise this will work on managed multi-user school computers.",
            style = MaterialTheme.typography.bodyMedium,
            color = PrimmAdditionalColors.textQuiet,
        )
    }
}
