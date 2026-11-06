// :server — Cloud Run に載せる Ktor サーバ。:core のデータ形式をそのまま使う。
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jib)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

application {
    mainClass.set("com.rein4ce.roadcollector.server.ApplicationKt")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback.classic)
    implementation(libs.google.genai)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
}

// Jib：Docker 無しでコンテナイメージを作って Artifact Registry に push する。
//   ./gradlew :server:jib --image=asia-northeast1-docker.pkg.dev/<PROJECT_ID>/road-collector/server
jib {
    from {
        image = "eclipse-temurin:17-jre"
    }
    to {
        image = System.getenv("SERVER_IMAGE")
            ?: "asia-northeast1-docker.pkg.dev/PROJECT_ID/road-collector/server"
    }
    container {
        ports = listOf("8080")
        mainClass = "com.rein4ce.roadcollector.server.ApplicationKt"
        jvmFlags = listOf("-Xmx256m")
    }
}
