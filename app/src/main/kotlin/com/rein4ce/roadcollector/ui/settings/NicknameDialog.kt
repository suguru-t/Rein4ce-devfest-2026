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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.rein4ce.roadcollector.data.AppSettings
import com.rein4ce.roadcollector.data.nicknameHintsFor
import com.rein4ce.roadcollector.net.ApiClient
import com.rein4ce.roadcollector.ui.common.PressTextButton
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.notes
import com.rein4ce.roadcollector.ui.inventory.AiNameButton
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import kotlinx.coroutines.launch

/**
 * ニックネームを付ける（「みんなのタワー」に作者名として出る）。見た目はパーツの「名前を付ける」と同じ。
 * 空にすると名無しに戻る。[AppSettings.MAX_NICKNAME_LENGTH] 文字を超えると保存できない（入力は止めない）。
 *
 * 「AI に考えてもらう」で、サーバ（Gemini）にニックネームを考えてもらい、入力欄に入れる。保存するかは自分で決める。
 * 手がかりは、コレクションのパーツの名前・本数・合計距離だけ（[nicknameHintsFor]。場所は送らない）。
 * 設定で AI の名前付けをオフにしていたら、ボタンを出さない（何も送らない）
 */
@Composable
internal fun NicknameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val app = RoadCollectorApp.from(LocalContext.current)
    val aiOn by app.settings.aiNaming.collectAsStateWithLifecycle()
    val parts by app.partRepository.parts.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(TextFieldValue(current, selection = TextRange(current.length))) }
    val length = text.text.trim().let { it.codePointCount(0, it.length) }
    val max = AppSettings.MAX_NICKNAME_LENGTH
    val tooLong = length > max
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // AI にニックネームを考えてもらっているところ（窓を閉じると取りやめる）
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<Boolean?>(null) } // true：考えられた、false：考えられなかった
    fun ask() {
        asking = true
        aiResult = null
        scope.launch {
            val name = ApiClient.suggestNickname(nicknameHintsFor(parts))
            asking = false
            if (name != null) {
                text = TextFieldValue(name, selection = TextRange(name.length))
                aiResult = true
            } else {
                aiResult = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = Fieldbook.Paper,
        titleContentColor = Fieldbook.Ink,
        textContentColor = Fieldbook.Ink,
        title = { Text("ニックネーム", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
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
                // 設定で AI の名前付けをオフにしていたら、ボタンを出さない
                if (aiOn) {
                    AiNameButton(asking = asking, again = aiResult == true, onClick = ::ask)
                    Text(
                        when (aiResult) {
                            true -> notes("AI の案です", "直してから保存もできます")
                            false -> notes("ニックネームを考えられませんでした", "もう一度押すか、自分で付けてください")
                            null -> notes("場所は送らず、集めた道の名前・本数・距離で考えます")
                        },
                        style = noteStyle,
                        fontSize = 12.sp,
                        color = if (aiResult == false) Fieldbook.Danger else Fieldbook.Muted,
                        // 結果は入力欄の外に出るので、読み上げでも知らせる
                        modifier = Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
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
