plugins {
    id("makeeb.android.application")
    // The IME service and MainActivity host Compose content.
    alias(libs.plugins.kotlin.compose)
}

// The Android wrapper: manifest, resources, signing and packaging, plus the three Android entry
// points (Application, MaKeebInputMethodService, MainActivity), mirroring app/ios. Everything
// they host lives in the shared KMP modules.
android {
    namespace = "com.makeeb.android"

    defaultConfig {
        applicationId = "com.makeeb"
        versionCode = 1
        versionName = "0.1.0"
    }

    // Dictionary packs are memory-mapped straight out of the APK (AssetBundledFiles), which only
    // works for assets stored uncompressed.
    androidResources {
        noCompress += "mkd"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Release code (R8, not debuggable) signed with the debug key and installed alongside the
        // debug app, for measuring frame times and latency: debug Compose is several times slower.
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
}

// The bundled dictionary packs (en_US.mkd), built from pinned sources at build time rather than
// committed. -Pmakeeb.dictionaries=false leaves them out (offline builds); the keyboard then falls
// back to its starter word list.
val dictionaryPacks = configurations.dependencyScope("dictionaryPacks")
val dictionaryPackFiles = configurations.resolvable("dictionaryPackFiles") {
    extendsFrom(dictionaryPacks.get())
    attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, "makeeb-dictionary-packs")) }
}
if (providers.gradleProperty("makeeb.dictionaries").orNull != "false") {
    dependencies { add(dictionaryPacks.name, project(":tools:dictionaries")) }
    val dictionaryAssets = tasks.register<DictionaryAssets>("dictionaryAssets") {
        packs.from(dictionaryPackFiles)
    }
    androidComponents {
        onVariants { variant ->
            variant.sources.assets?.addGeneratedSourceDirectory(dictionaryAssets, DictionaryAssets::outputDirectory)
        }
    }
}

dependencies {
    implementation(project(":shared:surface"))
    implementation(project(":shared:companion"))
    implementation(project(":core:settings"))
    implementation(libs.koin.android)

    // Compose hosting: ComposeView in the IME window, setContent in the activity.
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    // The IME service is its own LifecycleOwner / ViewModelStoreOwner / SavedStateRegistryOwner.
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
}

/** Copies the dictionary packs built by :tools:dictionaries into a generated assets directory. */
abstract class DictionaryAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun copy() {
        files.sync {
            from(packs)
            into(outputDirectory)
        }
    }
}
