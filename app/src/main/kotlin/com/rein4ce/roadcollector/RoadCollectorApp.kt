package com.rein4ce.roadcollector

import android.app.Application
import android.content.Context
import com.rein4ce.roadcollector.collect.LocationRecordingService
import com.rein4ce.roadcollector.data.AppSettings
import com.rein4ce.roadcollector.data.MyTowers
import com.rein4ce.roadcollector.net.ApiClient
import com.rein4ce.roadcollector.data.PartRepository
import com.rein4ce.roadcollector.data.WeatherService
import com.rein4ce.roadcollector.ui.gallery.TowerGallery

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

    /** このスマホから送信したタワーの id（「みんな」で「あなた」の札を出す） */
    lateinit var myTowers: MyTowers
        private set

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        // タワーを送るときの作者名は、設定のニックネーム（空なら名無し）
        ApiClient.authorName = { settings.nickname.value.ifBlank { null } }
        // 送信できたタワーは、自分のものとして端末に覚えておく
        myTowers = MyTowers(this)
        ApiClient.onPosted = { myTowers.add(it.id) }
        partRepository = PartRepository(this)
        weatherService = WeatherService(this, partRepository)
        gallery = TowerGallery()
        LocationRecordingService.createNotificationChannel(this)
    }

    companion object {
        fun from(context: Context): RoadCollectorApp = context.applicationContext as RoadCollectorApp
    }
}
