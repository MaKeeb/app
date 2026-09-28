import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Build-time JVM tools for dictionary packs, outside the runtime graph (nothing ships this code).
//
// - `dictionaryPacks` downloads each pinned source into build/downloads (cached, SHA-256 checked)
//   and writes the bundled packs to build/packs. app/android packages them as uncompressed
//   assets; the iOS keyboard extension bundles them (app/ios/project.yml).
// - `typingHarness` measures the suggestion engine on the en_US pack (src/test, since it drives
//   the engine through the :testing fakes).
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

val dictionaryPacks = tasks.register<JavaExec>("dictionaryPacks") {
    group = "build"
    description = "Builds the bundled dictionary packs (en_US.mkd) from their pinned sources."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.makeeb.tools.dictionaries.DictionaryBuilderKt")
    maxHeapSize = "1g"
    args("--out", packsDirectory.get().asFile.absolutePath, "--cache", downloadsDirectory.get().asFile.absolutePath)
    outputs.dir(packsDirectory)
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
    args("--pack", pack)
}

// `./gradlew jvmTest` is the project's "all JVM unit tests" command; include this module's.
tasks.register("jvmTest") {
    group = "verification"
    description = "Runs this module's tests (alias of test)."
    dependsOn(tasks.test)
}
