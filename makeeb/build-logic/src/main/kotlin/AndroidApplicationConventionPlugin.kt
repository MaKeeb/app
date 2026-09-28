import com.android.build.api.dsl.ApplicationExtension
import com.makeeb.buildlogic.libs
import com.makeeb.buildlogic.versionInt
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention for `:app:android`. AGP 9 has Kotlin built in, so kotlin-android is not applied.
 * The module is a thin shell (manifest, resources, Application class); UI and the IME service
 * live in the KMP app modules.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                compileSdk = libs.versionInt("compileSdk")
                defaultConfig.minSdk = libs.versionInt("minSdk")
                defaultConfig.targetSdk = libs.versionInt("targetSdk")

                compileOptions.sourceCompatibility = JavaVersion.VERSION_17
                compileOptions.targetCompatibility = JavaVersion.VERSION_17

                packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
    }
}
