plugins {
    id("makeeb.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(project(":ui:theme"))
        }
    }
}
