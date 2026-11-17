package uk.codecymru.primm.controller

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.codecymru.primm.model.Language

object LocaleController {
    const val KEY_LANGUAGE = "language"

    private val mutableLanguage = MutableStateFlow(Language.ENGLISH)
    val language: StateFlow<Language> = mutableLanguage.asStateFlow()

    private var browser: BrowserServices? = null

    fun start(browser: BrowserServices) {
        this.browser = browser
        mutableLanguage.value = Language.preferred(browser.readSetting(KEY_LANGUAGE), browser.browserLanguages())
        browser.useLanguage(mutableLanguage.value.iso)
    }

    fun setLanguage(language: Language) {
        browser?.writeSetting(KEY_LANGUAGE, language.iso)
        browser?.useLanguage(language.iso)
        mutableLanguage.value = language
    }

    fun toggle() = setLanguage(language.value.other)
}
