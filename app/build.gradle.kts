plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.arra.saccadence"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arra.saccadence"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }
}

dependencies {
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Rig pairing over the clinic LAN (WebSocket client).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // On-device iris/face-mesh landmarks, NPU-accelerated where supported.
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // QR scan for rig pairing (decodes the ws://host:port/?join=CODE the rig's start screen shows).
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // On-device LLM rewrite path for the clinic note — see notes/ClinicNoteGenerator.kt.
    // Guardrailed and off by default (modelPath = null); never the source of a
    // measured number, only an optional rewrite of the deterministic template.
    implementation("com.google.mediapipe:tasks-genai:0.10.24")

    testImplementation("junit:junit:4.13.2")
    // Unit tests (plain JVM, no Robolectric) hit the Android SDK stub jar's
    // org.json, which throws "not mocked" at runtime. This pulls in the real
    // implementation under the same package name for the test classpath only.
    testImplementation("org.json:json:20240303")
}
