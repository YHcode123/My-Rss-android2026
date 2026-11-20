plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.rssreader"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.rssreader"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
}

dependencies {
    // 主模块依赖数据层 + 伴生通知模块（同一仓库、相互关联）
    implementation(project(":core_data"))
    implementation(project(":feature_notification"))

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.10.0")

    // 协程 + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // 主模块自己的后台同步用（不与通知模块的 Worker 混在一起）
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}
