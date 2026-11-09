package com.rein4ce.roadcollector.ui.settings

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.data.AppSettings
import com.rein4ce.roadcollector.data.nicknameHintsFor
import com.rein4ce.roadcollector.net.ApiClient
import com.rein4ce.roadcollector.ui.common.AiNamingRow
import com.rein4ce.roadcollector.ui.common.PressTextButton
import com.rein4ce.roadcollector.ui.common.ProvideAppHaptics
import com.rein4ce.roadcollector.ui.common.fieldbookTextFieldColors
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.notes
import com.rein4ce.roadcollector.ui.common.rememberAiNaming
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * ニックネームを付ける（「みんなのタワー」に作者名として出る）。見た目はパーツの「名前を付ける」と同じ（部品は ui/common/NameField.kt）。
 * 空にすると名無しに戻る。[AppSettings.MAX_NICKNAME_LENGTH] 文字を超えると保存できない（入力は止めない）。
 *
 * 「AI に考えてもらう」で、サーバ（Gemini）にニックネームを考えてもらい、入力欄に入れる。保存するかは自分で決める。
 * 手がかりは、コレクションのパーツに付いている名前・本数・合計距離だけ（[nicknameHintsFor]。歩いた場所（位置）は送らない）。
 *
 * @param aiOn 「AI に考えてもらう」を出すか。設定で AI の名前付けをオフにしていたら false（何も送らない）
 * @param suggest ニックネームを考えてもらう。考えられなければ null を返す。コレクションの読み込みが終わるまでは null
 *   （読み込み前に押すと、パーツが 0 本として送られてしまうため。#81 のレビュー）。テストやプレビューでは差し替える
 */
@Composable
internal fun NicknameDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    aiOn: Boolean = rememberAiNamingOn(),
    suggest: (suspend () -> String?)? = rememberNicknameSuggester(),
) {
    var text by remember { mutableStateOf(TextFieldValue(current, selection = TextRange(current.length))) }
    val length = text.text.trim().let { it.codePointCount(0, it.length) }
    val max = AppSettings.MAX_NICKNAME_LENGTH
    val tooLong = length > max
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // AI にニックネームを考えてもらっているところ（窓を閉じると取りやめる）
    val naming = rememberAiNaming()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = Fieldbook.Paper,
        titleContentColor = Fieldbook.Ink,
        textContentColor = Fieldbook.Ink,
        title = { Text("ニックネーム", fontWeight = FontWeight.Bold) },
        // 入力欄で字を長押しして選ぶときの振動も、設定の「振動」に従わせる（ui/common/Haptics.kt）
        text = { ProvideAppHaptics {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    // AI に聞いている間は打てなくする（届いた案で、待っている間に打った字を上書きしないように）
                    readOnly = naming.asking,
                    singleLine = true,
                    label = { Text("ニックネーム") },
                    placeholder = { Text("名無し") },
                    isError = tooLong,
                    supportingText = {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                if (tooLong) notes("$max 文字までです") else notes("空なら名無しになります"),
                                style = noteStyle,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            )
                            Text("$length/$max", fontFamily = Fieldbook.Data)
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
                        // コレクションの読み込みが終わるまでは押せない
                        enabled = suggest != null,
                        // パーツの名前は自分で自由に打てるので、送らないのは「位置の情報」だと分かる言い方にする（#81 のレビュー）
                        idleNote = notes("歩いた場所（位置）は送らず、集めた道の名前・本数・距離で考えます"),
                        failedNote = notes("ニックネームを考えられませんでした", "もう一度押すか、自分で付けてください"),
                        onAsk = {
                            val ask = suggest ?: return@AiNamingRow
                            naming.ask(suggest = ask) { name -> text = TextFieldValue(name, selection = TextRange(name.length)) }
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

/** 設定の「AI に名前を考えてもらう」 */
@Composable
private fun rememberAiNamingOn(): Boolean {
    val enabled by RoadCollectorApp.from(LocalContext.current).settings.aiNaming.collectAsStateWithLifecycle()
    return enabled
}

/**
 * コレクションから手がかりを作って、サーバにニックネームを考えてもらう関数。コレクションの読み込みが終わるまでは null。
 * 押した瞬間のコレクションを使う（rememberUpdatedState）
 */
@Composable
private fun rememberNicknameSuggester(): (suspend () -> String?)? {
    val repository = RoadCollectorApp.from(LocalContext.current).partRepository
    val loaded by repository.isLoaded.collectAsStateWithLifecycle()
    val parts by repository.parts.collectAsStateWithLifecycle()
    val latest by rememberUpdatedState(parts)
    val suggest: suspend () -> String? = remember { { ApiClient.suggestNickname(nicknameHintsFor(latest)) } }
    return if (loaded) suggest else null
}
