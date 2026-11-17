package uk.codecymru.primm.controller

import uk.codecymru.primm.model.Snippet
import uk.codecymru.primm.model.SnippetLibrary
import uk.codecymru.primm.model.Topic


object Snippets {
    const val FOLDER = "primm/snippets/"

    private var current: SnippetLibrary? = null

    val library: SnippetLibrary
        get() = current ?: SnippetLibraryReader.read(bundledSnippetFiles).also { current = it }

    fun use(library: SnippetLibrary) {
        current = library
    }

    val all: List<Snippet> get() = library.snippets

    val topics: List<Topic> get() = library.topics

    val starter: Snippet get() = library.starter

    fun byId(id: String): Snippet? = library.byId(id)

    fun search(query: String): List<Snippet> = library.search(query)

    fun grouped(snippets: List<Snippet> = all): List<Pair<Topic, List<Snippet>>> = library.grouped(snippets)

    fun loadFrom(browser: BrowserServices, folder: String = FOLDER, onDone: (problem: String?) -> Unit) {
        browser.fetchText(folder + SnippetLibraryReader.INDEX) { result ->
            val index = (result as? FetchResult.Success)?.text
                ?: return@fetchText onDone("couldn't fetch ${folder}${SnippetLibraryReader.INDEX}")
            val needed = try {
                SnippetLibraryReader.neededFiles(index)
            } catch (e: SnippetLibraryException) {
                return@fetchText onDone(e.message)
            }
            val files = linkedMapOf(SnippetLibraryReader.INDEX to index)
            var waiting = needed.size
            var problem: String? = null
            fun finish() {
                if (problem == null) {
                    try {
                        use(SnippetLibraryReader.read(files))
                    } catch (e: SnippetLibraryException) {
                        problem = e.message
                    }
                }
                onDone(problem)
            }
            if (needed.isEmpty()) return@fetchText finish()
            for (path in needed) {
                browser.fetchText(folder + path) { file ->
                    when (file) {
                        is FetchResult.Success -> files[path] = file.text
                        is FetchResult.Failure -> problem = problem ?: "couldn't fetch $folder$path"
                    }
                    waiting--
                    if (waiting == 0) finish()
                }
            }
        }
    }
}
