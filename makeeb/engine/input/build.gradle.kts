plugins {
    id("makeeb.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:settings"))
            api(project(":platform:host"))
            api(project(":engine:layout"))
            api(project(":engine:prediction"))
            implementation(project(":core:common"))
        }
        commonTest.dependencies {
            implementation(project(":testing"))
        }
    }
}
