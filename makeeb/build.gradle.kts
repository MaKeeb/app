// Top-level build file. Plugins are declared here with `apply false` and applied per module,
// directly or through the build-logic convention plugins (whose plugin dependencies are
// compileOnly and resolve from this classpath).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
