plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ir.kaveh.yaddashtyar"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.kaveh.yaddashtyar"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.5"
    }

    // دو نسخه با یک applicationId و یک کلید امضا: lite (بدون هوش مصنوعی) و full.
    // چون شناسه و امضا یکی است، نصب full روی lite داده‌ها را حفظ می‌کند.
    flavorDimensions += "edition"
    productFlavors {
        create("lite") {
            dimension = "edition"
            versionNameSuffix = "-lite"
            buildConfigField("boolean", "AI_ENABLED", "false")
        }
        create("full") {
            dimension = "edition"
            versionNameSuffix = "-full"
            buildConfigField("boolean", "AI_ENABLED", "true")
        }
    }

    // کلید امضای ثابت از متغیرهای محیطی (GitHub Secrets). بدون آن، با کلید debug امضا می‌شود.
    val ksPath: String? = System.getenv("KEYSTORE_PATH")
    val hasKeystore = ksPath != null && file(ksPath).exists()
    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(ksPath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            // با همان کلید ثابت، حتی نسخه debug هم روی نسخهٔ قبلی نصب (آپدیت) می‌شود
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
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
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
