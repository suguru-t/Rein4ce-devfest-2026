#!/usr/bin/env python3
"""
アプリの効果音（app/src/main/res/raw/sfx_*.ogg）を作る。

BGM（tools/make_bgm.py）と同じ木琴（マリンバ）の音で作り、曲と同じハ長調の音にする（曲に重なっても濁らない）。
音はこのスクリプトの中で作る（音の素材を使わないので、ライセンスの心配がない）。

- sfx_press.ogg：ボタンを押したとき。高めの短い「ぽこっ」。いちばんよく鳴るので、いちばん小さく
- sfx_hop.ogg：題字・画面名の積み木が跳ねたとき。下から上へ跳ねる 2 つの音「ぴょん」。
  積み木ごとに高さを変えて鳴らす（音階を上がって最後の積み木が上のド。アプリが再生の速さで音の高さを変える。audio/SoundEffects.kt の BlockNotes）ので、
  基準の高さ（G5）で作っておく
- sfx_land.ogg：最後の積み木が落ちてきて着いたとき。軽い木の打音「コ・トッ」（押したときは、ほかの積み木と同じ「ぴょん」を鳴らしてある）。
  直前の「ぴょん」の響きと重なるので、音階の音（木琴）にはしない（ミ→ドで作ったら、最後の積み木のファと半音でぶつかって濁った）
- sfx_done.ogg：記録を止めて道を 1 本集めたとき。ド・ミ・ソ・ドと駆け上がる短いごほうび

使い方（リポジトリのいちばん上で）:
    python3 tools/make_sfx.py

要るもの: Python 3 と numpy・soundfile（pip install numpy soundfile）。make_bgm.py の音（marimba）を使う。
"""

import sys
from pathlib import Path

import numpy as np
import soundfile as sf

sys.dont_write_bytecode = True  # make_bgm を読み込んでも tools/__pycache__ を作らない
from make_bgm import SR, freq, marimba, midi  # noqa: E402

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/res/raw"
QUALITY = 0.6  # Vorbis の音質（0〜1）。短い音なので、少し上げても数 KB

rng = np.random.default_rng(20261109)


def place(buf: np.ndarray, at_s: float, tone: np.ndarray) -> None:
    """[buf] の [at_s] 秒のところに [tone] を重ねる（はみ出た分は切る）"""
    start = int(round(at_s * SR))
    end = min(len(buf), start + len(tone))
    buf[start:end] += tone[: end - start]


def finish(buf: np.ndarray, peak: float) -> np.ndarray:
    """終わりを 20 ms でならして（ぷつっと切れないように）、いちばん大きいところを [peak] にそろえる"""
    fade = min(len(buf), int(0.02 * SR))
    buf[-fade:] *= np.linspace(1, 0, fade)
    return buf * (peak / np.max(np.abs(buf)))


def knock(length_s: float, gain: float) -> np.ndarray:
    """木を軽く叩いた「コツ」（短い雑音を丸めたもの）。着いた音の手ごたえに足す"""
    n = int(length_s * SR)
    noise = rng.standard_normal(n) * np.exp(-np.arange(n) / (0.008 * SR))
    return np.convolve(noise, np.ones(8) / 8, mode="same") * gain


def press() -> np.ndarray:
    """ボタン：高めの短い「ぽこっ」。響きを 0.18 秒で切って、軽くする"""
    buf = np.zeros(int(0.18 * SR))
    place(buf, 0.0, marimba(midi("G5"), 1.0))
    return finish(buf, 0.22)


def hop() -> np.ndarray:
    """積み木が跳ねる：4 度下から基準の音（G5）へ、すばやく 2 つ「ぴょん」"""
    buf = np.zeros(int(0.42 * SR))
    place(buf, 0.0, marimba(midi("D5"), 0.45))
    place(buf, 0.045, marimba(midi("G5"), 1.0))
    return finish(buf, 0.32)


def wood(at_hz: float, gain: float) -> np.ndarray:
    """小さな木の板を叩いた「コッ」。ごく短く響く板の鳴り（十数 ms で消えるので、音の高さはほとんど聞こえない）と、叩いた瞬間の雑音"""
    n = int(0.06 * SR)
    t = np.arange(n) / SR
    # 響きは 4 ms ほどで消す（長く響くと音の高さが聞こえて、直前の「ぴょん」とぶつかる）
    body = np.sin(2 * np.pi * at_hz * t) * np.exp(-t / 0.004)
    body += 0.5 * np.sin(2 * np.pi * at_hz * 1.6 * t) * np.exp(-t / 0.0025)
    tone = body + knock(0.06, 0.5)
    attack = int(0.001 * SR)
    tone[:attack] *= np.linspace(0, 1, attack)
    return tone * gain


def land() -> np.ndarray:
    """最後の積み木が着く：木の打音を 2 つ「コ・トッ」（1 つ目は小さく高め、2 つ目が着いた音）。音階の音にしないので、どの「ぴょん」と重なっても濁らない"""
    buf = np.zeros(int(0.12 * SR))
    # 板の鳴りは、少しだけ残る高さを レ（D7）・ソ（G6）にする。直前の最後の積み木の音（上のド）と協和する
    place(buf, 0.0, wood(freq(midi("D7")), 0.35))
    place(buf, 0.03, wood(freq(midi("G6")), 1.0))
    return finish(buf, 0.3)


def done() -> np.ndarray:
    """道を 1 本集めた：ド・ミ・ソ・ドと駆け上がり、最後のドに下のミとソを重ねて少し響かせる"""
    buf = np.zeros(int(1.1 * SR))
    for k, name in enumerate(("C5", "E5", "G5")):
        place(buf, k * 0.075, marimba(midi(name), 0.7))
    top = 3 * 0.075
    place(buf, top, marimba(midi("C6"), 1.0))
    place(buf, top, marimba(midi("E5"), 0.35))
    place(buf, top, marimba(midi("G5"), 0.35))
    return finish(buf, 0.4)


SOUNDS = {
    "sfx_press": press,
    "sfx_hop": hop,
    "sfx_land": land,
    "sfx_done": done,
}


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, make in SOUNDS.items():
        pcm = np.round(make() * 32767).astype("<i2")
        out = OUT_DIR / f"{name}.ogg"
        # soundfile の compression_level は「どれだけ削るか」なので、音質の逆
        sf.write(out, pcm, SR, format="OGG", subtype="VORBIS", compression_level=1 - QUALITY)
        print(f"{out}  {len(pcm) / SR:.2f} s  {out.stat().st_size / 1e3:.1f} KB")


if __name__ == "__main__":
    main()
