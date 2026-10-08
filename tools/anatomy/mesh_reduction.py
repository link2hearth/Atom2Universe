"""Offline mobile mesh reduction. Requires numpy and fast-simplification==0.1.13.

Only large muscular surfaces are reduced. Bones and small structures stay intact.
Reject a reduction if it drops a connected component or moves a bounding plane
by more than 0.75 mm. These checks are geometric guards, not medical validation.
"""
import numpy as np
import fast_simplification


def component_roots(faces, vertex_count):
    parents = list(range(vertex_count))

    def root(i):
        while parents[i] != i:
            parents[i] = parents[parents[i]]
            i = parents[i]
        return i

    used = set()
    for a, b, c in faces:
        a, b, c = int(a), int(b), int(c)
        used.update((a, b, c))
        ra, rb, rc = root(a), root(b), root(c)
        parents[rb] = ra
        parents[rc] = ra
    return np.asarray([root(int(face[0])) for face in faces])


def components(faces, vertex_count):
    return len(np.unique(component_roots(faces, vertex_count)))


def reduce(vertices, faces):
    if len(faces) <= 8000:
        return vertices, faces, 'unchanged-small'
    # Source OBJ files repeat identical coordinates at many triangle boundaries.
    # Weld exact duplicates, without tolerance or movement, before analysing connectivity.
    points, inverse = np.unique(np.asarray(vertices, dtype=np.float64), axis=0, return_inverse=True)
    triangles = inverse[np.asarray(faces, dtype=np.int32)]
    roots = component_roots(triangles, len(points))
    order = np.argsort(roots, kind='stable')
    parts = np.split(order, np.flatnonzero(np.diff(roots[order])) + 1)
    output_points, output_faces = [], []
    offset = 0
    for indices in parts:
        vertex_ids, remap = np.unique(triangles[indices], return_inverse=True)
        part_points = points[vertex_ids]
        part_faces = remap.reshape((-1, 3))
        # Preserve every small island. Simplifying the whole mesh would erase some of them.
        if len(part_faces) > 1000:
            low, high = part_points.min(axis=0), part_points.max(axis=0)
            scale = float((high - low).max())
            if scale > 0:
                reduced, reduced_faces = fast_simplification.simplify(
                    (part_points - low) / scale, part_faces,
                    target_count=max(500, int(len(part_faces) * .4)), agg=7.0)
                reduced = reduced * scale + low
                if not np.isfinite(reduced).all() or not len(reduced_faces):
                    raise ValueError('Invalid reduced mesh')
                drift = max(float(np.abs(reduced.min(axis=0) - low).max()), float(np.abs(reduced.max(axis=0) - high).max()))
                if drift <= .00075 and components(reduced_faces, len(reduced)) == 1:
                    part_points, part_faces = reduced, reduced_faces
        output_points.extend(part_points.tolist())
        output_faces.extend((part_faces + offset).tolist())
        offset += len(part_points)
    assert components(output_faces, len(output_points)) == len(parts)
    return output_points, output_faces, 'reduced' if len(output_faces) < len(faces) else 'welded-only'
