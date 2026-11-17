package uk.codecymru.primm.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.codecymru.primm.model.AppState
import uk.codecymru.primm.model.Language
import uk.codecymru.primm.model.RuntimeStatus
import uk.codecymru.primm.view.components.ButtonType
import uk.codecymru.primm.view.components.PrimmButton
import uk.codecymru.primm.view.components.Padlock
import primm.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.codecymru.primm.model.tr

@Composable
fun TopBar(
    state: AppState,
    onLoad: () -> Unit,
    onSave: () -> Unit,
    onAutosaveChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    language: Language,
    onToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer),//.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.width(2.dp))
        Text(
            stringResource(Res.string.APP_NAME),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = PrimmScheme.onPrimaryContainer,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onLongPress = { onOpenSettings() })
            },
        )
//        StageChip(state)
        Spacer(Modifier.weight(1f))
        LanguageFlag(language, onToggleLanguage)
        PrimmButton(
            ButtonType.OUTLINE,
            true,
            onLoad,
            PrimmScheme.onPrimaryContainer,
            stringResource(Res.string.TOPBAR_LOAD),
            null
        )
        PrimmButton(
            ButtonType.OUTLINE,
            true,
            onSave,
            PrimmScheme.onPrimaryContainer,
            stringResource(Res.string.TOPBAR_SAVE),
            null
        )
        //AutosaveBox(state.preferences.autosave, onAutosaveChange)
        StatusBadge(state.status, state.loadingFromLink)
        Spacer(Modifier.width(16.dp))
    }
}

@Composable
private fun LanguageFlag(language: Language, onToggle: () -> Unit) {
    val description = stringResource(Res.string.TOPBAR_CHANGE_LANGUAGE, language.other.title)
    val shape = RoundedCornerShape(3.dp)
    Image(
        painter = painterResource(if (language == Language.WELSH) Res.drawable.flag_welsh else Res.drawable.flag_english),
        contentDescription = description,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .size(width = 32.dp, height = 24.dp)
            .clip(shape)
            .border(1.dp, PrimmScheme.onPrimaryContainer, shape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onToggle),
    )
}

@Composable
private fun AutosaveBox(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).clickable { onChange(!checked) }.padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors().copy(
                checkedBoxColor = PrimmScheme.onPrimaryContainer,
                uncheckedBoxColor = PrimmScheme.onPrimaryContainer,

                checkedCheckmarkColor = PrimmScheme.primary,
                uncheckedCheckmarkColor = PrimmScheme.onPrimaryContainer
            )
        )
        Text(stringResource(Res.string.TOPBAR_AUTOSAVE), style = MaterialTheme.typography.bodyMedium, color = PrimmScheme.onPrimaryContainer)
    }
}

@Composable
private fun StageChip(state: AppState) {
    Row(
        Modifier
            .background(PrimmAdditionalColors.pane, RoundedCornerShape(14.dp))
            .border(1.dp, PrimmAdditionalColors.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(Res.string.TOPBAR_STAGE, state.stage.title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text("·", color = PrimmAdditionalColors.textQuiet)
        if (state.isEditorReadOnly) {
            Padlock(PrimmAdditionalColors.textQuiet)
            Text(stringResource(Res.string.TOPBAR_CODE_LOCKED), style = MaterialTheme.typography.bodyMedium, color = PrimmAdditionalColors.textQuiet)
        } else {
            Text(stringResource(Res.string.TOPBAR_CODE_UNLOCKED), style = MaterialTheme.typography.bodyMedium, color = PrimmAdditionalColors.textQuiet)
        }
    }
}

@Composable
private fun StatusBadge(status: RuntimeStatus, loadingFromLink: Boolean) {
    val (label, colour) = if (loadingFromLink) stringResource(Res.string.TOPBAR_LOADING_PROGRAM) to PrimmAdditionalColors.statusIdle else statusLabel(status)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).border(1.dp, PrimmScheme.onPrimaryContainer, CircleShape).background(colour, CircleShape))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = PrimmScheme.onPrimaryContainer)
    }
}

fun statusLabel(status: RuntimeStatus): Pair<String, Color> = when (status) {
    RuntimeStatus.Loading -> tr(Res.string.TOPBAR_PYTHON_LOADING) to PrimmAdditionalColors.statusIdle
    RuntimeStatus.Ready -> tr(Res.string.TOPBAR_PYTHON_READY) to PrimmAdditionalColors.statusReady
    RuntimeStatus.Running -> tr(Res.string.TOPBAR_RUNNING) to PrimmAdditionalColors.statusRunning
    RuntimeStatus.WaitingForInput -> tr(Res.string.TOPBAR_WAITING_INPUT) to PrimmAdditionalColors.statusAttention
    RuntimeStatus.Paused -> tr(Res.string.TOPBAR_PAUSED_PRESS_NEXT) to PrimmAdditionalColors.statusAttention
    RuntimeStatus.Stopping -> tr(Res.string.TOPBAR_STOPPING) to PrimmAdditionalColors.statusAttention
    RuntimeStatus.Unavailable -> tr(Res.string.TOPBAR_PYTHON_UNAVAILABLE) to PrimmAdditionalColors.statusProblem
}
