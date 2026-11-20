plugins {
    id("com.android.library")
}

android {
    namespace = "com.example.rssreader.notify"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // 与主模块共享同一份 Room 数据库，因此通知模块能直接统计未读、直接改已读状态
    implementation(project(":core_data"))

    // 后台定时任务
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("com.google.android.material:material:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
