#!/usr/bin/env python3
"""
アプリの BGM（app/src/main/res/raw/bgm.ogg）を作る。

積み木らしい木琴（マリンバ）の音で、歩く速さくらいのゆったりした曲を 16 小節ぶん鳴らし、
最後から最初へつなぎ目なく戻る 1 つのループにする（響きの尾も、曲の頭に回して重ねる）。
音はこのスクリプトの中で作る（音の素材を使わないので、ライセンスの心配がない）。

使い方（リポジトリのいちばん上で）:
    python3 tools/make_bgm.py

要るもの: Python 3 と numpy・soundfile（pip install numpy soundfile）。乱数の種を決めてあるので、何度作っても同じ音になる
（ファイルの中身は、OGG の通し番号が毎回変わるので少しだけ違う）。

OGG（Vorbis）にするのは、ファイルを小さくするため（WAV だと 1.9 MB、OGG だと 0.16 MB。res/raw は APK の中で圧縮されない）。
Vorbis は長さを 1 点単位で覚えているので、MP3・AAC と違って頭と終わりに無音が足されず、ループのつなぎ目がずれない。

チームで用意した曲に差し替えるときは、res/raw/bgm.ogg を消して、同じ名前（bgm.ogg・bgm.mp3 など、
Android の MediaPlayer が読める形式）で置けばよい。アプリはファイルの名前（R.raw.bgm）でしか見ていない。
曲の規約（リポジトリが公開なので、ファイルを置いてよいか・クレジットの書き方）は確かめてから入れる。
"""

from pathlib import Path

import numpy as np
import soundfile as sf

SR = 22050  # 1 秒の点の数（マリンバの音は 4 kHz くらいまでなので、これで足りる。ファイルを小さくする）
BPM = 88  # 歩く速さくらい
BEAT = 60.0 / BPM
BARS = 16
LOOP_SAMPLES = round(BARS * 4 * BEAT * SR)

OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/raw/bgm.ogg"
QUALITY = 0.5  # Vorbis の音質（0〜1）。0.5 で 1 秒あたり 30 kbit ほど。木琴の音なら、聞いて違いが分からない

rng = np.random.default_rng(20261108)

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

# 1 小節ずつの和音（最後の小節は前半 F・後半 G で、頭の C へ戻る）
HARMONY = [
    ["C"], ["Am"], ["F"], ["G"], ["C"], ["Am"], ["Dm"], ["G"],
    ["F"], ["G"], ["Em"], ["Am"], ["Dm"], ["G"], ["C"], ["F", "G"],
]

# 旋律：小節ごとに（拍, 音）。拍は小節の頭から 0〜4
MELODY = [
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


def events():
    """（鳴らす時刻 [s], MIDI 番号, 強さ）の並び"""
    out = []
    for bar in range(BARS):
        bar_start = bar * 4 * BEAT
        chords = HARMONY[bar]
        span = 4 / len(chords)
        for k, name in enumerate(chords):
            root, third, fifth = CHORDS[name]
            beat0 = k * span
            # ベース（低い音）：和音の始めに根音、2 拍あとに 5 度（歩く足どりのように）
            r = nearest_at_or_above(root, midi("E2"))
            out.append((bar_start + beat0 * BEAT, r, 0.42))
            if span >= 4:
                out.append((bar_start + (beat0 + 2) * BEAT, nearest_at_or_above(fifth, r + 1), 0.30))
            # 裏拍に、3 度と 5 度の重なりを小さく（軽く跳ねる感じ）
            for off in np.arange(0.5, span, 1.0):
                for letter in (third, fifth):
                    out.append((bar_start + (beat0 + off) * BEAT, nearest_at_or_above(letter, midi("E4")), 0.13))
        for beat, note in MELODY[bar]:
            out.append((bar_start + beat * BEAT, midi(note), 0.50))
    return out


def render() -> np.ndarray:
    tail = 5 * SR
    buf = np.zeros(LOOP_SAMPLES + tail)
    for t, m, vel in events():
        # 手で叩いたように、時刻と強さを少しだけばらつかせる
        start = int(round((t + rng.normal(0, 0.004)) * SR))
        start = max(start, 0)
        tone = marimba(m, vel * rng.uniform(0.92, 1.05))
        buf[start:start + len(tone)] += tone[: len(buf) - start]
    # ループの終わりからはみ出た響きは、頭に重ねる（つなぎ目で響きが切れないように）
    loop = buf[:LOOP_SAMPLES].copy()
    loop[:tail] += buf[LOOP_SAMPLES:]
    return loop


def reverb(dry: np.ndarray) -> np.ndarray:
    """部屋の響き。ループなので、響きの尾も頭へ回る（円の畳み込み）"""
    n = int(1.6 * SR)
    t = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-t / 0.35)
    ir = np.convolve(ir, np.ones(6) / 6, mode="same")  # 高い音を少し丸めて、やわらかく
    ir[: int(0.015 * SR)] = 0  # 直接の音から少し遅れて響く
    ir /= np.sqrt(np.sum(ir ** 2))
    padded = np.zeros(len(dry))
    padded[:n] = ir
    wet = np.real(np.fft.ifft(np.fft.fft(dry) * np.fft.fft(padded)))
    return dry + 0.22 * wet


def main():
    music = reverb(render())
    music *= 0.89 / np.max(np.abs(music))  # いちばん大きいところを -1 dB に
    pcm = np.round(music * 32767).astype("<i2")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    # soundfile の compression_level は「どれだけ削るか」なので、音質の逆
    sf.write(OUT, pcm, SR, format="OGG", subtype="VORBIS", compression_level=1 - QUALITY)
    print(f"{OUT}  {len(pcm) / SR:.1f} s  {OUT.stat().st_size / 1e6:.2f} MB")


if __name__ == "__main__":
    main()
