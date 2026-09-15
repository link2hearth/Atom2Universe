"""Original Space Fight effects, deterministic PCM, no downloaded samples.
Run with Python 3 (standard library only). Outputs are shipped, never synthesized in-game.
"""
import math
import random
import struct
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'app/src/main/assets/spacefight/audio'
RATE = 22050
rng = random.Random(2409)


def sweep(seconds, high, low, volume=0.5, noise=0.0):
    result = []
    phase = 0.0
    filtered = 0.0
    for i in range(int(seconds * RATE)):
        t = i / RATE
        x = t / seconds
        hz = low + (high - low) * math.exp(-5 * x)
        phase += 2 * math.pi * hz / RATE
        filtered += .16 * (rng.uniform(-1, 1) - filtered)
        envelope = min(1, t / .006) * (1 - x) ** 2
        result.append(volume * envelope * (math.sin(phase) + .1 * math.sin(phase * 2)
                                             + noise * filtered))
    return result


def chime(notes, spacing=.12, length=.45):
    result = [0.0] * int((len(notes) * spacing + length) * RATE)
    for n, note in enumerate(notes):
        hz = 440 * 2 ** ((note - 69) / 12)
        offset = int(n * spacing * RATE)
        for i in range(int(length * RATE)):
            t = i / RATE
            envelope = min(1, t / .008) * math.exp(-7 * t / length) * (1 - t / length)
            result[offset + i] += .30 * envelope * (
                math.sin(2 * math.pi * hz * t) + .16 * math.sin(2 * math.pi * hz * 3 * t))
    return result


def save(name, samples):
    # No normalization: deliberately keep quieter combat sounds below the jingles.
    assert max(abs(v) for v in samples) < .95, name
    assert abs(samples[0]) < .001 and abs(samples[-1]) < .001, name
    with wave.open(str(OUT / (name + '.wav')), 'wb') as f:
        f.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
        f.writeframes(b''.join(struct.pack('<h', round(v * 32767)) for v in samples))
    print(f'{name}: {len(samples)/RATE:.2f}s, peak {max(abs(v) for v in samples):.3f}')


OUT.mkdir(parents=True, exist_ok=True)
for i, pitch in enumerate((760, 810, 860)):
    save(f'shot_{i}', sweep(.105, pitch, 310, .27))
save('pop', sweep(.19, 430, 115, .43, .22))
save('hit', sweep(.23, 210, 80, .48, .5))
save('boss', chime([60, 64, 67, 72, 76], .085, .65))
save('wave', chime([72, 76, 79], .10, .45))
save('meteor', chime([79, 74], .15, .35))
save('game_over', chime([76, 72, 67, 60], .21, .7))
