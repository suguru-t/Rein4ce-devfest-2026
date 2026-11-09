#!/usr/bin/env python3
"""
アプリの BGM（app/src/main/res/raw/bgm*.ogg）を作る。設定で選べる 3 曲（決定 48）：

- bgm.ogg「いつもの」：歩く速さくらい（88 BPM）のゆったりした曲。はじめからある曲で、音は前と同じ
- bgm_light.ogg「軽やか」：速め（112 BPM）。低い音が 1・3 拍、和音が 2・4 拍で弾み、旋律も細かく動く
- bgm_night.ogg「夜」：ゆっくり（66 BPM）。イ短調の響き（ハ長調と同じ音だけ）で、和音を小さく分散して鳴らし、響きを多めに

どれも積み木らしい木琴（マリンバ）の音で 16 小節ぶん鳴らし、最後から最初へつなぎ目なく戻る 1 つのループにする
（響きの尾も、曲の頭に回して重ねる）。3 曲とも効果音（tools/make_sfx.py）と同じハ長調の音だけを使うので、重なっても濁らない。
音はこのスクリプトの中で作る（音の素材を使わないので、ライセンスの心配がない）。

使い方（リポジトリのいちばん上で）:
    python3 tools/make_bgm.py

要るもの: Python 3 と numpy・soundfile（pip install numpy soundfile）。乱数の種を決めてあるので、何度作っても同じ音になる
（ファイルの中身は、OGG の通し番号が毎回変わるので少しだけ違う）。

OGG（Vorbis）にするのは、ファイルを小さくするため（WAV だと 1.9 MB、OGG だと 0.16 MB。res/raw は APK の中で圧縮されない）。
Vorbis は長さを 1 点単位で覚えているので、MP3・AAC と違って頭と終わりに無音が足されず、ループのつなぎ目がずれない。

チームで用意した曲に差し替えるときは、res/raw/bgm*.ogg を消して、同じ名前（bgm.ogg・bgm.mp3 など、
Android の MediaPlayer が読める形式）で置けばよい。アプリはファイルの名前（R.raw.bgm・bgm_light・bgm_night）でしか見ていない。
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

# ---- 「いつもの」（88 BPM） ----

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


# ---- 「軽やか」（112 BPM） ----

# よく聞く明るい進行（ド・ソ・ラ・ファ）。最後の小節は前半 F・後半 G で、頭の C へ戻る
LIGHT_HARMONY = [
    ["C"], ["G"], ["Am"], ["F"], ["C"], ["G"], ["F"], ["G"],
    ["Am"], ["Em"], ["F"], ["C"], ["Dm"], ["G"], ["C"], ["F", "G"],
]

# 8 分音符で弾む旋律（高め）
LIGHT_MELODY = [
    [(0, "E5"), (0.5, "G5"), (1, "C6"), (2, "G5"), (2.5, "E5"), (3, "G5")],
    [(0, "D5"), (0.5, "G5"), (1, "B5"), (2, "D6"), (3, "B5")],
    [(0, "C6"), (0.5, "B5"), (1, "A5"), (2, "E5"), (3, "A5")],
    [(0, "A5"), (0.5, "G5"), (1, "F5"), (2, "C5"), (3, "F5"), (3.5, "G5")],
    [(0, "E5"), (0.5, "G5"), (1, "C6"), (2, "E6"), (2.5, "D6"), (3, "C6")],
    [(0, "B5"), (1, "G5"), (1.5, "A5"), (2, "B5"), (3, "D6")],
    [(0, "C6"), (0.5, "A5"), (1, "F5"), (2, "A5"), (3, "C6")],
    [(0, "B5"), (1, "A5"), (2, "G5"), (3, "D5")],
    [(0, "E5"), (0.5, "A5"), (1, "C6"), (2, "B5"), (3, "A5")],
    [(0, "G5"), (0.5, "B5"), (1, "E6"), (2, "D6"), (3, "B5")],
    [(0, "A5"), (0.5, "C6"), (1, "F6"), (2, "E6"), (3, "C6")],
    [(0, "G5"), (1, "E5"), (1.5, "G5"), (2, "C6")],
    [(0, "D6"), (0.5, "C6"), (1, "A5"), (2, "F5"), (3, "A5")],
    [(0, "B5"), (0.5, "D6"), (1, "G5"), (2, "B5"), (3, "D6")],
    [(0, "C6"), (1, "G5"), (2, "E5"), (3, "G5")],
    [(0, "A5"), (1, "F5"), (2, "G5"), (2.5, "B5"), (3, "D6")],
]

# ---- 「夜」（66 BPM） ----

# イ短調の響き（ハ長調と同じ音だけ。効果音と重なっても濁らない）。最後の小節は前半 Dm・後半 Em で、頭の Am へ戻る
NIGHT_HARMONY = [
    ["Am"], ["F"], ["C"], ["G"], ["Am"], ["F"], ["Dm"], ["Em"],
    ["F"], ["C"], ["Dm"], ["Am"], ["F"], ["G"], ["Am"], ["Dm", "Em"],
]

# 音の少ない、長くのばす旋律（中くらいの高さ）
NIGHT_MELODY = [
    [(0, "E5"), (2, "A5")],
    [(0, "A5"), (1.5, "G5"), (2, "F5")],
    [(0, "E5"), (2, "G5")],
    [(0, "D5"), (2, "G5")],
    [(0, "C6"), (2, "B5"), (3, "A5")],
    [(0, "A5"), (2, "C6")],
    [(0, "F5"), (1.5, "E5"), (2, "D5")],
    [(0, "E5"), (2, "G5"), (3, "B5")],
    [(0, "A5"), (2, "F5")],
    [(0, "G5"), (1.5, "E5"), (2, "C5")],
    [(0, "D5"), (2, "F5"), (3, "A5")],
    [(0, "E5"), (2, "C5")],
    [(0, "C6"), (1.5, "A5"), (2, "F5")],
    [(0, "G5"), (2, "D5")],
    [(0, "C5"), (1, "E5"), (2, "A5")],
    [(0, "F5"), (1, "D5"), (2, "E5")],
]


def walk_accompaniment(out, bar_start, beat, chords):
    """「いつもの」の伴奏：低い音が歩く足どりのように、和音の始めに根音・2 拍あとに 5 度。裏拍に 3 度と 5 度を小さく"""
    span = 4 / len(chords)
    for k, name in enumerate(chords):
        root, third, fifth = CHORDS[name]
        beat0 = k * span
        r = nearest_at_or_above(root, midi("E2"))
        out.append((bar_start + beat0 * beat, r, 0.42))
        if span >= 4:
            out.append((bar_start + (beat0 + 2) * beat, nearest_at_or_above(fifth, r + 1), 0.30))
        for off in np.arange(0.5, span, 1.0):
            for letter in (third, fifth):
                out.append((bar_start + (beat0 + off) * beat, nearest_at_or_above(letter, midi("E4")), 0.13))


def bounce_accompaniment(out, bar_start, beat, chords):
    """「軽やか」の伴奏：低い音が 1・3 拍（根音と 5 度）、和音（3 つの音）が 2・4 拍で弾む"""
    span = 4 / len(chords)
    for k, name in enumerate(chords):
        root, third, fifth = CHORDS[name]
        beat0 = k * span
        r = nearest_at_or_above(root, midi("E2"))
        out.append((bar_start + beat0 * beat, r, 0.40))
        if span >= 4:
            out.append((bar_start + (beat0 + 2) * beat, nearest_at_or_above(fifth, r + 1), 0.32))
        for off in np.arange(1.0, span, 2.0):
            for letter in (root, third, fifth):
                out.append((bar_start + (beat0 + off) * beat, nearest_at_or_above(letter, midi("E4")), 0.12))


def arpeggio_accompaniment(out, bar_start, beat, chords):
    """「夜」の伴奏：和音の始めに低い根音、そのあと 8 分音符で 5 度・3 度・根音を上下に小さく分散する"""
    span = 4 / len(chords)
    for k, name in enumerate(chords):
        root, third, fifth = CHORDS[name]
        beat0 = k * span
        r = nearest_at_or_above(root, midi("E2"))
        out.append((bar_start + beat0 * beat, r, 0.32))
        f = nearest_at_or_above(fifth, r + 1)
        t = nearest_at_or_above(third, f + 1)
        top = nearest_at_or_above(root, t + 1)
        pattern = [f, t, top, t, f, t, top, t]
        for i, note in enumerate(pattern[: int(span * 2) - 1]):
            out.append((bar_start + (beat0 + 0.5 * (i + 1)) * beat, note, 0.12))


@dataclass
class Song:
    """1 曲ぶんの決まり"""
    file: str  # res/raw に置く名前
    bpm: float
    harmony: list
    melody: list
    accompaniment: Callable
    melody_velocity: float
    seed: int
    room_s: float = 0.35  # 部屋の響きが消える速さ [s]
    wet: float = 0.22  # 響きの量

    @property
    def beat(self) -> float:
        return 60.0 / self.bpm

    @property
    def loop_samples(self) -> int:
        return round(BARS * 4 * self.beat * SR)


SONGS = [
    Song("bgm.ogg", 88, USUAL_HARMONY, USUAL_MELODY, walk_accompaniment, 0.50, seed=20261108),
    Song("bgm_light.ogg", 112, LIGHT_HARMONY, LIGHT_MELODY, bounce_accompaniment, 0.45, seed=20261109),
    Song("bgm_night.ogg", 66, NIGHT_HARMONY, NIGHT_MELODY, arpeggio_accompaniment, 0.38, seed=20261110, room_s=0.5, wet=0.32),
]


def events(song: Song):
    """（鳴らす時刻 [s], MIDI 番号, 強さ）の並び"""
    out = []
    for bar in range(BARS):
        bar_start = bar * 4 * song.beat
        song.accompaniment(out, bar_start, song.beat, song.harmony[bar])
        for beat, note in song.melody[bar]:
            out.append((bar_start + beat * song.beat, midi(note), song.melody_velocity))
    return out


def render(song: Song) -> np.ndarray:
    tail = 5 * SR
    buf = np.zeros(song.loop_samples + tail)
    for t, m, vel in events(song):
        # 手で叩いたように、時刻と強さを少しだけばらつかせる
        start = int(round((t + rng.normal(0, 0.004)) * SR))
        start = max(start, 0)
        tone = marimba(m, vel * rng.uniform(0.92, 1.05))
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


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for song in SONGS:
        use_seed(song.seed)
        music = reverb(render(song), song.room_s, song.wet)
        music *= 0.89 / np.max(np.abs(music))  # いちばん大きいところを -1 dB に
        pcm = np.round(music * 32767).astype("<i2")
        out = OUT_DIR / song.file
        # soundfile の compression_level は「どれだけ削るか」なので、音質の逆
        sf.write(out, pcm, SR, format="OGG", subtype="VORBIS", compression_level=1 - QUALITY)
        print(f"{out}  {len(pcm) / SR:.1f} s  {out.stat().st_size / 1e6:.2f} MB")


if __name__ == "__main__":
    main()
