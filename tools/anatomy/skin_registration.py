"""Reproducible local accommodation of skin around confirmed superficial structures.

The source skin is a thin closed tissue shell with inner AND outer faces. Only
its outer surface receives a smooth outward field in the confirmed lateral-thigh,
medial-leg and anterior-neck regions. The inner sheet and underlying fascia remain
exactly where the anatomical source put them. This is an illustrative surface
adaptation, not an assertion of measured dermal/subcutaneous thickness.

All coordinates are metres in the atlas frame (+X anatomical left, +Y superior,
+Z anterior). No global inflation, decimation, face changes or tendon deformation.
"""
from collections import defaultdict
import hashlib
import itertools
from pathlib import Path
import zipfile
import sys

import numpy as np

SOURCE = Path(__file__).resolve().parents[2] / 'app/build/anatomy-source'
CLEARANCE = .0005
PLATEAU_RADIUS = .006
BLEND_RADIUS = .020
MAX_DISPLACEMENT = .008
SOURCE_ELEMENTS = ('FJ1423', 'FJ1423M')
_LAST_REPORT = None

class LocalSurface:
    """Exact triangle distances in a spatial grid, signed by outward face normals.

    This is intentionally a local outward patch, not the closed skin tissue
    volume: the source's inner coating would give misleading body containment.
    """

    def __init__(self, vertices, faces, cell=.02):
        self.tri = np.asarray(vertices, dtype='float64')[np.asarray(faces)]
        self.cell = cell
        normals = np.cross(self.tri[:, 1] - self.tri[:, 0], self.tri[:, 2] - self.tri[:, 0])
        self.normals = normals / np.maximum(np.linalg.norm(normals, axis=1, keepdims=True), 1e-20)
        self.grid = defaultdict(list)
        low = np.floor(self.tri.min(1) / cell).astype(int)
        high = np.floor(self.tri.max(1) / cell).astype(int)
        for i, (lo, hi) in enumerate(zip(low, high)):
            for key in itertools.product(*(range(a, b + 1) for a, b in zip(lo, hi))):
                self.grid[key].append(i)
        self.cache = {}

    def query(self, points):
        points = np.asarray(points, dtype='float64')
        keys = np.floor(points / self.cell).astype(int)
        bins = defaultdict(list)
        for i, key in enumerate(keys):
            bins[tuple(key)].append(i)
        out = np.empty_like(points)
        distances = np.empty(len(points))
        ids = np.empty(len(points), dtype=int)
        for key, index in bins.items():
            samples = points[index]
            rings = 1
            while True:
                cache_key = (key, rings)
                if cache_key not in self.cache:
                    candidates = set()
                    for grid_key in itertools.product(*(range(k - rings, k + rings + 1) for k in key)):
                        candidates.update(self.grid.get(grid_key, ()))
                    self.cache[cache_key] = np.array(sorted(candidates), dtype=int)
                indices = self.cache[cache_key]
                if len(indices):
                    nearest, distance, face_indices = closest(samples, self.tri[indices])
                    # Every omitted triangle is farther than this grid bound.
                    if max(distance) < rings * self.cell:
                        break
                rings *= 2
                if rings > 128:
                    raise ValueError('No surface')
            out[index] = nearest
            distances[index] = distance
            ids[index] = indices[face_indices]
        signed = distances * np.sign(np.einsum('ij,ij->i', points - out, self.normals[ids]))
        return out, signed, ids


def closest(points, triangles):
    """Nearest point on each triangle: interior projection or clamped edges."""
    a, b, c = triangles.transpose(1, 0, 2)
    ab, ac = b - a, c - a
    aa, bb, cc = (ab * ab).sum(1), (ab * ac).sum(1), (ac * ac).sum(1)
    denominator = aa * cc - bb * bb
    results, distances, indices = [], [], []
    for samples in np.array_split(points, max(1, (len(points) + 31) // 32)):
        ap = samples[:, None, :] - a
        d, e = (ap * ab).sum(2), (ap * ac).sum(2)
        u = (cc * d - bb * e) / np.maximum(denominator, 1e-30)
        w = (aa * e - bb * d) / np.maximum(denominator, 1e-30)
        projection = a + u[:, :, None] * ab + w[:, :, None] * ac
        valid = (u >= 0) & (w >= 0) & (u + w <= 1) & (denominator > 1e-24)
        distance = ((samples[:, None, :] - projection) ** 2).sum(2)
        distance[~valid] = np.inf
        for start, end in ((a, b), (b, c), (c, a)):
            edge = end - start
            fraction = np.clip(((samples[:, None, :] - start) * edge).sum(2)
                               / np.maximum((edge * edge).sum(1), 1e-30), 0, 1)
            candidate = start + fraction[:, :, None] * edge
            candidate_distance = ((samples[:, None, :] - candidate) ** 2).sum(2)
            take = candidate_distance < distance
            projection[take] = candidate[take]
            distance[take] = candidate_distance[take]
        nearest_indices = distance.argmin(1)
        nearest = projection[np.arange(len(samples)), nearest_indices]
        results.append(nearest)
        distances.append(np.linalg.norm(samples - nearest, axis=1))
        indices.append(nearest_indices)
    return np.concatenate(results), np.concatenate(distances), np.concatenate(indices)

def _read_tracts(source):
    archive = zipfile.ZipFile(source / 'bodyparts.zip')
    paths = {Path(name).stem: name for name in archive.namelist() if name.endswith('.obj')}
    result = []
    for element in SOURCE_ELEMENTS:
        vertices, faces = [], []
        raw = archive.read(paths[element])
        for line in raw.decode('utf-8-sig').splitlines():
            fields = line.split()
            if not fields:
                continue
            if fields[0] == 'v':
                x, y, z = map(float, fields[1:4])
                vertices.append((x / 1000, z / 1000, -y / 1000))
            elif fields[0] == 'f':
                polygon = [int(p.split('/')[0]) for p in fields[1:]]
                polygon = [i - 1 if i > 0 else len(vertices) + i for i in polygon]
                faces.extend((polygon[0], polygon[j], polygon[j + 1]) for j in range(1, len(polygon) - 1))
        result.append((element, np.asarray(vertices), np.asarray(faces), hashlib.sha256(raw).hexdigest()))
    return result


def _normals(vertices, faces):
    triangles = vertices[faces]
    face_normals = np.cross(triangles[:, 1] - triangles[:, 0], triangles[:, 2] - triangles[:, 0])
    vertex_normals = np.zeros_like(vertices)
    for corner in range(3):
        np.add.at(vertex_normals, faces[:, corner], face_normals)
    vertex_normals /= np.maximum(np.linalg.norm(vertex_normals, axis=1, keepdims=True), 1e-20)
    return face_normals, vertex_normals


def _smooth_surface_normals(vertices, faces, normals, points, face_indices):
    triangle = vertices[faces[face_indices]]
    a, b, c = triangle.transpose(1, 0, 2)
    ab, ac, ap = b - a, c - a, points - a
    aa, bb, cc = (ab * ab).sum(1), (ab * ac).sum(1), (ac * ac).sum(1)
    d, e = (ap * ab).sum(1), (ap * ac).sum(1)
    denominator = np.maximum(aa * cc - bb * bb, 1e-30)
    u, w = (cc * d - bb * e) / denominator, (aa * e - bb * d) / denominator
    weights = np.stack([1 - u - w, u, w], axis=1)
    direction = (normals[faces[face_indices]] * weights[:, :, None]).sum(1)
    return direction / np.maximum(np.linalg.norm(direction, axis=1, keepdims=True), 1e-20)


def _adapt_iliotibial_skin(vertices, faces, source=SOURCE):
    global _LAST_REPORT
    original = np.asarray(vertices, dtype=np.float64)
    faces = np.asarray(faces, dtype=np.int32)
    assert original.shape == (102467, 3) and faces.shape == (203382, 3)
    current = original.copy()
    original_face_normals, _ = _normals(original, faces)
    midpoints = original[faces].mean(1)
    unit = original_face_normals / np.maximum(np.linalg.norm(original_face_normals, axis=1, keepdims=True), 1e-20)
    reports = {}
    for element, tract_vertices, tract_faces, source_hash in _read_tracts(source):
        side = -1 if element == 'FJ1423' else 1
        # A directional patch distinguishes the external sheet from the inner
        # coating. The independent six-view exterior audit agrees on every
        # exposed tract sample. Medial leg walls and other limbs are excluded.
        patch = ((midpoints[:, 0] * side > .06) & (midpoints[:, 0] * side < .22)
                 & (midpoints[:, 1] > .28) & (midpoints[:, 1] < 1.0)
                 & (unit[:, 0] * side > .3))
        patch_faces = np.flatnonzero(patch)
        outer_vertices = np.unique(faces[patch])
        points = np.vstack([tract_vertices, tract_vertices[tract_faces].mean(1)])
        before = None
        seed_bounds = None
        iterations = 0
        for iteration in range(3):
            surface = LocalSurface(current, faces[patch])
            nearest, distance, indices = surface.query(points)
            if before is None:
                before = distance.copy()
            # Stop with a small tolerance on the requested half-millimetre cover.
            if distance.max() <= -CLEARANCE + .0001:
                break
            contact = distance > -CLEARANCE
            # Source triangles can span 5 cm: constraints must reach their
            # three supporting vertices, even when the closest point lies more
            # than the blend radius from every corner.
            supporting_faces = patch_faces[indices[contact]]
            locations = current[faces[supporting_faces]].reshape(-1, 3)
            required = np.repeat(distance[contact] + CLEARANCE, 3)
            assert required.max() <= MAX_DISPLACEMENT, (element, required.max())
            if seed_bounds is None:
                seed_bounds = [locations.min(0).tolist(), locations.max(0).tolist()]
            # Coalesce constraints by 2 mm cells, retaining each cell's largest
            # requirement instead of averaging away its most exposed point.
            voxels = np.floor(locations / .002).astype(int)
            chosen = {}
            for i, key in enumerate(map(tuple, voxels)):
                if key not in chosen or required[i] > required[chosen[key]]:
                    chosen[key] = i
            ix = list(chosen.values())
            locations, required = locations[ix], required[ix]
            lo, hi = locations.min(0) - BLEND_RADIUS, locations.max(0) + BLEND_RADIUS
            eligible = outer_vertices[((current[outer_vertices] >= lo) & (current[outer_vertices] <= hi)).all(1)]
            amplitude = np.zeros(len(eligible))
            for rows in np.array_split(np.arange(len(eligible)), max(1, (len(eligible) + 63) // 64)):
                d = np.linalg.norm(current[eligible[rows], None, :] - locations[None, :, :], axis=2)
                t = np.clip((d - PLATEAU_RADIUS) / (BLEND_RADIUS - PLATEAU_RADIUS), 0, 1)
                kernel = 1 - t * t * (3 - 2 * t)
                amplitude[rows] = (required[None, :] * kernel).max(1)
            active = amplitude > 1e-12
            eligible, amplitude = eligible[active], amplitude[active]
            _, normals = _normals(current, faces)
            nearest_vertices, _, face_indices = surface.query(current[eligible])
            direction = _smooth_surface_normals(current, faces, normals, nearest_vertices,
                                                patch_faces[face_indices])
            assert (direction[:, 0] * side > 0).all()
            current[eligible] += amplitude[:, None] * direction
            iterations += 1
        surface = LocalSurface(current, faces[patch])
        _, after, _ = surface.query(points)
        assert after.max() <= -CLEARANCE + .0001, (element, 'insufficient tract cover', float(after.max()))
        reports[element] = {
            'sourceObjSha256': source_hash, 'samples': len(points), 'iterations': iterations,
            'outsideBeforeOver01mm': int((before > .0001).sum()),
            'maximumOutsideBeforeMm': float(before.max() * 1000),
            'outsideAfterOver01mm': int((after > .0001).sum()),
            'maximumSignedDistanceAfterMm': float(after.max() * 1000),
            'seedBoundsMetres': seed_bounds,
        }
    displacement = np.linalg.norm(current - original, axis=1)
    new_normals, _ = _normals(current, faces)
    valid = np.linalg.norm(original_face_normals, axis=1) > 1e-15
    assert np.einsum('ij,ij->i',original_face_normals[valid],new_normals[valid]).min() > 0, 'Skin face inversion'
    assert displacement.max() <= MAX_DISPLACEMENT, displacement.max()
    assert np.isfinite(current).all()
    _LAST_REPORT = {
        'version': 1, 'method': 'Local outward accommodation of the existing outer lateral-thigh skin sheet, retaining the inner sheet coordinates around unchanged BodyParts3D iliotibial tracts. Vertex and triangle-centroid constraints propagated to the three supporting skin-face corners, 0.5 mm target cover, maximum constraint field with a 6 mm plateau and smoothstep falloff to 20 mm around those corners. Per-vertex external smooth normals; topology and indices retained. No global inflation or tendon displacement.',
        'targetCoverMm': CLEARANCE * 1000, 'blendRadiusMm': BLEND_RADIUS * 1000,
        'displacementLimitMm': MAX_DISPLACEMENT * 1000,
        'changedVertices': int((displacement > 1e-9).sum()), 'totalVertices': len(current),
        'maximumDisplacementMm': float(displacement.max() * 1000),
        'sourceVerticesSha256': hashlib.sha256(original.astype('<f8').tobytes()).hexdigest(),
        'sourceIndicesSha256': hashlib.sha256(faces.astype('<i4').tobytes()).hexdigest(),
        'tracts': reports,
        'limitations': 'Illustrative local accommodation of differing source surfaces, not measured skin or subcutaneous-tissue thickness. Other anatomical meshes, natural openings and the rest of the skin remain unchanged. This does not establish whole-body clinical containment or validation.',
    }
    return current, faces


def report():
    return _LAST_REPORT


SUPERFICIAL_ELEMENTS = ('FJ1397', 'FJ1397M', 'FJ1434', 'FJ1434M', 'FJ1558', 'FJ1587')
SUPERFICIAL_MAX_DISPLACEMENT = .012


def _visible_exterior_faces(vertices, faces, candidates):
    """Classify candidate outer faces by orthographic visibility from six sides.

    Each centroid is compared with the extremal ray intersections through the
    complete source skin. This distinguishes the two skin sheets without relying
    on the tissue-shell signed volume. Only convex leg and anterior-neck patches
    use this classifier; body openings are outside the adjustment regions.
    """
    triangles = vertices[faces]
    centres = triangles.mean(1)
    visible = np.zeros(len(faces), dtype=bool)
    candidate_ids = np.flatnonzero(candidates)
    cell = .02
    for axis in range(3):
        uv = [index for index in range(3) if index != axis]
        projected = triangles[:, :, uv]
        queries = centres[:, uv]
        grid, bins = defaultdict(list), defaultdict(list)
        low = np.floor(projected.min(1) / cell).astype(int)
        high = np.floor(projected.max(1) / cell).astype(int)
        for i, (lo, hi) in enumerate(zip(low, high)):
            for key in itertools.product(*(range(a, b + 1) for a, b in zip(lo, hi))):
                grid[key].append(i)
        for i in candidate_ids:
            bins[tuple(np.floor(queries[i] / cell).astype(int))].append(i)
        for key, rows in bins.items():
            indices = np.array(grid[key])
            a, b, c = projected[indices].transpose(1, 0, 2)
            ab, ac = b - a, c - a
            determinant = ab[:, 0] * ac[:, 1] - ab[:, 1] * ac[:, 0]
            valid = abs(determinant) > 1e-20
            denominator = np.where(valid, determinant, 1)
            for batch in np.array_split(np.array(rows), max(1, (len(rows) + 31) // 32)):
                ap = queries[batch, None, :] - a
                u = (ap[:, :, 0] * ac[:, 1] - ap[:, :, 1] * ac[:, 0]) / denominator
                w = (ab[:, 0] * ap[:, :, 1] - ab[:, 1] * ap[:, :, 0]) / denominator
                inside = valid & (u >= -1e-8) & (w >= -1e-8) & (u + w <= 1 + 1e-8)
                depth = (triangles[indices, 0, axis]
                         + u * (triangles[indices, 1, axis] - triangles[indices, 0, axis])
                         + w * (triangles[indices, 2, axis] - triangles[indices, 0, axis]))
                minimum = np.where(inside, depth, np.inf).min(1)
                maximum = np.where(inside, depth, -np.inf).max(1)
                visible[batch] |= ((abs(centres[batch, axis] - minimum) < .00002)
                                   | (abs(centres[batch, axis] - maximum) < .00002))
    return visible


def _superficial_surfaces(source):
    # The same deterministic reducer used by the atlas is included in the
    # constraints, so both the original and displayed muscle surfaces fit.
    dependency_path = str(source / 'python')
    if dependency_path not in sys.path:
        sys.path.insert(0, dependency_path)
    from mesh_reduction import reduce
    result = {}
    with zipfile.ZipFile(source / 'bodyparts.zip') as archive:
        paths = {Path(name).stem: name for name in archive.namelist() if name.endswith('.obj')}
        for element in SUPERFICIAL_ELEMENTS:
            vertices, faces = [], []
            raw = archive.read(paths[element])
            for line in raw.decode('utf-8-sig').splitlines():
                fields = line.split()
                if not fields:
                    continue
                if fields[0] == 'v':
                    x, y, z = map(float, fields[1:4])
                    vertices.append((x / 1000, z / 1000, -y / 1000))
                elif fields[0] == 'f':
                    polygon = [int(item.split('/')[0]) for item in fields[1:]]
                    polygon = [i - 1 if i > 0 else len(vertices) + i for i in polygon]
                    faces.extend((polygon[0], polygon[j], polygon[j + 1]) for j in range(1, len(polygon) - 1))
            displayed_vertices, displayed_faces, _ = reduce(vertices, faces)
            vertices, faces = np.asarray(vertices), np.asarray(faces)
            displayed_vertices = np.asarray(displayed_vertices, dtype=np.float32)
            displayed_faces = np.asarray(displayed_faces)
            samples = np.unique(np.vstack([
                vertices, vertices[faces].mean(1), displayed_vertices,
                displayed_vertices[displayed_faces].mean(1),
            ]), axis=0)
            result[element] = (samples, hashlib.sha256(raw).hexdigest())
    return result


def _accommodate_patch(current, faces, patch, points):
    patch_faces = np.flatnonzero(patch)
    outer_vertices = np.unique(faces[patch])
    history = []
    before = None
    for iteration in range(3):
        surface = LocalSurface(current, faces[patch])
        nearest, distance, indices = surface.query(points)
        if before is None:
            before = distance.copy()
        history.append(float(distance.max() * 1000))
        if distance.max() <= -CLEARANCE + .0001:
            break
        contact = distance > -CLEARANCE
        locations = current[faces[patch_faces[indices[contact]]]].reshape(-1, 3)
        required = np.repeat(distance[contact] + CLEARANCE, 3)
        assert required.max() <= SUPERFICIAL_MAX_DISPLACEMENT, required.max()
        chosen = {}
        for i, key in enumerate(map(tuple, np.floor(locations / .002).astype(int))):
            if key not in chosen or required[i] > required[chosen[key]]:
                chosen[key] = i
        indices = list(chosen.values())
        locations, required = locations[indices], required[indices]
        lo, hi = locations.min(0) - BLEND_RADIUS, locations.max(0) + BLEND_RADIUS
        eligible = outer_vertices[((current[outer_vertices] >= lo) & (current[outer_vertices] <= hi)).all(1)]
        amplitude = np.zeros(len(eligible))
        for rows in np.array_split(np.arange(len(eligible)), max(1, (len(eligible) + 63) // 64)):
            distance = np.linalg.norm(current[eligible[rows], None, :] - locations[None, :, :], axis=2)
            t = np.clip((distance - PLATEAU_RADIUS) / (BLEND_RADIUS - PLATEAU_RADIUS), 0, 1)
            amplitude[rows] = (required[None, :] * (1 - t * t * (3 - 2 * t))).max(1)
        active = amplitude > 1e-12
        eligible, amplitude = eligible[active], amplitude[active]
        _, normals = _normals(current, faces)
        nearest, _, indices = surface.query(current[eligible])
        direction = _smooth_surface_normals(current, faces, normals, nearest, patch_faces[indices])
        current[eligible] += amplitude[:, None] * direction
    _, after, _ = LocalSurface(current, faces[patch]).query(points)
    assert after.max() <= -CLEARANCE + .0001, after.max()
    return {
        'samples': len(points), 'maximumSignedDistancesByPassMm': history,
        'outsideBeforeOver01mm': int((before > .0001).sum()),
        'maximumOutsideBeforeMm': float(before.max() * 1000),
        'outsideAfterOver01mm': int((after > .0001).sum()),
        'maximumSignedDistanceAfterMm': float(after.max() * 1000),
    }


def adapt_skin(vertices, faces, source=SOURCE):
    global _LAST_REPORT
    original = np.asarray(vertices, dtype=np.float64)
    current, faces = _adapt_iliotibial_skin(vertices, faces, source)
    surfaces = _superficial_surfaces(source)
    centres = original[faces].mean(1)
    leg_region = ((abs(centres[:, 0]) > .001) & (abs(centres[:, 0]) < .13)
                  & (centres[:, 1] > .10) & (centres[:, 1] < .61))
    neck_region = ((abs(centres[:, 0]) < .12) & (centres[:, 1] > 1.25)
                   & (centres[:, 1] < 1.44) & (centres[:, 2] > .05))
    exterior = _visible_exterior_faces(original, faces, leg_region | neck_region)
    regions = []
    for side, elements in [(-1, ('FJ1397', 'FJ1434')), (1, ('FJ1397M', 'FJ1434M'))]:
        points = np.vstack([surfaces[element][0] for element in elements])
        points = points[(points[:, 1] > .12) & (points[:, 1] < .58)]
        patch = leg_region & exterior & (centres[:, 0] * side > .001)
        regions.append(('right_medial_leg' if side < 0 else 'left_medial_leg', patch, points, elements))
    elements = ('FJ1558', 'FJ1587')
    points = np.vstack([surfaces[element][0] for element in elements])
    points = points[(points[:, 1] > 1.31) & (points[:, 1] < 1.415) & (abs(points[:, 0]) < .07)]
    regions.append(('anterior_neck', neck_region & exterior, points, elements))
    reports = {}
    for name, patch, points, elements in regions:
        reports[name] = _accommodate_patch(current, faces, patch, points)
        reports[name]['sourceObjSha256'] = {element: surfaces[element][1] for element in elements}
    displacement = np.linalg.norm(current - original, axis=1)
    old_normals, _ = _normals(original, faces)
    new_normals, _ = _normals(current, faces)
    assert np.einsum('ij,ij->i', old_normals, new_normals).min() > 0, 'Skin face inversion'
    assert displacement.max() <= SUPERFICIAL_MAX_DISPLACEMENT, displacement.max()
    assert np.isfinite(current).all()
    _LAST_REPORT.update({
        'version': 2, 'displacementLimitMm': SUPERFICIAL_MAX_DISPLACEMENT * 1000,
        'changedVertices': int((displacement > 1e-9).sum()),
        'maximumDisplacementMm': float(displacement.max() * 1000),
        'superficialRegions': reports,
    })
    _LAST_REPORT['method'] += (' The same local external-sheet accommodation covers confirmed source '
                              'protrusions of the medial gastrocnemius heads, sartorius muscles and '
                              'anterior-neck platysma. A six-direction source-surface visibility test '
                              'identifies the outer sheet in these bounded regions. Original and '
                              'displayed muscle coordinates remain unchanged; both constrain the skin.')
    return current, faces
