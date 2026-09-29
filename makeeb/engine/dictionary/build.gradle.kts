plugins {
    id("makeeb.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ByteRegion appears in MappedDictionary's constructor, PrivateFiles in LearnedWordsStore's.
            api(project(":platform:storage"))
        }
        commonTest.dependencies {
            implementation(project(":testing"))
        }
    }
}
