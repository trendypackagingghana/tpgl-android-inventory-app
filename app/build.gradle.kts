import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// API keys live in <project>/secrets.properties (git-ignored), never in source.
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.example.tpglstock"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.tpglstock"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val deepSeekKey = secrets.getProperty("DEEPSEEK_API_KEY", "").trim()
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepSeekKey\"")
        buildConfigField("String", "SUPABASE_URL", "\"${secrets.getProperty("SUPABASE_URL", "").trim()}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${secrets.getProperty("SUPABASE_KEY", "").trim()}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(secrets.getProperty("RELEASE_STORE_FILE", "release.jks"))
            storePassword = secrets.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = secrets.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = secrets.getProperty("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
