plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.google.dagger.hilt)
}

android {
    namespace = "com.example.se114_callingsystem.core.di"
    compileSdk = 36

    defaultConfig {
        minSdk = 32
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.appcompat)
    
    // Cloudinary
    implementation("com.cloudinary:cloudinary-android:2.5.0")
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.android.gms:play-services-auth:21.0.0")

    // Hilt
    implementation(libs.hilt.android)
    annotationProcessor(libs.hilt.compiler)
}
