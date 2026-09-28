plugins {
    `kotlin-dsl`
}

// The plugin artifacts are compileOnly: at runtime they resolve from the main build's classpath,
// where the root build.gradle.kts declares them with `apply false`.
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeMultiplatform.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "makeeb.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "makeeb.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("androidApplication") {
            id = "makeeb.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
    }
}
