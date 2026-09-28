plugins {
    id("makeeb.kmp.compose")
}

// The Compose keyboard surface: strip + keys + panels over a KeyboardSession. The Android IME
// service in app/android hosts it. It also compiles for iOS, but the iOS extension does not link it today:
// Compose Multiplatform is not extension-safe yet (docs/research/platform-apis.md). Keeping the
// iOS targets green means a Compose-in-extension spike is only a linking change away.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:keyboard"))
            implementation(project(":ui:theme"))
            implementation(project(":feature:keyboard"))
            implementation(project(":feature:suggestions"))
            implementation(project(":feature:emoji"))
            implementation(project(":feature:clipboard"))
        }
    }
}
