pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MaKeeb"

// Layers, lowest first: core → platform → engine → ui → feature → shared → app. A module may
// depend on its own layer or any layer below it, never above (see .ai/instructions.md → "Module graph").

// core — foundations shared by everything
include(":core:model")
include(":core:common")
include(":core:settings")

// platform — ports onto OS services, with androidMain/iosMain adapters
include(":platform:host")
include(":platform:feedback")
include(":platform:clipboard")
include(":platform:storage")
include(":platform:network")

// engine — pure input logic (commonMain only, no Compose)
include(":engine:layout")
include(":engine:input")
include(":engine:touch")
include(":engine:dictionary")
include(":engine:prediction")
include(":engine:gesture")
include(":engine:emoji")
include(":engine:clipboard")
include(":engine:packs")

// ui — design system
include(":ui:theme")
include(":ui:components")

// feature — Compose UI slices
include(":feature:keyboard")
include(":feature:suggestions")
include(":feature:emoji")
include(":feature:clipboard")
include(":feature:settings")
include(":feature:onboarding")

// testing — fakes for the platform ports; test source sets only
include(":testing")

// tools — build-time JVM tools, outside the runtime graph: dictionary packs and the typing harness
include(":tools:dictionaries")

// shared — composition roots: wire features and engines into a keyboard and a companion app,
// and produce the iOS frameworks (MaKeebKeyboard, MaKeebCompanion)
include(":shared:keyboard")
include(":shared:surface")
include(":shared:companion")

// app — platform wrappers only: the Android application here, the Xcode project in app/ios
include(":app:android")
