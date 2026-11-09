package com.rein4ce.roadcollector.core.model

import kotlinx.serialization.Serializable

/**
 * パーツを歩いた場所：形の中心の緯度・経度と、形の長辺の長さ [m]。
 * パーツの点列（中心が原点・長辺が 1.0）を、地図と同じ縮尺・位置に重ねるのに使う。
 *
 * **[Part] には入れない。** Part はタワーと一緒にサーバへ送られ、共有ページや「みんな」の一覧に出るので、
 * 入れるとどこを歩いたか（家の近くなど）が分かってしまう。アプリはこれをスマホの中だけ（parts_places.json）に持つ。
 *
 * @property lat   形の中心の緯度
 * @property lng   形の中心の経度
 * @property sizeM 形の長辺の長さ [m]（パーツの点列の 1.0 にあたる）
 * @property startedAt 歩き始めた時刻（最初の点の時刻 [ms]）。パーツの天気は、この時刻の天気にする（決定 55）。
 *   これを足す前に採集したパーツには無い（null。そのときは歩き終えた時刻 [Part.walkedAt] の天気）
 */
@Serializable
data class PartPlace(val lat: Double, val lng: Double, val sizeM: Double, val startedAt: Long? = null)
