import com.makeeb.buildlogic.configureIosTargets
import com.makeeb.buildlogic.configureKmpAndroidTarget
import com.makeeb.buildlogic.libs
import com.makeeb.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

/**
 * Convention for non-UI modules (`:core:*`, `:platform:*`, `:engine:*`): Android, JVM and iOS.
 *
 * The JVM target exists for fast host-side unit tests (`./gradlew jvmTest`); nothing ships on
 * it. Engine modules keep all code in commonMain. Platform modules put their adapters in
 * androidMain/iosMain and have no JVM adapter (tests use fakes from `:core:testing`).
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")

            extensions.configure<KotlinMultiplatformExtension> {
                configureKmpAndroidTarget(this)
                jvm {
                    @OptIn(ExperimentalKotlinGradlePluginApi::class)
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                }
                configureIosTargets()

                sourceSets.apply {
                    commonMain.get().dependencies {
                        implementation(libs.library("kotlinx-coroutines-core"))
                    }
                    commonTest.get().dependencies {
                        implementation(kotlin("test"))
                        implementation(libs.library("kotlinx-coroutines-test"))
                    }
                }
            }

            // commonTest also runs on the iOS simulator (Kotlin/Native), the runtime the keyboard
            // extension uses. Run it through scripts/ios-sim-test.sh, which boots the device first:
            // standalone spawning (`simctl spawn --standalone`) hangs with the Xcode 27 simulator.
            tasks.withType<KotlinNativeSimulatorTest>().configureEach {
                device.set(providers.gradleProperty("makeeb.iosTestDevice").orElse(DEFAULT_IOS_TEST_DEVICE))
                standalone.set(false)
            }
        }
    }

    private companion object {
        /** A simulator every current Xcode ships; override with -Pmakeeb.iosTestDevice=<name or UDID>. */
        const val DEFAULT_IOS_TEST_DEVICE = "iPhone 17"
    }
}
