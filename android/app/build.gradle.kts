plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The archive is not duplicated in the repo: it is copied from the Reader's slot (reader/data/archive.db) at build time.
val archiveSrc = rootProject.file("../reader/data/archive.db")
val archiveDir = layout.buildDirectory.dir("generated/archive")
val copyArchive by tasks.registering(Copy::class) {
    from(archiveSrc)
    into(archiveDir)
    doFirst { require(archiveSrc.exists()) { "missing ${archiveSrc.path} - put the latest export there" } }
}

android {
    namespace = "com.worldendarchive.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.worldendarchive.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = "2.0." + (System.getenv("BUILD_NUMBER") ?: "0")
    }

    signingConfigs {
        // Shared debug key, committed on purpose (passwords are Android's public defaults): every build - local or CI -
        // is signed with the same key, so new APKs install over old ones. Not a secret, not for app stores.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // CI: set the repo secrets ANDROID_KEYSTORE_BASE64 / _PASSWORD / ANDROID_KEY_ALIAS / ANDROID_KEY_PASSWORD
        // so every release is signed with the same key (needed to update the app in place).
        create("release") {
            val ks = System.getenv("ANDROID_KEYSTORE_FILE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val rel = signingConfigs.getByName("release")
            signingConfig = if (rel.storeFile != null) rel else signingConfigs.getByName("debug")
        }
    }

    sourceSets["main"].assets.srcDir(archiveDir)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }

tasks.named("preBuild") { dependsOn(copyArchive) }

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.07.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
}
