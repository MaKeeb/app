plugins {
    id("makeeb.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":engine:emoji"))
            implementation(project(":ui:theme"))
        }
    }
}
