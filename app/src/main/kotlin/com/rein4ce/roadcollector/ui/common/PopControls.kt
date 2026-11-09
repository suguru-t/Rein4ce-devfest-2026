package com.rein4ce.roadcollector.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/*
 * ポップな見た目の入力の部品（設定の窓など。決定 56）。標準の Switch・RadioButton・Checkbox・Slider のつまみの代わりに、
 * 題字の積み木と同じ「下に濃い色の側面がのぞくブロック」で作る。どれも見た目だけで、押せる範囲は使う側が行ごと
 * （toggleable・selectable）で取る（行のどこを押しても切り替わり、読み上げも行ごとに伝わる）
 */

/**
 * オン・オフ。オンは青緑の面で、白い小さなブロック（つまみ）が右へ寄る。オフは灰色の面で、つまみは左。
 * つまみは弾むように動く（端末で動きを減らしていれば、すぐに切り替わる）
 */
@Composable
internal fun PopSwitch(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val reduceMotion = rememberReduceMotion()
    val on by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "PopSwitch",
    )
    val face = if (enabled) lerp(Fieldbook.Mark, Pop.TealDeep, on) else Fieldbook.GridMajor
    Box(
        modifier = modifier
            .size(width = SWITCH_WIDTH, height = SWITCH_HEIGHT + SWITCH_DEPTH)
            .popBlock(face = face, corner = SWITCH_HEIGHT / 2, depth = SWITCH_DEPTH),
    ) {
        val travel = SWITCH_WIDTH - KNOB_SIZE - KNOB_INSET * 2
        Box(
            modifier = Modifier
                .offset(x = KNOB_INSET + travel * on, y = (SWITCH_HEIGHT - KNOB_SIZE) / 2 - 1.dp)
                .size(width = KNOB_SIZE, height = KNOB_SIZE + KNOB_DEPTH)
                .popBlock(face = Pop.Letter, corner = KNOB_SIZE / 2, depth = KNOB_DEPTH),
        )
    }
}

/** 1 つだけ選ぶ印。選んでいるときは青緑の丸いブロックに白い点、選んでいないときは紙の色に灰色の縁 */
@Composable
internal fun PopRadio(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = MARK_SIZE, height = MARK_SIZE + MARK_DEPTH)
            .popBlock(
                face = if (selected) Pop.TealDeep else Fieldbook.Card,
                corner = MARK_SIZE / 2,
                depth = MARK_DEPTH,
                edge = if (selected) null else Fieldbook.Mark,
            ),
    ) {
        if (selected) Box(Modifier.size(8.dp).background(Pop.Letter, CircleShape))
    }
}

/** チェックの印。入っているときは青緑の角の丸いブロックに白いチェック、入っていないときは紙の色に灰色の縁 */
@Composable
internal fun PopCheck(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = MARK_SIZE, height = MARK_SIZE + MARK_DEPTH)
            .popBlock(
                face = if (checked) Pop.TealDeep else Fieldbook.Card,
                corner = 6.dp,
                depth = MARK_DEPTH,
                edge = if (checked) null else Fieldbook.Mark,
            ),
    ) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Pop.Letter, modifier = Modifier.size(16.dp))
    }
}

/** スライダーのつまみ：青緑の丸いブロック（押せないときは灰色）。Slider の thumb に渡す */
@Composable
internal fun PopSliderThumb(enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .size(width = SLIDER_THUMB_SIZE, height = SLIDER_THUMB_SIZE + KNOB_DEPTH)
            .popBlock(face = if (enabled) Pop.TealDeep else Fieldbook.Mark, corner = SLIDER_THUMB_SIZE / 2, depth = KNOB_DEPTH),
    )
}

/** スライダーのつまみの大きさ（目盛りの名前をつまみのまん中に合わせるのに、使う側も読む） */
internal val SLIDER_THUMB_SIZE = 24.dp

private val SWITCH_WIDTH = 50.dp
private val SWITCH_HEIGHT = 30.dp
private val SWITCH_DEPTH = 3.dp
private val KNOB_SIZE = 22.dp
private val KNOB_DEPTH = 2.dp
private val KNOB_INSET = 4.dp
private val MARK_SIZE = 24.dp
private val MARK_DEPTH = 2.dp
