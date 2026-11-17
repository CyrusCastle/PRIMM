package uk.codecymru.primm.controller

import uk.codecymru.primm.model.FetchFailure
import primm.shared.generated.resources.*
import uk.codecymru.primm.model.tr

sealed interface FetchResult {
    data class Success(val text: String, val contentType: String) : FetchResult

    data class Failure(val failure: FetchFailure) : FetchResult
}

sealed interface FilePick {
    data class Picked(val name: String, val text: String) : FilePick

    data object Cancelled : FilePick

    data class Failed(val message: String) : FilePick
}

sealed interface ImageResult {
    data object Done : ImageResult

    data class Failed(val message: String) : ImageResult
}

interface BrowserServices {
    fun readSetting(key: String): String?

    fun writeSetting(key: String, value: String)

    fun queryString(): String

    fun replaceQueryString(query: String)

    fun pageAddress(): String

    fun copyText(text: String, onResult: (Boolean) -> Unit)

    fun fetchText(url: String, onResult: (FetchResult) -> Unit)

    fun downloadText(fileName: String, text: String, mimeType: String)

    fun pickTextFile(accept: String, onResult: (FilePick) -> Unit)

    fun today(): String

    fun browserLanguages(): List<String>

    fun useLanguage(iso: String)

    fun clockTime(): String

    fun printReport(html: String)

    fun saveReportImage(html: String, fileName: String, copyToClipboard: Boolean, onResult: (ImageResult) -> Unit)
}

class BridgeBrowserServices : BrowserServices {
    override fun readSetting(key: String): String? = bridgeStorageGet(key)

    override fun writeSetting(key: String, value: String) {
        bridgeStorageSet(key, value)
    }

    override fun queryString(): String = bridgeGetQuery()

    override fun replaceQueryString(query: String) {
        bridgeReplaceQuery(query)
    }

    override fun pageAddress(): String = bridgePageAddress()

    override fun copyText(text: String, onResult: (Boolean) -> Unit) {
        bridgeCopyText(text) { result -> onResult(result == "ok") }
    }

    override fun fetchText(url: String, onResult: (FetchResult) -> Unit) {
        bridgeFetchText(url) { json -> onResult(FetchResultDecoder.decode(json)) }
    }

    override fun downloadText(fileName: String, text: String, mimeType: String) {
        bridgeDownloadText(fileName, text, mimeType)
    }

    override fun pickTextFile(accept: String, onResult: (FilePick) -> Unit) {
        bridgePickTextFile(accept) { json -> onResult(FilePickDecoder.decode(json)) }
    }

    override fun today(): String = bridgeToday()

    override fun browserLanguages(): List<String> =
        ((runCatching { JsonReader.parse(bridgeBrowserLanguages()) }.getOrNull() as? JsonArray)?.items ?: emptyList())
            .mapNotNull { (it as? JsonString)?.value }

    override fun useLanguage(iso: String) {
        bridgeUseLanguage(iso)
    }

    override fun clockTime(): String = bridgeClockTime()

    override fun printReport(html: String) {
        bridgePrintReport(html)
    }

    override fun saveReportImage(html: String, fileName: String, copyToClipboard: Boolean, onResult: (ImageResult) -> Unit) {
        bridgeReportImage(html, REPORT_WIDTH, if (copyToClipboard) "clipboard" else "download", fileName) { json ->
            onResult(ImageResultDecoder.decode(json))
        }
    }

    private companion object {
        const val REPORT_WIDTH = 900 // px
    }
}

object ImageResultDecoder {
    fun decode(json: String): ImageResult {
        val parsed: JsonValue? = try {
            JsonReader.parse(json)
        } catch (e: JsonParseException) {
            null
        }
        val result = parsed as? JsonObject ?: return ImageResult.Failed(FAILED)
        if (result.boolean("ok") == true) return ImageResult.Done
        return ImageResult.Failed(
            when (result.string("reason")) {
                "tooTall" -> tr(Res.string.SAVE_IMAGE_TOO_TALL)
                "clipboard" -> tr(Res.string.SAVE_IMAGE_CLIPBOARD_BLOCKED)
                else -> FAILED
            },
        )
    }

    private val FAILED: String get() = tr(Res.string.SAVE_IMAGE_FAILED)
}

object FilePickDecoder {
    fun decode(json: String): FilePick {
        val parsed: JsonValue? = try {
            JsonReader.parse(json)
        } catch (e: JsonParseException) {
            null
        }
        val result = parsed as? JsonObject ?: return FilePick.Failed(UNREADABLE)
        if (result.boolean("ok") == true) {
            return FilePick.Picked(result.string("name") ?: "", result.string("text") ?: "")
        }
        return when (result.string("reason")) {
            "cancelled" -> FilePick.Cancelled
            "tooLarge" -> FilePick.Failed(tr(Res.string.SESSION_FILE_TOO_BIG))
            else -> FilePick.Failed(UNREADABLE)
        }
    }

    private val UNREADABLE: String get() = tr(Res.string.FILE_UNREADABLE)
}

object FetchResultDecoder {
    fun decode(json: String): FetchResult {
        val parsed = try {
            JsonReader.parse(json)
        } catch (e: JsonParseException) {
            return FetchResult.Failure(FetchFailure.Blocked)
        }
        val result = parsed as? JsonObject ?: return FetchResult.Failure(FetchFailure.Blocked)

        if (result.boolean("ok") == true) {
            return FetchResult.Success(
                text = result.string("text") ?: "",
                contentType = result.string("contentType") ?: "",
            )
        }
        val failure = when (result.string("kind")) {
            "http" -> FetchFailure.Http(result.int("status") ?: 0)
            "tooLarge" -> FetchFailure.TooLarge
            else -> FetchFailure.Blocked
        }
        return FetchResult.Failure(failure)
    }
}
