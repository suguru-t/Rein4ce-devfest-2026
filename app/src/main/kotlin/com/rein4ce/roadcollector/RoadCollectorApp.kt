package com.rein4ce.roadcollector

import android.app.Application
import android.content.Context
import com.rein4ce.roadcollector.collect.LocationRecordingService
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

    /**
     * 「みんなのタワー」の一覧（サーバから）。画面ではなくアプリ全体で持ち、画面が出たときに先読みする（AppNav）。
     * ここ（Application.onCreate）では通信を始めない：記録サービスだけが裏で立ち上がったときにも呼ばれるため
     */
    lateinit var gallery: TowerGallery
        private set

    override fun onCreate() {
        super.onCreate()
        partRepository = PartRepository(this)
        weatherService = WeatherService(this, partRepository)
        gallery = TowerGallery()
        LocationRecordingService.createNotificationChannel(this)
    }

    companion object {
        fun from(context: Context): RoadCollectorApp = context.applicationContext as RoadCollectorApp
    }
}
