"""Fabrique la banque Orchestral : de vrais échantillons allégés, de sources dont la licence permet la copie.

    python tools/orchestral/build_bank.py C:\\chemin\\Perso.sf2 C:\\chemin\\VSCO-2-CE

Deux sources :
- PROGRAMS : programmes repris du SoundFont (le piano, en CC0) ;
- WAVES : prises de VSCO 2 Community Edition (CC0, github.com/sgossner/VSCO-2-CE), une par tierce
  environ, la nuance la plus forte. Chaque prise est mise en mono et accordée. Une note tenue est
  coupée après l'attaque puis bouclée sur une seconde environ, vibrato compris ; une note frappée
  ou pincée garde sa résonance, raccourcie.
Les autres instruments d'Orchestral restent recalculés par l'application.

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
import re
import struct
import sys
from pathlib import Path

import numpy as np
import soundfile
from scipy.signal import resample_poly

OUT = Path(__file__).resolve().parents[2] / "app/src/main/resources/studio/orchestral/bank.sf2"
PROGRAMS = {0: 25}          # programme -> atténuation du preset en centibels (niveau égal au SF2 d'origine)
# programme -> prises de VSCO 2 CE. Tout le clavier joue : hors de l'étendue de l'instrument, la prise
# la plus proche est transposée (le lecteur ne retombe pas sur le piano).
# folders : dossiers de prises, les suivants l'emportent sur les précédents pour une même note ;
# keep : durée gardée d'une note frappée, en secondes (absent : note tenue, bouclée) ;
# release : extinction en secondes ; gain : décibels ajoutés au niveau commun (niveau égal au SF2
# de référence sur une phrase) ; same : programme dont les prises sont reprises telles quelles ;
# numbered : pour des fichiers numérotés par touche, la note du fichier 0 ; only : texte que le nom
# des prises doit contenir ; octave : décalage des notes écrites quand la mesure ne peut pas le
# trouver ; pitches : hauteur donnée à la main (début du nom -> note MIDI décimale), pour les
# timbales, dont le son est inharmonique (note = premier mode de la peau, relevé au spectre).
STRINGS = ["Strings/Solo Contrabass/SusVib", "Strings/Cello Section/susvib", "Strings/Viola Section/susvib",
           "Strings/Violin Section/susVib"]
WAVES = {
    9: dict(name="Glockenspiel", folders=["Percussion/Glock"], keep=2.0, release=1.0, gain=-7.3),
    12: dict(name="Marimba", folders=["Percussion/Marimba"], keep=1.5, release=0.8, gain=8.0),
    13: dict(name="Xylophone", folders=["Percussion/Xylo"], keep=1.0, release=0.6, gain=4.5),
    19: dict(name="Church Organ", folders=["Keys/Organ/Loud"], only="Man3Open", numbered=35, release=0.6, gain=-0.5),
    40: dict(name="Violin", folders=["Strings/Solo Violin/Arco Vib"], release=0.5, gain=1.0),
    41: dict(name="Viola", folders=["Strings/Viola Section/susvib"], release=0.5, gain=2.5),
    42: dict(name="Cello", folders=["Strings/Cello Section/susvib"], release=0.5, gain=8.0),
    43: dict(name="Contrabass", folders=["Strings/Solo Contrabass/SusVib"], release=0.5, gain=8.0),
    44: dict(name="Tremolo Strings", folders=["Strings/Violin Section/Trem"], release=0.5, gain=2.6),
    45: dict(name="Pizzicato Strings", folders=["Strings/Solo Contrabass/Pizz", "Strings/Cello Section/pizzT",
                                               "Strings/Viola Section/pizz", "Strings/Violin Section/Pizz"],
             octave=12, keep=1.2, release=0.6, gain=-0.7),
    46: dict(name="Harp", folders=["Strings/Harp"], keep=2.5, release=1.5, gain=5.3),
    47: dict(name="Timpani", folders=["Percussion/Timpani"], keep=2.5, release=2.0, gain=7.9,
             pitches={"Timpani1": 41.45, "Timpani2": 46.8, "Timpani3": 49.45, "Timpani4": 52.4, "Timpani5": 54.6}),
    48: dict(name="String Ensemble 1", folders=STRINGS, release=0.6, gain=5.4),
    49: dict(name="String Ensemble 2", same=48),
    56: dict(name="Trumpet", folders=["Brass/Trumpet/sus"], release=0.3, gain=0.3),
    57: dict(name="Trombone", folders=["Brass/Tenor Trombone/sus"], release=0.3, gain=0.3),
    58: dict(name="Tuba", folders=["Brass/Tuba/sus"], release=0.3, gain=0.7),
    59: dict(name="Muted Trumpet", folders=["Brass/Trumpet/harmonM-sus"], release=0.3, gain=1.4),
    60: dict(name="French Horn", folders=["Brass/F Horn/sus"], release=0.4, gain=4.4),
    68: dict(name="Oboe", folders=["Woodwinds/Oboe/Sus"], release=0.4, gain=1.3),
    70: dict(name="Bassoon", folders=["Woodwinds/Bassoon/sus"], release=0.4, gain=7.2),
    71: dict(name="Clarinet", folders=["Woodwinds/Clarinet/susLong"], release=0.4, gain=7.4),
    72: dict(name="Piccolo", folders=["Woodwinds/Piccolo/Sus"], release=0.4, gain=8.0),
    73: dict(name="Flute", folders=["Woodwinds/Flute/susvib"], release=0.5, gain=3.3),
}
DYNAMICS = {"pp": 0, "p": 1, "mp": 2, "mf": 3, "f": 4, "ff": 5}
NOTE = re.compile(r"(?<![A-Za-z])([A-G])(#?)(\d)(?![0-9])")
STEPS = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
WAVE_LEVEL = 3300           # niveau efficace commun des notes tenues (-20 dB)
NO_SHORTENING = range(112, 128)   # percussions et bruitages : sons qui évoluent, jamais bouclés ici
BRIGHT_DB = -15.0           # part d'énergie au-dessus de 9,5 kHz à partir de laquelle la fréquence est gardée
MONO_GAIN = 10 ** (-3 / 20) # un son mono centré sonne 3 dB plus fort qu'une paire : niveau commun

# Numéros de générateurs SF2
G_PAN, G_DELAY, G_ATTACK, G_HOLD, G_DECAY, G_SUSTAIN, G_RELEASE = 17, 33, 34, 35, 36, 37, 38
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


def rms(signal):
    return math.sqrt(power(signal))


def pitch(signal, rate):
    """Hauteur d'une note, en numéro MIDI décimal (autocorrélation). Peu sûre à l'octave près, et fausse sur
    les sons inharmoniques : tuning() la recoupe avec la note écrite dans le nom du fichier."""
    spectrum = np.fft.rfft(signal * np.hanning(len(signal)), 1 << 17)
    correlation = np.fft.irfft(np.abs(spectrum) ** 2)
    low, high = rate // 4500, rate // 28
    lag = low + int(np.argmax(correlation[low:high]))
    top = correlation[lag]
    for divisor in (4, 3, 2):       # la vraie période est le plus petit sous-multiple encore fort
        shorter = int(round(lag / divisor))
        if shorter >= low:
            near = shorter - 1 + int(np.argmax(correlation[shorter - 1:shorter + 2]))
            if correlation[near] > 0.85 * top:
                lag = near
                break
    a, b, c = correlation[lag - 1], correlation[lag], correlation[lag + 1]
    exact = lag + 0.5 * (a - c) / (a - 2 * b + c) if a - 2 * b + c else lag
    return 69 + 12 * math.log2(rate / max(exact, 1.0) / 440)


def tuning(prepared, numbered, octave):
    """Note et correction en cents de chaque prise d'un dossier. prepared : [(chemin, hauteur mesurée)].
    Le nom du fichier donne la note ; l'octave d'écriture, variable d'un dossier à l'autre, est celle
    que confirme le plus grand nombre de mesures. La mesure n'accorde que si elle tombe sur cette note."""
    written = []
    for path, measured in prepared:
        note = NOTE.search(path.stem)
        number = re.search(r"_(\d+)$", path.stem)
        if note:
            written.append(12 * (int(note.group(3)) + 1) + STEPS[note.group(1)] + len(note.group(2)))
        elif numbered is not None and number:
            written.append(numbered + int(number.group(1)))
        else:
            written.append(None)
    votes = {}
    for (path, measured), nominal in zip(prepared, written):
        if nominal is not None and numbered is None:
            octaves = round((measured - nominal) / 12)
            if abs(measured - nominal - 12 * octaves) < 0.5:
                votes[octaves] = votes.get(octaves, 0) + 1
    shift = 12 * max(votes, key=votes.get) if votes else octave
    result = []
    for (path, measured), nominal in zip(prepared, written):
        root = int(round(measured)) if nominal is None else nominal + shift
        cents = (root - measured) * 100
        result.append((root, int(round(cents)) if abs(cents) <= 50 else 0))
    return result


def takes(folder, only, pitches):
    """Les prises à garder d'un dossier : par note écrite dans le nom, la nuance la plus forte, première prise."""
    best = {}
    for path in sorted(folder.glob("*.wav")):
        stem = path.stem
        if only and only not in stem:
            continue
        note = NOTE.search(stem)
        layer = re.search(r"_v(\d)", stem)
        dynamic = re.search(r"_(pp|p|mp|mf|f|ff)(?:_|$)", stem)
        rank = int(layer.group(1)) if layer else DYNAMICS[dynamic.group(1)] if dynamic else 0
        key = note.group(0) if note else next((name for name in pitches if stem.startswith(name)), stem)
        if key not in best or rank > best[key][0]:
            best[key] = (rank, path)
    return [path for _, path in best.values()]


def recording(path):
    """Lit une prise : mono, à demi-fréquence si rien d'audible ne s'y perd. Retourne (signal, fréquence, niveaux par 5 ms)."""
    stereo, rate = soundfile.read(str(path), always_2d=True)
    waves = [stereo[:, c] * 32768 for c in range(stereo.shape[1])]
    mono = sum(waves) / len(waves)
    if len(waves) == 2 and math.sqrt(sum(power(w) for w in waves) / 2) / rms(mono) > 10 ** (1.5 / 20):
        mono = max(waves, key=power)
    if rate >= 40000 and not is_bright(mono, rate):
        mono = resample_poly(mono, 1, 2)
        rate //= 2
    hop = rate // 200
    levels = np.array([rms(mono[i:i + hop]) for i in range(0, len(mono) - hop, hop)])
    return mono, rate, levels


def sustained(path, gain):
    """Prépare une note tenue. Retourne (signal, début, fin de boucle, fréquence, hauteur mesurée)."""
    mono, rate, levels = recording(path)
    hop = rate // 200
    first = int(np.argmax(levels > 0.05 * levels.max()))
    body_level = float(np.median(levels[first + 100:first + 500]))
    onset = max(0, (int(np.argmax(levels > 0.1 * body_level)) - 1) * hop)
    measured = pitch(mono[onset + int(0.6 * rate):onset + int(1.1 * rate)], rate)
    fade = rate // 10
    best = None
    # Une boucle d'une seconde et plus ; pour une prise courte, une demi-seconde.
    for shortest, longest, margin in ((0.9, 1.5, 1.0), (0.4, 0.9, 0.3)):
        for delay in (0.6, 0.75, 0.9):
            start = onset + int(delay * rate)
            low, high = start + int(shortest * rate), start + int(longest * rate)
            if high > len(mono) - int(margin * rate):
                continue
            before = mono[start - fade:start]
            zone = mono[low - fade:high]
            match = np.correlate(zone, before, "valid")
            energy = np.convolve(zone * zone, np.ones(fade), "valid")
            likeness = match / np.sqrt(energy * float(np.dot(before, before)) + 1e-9)
            index = int(np.argmax(likeness))
            if best is None or likeness[index] > best[0]:
                best = (float(likeness[index]), start, low + index)
        if best:
            break
    if best is None:
        raise ValueError(f"{path.name} : note trop courte pour être bouclée")
    likeness, start, end = best
    before = mono[start - fade:start]
    # La boucle finit au niveau où elle commence, puis se fond dans ce qui précède son début.
    body = mono[start:end] * (rms(before) / rms(mono[end - fade:end])) ** (np.arange(end - start) / (end - start))
    mix = np.arange(fade) / fade
    body[-fade:] = (body[-fade:] * (1 - mix) + before * mix) / np.sqrt(
        mix * mix + (1 - mix) ** 2 + 2 * mix * (1 - mix) * max(likeness, 0.0))
    signal = np.concatenate([mono[onset:start], body, body[:8]])
    signal[:hop] *= np.linspace(0, 1, hop)
    signal *= min(WAVE_LEVEL * 10 ** (gain / 20) / rms(body), 30000 / float(np.abs(signal).max()))
    rise = int(np.argmax(levels[onset // hop:] > 0.7 * body_level)) * hop / rate
    print(f"    {path.name:40s} note {measured:6.2f}  boucle {(end - start) / rate:4.2f} s  "
          f"ressemblance {likeness:4.2f}  montée {rise * 1000:4.0f} ms")
    return signal, start - onset, end - onset, rate, measured


def struck(path, gain, keep):
    """Prépare une note frappée ou pincée, sans boucle. Même retour que sustained."""
    mono, rate, levels = recording(path)
    hop = rate // 200
    onset = max(0, (int(np.argmax(levels > 0.05 * levels.max())) - 1) * hop)
    length = min(int(keep * rate), trimmed_length(mono[onset:], rate))
    signal = mono[onset:onset + length].copy()
    fade = len(signal) // 4
    signal[-fade:] *= np.linspace(1, 0, fade) ** 2
    signal[:hop // 2] *= np.linspace(0, 1, hop // 2)
    measured = pitch(mono[onset + int(0.03 * rate):onset + int(0.35 * rate)], rate)
    window = rate // 20
    loud = max(rms(signal[i:i + window]) for i in range(0, min(len(signal) - window, rate // 3), window // 2))
    signal *= min(WAVE_LEVEL * 10 ** (gain / 20) / loud, 30000 / float(np.abs(signal).max()))
    print(f"    {path.name:40s} note {measured:6.2f}  durée {len(signal) / rate:4.2f} s")
    return signal, 0, len(signal), rate, measured


class Bank:
    def __init__(self, source):
        self.sf = read(source)
        self.pcm = []           # échantillons 16 bits, chacun suivi de 46 zéros
        self.headers = []       # (nom, début, fin, boucle début, boucle fin, fréquence, note, correction)
        self.position = 0
        self.sounds = {}        # clé d'échantillon -> (numéro, informations de boucle)
        self.instruments = {}   # instrument d'origine -> numéro
        self.instrument_zones = []  # (nom, [zones])
        self.wave_instruments = {}  # programme de WAVES -> numéro d'instrument

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

    def waves(self, program, folder):
        """Un programme fait de prises enregistrées. Retourne le preset."""
        spec = WAVES[program]
        if "same" in spec:
            return spec["name"], program, [[(G_INSTRUMENT, self.wave_instruments[spec["same"]])]]
        looped = "keep" not in spec
        made = {}
        for source in spec["folders"]:
            pitches = spec.get("pitches", {})
            paths = takes(folder / source, spec.get("only"), pitches)
            sounds = [sustained(path, spec["gain"]) if looped else struck(path, spec["gain"], spec["keep"])
                      for path in paths]
            measured = [next((pitches[name] for name in pitches if path.stem.startswith(name)), sound[4])
                        for path, sound in zip(paths, sounds)]
            tuned = tuning(list(zip(paths, measured)), spec.get("numbered"), spec.get("octave", 0))
            for sound, (root, correction) in zip(sounds, tuned):
                made[root] = (*sound[:4], correction)
            print("    notes :", " ".join(f"{root}{correction:+d}" for root, correction in sorted(tuned)))
        zones_ = []
        roots = sorted(made)
        for i, root in enumerate(roots):
            signal, loop_start, loop_end, rate, correction = made[root]
            low = 0 if i == 0 else (roots[i - 1] + root) // 2 + 1
            high = 127 if i == len(roots) - 1 else (root + roots[i + 1]) // 2
            zones_.append([(G_KEYS, low | high << 8), (G_RELEASE, timecents(spec["release"], 8000)),
                           (G_MODES, 1 if looped else 0), (G_SAMPLE, len(self.headers))])
            self.headers.append((f"{spec['name']} {root}", self.position, self.position + len(signal),
                                 self.position + loop_start, self.position + loop_end, rate, root, correction))
            self.pcm.append(np.clip(np.round(signal), -32768, 32767).astype("<i2"))
            self.pcm.append(np.zeros(46, dtype="<i2"))
            self.position += len(signal) + 46
        self.wave_instruments[program] = len(self.instrument_zones)
        self.instrument_zones.append((spec["name"], zones_))
        return spec["name"], program, [[(G_INSTRUMENT, self.wave_instruments[program])]]

    def build(self, recordings=None):
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
        for program in WAVES if recordings else ():
            before = self.position
            presets.append(self.waves(program, recordings))
            print(f"{program:3d} {WAVES[program]['name']:22s} {(self.position - before) * 2 / 1e6:5.2f} Mo")
        presets.sort(key=lambda preset: preset[1])
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
    Bank(sys.argv[1]).build(Path(sys.argv[2]) if len(sys.argv) > 2 else None)
