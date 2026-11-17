package uk.codecymru.primm.controller

// Global functions defined in resources/primm/bridge.js.
private external fun primmStorageGet(key: String): String?
private external fun primmStorageSet(key: String, value: String)
private external fun primmGetQuery(): String
private external fun primmPageAddress(): String
private external fun primmCopyText(text: String, onResult: (String) -> Unit)
private external fun primmReplaceQuery(query: String)
private external fun primmFetchText(url: String, onResult: (String) -> Unit)
private external fun primmDownloadText(fileName: String, text: String, mimeType: String)
private external fun primmPickTextFile(accept: String, onResult: (String) -> Unit)
private external fun primmToday(): String
private external fun primmBrowserLanguages(): String
private external fun primmUseLanguage(iso: String)
private external fun primmClockTime(): String
private external fun primmPrintReport(html: String)
private external fun primmReportImage(html: String, width: Int, action: String, fileName: String, onResult: (String) -> Unit)

internal actual fun bridgeStorageGet(key: String): String? = primmStorageGet(key)

internal actual fun bridgeStorageSet(key: String, value: String) {
    primmStorageSet(key, value)
}

internal actual fun bridgeGetQuery(): String = primmGetQuery()

internal actual fun bridgePageAddress(): String = primmPageAddress()

internal actual fun bridgeCopyText(text: String, onResult: (String) -> Unit) {
    primmCopyText(text, onResult)
}

internal actual fun bridgeReplaceQuery(query: String) {
    primmReplaceQuery(query)
}

internal actual fun bridgeFetchText(url: String, onResult: (String) -> Unit) {
    primmFetchText(url, onResult)
}

internal actual fun bridgeDownloadText(fileName: String, text: String, mimeType: String) {
    primmDownloadText(fileName, text, mimeType)
}

internal actual fun bridgePickTextFile(accept: String, onResult: (String) -> Unit) {
    primmPickTextFile(accept, onResult)
}

internal actual fun bridgeToday(): String = primmToday()

internal actual fun bridgeBrowserLanguages(): String = primmBrowserLanguages()

internal actual fun bridgeUseLanguage(iso: String) {
    primmUseLanguage(iso)
}

internal actual fun bridgeClockTime(): String = primmClockTime()

internal actual fun bridgePrintReport(html: String) {
    primmPrintReport(html)
}

internal actual fun bridgeReportImage(html: String, width: Int, action: String, fileName: String, onResult: (String) -> Unit) {
    primmReportImage(html, width, action, fileName, onResult)
}
