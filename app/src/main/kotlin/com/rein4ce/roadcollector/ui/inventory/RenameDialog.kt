package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.net.ApiClient
import com.rein4ce.roadcollector.ui.common.AiNamingRow
import com.rein4ce.roadcollector.ui.common.PressTextButton
import com.rein4ce.roadcollector.ui.common.ProvideAppHaptics
import com.rein4ce.roadcollector.ui.common.fieldbookTextFieldColors
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.notes
import com.rein4ce.roadcollector.ui.common.rememberAiNaming

/** 名前の長さの上限。命名 API と同じ（docs/decisions.md の 9 番）で、カードに収まる長さ */
internal const val MAX_NAME_LENGTH = 10

/** 名前の文字数。前後の空白は数えない（保存のときに落とすため）。絵文字を 2 文字と数えないよう、コードポイントで数える */
internal fun nameLength(name: String): Int = name.trim().let { it.codePointCount(0, it.length) }

/**
 * パーツに名前を付ける（付け直す）ダイアログ。見た目は削除の確認と同じ野帳の紙。
 * - 今の名前が入った状態で開く。名前がなければ空で、「341 m の散歩」をうすく出す
 * - 空にして保存すると名前を外し、「341 m の散歩」の呼び名に戻る
 * - 10 文字を超えると保存できない。入力は止めない（日本語の変換中に切ると打ちにくいため）
 * - 「AI に考えてもらう」で、サーバの命名 API（Gemini）に名前を考えてもらい、入力欄に入れる。保存するかは自分で決める。
 *   考えられなかったときは、入力欄はそのままにして知らせる（decisions.md の 7）。送るのは形・距離・日時だけ（8）
 *
 * @param aiOn    「AI に考えてもらう」を出すか。設定（ホームの歯車）で AI の名前付けをオフにしていたら false
 * @param suggest 名前を考えてもらう。考えられなければ null（テストやプレビューでは差し替える）
 */
@Composable
internal fun RenameDialog(
    part: Part,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    aiOn: Boolean,
    suggest: suspend (Part) -> String? = ApiClient::namePart,
) {
    val fallback = "${part.distanceM} m の散歩"
    val current = part.name.orEmpty()
    var text by remember { mutableStateOf(TextFieldValue(current, selection = TextRange(current.length))) }
    val length = nameLength(text.text)
    val tooLong = length > MAX_NAME_LENGTH
    // 開いたらすぐ打てるように、入力欄にカーソルを置く（キーボードも出る）
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // AI に名前を考えてもらっているところ（ダイアログを閉じると取りやめる。ui/common/NameField.kt）
    val naming = rememberAiNaming()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = Fieldbook.Paper,
        titleContentColor = Fieldbook.Ink,
        textContentColor = Fieldbook.Ink,
        title = { Text("名前を付ける", fontWeight = FontWeight.Bold) },
        // 入力欄で字を長押しして選ぶときの振動も、設定の「振動」に従わせる（ui/common/Haptics.kt）
        text = { ProvideAppHaptics {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    // AI に聞いている間は打てなくする（届いた案で、待っている間に打った字を上書きしないように）
                    readOnly = naming.asking,
                    singleLine = true,
                    label = { Text("名前") },
                    placeholder = { Text(fallback) },
                    isError = tooLong,
                    supportingText = {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                // 数と単位（「10 文字」「488 m」）が別の行に分かれないよう、notes() を通す
                                notes(if (tooLong) "$MAX_NAME_LENGTH 文字までです" else "空なら「$fallback」に戻る"),
                                style = noteStyle,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            )
                            Text("$length/$MAX_NAME_LENGTH", fontFamily = Fieldbook.Data)
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!tooLong) onSave(text.text) }),
                    shape = RoundedCornerShape(4.dp),
                    colors = fieldbookTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                // 設定で AI の名前付けをオフにしていたら、ボタンを出さない
                if (aiOn) {
                    AiNamingRow(
                        naming = naming,
                        enabled = true,
                        idleNote = notes("歩いた場所（位置）は送らず、形・距離・日時で考えます"),
                        failedNote = notes("名前を考えられませんでした", "もう一度押すか、自分で付けてください"),
                        onAsk = {
                            naming.ask(suggest = { suggest(part) }) { name -> text = TextFieldValue(name, selection = TextRange(name.length)) }
                        },
                    )
                }
            }
        } },
        confirmButton = {
            PressTextButton(onClick = { onSave(text.text) }, enabled = !tooLong) {
                Text("保存", color = if (tooLong) Fieldbook.Mark else Fieldbook.Survey, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            PressTextButton(onClick = onDismiss) { Text("やめる", color = Fieldbook.Muted) }
        },
    )
}
