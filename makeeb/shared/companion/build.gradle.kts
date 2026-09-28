import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("makeeb.kmp.compose")
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
        commonMain.dependencies {
            implementation(project(":core:settings"))
            implementation(project(":engine:layout"))
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
