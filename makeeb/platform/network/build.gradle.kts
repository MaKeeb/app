plugins {
    id("makeeb.kmp.library")
}

// HTTP for the companion app's downloads (dictionary packs). The keyboard runtime never depends
// on this module: nothing on the typing path may touch the network (.ai/instructions.md → Network).
