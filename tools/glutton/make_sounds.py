"""Rebuild the four original Pocket Gobbler sound effects; standard library only."""
from pathlib import Path
import math
import struct
import wave


def main():
    destination = Path(__file__).resolve().parents[2] / "app/src/main/res/raw"
    destination.mkdir(parents=True, exist_ok=True)
    rate = 22050
    sounds = [
        ("glutton_bite", .10, [(0, 700, .10)]),
        ("glutton_grow", .36, [(0, 523.25, .15), (.075, 659.25, .15), (.15, 783.99, .2)]),
        ("glutton_vacuum", .26, [(0, 160, .26)]),
        ("glutton_bump", .17, [(0, 135, .17)]),
    ]
    for name, duration, notes in sounds:
        pcm = bytearray()
        for sample in range(int(rate * duration)):
            time = sample / rate
            value = 0
            for start, frequency, length in notes:
                age = time - start
                if 0 <= age < length:
                    sweep = 800 if name == "glutton_bite" else 130
                    phase = 2 * math.pi * (frequency * age + sweep * age * age)
                    envelope = min(1, age / .008) * (1 - age / length) ** 1.8
                    value += math.sin(phase) * envelope * .28
            pcm += struct.pack("<h", int(max(-1, min(1, value)) * 32767))
        with wave.open(str(destination / (name + ".wav")), "wb") as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(rate)
            output.writeframes(pcm)


if __name__ == "__main__":
    main()
