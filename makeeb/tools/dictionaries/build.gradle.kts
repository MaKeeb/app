import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Build-time JVM tools for dictionary packs, outside the runtime graph (nothing ships this code).
//
// - `dictionaryPacks` downloads each pinned source into build/downloads (cached, SHA-256 checked)
//   and writes the bundled packs to build/packs. app/android packages them as uncompressed
//   assets; the iOS keyboard extension bundles them (app/ios/project.yml). The first build also
//   downloads the next-word corpora (about 520 MB) and counts them (about a minute); the counts
//   are cached in build/downloads. -Pmakeeb.ngrams=false builds packs without next-word data.
// - `languagePacks` builds the packs the companion app downloads (every other language) into
//   build/language-packs, from their own pinned sources (docs/dictionaries/mkd-format.md).
// - `typingHarness` measures the suggestion engine on the en_US pack (src/test, since it drives
//   the engine through the :testing fakes), including next-word predictions on sentences held
//   out of the counts (build/heldout).
// The pack codec itself lives in :engine:dictionary, so the builder and the keyboard share it.

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":engine:dictionary"))
    implementation(project(":core:common"))

    testImplementation(kotlin("test"))
    testImplementation(project(":engine:prediction"))
    testImplementation(project(":engine:input"))
    testImplementation(project(":engine:layout"))
    testImplementation(project(":core:model"))
    testImplementation(project(":core:settings"))
    testImplementation(project(":testing"))
    testImplementation(libs.kotlinx.coroutines.core)
}

val packsDirectory = layout.buildDirectory.dir("packs")
val downloadsDirectory = layout.buildDirectory.dir("downloads")
val heldOutDirectory = layout.buildDirectory.dir("heldout")
val ngrams = providers.gradleProperty("makeeb.ngrams").orNull != "false"

val dictionaryPacks = tasks.register<JavaExec>("dictionaryPacks") {
    group = "build"
    description = "Builds the bundled dictionary packs (en_US.mkd) from their pinned sources."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.makeeb.tools.dictionaries.DictionaryBuilderKt")
    // Counting the corpora holds their 42M word ids and one n-gram key per word.
    maxHeapSize = "1536m"
    args(
        "--out", packsDirectory.get().asFile.absolutePath,
        "--cache", downloadsDirectory.get().asFile.absolutePath,
        "--heldout", heldOutDirectory.get().asFile.absolutePath,
        "--ngrams", ngrams.toString(),
    )
    inputs.property("ngrams", ngrams)
    outputs.dir(packsDirectory)
    outputs.dir(heldOutDirectory)
}

// The packs the companion app downloads (every language but English), in their own directory so
// app/android never bundles them. Only this task downloads their sources: an AOSP word list
// (about 1 MB) and a Leipzig news corpus (about 250 MB) per language, cached like English's.
val languagePacksDirectory = layout.buildDirectory.dir("language-packs")
val languagePacks = tasks.register<JavaExec>("languagePacks") {
    group = "build"
    description = "Builds the downloadable language packs (de, es, fr, it, nl, pl, pt_BR, sv, hu) from their pinned sources."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.makeeb.tools.dictionaries.DictionaryBuilderKt")
    // Counting Hungarian words holds a million distinct forms before the cuts.
    maxHeapSize = "3g"
    // -Pmakeeb.languagePacks=hu,sv rebuilds only those (by file stem) while tuning one.
    val which = providers.gradleProperty("makeeb.languagePacks").orNull ?: "downloadable"
    args(
        "--packs", which,
        "--out", languagePacksDirectory.get().asFile.absolutePath,
        "--cache", downloadsDirectory.get().asFile.absolutePath,
        "--heldout", heldOutDirectory.get().asFile.absolutePath,
        "--ngrams", ngrams.toString(),
    )
    inputs.property("ngrams", ngrams)
    inputs.property("packs", which)
    outputs.dir(languagePacksDirectory)
}

// What app/android resolves to package the packs as assets.
configurations.consumable("dictionaryPackElements") {
    attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, "makeeb-dictionary-packs")) }
    outgoing.artifact(packsDirectory) { builtBy(dictionaryPacks) }
}

tasks.register<JavaExec>("typingHarness") {
    group = "verification"
    description = "Simulated typing on the en_US pack: keystroke savings, typo fixes, false corrections. " +
        "-Pharness.pack=starter measures the 250-word starter list instead."
    dependsOn(dictionaryPacks)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.makeeb.tools.dictionaries.harness.TypingHarnessKt")
    val pack = providers.gradleProperty("harness.pack").orNull ?: packsDirectory.get().file("en_US.mkd").asFile.absolutePath
    args("--pack", pack, "--heldout", heldOutDirectory.get().file("en_US-heldout.txt").asFile.absolutePath)
}

// `./gradlew jvmTest` is the project's "all JVM unit tests" command; include this module's.
tasks.register("jvmTest") {
    group = "verification"
    description = "Runs this module's tests (alias of test)."
    dependsOn(tasks.test)
}

