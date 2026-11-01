"""Reshape the female atlas into a slim mannequin envelope supplied by the project owner.

The Human Reference Atlas organs keep their topology and relative arrangement; only
their positions change. Each horizontal slice of the HRA skin is matched to the same
slice of the mannequin skin: a point is expressed as (angle, radius / skin radius at
that angle) around the trunk or leg centre, then re-expressed in the mannequin at the
height mapped through five landmarks (soles, crotch, armpits, neck, head top). Arms
carry no structures. Breast surface parts are kept inside the new skin.
"""
import hashlib
import json
from pathlib import Path

import numpy as np
from scipy.ndimage import gaussian_filter1d
from scipy.sparse import coo_matrix
from scipy.sparse.csgraph import connected_components
from scipy.spatial import cKDTree

REGISTRATION = Path(__file__).with_name('female_envelope_registration.json')
ANGLES = 72
STEP = 0.01
SKIN_ID = 'A2U_FEMALE_SKIN'
SKIN_NOTE = ('Skin surface of a slim adult female mannequin generated with Tripo AI from a text description, scaled to the '
             'height of the reference body. It gives the female atlas a complete silhouette; it is not a scan of a person.',
             'Surface cutanée d’un mannequin féminin adulte mince, généré avec Tripo AI à partir d’une description textuelle '
             'et mis à l’échelle du corps de référence. Il donne à l’atlas féminin une silhouette complète ; ce n’est pas la '
             'numérisation d’une personne.', 'Human_skin', 'Peau_humaine')


def load_obj(path):
    vertices, faces = [], []
    with open(path, 'r', encoding='utf-8', errors='replace') as handle:
        for line in handle:
            if line.startswith('v '):
                p = line.split()
                vertices.append((float(p[1]), float(p[2]), float(p[3])))
            elif line.startswith('f '):
                idx = [int(t.split('/')[0]) - 1 for t in line.split()[1:]]
                for k in range(1, len(idx) - 1):
                    faces.append((idx[0], idx[k], idx[k + 1]))
    return np.asarray(vertices, dtype=np.float64), np.asarray(faces, dtype=np.int64)


def load_mannequin(folder):
    registration = json.loads(REGISTRATION.read_text(encoding='utf8'))
    path = Path(folder) / registration['file']
    if not path.is_file():
        path = Path(folder) / 'female' / registration['file']
    assert hashlib.sha256(path.read_bytes()).hexdigest() == registration['sha256'], 'Unexpected mannequin file'
    v, f = load_obj(path)
    span = v.max(0) - v.min(0)
    assert np.argmax(span) == 1, 'Mannequin must stand along +Y'
    v = v * (registration['heightMetres'] / span[1])
    v[:, 1] -= v[:, 1].min()
    v[:, [0, 2]] -= (v[:, [0, 2]].min(0) + v[:, [0, 2]].max(0)) / 2
    return v, f, registration


def _components(points, radius=0.025):
    tree = cKDTree(points)
    pairs = tree.query_pairs(radius, output_type='ndarray')
    if len(pairs) == 0:
        return np.arange(len(points))
    m = coo_matrix((np.ones(len(pairs)), (pairs[:, 0], pairs[:, 1])), shape=(len(points), len(points)))
    return connected_components(m, directed=False)[1]


def _polar_profile(pts, centre):
    d = pts - centre
    theta = np.arctan2(d[:, 1], d[:, 0])
    rho = np.hypot(d[:, 0], d[:, 1])
    bins = ((theta + np.pi) / (2 * np.pi) * ANGLES).astype(int) % ANGLES
    r = np.full(ANGLES, np.nan)
    for b in range(ANGLES):
        sel = rho[bins == b]
        if len(sel):
            r[b] = np.percentile(sel, 90)
    if np.isnan(r).any():
        ok = ~np.isnan(r)
        idx = np.arange(ANGLES)
        r = np.interp(idx, idx[ok], r[ok], period=ANGLES)
    return r


class Tube:
    """Per-height centre and polar radius table of one body tube."""

    def __init__(self, ys, centres, radii):
        self.ys, self.centres, self.radii = np.asarray(ys), np.asarray(centres), np.asarray(radii)

    def smooth(self, sigma=2.0):
        self.centres = gaussian_filter1d(self.centres, sigma, axis=0, mode='nearest')
        self.radii = gaussian_filter1d(self.radii, sigma, axis=0, mode='nearest')
        return self

    def at(self, y):
        y = np.clip(y, self.ys[0], self.ys[-1])
        pos = np.interp(y, self.ys, np.arange(len(self.ys)))
        i0 = np.clip(np.floor(pos).astype(int), 0, len(self.ys) - 1)
        i1 = np.clip(i0 + 1, 0, len(self.ys) - 1)
        w = (pos - i0)[:, None]
        return (1 - w) * self.centres[i0] + w * self.centres[i1], (1 - w) * self.radii[i0] + w * self.radii[i1]

    def radius_at(self, y, theta):
        centres, radii = self.at(y)
        pos = (theta + np.pi) / (2 * np.pi) * ANGLES
        b0 = np.floor(pos).astype(int) % ANGLES
        b1 = (b0 + 1) % ANGLES
        w = pos - np.floor(pos)
        rows = np.arange(len(y))
        return centres, (1 - w) * radii[rows, b0] + w * radii[rows, b1]


class Body:
    """Skin envelope analysed into trunk (with neck and head) and leg tubes, plus landmarks."""

    def __init__(self, v, name=''):
        self.name = name
        ymin, ymax = v[:, 1].min(), v[:, 1].max()
        ys = np.arange(ymin + STEP / 2, ymax, STEP)
        trunk, legs, arm_count = {}, {0: {}, 1: {}}, {}
        for y in ys:
            band = v[np.abs(v[:, 1] - y) <= STEP]
            if len(band) < 6:
                continue
            pts = band[:, [0, 2]]
            labels = _components(pts)
            comps = [(pts[labels == lab].mean(0), pts[labels == lab]) for lab in np.unique(labels) if (labels == lab).sum() >= 6]
            if not comps:
                continue
            mid = [c for c in comps if c[1][:, 0].min() < 0 < c[1][:, 0].max()]
            if mid:
                centre, sel = max(mid, key=lambda c: len(c[1]))
                trunk[y] = (centre, _polar_profile(sel, centre), sel[:, 0].min(), sel[:, 0].max())
                arm_count[y] = sum(1 for c in comps if c[1] is not sel)
            else:
                comps.sort(key=lambda c: -len(c[1]))
                for centre, sel in comps[:2]:
                    side = 0 if centre[0] < 0 else 1
                    if y not in legs[side]:
                        legs[side][y] = (centre, _polar_profile(sel, centre))
        self.trunk = self._tube(trunk)
        self.legs = [self._tube(legs[0]), self._tube(legs[1])]
        trunk_ys = np.array(sorted(trunk))
        self.sole, self.top, self.crotch = float(ymin), float(ymax), float(trunk_ys.min())
        with_arms = np.array([y for y in trunk_ys if arm_count.get(y, 0) >= 2])
        self.armpit = float(with_arms.max()) + STEP if len(with_arms) else self.crotch + 0.4 * (ymax - self.crotch)
        upper = [(y, trunk[y][3] - trunk[y][2]) for y in trunk_ys if y > self.armpit + 0.05]
        widths = np.array([w for _, w in upper]); uys = np.array([y for y, _ in upper])
        shoulder_w = widths[:max(1, len(widths) // 3)].max()
        narrow = uys[widths < 0.6 * shoulder_w]
        neck_base = float(narrow.min()) if len(narrow) else self.armpit + 0.2
        above = [(y, w) for y, w in zip(uys, widths) if y >= neck_base]
        self.neck = float(min(above, key=lambda t: t[1])[0]) if above else neck_base

    @staticmethod
    def _tube(table):
        ys = np.array(sorted(table))
        return Tube(ys, [table[y][0] for y in ys], [table[y][1] for y in ys]).smooth()

    def landmarks(self):
        return np.array([self.sole, self.crotch, self.armpit, self.neck, self.top])


class Warp:
    def __init__(self, source, target, blend=0.04):
        self.s, self.t, self.blend = source, target, blend
        self.ls, self.lt = source.landmarks(), target.landmarks()

    def height(self, y):
        return np.interp(y, self.ls, self.lt)

    def _map_tube(self, p, ts, tt, yt, clamp):
        cs, _ = ts.at(p[:, 1])
        d = p[:, [0, 2]] - cs
        theta = np.arctan2(d[:, 1], d[:, 0])
        rho = np.hypot(d[:, 0], d[:, 1])
        _, rs = ts.radius_at(p[:, 1], theta)
        ct, rt = tt.radius_at(yt, theta)
        rho_n = rho / np.maximum(rs, 1e-6)
        if clamp:
            rho_n = np.minimum(rho_n, 0.995)
        out = np.empty_like(p)
        out[:, 0] = ct[:, 0] + rho_n * rt * np.cos(theta)
        out[:, 1] = yt
        out[:, 2] = ct[:, 1] + rho_n * rt * np.sin(theta)
        return out, rho_n

    def apply(self, p, clamp=False):
        p = np.asarray(p, dtype=np.float64)
        yt = self.height(p[:, 1])
        out, rho = self._map_tube(p, self.s.trunk, self.t.trunk, yt, clamp)
        w = np.clip((p[:, 1] - self.s.crotch) / self.blend, 0, 1)
        low = w < 1
        if low.any():
            q = p[low]
            cs, _ = self.s.trunk.at(np.full(len(q), self.s.crotch))
            side = (q[:, 0] >= cs[:, 0]).astype(int)
            leg = np.empty_like(q); rl = np.empty(len(q))
            for k in (0, 1):
                sel = side == k
                if sel.any():
                    leg[sel], rl[sel] = self._map_tube(q[sel], self.s.legs[k], self.t.legs[k], yt[low][sel], clamp)
            ww = w[low][:, None]
            out[low] = ww * out[low] + (1 - ww) * leg
            rho[low] = np.where(w[low] > 0.5, rho[low], rl)
        return out, rho


def build_warp(source_folder, items):
    """Warp from the HRA skin found in items to the registered mannequin."""
    skin = [(record, v) for record, v, _ in items if record['layer'] == 'skin' and record.get('source') == 'hra-female-v1.5']
    assert len(skin) == 1, 'Expected exactly one HRA skin'
    man_v, man_f, registration = load_mannequin(source_folder)
    source, target = Body(skin[0][1], 'hra'), Body(man_v, 'mannequin')
    return Warp(source, target), (man_v, man_f), registration


def apply(source_folder, items):
    """Return reshaped items (mannequin skin replacing the HRA skin) and a provenance report."""
    warp, (man_v, man_f), registration = build_warp(source_folder, items)
    result, clamped = [], []
    for record, v, f in items:
        if record['layer'] == 'skin':
            continue
        clamp = record.get('category') == 'female_breast'
        w, _ = warp.apply(v, clamp=clamp)
        record = dict(record, reshaped=True)
        result.append((record, w, f))
        if clamp:
            clamped.append(record['id'])
    skin = dict(id=SKIN_ID, sourceName='mannequin skin', sourceObject=registration['file'], source='tripo-mannequin',
                elements=[SKIN_ID], nameEn='Skin', nameFr='Peau', summaryEn=SKIN_NOTE[0], summaryFr=SKIN_NOTE[1],
                wikiEn=SKIN_NOTE[2], wikiFr=SKIN_NOTE[3], layer='skin', region='body', category='female_skin',
                groupEn='Skin', groupFr='Peau', color=[.76, .55, .42], standard=False, boneCount=0)
    result.append((skin, man_v, man_f))
    names = ['sole', 'crotch', 'armpit', 'neck', 'top']
    report = {'method': ('Per-slice polar remapping of every HRA structure into the mannequin skin: angle and normalised radius '
                         'around the trunk or leg centre are preserved; heights are mapped through five landmarks. '
                         'Topology and triangle counts are unchanged; the HRA skin itself is replaced by the mannequin skin.'),
              'mannequin': dict(registration, vertices=int(len(man_v)), triangles=int(len(man_f))),
              'landmarksMetres': {'hra': dict(zip(names, warp.ls.round(4).tolist())),
                                  'mannequin': dict(zip(names, warp.lt.round(4).tolist()))},
              'breastSurfaceClampedInsideSkin': clamped, 'sliceStepMetres': STEP, 'angularBins': ANGLES,
              'generatorSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    return result, report, warp
