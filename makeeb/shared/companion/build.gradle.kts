import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("makeeb.kmp.compose")
}

// Where the dictionary packs are published (gradle.properties, makeeb.packs.catalogueUrl), as the
// constant PackHosting.CATALOGUE_URL. Empty: this build offers no downloads. Override it with -P
// or ~/.gradle/gradle.properties to test against a local server (docs/dictionaries/pack-catalogue.md).
val packHosting = tasks.register<GeneratePackHosting>("generatePackHosting") {
    catalogueUrl.set(providers.gradleProperty("makeeb.packs.catalogueUrl").orElse(""))
    outputDirectory.set(layout.buildDirectory.dir("generated/packHosting/kotlin"))
}

// The companion app: setup, settings, a field to try the keyboard. app/android's MainActivity
// hosts CompanionApp(); iOS links the "MaKeebCompanion" framework and calls MainViewController().
kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "MaKeebCompanion"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(packHosting)
        }
        commonMain.dependencies {
            implementation(project(":core:settings"))
            implementation(project(":engine:layout"))
            implementation(project(":engine:packs"))
            implementation(project(":platform:network"))
            implementation(project(":platform:storage"))
            implementation(project(":ui:theme"))
            implementation(project(":ui:components"))
            implementation(project(":feature:settings"))
            implementation(project(":feature:onboarding"))
            api(libs.koin.core)
            implementation(libs.koin.compose)
            // CompanionApp is hosted by app/android and the iOS app: its Compose API is public.
            api(libs.compose.runtime)
            api(libs.compose.ui)
        }
    }
}

/** Writes `PackHosting.kt`: the catalogue URL as a compile-time constant. */
abstract class GeneratePackHosting : DefaultTask() {
    @get:Input
    abstract val catalogueUrl: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val url = catalogueUrl.get().replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        val file = outputDirectory.get().file("com/makeeb/shared/companion/PackHosting.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.makeeb.shared.companion
            |
            |/** Generated from the Gradle property `makeeb.packs.catalogueUrl` (shared/companion/build.gradle.kts). */
            |internal object PackHosting {
            |    /** The dictionary pack catalogue; empty when this build offers no downloads. */
            |    const val CATALOGUE_URL: String = "$url"
            |}
            |
            """.trimMargin(),
        )
    }
}
