plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "dev.edgeflow"
    compileSdk = 36
    ndkVersion = "30.0.15729638"
    defaultConfig {
        applicationId = "dev.edgeflow"
        minSdk = 31
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild { cmake { arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_shared") } }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "4.1.2" } }
    buildTypes { release { isMinifyEnabled = false } }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
