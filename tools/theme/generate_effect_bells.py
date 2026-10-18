"""Generate the original, quiet Christmas bell asset using only Python's standard library."""

import math
from pathlib import Path
import struct
import wave

OUTPUT = Path(__file__).resolve().parents[2] / "app/src/main/res/raw/effect_christmas_bells.wav"
RATE = 24_000
DURATION = 1.8
NOTES = ((0.0, 1046.5), (0.22, 1318.51), (0.44, 1567.98))
PARTIALS = ((1, 1, 3.3), (2.76, .24, 5.8), (5.4, .08, 10))


def main():
    samples = bytearray()
    for i in range(int(RATE * DURATION)):
        t = i / RATE
        amplitude = 0.0
        for start, frequency in NOTES:
            u = t - start
            if u < 0:
                continue
            attack = min(1, u / .012)
            for ratio, strength, decay in PARTIALS:
                amplitude += (attack * strength * math.exp(-u * decay)
                              * math.sin(2 * math.pi * frequency * ratio * u))
        amplitude *= min(1, (DURATION - t) / .15) * .19
        samples.extend(struct.pack("<h", int(max(-1, min(1, amplitude)) * 32767)))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUTPUT), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(RATE)
        output.writeframes(samples)


if __name__ == "__main__":
    main()
