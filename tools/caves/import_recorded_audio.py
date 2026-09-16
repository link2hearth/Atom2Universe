"""Prepare licensed recordings; never synthesizes effects or changes the approved shotgun.

Requires numpy and soundfile. Download with fetch_audio_sources.py, then extract with
inspect_audio_sources.py. Original inputs stay in .audio-work (not shipped).
"""
from pathlib import Path
import sys
import json
import hashlib
sys.path.insert(0, str(Path(__file__).parent / '.audio-work/libs'))
import numpy as np
import soundfile as sf

HERE = Path(__file__).parent
WORK = HERE / '.audio-work'
OUT = HERE.parents[1] / 'app/src/main/assets/caves/audio'
SOURCES = {
    'impact': ('Kenney', 'Impact Sounds', 'https://kenney.nl/assets/impact-sounds', 'CC0-1.0'),
    'firearms': ('Ben Jaszczak, Brian Nelson, Kevin Heras, Matthew Nanney', 'The Free Firearm Sound Library',
                 'https://opengameart.org/content/the-free-firearm-sound-library', 'CC0-1.0'),
    'farm': ('Secretlondon', 'Farm animals / Mudchute City Farm, London',
             'https://opengameart.org/content/farm-animals', 'CC-BY-SA-3.0'),
    'sheep': ('mikewest; edited by AntumDeluge', 'Sheep Baa',
              'https://opengameart.org/content/sheep-baa', 'CC0-1.0'),
    'chicken': ('IMadeIt', 'Chicken Sound Effect',
                'https://opengameart.org/content/chicken-sound-effect', 'CC-BY-3.0'),
    'bow': ('artisticdude', 'Battle Sound Effects',
            'https://opengameart.org/content/battle-sound-effects', 'CC0-1.0'),
    'crossbow': ('spookymodem', 'Crossbow Shot',
                 'https://opengameart.org/content/crossbow-shot', 'CC-BY-3.0'),
}
LICENSES = {'CC0-1.0': 'https://creativecommons.org/publicdomain/zero/1.0/',
            'CC-BY-3.0': 'https://creativecommons.org/licenses/by/3.0/',
            'CC-BY-SA-3.0': 'https://creativecommons.org/licenses/by-sa/3.0/'}
manifest = []
prepared = {}


def prepare(name, source, relative, start=0, end=None, peak=.78):
    path = WORK / source / relative
    data, rate = sf.read(path, always_2d=True)
    data = data[int(start * rate):int(end * rate) if end is not None else None].mean(axis=1)
    assert len(data) and np.isfinite(data).all(), name
    data -= data.mean()
    # Windowed-sinc low-pass before resampling, including high-rate firearm recordings.
    target_rate = 22050
    if rate > target_rate:
        n = np.arange(-64, 65)
        cutoff = .46 * target_rate / rate
        kernel = 2 * cutoff * np.sinc(2 * cutoff * n) * np.hamming(len(n))
        data = np.convolve(data, kernel / kernel.sum(), mode='same')
    data = np.interp(np.arange(round(len(data) * target_rate / rate)) * rate / target_rate,
                     np.arange(len(data)), data)
    # Remove leading/trailing dead air only; keep natural timbre and tails.
    active = np.flatnonzero(np.abs(data) > max(np.max(np.abs(data)) * .008, .0001))
    assert len(active), name
    data = data[max(0, active[0] - 66):min(len(data), active[-1] + 220)]
    data *= peak / max(np.max(np.abs(data)), .001)
    if name.startswith('step_'):
        # Soften sole/gravel clicks without boosting the filtered result back up.
        n = np.arange(-64, 65)
        cutoff = 1200 / target_rate
        kernel = 2 * cutoff * np.sinc(2 * cutoff * n) * np.hamming(len(n))
        data = np.convolve(data, kernel / kernel.sum(), mode='same')
    fade_in = min(22, len(data) // 4)
    fade_out = min(330, len(data) // 4)
    data[:fade_in] *= np.linspace(0, 1, fade_in)
    data[-fade_out:] *= np.linspace(1, 0, fade_out)
    assert 0 < len(data) / target_rate < 3.5 and np.max(np.abs(data)) <= .90
    prepared[name] = data
    author, title, url, license_id = SOURCES[source]
    manifest.append(dict(file=name + '.wav', author=author, title=title, source=url,
                         license=license_id, license_url=LICENSES[license_id],
                         original=relative, input_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                         selection_seconds=[start, end], peak=peak,
                         modifications='Excerpt; mono; anti-aliased 22050 Hz conversion; DC removal; gain; edge fades.'
                         + (' Footsteps: 1200 Hz low-pass, no makeup gain.' if name.startswith('step_') else ''),
                         duration_seconds=round(len(data) / target_rate, 5)))


def main():
    shotgun = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in OUT.glob('shotgun_*.wav')}
    assert len(shotgun) == 3
    for surface, original in [('stone', 'concrete'), ('earth', 'grass'), ('wood', 'wood')]:
        prepare(f'step_{surface}_0', 'impact', f'extracted/Audio/footstep_{original}_000.ogg', peak=.56)
    for name, family, selections, duration in [
        ('gun', '1911', [('A_34P.wav', 1.53), ('A_34P.wav', 6.64), ('A_42P.wav', .93)], .45),
        ('smg', 'Carl Gustav M45', [('G_20P.wav', .33), ('G_20P.wav', 2.24), ('G_20P.wav', 5.25)], .20),
        ('lever_rifle', 'Marlin 336', [('I_17P.wav', .59), ('I_17P.wav', 6.83), ('I_22P.wav', .73)], .60),
    ]:
        for i, (file, start) in enumerate(selections):
            prepare(f'{name}_{i}', 'firearms', f'extracted/Prepared SFX Library/{family}/{file}', start, start + duration)
    prepare('bow', 'bow', 'extracted/battle_sound_effects/Bow.wav', peak=.70)
    prepare('sling', 'bow', 'extracted/battle_sound_effects/swish_4.wav', peak=.60)
    # The source includes a later target impact: keep only the release.
    prepare('crossbow', 'crossbow', 'Crossbow Shot.wav', .27, .51, peak=.72)
    prepare('animal_cow_0', 'farm', 'extracted/MudchuteAnimals/Mudchute_cow_1.ogg', peak=.70)
    prepare('animal_cow_1', 'farm', 'extracted/MudchuteAnimals/Mudchute_cow_1.ogg', peak=.65)
    for i in range(2):
        prepare(f'animal_pig_{i}', 'farm', f'extracted/MudchuteAnimals/Mudchute_pig_{i+1}.ogg', peak=.65)
    prepare('animal_sheep_0', 'sheep', 'sheep_baa.flac', peak=.68)
    prepare('animal_sheep_1', 'farm', 'extracted/MudchuteAnimals/Mudchute_sheep_1.ogg', peak=.68)
    prepare('animal_chicken_0', 'chicken', 'extracted/Chicken Sound Effect.ogg', .18, .56, peak=.62)
    prepare('animal_chicken_1', 'chicken', 'extracted/Chicken Sound Effect.ogg', 1.04, 1.55, peak=.62)
    prepare('hurt', 'impact', 'extracted/Audio/impactPunch_heavy_000.ogg', peak=.65)
    prepare('boss', 'impact', 'extracted/Audio/impactWood_heavy_000.ogg', peak=.65)
    for surface in ('stone', 'earth', 'wood'):
        source_name = f'step_{surface}_0'
        original = prepared[source_name]
        # Isolate a single sole contact; the full grass take contains a long scuff.
        peak_at = int(np.argmax(np.abs(original)))
        start = max(0, peak_at - 220)
        contact = original[start:start + 2646].copy()  # At most 120 ms.
        contact[:44] *= np.linspace(0, 1, 44)
        contact[-441:] *= np.linspace(1, 0, 441)
        loop = np.zeros(10143)  # Exactly 460 ms at 22050 Hz; one contact per cycle.
        loop[:len(contact)] = contact
        name = f'step_{surface}_loop'
        prepared[name] = loop
        row = next(r for r in manifest if r['file'] == source_name + '.wav').copy()
        row.update(file=name + '.wav', duration_seconds=.46,
                   modifications=row['modifications'] + f' Single contact at processed sample {start}, max 120 ms; faded into a 460 ms loop.')
        manifest.append(row)
        # The isolated source is only an intermediate: ship the playable loop alone.
        del prepared[source_name]
        manifest[:] = [r for r in manifest if r['file'] != source_name + '.wav']
    for name, data in prepared.items():
        sf.write(OUT / (name + '.wav'), data, 22050, subtype='PCM_16')
    for row in manifest:
        row['sha256'] = hashlib.sha256((OUT / row['file']).read_bytes()).hexdigest()
    (OUT / 'sources.json').write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    assert shotgun == {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in OUT.glob('shotgun_*.wav')}
    expected = {name + '.wav' for name in prepared} | set(shotgun)
    actual = {p.name for p in OUT.glob('*.wav')}
    assert actual == expected, f'Unexpected or missing WAVs: {actual ^ expected}'
    print(f'Imported {len(prepared)} recorded effects. All three shotgun hashes unchanged.')


if __name__ == '__main__':
    main()
