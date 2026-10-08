"""Generate the golf module's original landing, cup and penalty feedback sounds."""
from pathlib import Path
import math
import random
import struct
import wave

OUTPUT = Path(__file__).resolve().parents[2] / 'app/src/main/assets/golf/sounds'
RATE = 22050


def write(name, duration, sample):
    noise = random.Random(7349)
    frames = []
    for i in range(int(duration * RATE)):
        t = i / RATE
        fade = min(1, t / .004, (duration - t) / .012)
        value = sample(t, noise) * max(0, fade)
        frames.append(struct.pack('<h', int(max(-1, min(1, value)) * 32767)))
    with wave.open(str(OUTPUT / name), 'wb') as sound:
        sound.setnchannels(1)
        sound.setsampwidth(2)
        sound.setframerate(RATE)
        sound.writeframes(b''.join(frames))


if __name__ == '__main__':
    OUTPUT.mkdir(parents=True, exist_ok=True)
    write('landing.wav', .15, lambda t, n: math.exp(-t*35) *
          (.24*math.sin(2*math.pi*170*t) + .20*n.uniform(-1,1)))
    write('cup.wav', .38, lambda t, n: math.exp(-t*9) *
          (.23*math.sin(2*math.pi*784*t) + .13*math.sin(2*math.pi*1176*t)))
    write('penalty.wav', .28, lambda t, n: .24*math.exp(-t*10) *
          math.sin(2*math.pi*(330*t-170*t*t)))
