package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerMode
import com.rein4ce.roadcollector.core.svg.TowerSvg
import java.time.LocalDate
import java.util.Locale

/**
 * 共有ページ。LINE / X に貼ったときのプレビュー（OGP）と、タワーの絵（インライン SVG）。
 *
 * og:image は PNG（/towers/{id}/image.png）。SVG を読まないクローラが多いため。PNG は TowerPng がその場で描く。
 *
 * 見た目はアプリにそろえる（決定 94）：字はアプリと同じ M PLUS Rounded 1c の太字（このページで使う字だけを切り出して、このサーバから配る。
 * 決定 95。前は Google Fonts から読んでいて、開いた人のブラウザから Google にも通信が行っていた。読めなければ端末の丸い字。
 * 作者名は人が入れる字で切り出しに入っていないので、作者名だけ端末の字で出す）、
 * 地は方眼紙（8 px ごとに細い線、5 本ごとに太い線。アプリの Fieldbook と同じ色）、題字「ミチツミ」は 4 色の積み木、
 * 遊び方の札と高さの数字は遊び方ごとの色（アプリの ModeTheme と同じ）、絵は下に側面がのぞくカード（アプリの popBlock と同じ）。
 * 端末がダークモードなら、アプリのダークの色にする
 */
object SharePage {

    fun render(tower: Tower, baseUrl: String): String {
        val heightText = String.format(Locale.US, "%.2f", tower.height)
        val title = "高さ $heightText m のタワー"
        // 作者名（アプリの設定のニックネーム）があれば、説明に入れる。LINE / X のプレビューにも出る
        val author = cleanAuthor(tower.author)
        // 「〜さん」で終わる名前には「さん」を重ねない
        val who = when {
            author.isEmpty() -> ""
            author.endsWith("さん") -> "$author が "
            else -> "$author さんが "
        }
        // 本文の一言。プレビュー（og:description）には、どのアプリかが分かるよう「— ミチツミ」を付ける（本文は下の行に出すので付けない）
        val rest = "${tower.placements.size} 個の散歩パーツで積みました"
        val sentence = "$who$rest"
        val desc = "$sentence — ミチツミ"
        // 本文では作者名だけ端末の字で出す（切り出した字体に入っていない字で、1 つの名前の中で字体が混ざらないように）
        val sentenceHtml = if (author.isEmpty()) esc(rest) else "<span class=\"name\">${esc(author)}</span>${esc(who.removePrefix(author))}${esc(rest)}"
        val url = "$baseUrl/towers/${tower.id}"
        val image = "$baseUrl/towers/${tower.id}/image.png"
        val svg = TowerSvg.render(tower, widthPx = 480, heightPx = 640)
        val kinds = tower.parts.joinToString("、") { it.kind.label }
        val look = ModeLook.of(tower.mode)
        val streak = streakLine(tower)

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
  @font-face {
    font-family: "MichitsumiRounded"; src: url("$FONT_PATH") format("woff");
    font-weight: 800 900; font-display: swap;
  }
  :root {
    --paper: #F3F6F0; --grid-minor: #E2E8DF; --grid-major: #CED8CA;
    --ink: #2F3A2E; --muted: #5E6B5D; --card: #FBFCF9; --edge: #B8C5B3; --edge-side: ${side("B8C5B3")};
    --face: #${look.face}; --face-side: ${side(look.face)}; --mode-ink: #${look.ink};
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --paper: #161B17; --grid-minor: #1F2620; --grid-major: #2D3830;
      --ink: #E3E9DF; --muted: #A3AFA0; --card: #20271F; --edge: #3A463C; --edge-side: ${side("3A463C")};
      --face: #${look.darkFace}; --face-side: ${side(look.darkFace)}; --mode-ink: #${look.darkInk};
    }
  }
  body {
    margin: 0; color: var(--ink);
    font-family: "MichitsumiRounded", "Hiragino Maru Gothic ProN", "Hiragino Sans", system-ui, sans-serif; font-weight: 800;
    background-color: var(--paper);
    background-image:
      linear-gradient(var(--grid-major) 1px, transparent 1px),
      linear-gradient(90deg, var(--grid-major) 1px, transparent 1px),
      linear-gradient(var(--grid-minor) 1px, transparent 1px),
      linear-gradient(90deg, var(--grid-minor) 1px, transparent 1px);
    background-size: 40px 40px, 40px 40px, 8px 8px, 8px 8px;
  }
  main { max-width: 520px; margin: 0 auto; padding: 28px 16px 36px; text-align: center; }
  .title { display: flex; justify-content: center; gap: 6px; margin: 0 0 22px; }
  .title span {
    display: inline-flex; align-items: center; justify-content: center; width: 42px; height: 42px;
    border-radius: 10px; color: #F3F6F0; font-size: 27px; font-weight: 900; line-height: 1;
  }
  .tag {
    display: inline-block; padding: 6px 16px; border-radius: 12px; background: var(--face); color: #F3F6F0;
    font-size: 16px; font-weight: 900; box-shadow: 0 4px 0 var(--face-side); transform: rotate(-2deg);
  }
  .label { margin: 18px 0 0; font-size: 15px; }
  .height { margin: 0; color: var(--mode-ink); font-weight: 900; line-height: 1.1; }
  .height .num { font-size: 56px; }
  .height .unit { font-size: 24px; margin-left: 4px; }
  .desc { margin: 8px 0 0; font-size: 15px; }
  .desc .name { font-family: system-ui, sans-serif; font-weight: 700; }
  .streak { margin: 4px 0 0; font-size: 14px; color: var(--mode-ink); }
  .card {
    margin: 18px 0 0; padding: 8px; background: var(--card); border: 2px solid var(--edge); border-radius: 14px;
    box-shadow: 0 4px 0 var(--edge-side);
  }
  .card svg { display: block; width: 100%; height: auto; }
  /* 絵の地はカードの色に、絵の中の高さの字は出さない（上に大きく出しているため） */
  .card svg > rect:first-of-type { fill: transparent; }
  .card svg text { display: none; }
  .parts { margin: 14px 0 0; font-size: 14px; color: var(--muted); }
  .foot { margin: 22px 0 0; font-size: 13px; color: var(--muted); }
</style>
</head>
<body>
<main>
  <div class="title" role="img" aria-label="ミチツミ">${titleBlocks()}</div>
  <span class="tag">${esc(look.label)}</span>
  <p class="label">高さ</p>
  <p class="height"><span class="num">${esc(heightText)}</span><span class="unit">m</span></p>
  <p class="desc">$sentenceHtml</p>
${if (streak != null) "  <p class=\"streak\">${esc(streak)}</p>\n" else ""}  <div class="card">$svg</div>
  <p class="parts">使ったパーツ：${esc(kinds)}</p>
  <p class="foot">歩いた道を、積み上げよう — ミチツミ</p>
</main>
</body>
</html>
""".trimIndent()
    }

    /**
     * 共有ページに出す作者名：前後の空白を落とし、制御文字と「文字の向きを変える字」（U+202E など）を取り除く。
     * 向きを変える字が混ざると、LINE や X のプレビューで文の並びを入れ替えられてしまうため
     */
    internal fun cleanAuthor(author: String?): String =
        author.orEmpty()
            .filterNot { it.isISOControl() || it.category == CharCategory.FORMAT }
            .trim()

    /**
     * 毎日 1 つずつ積むの塔なら「11/10 まで 3 日続けて積みました」。1 日だけなら「11/10 に積みました」（「1 日続けて」は不思議に読めるため。決定 95）。
     * ほかの遊び方・日付が読めないときは null
     */
    internal fun streakLine(tower: Tower): String? {
        if (tower.mode != TowerMode.DAILY || tower.streakDays <= 0) return null
        val last = tower.lastDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val day = "${last.monthValue}/${last.dayOfMonth}"
        return if (tower.streakDays == 1) "$day に積みました" else "$day まで ${tower.streakDays} 日続けて積みました"
    }

    /** 切り出した字体の場所（Routes.kt の GET /fonts/share-rounded.woff。tools/subset_share_font.py で作る） */
    const val FONT_PATH = "/fonts/share-rounded.woff"

    /** 題字「ミチツミ」の積み木（アプリの TitleBlocks と同じ 4 色と傾き。側面は面の色を 3 割黒に寄せる） */
    private fun titleBlocks(): String = TITLE.joinToString("") { (char, face, tilt) ->
        """<span style="background:#$face;box-shadow:0 4px 0 ${side(face)};transform:rotate(${tilt}deg)">$char</span>"""
    }

    private val TITLE = listOf(
        Triple('ミ', "E07A2A", -5),
        Triple('チ', "2A9D8F", 3),
        Triple('ツ', "F2C14E", -2),
        Triple('ミ', "4A4E69", 8),
    )

    /** 積み木の側面の色：面の色を 3 割黒に寄せる（アプリの Pop.side と同じ） */
    internal fun side(hex: String): String {
        val v = hex.removePrefix("#").toInt(16)
        fun ch(shift: Int) = (((v shr shift) and 0xFF) * 0.7).toInt().coerceIn(0, 255)
        return String.format(Locale.US, "#%02X%02X%02X", ch(16), ch(8), ch(0))
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

/** 遊び方の札の名前と色（アプリの ModeTheme と同じ色。面は白い字を載せる塗り、字は高さの数字の色） */
internal class ModeLook(val label: String, val face: String, val ink: String, val darkFace: String, val darkInk: String) {
    companion object {
        fun of(mode: TowerMode): ModeLook = when (mode) {
            TowerMode.NORMAL -> ModeLook("通常", "A15D36", "9A5634", "9B613A", "E9B48F")
            TowerMode.CHALLENGE -> ModeLook("90 秒チャレンジ", "3F7970", "2F6B61", "3B796E", "93CFC3")
            TowerMode.DAILY -> ModeLook("毎日 1 つずつ積む", "6269AB", "545A92", "5E6499", "B9BDE6")
        }
    }
}
