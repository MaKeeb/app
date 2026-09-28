plugins {
    id("makeeb.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":engine:layout"))
            api(project(":engine:dictionary"))
        }
    }
}
