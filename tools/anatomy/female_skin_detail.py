"""Finishing of the mannequin skin and placement of the perineal reconstructions.

1. Swimsuit seams: the generated mannequin wears a fitted suit modelled as thin ridges
   on the trunk. Ridge vertices (sharp normal breaks) are found and smoothed locally.
2. Vulva: a sagittal cleft with labia majora bulges is sculpted on the perineum,
   between the mons pubis and the posterior commissure, so the skin shows external
   female anatomy by itself.
3. The teaching reconstructions (clitoris, bulbs, labia, urethra) are moved as one
   set with a similarity transform so their labia follow the sculpted cleft; the
   urethra keeps its bladder end in place and blends towards the new meatus.
"""
import numpy as np
from scipy.sparse import coo_matrix
from scipy.spatial import cKDTree

EXTERNAL_PREFIX = 'A2U_FEMALE_'


def vertex_normals(v, f):
    tri = v[f]
    n = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    normals = np.column_stack([np.bincount(f.ravel(), weights=np.repeat(n[:, k], 3), minlength=len(v)) for k in range(3)])
    length = np.linalg.norm(normals, axis=1)
    return normals / np.maximum(length, 1e-30)[:, None]


def adjacency(v, f):
    i = np.concatenate([f[:, 0], f[:, 1], f[:, 2], f[:, 1], f[:, 2], f[:, 0]])
    j = np.concatenate([f[:, 1], f[:, 2], f[:, 0], f[:, 0], f[:, 1], f[:, 2]])
    a = coo_matrix((np.ones(len(i)), (i, j)), shape=(len(v), len(v))).tocsr()
    a.data[:] = 1
    return a


def smooth_seams(v, f, trunk_x=0.20, trunk_y=(0.60, 1.47), angle_deg=10., rings=12, iterations=80):
    """Flatten thin ridges on the trunk (swimsuit seams) with local Laplacian smoothing.

    A ridge vertex is one whose normal departs from the mean normal of its two-ring
    neighbourhood; on this dense mesh the seams are the only such lines on the trunk.
    The suit surface sits a millimetre proud of the skin, so a wide band is smoothed
    to turn that step into an invisible slope.
    """
    v = v.copy()
    a = adjacency(v, f)
    degree = np.asarray(a.sum(1)).ravel()
    n = vertex_normals(v, f)
    wide = a @ a
    wide.data[:] = 1
    mean = wide @ n / np.maximum(np.asarray(wide.sum(1)).ravel(), 1)[:, None]
    mean /= np.maximum(np.linalg.norm(mean, axis=1), 1e-30)[:, None]
    angle = np.degrees(np.arccos(np.clip((n * mean).sum(1), -1, 1)))
    limit = np.abs(v[:, 0]) < np.where(v[:, 1] > 1.0, 0.27, trunk_x)
    seam = (angle > angle_deg) & limit & (v[:, 1] > trunk_y[0]) & (v[:, 1] < trunk_y[1])
    selected = seam.astype(float)
    for _ in range(rings):
        selected = np.minimum(1, selected + a @ selected)
    moving = selected > 0
    for _ in range(iterations):
        laplacian = (a @ v) / np.maximum(degree, 1)[:, None] - v
        v[moving] += 0.5 * laplacian[moving]
    return v, {'ridgeVertices': int(seam.sum()), 'smoothedVertices': int(moving.sum()), 'angleDegrees': angle_deg,
               'iterations': iterations}


def _midline_profile(v, centre, x_mid, floor, half_width=0.004, bins=180):
    """Skin radius around a sagittal centre, as a function of angle in the (z, y) plane.

    Only the trunk surface counts (y above the floor): the inner thighs touch below it.
    """
    sel = v[(np.abs(v[:, 0] - x_mid) < half_width) & (v[:, 1] >= floor)]
    d = sel[:, [2, 1]] - centre[[2, 1]]
    theta = np.arctan2(d[:, 1], d[:, 0])
    rho = np.hypot(d[:, 0], d[:, 1])
    edges = np.linspace(-np.pi, np.pi, bins + 1)
    radius = np.full(bins, np.nan)
    which = np.clip(np.digitize(theta, edges) - 1, 0, bins - 1)
    for b in range(bins):
        r = rho[which == b]
        if len(r):
            radius[b] = np.percentile(r, 20)  # Inner envelope of the sagittal slice: the skin itself.
    centres = (edges[:-1] + edges[1:]) / 2
    ok = ~np.isnan(radius)
    return centres, np.interp(centres, centres[ok], radius[ok], period=2 * np.pi)


def sculpt_vulva(v, f, x_mid, crotch_y, depth=0.006, width=0.0045, bulge=0.0025, bulge_offset=0.011,
                 front_deg=-38., back_deg=-128., samples=48):
    """Cut a sagittal cleft with labial bulges on the perineum; return the new skin and its anchors."""
    v = v.copy()
    # The perineum is the lowest trunk surface on the midline; the thighs may touch below it.
    band = v[(np.abs(v[:, 0] - x_mid) < 0.004) & (v[:, 1] > crotch_y - 0.01) & (v[:, 2] > -0.02) & (v[:, 2] < 0.05)]
    perineum_y = float(band[:, 1].min())
    centre = np.array([x_mid, perineum_y + 0.045, 0.012])
    floor = perineum_y - 0.006
    angles, radius = _midline_profile(v, centre, x_mid, floor)
    path = []
    for deg in np.linspace(front_deg, back_deg, samples):
        theta = np.radians(deg)
        r = np.interp(theta, angles, radius, period=2 * np.pi)
        path.append([x_mid, centre[1] + r * np.sin(theta), centre[2] + r * np.cos(theta)])
    path = np.asarray(path)
    seg = np.linalg.norm(np.diff(path, axis=0), axis=1)
    arc = np.concatenate([[0], np.cumsum(seg)])
    t_of_point = arc / arc[-1]
    normals = vertex_normals(v, f)
    near = np.flatnonzero((np.abs(v[:, 0] - x_mid) < 0.035) & (v[:, 1] >= floor) & (v[:, 1] < crotch_y + 0.11))
    tree = cKDTree(path)
    dist, index = tree.query(v[near])
    t = t_of_point[index]
    taper = np.clip(np.sin(np.pi * t), 0, 1) ** 0.6
    groove = depth * np.exp(-(dist / width) ** 2)
    labia = bulge * np.exp(-((dist - bulge_offset) / 0.005) ** 2)
    shift = (labia - groove) * taper
    active = dist < 0.03
    v[near[active]] += normals[near[active]] * shift[active][:, None]
    inward = -normals[near[active]].mean(0)
    inward /= np.linalg.norm(inward)
    anchors = {'anteriorCommissure': path[0].tolist(), 'posteriorCommissure': path[-1].tolist(),
               'cleftLengthMetres': float(arc[-1]), 'midlineX': float(x_mid), 'perineumY': perineum_y,
               'inward': inward.tolist()}
    return v, anchors, {'depthMetres': depth, 'widthMetres': width, 'labialBulgeMetres': bulge, 'displacedVertices': int(active.sum()),
                        **anchors}


def _similarity_yz(src_a, src_b, dst_a, dst_b):
    """Similarity in the sagittal (y, z) plane mapping segment src -> dst; x is translated only."""
    s, d = src_b[[1, 2]] - src_a[[1, 2]], dst_b[[1, 2]] - dst_a[[1, 2]]
    scale = np.linalg.norm(d) / np.linalg.norm(s)
    angle = np.arctan2(d[1], d[0]) - np.arctan2(s[1], s[0])
    c, si = np.cos(angle), np.sin(angle)
    rot = np.array([[c, -si], [si, c]]) * scale

    def apply(p):
        p = np.asarray(p, dtype=np.float64)
        out = p.copy()
        out[:, [1, 2]] = (p[:, [1, 2]] - src_a[[1, 2]]) @ rot.T + dst_a[[1, 2]]
        out[:, 0] = p[:, 0] - src_a[0] + dst_a[0]
        return out

    return apply, {'scale': float(scale), 'rotationDegrees': float(np.degrees(angle))}


def place_external_genitals(items, anchors, sink=0.004):
    """Move the reconstruction set so the labia majora follow the sculpted cleft, sunk slightly into the skin."""
    by_id = {record['id']: (record, v, f) for record, v, f in items}
    labia = np.concatenate([by_id[k][1] for k in ('A2U_FEMALE_LABIUM_MAJUS_LEFT', 'A2U_FEMALE_LABIUM_MAJUS_RIGHT')])
    score = labia[:, 1] + labia[:, 2]
    src_a, src_b = labia[np.argmax(score)].copy(), labia[np.argmin(score)].copy()
    x_mid = float(labia[:, 0].mean())
    src_a[0] = src_b[0] = x_mid
    dst_a, dst_b = np.array(anchors['anteriorCommissure']), np.array(anchors['posteriorCommissure'])
    similarity, params = _similarity_yz(src_a, src_b, dst_a, dst_b)
    offset = np.asarray(anchors['inward']) * sink

    def transform(p):
        return similarity(p) + offset

    moved = []
    result = []
    urethra = 'A2U_FEMALE_FEMALE_URETHRA'
    for record, v, f in items:
        if not record['id'].startswith(EXTERNAL_PREFIX) or record['layer'] == 'skin':
            result.append((record, v, f))
            continue
        if record['id'] == urethra:
            # Bladder end stays on the warped bladder neck; the meatus follows the vulva.
            w = np.clip((v[:, 1].max() - v[:, 1]) / max(np.ptp(v[:, 1]), 1e-6), 0, 1)[:, None]
            v = (1 - w) * v + w * transform(v)
        else:
            v = transform(v)
        moved.append(record['id'])
        result.append((record, v, f))

    def anchor_transform(key, point):
        point = np.asarray(point, dtype=np.float64)[None]
        return point[0] if key == 'bladderNeckCentre' else transform(point)[0]

    report = {'method': 'Similarity in the sagittal plane from the labia majora extremities to the sculpted cleft; '
                        'urethra blended from its bladder end to the new meatus.', 'moved': moved, **params,
              'sinkMetres': sink,
              'sourceSegment': [src_a.tolist(), src_b.tolist()], 'targetSegment': [dst_a.tolist(), dst_b.tolist()]}
    return result, report, anchor_transform
