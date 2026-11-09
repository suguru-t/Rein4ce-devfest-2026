package com.rein4ce.roadcollector.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * 名前を付ける窓（パーツの「名前を付ける」・ニックネーム）で共通の部品。
 * 入力欄の色、「AI に考えてもらう」ボタンと、その下の一言、AI に聞いている間の流れ。
 * 2 つの窓で同じ作りにして、片方だけ直す心配がないようにする（#81 のレビュー）
 */

/** 野帳の紙の上の入力欄の色（カードの白地に墨の字、押している間は測量の青、長すぎるときは赤） */
@Composable
internal fun fieldbookTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Fieldbook.Ink,
    unfocusedTextColor = Fieldbook.Ink,
    errorTextColor = Fieldbook.Ink,
    focusedContainerColor = Fieldbook.Card,
    unfocusedContainerColor = Fieldbook.Card,
    errorContainerColor = Fieldbook.Card,
    cursorColor = Pop.TealLine,
    errorCursorColor = Fieldbook.Danger,
    focusedBorderColor = Pop.TealLine,
    unfocusedBorderColor = Fieldbook.CardEdge,
    errorBorderColor = Fieldbook.Danger,
    focusedLabelColor = Pop.TealLine,
    unfocusedLabelColor = Fieldbook.Muted,
    errorLabelColor = Fieldbook.Danger,
    focusedPlaceholderColor = Fieldbook.Mark,
    unfocusedPlaceholderColor = Fieldbook.Mark,
    focusedSupportingTextColor = Fieldbook.Muted,
    unfocusedSupportingTextColor = Fieldbook.Muted,
    errorSupportingTextColor = Fieldbook.Danger,
)

/**
 * AI に名前を考えてもらっている流れ。窓を閉じると取りやめる（窓の rememberCoroutineScope で動かすため）。
 * 考えている間（[asking]）は、入力欄を打てなくする（届いた案で、待っている間に打った字を上書きしないように。#81 のレビュー）
 */
@Stable
internal class AiNaming(private val scope: CoroutineScope) {
    /** 考えてもらっているところか */
    var asking by mutableStateOf(false)
        private set

    /** 前に聞いた結果。true：案が入った、false：考えられなかった、null：まだ聞いていない */
    var result by mutableStateOf<Boolean?>(null)
        private set

    /** [suggest] で名前を考えてもらい、考えられたら [onName] で入力欄に入れる */
    fun ask(suggest: suspend () -> String?, onName: (String) -> Unit) {
        if (asking) return
        asking = true
        result = null
        scope.launch {
            val name = try {
                suggest()
            } finally {
                asking = false
            }
            if (name != null) onName(name)
            result = name != null
        }
    }
}

@Composable
internal fun rememberAiNaming(): AiNaming {
    val scope = rememberCoroutineScope()
    return remember(scope) { AiNaming(scope) }
}

/**
 * 「AI に考えてもらう」ボタンと、その下の一言（何を送るか・案が入ったか・考えられなかったか）。
 *
 * @param enabled 押せるか（コレクションの読み込みが終わるまでなど）。考えている間は、これにかかわらず押せない
 * @param idleNote まだ聞いていないときの一言（何を送るか）
 * @param failedNote 考えられなかったときの一言
 */
@Composable
internal fun AiNamingRow(naming: AiNaming, enabled: Boolean, idleNote: String, failedNote: String, onAsk: () -> Unit) {
    AiNameButton(asking = naming.asking, again = naming.result == true, enabled = enabled, onClick = onAsk)
    Text(
        when (naming.result) {
            true -> notes("AI の案です", "直してから保存もできます")
            false -> failedNote
            null -> idleNote
        },
        style = noteStyle,
        fontSize = 12.sp,
        color = if (naming.result == false) Fieldbook.Danger else Fieldbook.Muted,
        // 結果は入力欄の外に出るので、読み上げでも知らせる
        modifier = Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** 「AI に考えてもらう」ボタン。考えている間は押せず、「考え中…」と回る印を出す */
@Composable
internal fun AiNameButton(asking: Boolean, again: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    PressTextButton(onClick = onClick, enabled = enabled && !asking) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (asking) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Pop.TealLine)
            Text(
                when {
                    asking -> "考え中…"
                    again -> "AI にもう一度考えてもらう"
                    else -> "AI に考えてもらう"
                },
                color = if (asking || !enabled) Fieldbook.Muted else Pop.TealLine,
                fontFamily = Pop.Rounded,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

/**
 * 名前を付ける窓の枠（パーツの「名前を付ける」と、設定のニックネーム。決定 56）。設定やコレクションの拡大表示と同じポップな枠
 * （紺の名札の見出し・下に側面がのぞく紙の面）に中身（入力欄と AI のボタン）を載せ、いちばん下に「保存」（青緑のブロック）。
 * やめるときは、枠の下の丸い「×」か、枠の外を押す
 *
 * @param saveEnabled 「保存」を押せるか（字数を超えているときは押せない）
 */
@Composable
internal fun NameDialogFrame(
    title: String,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    FramedDialog(
        title = title,
        onDismiss = onDismiss,
        pop = true,
        nameTag = false,
        footer = {
            PopButton("保存", onClick = onSave, face = Pop.TealDeep, text = Pop.Letter, enabled = saveEnabled, modifier = Modifier.fillMaxWidth())
        },
    ) {
        // 入力欄で字を長押しして選ぶときの振動も、設定の「振動」に従わせる（ui/common/Haptics.kt）
        ProvideAppHaptics { Column(content = content) }
    }
}
