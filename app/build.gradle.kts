plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.tekadi.kvvs.league"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tekadi.kvvs.league"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Point this at your backend from V1__init_schema.sql / the Spring Boot scaffold.
        // 10.0.2.2 is how the Android emulator reaches your host machine's localhost.
        buildConfigField("String", "API_BASE_URL", "\"https://kvsv-backend-service-test-965304126046.us-central1.run.app/\"")
//        buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.5:8080/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // GAP FIX: rele    ase builds were shipping with the SAME API_BASE_URL as debug —
            // http://10.0.2.2:8080/, which only resolves to "the emulator's host machine"
            // inside the emulator. On a real device that address isn't reachable at all,
            // so every request from a release APK would hang until the client's own
            // timeout fired — this alone explains a "frequent timeout" report from real
            // users while everything worked fine for whoever tested it in the emulator.
            // TODO: replace with your actual deployed backend URL (Cloud Run service URL,
            // custom domain, etc.) before shipping a build to real devices.
            buildConfigField("String", "API_BASE_URL", "\"https://kvsv-backend-service-test-965304126046.us-central1.run.app/\"")
//            buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.5:8080/\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Networking — talks to the Spring Boot backend
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Local persistence for the offline scoring queue (Module 19: Offline Scoring + Auto Sync).
    // Kept to DataStore + JSON (no Room/KSP) so this scaffold builds with a plain Kotlin+Compose setup.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Unit tests — ScoringEngine has no Android dependencies, so these run as
    // plain JVM tests (`./gradlew test`), no emulator/device required.
    testImplementation("junit:junit:4.13.2")

    // Remote image loading (banner images, feed images) — dashboard feature.
    implementation("io.coil-kt:coil-compose:2.6.0")

}
