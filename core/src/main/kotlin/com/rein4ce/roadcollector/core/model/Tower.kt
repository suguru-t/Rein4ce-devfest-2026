package com.rein4ce.roadcollector.core.model

import com.rein4ce.roadcollector.core.physics.StackWorld
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * タワー内でのパーツ 1 個の最終配置（物理の再現ではなく、静止した結果を保存する）。
 * 座標はワールド座標 [m]。角度はラジアン。
 */
@Serializable
data class Placement(
    val partId: String,
    val x: Float,
    val y: Float,
    val angleRad: Float,
    /**
     * パーツの大きさ（長辺 [m]）。歩いた距離で決まる（[com.rein4ce.roadcollector.core.physics.PartSize]）。
     * 描くときは、パーツの形をこの倍率で大きくしてから置く。大きさを入れる前のタワーには無いので 1
     */
    val scale: Float = 1f,
)

/**
 * タワーを積んだ遊び方（決定 77）。「みんな」は遊び方ごとに別のランキングにする（みちをつむの 3 つのモードに合わせる）。
 * 送る先もサーバの保存先も遊び方ごとに分ける（通常は `POST /towers`、3 分チャレンジは `POST /towers/challenge`、
 * 毎日 1 つずつ積むは `PUT /towers/daily`）。遊び方を入れる前のタワーには無いので、通常
 */
@Serializable
enum class TowerMode(val wire: String) {
    @SerialName("normal") NORMAL("normal"),
    @SerialName("challenge") CHALLENGE("challenge"),
    @SerialName("daily") DAILY("daily"),
    ;

    companion object {
        /** `GET /towers?mode=` の値から。知らない値は null */
        fun of(wire: String?): TowerMode? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * 完成したタワー（作品）。共有・ギャラリー・ランキングの単位。
 *
 * @property author  作者名（アプリの設定のニックネーム）。見た目だけで、誰かを見分けるものではない
 * @property ownerId 持ち主の印。アプリが端末ごとに作る 64 桁の 16 進（端末の id を塩を付けてハッシュしたもの。元の id は分からない）。
 *   サーバはこれを保存して「あなたのタワーか」を答えるのに使い、**誰にも返さない**（一覧は [TowerSummary.mine] だけ、
 *   1 基の JSON では null にして返す）。古いアプリは送らないので null
 * @property platformWidth 台の幅 [m]。積む画面の通常（#108・決定 62）は広い台（2.8 m）で積むので、送ったあとの絵（「みんな」・共有ページ）も
 *   同じ幅の台で描くために持つ（決定 66）。入れる前のタワーには無いので、今までの台の幅（[StackWorld.PLATFORM_WIDTH]、1.4 m）
 * @property mode 積んだ遊び方（決定 77）。入れる前のタワーは通常
 * @property rule 3 分チャレンジの決まりの ID（`manual-180-v6` など）。決まりが違う記録は同じランキングに混ぜない。ほかの遊び方は null
 * @property streakDays 毎日 1 つずつ積むの、最後に積んだ日（[lastDay]）までの連続日数。「今も続いている日数」は見るときに、
 *   [lastDay] が今日か昨日なら [streakDays]、それより前なら 0 と決める（送ったあとに積むのをやめると、日がたつと 0 になる）
 * @property lastDay 毎日 1 つずつ積むで、最後に積んだ日（端末の日付。"2026-11-10"）。ほかの遊び方は null
 * @property colors パーツの色（パーツの id → 塗りと縁。決定 95）。積む画面と同じ、歩いたときの天気と時間帯の色（決定 74）で、
 *   送るときにアプリが入れる（通常と毎日 1 つずつ積む。90 秒チャレンジは形ごとの色で遊ぶので入れない）。
 *   「みんな」の絵と共有ページの絵・PNG は、色があればそれで描き、無いパーツ（入れる前のタワー）は形ごとの色で描く
 */
@Serializable
data class Tower(
    val id: String = "",
    val parts: List<Part>,
    val placements: List<Placement>,
    /** 台の上面からの高さ [m] */
    val height: Float,
    val createdAt: Long,
    val author: String? = null,
    val ownerId: String? = null,
    val platformWidth: Float = StackWorld.PLATFORM_WIDTH.toFloat(),
    val mode: TowerMode = TowerMode.NORMAL,
    val rule: String? = null,
    val streakDays: Int = 0,
    val lastDay: String? = null,
    val colors: Map<String, PartColors> = emptyMap(),
)

/**
 * パーツ 1 つの色（決定 95）。どちらも "#RRGGBB"。
 * @property fill 面の色
 * @property outline 形を囲む線の色（面の色を暗くしたもの）
 */
@Serializable
data class PartColors(val fill: String, val outline: String)

/**
 * ギャラリー一覧用の軽い形。
 * @property mine 一覧を取ったアプリ（X-Owner-Id ヘッダの持ち主）が送ったタワーなら true。持ち主の印そのものは入れない
 * @property mode・rule・streakDays・lastDay は [Tower] と同じ（決定 77）
 */
@Serializable
data class TowerSummary(
    val id: String,
    val height: Float,
    val partCount: Int,
    val createdAt: Long,
    val author: String? = null,
    val mine: Boolean = false,
    val mode: TowerMode = TowerMode.NORMAL,
    val rule: String? = null,
    val streakDays: Int = 0,
    val lastDay: String? = null,
)

/** POST /towers の応答。 */
@Serializable
data class TowerCreated(
    val id: String,
    val url: String,
)
