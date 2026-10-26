import org.jetbrains.kotlin.konan.properties.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    base
}

base {
    archivesName.set("chatsim")
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// 版本号唯一来源：分支上最新的 release(<major.minor.patch>[-<suffix>]): ... 提交；
// git 不可用时回退到最新的 v<major.minor.patch>[-<suffix>] 标签；仍没有则回退 0.0.0 / versionCode 1。
// 可用 -PappVersionName / -PappVersionCode 显式覆盖（例如脱离 git 的构建）。
val releaseVersionPattern = Regex("""^release\(v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?\)""")
val tagVersionPattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$""")

val gitLogVersion: List<String>? = runCatching {
    providers.exec {
        workingDir(rootProject.projectDir)
        commandLine("git", "log", "--format=%s")
    }
        .standardOutput.asText.get()
        .lineSequence()
        .mapNotNull { releaseVersionPattern.find(it.trim()) }
        .firstOrNull()
        ?.groupValues
        ?.drop(1)
}.getOrNull()

val gitTagVersion: List<String>? = runCatching {
    val tag = providers.exec {
        workingDir(rootProject.projectDir)
        commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")
    }
        .standardOutput.asText.get()
        .trim()
    tagVersionPattern.find(tag)?.groupValues?.drop(1)
}.getOrNull()

val gitReleaseVersion: List<String>? = gitLogVersion ?: gitTagVersion
val versionNumbers: List<Int>? = gitReleaseVersion?.take(3)?.map { it.toInt() }
val versionSuffix: String? = gitReleaseVersion?.getOrNull(3)?.takeIf { it.isNotBlank() }

val overrideVersionName: String? = providers.gradleProperty("appVersionName").orNull
val overrideVersionCode: String? = providers.gradleProperty("appVersionCode").orNull

val derivedVersionName: String = versionNumbers?.joinToString(".")?.let { base ->
    if (versionSuffix != null) "$base-$versionSuffix" else base
} ?: "0.0.0"
val derivedVersionCode: Int = versionNumbers
    ?.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }
    ?: 1

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = libs.versions.app.version.appId.get()
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionName = overrideVersionName ?: derivedVersionName
        versionCode = overrideVersionCode?.toInt() ?: derivedVersionCode
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    androidResources {
        localeFilters += listOf("en", "zh-rCN")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("variants")
    productFlavors {
        register("core")
        register("fdroid")
    }

    compileOptions {
        val currentJavaVersionFromLibs = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    namespace = libs.versions.app.version.appId.get()

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.android.smsmms)
    implementation(libs.shortcut.badger)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.ez.vcard)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.google.android.material)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
