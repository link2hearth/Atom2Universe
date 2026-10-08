"""Reversible neutral display surfaces, never replacements for source anatomy.

Only the auxiliary male skin and the distal ends of five tubes are clipped.
The two skin walls remain closed, with separate caps.  The female reference
skin already has a smooth perineum; its detailed external structures are
separate selectable meshes and need no skin variant.

Coordinates are metres in the displayed atlas.  The quadratic cutting surface
is an illustrative display closure, not a reconstruction or a clinical model.
"""
from collections import defaultdict
import hashlib
import numpy as np

TUBES = ('FMA19667', 'FMA14759', 'FMA14760', 'FMA14341', 'FMA14345')


def _height(points):
    return (.110 + .35 * (points[..., 1] - .72) + 20 * points[..., 0] ** 2
            + 6 * (points[..., 1] - .74) ** 2)


def _edge_error(points, edges):
    delta = points[edges[:, 0], :2] - points[edges[:, 1], :2]
    return (20 * delta[:, 0] ** 2 + 6 * delta[:, 1] ** 2) / 4


def _edges(faces):
    return np.concatenate((faces[:, [0, 1]], faces[:, [1, 2]], faces[:, [2, 0]]))


def _split_faces(faces, midpoints):
    result = []
    for a, b, c in faces:
        ab, bc, ca = [midpoints.get(tuple(sorted(e))) for e in ((a, b), (b, c), (c, a))]
        count = sum(x is not None for x in (ab, bc, ca))
        if count == 0:
            result.append((a, b, c))
        elif count == 3:
            result.extend(((a, ab, ca), (ab, b, bc), (ca, bc, c), (ab, bc, ca)))
        elif count == 1:
            if ab is not None:
                result.extend(((a, ab, c), (ab, b, c)))
            elif bc is not None:
                result.extend(((b, bc, a), (bc, c, a)))
            else:
                result.extend(((c, ca, b), (ca, a, b)))
        elif ab is None:
            result.extend(((a, b, ca), (b, bc, ca), (bc, c, ca)))
        elif bc is None:
            result.extend(((b, c, ab), (c, ca, ab), (ca, a, ab)))
        else:
            result.extend(((c, a, bc), (a, ab, bc), (ab, b, bc)))
    return np.asarray(result, dtype=np.int32)


def _refine_near_cut(vertices, faces, recess=0.):
    """Subdivide linearly: untouched source triangles retain exact geometry.

    A curved cut can cross a long triangle even when its three vertices lie
    behind it. Bound that interpolation error to 0.05 mm before clipping.
    """
    for _ in range(7):
        tri = vertices[faces]
        distance = tri[:, :, 2] - _height(tri) + recess
        span = np.ptp(tri[:, :, :2], axis=1)
        bound = (20 * span[:, 0] ** 2 + 6 * span[:, 1] ** 2) / 4
        near = (distance.max(1) + bound > -.003) & (distance.min(1) < .003)
        edges = np.unique(np.sort(_edges(faces[near]), axis=1), axis=0)
        edges = edges[_edge_error(vertices, edges) > .00005]
        if not len(edges):
            break
        midpoints = {tuple(e): len(vertices) + i for i, e in enumerate(edges)}
        vertices = np.vstack((vertices, vertices[edges].mean(1)))
        faces = _split_faces(faces, midpoints)
    return vertices, faces


def _clip(vertices, faces, recess=0.):
    points = vertices.tolist()
    lookup = {tuple(np.round(p, 12)): i for i, p in reversed(list(enumerate(vertices)))}
    distance = vertices[:, 2] - _height(vertices) + recess
    retained = []
    changed_faces = 0

    def index(point):
        key = tuple(np.round(point, 12))
        if key not in lookup:
            lookup[key] = len(points)
            points.append(point.tolist())
        return lookup[key]

    for face in faces:
        tri, d = vertices[face], distance[face]
        if (d < 0).all():
            retained.append(face.tolist())
            continue
        changed_faces += 1
        polygon = []
        for j, k in ((0, 1), (1, 2), (2, 0)):
            a, b, da, db = tri[j], tri[k], d[j], d[k]
            if da < 0:
                polygon.append(a)
            if (da < 0) != (db < 0):
                polygon.append(a + da / (da - db) * (b - a))
        ids = [index(p) for p in polygon]
        for j in range(1, len(ids) - 1):
            if len({ids[0], ids[j], ids[j + 1]}) == 3:
                retained.append((ids[0], ids[j], ids[j + 1]))
    return np.asarray(points), np.asarray(retained, dtype=np.int32), changed_faces


def _boundary_loops(vertices, faces):
    _, inverse = np.unique(np.round(vertices, 11), axis=0, return_inverse=True)
    directed = _edges(inverse[faces])
    _, edge_ids, counts = np.unique(np.sort(directed, axis=1), axis=0,
                                   return_inverse=True, return_counts=True)
    boundary = directed[counts[edge_ids] == 1]
    canonical = {u: i for i, u in reversed(list(enumerate(inverse)))}
    links = defaultdict(list)
    for a, b in boundary:
        links[int(a)].append(int(b))
    if not all(len(out) == 1 for out in links.values()):
        raise ValueError('Neutral skin boundary is not a set of simple loops')
    loops, left = [], set(links)
    while left:
        start, loop = min(left), []
        p = start
        while p not in loop:
            loop.append(p)
            p = links[p][0]
        assert p == start
        left -= set(loop)
        loops.append([canonical[p] for p in loop])
    return loops


def _ear_clip(points):
    xy = points[:, :2]
    area = np.sum(xy[:, 0] * np.roll(xy[:, 1], -1) - xy[:, 1] * np.roll(xy[:, 0], -1))
    sign = np.sign(area)
    remaining, triangles = list(range(len(xy))), []

    def cross(a, b):
        return a[0] * b[1] - a[1] * b[0]

    while len(remaining) > 3:
        for j in range(len(remaining)):
            ia, ib, ic = remaining[j - 1], remaining[j], remaining[(j + 1) % len(remaining)]
            a, b, c = xy[[ia, ib, ic]]
            if cross(b - a, c - a) * sign < 1e-14:
                continue
            inside = False
            for i in remaining:
                if i in (ia, ib, ic):
                    continue
                q = xy[i]
                if min(cross(b - a, q - a) * sign, cross(c - b, q - b) * sign,
                       cross(a - c, q - c) * sign) >= -1e-14:
                    inside = True
                    break
            if inside:
                continue
            triangles.append((ia, ib, ic))
            remaining.pop(j)
            break
        else:
            raise ValueError('Cannot triangulate the neutral skin boundary')
    triangles.append(remaining)
    return triangles


def _compact(vertices, faces):
    used, inverse = np.unique(faces, return_inverse=True)
    return vertices[used].astype(np.float32), inverse.reshape(-1, 3).astype(np.int32)


def _neutral_skin(vertices, faces):
    refined_v, refined_f = _refine_near_cut(vertices.astype(np.float64), faces.copy())
    clipped_v, clipped_f, changed = _clip(refined_v, refined_f)
    loops = _boundary_loops(clipped_v, clipped_f)
    assert len(loops) == 2, 'Expected one outer and one inner skin boundary'
    all_v, caps, reports = clipped_v.tolist(), [], []
    signs = []
    for loop in loops:
        points = clipped_v[loop]
        signed = np.sum(points[:, 0] * np.roll(points[:, 1], -1)
                        - points[:, 1] * np.roll(points[:, 0], -1))
        signs.append(np.sign(signed))
        # Only generated cut vertices move. The external cap sits 0.3 mm in
        # front of the mathematical cut; the internal cap is 2 mm behind it.
        offset = .0003 if signed < 0 else -.002
        for vi in loop:
            assert vi >= len(refined_v)
            all_v[vi][2] = float(_height(np.asarray(all_v[vi]))) + offset
        cap = np.asarray([(loop[c], loop[b], loop[a]) for a, b, c in _ear_clip(points)], dtype=np.int32)
        for _ in range(8):
            edges, count = np.unique(np.sort(_edges(cap), axis=1), axis=0, return_counts=True)
            edges = edges[(count == 2) & (_edge_error(np.asarray(all_v), edges) > .0001)]
            if not len(edges):
                break
            midpoints = {}
            for a, b in edges:
                midpoints[(int(a), int(b))] = len(all_v)
                all_v.append(((np.asarray(all_v[a]) + all_v[b]) / 2).tolist())
            cap = _split_faces(cap, midpoints)
        used = np.unique(cap)
        for vi in used:
            all_v[vi][2] = float(_height(np.asarray(all_v[vi]))) + offset
        cap_points = np.asarray(all_v)[cap]
        span = np.ptp(cap_points[:, :, :2], axis=1)
        error_bound = float(((20 * span[:, 0] ** 2 + 6 * span[:, 1] ** 2) / 4).max())
        assert error_bound < .0003, 'Cap curvature error would compromise wall separation'
        caps.extend(cap.tolist())
        reports.append({'wall': 'outer' if signed < 0 else 'inner', 'boundaryVertices': len(loop),
                        'capTriangles': len(cap), 'offsetMm': offset * 1000,
                        'curvatureErrorBoundMm': error_bound * 1000,
                        'boundaryBounds': [points.min(0).tolist(), points.max(0).tolist()]})
    assert sorted(signs) == [-1., 1.]
    result = _compact(np.asarray(all_v), np.vstack((clipped_f, np.asarray(caps))))
    return result, {'method': 'Local quadratic cut with separate, closed external and internal display caps',
                    'changedRefinedTriangles': changed, 'caps': reports,
                    'minimumAnalyticWallSeparationMm': 2.3 - max(r['curvatureErrorBoundMm'] for r in reports)}


def _neutral_tube(vertices, faces):
    # Keep cut tube ends at least 1.5 mm behind the neutral surface. Their
    # sectional openings are intentional; pre-existing source openings remain.
    v, f = _refine_near_cut(vertices.astype(np.float64), faces.copy(), recess=.0015)
    v, f, changed = _clip(v, f, recess=.0015)
    result = _compact(v, f)
    assert len(result[1]) and len(result[1]) < len(faces) + 100
    points = result[0][result[1]].astype(np.float64)
    span = np.ptp(points[:, :, :2], axis=1)
    bound = (20 * span[:, 0] ** 2 + 6 * span[:, 1] ** 2) / 4
    clearance = -(points[:, :, 2] - _height(points)).max(1) - bound
    assert clearance.min() > .001, 'A trimmed tube could protrude through the display skin'
    return result, {'method': 'Distal clipping behind the neutral skin, proximal route retained',
                    'cutEnds': 'Open sectional display ends; no reconstructed anatomy',
                    'changedRefinedTriangles': changed, 'minimumSurfaceClearanceMm': float(clearance.min() * 1000)}


def _hash(values):
    return hashlib.sha256(np.ascontiguousarray(values).tobytes()).hexdigest()


def build(atlas, meshes):
    """Return compact auxiliary meshes and a serializable provenance report."""
    assert atlas in ('male', 'female')
    report = {'scope': 'External genital display only; internal organs and breast silhouette retained',
              'anatomicalSourceMeshesModified': False,
              'clinicalUse': 'Illustrative reversible display closure, not anatomy', 'structures': {}}
    if atlas == 'female':
        report['skin'] = ('HRAF_2 already has a smooth perineum. Detailed external structures are separate '
                          'meshes; no female skin or urethra variant is needed.')
        return {}, report
    variants = {}
    for identity in ('FMA7163',) + TUBES:
        vertices, faces = meshes[identity]
        before = (_hash(vertices), _hash(faces))
        variant, detail = (_neutral_skin if identity == 'FMA7163' else _neutral_tube)(vertices, faces)
        assert before == (_hash(vertices), _hash(faces)), 'Anatomical input mutated'
        v, f = variant
        assert np.isfinite(v).all() and f.min() >= 0 and f.max() < len(v)
        assert len(np.unique(f)) == len(v)
        detail.update(sourceVertices=len(vertices), sourceTriangles=len(faces), vertices=len(v), triangles=len(f),
                      sourceVerticesSha256=before[0], sourceIndicesSha256=before[1],
                      bounds={'min': v.min(0).tolist(), 'max': v.max(0).tolist()})
        variants[identity] = variant
        report['structures'][identity] = detail
    return variants, report
