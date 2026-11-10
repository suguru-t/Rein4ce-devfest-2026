package com.rein4ce.roadcollector

import android.app.Application
import android.content.Context
import com.rein4ce.roadcollector.audio.BgmPlayer
import com.rein4ce.roadcollector.audio.SoundEffects
import com.rein4ce.roadcollector.collect.LocationRecordingService
import com.rein4ce.roadcollector.data.AppSettings
import com.rein4ce.roadcollector.data.MyTowers
import com.rein4ce.roadcollector.data.OwnerId
import com.rein4ce.roadcollector.net.ApiClient
import com.rein4ce.roadcollector.data.PartRepository
import com.rein4ce.roadcollector.data.DailyTowerStore
import com.rein4ce.roadcollector.data.StackRecords
import com.rein4ce.roadcollector.data.WeatherService
import com.rein4ce.roadcollector.ui.common.Daypart
import com.rein4ce.roadcollector.ui.common.PiecePalette
import com.rein4ce.roadcollector.ui.common.toPartColors
import com.rein4ce.roadcollector.ui.gallery.TowerGallery
import com.rein4ce.roadcollector.ui.theme.JapaneseText

/**
 * アプリ全体で共有するもの（リポジトリなど）の置き場。
 * DI ライブラリは使わず、ここから取り出す。
 */
class RoadCollectorApp : Application() {

    lateinit var partRepository: PartRepository
        private set

    /** 天気を取ってくる係（歩いたときの天気と、今の天気） */
    lateinit var weatherService: WeatherService
        private set

    /** アプリの設定（画面の明るさなど） */
    lateinit var settings: AppSettings

    /**
     * 「みんなのタワー」の一覧（サーバから）。画面ではなくアプリ全体で持ち、画面が出たときに先読みする（AppNav）。
     * ここ（Application.onCreate）では通信を始めない：記録サービスだけが裏で立ち上がったときにも呼ばれるため
     */
    lateinit var gallery: TowerGallery
        private set

    /** BGM（全部の画面で同じ曲）。流すかどうかは MainActivity が、アプリが前に出ているかと設定から決める */
    val bgm: BgmPlayer by lazy { BgmPlayer(this) }

    /**
     * 効果音（ボタン・積み木・道を 1 本集めたとき）。画面を開いたとき（MainActivity）に読み込みを始める
     * （記録サービスだけが裏で立ち上がったときには読み込まない）
     */
    val sfx: SoundEffects by lazy { SoundEffects(this, settings, bgmGivingWay = { bgm.givingWay }) }

    /** このスマホから送信したタワーの id（「みんな」で「あなた」の札を出す） */
    lateinit var myTowers: MyTowers
        private set

    /** 積む画面の、モードごとの自分の記録（モードを選ぶ画面に出す） */
    val stackRecords: StackRecords by lazy { StackRecords(this) }

    /** 「毎日 1 つずつ積む」の塔（スマホの中だけ。決定 61） */
    val dailyTower: DailyTowerStore by lazy { DailyTowerStore(filesDir) }

    override fun onCreate() {
        super.onCreate()
        // 端末の言語によらず、字を日本語として描く（漢字が中国語の形にならないように。#46）
        JapaneseText.apply()
        settings = AppSettings(this)
        // タワーを送るときの作者名は、設定のニックネーム（空なら入れない。「みんな」では「ゲスト」と出る）
        ApiClient.authorName = { settings.nickname.value.ifBlank { null } }
        // 送信できたタワーは、自分のものとして端末に覚えておく（サーバが落ちていても印が出る）
        myTowers = MyTowers(this)
        ApiClient.onPosted = { created, mode ->
            myTowers.add(created.id)
            // 送れたら、その遊び方の一覧も取り直す（送ってすぐ「みんな」を開いても、新しいタワーが出る。取っている最中なら 1 回だけ取り直す）
            gallery.refresh(mode)
        }
        // 持ち主の印。送るタワーに入れ、一覧ではサーバが「あなたのか」を答える。新しく入れたアプリは乱数から作るので、
        // 入れ直すと新しい印になる。前の版で送ったことがあれば（myTowers に id がある）前の印のまま（決定 90）
        val owner = OwnerId.of(this, sentBefore = myTowers.ids.value.isNotEmpty())
        ApiClient.ownerId = { owner }
        partRepository = PartRepository(this)
        // 送る塔のパーツの色：積む画面と同じ、歩いたときの天気と時間帯の色（決定 74・95）
        ApiClient.pieceColors = { part ->
            PiecePalette.colors(partRepository.weatherOf(part.id)?.sky, Daypart.of(part, partRepository.placeOf(part.id)?.startedAt)).toPartColors()
        }
        weatherService = WeatherService(this, partRepository)
        gallery = TowerGallery()
        LocationRecordingService.createNotificationChannel(this)
    }

    companion object {
        fun from(context: Context): RoadCollectorApp = context.applicationContext as RoadCollectorApp
    }
}
