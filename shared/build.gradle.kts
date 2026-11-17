import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js {
        browser {
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
        binaries.executable()
    }
    
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("br.com.devsrsouza.compose.icons:font-awesome:1.1.1")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
        }
    }
}
//compose.resources {
//    packageOfResClass = "uk.codecymru.primm.resources"
//}

// We need to generate a file containing our strings for outside of composables
val generateEnglishStrings by tasks.registering {
    val source = layout.projectDirectory.file("src/commonMain/composeResources/values/strings.xml")
    val target = layout.buildDirectory.dir("generated/englishStrings/kotlin")
    inputs.file(source)
    outputs.dir(target)
    doLast {
        fun resourceText(raw: String): String {
            val xml = raw
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&")
            val out = StringBuilder()
            var i = 0
            while (i < xml.length) {
                val c = xml[i]
                if (c == '\\' && i + 1 < xml.length) {
                    when (val next = xml[i + 1]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        '\\' -> out.append('\\')
                        else -> out.append('\\').append(next)
                    }
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }

        fun kotlinLiteral(text: String): String = buildString {
            append('"')
            for (c in text) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\$")
                    '\n' -> append("\\n")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
            append('"')
        }

        val entry = Regex("""<string\s+name="([A-Za-z0-9_]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val entries = entry.findAll(source.asFile.readText()).joinToString("\n") { match ->
            "    \"${match.groupValues[1]}\" to ${kotlinLiteral(resourceText(match.groupValues[2]))},"
        }
        val file = target.get().file("uk/codecymru/primm/model/EnglishStrings.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package uk.codecymru.primm.model\n\n" +
                "// Generated from composeResources/values/strings.xml by generateEnglishStrings. Do not edit.\n" +
                "internal val englishStrings: Map<String, String> = mapOf(\n$entries\n)\n",
        )
    }
}

// For tests and fallbacks
val generateBundledSnippets by tasks.registering {
    val source = rootProject.layout.projectDirectory.dir("webApp/src/webMain/resources/primm/snippets")
    val target = layout.buildDirectory.dir("generated/bundledSnippets/kotlin")
    inputs.dir(source)
    outputs.dir(target)
    doLast {
        fun kotlinLiteral(text: String): String = buildString {
            append('"')
            for (c in text) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\$")
                    '\n' -> append("\\n")
                    '\t' -> append("\\t")
                    '\r' -> Unit
                    else -> append(c)
                }
            }
            append('"')
        }

        val root = source.asFile
        val entries = root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
            .sortedBy { it.first }
            .joinToString("\n") { (path, text) -> "    ${kotlinLiteral(path)} to ${kotlinLiteral(text)}," }
        val file = target.get().file("uk/codecymru/primm/controller/BundledSnippets.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package uk.codecymru.primm.controller\n\n" +
                "// Generated from webApp/src/webMain/resources/primm/snippets by generateBundledSnippets. Do not edit.\n" +
                "internal val bundledSnippetFiles: Map<String, String> = mapOf(\n$entries\n)\n",
        )
    }
}

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generateEnglishStrings)
    kotlin.srcDir(generateBundledSnippets)
}
