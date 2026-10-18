"""OpenEar ZETA: a separate right-ear specimen, at its original physical scale.

Twelve fine structures retain every source vertex and triangle. Only the temporal
bone context is simplified, with topology preservation and independent checks.
The display rotation is common to all objects, not a registration to BodyParts3D.
Run ``python -B tools/anatomy/ear_content.py --prepare`` to populate the cache.
"""
import hashlib
import io
import json
from pathlib import Path
import sys
sys.dont_write_bytecode = True
import urllib.request
import zipfile
import numpy as np

SOURCE_URL = 'https://zenodo.org/api/records/1473724/files/ZETA.zip/content'
SOURCE_BYTES = 3868308936
SOURCE_MD5 = '66fc062426086d2757e837593fd548e5'
LICENSE_URL = 'https://creativecommons.org/licenses/by/4.0/'
MANIFEST = Path(__file__).with_name('ear_registration.json')
REFERENCES = [
    'https://zenodo.org/records/1473724',
    'https://pmc.ncbi.nlm.nih.gov/articles/PMC6326113/',
    'https://www.nidcd.nih.gov/health/how-do-we-hear',
    'https://openstax.org/books/anatomy-and-physiology-2e/pages/14-1-sensory-perception',
]

# Source file | English | French | layer | category | Wikipedia EN | FR
_TERMS = '''
01_Scala Tympani.ply|Scala tympani|Rampe tympanique|senses|ear_inner|Scala tympani|Rampe tympanique
02_Scala Vestibuli.ply|Scala vestibuli and vestibular spaces|Rampe vestibulaire et espaces vestibulaires|senses|ear_inner|Bony labyrinth|Labyrinthe osseux
03_Malleus.ply|Malleus|Marteau|skeleton|ear_ossicles|Malleus|Marteau (anatomie)
04_Incus.ply|Incus|Enclume|skeleton|ear_ossicles|Incus|Enclume (anatomie)
05_Stapes.ply|Stapes|Étrier|skeleton|ear_ossicles|Stapes|Étrier (anatomie)
06_Facial Nerve.ply|Facial nerve · temporal segment|Nerf facial · portion temporale|nervous|ear_nerves|Facial nerve|Nerf facial
07_Chorda Tympani.ply|Chorda tympani|Corde du tympan|nervous|ear_nerves|Chorda tympani|Corde du tympan
08_Cochleovestibular Nerve.ply|Vestibulocochlear nerve · local segment|Nerf vestibulocochléaire · portion locale|nervous|ear_nerves|Vestibulocochlear nerve|Nerf vestibulocochléaire
09_Tympanic Membrane.ply|Tympanic membrane|Membrane tympanique|senses|ear_middle|Eardrum|Tympan (anatomie)
10_External Auditory Canal.ply|External auditory canal · space|Conduit auditif externe · espace|senses|ear_external|Ear canal|Conduit auditif externe
11_Sinus Dura.ply|Sigmoid sinus and dura · source group|Sinus sigmoïde et dure-mère · ensemble source|connective|ear_dural_context|Sigmoid sinus|Sinus sigmoïde
12_Carotis Interna.ply|Internal carotid artery · local segment|Artère carotide interne · portion locale|vascular|ear_vessels|Internal carotid artery|Artère carotide interne
Bone.ply|Temporal bone · specimen context|Os temporal · contexte du spécimen|skeleton|ear_temporal_context|Temporal bone|Os temporal
'''
TERMS = [line.split('|') for line in _TERMS.strip().splitlines()]
GROUPS = {
    'ear_inner': ('Inner ear', 'Oreille interne'),
    'ear_ossicles': ('Auditory ossicles', 'Osselets'),
    'ear_nerves': ('Local nerves', 'Nerfs locaux'),
    'ear_middle': ('Tympanic membrane', 'Membrane tympanique'),
    'ear_external': ('External auditory canal', 'Conduit auditif externe'),
    'ear_dural_context': ('Dura and venous sinus', 'Dure-mère et sinus veineux'),
    'ear_vessels': ('Local artery', 'Artère locale'),
    'ear_temporal_context': ('Temporal bone context', 'Os temporal de contexte'),
}
SUMMARIES = {
    '01': (
        'Perilymphatic compartment of the cochlea extending towards the round window. Its fluid transmits pressure changes associated with sound. This surface represents the compartment; hair cells and the organ of Corti are not separately modelled.',
        'Compartiment périlymphatique de la cochlée se prolongeant vers la fenêtre ronde. Son liquide transmet les variations de pression associées au son. La surface représente le compartiment ; les cellules ciliées et l’organe de Corti ne sont pas individualisés.'),
    '02': (
        'The source combines the scala vestibuli with the vestibule and semicircular canal spaces. The cochlear portion participates in sound transmission; the vestibular apparatus contributes to balance. Individual membranous ducts and sensory patches are not resolved.',
        'Le maillage source regroupe la rampe vestibulaire, le vestibule et les espaces des canaux semi-circulaires. La portion cochléaire participe à la transmission sonore ; l’appareil vestibulaire contribue à l’équilibre. Les conduits membraneux et les plages sensorielles ne sont pas individualisés.'),
    '03': (
        'Auditory ossicle coupled to the tympanic membrane. It passes mechanical vibration to the incus. Its geometry and position relative to the other ossicles come from the same right-ear specimen.',
        'Osselet lié à la membrane tympanique. Il transmet les vibrations mécaniques à l’enclume. Sa géométrie et sa position par rapport aux autres osselets proviennent du même spécimen d’oreille droite.'),
    '04': (
        'Middle ossicle of the chain, articulating with the malleus and stapes. It transfers vibration through the middle ear; the microscopic joint tissues are not separately represented.',
        'Osselet intermédiaire de la chaîne, articulé avec le marteau et l’étrier. Il transmet les vibrations à travers l’oreille moyenne ; les tissus microscopiques des articulations ne sont pas représentés séparément.'),
    '05': (
        'The stapes receives vibration from the incus. Its footplate interfaces with the oval window, transmitting pressure to inner-ear fluid. The annular ligament is not separately segmented.',
        'L’étrier reçoit les vibrations de l’enclume. Sa platine est en rapport avec la fenêtre ovale et transmet la pression au liquide de l’oreille interne. Le ligament annulaire n’est pas segmenté séparément.'),
    '06': (
        'Local course of cranial nerve VII through the temporal region. The complete facial nerve also carries motor fibres to facial muscles and other functional fibres; those distant branches are outside this specimen.',
        'Trajet local du nerf crânien VII dans la région temporale. Le nerf facial complet transporte notamment des fibres motrices vers les muscles du visage et d’autres fibres fonctionnelles ; ses branches éloignées sont hors du spécimen.'),
    '07': (
        'Branch of the facial nerve crossing the middle ear. It carries taste information from the anterior tongue and parasympathetic fibres towards salivary glands. Only its course within the sampled temporal region is shown.',
        'Branche du nerf facial traversant l’oreille moyenne. Elle transporte des informations gustatives de la partie antérieure de la langue et des fibres parasympathiques vers des glandes salivaires. Seul son trajet dans la région temporale prélevée est montré.'),
    '08': (
        'Local segment of cranial nerve VIII carrying hearing and balance information towards the brainstem. Cochlear and vestibular divisions are grouped in this source surface, not independently selectable fascicles.',
        'Portion locale du nerf crânien VIII conduisant vers le tronc cérébral les informations de l’audition et de l’équilibre. Les divisions cochléaire et vestibulaire sont regroupées dans cette surface source, sans faisceaux sélectionnables individuellement.'),
    '09': (
        'Membrane separating the external auditory canal from the middle ear. Sound pressure makes it vibrate and drives the malleus. Its layered microscopic structure is not resolved; transparency is an illustrative display choice.',
        'Membrane séparant le conduit auditif externe de l’oreille moyenne. La pression sonore la fait vibrer et entraîne le marteau. Sa structure microscopique en couches n’est pas résolue ; la transparence est un choix de représentation.'),
    '10': (
        'Segmented space of the external auditory canal leading towards the tympanic membrane. This translucent surface outlines the canal lumen, not a separate solid organ or a detailed skin layer.',
        'Espace segmenté du conduit auditif externe menant vers la membrane tympanique. Cette surface translucide délimite la lumière du conduit, sans représenter un organe plein ni une couche cutanée détaillée.'),
    '11': (
        'Combined source segmentation of the venous sinus and adjacent dura around the temporal specimen. These are different anatomical tissues retained as one source group; their boundary is not separately labelled in the mesh.',
        'Segmentation source combinant le sinus veineux et la dure-mère voisine autour du prélèvement temporal. Ces tissus anatomiques différents restent réunis dans un ensemble source ; leur limite n’est pas individualisée dans le maillage.'),
    '12': (
        'Local course of the internal carotid artery near the temporal bone. The red display colour is illustrative. The specimen does not contain the complete arterial pathway or microscopic vessel-wall layers.',
        'Trajet local de l’artère carotide interne près de l’os temporal. Le rouge est une couleur illustrative. Le spécimen ne contient ni le trajet artériel complet ni les couches microscopiques de la paroi.'),
    'Bo': (
        'Sampled temporal bone surrounding the ear structures, including its porous internal anatomy. The surfaces are simplified for mobile display, with finer detail retained on the small ear structures. The bone is a cropped specimen, not a complete skull.',
        'Prélèvement d’os temporal entourant les structures de l’oreille, avec son anatomie interne poreuse. Les surfaces sont simplifiées pour l’affichage mobile, avec davantage de détails conservés sur les petits éléments de l’oreille. Il s’agit d’un prélèvement limité, pas d’un crâne complet.'),
}


def read_ply(path):
    raw = Path(path).read_bytes()
    end = raw.index(b'end_header\n') + len(b'end_header\n')
    header = raw[:end].decode('ascii').splitlines()
    assert header[1] == 'format binary_little_endian 1.0'
    nv = int(next(line.split()[-1] for line in header if line.startswith('element vertex ')))
    nf = int(next(line.split()[-1] for line in header if line.startswith('element face ')))
    assert [s for s in header if s.startswith('property ')] == [
        'property float x', 'property float y', 'property float z', 'property list uchar int vertex_indices']
    v = np.frombuffer(raw, dtype='<f4', count=nv * 3, offset=end).reshape(-1, 3).astype(np.float64)
    packed = np.frombuffer(raw, dtype=np.dtype([('n', 'u1'), ('indices', '<i4', (3,))]),
                           count=nf, offset=end + nv * 12)
    assert (packed['n'] == 3).all() and end + nv * 12 + nf * 13 == len(raw)
    f = packed['indices'].copy()
    assert np.isfinite(v).all() and f.min() >= 0 and f.max() < len(v)
    return v, f


def build(source):
    manifest = json.loads(MANIFEST.read_text(encoding='utf8'))
    cache = Path(source) / 'ear/ZETA'
    rotation = np.array(manifest['displayRotation'])
    center = np.array(manifest['displayCenterMm'])
    assert np.allclose(rotation.T @ rotation, np.eye(3)) and np.linalg.det(rotation) > .999999
    items = []
    for filename, english, french, layer, category, wiki_en, wiki_fr in TERMS:
        info = manifest['files'][filename]
        raw_path = cache / filename
        assert hashlib.sha256(raw_path.read_bytes()).hexdigest() == info['sha256'], filename
        if filename == 'Bone.ply':
            reduced = cache / 'Bone_context.npz'
            assert hashlib.sha256(reduced.read_bytes()).hexdigest() == manifest['context']['cacheSha256']
            with np.load(reduced, allow_pickle=False) as data:
                v, f = data['vertices'], data['faces']
            assert len(f) == manifest['context']['contextTriangles']
        else:
            v, f = read_ply(raw_path)
            assert len(v) == info['vertices'] and len(f) == info['triangles'], filename
        key = filename[:2]
        color = {
            '01': [.29, .66, .87], '02': [.17, .43, .74],
            '03': [.94, .87, .68], '04': [.89, .82, .64], '05': [.96, .91, .76],
            '06': [.94, .77, .22], '07': [.96, .84, .40], '08': [.90, .67, .18],
            '09': [.79, .70, .82], '10': [.68, .51, .43], '11': [.48, .55, .68],
            '12': [.80, .20, .19], 'Bo': [.85, .80, .66],
        }[key]
        group_en, group_fr = GROUPS[category]
        summary_en, summary_fr = SUMMARIES[key]
        identity = 'openear_zeta_' + ('temporal_context' if key == 'Bo' else key)
        record = {
            'id': identity, 'sourceName': Path(filename).stem,
            'nameEn': english, 'nameFr': french, 'summaryEn': summary_en, 'summaryFr': summary_fr,
            'layer': layer, 'region': 'skull', 'category': category,
            'groupEn': group_en, 'groupFr': group_fr, 'wikiEn': wiki_en, 'wikiFr': wiki_fr,
            'source': 'OpenEar ZETA, right temporal bone, CC BY 4.0',
            'sourceObject': filename, 'elements': [filename], 'color': color,
            'standard': False, 'boneCount': 0,
        }
        if key in ('Bo', '11'):
            record['hiddenByDefault'] = True
        if key == '09': record['opacity'] = .52
        if key == '10': record['opacity'] = .14
        transformed = (v - center) @ rotation / 1000.
        assert np.isfinite(transformed).all() and f.min() >= 0 and f.max() < len(v)
        items.append((record, transformed, f))
    return items, {
        'source': 'OpenEar library, ZETA specimen', 'license': 'CC BY 4.0', 'licenseUrl': LICENSE_URL,
        'attribution': 'Sieber DM, Erfurt P, John S, Ribeiro dos Santos G, Schurzig D, Sørensen MS, Lenarz T. '
                       'The OpenEar library of 3D models of the human temporal bone based on computed tomography '
                       'and micro-slicing (2019). DOI:10.1038/sdata.2018.297; dataset DOI:10.5281/zenodo.1473724. CC BY 4.0.',
        'sourceArchive': {'url': SOURCE_URL, 'bytes': SOURCE_BYTES, 'publishedMd5': SOURCE_MD5,
                          'verification': 'ZIP members extracted with CRC checks and individually pinned SHA-256; full multi-GB archive not downloaded.'},
        'laterality': 'Right temporal bone: primary archive folder 01_CBCT_Unembedded/DICOM_rechts_20150826_X. No mirroring.',
        'units': 'Source PLY coordinates in millimetres; one common rigid display transform then conversion to metres. No scale fitting.',
        'registration': 'Separate local specimen, not registered to the BodyParts3D body. Display axes do not assert clinical whole-head orientation.',
        'displayTransform': {'rotation': rotation.tolist(), 'centerMm': center.tolist()},
        'sourceFiles': manifest['files'], 'temporalContext': manifest['context'],
        'modifications': 'All twelve fine PLY surfaces retain all original vertices, indices, winding and mutual distances. '
                         'One shared translation/rotation and unit conversion. Illustrative colours and selected transparency. '
                         'Only temporal bone context is simplified; no fabricated ear structures or contralateral mirror.',
        'limitations': 'One cropped cadaveric specimen, not a complete ear or clinical reference standard. The scala vestibuli mesh includes '
                       'vestibular spaces as one source group. The sinus/dura mesh combines tissues. No independent organ of Corti, '
                       'hair cells, membranous labyrinth, ossicular ligaments, stapedius or tensor tympani, Eustachian tube or pinna. '
                       'Source preparation, segmentation and smoothing limit accuracy; no sound propagation or surgical simulation.',
        'references': REFERENCES,
        'defaultLayers': ['skeleton', 'senses', 'nervous', 'vascular', 'connective'],
    }


class _RemoteZip(io.RawIOBase):
    def __init__(self):
        self.position = 0

    def seekable(self): return True
    def tell(self): return self.position
    def seek(self, offset, whence=0):
        self.position = offset if whence == 0 else self.position + offset if whence == 1 else SOURCE_BYTES + offset
        return self.position

    def read(self, count=-1):
        count = SOURCE_BYTES - self.position if count < 0 else min(count, SOURCE_BYTES - self.position)
        if count == 0: return b''
        start, end = self.position, self.position + count - 1
        assert 0 <= start <= end < SOURCE_BYTES and count < 128 * 1024 * 1024
        request = urllib.request.Request(SOURCE_URL, headers={'Range': f'bytes={start}-{end}'})
        with urllib.request.urlopen(request, timeout=120) as response:
            assert response.status == 206 and response.headers['Content-Range'].startswith(f'bytes {start}-{end}/')
            data = response.read(count + 1)
        assert len(data) == count
        self.position += count
        return data


def prepare(source):
    """Reproducible cache preparation; PyMeshLab 2025.7 only needed for the context."""
    manifest = json.loads(MANIFEST.read_text(encoding='utf8'))
    cache = Path(source) / 'ear/ZETA'
    cache.mkdir(parents=True, exist_ok=True)
    missing = [name for name, info in manifest['files'].items()
               if not (cache / name).exists() or hashlib.sha256((cache / name).read_bytes()).hexdigest() != info['sha256']]
    if missing:
        with zipfile.ZipFile(_RemoteZip()) as archive:
            for name in missing:
                raw = archive.read('07_3D_Models/' + name)
                assert hashlib.sha256(raw).hexdigest() == manifest['files'][name]['sha256']
                (cache / name).write_bytes(raw)
    reduced = cache / 'Bone_context.npz'
    if reduced.exists() and hashlib.sha256(reduced.read_bytes()).hexdigest() == manifest['context']['cacheSha256']:
        return
    sys.path.insert(0, str(Path(source) / 'ear/python'))
    import pymeshlab
    ms = pymeshlab.MeshSet()
    ms.load_new_mesh(str(cache / 'Bone.ply'))
    ms.meshing_decimation_quadric_edge_collapse(**manifest['context']['parameters'])
    ms.meshing_remove_unreferenced_vertices()
    mesh = ms.current_mesh()
    np.savez_compressed(reduced, vertices=mesh.vertex_matrix(), faces=mesh.face_matrix())
    assert hashlib.sha256(reduced.read_bytes()).hexdigest() == manifest['context']['cacheSha256'], 'Context must match the reviewed result'


if __name__ == '__main__':
    source = Path(__file__).resolve().parents[2] / 'app/build/anatomy-source'
    if '--prepare' in sys.argv: prepare(source)
    items, provenance = build(source)
    print(json.dumps({'structures': len(items), 'triangles': sum(len(f) for _, _, f in items),
                      'licence': provenance['license']}, indent=2))
