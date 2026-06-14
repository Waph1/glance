plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.waph1.glance"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.waph1.glance"
        minSdk = 24
        targetSdk = 34
        versionCode = 6
        versionName = "1.2"

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

    flavorDimensions += "type"
    productFlavors {
        create("launcher") {
            dimension = "type"
            applicationId = "com.waph1.glance"
            manifestPlaceholders["homeCategory"] = "android.intent.category.HOME"
            manifestPlaceholders["appName"] = "Glance"
        }
        create("drawer") {
            dimension = "type"
            applicationId = "com.waph1.glance.drawer"
            manifestPlaceholders["homeCategory"] = "android.intent.category.DEFAULT"
            manifestPlaceholders["appName"] = "Glance Drawer"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
