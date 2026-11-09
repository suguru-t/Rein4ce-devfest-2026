package com.rein4ce.roadcollector.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.weather.OpenMeteo
import com.rein4ce.roadcollector.core.weather.WalkWeather
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 天気を取ってくる係（B）。取ってくるのは 2 つ：
 * - **パーツを歩いたときの天気**：場所（PartPlace）が分かっていて天気がまだのパーツを見つけたら、歩き始めた時刻（[PartPlace.startedAt]。
 *   それが無い前のパーツは歩き終えた時刻）の天気を聞いて、[PartRepository.setWeather] で保存する。
 *   長く歩いて途中で天気が変わっても、歩き始めたときの天気に決める（決定 55）。採集した直後のパーツも、前に採集したパーツ（90 日前まで）も同じ流れ
 * - **今の天気**（[current]）：ホーム・採集・コレクションの一覧・積む・みんなの背景に使う。スマホの最後の位置（位置の許可があるとき）、無ければ最後に歩いた場所で聞く
 *
 * 天気は Open-Meteo（キー不要・無料）から取る。送る位置は 0.1 度（約 10 km）に丸める（OpenMeteo のコメント）。
 * 電波が無いなどで取れなかったパーツは、次にアプリを開いたときにもう一度聞く。
 *
 * テストでは [fetch]・[lastLocation]・[now] を差し替える。
 *
 * @param fetch URL を開いて、返事の文字列を返す（失敗なら null）
 * @param lastLocation スマホの最後の位置（緯度・経度）。分からなければ null
 */
class WeatherService(
    private val repository: PartRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val fetch: suspend (String) -> String?,
    private val lastLocation: suspend () -> Pair<Double, Double>?,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** アプリからはこちら */
    constructor(context: Context, repository: PartRepository) : this(
        repository = repository,
        fetch = HttpFetcher()::get,
        lastLocation = { lastKnownLocation(context.applicationContext) },
    )

    private val _current = MutableStateFlow<WalkWeather?>(null)

    /** 今の天気（採集画面の背景）。まだ聞いていない・聞けなかったときは null */
    val current: StateFlow<WalkWeather?> = _current

    private var currentAt = 0L

    /** このアプリを開いている間に一度聞いたパーツ（取れなくても、同じ間には聞き直さない） */
    private val asked = HashSet<String>()

    init {
        scope.launch {
            repository.isLoaded.first { it }
            // 場所はあるのに天気がまだのパーツ。天気を保存するたびに、残りを順に聞く
            combine(repository.places, repository.weather) { places, weather -> places.keys - weather.keys }
                .collect { missing -> missing.forEach { id -> fill(id) } }
        }
    }

    private suspend fun fill(id: String) {
        if (!asked.add(id)) return
        val place = repository.places.value[id] ?: return
        val part = repository.parts.value.firstOrNull { it.id == id } ?: return
        val weather = weatherAt(place.lat, place.lng, place.startedAt ?: part.walkedAt) ?: return
        repository.setWeather(id, weather)
    }

    /**
     * その場所・時刻の天気。[timeMs] が今と同じ 1 時間（世界時の○時台。1 時間ごとの天気の区切りと同じ）のうちなら今の天気、
     * 前の時台なら（90 日前まで）その 1 時間の天気を聞く。それより前は分からない（null）。
     * 歩き始めた時刻で聞くので、1 時間に満たない散歩でも、歩き始めが前の時台なら、歩き始めの 1 時間の天気になる
     * （前は「1 時間以内なら今の天気」で、ふだんの散歩は歩き終えたときの天気になっていた。#103 の C のレビュー）
     */
    suspend fun weatherAt(lat: Double, lng: Double, timeMs: Long): WalkWeather? {
        val nowMs = now()
        val age = nowMs - timeMs
        return when {
            // 今の時台のうち（と、端末の時計のずれで少し先の時刻）は、今の天気
            age < 0 || timeMs / HOUR_MS == nowMs / HOUR_MS -> fetch(OpenMeteo.currentUrl(lat, lng))?.let(OpenMeteo::parseCurrent)
            age < OpenMeteo.MAX_PAST_DAYS * DAY_MS -> fetch(OpenMeteo.hourUrl(lat, lng, timeMs))?.let(OpenMeteo::parseHour)
            else -> null
        }
    }

    /**
     * 今の天気を聞き直す（採集画面を開いている間、少しずつ呼ばれる）。15 分以内に聞いていれば、聞き直さない。
     * 位置は、スマホの最後の位置か、最後に歩いた場所（見本は除く）。どちらも無ければ聞かない
     */
    suspend fun refreshCurrent() {
        if (_current.value != null && now() - currentAt < CURRENT_INTERVAL_MS) return
        val (lat, lng) = lastLocation() ?: latestWalkPlace() ?: return
        val weather = fetch(OpenMeteo.currentUrl(lat, lng))?.let(OpenMeteo::parseCurrent) ?: return
        _current.value = weather
        currentAt = now()
    }

    private fun latestWalkPlace(): Pair<Double, Double>? {
        val places = repository.places.value
        return repository.parts.value
            .filter { SampleParts.byId(it.id) == null && it.id in places }
            .maxByOrNull { it.walkedAt }
            ?.let { places.getValue(it.id) }
            ?.let { it.lat to it.lng }
    }

    companion object {
        private const val HOUR_MS = 60 * 60 * 1000L
        private const val DAY_MS = 24 * HOUR_MS
        private const val CURRENT_INTERVAL_MS = 15 * 60 * 1000L
    }
}

/** URL を開いて返事の文字列を返す。失敗したら null（天気は無くても困らないので、失敗は記録だけする） */
private class HttpFetcher {
    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
    }

    suspend fun get(url: String): String? = runCatching {
        val response = client.get(url)
        if (response.status.isSuccess()) response.bodyAsText() else null
    }.onFailure { Log.w("WeatherService", "weather request failed", it) }.getOrNull()
}

/** スマホの最後の位置。位置の許可が無い・まだ一度も測っていないときは null（新しく測りには行かない） */
@SuppressLint("MissingPermission") // 許可はすぐ上で確かめている
private suspend fun lastKnownLocation(context: Context): Pair<Double, Double>? {
    val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    if (!granted) return null
    return suspendCancellableCoroutine { cont ->
        LocationServices.getFusedLocationProviderClient(context).lastLocation
            .addOnSuccessListener { loc -> cont.resume(loc?.let { it.latitude to it.longitude }) }
            .addOnFailureListener { cont.resume(null) }
    }
}
