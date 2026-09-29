plugins {
    id("makeeb.kmp.library")
}

// Downloadable dictionary packs: the catalogue the release publishes and :tools:dictionaries
// writes, and the companion app's installer. The keyboard runtime doesn't depend on this module
// (it only maps installed packs, :engine:dictionary), so nothing on the typing path can reach
// the network.
kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(project(":testing"))
        }
    }
}
