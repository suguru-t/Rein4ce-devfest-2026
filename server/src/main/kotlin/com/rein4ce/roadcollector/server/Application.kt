package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.AppJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing

/**
 * Cloud Run は環境変数 PORT で待ち受けポートを渡してくる（既定 8080）。
 * ローカル実行： ./gradlew :server:run → http://localhost:8080/
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }.start(wait = true)
}

fun Application.module(
    store: TowerStore = TowerStore.fromEnvironment(),
    namer: PartNamer = PartNamer.fromEnvironment(),
    limits: RateLimits = RateLimits.fromEnvironment(),
) {
    install(ContentNegotiation) { json(AppJson) }
    install(CallLogging)
    // CORS は入れない（決定 57）。ブラウザからこのサーバの API を呼ぶ画面は無く（アプリはブラウザではない。共有ページはサーバ自身が返す）、
    // 「どこからでも可」にしておくと、ほかのサイトが訪問者のブラウザからこの API を叩けてしまうため。
    // 別のドメインのウェブ画面から呼ぶことになったら、そのドメインだけを許す形で入れ直す
    log.info("RateLimit: ${limits.description}")

    routing {
        towerRoutes(store, limits)
        partRoutes(namer, limits)
    }
}
