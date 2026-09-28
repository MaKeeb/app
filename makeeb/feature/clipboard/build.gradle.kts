plugins {
    id("makeeb.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":engine:clipboard"))
            implementation(project(":ui:theme"))
        }
    }
}
