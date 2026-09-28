import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("makeeb.kmp.library")
}

// The keyboard's composition root, without Compose. On Android it backs the IME service in
// :shared:surface; on iOS it is the only Kotlin framework the keyboard extension links,
// so it must stay free of Compose and of anything that touches UIApplication.
kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "MaKeebKeyboard"
            isStatic = true
            // Shared value types (palette, modes, suggestions) keep their plain Swift names.
            export(project(":core:model"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:settings"))
            api(project(":platform:host"))
            api(project(":platform:feedback"))
            api(project(":platform:clipboard"))
            api(project(":engine:input"))
            api(project(":engine:touch"))
            api(project(":engine:emoji"))
            api(project(":engine:clipboard"))
            implementation(project(":engine:dictionary"))
            implementation(project(":engine:prediction"))
            implementation(project(":engine:gesture"))
            implementation(project(":core:common"))
            api(libs.koin.core)
        }
    }
}
