plugins {
    id("makeeb.kmp.library")
}

// Fakes for the platform ports. Depend on this from commonTest source sets only.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":platform:host"))
            api(project(":platform:clipboard"))
            api(project(":core:settings"))
            implementation(project(":core:common"))
        }
    }
}
