package uk.codecymru.primm.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf

object LocalAppLocaleIso {
    private val local = staticCompositionLocalOf { "en" }

    val current: String
        @Composable get() = local.current

    infix fun provides(iso: String): ProvidedValue<String> = local.provides(iso)
}
