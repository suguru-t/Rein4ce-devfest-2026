package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.net.ApiClient
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.notes
import kotlinx.coroutines.launch

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
 * @param suggest 名前を考えてもらう。考えられなければ null（テストやプレビューでは差し替える）
 */
@Composable
internal fun RenameDialog(
    part: Part,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
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
    // AI に名前を考えてもらっているところ（ダイアログを閉じると取りやめる）
    val scope = rememberCoroutineScope()
    val aiOn by RoadCollectorApp.from(LocalContext.current).settings.aiNaming.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<AiResult?>(null) }
    fun ask() {
        asking = true
        aiResult = null
        scope.launch {
            val name = suggest(part)
            asking = false
            if (name != null) {
                text = TextFieldValue(name, selection = TextRange(name.length))
                aiResult = AiResult.Named
            } else {
                aiResult = AiResult.Failed
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = Fieldbook.Paper,
        titleContentColor = Fieldbook.Ink,
        textContentColor = Fieldbook.Ink,
        title = { Text("名前を付ける", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Fieldbook.Ink,
                        unfocusedTextColor = Fieldbook.Ink,
                        errorTextColor = Fieldbook.Ink,
                        focusedContainerColor = Fieldbook.Card,
                        unfocusedContainerColor = Fieldbook.Card,
                        errorContainerColor = Fieldbook.Card,
                        cursorColor = Fieldbook.Survey,
                        errorCursorColor = Fieldbook.Danger,
                        focusedBorderColor = Fieldbook.Survey,
                        unfocusedBorderColor = Fieldbook.CardEdge,
                        errorBorderColor = Fieldbook.Danger,
                        focusedLabelColor = Fieldbook.Survey,
                        unfocusedLabelColor = Fieldbook.Muted,
                        errorLabelColor = Fieldbook.Danger,
                        focusedPlaceholderColor = Fieldbook.Mark,
                        unfocusedPlaceholderColor = Fieldbook.Mark,
                        focusedSupportingTextColor = Fieldbook.Muted,
                        unfocusedSupportingTextColor = Fieldbook.Muted,
                        errorSupportingTextColor = Fieldbook.Danger,
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                // 設定（採集画面の歯車）で AI の名前付けをオフにしていたら、ボタンを出さない
                if (aiOn) {
                    AiNameButton(asking = asking, again = aiResult == AiResult.Named, onClick = ::ask)
                    Text(
                        when (aiResult) {
                            AiResult.Named -> notes("AI の案です", "直してから保存もできます")
                            AiResult.Failed -> notes("名前を考えられませんでした", "もう一度押すか、自分で付けてください")
                            null -> notes("場所は送らず、形・距離・日時で考えます")
                        },
                        style = noteStyle,
                        fontSize = 12.sp,
                        color = if (aiResult == AiResult.Failed) Fieldbook.Danger else Fieldbook.Muted,
                        // 結果は入力欄の外に出るので、読み上げでも知らせる
                        modifier = Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.text) }, enabled = !tooLong) {
                Text("保存", color = if (tooLong) Fieldbook.Mark else Fieldbook.Survey, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("やめる", color = Fieldbook.Muted) }
        },
    )
}

/** AI に名前を考えてもらった結果 */
private enum class AiResult { Named, Failed }

/** 「AI に考えてもらう」ボタン。考えている間は押せず、「考え中…」と回る印を出す */
@Composable
private fun AiNameButton(asking: Boolean, again: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = !asking) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (asking) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Fieldbook.Survey)
            Text(
                when {
                    asking -> "考え中…"
                    again -> "AI にもう一度考えてもらう"
                    else -> "AI に考えてもらう"
                },
                color = if (asking) Fieldbook.Muted else Fieldbook.Survey,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
