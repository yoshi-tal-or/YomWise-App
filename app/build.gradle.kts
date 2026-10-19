plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.hebdate"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.yomwise.app"
        minSdk = 26
        targetSdk = 34
        val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = run
        versionName = "1.$run"
    }
    val keystorePath: String? = System.getenv("KEYSTORE_FILE")
    if (keystorePath != null) {
        signingConfigs {
            create("yomwise") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("yomwise")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.kosherjava:zmanim:2.5.0")
}
