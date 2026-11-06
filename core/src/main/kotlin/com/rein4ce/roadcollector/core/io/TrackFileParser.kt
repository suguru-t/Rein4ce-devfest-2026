package com.rein4ce.roadcollector.core.io

import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.TrackPoint
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * 実機で録った軌跡ファイルを [TrackPoint] の列に読み込む。
 *
 * - GPX：`<trkpt lat="…" lon="…"><time>…</time></trkpt>`（`<rtept>` も可）。
 *   精度は GPX に無いので 0（不明）にする。`<time>` が無ければ 0
 * - JSON：[TrackPoint] の配列（`[{"lat":…,"lng":…},…]`）か、`points` を持つオブジェクト
 *   （[com.rein4ce.roadcollector.core.model.Track] 全体でも、`{"points":[…]}` だけでも可）
 * - 先頭の BOM・空白・改行は無視する
 *
 * 読めないときは [IllegalArgumentException]（メッセージは画面にそのまま出す想定）。
 */
object TrackFileParser {

    enum class Format { GPX, JSON }

    /** 先頭の文字で GPX（XML）か JSON かを判定する。 */
    fun detect(text: String): Format? {
        val head = clean(text)
        return when {
            head.startsWith("<") -> Format.GPX
            head.startsWith("{") || head.startsWith("[") -> Format.JSON
            else -> null
        }
    }

    fun parse(text: String): List<TrackPoint> {
        val body = clean(text)
        return when (detect(body)) {
            Format.GPX -> parseGpx(body)
            Format.JSON -> parseJson(body)
            null -> throw IllegalArgumentException("GPX でも JSON でもありません")
        }
    }

    /** BOM と先頭の空白・改行を落とす（BOM 付き JSON や、先頭が改行の GPX も読めるように） */
    private fun clean(text: String): String = text.trimStart('﻿', ' ', '\t', '\r', '\n')

    fun parseJson(text: String): List<TrackPoint> {
        val element = try {
            AppJson.parseToJsonElement(clean(text))
        } catch (e: Exception) {
            throw IllegalArgumentException("JSON として読めません：${e.message}")
        }
        val pointsSerializer = ListSerializer(TrackPoint.serializer())
        val points = try {
            when {
                element is JsonArray -> AppJson.decodeFromJsonElement(pointsSerializer, element)
                // Track 全体でも、{"points":[…]} だけ（id などが無い）でも読めるように points だけを見る
                element is JsonObject && "points" in element ->
                    AppJson.decodeFromJsonElement(pointsSerializer, element.getValue("points"))
                else -> throw IllegalArgumentException("points の配列が見つかりません")
            }
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("軌跡の形式が違います：${e.message}")
        }
        if (points.isEmpty()) throw IllegalArgumentException("点が 1 つもありません")
        return points
    }

    fun parseGpx(rawText: String): List<TrackPoint> {
        val text = clean(rawText)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // 外部エンティティは読まない（他人のファイルを開くので）
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
        }
        val doc = try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) {
            throw IllegalArgumentException("GPX（XML）として読めません：${e.message}")
        }

        val points = ArrayList<TrackPoint>()
        for (tag in listOf("trkpt", "rtept")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                val lat = el.getAttribute("lat").toDoubleOrNull() ?: continue
                val lng = el.getAttribute("lon").toDoubleOrNull() ?: continue
                points += TrackPoint(lat = lat, lng = lng, accuracyM = 0f, timeMs = parseTime(childText(el, "time")))
            }
            if (points.isNotEmpty()) break // trkpt があれば rtept は見ない
        }
        if (points.isEmpty()) throw IllegalArgumentException("trkpt が 1 つもありません")
        return points
    }

    private fun childText(el: Element, tag: String): String? {
        val children = el.childNodes
        for (i in 0 until children.length) {
            val c = children.item(i) as? Element ?: continue
            if (c.tagName == tag || c.tagName.endsWith(":$tag")) return c.textContent?.trim()
        }
        return null
    }

    /** ISO 8601（`2026-11-06T03:00:00Z`、`+09:00` 付き、時差なし）→ epoch millis。読めなければ 0 */
    internal fun parseTime(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli() }
        return 0L
    }
}
