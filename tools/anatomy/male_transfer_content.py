"""Transfer the stomach and oesophagus of the packaged male atlas into the female atlas.

The Human Reference Atlas supplies neither organ. The male BodyParts3D surfaces are
placed with an affine fit on 28 organs and vertebrae present in both bodies (liver,
spleen, duodenum, pancreas, kidneys, heart, trachea, lungs, T1 to T12...), blended
between an abdominal and a thoracic fit across the diaphragm. Triangles are unchanged.
"""
import hashlib
import json
from pathlib import Path
import subprocess

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
MALE_PACKAGE = ROOT / 'app/src/main/assets/science/biology/atlas-000.part.gzip'
DECODER = Path(__file__).with_name('decode_male.mjs')
T_MALE = ['FMA9165', 'FMA9187', 'FMA9209', 'FMA9248', 'FMA9922', 'FMA9945', 'FMA9968', 'FMA9991', 'FMA10014', 'FMA10037', 'FMA10059', 'FMA10081']
T_FEMALE = ['HRAF_%d' % i for i in range(1054, 1066)]
# (name, male ids, female ids, mode): 'box' uses centroid and extents, 'c' the centroid only.
PAIRS = [('liver', ['FMA7197'], ['HRAF_521'], 'box'), ('spleen', ['FMA7196'], ['HRAF_945'], 'box'),
         ('duodenum', ['FMA7206'], ['HRAF_499'], 'box'), ('pancreas', ['FMA7198'], ['HRAF_554'], 'box'),
         ('left kidney', ['FMA7205'], ['HRAF_565'], 'box'), ('right kidney', ['FMA7204'], ['HRAF_596'], 'box'),
         ('gallbladder', ['FMA7202'], ['HRAF_560'], 'c'), ('transverse colon', ['FMA14546'], ['HRAF_488'], 'box'),
         ('heart', ['FMA7088'], ['HRAF_675', 'HRAF_676', 'HRAF_677', 'HRAF_678', 'HRAF_679'], 'box'),
         ('trachea', ['FMA7394'], ['HRAF_897'], 'box'),
         ('left lower lung', ['ZAN_inferior_lobe_of_left_lung'], ['HRAF_860'], 'box'),
         ('right lower lung', ['ZAN_inferior_lobe_of_right_lung'], ['HRAF_867'], 'box'),
         ('lumbar block', ['FMA13072', 'FMA13073', 'FMA13074', 'FMA13075', 'FMA13076'], ['HRAF_1048'], 'c'),
         ('C7', ['FMA12525'], ['HRAF_1072'], 'c')]
PAIRS += [('T%d' % (i + 1), [m], [f], 'c') for i, (m, f) in enumerate(zip(T_MALE, T_FEMALE))]
ABDOMEN = ['liver', 'spleen', 'duodenum', 'pancreas', 'left kidney', 'right kidney', 'gallbladder', 'transverse colon',
           'left lower lung', 'right lower lung', 'lumbar block', 'T9', 'T10', 'T11', 'T12']
THORAX = ['heart', 'trachea', 'left lower lung', 'right lower lung', 'C7', 'liver'] + ['T%d' % i for i in range(1, 13)]
TRANSFERS = [
    dict(male='FMA7148', id='BP3D_FEMALE_STOMACH', nameEn='Stomach', nameFr='Estomac', region='abdomen', note='stomach',
         color=[.86, .56, .44]),
    dict(male='FMA7131', id='BP3D_FEMALE_ESOPHAGUS', nameEn='Oesophagus', nameFr='Œsophage', region='thorax', note='esophagus',
         color=[.82, .64, .52]),
]
NOTES = {
    'stomach': ('The stomach receives food from the oesophagus, mixes it with acid and enzymes and releases it gradually into the duodenum. '
                'This surface comes from the male BodyParts3D reference and was placed between the liver, spleen and duodenum of the female atlas.',
                'L’estomac reçoit les aliments venus de l’œsophage, les mélange aux acides et aux enzymes, puis les libère progressivement dans le duodénum. '
                'Cette surface provient de la référence masculine BodyParts3D et a été placée entre le foie, la rate et le duodénum de l’atlas féminin.',
                'Stomach', 'Estomac'),
    'esophagus': ('The oesophagus carries swallowed food from the pharynx to the stomach, passing behind the trachea and heart. '
                  'This surface comes from the male BodyParts3D reference and was aligned on the female spine, trachea and heart.',
                  'L’œsophage conduit les aliments avalés du pharynx à l’estomac, en passant derrière la trachée et le cœur. '
                  'Cette surface provient de la référence masculine BodyParts3D et a été alignée sur la colonne, la trachée et le cœur féminins.',
                  'Esophagus', 'Œsophage'),
}


def male_meshes(source):
    """Decode the packaged (Draco) male atlas once into raw arrays."""
    folder = Path(source) / 'male-packaged'
    index, data = folder / 'male_meshes.json', folder / 'male_meshes.bin'
    package_sha = hashlib.sha256(MALE_PACKAGE.read_bytes()).hexdigest()
    stamp = folder / 'source.sha256'
    if not (index.exists() and data.exists() and stamp.exists() and stamp.read_text().strip() == package_sha):
        folder.mkdir(parents=True, exist_ok=True)
        subprocess.run(['node', str(DECODER), str(MALE_PACKAGE), str(folder)], check=True)
        stamp.write_text(package_sha)
    rows = json.loads(index.read_text(encoding='utf8'))
    raw = np.fromfile(data, dtype=np.uint8)
    meshes = {}
    for m in rows:
        v = raw[m['posOffset']:m['posOffset'] + m['vertices'] * 12].view('<f4').reshape(-1, 3).astype(np.float64)
        f = raw[m['idxOffset']:m['idxOffset'] + m['faces'] * 12].view('<u4').reshape(-1, 3).astype(np.int64)
        meshes[m['name']] = (v, f)
    return meshes, package_sha


def _points(v, mode):
    c = v.mean(0)
    if mode == 'c':
        return c[None]
    half = (v.max(0) - v.min(0)) / 2
    return np.vstack([c] + [c + np.eye(3)[k] * half[k] * sgn for k in range(3) for sgn in (1, -1)])


def _fit(names, male, female):
    src, dst = [], []
    for name, mids, fids, mode in PAIRS:
        if name not in names:
            continue
        mv = np.concatenate([male[i][0] for i in mids]); fv = np.concatenate([female[i] for i in fids])
        src.append(_points(mv, mode)); dst.append(_points(fv, mode))
    src, dst = np.concatenate(src), np.concatenate(dst)
    M, *_ = np.linalg.lstsq(np.hstack([src, np.ones((len(src), 1))]), dst, rcond=None)
    return M


def _apply(M, v):
    return np.hstack([v, np.ones((len(v), 1))]) @ M


def build(source, items):
    """Return ([(record, vertices, faces)], report) for the transferred organs, in HRA space."""
    female = {record['id']: v for record, v, _ in items}
    male, package_sha = male_meshes(source)
    M_abd, M_tho = _fit(ABDOMEN, male, female), _fit(THORAX, male, female)
    top = male['FMA7148'][0][:, 1].max()
    y_lo, y_hi = top - 0.03, top + 0.08

    def blended(v):
        w = np.clip((v[:, 1] - y_lo) / (y_hi - y_lo), 0, 1)[:, None]
        return (1 - w) * _apply(M_abd, v) + w * _apply(M_tho, v)

    residuals = {}
    for name, mids, fids, mode in PAIRS:
        mv = np.concatenate([male[i][0] for i in mids]); fv = np.concatenate([female[i] for i in fids])
        d = blended(mv.mean(0, keepdims=True))[0] - fv.mean(0)
        residuals[name] = round(float(np.linalg.norm(d)) * 1000, 1)
    result = []
    for t in TRANSFERS:
        v, f = male[t['male']]
        note = NOTES[t['note']]
        record = dict(id=t['id'], sourceName=t['nameEn'].lower(), sourceObject=t['male'], source='bodyparts3d-4.0-via-male-atlas',
                      transferred=True, elements=[t['male']], nameEn=t['nameEn'], nameFr=t['nameFr'],
                      summaryEn=note[0], summaryFr=note[1], wikiEn=note[2], wikiFr=note[3], layer='organs', region=t['region'],
                      category='female_digestive', groupEn='Digestive organs', groupFr='Organes digestifs',
                      organSystem='digestive', color=t['color'], standard=False, boneCount=0)
        result.append((record, blended(v), f))
    report = {'method': ('Affine placement of the male BodyParts3D stomach and oesophagus using organs and vertebrae present in '
                         'both atlases; an abdominal and a thoracic fit are blended across the diaphragm. Triangles unchanged.'),
              'malePackageSha256': package_sha, 'landmarkPairs': len(PAIRS), 'centroidResidualsMm': residuals,
              'abdominalLandmarks': ABDOMEN, 'thoracicLandmarks': THORAX, 'blendHeightsMetres': [round(y_lo, 4), round(y_hi, 4)],
              'generatorSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    return result, report
