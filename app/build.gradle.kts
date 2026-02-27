import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.walkietalkie.dictationime"
    compileSdk = 35

    val releaseSigningProperties = Properties().apply {
        val file = rootProject.file("release-signing.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }

    fun envOrGradle(key: String): String? =
        System.getenv(key)
            ?: (project.findProperty(key) as String?)
            ?: releaseSigningProperties.getProperty(key)

    val appMode = (envOrGradle("APP_MODE") ?: "dev").trim().lowercase()
    val isOpenSourceMode = appMode == "dev" || appMode == "open_source" || appMode == "oss"
    val appPropertiesFile = envOrGradle("APP_PROPERTIES_FILE") ?: if (isOpenSourceMode) "gradle.properties.dev" else ""
    val modeProperties = Properties().apply {
        if (appPropertiesFile.isNotBlank()) {
            val modeFile = rootProject.file(appPropertiesFile)
            if (modeFile.exists()) {
                modeFile.inputStream().use { load(it) }
            }
        }
    }
    fun envOrProp(key: String): String? =
        System.getenv(key)
            ?: (project.findProperty(key) as String?)
            ?: modeProperties.getProperty(key)

    val openAiApiKey = ""
    val openAiBaseUrl = envOrProp("OPENAI_BASE_URL") ?: "https://api.openai.com/v1"
    val backendBaseUrl = envOrProp("BACKEND_BASE_URL") ?: ""
    val enableBackendFeatures = (!isOpenSourceMode).toString()
    val requireAuth = (!isOpenSourceMode).toString()
    val isReleaseBuild = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
    val releaseStoreFile = envOrGradle("RELEASE_STORE_FILE")
    val releaseStorePassword = envOrGradle("RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = envOrGradle("RELEASE_KEY_ALIAS")
    val releaseKeyPassword = envOrGradle("RELEASE_KEY_PASSWORD")

    defaultConfig {
        applicationId = "com.walkietalkie.dictationime"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "OPENAI_API_KEY", "\"${openAiApiKey}\"")
        buildConfigField("String", "OPENAI_BASE_URL", "\"${openAiBaseUrl}\"")
        buildConfigField("String", "BACKEND_BASE_URL", "\"${backendBaseUrl}\"")
        buildConfigField("String", "APP_MODE", "\"${appMode}\"")
        buildConfigField("boolean", "ENABLE_BACKEND_FEATURES", enableBackendFeatures)
        buildConfigField("boolean", "REQUIRE_AUTH", requireAuth)
    }

    signingConfigs {
        create("release") {
            if (
                !releaseStoreFile.isNullOrBlank() &&
                !releaseStorePassword.isNullOrBlank() &&
                !releaseKeyAlias.isNullOrBlank() &&
                !releaseKeyPassword.isNullOrBlank()
            ) {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (isReleaseBuild) {
                require(appMode == "prod") {
                    "Release builds require APP_MODE=prod. Current APP_MODE=$appMode"
                }
                require(!isOpenSourceMode) {
                    "Release builds in OSS mode are blocked to prevent accidental secret leakage."
                }
                require(!backendBaseUrl.isNullOrBlank()) {
                    "Release builds require BACKEND_BASE_URL."
                }
                require(
                    !releaseStoreFile.isNullOrBlank() &&
                    !releaseStorePassword.isNullOrBlank() &&
                    !releaseKeyAlias.isNullOrBlank() &&
                    !releaseKeyPassword.isNullOrBlank()
                ) {
                    "Release signing is not configured. Provide RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD."
                }
            }
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
