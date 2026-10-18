"""Offline point-to-triangle measurements for anatomy registration and checks."""
import numpy as np


def closest(points, triangles):
    """Closest surface points, including triangle edges and degenerate faces."""
    a, b, c = triangles.transpose(1, 0, 2)
    ab, ac = b - a, c - a
    aa, bb, cc = (ab * ab).sum(1), (ab * ac).sum(1), (ac * ac).sum(1)
    denominator = aa * cc - bb * bb
    result = []
    for p in np.array_split(points, max(1, (len(points) + 63) // 64)):
        ap = p[:, None, :] - a
        d, e = (ap * ab).sum(2), (ap * ac).sum(2)
        v = (cc * d - bb * e) / np.maximum(denominator, 1e-30)
        w = (aa * e - bb * d) / np.maximum(denominator, 1e-30)
        projected = a + v[:, :, None] * ab + w[:, :, None] * ac
        valid = (v >= 0) & (w >= 0) & (v + w <= 1) & (denominator > 1e-24)
        distance = ((p[:, None, :] - projected) ** 2).sum(2)
        distance[~valid] = np.inf
        for start, end in ((a, b), (b, c), (c, a)):
            edge = end - start
            t = np.clip(((p[:, None, :] - start) * edge).sum(2) /
                        np.maximum((edge * edge).sum(1), 1e-30), 0, 1)
            q = start + t[:, :, None] * edge
            candidate = ((p[:, None, :] - q) ** 2).sum(2)
            take = candidate < distance
            projected[take], distance[take] = q[take], candidate[take]
        result.append(projected[np.arange(len(p)), distance.argmin(1)])
    q = np.concatenate(result)
    return q, np.linalg.norm(points - q, axis=1)


def inside(points, triangles):
    """Generalized winding number: independent of outward winding direction."""
    winding = []
    for p in np.array_split(points, max(1, (len(points) + 63) // 64)):
        a, b, c = (triangles[None, :, :, :] - p[:, None, None, :]).transpose(2, 0, 1, 3)
        la, lb, lc = (np.linalg.norm(v, axis=2) for v in (a, b, c))
        determinant = np.einsum('ijk,ijk->ij', a, np.cross(b, c))
        denominator = la * lb * lc + (a * b).sum(2) * lc + (b * c).sum(2) * la + (c * a).sum(2) * lb
        winding.extend((2 * np.arctan2(determinant, denominator)).sum(1) / (4 * np.pi))
    return np.abs(winding) > .5


def signed_distance(points, triangles):
    q, distance = closest(points, triangles)
    return q, distance * np.where(inside(points, triangles), -1, 1)


def similarity(a, b, weights=None, scale=True):
    """Weighted Procrustes fit using row-vector coordinates; never reflects."""
    w = np.ones((len(a), 1)) if weights is None else np.asarray(weights).reshape(-1, 1)
    ma, mb = (a * w).sum(0) / w.sum(), (b * w).sum(0) / w.sum()
    aa, bb = a - ma, b - mb
    u, values, vt = np.linalg.svd((aa * w).T @ bb)
    sign = np.eye(3)
    sign[2, 2] = np.linalg.det(u @ vt)
    rotation = u @ sign @ vt
    factor = (values * np.diag(sign)).sum() / (aa * aa * w).sum() if scale else 1.
    matrix = rotation * factor
    return matrix, mb - ma @ matrix
