plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.evolearn.kidszone"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.evolearn.kidszone"
        minSdk = 21
        targetSdk = 35
        versionCode = 5
        versionName = "1.4.0"

        // Vosk 实时英文识别：只保留主流电视盒子(arm64)与模拟器(x86_64)架构，控制APK体积
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }

        // 内置默认远程配置地址（第2优先级）。部署 server/config.json 后改成你的地址，
        // 也可不改——在应用「家长设置 → 修改配置地址」里输入即可（第1优先级）。
        buildConfigField(
            "String", "DEFAULT_CONFIG_URL",
            "\"https://raw.githubusercontent.com/yourname/evo-learn-tv/main/server/config.json\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // Android TV 标准组件（Browse 列表、TV 主题）
    implementation("androidx.leanback:leanback:1.0.0")
    // 播放器
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    // 网络 / JSON / 二维码
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.zxing:core:3.5.3")
    // 中文转拼音：TV遥控器难输中文，支持输入拼音搜片名（如 peiqi → 小猪佩奇）
    implementation("com.github.promeg:tinypinyin:2.0.3")
    // 离线英文语音识别（Vosk）：无B站字幕轨的视频实时识别英文字幕行
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
