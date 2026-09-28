package com.makeeb.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.versionInt(alias: String): Int =
    findVersion(alias).get().requiredVersion.toInt()

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

/** `:engine:input` → `com.makeeb.engine.input`. Kotlin packages follow the same scheme. */
internal val Project.makeebNamespace: String
    get() = "com.makeeb." + path.removePrefix(":").replace(":", ".")

/**
 * The Android target of a KMP module. AGP 9's `com.android.kotlin.multiplatform.library` is the
 * only sanctioned way to give a KMP module an Android target; it registers as `androidLibrary`.
 */
internal fun Project.configureKmpAndroidTarget(kotlin: KotlinMultiplatformExtension) {
    (kotlin as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("androidLibrary") {
        namespace = makeebNamespace
        compileSdk = libs.versionInt("compileSdk")
        minSdk = libs.versionInt("minSdk")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

/** Only arm64 targets: every supported device and Apple-silicon simulator. */
internal fun KotlinMultiplatformExtension.configureIosTargets() {
    iosArm64()
    iosSimulatorArm64()
}
