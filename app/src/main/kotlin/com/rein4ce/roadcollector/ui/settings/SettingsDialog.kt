package com.rein4ce.roadcollector.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.data.AppSettings
import com.rein4ce.roadcollector.data.BgmTrack
import com.rein4ce.roadcollector.data.DEMO_SETTINGS
import com.rein4ce.roadcollector.data.DemoSky
import com.rein4ce.roadcollector.data.ThemeMode
import com.rein4ce.roadcollector.data.WeatherMotion
import com.rein4ce.roadcollector.ui.common.FramedDialog
import com.rein4ce.roadcollector.ui.common.PressIconButton
import com.rein4ce.roadcollector.ui.common.PressTextButton
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import kotlin.math.roundToInt

/**
 * 設定（採集画面の右上の歯車から開く）。選ぶとすぐに効き、スマホの中に保存する。
 *
 * 最初の画面は、項目の名前と今の設定だけの一覧（長くならないように）。項目を押すと、同じ枠の中で
 * 選ぶ画面に切り替わる（枠の下の丸いボタンが「＜」になり、それか戻る・枠の外で一覧に戻る。一覧では「×」で設定を閉じる）。
 * - 画面の明るさ：端末に合わせる・ライト・ダーク
 * - 背景の天気の動き：スライダーで 4 段階（出さない・止める・ふつう・なめらか）。選んでいる段階の説明を下に出す
 * - BGM：オン・オフのスイッチと、曲（トコトコ・ピコピコ・ウクレレを「‹ ›」で切り替える）、音量のスライダー（10 段階）。オフのときは曲と音量を選べない。初期設定に戻すボタン
 * - 効果音：オン・オフのスイッチ（ボタン・積み木・道を 1 本集めたときの木琴の音。BGM とは別に切り替えられる）
 * - 記録：記録中は画面を消さない、AI に名前を考えてもらう（オン・オフと、その説明）
 * - みんなのタワー：ニックネーム（作者名。入力の窓で付ける）
 * - デモ（発表で使う）：見本のパーツ（コレクションに出す・出さない）、デモの天気（背景とデモ散歩の天気を選んだ天気にする）。
 *   [DEMO_SETTINGS] が false なら出さない
 * - その他：使い方（初めて開いたときに出すものと同じ）、アプリについて
 *
 * 説明の文は「です・ます」で書く
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val settings = RoadCollectorApp.from(LocalContext.current).settings
    val theme by settings.themeMode.collectAsStateWithLifecycle()
    val motion by settings.weatherMotion.collectAsStateWithLifecycle()
    val keepOn by settings.keepScreenOn.collectAsStateWithLifecycle()
    val aiNaming by settings.aiNaming.collectAsStateWithLifecycle()
    val haptics by settings.haptics.collectAsStateWithLifecycle()
    val bgmOn by settings.bgmOn.collectAsStateWithLifecycle()
    val bgmVolume by settings.bgmVolume.collectAsStateWithLifecycle()
    val bgmTrack by settings.bgmTrack.collectAsStateWithLifecycle()
    val sfxOn by settings.sfxOn.collectAsStateWithLifecycle()
    val nickname by settings.nickname.collectAsStateWithLifecycle()
    val demoSky by settings.demoSky.collectAsStateWithLifecycle()
    // 見本のパーツがコレクションにあるか（1 本でもあればオン。読み込み中は切り替えさせない）
    val repository = RoadCollectorApp.from(LocalContext.current).partRepository
    val parts by repository.parts.collectAsStateWithLifecycle()
    val partsLoaded by repository.isLoaded.collectAsStateWithLifecycle()
    val samplesShown = parts.any { SampleParts.byId(it.id) != null }
    val systemDark = isSystemInDarkTheme()
    var page by remember { mutableStateOf(Page.LIST) }
    var editingNickname by remember { mutableStateOf(false) }
    val back = { page = Page.LIST }

    // 同じ枠の中で中身を切り替える（窓を開き直さないので、ちらつかない）
    FramedDialog(
        title = page.title,
        onDismiss = { if (page == Page.LIST) onDismiss() else back() },
        onClose = onDismiss,
        onBack = if (page == Page.LIST) null else back,
    ) {
        when (page) {
            Page.LIST -> {
                // ニックネーム（「みんな」に出る作者名）を、いちばん上に置く（名前を付ける所なので、最初に目に入るように）
                Section("みんなのタワー") {
                    LinkRow("ニックネーム", nickname.ifBlank { "名無し" }) { editingNickname = true }
                }
                // 前は「見た目」。振動も入れたので「システム」にした
                Section("システム") {
                    LinkRow(Page.THEME.title, theme.label) { page = Page.THEME }
                    ItemDivider()
                    LinkRow(Page.MOTION.title, motion.label) { page = Page.MOTION }
                    ItemDivider()
                    LinkRow(Page.HAPTICS.title, onOff(haptics)) { page = Page.HAPTICS }
                    ItemDivider()
                    LinkRow(Page.BGM.title, if (bgmOn) "${bgmTrack.label}・音量 $bgmVolume" else "オフ") { page = Page.BGM }
                    ItemDivider()
                    LinkRow(Page.SFX.title, onOff(sfxOn)) { page = Page.SFX }
                }
                Section("記録") {
                    LinkRow(Page.KEEP_ON.title, onOff(keepOn)) { page = Page.KEEP_ON }
                    ItemDivider()
                    LinkRow(Page.AI.title, onOff(aiNaming)) { page = Page.AI }
                }
                // 発表が終わったら、DEMO_SETTINGS（AppSettings.kt）を false にすれば出なくなる
                if (DEMO_SETTINGS) {
                    Section("デモ") {
                        LinkRow(Page.SAMPLES.title, if (samplesShown) "出す" else "出さない") { page = Page.SAMPLES }
                        ItemDivider()
                        LinkRow(Page.DEMO_SKY.title, demoSky.label) { page = Page.DEMO_SKY }
                    }
                }
                Section("その他") {
                    LinkRow(Page.HOWTO.title, null) { page = Page.HOWTO }
                    ItemDivider()
                    LinkRow(Page.ABOUT.title, null) { page = Page.ABOUT }
                }
            }
            Page.THEME -> Column(modifier = Modifier.selectableGroup().padding(top = 8.dp)) {
                for (m in ThemeMode.entries) {
                    Choice(
                        label = m.label,
                        // 端末の設定がいまどちらかも出す（選んだときにどちらになるか分かるように）
                        note = if (m == ThemeMode.SYSTEM) (if (systemDark) "いまはダークです" else "いまはライトです") else null,
                        selected = m == theme,
                        onSelect = { settings.setThemeMode(m) },
                    )
                }
            }
            Page.MOTION -> MotionSlider(motion = motion, onChange = settings::setWeatherMotion)
            Page.HAPTICS -> SwitchPage(
                checked = haptics,
                onChange = settings::setHaptics,
                explanation = "ボタンを押したときなどに、端末を軽く振動させます。",
            )
            Page.BGM -> BgmPage(
                on = bgmOn,
                track = bgmTrack,
                volume = bgmVolume,
                onOnChange = settings::setBgmOn,
                onTrackChange = settings::setBgmTrack,
                onVolumeChange = settings::setBgmVolume,
                onReset = {
                    settings.setBgmOn(true)
                    settings.setBgmTrack(BgmTrack.MARIMBA)
                    settings.setBgmVolume(AppSettings.DEFAULT_BGM_VOLUME)
                },
            )
            Page.SFX -> SwitchPage(
                checked = sfxOn,
                onChange = settings::setSfxOn,
                explanation = "ボタンを押したときや、題字の積み木を押したとき、道を 1 本集めたときに、木琴の音を鳴らします。" +
                    "音の大きさは、端末の音量で変わります。",
            )
            Page.KEEP_ON -> SwitchPage(
                checked = keepOn,
                onChange = settings::setKeepScreenOn,
                explanation = "記録中は画面をつけたままにします。電池を多く使います。",
            )
            Page.AI -> SwitchPage(
                checked = aiNaming,
                onChange = settings::setAiNaming,
                explanation = "記録したパーツに AI が名前を付けます。送るのは形・距離・日時だけです。",
            )
            Page.SAMPLES -> {
                Toggle(
                    label = if (samplesShown) "出す" else "出さない",
                    note = null,
                    checked = samplesShown,
                    onChange = { on ->
                        if (!partsLoaded) return@Toggle
                        if (on) repository.restoreMissingSamples() else repository.removeSamples()
                    },
                )
                Text(
                    "コレクションに見本（${SampleParts.all.size} 本）を出します。記録したパーツはそのままです。",
                    style = noteStyle,
                    fontSize = 14.sp,
                    color = Fieldbook.Ink,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
            Page.DEMO_SKY -> Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    "背景とデモ散歩の天気を、選んだ天気にします（発表用）。ふだんは「本当の天気」です。",
                    style = noteStyle,
                    fontSize = 14.sp,
                    color = Fieldbook.Ink,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Column(modifier = Modifier.selectableGroup()) {
                    for (sky in DemoSky.entries) {
                        Choice(label = sky.label, note = null, selected = sky == demoSky, onSelect = { settings.setDemoSky(sky) })
                    }
                }
            }
            Page.HOWTO -> HowToContent()
            Page.ABOUT -> AboutContent()
        }
    }

    if (editingNickname) {
        NicknameDialog(
            current = nickname,
            onSave = { settings.setNickname(it); editingNickname = false },
            onDismiss = { editingNickname = false },
        )
    }
}

/** 設定の中の画面。[title] は見出しの帯と、一覧の項目の名前 */
private enum class Page(val title: String) {
    LIST("設定"),
    THEME("画面の明るさ"),
    MOTION("背景の天気の動き"),
    HAPTICS("振動"),
    BGM("BGM"),
    SFX("効果音"),
    KEEP_ON("記録中は画面を消さない"),
    AI("AI に名前を考えてもらう"),
    SAMPLES("見本のパーツ"),
    DEMO_SKY("デモの天気"),
    HOWTO("使い方"),
    ABOUT("アプリについて"),
}

private fun onOff(on: Boolean) = if (on) "オン" else "オフ"

/**
 * 背景の天気の動きを選ぶスライダー（左ほど動きが少ない）。目盛りの下に段階の名前、その下に選んでいる段階の説明。
 * 動かすとすぐに効く（後ろの天気が変わる）。段階の名前を押しても選べる
 */
@Composable
private fun MotionSlider(motion: WeatherMotion, onChange: (WeatherMotion) -> Unit) {
    val stops = WeatherMotion.entries
    fun select(i: Int) {
        val m = stops[i.coerceIn(0, stops.lastIndex)]
        if (m != motion) onChange(m)
    }
    Column(modifier = Modifier.padding(top = 12.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 段階の名前は等しい幅のます目のまん中に置き、スライダーの目盛りをそのまん中に合わせる
            // （スライダーの両端を、ます目の半分 − つまみの半分だけ内側に寄せる）。文字が大きくても名前どうしが重ならない
            val cell = maxWidth / stops.size
            // 段階の名前の字の大きさ。ふだんは 14sp、文字を大きくしている端末では、いちばん長い名前がます目に 1 行で収まるまで
            // 小さくする（4 つとも同じ大きさにそろえる。日本語の字はほぼ正方形なので、字数 × 字の大きさ ≒ 幅）
            val longest = stops.maxOf { it.label.length }
            val labelSize = with(LocalDensity.current) {
                minOf(14.sp.toPx(), (cell - LabelPadding * 2).toPx() / (longest + 0.5f)).toSp()
            }
            Column {
                Slider(
                    value = motion.ordinal.toFloat(),
                    onValueChange = { select(it.roundToInt()) },
                    valueRange = 0f..stops.lastIndex.toFloat(),
                    steps = stops.size - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = Fieldbook.Survey,
                        activeTrackColor = Fieldbook.Survey,
                        activeTickColor = Fieldbook.Card,
                        inactiveTrackColor = Fieldbook.CardEdge,
                        inactiveTickColor = Fieldbook.Muted,
                    ),
                    modifier = Modifier
                        .padding(horizontal = (cell / 2 - SliderThumbHalfWidth).coerceAtLeast(0.dp))
                        .semantics {
                            contentDescription = Page.MOTION.title
                            stateDescription = motion.label
                        },
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    stops.forEachIndexed { i, m ->
                        val selected = m == motion
                        Text(
                            m.label,
                            fontSize = labelSize,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Fieldbook.Survey else Fieldbook.Muted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { select(i) }
                                .padding(vertical = 6.dp, horizontal = LabelPadding)
                                // 読み上げではスライダーだけを使う（同じ選択肢を 2 度読まない）
                                .clearAndSetSemantics {},
                        )
                    }
                }
            }
        }
        // 選んでいる段階の説明。段階で行の数が変わっても、窓の高さが変わらないように 2 行ぶん取っておく
        Text(
            motion.note,
            style = noteStyle,
            fontSize = 14.sp,
            color = Fieldbook.Ink,
            minLines = 2,
            modifier = Modifier
                .padding(top = 14.dp, bottom = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * BGM の画面：いちばん上にオン・オフのスイッチ、その下に曲（「‹ トコトコ ›」で切り替える）と音量のスライダー（1〜10）。
 * オフのときは、曲と音量をうすくして選べない。説明、いちばん下に「初期設定に戻す」（オン・トコトコ・音量 [AppSettings.DEFAULT_BGM_VOLUME]）。
 * 選ぶ・動かすとすぐに音が変わる（曲は、新しい曲の頭から流れる）
 */
@Composable
private fun BgmPage(
    on: Boolean,
    track: BgmTrack,
    volume: Int,
    onOnChange: (Boolean) -> Unit,
    onTrackChange: (BgmTrack) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onReset: () -> Unit,
) {
    Toggle(label = if (on) "オン" else "オフ", note = null, checked = on, onChange = onOnChange)
    Column(modifier = Modifier.padding(top = 8.dp).alpha(if (on) 1f else 0.45f)) {
        TrackPicker(track = track, enabled = on, onChange = onTrackChange)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("音量", fontSize = 15.sp, color = Fieldbook.Ink, modifier = Modifier.weight(1f))
            Text("$volume", fontFamily = Fieldbook.Data, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Survey)
        }
        Slider(
            value = volume.toFloat(),
            onValueChange = { v -> v.roundToInt().let { if (it != volume) onVolumeChange(it) } },
            valueRange = 1f..AppSettings.MAX_BGM_VOLUME.toFloat(),
            steps = AppSettings.MAX_BGM_VOLUME - 2,
            enabled = on,
            colors = SliderDefaults.colors(
                thumbColor = Fieldbook.Survey,
                activeTrackColor = Fieldbook.Survey,
                activeTickColor = Fieldbook.Card,
                inactiveTrackColor = Fieldbook.CardEdge,
                inactiveTickColor = Fieldbook.Muted,
                disabledThumbColor = Fieldbook.Muted,
                disabledActiveTrackColor = Fieldbook.Muted,
                disabledActiveTickColor = Fieldbook.Card,
                disabledInactiveTrackColor = Fieldbook.CardEdge,
                disabledInactiveTickColor = Fieldbook.Muted,
            ),
            modifier = Modifier.semantics {
                contentDescription = "BGM の音量"
                stateDescription = "${AppSettings.MAX_BGM_VOLUME} 段階の $volume"
            },
        )
        Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            Text("小さい", fontSize = 12.sp, color = Fieldbook.Muted, modifier = Modifier.weight(1f))
            Text("大きい", fontSize = 12.sp, color = Fieldbook.Muted)
        }
    }
    Text(
        "全部の画面で流れます。ほかのアプリで音楽を流しているときは流しません。",
        style = noteStyle,
        fontSize = 14.sp,
        color = Fieldbook.Ink,
        modifier = Modifier.padding(top = 10.dp),
    )
    // 初期設定（オン・トコトコ・音量 まん中）に戻す。すでに初期設定なら押せない
    val isDefault = on && track == BgmTrack.MARIMBA && volume == AppSettings.DEFAULT_BGM_VOLUME
    PressTextButton(onClick = onReset, enabled = !isDefault, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Text("初期設定に戻す", color = if (isDefault) Fieldbook.Mark else Fieldbook.Survey, fontWeight = FontWeight.Bold)
    }
}

/**
 * BGM の曲を「‹ 曲の名前 ›」で切り替える行。端まで行ったら反対の端へ回る。押すとすぐに新しい曲の頭から流れる。
 * 曲の説明は出さない（名前と、流れる曲そのもので分かる。B が決めた）。
 * 読み上げでは、名前の所は「曲 トコトコ、3 曲の 1 曲目」、ボタンは「前の曲」「次の曲」。切り替えると名前を読み直す
 */
@Composable
private fun TrackPicker(track: BgmTrack, enabled: Boolean, onChange: (BgmTrack) -> Unit) {
    val all = BgmTrack.entries
    val index = all.indexOf(track)
    fun step(by: Int) = onChange(all[Math.floorMod(index + by, all.size)])
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("曲", fontSize = 15.sp, color = Fieldbook.Ink, modifier = Modifier.weight(1f))
        PressIconButton(onClick = { step(-1) }, enabled = enabled) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前の曲", tint = Fieldbook.Survey)
        }
        Text(
            track.label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Fieldbook.Survey,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .widthIn(min = TrackNameWidth)
                .semantics {
                    contentDescription = "曲 ${track.label}、${all.size} 曲の ${index + 1} 曲目"
                    liveRegion = LiveRegionMode.Polite
                },
        )
        PressIconButton(onClick = { step(1) }, enabled = enabled) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "次の曲", tint = Fieldbook.Survey)
        }
    }
}

/** 曲の名前の幅（いちばん長い名前が入る幅にそろえ、切り替えても「‹」「›」の場所が動かないように） */
private val TrackNameWidth = 104.dp

/** スライダーの段階の名前の左右の余白 */
private val LabelPadding = 2.dp

/** M3 のスライダーのつまみの幅の半分（つまみのまん中は、スライダーの端からこれだけ内側までしか行かない） */
private val SliderThumbHalfWidth = 2.dp

/** オン・オフを切り替える画面：スイッチの行と、その下にくわしい説明 */
@Composable
private fun SwitchPage(checked: Boolean, onChange: (Boolean) -> Unit, explanation: String) {
    Toggle(label = if (checked) "オン" else "オフ", note = null, checked = checked, onChange = onChange)
    Text(explanation, style = noteStyle, fontSize = 14.sp, color = Fieldbook.Ink, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
}

/**
 * ひとまとまりの項目（見た目・採集など）。[title] があれば上に小さな見出し。
 * まとまりごとに 1 つの枠（コレクションの紙片と同じ、紙の色の角丸）でくくり、中の項目は細い線（[ItemDivider]）で分ける
 */
@Composable
private fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Fieldbook.Muted,
                modifier = Modifier.semantics { heading() },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Fieldbook.Card)
                .border(1.dp, Fieldbook.CardEdge, shape),
            content = content,
        )
    }
}

/** 枠の中の項目と項目のあいだの細い線 */
@Composable
private fun ItemDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 14.dp), color = Fieldbook.CardEdge)
}

/** 1 つだけ選ぶ項目の 1 行（行ごと押せる） */
@Composable
private fun Choice(label: String, note: String?, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null, // 行ごと押せる（selectable）
            colors = RadioButtonDefaults.colors(selectedColor = Fieldbook.Survey, unselectedColor = Fieldbook.Muted),
        )
        Spacer(Modifier.width(8.dp))
        Labels(label, note, modifier = Modifier.padding(vertical = 4.dp))
    }
}

/** オン・オフの項目の 1 行（行ごと押せる） */
@Composable
private fun Toggle(label: String, note: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Labels(label, note, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null, // 行ごと押せる（toggleable）
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = Fieldbook.Survey,
                uncheckedThumbColor = Fieldbook.Muted,
                uncheckedTrackColor = Fieldbook.Card,
                uncheckedBorderColor = Fieldbook.Muted,
            ),
        )
    }
}

/** 押すと別の画面が開く項目の 1 行。左に名前、右に今の設定と「›」（まとまりの枠の中に並べる） */
@Composable
private fun LinkRow(label: String, value: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, color = Fieldbook.Ink, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, fontSize = 15.sp, color = Fieldbook.Muted, maxLines = 1)
        }
        Text("　›", fontSize = 18.sp, color = Fieldbook.Muted)
    }
}

/** 項目の名前と、その下の小さな説明 */
@Composable
private fun Labels(label: String, note: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 16.sp, color = Fieldbook.Ink)
        if (note != null) Text(note, style = noteStyle, fontSize = 12.sp, color = Fieldbook.Muted)
    }
}
