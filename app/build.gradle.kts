import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "es.altamirano.linvideo"
    compileSdk = 37

    defaultConfig {
        applicationId = "es.altamirano.linvideo"
        minSdk = 30          // Android 11: almacenamiento acotado, sin el «legacy»
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        // yt-dlp, Python y FFmpeg van como binarios nativos: sólo arm64 para que el APK no pese el doble.
        ndk { abiFilters += "arm64-v8a" }
    }

    // La clave de release vive fuera del repositorio (ver CLAUDE.md). Sin ella, la release sale
    // sin firmar y la de depuración sigue funcionando.
    val keyFile = File(System.getProperty("user.home"), ".android/claves/linvideo-release.properties")
    val releaseKey = if (keyFile.exists()) Properties().apply { keyFile.inputStream().use { load(it) } } else null
    signingConfigs {
        if (releaseKey != null) create("release") {
            storeFile = File(releaseKey.getProperty("storeFile"))
            storePassword = releaseKey.getProperty("storePassword")
            keyAlias = releaseKey.getProperty("keyAlias")
            keyPassword = releaseKey.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            if (releaseKey != null) signingConfig = signingConfigs.getByName("release")
            // Sin R8: casi todo el peso son los binarios nativos, y la librería usa Jackson por reflexión.
            isMinifyEnabled = false
        }
    }
    // La librería ejecuta los binarios desde nativeLibraryDir: tienen que extraerse al instalar.
    packaging { jniLibs.useLegacyPackaging = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.datastore.core)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.ytdl.library)
    implementation(libs.ytdl.ffmpeg)
    testImplementation(libs.junit)
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}
