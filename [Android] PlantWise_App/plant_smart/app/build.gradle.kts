plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.plant_smart"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.plant_smart"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // Charting library (MPAndroidChart)
    implementation("com.github.Philjay:MPAndroidChart:v3.1.0")  //
    implementation(libs.material)
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("com.github.hannesa2:paho.mqtt.android:3.6.6")
    implementation("androidx.appcompat:appcompat:1.6.1") // Use the latest version
    implementation("androidx.cardview:cardview:1.0.0") // Use the latest version
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2") // Use the latest version
    implementation("androidx.cardview:cardview:1.0.0") // Use the latest version

    implementation("com.github.bumptech.glide:glide:4.16.0")  //
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    //implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
}