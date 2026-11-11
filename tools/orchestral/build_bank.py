"""Fabrique la banque Orchestral : une version allégée d'un SoundFont, avec ses vrais échantillons.

    python tools/orchestral/build_bank.py C:\\chemin\\Perso.sf2

Seuls les programmes de PROGRAMS sont repris : des sons dont la licence permet la copie (le piano
est en CC0). Les autres instruments d'Orchestral restent recalculés par l'application, sans
aucun échantillon embarqué.

Pour chaque programme retenu :
- les paires stéréo deviennent mono ;
- les échantillons à 40 kHz ou plus passent à la moitié de leur fréquence, sauf les sons brillants ;
- les longues notes sont coupées après une à deux secondes puis bouclées. L'enveloppe de volume
  reprend alors la décroissance mesurée sur l'échantillon d'origine ;
- la fin inaudible des notes sans boucle est retirée.
Le résultat est un SF2 ordinaire. Les modulateurs ne sont pas recopiés (le moteur ne les lit pas).
"""
import math
import mmap
import struct
import sys
from pathlib import Path

import numpy as np
from scipy.signal import resample_poly

OUT = Path(__file__).resolve().parents[2] / "app/src/main/resources/studio/orchestral/bank.sf2"
PROGRAMS = {0: 25}          # programme -> atténuation du preset en centibels (niveau égal au SF2 d'origine)
NO_SHORTENING = range(112, 128)   # percussions et bruitages : sons qui évoluent, jamais bouclés ici
BRIGHT_DB = -15.0           # part d'énergie au-dessus de 9,5 kHz à partir de laquelle la fréquence est gardée
MONO_GAIN = 10 ** (-3 / 20) # un son mono centré sonne 3 dB plus fort qu'une paire : niveau commun

# Numéros de générateurs SF2
G_PAN, G_DELAY, G_ATTACK, G_HOLD, G_DECAY, G_SUSTAIN = 17, 33, 34, 35, 36, 37
G_KEY_HOLD, G_KEY_DECAY = 39, 40
G_INSTRUMENT, G_KEYS, G_VELS, G_ATTENUATION = 41, 43, 44, 48
G_SAMPLE, G_MODES, G_ROOT = 53, 54, 58
OFFSETS = (0, 1, 2, 3, 4, 12, 45, 50)   # décalages de début, de fin et de boucle


def read(path):
    handle = open(path, "rb")
    data = mmap.mmap(handle.fileno(), 0, access=mmap.ACCESS_READ)
    chunks = {}
    pos = 12
    while pos < len(data):
        name, size = struct.unpack_from("<4sI", data, pos)
        if name == b"LIST":
            inner = pos + 12
            while inner < pos + 8 + size:
                sub, length = struct.unpack_from("<4sI", data, inner)
                chunks[sub.decode()] = (inner + 8, length)
                inner += 8 + length + (length & 1)
        pos += 8 + size + (size & 1)

    def records(chunk, layout):
        start, size = chunks[chunk]
        step = struct.calcsize(layout)
        return [struct.unpack_from(layout, data, start + i * step) for i in range(size // step)]

    return {
        "data": data, "smpl": chunks["smpl"][0], "frames": chunks["smpl"][1] // 2,
        "phdr": records("phdr", "<20sHHHIII"), "pbag": records("pbag", "<HH"), "pgen": records("pgen", "<Hh"),
        "inst": records("inst", "<20sH"), "ibag": records("ibag", "<HH"), "igen": records("igen", "<Hh"),
        "shdr": records("shdr", "<20sIIIIIBbHH"),
    }


def zones(bags, gens, first, last):
    return [gens[bags[b][0]:bags[b + 1][0]] for b in range(first, last)]


def text(raw):
    return raw.split(b"\0")[0].decode("latin1")


def seconds(timecents):
    return 2.0 ** (timecents / 1200)


def timecents(value, limit):
    return int(max(-12000, min(limit, round(1200 * math.log2(max(value, 1e-4))))))


def level_db(signal, start, stop):
    part = signal[max(0, start):max(1, stop)]
    return 10 * math.log10(float(np.mean(part * part)) + 1e-12)


def power(signal):
    return float(np.mean(signal * signal)) + 1e-12


def is_bright(signal, rate):
    """Vrai si le son a une part audible au-dessus de 9,5 kHz, que la moitié de la fréquence couperait."""
    if len(signal) < 2048:
        return False
    size = 4096 if len(signal) >= 4096 else 2048
    total = np.zeros(size // 2 + 1)
    window = np.hanning(size)
    for start in range(0, len(signal) - size + 1, size):
        total += np.abs(np.fft.rfft(signal[start:start + size] * window)) ** 2
    frequencies = np.fft.rfftfreq(size, 1 / rate)
    high = total[frequencies >= 9500].sum()
    return 10 * math.log10(high / total.sum() + 1e-12) > BRIGHT_DB


def halve_looped(sound, loop_start, loop_end, tail):
    """Divise la fréquence par deux en gardant une boucle exacte. Retourne (signal, début, fin de boucle)."""
    length = loop_end - loop_start
    if length % 2 and length > 2000:
        loop_end -= 1       # un échantillon de moins sur une longue boucle ne s'entend pas
        length -= 1
    turns = 1 if length % 2 == 0 else 2
    loop = sound[loop_start:loop_end]
    after = tail if tail is not None else np.tile(loop, 256 // length + 2)[:max(256, length)]
    half = resample_poly(np.concatenate([sound[:loop_start], np.tile(loop, turns), after]), 1, 2)
    start = (loop_start + 1) // 2
    end = start + turns * length // 2
    keep = len(half) if tail is not None else end + 8
    return half[:keep], start, end


def shorten(sound, rate, limit, root, floor_from):
    """Coupe et boucle une longue note. Retourne (signal, début, fin de boucle, tenue s, chute dB/s, plancher dB)."""
    keep = 2.0 if root < 48 else 1.5
    start = int(keep * rate)
    hop = max(1, rate // 20)
    first = start - rate // 2
    last = min(limit, start + int(2.5 * rate))
    times = np.arange(first, last - hop, hop)
    if len(times) < 6:
        return None
    levels = np.array([level_db(sound, t, t + hop) for t in times])
    slope, origin = np.polyfit((times + hop / 2 - start) / rate, levels, 1)
    if slope > 0.5:
        return None         # un son qui enfle ne se boucle pas
    slope = float(max(-30.0, min(-0.3, slope)))
    loop = 1.2 if slope > -2 else (0.8 if root < 48 else 0.6)
    length = int(loop * rate)
    if limit < start + length + rate // 2:
        return None
    fade = int(0.15 * rate)
    floor = None if floor_from is None else min(0.0, level_db(sound, floor_from, len(sound)) - origin)
    # La boucle est remise à niveau constant : l'enveloppe de volume refait la décroissance.
    index = np.arange(start - fade, start + length)
    flat = sound[index] * 10 ** (-slope * (index - start) / rate / 20)
    body = flat[fade:].copy()
    mix = np.arange(fade) / fade * (math.pi / 2)
    body[length - fade:] = body[length - fade:] * np.cos(mix) + flat[:fade] * np.sin(mix)
    signal = np.concatenate([sound[:start], body, body[:8]])
    return signal, start, start + length, start / rate, slope, floor


def trimmed_length(sound, rate):
    """Longueur utile d'une note sans boucle (la fin sous -55 dB est inaudible)."""
    hop = max(1, rate // 20)
    if len(sound) < 4 * hop:
        return len(sound)
    levels = np.array([level_db(sound, t, t + hop) for t in range(0, len(sound) - hop, hop)])
    quiet = np.where(levels < levels.max() - 55)[0]
    quiet = quiet[quiet > levels.argmax()]
    return max(int(0.3 * rate), int(quiet[0]) * hop) if len(quiet) else len(sound)


class Bank:
    def __init__(self, source):
        self.sf = read(source)
        self.pcm = []           # échantillons 16 bits, chacun suivi de 46 zéros
        self.headers = []       # (nom, début, fin, boucle début, boucle fin, fréquence, note, correction)
        self.position = 0
        self.sounds = {}        # clé d'échantillon -> (numéro, informations de boucle)
        self.instruments = {}   # instrument d'origine -> numéro
        self.instrument_zones = []  # (nom, [zones])

    def wave(self, sample, start, end):
        return np.frombuffer(self.sf["data"], dtype="<i2", count=end - start,
                             offset=self.sf["smpl"] + 2 * start).astype(np.float64)

    def sound(self, key, decay, can_shorten):
        """Prépare un échantillon. key = (numéros, début, fin, boucle début, boucle fin, mode).
        decay = (début s, chute dB/s, plancher dB) de l'enveloppe d'origine, ou None."""
        for known in ((key, decay, can_shorten), (key, None, False)):
            if known in self.sounds:
                return self.sounds[known]
        ids, start, end, loop_start, loop_end, mode = key
        header = self.sf["shdr"][ids[0]]
        rate = header[5] if header[5] > 0 else 44100
        waves = [self.wave(i, start + self.sf["shdr"][i][1] - header[1], end + self.sf["shdr"][i][1] - header[1])
                 for i in ids]
        mono = sum(waves) / len(waves)
        if len(waves) == 2:
            # Deux micros écartés s'annulent en partie quand on les additionne : garder la
            # puissance de la paire, et prendre le canal le plus fort si la perte dépasse 1,5 dB.
            target = math.sqrt(sum(power(w) for w in waves) / 2)
            if 20 * math.log10(target / math.sqrt(power(mono))) > 1.5:
                mono = max(waves, key=power)
            gain = target / math.sqrt(power(mono))
        else:
            gain = MONO_GAIN
        mono = mono * min(gain, 32767 / max(1.0, float(np.abs(mono).max())))
        looped = mode in (1, 3)
        loop_start -= start
        loop_end -= start
        halve = rate >= 40000 and not is_bright(mono, rate)
        info = None
        can_shorten = can_shorten and mode != 3
        if halve:
            rate = int(round(rate / 2))

        def candidate(plain):
            # La décroissance de l'enveloppe d'origine est inscrite dans le son raccourci,
            # pour que la pente mesurée et la nouvelle enveloppe la contiennent.
            if decay is None:
                return plain
            begin, fall, bottom = decay
            return plain * 10 ** (-np.clip((np.arange(len(plain)) / rate - begin) * fall, 0, bottom) / 20)

        if looped:
            plain = resample_poly(mono, 1, 2) if halve else mono
            scale = 2 if halve else 1
            result = shorten(candidate(plain), rate, loop_start // scale, header[6], loop_start // scale) if can_shorten else None
            if result:
                signal, loop_start, loop_end, *info = result
            elif halve:
                tail = mono[loop_end:] if mode == 3 else None
                signal, loop_start, loop_end = halve_looped(mono, loop_start, loop_end, tail)
            else:
                signal = mono if mode == 3 else np.concatenate([mono[:loop_end], mono[loop_start:loop_end][:8]])
        else:
            plain = resample_poly(mono, 1, 2) if halve else mono
            length = trimmed_length(plain, rate)
            result = shorten(candidate(plain), rate, length, header[6], None) if can_shorten else None
            if result:
                signal, loop_start, loop_end, *info = result
                looped = True
            else:
                signal = plain[:length].copy()
                fade = min(len(signal), max(1, rate // 50)) if length < len(plain) else 0
                if fade:
                    signal[-fade:] *= np.linspace(1, 0, fade)
                loop_start, loop_end = 0, len(signal)
        number = len(self.headers)
        name = text(header[0])
        if len(ids) == 2:
            name = name.replace("_L", "").replace("_R", "")
        self.headers.append((name, self.position, self.position + len(signal), self.position + loop_start,
                             self.position + loop_end, rate, header[6], header[7]))
        self.pcm.append(np.clip(np.round(signal), -32768, 32767).astype("<i2"))
        self.pcm.append(np.zeros(46, dtype="<i2"))
        self.position += len(signal) + 46
        made = (number, (looped, mode, info))
        self.sounds[(key, decay, True) if info else (key, None, False)] = made
        return made

    def envelope(self, gens, effective, info, root):
        """Écrit la tenue et la décroissance d'une note raccourcie."""
        hold, slope, floor = info
        rate = -slope
        floor_cb = 1000 if floor is None else -10 * floor
        lead = seconds(effective.get(G_DELAY, -12000)) + seconds(effective.get(G_ATTACK, -12000))
        # Une note jouée plus haut lit l'échantillon plus vite : la tenue et la chute suivent la touche.
        track = 2.0 ** ((root - 60) / 12)
        gens[G_HOLD] = timecents(max(0.001, hold - lead) * track, 5000)
        gens[G_DECAY] = timecents(100 / rate * track, 8000)
        gens[G_SUSTAIN] = int(round(min(1000, floor_cb)))
        gens[G_KEY_HOLD] = 100
        gens[G_KEY_DECAY] = 100

    def instrument(self, index, program):
        if index in self.instruments:
            return self.instruments[index]
        sf = self.sf
        header = sf["inst"][index]
        shared = {}
        locals_ = []
        for gens in zones(sf["ibag"], sf["igen"], header[1], sf["inst"][index + 1][1]):
            values = dict(gens)
            if G_SAMPLE in values:
                locals_.append(values)
            elif not locals_:
                shared = values
        made = []
        used = set()
        for position, values in enumerate(locals_):
            if position in used:
                continue
            effective = {**shared, **values}
            sample = sf["shdr"][values[G_SAMPLE]]
            ids = [values[G_SAMPLE]]
            # Paire stéréo : deux zones jumelles, panoramiques opposés, échantillons liés.
            if sample[9] in (2, 4) and abs(effective.get(G_PAN, 0)) >= 400:
                for other in range(position + 1, len(locals_)):
                    twin = {**shared, **locals_[other]}
                    linked = sf["shdr"][twin[G_SAMPLE]]
                    if (other not in used and sample[8] == twin[G_SAMPLE] and linked[2] - linked[1] == sample[2] - sample[1]
                            and all(twin.get(g) == effective.get(g) for g in (G_KEYS, G_VELS, G_MODES, *OFFSETS))
                            and twin.get(G_PAN, 0) * effective.get(G_PAN, 0) < 0):
                        ids.append(twin[G_SAMPLE])
                        used.add(other)
                        break

            def offset(fine, coarse):
                return effective.get(fine, 0) + 32768 * effective.get(coarse, 0)

            start = sample[1] + offset(0, 4)
            end = sample[2] + offset(1, 12)
            loop_start = sample[3] + offset(2, 45)
            loop_end = sample[4] + offset(3, 50)
            mode = effective.get(G_MODES, 0) & 3
            if mode == 2:
                mode = 0
            if not (sample[1] <= start < end <= sf["frames"]) or end - start < 8:
                continue
            if mode and not (start <= loop_start and loop_start + 2 <= loop_end <= end):
                mode = 0
            lead = seconds(effective.get(G_DELAY, -12000)) + seconds(effective.get(G_ATTACK, -12000))
            sustain = min(1000, effective.get(G_SUSTAIN, 0))
            decay = None
            if sustain > 0:
                decay = (round(lead + seconds(effective.get(G_HOLD, -12000)), 3),
                         round(100 / seconds(min(8000, effective.get(G_DECAY, -12000))), 3), sustain / 10)
            root = effective.get(G_ROOT, -1)
            if not 0 <= root <= 127:
                root = sample[6]
            number, (looped, mode, info) = self.sound((tuple(ids), start, end, loop_start, loop_end, mode), decay,
                                                      program not in NO_SHORTENING)
            drop = {G_SAMPLE, G_MODES, G_KEYS, G_VELS, *OFFSETS}
            gens = {op: v for op, v in values.items() if op not in drop}
            if len(ids) == 2:
                gens[G_PAN] = 0
            if looped:
                gens[G_MODES] = mode if mode else 1
            elif G_MODES in shared:
                gens[G_MODES] = 0
            if info:
                self.envelope(gens, effective, info, root)
            zone = []
            if G_KEYS in values:
                zone.append((G_KEYS, values[G_KEYS]))
            if G_VELS in values:
                zone.append((G_VELS, values[G_VELS]))
            made.append(zone + sorted(gens.items()) + [(G_SAMPLE, number)])
        common = [(op, v) for op, v in shared.items() if op not in OFFSETS and op != G_MODES]
        common.sort(key=lambda g: (g[0] not in (G_KEYS, G_VELS), g[0]))
        self.instruments[index] = len(self.instrument_zones)
        self.instrument_zones.append((text(header[0]), ([common] if common else []) + made))
        return self.instruments[index]

    def build(self):
        sf = self.sf
        presets = []
        for program in PROGRAMS:
            index = next((i for i, p in enumerate(sf["phdr"][:-1]) if p[2] == 0 and p[1] == program), None)
            if index is None:
                continue
            before = self.position
            preset = sf["phdr"][index]
            made = []
            for gens in zones(sf["pbag"], sf["pgen"], preset[3], sf["phdr"][index + 1][3]):
                values = dict(gens)
                zone = [(op, v) for op, v in gens if op not in (G_INSTRUMENT, G_ATTENUATION)]
                if not made:
                    zone.append((G_ATTENUATION, PROGRAMS[program]))
                zone.sort(key=lambda g: (g[0] not in (G_KEYS, G_VELS), g[0]))
                if G_INSTRUMENT in values:
                    zone.append((G_INSTRUMENT, self.instrument(values[G_INSTRUMENT], program)))
                    made.append(zone)
                elif not made:
                    made.append(zone)
            if G_INSTRUMENT in dict(made[0]):
                made.insert(0, [(G_ATTENUATION, PROGRAMS[program])])
                made[1] = [g for g in made[1] if g[0] != G_ATTENUATION]
            presets.append((text(preset[0]), program, made))
            added = self.position - before
            print(f"{program:3d} {text(preset[0]):22s} {added * 2 / 1e6:5.2f} Mo")
        self.write(presets)

    def write(self, presets):
        def name20(value):
            return value.encode("latin1")[:19].ljust(20, b"\0")

        def gen(op, value):
            return struct.pack("<HH", op, value & 0xFFFF)

        phdr = pbag = pgen = inst = ibag = igen = shdr = b""
        count = bags = 0
        for name, program, made in presets:
            phdr += name20(name) + struct.pack("<HHHIII", program, 0, bags, 0, 0, 0)
            for zone in made:
                pbag += struct.pack("<HH", count, 0)
                pgen += b"".join(gen(op, value) for op, value in zone)
                count += len(zone)
                bags += 1
        phdr += name20("EOP") + struct.pack("<HHHIII", 0, 0, bags, 0, 0, 0)
        pbag += struct.pack("<HH", count, 0)
        pgen += gen(0, 0)
        count = bags = 0
        for name, made in self.instrument_zones:
            inst += name20(name) + struct.pack("<H", bags)
            for zone in made:
                ibag += struct.pack("<HH", count, 0)
                igen += b"".join(gen(op, value) for op, value in zone)
                count += len(zone)
                bags += 1
        inst += name20("EOI") + struct.pack("<H", bags)
        ibag += struct.pack("<HH", count, 0)
        igen += gen(0, 0)
        for name, start, end, loop_start, loop_end, rate, pitch, correction in self.headers:
            shdr += name20(name) + struct.pack("<IIIIIBbHH", start, end, loop_start, loop_end, rate, pitch, correction, 0, 1)
        shdr += name20("EOS") + struct.pack("<IIIIIBbHH", 0, 0, 0, 0, 0, 0, 0, 0, 0)

        def chunk(name, body):
            return name + struct.pack("<I", len(body)) + body + (b"\0" if len(body) & 1 else b"")

        def group(kind, body):
            return chunk(b"LIST", kind + body)

        samples = np.concatenate(self.pcm).tobytes()
        info = chunk(b"ifil", struct.pack("<HH", 2, 1)) + chunk(b"isng", b"EMU8000\0") + chunk(b"INAM", b"Orchestral\0\0")
        pdta = (chunk(b"phdr", phdr) + chunk(b"pbag", pbag) + chunk(b"pmod", bytes(10)) + chunk(b"pgen", pgen)
                + chunk(b"inst", inst) + chunk(b"ibag", ibag) + chunk(b"imod", bytes(10)) + chunk(b"igen", igen)
                + chunk(b"shdr", shdr))
        body = b"sfbk" + group(b"INFO", info) + group(b"sdta", chunk(b"smpl", samples)) + group(b"pdta", pdta)
        OUT.write_bytes(b"RIFF" + struct.pack("<I", len(body)) + body)
        print(f"{OUT} : {OUT.stat().st_size / 1e6:.1f} Mo, {len(self.headers)} échantillons")


if __name__ == "__main__":
    Bank(sys.argv[1]).build()
