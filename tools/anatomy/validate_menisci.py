"""Check generated knee placement on held-out surface samples, without an APK."""
import json
import sys
from pathlib import Path
import numpy as np
sys.dont_write_bytecode = True
from mesh_surface import closest, inside
from validate_atlas import read_glb, source_atlas_assets

ROOT = Path(__file__).resolve().parents[2]
ASSETS = source_atlas_assets()


def main():
    gltf, accessor = read_glb(ASSETS / 'atlas.glb')
    catalog = json.loads((ASSETS / 'catalog.json').read_text(encoding='utf-8'))['structures']
    names = {s['id']: s['sourceName'] for s in catalog}
    geometry = {}
    for node in gltf['nodes']:
        p = gltf['meshes'][node['mesh']]['primitives'][0]
        geometry[names[node['name']]] = (accessor(p['attributes']['POSITION']).astype(float),
                                       accessor(p['indices']).reshape(-1, 3))
    report = {}
    for side in ('left', 'right'):
        for kind in ('medial', 'lateral'):
            name = side + ' ' + kind + ' meniscus'
            v, f = geometry[name]
            triangles = v[f]
            # Interior samples not used by the registration (which fits vertices
            # and face centroids), plus edge midpoints to catch missed contacts.
            points = np.concatenate([np.einsum('ijk,j->ik', triangles, weight)
                                     for weight in ([.6, .2, .2], [.2, .6, .2], [.2, .2, .6], [.5, .5, 0.])])
            assert (points[:, 0].mean() > 0) == (side == 'left')
            checks = {}
            for bone in ('tibia', 'femur'):
                b, indices = geometry[side + ' ' + bone]
                all_triangles = b[indices]
                # Same surface around the knee, bounded away from cut faces.
                knee = all_triangles[(all_triangles[:, :, 1].max(1) > .30) &
                                     (all_triangles[:, :, 1].min(1) < .44)]
                _, distance = closest(points, knee)
                distance *= np.where(inside(points, all_triangles), -1, 1)
                mm = distance * 1000
                checks[bone] = {'maximumPenetrationMm': max(0., float(-mm.min())),
                                'p05ClearanceMm': float(np.quantile(mm, .05)),
                                'sampleCount': len(points)}
                # Guard against wrong registration, not a clinical tolerance.
                # Reduced source bone meshes have millimetre-scale facets.
                assert mm.min() > -2.5, (name, bone, checks[bone])
            report[name] = checks
    (ROOT / 'app/build/anatomy-source/meniscus_validation.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
