#!/usr/bin/env python3
"""
アプリの BGM（app/src/main/res/raw/bgm*.ogg）を作る。設定で選べる 3 曲（決定 48）：

- bgm.ogg「トコトコ」：木琴で、歩く速さくらい（88 BPM）のゆったりした曲。はじめからある曲（はじめは「いつもの」と呼んでいた）で、音は前と同じ
- bgm_chip.ogg「ピコピコ」：昔のゲーム機のような音（矩形波・三角波・雑音の太鼓）。140 BPM で、速い分散和音と太鼓で走る、冒険に出るような曲
- bgm_ukulele.ogg「ウクレレ」：ウクレレをジャカジャカ刻み、口笛が旋律を吹く。112 BPM で、晴れた日に外を歩くような曲
  （3 曲目は、オルゴールを「歯医者で流れていそう」でやめ、ウクレレとアコースティックギターを聞き比べて B がウクレレに決めた）

どれも 16 小節ぶん鳴らし、最後から最初へつなぎ目なく戻る 1 つのループにする（響きの尾も、曲の頭に回して重ねる）。
3 曲とも効果音（tools/make_sfx.py）と同じハ長調の音だけを使うので、重なっても濁らない。ほかの 2 曲の大きさは「トコトコ」にそろえる。
音はこのスクリプトの中で作る（音の素材を使わないので、ライセンスの心配がない）。

使い方（リポジトリのいちばん上で）:
    python3 tools/make_bgm.py

要るもの: Python 3 と numpy・soundfile（pip install numpy soundfile）。乱数の種を決めてあるので、何度作っても同じ音になる
（ファイルの中身は、OGG の通し番号が毎回変わるので少しだけ違う）。

OGG（Vorbis）にするのは、ファイルを小さくするため（WAV だと 1.9 MB、OGG だと 0.16 MB。res/raw は APK の中で圧縮されない）。
Vorbis は長さを 1 点単位で覚えているので、MP3・AAC と違って頭と終わりに無音が足されず、ループのつなぎ目がずれない。

チームで用意した曲に差し替えるときは、res/raw/bgm*.ogg を消して、同じ名前（bgm.ogg・bgm.mp3 など、
Android の MediaPlayer が読める形式）で置けばよい。アプリはファイルの名前（R.raw.bgm・bgm_chip・bgm_ukulele）でしか見ていない。
曲の規約（リポジトリが公開なので、ファイルを置いてよいか・クレジットの書き方）は確かめてから入れる。
"""

from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import numpy as np
import soundfile as sf

SR = 22050  # 1 秒の点の数（マリンバの音は 4 kHz くらいまでなので、これで足りる。ファイルを小さくする）
BARS = 16

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/res/raw"
QUALITY = 0.5  # Vorbis の音質（0〜1）。0.5 で 1 秒あたり 30 kbit ほど。木琴の音なら、聞いて違いが分からない

# 乱数（打つ時刻と強さのばらつき・打った瞬間の雑音・部屋の響き）。曲ごとに種を決め直す（[use_seed]）。
# 効果音（make_sfx.py）は、この種のまま marimba を使う
rng = np.random.default_rng(20261108)


def use_seed(seed: int) -> None:
    """乱数の種を決め直す（曲ごとに、何度作っても同じ音になるように）"""
    global rng
    rng = np.random.default_rng(seed)

# ---- 音の高さ ----

NOTE_INDEX = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def midi(name: str) -> int:
    """'E5' や 'F#4' を MIDI の番号に"""
    letter, rest = name[0], name[1:]
    sharp = rest.startswith("#")
    octave = int(rest[1:] if sharp else rest)
    return 12 * (octave + 1) + NOTE_INDEX[letter] + (1 if sharp else 0)


def freq(m: int) -> float:
    return 440.0 * 2 ** ((m - 69) / 12)


# ---- マリンバの音 1 つ ----


def marimba(m: int, velocity: float) -> np.ndarray:
    """MIDI 番号 [m] のマリンバの音（打って、自然に消えていく）。低い音ほど長く響く"""
    f = freq(m)
    tau = 0.55 * (261.6 / f) ** 0.6  # 響きの長さ [s]（C4 で 0.55 秒）
    n = int(min(6 * tau, 4.0) * SR)
    t = np.arange(n) / SR
    tone = np.sin(2 * np.pi * f * t) * np.exp(-t / tau)
    # 木の音板らしさ：基音のおよそ 4 倍・10 倍の倍音を、早く消えるように少しだけ
    for ratio, amp, life in ((3.93, 0.30, 0.30), (9.8, 0.06, 0.10)):
        if ratio * f < 0.45 * SR:
            tone += amp * np.sin(2 * np.pi * ratio * f * t) * np.exp(-t / (tau * life))
    # 打った瞬間の「コツ」：ごく短い雑音を丸めて
    click_n = int(0.006 * SR)
    click = rng.standard_normal(click_n) * np.linspace(1, 0, click_n) ** 2
    click = np.convolve(click, np.ones(4) / 4, mode="same") * 0.12
    tone[:click_n] += click
    # 立ち上がりを 2 ms でならす（ぷつっと鳴らないように）
    attack = min(n, int(0.002 * SR))
    tone[:attack] *= np.linspace(0, 1, attack)
    return tone * velocity


# ---- 曲 ----

CHORDS = {
    "C": ("C", "E", "G"),
    "Am": ("A", "C", "E"),
    "F": ("F", "A", "C"),
    "G": ("G", "B", "D"),
    "Dm": ("D", "F", "A"),
    "Em": ("E", "G", "B"),
}

# ---- 「トコトコ」（88 BPM）：木琴。はじめからある曲 ----

# 1 小節ずつの和音（最後の小節は前半 F・後半 G で、頭の C へ戻る）
USUAL_HARMONY = [
    ["C"], ["Am"], ["F"], ["G"], ["C"], ["Am"], ["Dm"], ["G"],
    ["F"], ["G"], ["Em"], ["Am"], ["Dm"], ["G"], ["C"], ["F", "G"],
]

# 旋律：小節ごとに（拍, 音）。拍は小節の頭から 0〜4
USUAL_MELODY = [
    [(0, "G4"), (1, "C5"), (2, "E5"), (3.5, "D5")],
    [(0, "C5"), (1, "A4"), (2, "C5"), (3, "E5")],
    [(0, "F5"), (1.5, "E5"), (2, "C5"), (3, "A4")],
    [(0, "G4"), (1, "B4"), (2, "D5")],
    [(0, "G4"), (1, "C5"), (2, "E5"), (3, "G5")],
    [(0, "A5"), (1.5, "G5"), (2, "E5"), (3, "C5")],
    [(0, "D5"), (1, "F5"), (2, "A5"), (3, "F5")],
    [(0, "E5"), (1, "D5"), (2, "B4"), (3, "D5")],
    [(0, "C6"), (1, "A5"), (2, "F5"), (3, "A5")],
    [(0, "B5"), (1.5, "A5"), (2, "G5")],
    [(0, "G5"), (1, "E5"), (2, "B4"), (3, "E5")],
    [(0, "A5"), (1, "G5"), (1.5, "E5"), (2, "C5")],
    [(0, "D5"), (1, "F5"), (2, "A5"), (3, "C6")],
    [(0, "B5"), (1, "G5"), (2, "D5"), (3, "F5")],
    [(0, "E5"), (1, "G5"), (2, "C6")],
    [(0, "A5"), (1, "F5"), (2, "D5"), (3, "B4")],
]


def nearest_at_or_above(letter: str, low: int) -> int:
    """[letter] の音のうち、MIDI 番号 [low] 以上でいちばん低いもの"""
    m = midi(letter + "0")
    while m < low:
        m += 12
    return m


# ---- 楽器 ----
# どの楽器も (MIDI 番号, 強さ, 長さ [s]) → 音。木琴（marimba）は長さを使わず、自然に消える


def marimba_voice(m: int, velocity: float, length: float) -> np.ndarray:
    return marimba(m, velocity)


def square(m: int, velocity: float, length: float, duty: float = 0.5) -> np.ndarray:
    """昔のゲーム機の「ピコッ」とした音（矩形波）。耳に痛い折り返しが出ないよう、倍音を足して作る（足すのは聞こえる高さまで）"""
    f = freq(m)
    n = int((length + 0.03) * SR)
    t = np.arange(n) / SR
    tone = np.zeros(n)
    k = 1
    while k * f < 0.45 * SR and k <= 40:
        a = np.sin(np.pi * k * duty) / k
        if abs(a) > 1e-6:
            tone += a * np.sin(2 * np.pi * k * f * t)
        k += 1
    return tone * chip_envelope(n, length) * velocity


def triangle(m: int, velocity: float, length: float) -> np.ndarray:
    """ゲーム機の低い音（三角波。やわらかい）"""
    f = freq(m)
    n = int((length + 0.03) * SR)
    t = np.arange(n) / SR
    tone = np.zeros(n)
    for i, k in enumerate(range(1, 30, 2)):
        if k * f >= 0.45 * SR:
            break
        tone += ((-1) ** i) / (k * k) * np.sin(2 * np.pi * k * f * t)
    return tone * chip_envelope(n, length) * velocity


def chip_envelope(n: int, length: float) -> np.ndarray:
    """ゲーム機の音の大きさの形：すぐ立ち上がり、少し下がって続き、長さのところで 30 ms かけて消える"""
    t = np.arange(n) / SR
    env = 0.7 + 0.3 * np.exp(-t / 0.05)
    env[: int(0.003 * SR)] *= np.linspace(0, 1, int(0.003 * SR))
    release = t > length
    env[release] *= np.exp(-(t[release] - length) / 0.01)
    return env


KICK, SNARE, HAT = -1, -2, -3  # 太鼓は MIDI 番号の代わりにこれで選ぶ


def drum(kind: int, velocity: float, length: float) -> np.ndarray:
    """ゲーム機の太鼓：低い「ドン」（下がっていく音）、雑音の「タン」、短く高い雑音の「チッ」"""
    if kind == KICK:
        n = int(0.15 * SR)
        t = np.arange(n) / SR
        pitch = 50 + 110 * np.exp(-t / 0.03)
        tone = np.sin(2 * np.pi * np.cumsum(pitch) / SR) * np.exp(-t / 0.06)
    elif kind == SNARE:
        n = int(0.15 * SR)
        t = np.arange(n) / SR
        tone = rng.standard_normal(n) * np.exp(-t / 0.045) * 0.6 + np.sin(2 * np.pi * 190 * t) * np.exp(-t / 0.03) * 0.4
    else:
        n = int(0.04 * SR)
        t = np.arange(n) / SR
        noise = rng.standard_normal(n)
        noise -= np.convolve(noise, np.ones(4) / 4, mode="same")  # 低いところを削って、高い「チッ」だけにする
        tone = noise * np.exp(-t / 0.01) * 0.5
    return tone * velocity


PLUCK_OVERSAMPLE = 4  # 弦の音を作るときの点の細かさ（何倍か）


def pluck(m: int, velocity: float, length: float, decay_s: float = 0.8, softness: int = 1) -> np.ndarray:
    """弦をはじいた音（Karplus-Strong：雑音を 1 周期ぶんの長さで回しながら、少しずつならして消していく）。
    [decay_s] は響きが 1/e になるまでの時間 [s]。[softness] が大きいほど、はじいた瞬間の雑音を丸めて、やわらかい音にする"""
    f = freq(m)
    # 1 周の長さは点の数（整数）なので、そのままだと高い音ほど音程がずれる（E4 で 23 セント）。
    # 4 倍細かい点で作ってから戻し、ずれを 4 分の 1 にする
    sr = SR * PLUCK_OVERSAMPLE
    period = max(2, int(round(sr / f - 0.5)))  # ならすと半周期ぶん遅れるので、その分だけ短くする
    n = int(min(5 * decay_s, 3.0) * SR) * PLUCK_OVERSAMPLE
    gain = np.exp(-period / (sr * decay_s))  # 1 周ごとの小さくなる割合
    burst = rng.uniform(-1, 1, period)
    width = softness * PLUCK_OVERSAMPLE  # 細かい点で作るぶん、ならす幅も広げて音の明るさをそろえる
    burst = np.convolve(burst, np.ones(width) / width, mode="same")
    burst -= burst.mean()
    y = np.zeros(n + period + 1)
    y[1:period + 1] = burst
    for i in range(period + 1, len(y), period):
        j = min(i + period, len(y))
        y[i:j] = 0.5 * gain * (y[i - period:j - period] + y[i - period - 1:j - period - 1])
    out = y[1:n + 1].reshape(-1, PLUCK_OVERSAMPLE).mean(axis=1)
    attack = int(0.002 * SR)
    out[:attack] *= np.linspace(0, 1, attack)
    return out / (np.max(np.abs(out)) + 1e-9) * velocity


def whistle(m: int, velocity: float, length: float) -> np.ndarray:
    """口笛：ほぼ純音で、出だしは少し下からすくい上げ、のばすとゆるいビブラート。息の音を少しだけ混ぜる"""
    f = freq(m)
    n = int((length + 0.08) * SR)
    t = np.arange(n) / SR
    scoop = 1 - 0.02 * np.exp(-t / 0.03)  # 出だしの 30 ms で、少し下から正しい高さへ
    vibrato = 1 + 0.006 * np.clip((t - 0.15) / 0.2, 0, 1) * np.sin(2 * np.pi * 5.5 * t)
    phase = 2 * np.pi * np.cumsum(f * scoop * vibrato) / SR
    tone = np.sin(phase) + 0.03 * np.sin(2 * phase)
    breath = rng.standard_normal(n)
    breath = np.convolve(breath, np.ones(3) / 3, mode="same") * 0.04
    env = np.ones(n)
    attack = int(0.04 * SR)
    env[:attack] = np.linspace(0, 1, attack)
    release = t > length
    env[release] *= np.exp(-(t[release] - length) / 0.025)
    return (tone + breath) * env * velocity


def upright_bass(m: int, velocity: float, length: float) -> np.ndarray:
    """やわらかい低い弦（ウッドベースのような、はじいてすぐ丸く消える音）"""
    f = freq(m)
    n = int(0.9 * SR)
    t = np.arange(n) / SR
    tone = (np.sin(2 * np.pi * f * t) + 0.35 * np.sin(4 * np.pi * f * t) * np.exp(-t / 0.08)) * np.exp(-t / 0.35)
    attack = int(0.004 * SR)
    tone[:attack] *= np.linspace(0, 1, attack)
    return tone * velocity


SHAKER, CLAP = -4, -5  # 小さな打楽器は MIDI 番号の代わりにこれで選ぶ


def hand_percussion(kind: int, velocity: float, length: float) -> np.ndarray:
    """シェイカー（シャッ）・手拍子（パン）"""
    if kind == SHAKER:
        n = int(0.06 * SR)
        t = np.arange(n) / SR
        noise = rng.standard_normal(n)
        noise -= np.convolve(noise, np.ones(3) / 3, mode="same")
        env = np.minimum(t / 0.01, 1) * np.exp(-t / 0.02)
        return noise * env * 0.5 * velocity
    # 手拍子は、何人かの手が少しずつずれて鳴る（10 ms ずつ 3 回）
    n = int(0.15 * SR)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    noise = noise - np.convolve(noise, np.ones(6) / 6, mode="same")
    env = np.zeros(n)
    for k, d in enumerate((0.0, 0.01, 0.02)):
        on = t >= d
        env[on] += np.exp(-(t[on] - d) / (0.012 if k < 2 else 0.04))
    return noise * env * 0.35 * velocity


# ---- 「トコトコ」の並べ方（はじめからの曲。音を変えないよう、前と同じ順に並べる） ----


def arrange_usual(song) -> list:
    out = []
    beat = song.beat
    for bar in range(BARS):
        bar_start = bar * 4 * beat
        chords = USUAL_HARMONY[bar]
        span = 4 / len(chords)
        for k, name in enumerate(chords):
            root, third, fifth = CHORDS[name]
            beat0 = k * span
            # ベース（低い音）：和音の始めに根音、2 拍あとに 5 度（歩く足どりのように）
            r = nearest_at_or_above(root, midi("E2"))
            out.append((bar_start + beat0 * beat, r, 0.42, marimba_voice, 0.0))
            if span >= 4:
                out.append((bar_start + (beat0 + 2) * beat, nearest_at_or_above(fifth, r + 1), 0.30, marimba_voice, 0.0))
            # 裏拍に、3 度と 5 度の重なりを小さく（軽く跳ねる感じ）
            for off in np.arange(0.5, span, 1.0):
                for letter in (third, fifth):
                    out.append((bar_start + (beat0 + off) * beat, nearest_at_or_above(letter, midi("E4")), 0.13, marimba_voice, 0.0))
        for b, note in USUAL_MELODY[bar]:
            out.append((bar_start + b * beat, midi(note), 0.50, marimba_voice, 0.0))
    return out


# ---- 「ピコピコ」（140 BPM）：昔のゲーム機のような音。速い分散和音と太鼓で、冒険に出るような曲 ----

CHIP_HARMONY = [
    ["C"], ["Am"], ["F"], ["G"], ["C"], ["Am"], ["F"], ["G"],
    ["F"], ["G"], ["Em"], ["Am"], ["Dm"], ["G"], ["C"], ["F", "G"],
]

CHIP_MELODY = [
    [(0, "E5"), (0.5, "G5"), (1, "C6"), (2, "E6"), (3, "D6"), (3.5, "C6")],
    [(0, "A5"), (1, "E5"), (1.5, "A5"), (2, "C6"), (3, "B5")],
    [(0, "C6"), (0.5, "A5"), (1, "F5"), (2, "A5"), (2.5, "C6"), (3, "F6")],
    [(0, "D6"), (1, "B5"), (2, "G5"), (3, "A5"), (3.5, "B5")],
    [(0, "C6"), (0.5, "E6"), (1, "G6"), (2, "E6"), (3, "C6")],
    [(0, "A5"), (0.5, "C6"), (1, "E6"), (2, "D6"), (3, "C6"), (3.5, "B5")],
    [(0, "A5"), (1, "C6"), (2, "F6"), (3, "E6")],
    [(0, "D6"), (0.5, "C6"), (1, "B5"), (2, "G5"), (3, "D6")],
    [(0, "F5"), (0.5, "A5"), (1, "C6"), (1.5, "A5"), (2, "F6"), (3, "C6")],
    [(0, "G5"), (0.5, "B5"), (1, "D6"), (1.5, "B5"), (2, "G6"), (3, "D6")],
    [(0, "E6"), (1, "B5"), (2, "G5"), (3, "B5")],
    [(0, "C6"), (1, "A5"), (2, "E5"), (2.5, "A5"), (3, "C6")],
    [(0, "D6"), (0.5, "F6"), (1, "A6"), (2, "F6"), (3, "D6")],
    [(0, "B5"), (0.5, "D6"), (1, "G6"), (2, "F6"), (3, "D6"), (3.5, "B5")],
    [(0, "C6"), (1, "G5"), (2, "E5"), (3, "G5")],
    [(0, "A5"), (1, "C6"), (2, "B5"), (2.5, "D6"), (3, "G6")],
]


def arrange_chip(song) -> list:
    out = []
    beat = song.beat
    for bar in range(BARS):
        bar_start = bar * 4 * beat
        chords = CHIP_HARMONY[bar]
        span = 4 / len(chords)
        for k, name in enumerate(chords):
            root, third, fifth = CHORDS[name]
            beat0 = k * span
            r = nearest_at_or_above(root, midi("C3"))
            # 低い音：8 分音符で根音と 1 オクターブ上を行き来する（ドンドンと走る）
            for i in range(int(span * 2)):
                out.append((bar_start + (beat0 + 0.5 * i) * beat, r + (12 if i % 2 else 0), 0.55, triangle, 0.45 * beat))
            # 16 分音符の速い分散和音（ピロピロ）。小さく、細い音で
            arp = [nearest_at_or_above(x, midi("C5")) for x in (root, third, fifth)]
            arp.append(arp[0] + 12)
            for i in range(int(span * 4)):
                out.append((bar_start + (beat0 + 0.25 * i) * beat, arp[i % 4], 0.10, lambda m, v, d: square(m, v, d, duty=0.25), 0.2 * beat))
        # 太鼓：1・3 拍に「ドン」、2・4 拍に「タン」、8 分音符で「チッ」
        for b in (0, 2, 2.5):
            out.append((bar_start + b * beat, KICK, 0.9, drum, 0.0))
        for b in (1, 3):
            out.append((bar_start + b * beat, SNARE, 0.5, drum, 0.0))
        for i in range(8):
            out.append((bar_start + 0.5 * i * beat, HAT, 0.25, drum, 0.0))
        # 旋律：次の音の手前まで伸ばす
        notes = CHIP_MELODY[bar]
        for i, (b, note) in enumerate(notes):
            end = notes[i + 1][0] if i + 1 < len(notes) else 4
            out.append((bar_start + b * beat, midi(note), 0.28, square, (end - b) * beat * 0.85))
    return out


# ---- 「ウクレレ」（112 BPM）：ウクレレをジャカジャカ刻み、口笛が旋律を吹く。晴れた日に外を歩くような曲 ----

UKULELE_HARMONY = [
    ["C"], ["F"], ["G"], ["C"], ["Am"], ["F"], ["G"], ["G"],
    ["C"], ["Em"], ["F"], ["C"], ["Dm"], ["G"], ["C"], ["F", "G"],
]

# ウクレレの押さえ方（4 弦から 1 弦へ：G・C・E・A の弦。いちばん上の弦がいちばん低くない「リエントラント」）
UKULELE_CHORDS = {
    "C": ("G4", "C4", "E4", "C5"),
    "F": ("A4", "C4", "F4", "A4"),
    "G": ("G4", "D4", "G4", "B4"),
    "Am": ("A4", "C4", "E4", "A4"),
    "Em": ("G4", "E4", "G4", "B4"),
    "Dm": ("A4", "D4", "F4", "A4"),
}

# 口笛の旋律（書いた高さの 1 オクターブ上で吹く）
UKULELE_MELODY = [
    [(0, "G5"), (1, "E5"), (1.5, "F5"), (2, "G5"), (3, "C6")],
    [(0, "A5"), (1, "F5"), (2, "A5"), (2.5, "G5"), (3, "F5")],
    [(0, "D5"), (1, "G5"), (2, "B5"), (3, "A5")],
    [(0, "G5"), (2, "E5")],
    [(0, "E5"), (1, "A5"), (1.5, "B5"), (2, "C6"), (3, "A5")],
    [(0, "C6"), (1, "A5"), (2, "F5"), (3, "A5")],
    [(0, "G5"), (1, "B5"), (2, "D6"), (2.5, "C6"), (3, "B5")],
    [(0, "G5"), (2, "D5")],
    [(0, "E5"), (0.5, "G5"), (1, "C6"), (2, "E6"), (3, "D6")],
    [(0, "B5"), (1, "G5"), (2, "E5"), (3, "G5")],
    [(0, "A5"), (1, "C6"), (2, "F6"), (3, "E6")],
    [(0, "C6"), (2, "G5")],
    [(0, "F5"), (1, "A5"), (2, "D6"), (3, "C6")],
    [(0, "B5"), (1, "D6"), (2, "G5"), (3, "A5"), (3.5, "B5")],
    [(0, "C6"), (1, "G5"), (2, "E5"), (3, "G5")],
    [(0, "A5"), (1, "F5"), (2, "G5"), (2.5, "A5"), (3, "B5")],
]

# 刻み方（1 小節 8 つの 8 分音符のうち、どこで振り下ろす D・振り上げる U か）：D・D U・U D U
UKULELE_STRUM = [(0, "D"), (1, "D"), (1.5, "U"), (2.5, "U"), (3, "D"), (3.5, "U")]


def strum(out, at, chord, beat, down):
    """1 回ぶん弦をかき鳴らす。振り下ろしは 4 弦から、振り上げは 1 弦から、少しずつずらして鳴らす（振り上げは少し弱く）"""
    notes = [midi(x) for x in UKULELE_CHORDS[chord]]
    order = notes if down else notes[::-1]
    for k, m in enumerate(order):
        out.append((at + k * 0.012, m, 0.16 if down else 0.11, lambda mm, v, d: pluck(mm, v, d, decay_s=0.45, softness=2), 0.0))


def arrange_ukulele(song) -> list:
    out = []
    beat = song.beat
    for bar in range(BARS):
        bar_start = bar * 4 * beat
        chords = UKULELE_HARMONY[bar]
        for b, way in UKULELE_STRUM:
            chord = chords[0] if len(chords) == 1 or b < 2 else chords[1]
            strum(out, bar_start + b * beat, chord, beat, down=(way == "D"))
        # 低い音：和音の頭に根音、3 拍目に 5 度
        for k, name in enumerate(chords):
            root, _, fifth = CHORDS[name]
            beat0 = k * (4 / len(chords))
            r = nearest_at_or_above(root, midi("E2"))
            out.append((bar_start + beat0 * beat, r, 0.5, upright_bass, 0.0))
            if len(chords) == 1:
                out.append((bar_start + 2 * beat, nearest_at_or_above(fifth, r + 1), 0.4, upright_bass, 0.0))
        # シェイカーは 8 分音符（裏を少し強く）、手拍子は 2・4 拍に小さく
        for i in range(8):
            out.append((bar_start + 0.5 * i * beat, SHAKER, 0.35 if i % 2 else 0.2, hand_percussion, 0.0))
        for b in (1, 3):
            out.append((bar_start + b * beat, CLAP, 0.3, hand_percussion, 0.0))
        notes = UKULELE_MELODY[bar]
        for i, (b, note) in enumerate(notes):
            end = notes[i + 1][0] if i + 1 < len(notes) else 4
            out.append((bar_start + b * beat, midi(note) + 12, 0.30, whistle, (end - b) * beat * 0.9))
    return out


@dataclass
class Song:
    """1 曲ぶんの決まり"""
    file: str  # res/raw に置く名前
    bpm: float
    beats_per_bar: int
    arrange: Callable  # (Song) → [(時刻 [s], MIDI 番号, 強さ, 楽器, 長さ [s])]
    seed: int
    jitter_s: float = 0.004  # 手で叩いたような時刻のばらつき（ゲーム機の音は 0）
    room_s: float = 0.35  # 部屋の響きが消える速さ [s]
    wet: float = 0.22  # 響きの量

    @property
    def beat(self) -> float:
        return 60.0 / self.bpm

    @property
    def loop_samples(self) -> int:
        return round(BARS * self.beats_per_bar * self.beat * SR)


SONGS = [
    Song("bgm.ogg", 88, 4, arrange_usual, seed=20261108),
    Song("bgm_chip.ogg", 140, 4, arrange_chip, seed=20261109, jitter_s=0.0, room_s=0.2, wet=0.06),
    Song("bgm_ukulele.ogg", 112, 4, arrange_ukulele, seed=20261111, jitter_s=0.003, room_s=0.3, wet=0.15),
]


def render(song: Song) -> np.ndarray:
    tail = 5 * SR
    buf = np.zeros(song.loop_samples + tail)
    for t, m, vel, voice, length in song.arrange(song):
        # 手で叩いたように、時刻と強さを少しだけばらつかせる（ゲーム機の音は時刻をずらさない）
        start = int(round((t + (rng.normal(0, song.jitter_s) if song.jitter_s > 0 else 0.0)) * SR))
        start = max(start, 0)
        tone = voice(m, vel * rng.uniform(0.92, 1.05), length)
        buf[start:start + len(tone)] += tone[: len(buf) - start]
    # ループの終わりからはみ出た響きは、頭に重ねる（つなぎ目で響きが切れないように）
    loop = buf[:song.loop_samples].copy()
    loop[:tail] += buf[song.loop_samples:]
    return loop


def reverb(dry: np.ndarray, room_s: float = 0.35, wet: float = 0.22) -> np.ndarray:
    """部屋の響き。ループなので、響きの尾も頭へ回る（円の畳み込み）"""
    n = int(1.6 * SR)
    t = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-t / room_s)
    ir = np.convolve(ir, np.ones(6) / 6, mode="same")  # 高い音を少し丸めて、やわらかく
    ir[: int(0.015 * SR)] = 0  # 直接の音から少し遅れて響く
    ir /= np.sqrt(np.sum(ir ** 2))
    padded = np.zeros(len(dry))
    padded[:n] = ir
    wet_part = np.real(np.fft.ifft(np.fft.fft(dry) * np.fft.fft(padded)))
    return dry + wet * wet_part


PEAK = 0.89  # いちばん大きいところ（-1 dB）


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    reference_rms = None
    for song in SONGS:
        use_seed(song.seed)
        music = reverb(render(song), song.room_s, song.wet)
        if reference_rms is None:
            # 「トコトコ」は、いちばん大きいところを -1 dB に（前と同じ）。この大きさ（RMS）をほかの曲の目安にする
            music *= PEAK / np.max(np.abs(music))
            reference_rms = np.sqrt(np.mean(music ** 2))
        else:
            # ほかの曲は、「トコトコ」と同じくらいの大きさに聞こえるよう RMS をそろえる。
            # 弦をはじいた瞬間のような鋭い山があると、-1 dB に収めるために全体が小さくなってしまうので、
            # 大きいところだけを丸く抑えて（tanh。小さい音はそのまま）から、-1 dB に収める
            music *= reference_rms / np.sqrt(np.mean(music ** 2))
            if np.max(np.abs(music)) > PEAK:
                music = PEAK * np.tanh(music / PEAK)
            music *= min(1.0, PEAK / np.max(np.abs(music)))
        pcm = np.round(music * 32767).astype("<i2")
        out = OUT_DIR / song.file
        # soundfile の compression_level は「どれだけ削るか」なので、音質の逆
        sf.write(out, pcm, SR, format="OGG", subtype="VORBIS", compression_level=1 - QUALITY)
        print(f"{out}  {len(pcm) / SR:.1f} s  {out.stat().st_size / 1e6:.2f} MB")


if __name__ == "__main__":
    main()
