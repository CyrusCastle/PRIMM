package uk.codecymru.primm.view

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

object PrimmScheme {
    val primary = Color(0xFF002907)
    val onPrimary = Color(0xFFFFFFFF)
    val secondary = Color(0xFF172616)
    val onSecondary = Color(0xFFFFFFFF)
    val tertiary = Color(0xFF00272B)
    val onTertiary = Color(0xFFFFFFFF)

    val primaryContainer = Color(0xFF1E4C21)
    val onPrimaryContainer = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFF374735)
    val onSecondaryContainer = Color(0xFFFFFFFF)
    val tertiaryContainer = Color(0xFF1A494F)
    val onTertiaryContainer = Color(0xFFFFFFFF)

    val surfaceDim = Color(0xFFD8DBD2)
    val surface = Color(0xFFF7FBF1)
    val surfaceBright = Color(0xFFF7FBF1)
    val surfaceContainerLowest = Color(0xFFFFFFFF)
    val surfaceContainerLow = Color(0xFFF1F5EC)
    val surfaceContainer = Color(0xFFECEFE6)
    val surfaceContainerHigh = Color(0xFFE6E9E0)
    val surfaceContainerHighest = Color(0xFFE0E4DB)

    val onSurface = Color(0xFF000000)
    val onSurfaceVariant = Color(0xFF1F261E)
    val outline = Color(0xFF3E453C)
    val outlineVariant = Color(0xFF3E453C)

    val inverseSurface = Color(0xFF2D322C)
    val inverseOnSurface = Color(0xFFFFFFFF)
    val inversePrimary = Color(0xFFC4FABE)

    val scrim = Color(0xFF000000)

    val error = Color(0xFF4E0002)
    val onError = Color(0xFFFFFFFF)
    val errorContainer = Color(0xFF8C0009)
    val onErrorContainer = Color(0xFFFFFFFF)
}


object PrimmAdditionalColors {
    val page = PrimmScheme.surfaceContainer
    val pane = PrimmScheme.surfaceContainerLowest
    val border = PrimmScheme.surfaceDim
    val textQuiet = PrimmScheme.outline

    val consoleOutput = PrimmScheme.onSurfaceVariant
    val consoleError = PrimmScheme.errorContainer
    val consoleInput = PrimmScheme.tertiaryContainer
    val consoleAutoInput = PrimmScheme.primaryContainer
    val consoleInfo = PrimmScheme.outline

    val consoleHint = PrimmScheme.secondaryContainer

    val accent = PrimmScheme.primaryContainer

    val lockedTint = PrimmScheme.surfaceContainerHigh

    val selected = PrimmScheme.inversePrimary

    val nextLine = PrimmScheme.inversePrimary.copy(alpha = 0.75f)

    val guide = PrimmScheme.inversePrimary.copy(alpha = 0.45f)
    val guideEdge = PrimmScheme.primaryContainer
    val guideText = PrimmScheme.onPrimaryContainer

    val differs = Color(0xFFB86E00)
    val matches = PrimmScheme.primaryContainer
    val notRun = PrimmScheme.errorContainer

    val lockedRow = PrimmScheme.surfaceContainerLow

    val statusIdle = PrimmScheme.outline.copy(alpha = 0.45f)
    val statusReady = PrimmScheme.primaryContainer
    val statusRunning = PrimmScheme.tertiaryContainer
    val statusAttention = differs
    val statusProblem = PrimmScheme.errorContainer

    val diffAdded = PrimmScheme.inversePrimary
    val diffChanged = differs.copy(alpha = 0.18f)
    val diffRemoved = PrimmScheme.errorContainer.copy(alpha = 0.15f)
}

fun consoleTextStyle(fontSize: Int) = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = fontSize.sp,
    lineHeight = (fontSize * 1.45f).sp,
)

@Composable
fun PrimmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        //typography = typography(Res.font.sans2),
        colorScheme = lightColorScheme(
            primary = PrimmScheme.primary,
            onPrimary = PrimmScheme.onPrimary,
            primaryContainer = PrimmScheme.primaryContainer,
            onPrimaryContainer = PrimmScheme.onPrimaryContainer,
            inversePrimary = PrimmScheme.inversePrimary,
            secondary = PrimmScheme.secondary,
            onSecondary = PrimmScheme.onSecondary,
            secondaryContainer = PrimmScheme.secondaryContainer,
            onSecondaryContainer = PrimmScheme.onSecondaryContainer,
            tertiary = PrimmScheme.tertiary,
            onTertiary = PrimmScheme.onTertiary,
            tertiaryContainer = PrimmScheme.tertiaryContainer,
            onTertiaryContainer = PrimmScheme.onTertiaryContainer,
            background = PrimmScheme.surfaceContainer,
            onBackground = PrimmScheme.onSurface,
            surface = PrimmScheme.surfaceContainerLowest,
            onSurface = PrimmScheme.onSurface,
            surfaceVariant = PrimmScheme.surfaceContainerHighest,
            onSurfaceVariant = PrimmScheme.onSurfaceVariant,
            surfaceTint = PrimmScheme.primary,
            inverseSurface = PrimmScheme.inverseSurface,
            inverseOnSurface = PrimmScheme.inverseOnSurface,
            error = PrimmScheme.error,
            onError = PrimmScheme.onError,
            errorContainer = PrimmScheme.errorContainer,
            onErrorContainer = PrimmScheme.onErrorContainer,
            outline = PrimmScheme.outline,
            outlineVariant = PrimmScheme.surfaceDim,
            scrim = PrimmScheme.scrim,
            surfaceBright = PrimmScheme.surfaceBright,
            surfaceDim = PrimmScheme.surfaceDim,
            surfaceContainer = PrimmScheme.surfaceContainer,
            surfaceContainerHigh = PrimmScheme.surfaceContainerHigh,
            surfaceContainerHighest = PrimmScheme.surfaceContainerHighest,
            surfaceContainerLow = PrimmScheme.surfaceContainerLow,
            surfaceContainerLowest = PrimmScheme.surfaceContainerLowest,
        ),
        content = content,
    )
}

///////////////////////////////////////
// TYPOGRAPHY HELPER FROM MY WEBSITE //
///////////////////////////////////////

@Composable
fun typography(resource: FontResource) = Typography().let {
    val font = FontFamily(Font(resource))

    it.copy(
        displayLarge = it.displayLarge.copy(fontFamily = font),
        displayMedium = it.displayMedium.copy(fontFamily = font),
        displaySmall = it.displaySmall.copy(fontFamily = font),
        headlineLarge = it.headlineLarge.copy(fontFamily = font),
        headlineMedium = it.headlineMedium.copy(fontFamily = font),
        headlineSmall = it.headlineSmall.copy(fontFamily = font),
        titleLarge = it.titleLarge.copy(fontFamily = font),
        titleMedium = it.titleMedium.copy(fontFamily = font),
        titleSmall = it.titleSmall.copy(fontFamily = font),
        bodyLarge = it.bodyLarge.copy(fontFamily = font),
        bodyMedium = it.bodyMedium.copy(fontFamily = font),
        bodySmall = it.bodySmall.copy(fontFamily = font),
        labelLarge = it.labelLarge.copy(fontFamily = font),
        labelMedium = it.labelMedium.copy(fontFamily = font),
        labelSmall = it.labelSmall.copy(fontFamily = font)
    )
}
