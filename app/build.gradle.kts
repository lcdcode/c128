plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lcdcode.c128"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lcdcode.c128"
        minSdk = 26
        targetSdk = 34
        versionCode = 100
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    // Release signing is driven by the four C128_* values so the keystore
    // never lives in the repo. Each value is looked up in this order:
    //   1. Gradle project properties (e.g. ~/.gradle/gradle.properties)
    //   2. OS environment variables
    // All four must be set together; if any is missing the release config
    // falls back to unsigned (assembleRelease emits the *-unsigned apk
    // instead of failing, mirroring AGP's default behaviour).
    fun resolveSecret(name: String): String? =
        (project.findProperty(name) as? String) ?: System.getenv(name)

    val storeFilePath = resolveSecret("C128_STORE_FILE")
    val storePasswordEnv = resolveSecret("C128_STORE_PASSWORD")
    val keyAliasEnv = resolveSecret("C128_KEY_ALIAS")
    val keyPasswordEnv = resolveSecret("C128_KEY_PASSWORD")
    val haveSigningConfig = !storeFilePath.isNullOrBlank() &&
        !storePasswordEnv.isNullOrBlank() &&
        !keyAliasEnv.isNullOrBlank() &&
        !keyPasswordEnv.isNullOrBlank()

    signingConfigs {
        if (haveSigningConfig) {
            create("release") {
                storeFile = file(storeFilePath!!)
                storePassword = storePasswordEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (haveSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // APK filename: c128-<versionName>-<buildType>.apk, with a "-unsigned"
    // suffix on release builds when no signing config is wired up. That way a
    // missed env var doesn't ship a silently-unsigned artifact under the same
    // name as a properly-signed one.
    applicationVariants.all {
        val variant = this
        val unsignedReleaseSuffix =
            if (variant.buildType.name == "release" && !haveSigningConfig) "-unsigned" else ""
        outputs
            .filterIsInstance<com.android.build.gradle.internal.api.BaseVariantOutputImpl>()
            .forEach { output ->
                output.outputFileName =
                    "c128-${variant.versionName}-${variant.buildType.name}${unsignedReleaseSuffix}.apk"
            }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
}
