import com.makeeb.buildlogic.configureIosTargets
import com.makeeb.buildlogic.configureKmpAndroidTarget
import com.makeeb.buildlogic.libs
import com.makeeb.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention for Compose Multiplatform modules (`:ui:*`, `:feature:*`, `:shared:surface`,
 * `:shared:companion`): Android and iOS, with the CMP runtime/foundation/ui/material3 in commonMain.
 *
 * No JVM target: UI is verified on device/simulator, and logic worth unit-testing belongs in an
 * engine module anyway.
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("org.jetbrains.compose")

            extensions.configure<KotlinMultiplatformExtension> {
                configureKmpAndroidTarget(this)
                configureIosTargets()

                sourceSets.apply {
                    commonMain.get().dependencies {
                        implementation(libs.library("compose-runtime"))
                        implementation(libs.library("compose-foundation"))
                        implementation(libs.library("compose-ui"))
                        implementation(libs.library("compose-material3"))
                        implementation(libs.library("kotlinx-coroutines-core"))
                    }
                    commonTest.get().dependencies {
                        implementation(kotlin("test"))
                    }
                }
            }
        }
    }
}
