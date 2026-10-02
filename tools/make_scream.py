"""Builds sounds/scream.ogg (the screamer) from "Horror Hit Soundpack 1" (CC0, by psychhead_)
downloaded from https://opengameart.org/content/horror-hit-soundpack-1 .

Usage: python tools/make_scream.py "<path to the unpacked 'Horror Hit Soundpack 1' folder>"

The pack's hits are quiet stingers with long reverb tails. Layered together - lined up so they all hit at the same
instant, normalised, and pushed through a soft clipper - they make one loud, full-range jumpscare:
a deep impact in the chest, a dense middle and two shrill high stabs. Needs numpy and ffmpeg (libvorbis)."""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

RATE = 44100
LAYERS = [  # (file inside Sound/Pitches, gain)
    ("Very Bassy/Very Bassy 3.wav", 1.0),
    ("Mid/Mid 7.wav", 0.8),
    ("High/High 6.wav", 0.75),
    ("High/High 1.wav", 0.6),
]
LENGTH = 2.6  # seconds
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "brokenworld", "sounds", "scream.ogg")


def read(path):
    with wave.open(path) as w:
        ch, sw, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        raw = w.readframes(n)
    assert sw == 2 and sr == RATE, (path, sw, sr)
    return np.frombuffer(raw, "<i2").astype(float).reshape(-1, ch).mean(axis=1) / 32768


def aligned(a):
    """Cut the silence before the hit and scale it to full volume."""
    env = np.abs(a)
    onset = int(np.argmax(env > 0.1 * env.max()))
    a = a[max(0, onset - int(0.005 * RATE)):]
    return a / np.abs(a).max()


def main(pack):
    n = int(LENGTH * RATE)
    mix = np.zeros(n)
    for name, gain in LAYERS:
        a = aligned(read(os.path.join(pack, "Sound", "Pitches", name)))[:n]
        mix[:len(a)] += a * gain
    mix /= np.abs(mix).max()
    mix = np.tanh(mix * 3.0) / np.tanh(3.0)          # denser and louder
    fade = int(0.4 * RATE)
    mix[-fade:] *= np.linspace(1, 0, fade)
    mix *= 0.97
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, "scream.wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes((mix * 32767).astype("<i2").tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "6", OUT],
                       check=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main(sys.argv[1])
