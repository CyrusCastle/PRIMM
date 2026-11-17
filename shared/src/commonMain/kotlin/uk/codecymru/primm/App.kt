package uk.codecymru.primm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.getString
import uk.codecymru.primm.controller.AppController
import uk.codecymru.primm.controller.BridgeBrowserServices
import uk.codecymru.primm.controller.BridgePythonRuntime
import uk.codecymru.primm.controller.BrowserServices
import uk.codecymru.primm.controller.LocaleController
import uk.codecymru.primm.controller.Snippets
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import uk.codecymru.primm.model.Language
import uk.codecymru.primm.model.Strings
import primm.shared.generated.resources.*
import uk.codecymru.primm.view.LocalAppLocaleIso
import uk.codecymru.primm.view.MainScreen
import uk.codecymru.primm.view.PrimmTheme

@Composable
fun App() {
    val browser = remember { BridgeBrowserServices().also(LocaleController::start) }
    val language by LocaleController.language.collectAsState()
    var loaded by remember { mutableStateOf<Language?>(null) }
    var libraryReady by remember { mutableStateOf(false) }
    LaunchedEffect(language) {
        Strings.use(Res.allStringResources.mapValues { (_, resource) -> getString(resource) }, language.iso)
        loaded = language
    }
    LaunchedEffect(Unit) {
        val problem = withTimeoutOrNull(LIBRARY_WAIT_MS) {
            suspendCancellableCoroutine { done -> Snippets.loadFrom(browser) { done.resume(it) } } ?: ""
        } ?: "they took more than ${LIBRARY_WAIT_MS / 1000} seconds to fetch"
        if (problem.isNotEmpty()) println("PRIMM: using the built-in programs: $problem")
        libraryReady = true
    }
    val shown = loaded ?: return
    if (!libraryReady) return
    PrimmApp(browser, shown)
}

private const val LIBRARY_WAIT_MS = 5_000L

@Composable
private fun PrimmApp(browser: BrowserServices, language: Language) {
    val controller = remember { AppController(BridgePythonRuntime(), browser) }
    LaunchedEffect(controller) {
        controller.start()
    }
    CompositionLocalProvider(LocalAppLocaleIso provides language.iso) {
        key(language.iso) {
            PrimmTheme {
                MainScreen(controller)
            }
        }
    }
}
