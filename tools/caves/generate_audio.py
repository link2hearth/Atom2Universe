"""Regenerate only the approved original shotgun. Other effects are licensed recordings.
Use import_recorded_audio.py for those; never overwrite them with synthesis.
"""
import math
import random
import struct
import wave
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "app/src/main/assets/caves/audio"
RATE = 22050

def shotgun_blast(variant):
    """A single heavy blast: broad crack, dense low-mid body and a short rumble."""
    noise = random.Random(9152040 + variant)
    length = .78
    low = mid = 0.0
    samples = []
    fundamental = 64 + variant * 2
    for i in range(int(length * RATE)):
        t = i / RATE
        white = noise.uniform(-1, 1)
        low += .045 * (white - low)
        mid += .32 * (white - mid)
        attack = min(1, t / .0015)
        tail = min(1, (length - t) / .09)
        # Low mids carry the weight on phone speakers too. All frequencies are fixed;
        # no delayed clicks, repeated blasts or descending laser-like oscillators.
        crack = .9 * (white - mid) * math.exp(-t * 65)
        body = (1.8 * mid + 2.3 * low) * math.exp(-t * 10)
        thump = (.55 * math.sin(2 * math.pi * fundamental * t)
                 + .30 * math.sin(2 * math.pi * 132 * t)
                 + .17 * math.sin(2 * math.pi * 195 * t)) * math.exp(-t * 16)
        rumble = 1.7 * low * math.exp(-t * 6)
        samples.append(.86 * math.tanh(1.9 * (crack + body + thump + rumble)) * attack * tail)
    return samples


def save(name, samples):
    peak = max(abs(v) for v in samples)
    gain = min(1, .88 / max(peak, .001))
    samples[0] = samples[-1] = 0
    assert all(math.isfinite(v) for v in samples)
    with wave.open(str(OUT / (name + '.wav')), 'wb') as f:
        f.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
        f.writeframes(b''.join(struct.pack('<h', round(v * gain * 32767)) for v in samples))


OUT.mkdir(parents=True, exist_ok=True)
for variant in range(3):
    save(f"shotgun_{variant}", shotgun_blast(variant))
print("Regenerated only the three approved shotgun variants.")
