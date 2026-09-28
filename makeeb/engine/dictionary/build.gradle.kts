plugins {
    id("makeeb.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ByteRegion appears in MappedDictionary's constructor.
            api(project(":platform:storage"))
        }
    }
}
