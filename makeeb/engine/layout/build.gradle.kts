plugins {
    id("makeeb.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            // Layout and language files (engine/layout/data, docs/layouts/schema.md).
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
