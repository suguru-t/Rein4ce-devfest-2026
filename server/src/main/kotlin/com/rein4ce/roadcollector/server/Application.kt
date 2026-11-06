package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.AppJson
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing

/**
 * Cloud Run は環境変数 PORT で待ち受けポートを渡してくる（既定 8080）。
 * ローカル実行： ./gradlew :server:run → http://localhost:8080/
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }.start(wait = true)
}

fun Application.module(store: TowerStore = TowerStore.fromEnvironment()) {
    install(ContentNegotiation) { json(AppJson) }
    install(CallLogging)
    install(CORS) {
        anyHost() // ハッカソン用。公開時は Web フロントのドメインに絞る
        allowHeader(HttpHeaders.ContentType)
    }

    routing {
        towerRoutes(store)
    }
}
