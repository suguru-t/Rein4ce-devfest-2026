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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import com.rein4ce.roadcollector.data.ThemeMode
import com.rein4ce.roadcollector.data.WeatherMotion
import com.rein4ce.roadcollector.ui.common.FramedDialog
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import kotlin.math.roundToInt

/**
 * 設定（採集画面の右上の歯車から開く）。選ぶとすぐに効き、スマホの中に保存する。
 *
 * 最初の画面は、項目の名前と今の設定だけの一覧（長くならないように）。項目を押すと、同じ枠の中で
 * 選ぶ画面に切り替わる（見出しの帯の左の「＜」・戻る・枠の外で一覧に戻る。「×」は設定ごと閉じる）。
 * - 画面の明るさ：端末に合わせる・ライト・ダーク
 * - 背景の天気の動き：スライダーで 4 段階（出さない・止める・ふつう・なめらか）。選んでいる段階の説明を下に出す
 * - 記録：記録中は画面を消さない、AI に名前を考えてもらう（オン・オフと、その説明）
 * - みんなのタワー：ニックネーム（作者名。入力の窓で付ける）
 * - その他：アプリについて
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
    val nickname by settings.nickname.collectAsStateWithLifecycle()
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
                Section("見た目") {
                    LinkRow(Page.THEME.title, theme.label) { page = Page.THEME }
                    ItemDivider()
                    LinkRow(Page.MOTION.title, motion.label) { page = Page.MOTION }
                }
                Section("記録") {
                    LinkRow(Page.KEEP_ON.title, onOff(keepOn)) { page = Page.KEEP_ON }
                    ItemDivider()
                    LinkRow(Page.AI.title, onOff(aiNaming)) { page = Page.AI }
                }
                Section("みんなのタワー") {
                    LinkRow("ニックネーム", nickname.ifBlank { "名無し" }) { editingNickname = true }
                }
                Section("その他") {
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
            Page.KEEP_ON -> SwitchPage(
                checked = keepOn,
                onChange = settings::setKeepScreenOn,
                explanation = "記録している間は、画面を消しません。地図を見ながら歩くときに便利ですが、電池を多く使います。",
            )
            Page.AI -> SwitchPage(
                checked = aiNaming,
                onChange = settings::setAiNaming,
                explanation = "採集したパーツに、AI が名前を付けます。送るのは形・距離・日時だけで、歩いた場所は送りません。" +
                    "オフにすると何も送りません。",
            )
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
    KEEP_ON("記録中は画面を消さない"),
    AI("AI に名前を考えてもらう"),
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
