plugins {
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

val dist by tasks.registering(Sync::class) {
    group = "publishing"
    dependsOn(":webApp:wasmJsBrowserDistribution")

    from(project(":webApp").layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    into(layout.projectDirectory.dir("docs"))
}