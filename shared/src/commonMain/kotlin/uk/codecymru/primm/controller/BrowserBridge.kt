package uk.codecymru.primm.controller

// The browser-service functions in webApp/src/webMain/resources/primm/bridge.js.
internal expect fun bridgeStorageGet(key: String): String?

internal expect fun bridgeStorageSet(key: String, value: String)

internal expect fun bridgeGetQuery(): String

internal expect fun bridgePageAddress(): String

internal expect fun bridgeCopyText(text: String, onResult: (String) -> Unit)

internal expect fun bridgeReplaceQuery(query: String)

internal expect fun bridgeFetchText(url: String, onResult: (String) -> Unit)

internal expect fun bridgeDownloadText(fileName: String, text: String, mimeType: String)

internal expect fun bridgePickTextFile(accept: String, onResult: (String) -> Unit)

internal expect fun bridgeToday(): String

internal expect fun bridgeBrowserLanguages(): String

internal expect fun bridgeUseLanguage(iso: String)

internal expect fun bridgeClockTime(): String

internal expect fun bridgePrintReport(html: String)

internal expect fun bridgeReportImage(html: String, width: Int, action: String, fileName: String, onResult: (String) -> Unit)
