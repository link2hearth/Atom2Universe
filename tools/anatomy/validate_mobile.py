"""Independent geometry audit of the mobile derivative (numpy + scipy).

python tools/anatomy/validate_mobile.py --staged --compare
python tools/anatomy/validate_mobile.py --compare
Comparisons use the hash-checked originals retained by optimize_mobile.mjs in app/build.
Normal catalogue/GLB validation needs no original source downloads.
"""
import argparse
import json
import numpy as np
from scipy.spatial import cKDTree
from validate_atlas import ROOT, ASSETS, read_glb

WORK = ROOT / 'app/build/anatomy-source/mobile'


def distances_to_triangles(points, triangles):
    a, b, c = np.moveaxis(triangles, 2, 0)
    ab, ac, ap = b - a, c - a, points[:, None] - a
    aa, bb, cc = (ab * ab).sum(2), (ab * ac).sum(2), (ac * ac).sum(2)
    d, e = (ap * ab).sum(2), (ap * ac).sum(2)
    det = aa * cc - bb * bb
    u, w = (cc * d - bb * e) / np.maximum(det, 1e-30), (aa * e - bb * d) / np.maximum(det, 1e-30)
    projection = a + u[:, :, None] * ab + w[:, :, None] * ac
    distance = ((points[:, None] - projection) ** 2).sum(2)
    distance[(u < 0) | (w < 0) | (u + w > 1) | (det < 1e-30)] = np.inf
    for start, end in ((a, b), (b, c), (c, a)):
        edge = end - start
        fraction = np.clip(((points[:, None] - start) * edge).sum(2)
                           / np.maximum((edge * edge).sum(2), 1e-30), 0, 1)
        candidate = start + fraction[:, :, None] * edge
        distance = np.minimum(distance, ((points[:, None] - candidate) ** 2).sum(2))
    return np.sqrt(distance.min(1))


def nearest_surface(points, vertices, faces, review_limit):
    """Candidate distances are upper bounds; exhaustively resolve every outlier.

    Skinny triangles can have distant centres, so the coarse KD search alone
    must never be treated as an exact surface distance or used to reject a mesh.
    """
    triangles = vertices[faces].astype(np.float64)
    _, candidates = cKDTree(triangles.mean(1)).query(points, k=min(32, len(faces)))
    if candidates.ndim == 1:
        candidates = candidates[:, None]
    distance = distances_to_triangles(points, triangles[candidates])
    for i in np.flatnonzero(distance > review_limit):
        for start in range(0, len(triangles), 8192):
            exact = distances_to_triangles(points[i:i+1], triangles[None, start:start+8192])[0]
            distance[i] = min(distance[i], exact)
    return distance


def meshes(gltf, accessor):
    result = {}
    for node in gltf['nodes']:
        p, = gltf['meshes'][node['mesh']]['primitives']
        result[node['name']] = (accessor(p['attributes']['POSITION']),
                                accessor(p['indices']).reshape(-1, 3))
    return result


def validate(atlas, staged=False, compare=False):
    directory = WORK / atlas / 'optimized' if staged else ASSETS / (atlas if atlas != 'male' else '')
    catalog = json.loads((directory / 'catalog.json').read_text(encoding='utf8'))
    provenance = json.loads((directory / 'provenance.json').read_text(encoding='utf8'))
    mobile = provenance['mobileOptimization']
    gltf, accessor = read_glb(directory / 'atlas.glb')
    current = meshes(gltf, accessor)
    assert set(current) == {s['id'] for s in catalog['structures'] if s['mesh']} | set(catalog.get('externalHiddenVariants', {}))
    assert sum(len(f) for v, f in current.values()) == mobile['outputTriangles'] < mobile['inputTriangles']
    assert sum(len(v) for v, f in current.values()) == mobile['outputVertices']
    result = {'atlas': atlas, 'meshes': len(current), 'triangles': mobile['outputTriangles'],
              'compressedBytes': mobile['outputBytes']}
    if compare:
        original = WORK / atlas / 'original'
        source_provenance = json.loads((original / 'provenance.json').read_text(encoding='utf8'))
        assert source_provenance['packaging']['modelSha256'] == mobile['sourceModelSha256']
        source_catalog = json.loads((original / 'catalog.json').read_text(encoding='utf8'))
        source_gltf, source_accessor = read_glb(original / 'atlas.glb')
        source = meshes(source_gltf, source_accessor)
        # All semantics and interaction state survive; only model references and bounds change.
        def semantics(value):
            value = json.loads(json.dumps(value))
            for k in ('modelParts', 'modelBytes', 'externalHiddenBounds'):
                value.pop(k, None)
            for row in value['structures']:
                row.pop('min', None); row.pop('max', None)
            return value
        assert semantics(catalog) == semantics(source_catalog)
        assert gltf['nodes'] == source_gltf['nodes'] and gltf['materials'] == source_gltf['materials']
        assert set(current) == set(source)
        records = {s['id']: s for s in catalog['structures']}
        mapping = catalog.get('externalHiddenVariants', {})
        measurements = []
        for identity, (v, f) in current.items():
            ov, of = source[identity]
            assert len(f) <= len(of), identity
            sample_count = 384
            record = records[mapping.get(identity, identity)]
            delicate = record['layer'] in {'nervous', 'vascular', 'lymphatic', 'senses', 'cartilage'}
            limit = .00010 if atlas == 'ear' else .0005 if delicate else .0015
            # Deterministic vertex + face-centre samples in both directions.
            distances = []
            for a, af, b, bf in ((ov, of, v, f), (v, f, ov, of)):
                vi = np.linspace(0, len(a) - 1, min(sample_count, len(a)), dtype=int)
                fi = np.linspace(0, len(af) - 1, min(sample_count, len(af)), dtype=int)
                points = np.concatenate([a[vi], a[af[fi]].mean(1)])
                distances.extend(nearest_surface(points, b, bf, limit))
            maximum = float(np.max(distances))
            measurements.append({'id': identity, 'maxSampledDistanceMm': maximum * 1000,
                                 'p99SampledDistanceMm': float(np.percentile(distances, 99)) * 1000,
                                 'reviewLimitMm': limit * 1000})
        measurements.sort(key=lambda r: r['maxSampledDistanceMm'], reverse=True)
        (WORK / atlas / 'surface-comparison.json').write_text(json.dumps(measurements, indent=2), encoding='utf8')
        outliers = [r for r in measurements if r['maxSampledDistanceMm'] > r['reviewLimitMm']]
        result.update(maxSampledDistanceMm=measurements[0]['maxSampledDistanceMm'], review=outliers)
        assert not outliers, json.dumps({'atlas': atlas, 'review': outliers})
    print(json.dumps(result, indent=2))
    return len(current)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--staged', action='store_true')
    parser.add_argument('--compare', action='store_true')
    args = parser.parse_args()
    for atlas in ('male', 'female', 'ear'):
        validate(atlas, args.staged, args.compare)
