package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.svg.TowerSvg
import java.util.Locale

/**
 * 共有ページ。LINE / X に貼ったときのプレビュー（OGP）と、タワーの絵（インライン SVG）。
 *
 * og:image は PNG（/towers/{id}/image.png）。SVG を読まないクローラが多いため。PNG は TowerPng がその場で描く。
 */
object SharePage {

    fun render(tower: Tower, baseUrl: String): String {
        val title = "高さ ${String.format(Locale.US, "%.2f", tower.height)} m のタワー"
        val desc = "${tower.parts.size} 個の散歩パーツで積みました — ミチツミ"
        val url = "$baseUrl/towers/${tower.id}"
        val image = "$baseUrl/towers/${tower.id}/image.png"
        val svg = TowerSvg.render(tower, widthPx = 480, heightPx = 640)
        val kinds = tower.parts.joinToString("、") { it.kind.label }

        return """
<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)} - ミチツミ</title>
<meta property="og:type" content="website">
<meta property="og:title" content="${esc(title)}">
<meta property="og:description" content="${esc(desc)}">
<meta property="og:url" content="${esc(url)}">
<meta property="og:image" content="${esc(image)}">
<meta property="og:image:width" content="${TowerPng.WIDTH}">
<meta property="og:image:height" content="${TowerPng.HEIGHT}">
<meta property="og:image:alt" content="${esc(title)}">
<meta name="twitter:image" content="${esc(image)}">
<meta name="twitter:card" content="summary_large_image">
<style>
  body { margin: 0; font-family: system-ui, sans-serif; background: #FFF8F2; color: #1F2A37; }
  main { max-width: 520px; margin: 0 auto; padding: 24px 16px; text-align: center; }
  h1 { font-size: 22px; margin: 0 0 4px; }
  p { color: #5B6675; margin: 4px 0 16px; }
  svg { max-width: 100%; height: auto; border-radius: 12px; }
  .parts { font-size: 14px; color: #5B6675; }
</style>
</head>
<body>
<main>
  <h1>${esc(title)}</h1>
  <p>${esc(desc)}</p>
  $svg
  <p class="parts">使ったパーツ：${esc(kinds)}</p>
</main>
</body>
</html>
""".trimIndent()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
