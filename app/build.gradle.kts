import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// local.properties から秘密情報・環境依存の値を読む（git には含めない）
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// パソコンのユーザーの Gradle の設定ファイル（~/.gradle/gradle.properties。リポジトリの外）
val userGradleProps = Properties().apply {
    val f = File(gradle.gradleUserHomeDir, "gradle.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Maps API キー：次の順に見て、最初に空でないものを使う（どれにも無ければ空＝地図の代わりに軌跡を Canvas に描く）。
// 1. このクローンの local.properties
// 2. コマンドの -PMAPS_API_KEY=…
// 3. パソコンのユーザーの ~/.gradle/gradle.properties。1 回書いておけば、そのパソコンのどのクローン・作業フォルダ（git worktree）でも読まれる
// 4. 環境変数 MAPS_API_KEY（GitHub Actions は Secrets から渡す）
// local.properties に「MAPS_API_KEY=」の空の行だけがあるときも、次を見に行く。
// リポジトリの gradle.properties（git に入る）は、うっかり書いてもキーが公開されないよう、わざと読まない
// （providers.gradleProperty だと、そこも読んでしまう）
val mapsApiKey: String = listOf(
    localProps.getProperty("MAPS_API_KEY"),
    gradle.startParameter.projectProperties["MAPS_API_KEY"],
    userGradleProps.getProperty("MAPS_API_KEY"),
    System.getenv("MAPS_API_KEY"),
).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
val serverBaseUrl: String = localProps.getProperty("SERVER_BASE_URL")?.trim().orEmpty()
    .ifEmpty { "https://road-collector-415368216017.asia-northeast1.run.app" }

android {
    namespace = "com.rein4ce.roadcollector"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.rein4ce.roadcollector"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        buildConfigField("String", "SERVER_BASE_URL", "\"$serverBaseUrl\"")
    }

    // デバッグ署名はチーム共通の鍵（app/debug.keystore）を使う。
    // 全員と GitHub Actions のビルドが同じ署名になるので、Maps API キーの「Android アプリ」制限に
    // この鍵の SHA-1 を 1 つ登録すれば、誰のビルドでも地図が出る。端末に入れ替えるときのアンインストールも要らない。
    // デバッグ用なので秘密ではない（パスワードは Android 標準と同じ "android"）。
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // 発表・実機テスト用に、release もチーム共通の鍵（debug と同じ）で署名する。
            // debug ビルドは Compose が遅く、エミュレータで測ると起動が約 4.5 秒 → release で約 2 秒になった。
            // 同じ鍵なので、Maps API キーの制限（この鍵の SHA-1）のまま地図が出て、debug ビルドの上にそのまま入れ替えられる。
            // ストアに出すときは、ここを本番用の鍵に替える
            signingConfig = signingConfigs.getByName("debug")
            // R8（縮小・難読化）はまだ使わない。kotlinx.serialization・Ktor・dyn4j の keep ルールを確かめていないため
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // 手持ちのドラッグでの並べ替え（LazyVerticalGrid 用。Apache-2.0）
    implementation(libs.reorderable)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.play.services.location)
    implementation(libs.maps.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
