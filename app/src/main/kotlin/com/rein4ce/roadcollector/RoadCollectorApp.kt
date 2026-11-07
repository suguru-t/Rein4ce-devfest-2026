package com.rein4ce.roadcollector

import android.app.Application
import android.content.Context
import com.rein4ce.roadcollector.collect.LocationRecordingService
import com.rein4ce.roadcollector.data.AppSettings
import com.rein4ce.roadcollector.net.ApiClient
import com.rein4ce.roadcollector.data.PartRepository
import com.rein4ce.roadcollector.data.WeatherService

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
        private set

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        // タワーを送るときの作者名は、設定のニックネーム（空なら名無し）
        ApiClient.authorName = { settings.nickname.value.ifBlank { null } }
        partRepository = PartRepository(this)
        weatherService = WeatherService(this, partRepository)
        LocationRecordingService.createNotificationChannel(this)
    }

    companion object {
        fun from(context: Context): RoadCollectorApp = context.applicationContext as RoadCollectorApp
    }
}
