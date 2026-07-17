plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.notiontasks.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.notiontasks.app"
        minSdk = 26
        //noinspection EditedTargetSdkVersion
        targetSdk = 37
        versionCode = 19
        versionName = "1.3.2-dev.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core & Lifecycle
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // Jetpack Compose
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Retrofit & Kotlin Serialization
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Ensure the unitTestClasses and androidTestClasses tasks exist, which is required by some CI/build execution frameworks for Android projects.
tasks.register("unitTestClasses") {
    description = "Assembles compilation outputs for unit tests across all variants"
    group = "verification"
    dependsOn(tasks.matching { it.name.endsWith("UnitTestSources") || it.name.contains("UnitTestKotlin") })
}

tasks.register("androidTestClasses") {
    description = "Assembles compilation outputs for android tests across all variants"
    group = "verification"
    dependsOn(tasks.matching { it.name.contains("AndroidTest") || it.name.contains("AndroidTestKotlin") })
}
