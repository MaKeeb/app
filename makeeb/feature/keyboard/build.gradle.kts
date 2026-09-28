plugins {
    id("makeeb.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":engine:touch"))
            implementation(project(":ui:theme"))
        }
    }
}
