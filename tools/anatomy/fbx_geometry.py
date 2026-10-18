"""Read the static Blender FBX 7.4 exports used by Z-Anatomy.

This intentionally supports only the transform subset present in these exports.
Unsupported pivots, inheritance or animation fail instead of misplacing anatomy.
No embedded code, external textures or other FBX resources are executed/loaded.
"""
from pathlib import Path
import struct
import zlib

import numpy as np


def triangulate(polygon, vertices):
    if len(polygon) == 3:
        return [tuple(polygon)]
    points = vertices[polygon]
    normal = np.cross(points, np.roll(points, -1, axis=0)).sum(axis=0)
    points = np.delete(points, np.argmax(np.abs(normal)), axis=1)
    def cross(a, b):
        return a[0] * b[1] - a[1] * b[0]
    area = sum(cross(a, b) for a, b in zip(points, np.roll(points, -1, axis=0)))
    sign = 1 if area > 0 else -1
    epsilon = max(float(np.ptp(points, axis=0).max()) ** 2 * 1e-12, 1e-30)
    remaining = list(range(len(polygon)))
    faces = []
    while len(remaining) > 3:
        for k, mid in enumerate(remaining):
            before, after = remaining[k - 1], remaining[(k + 1) % len(remaining)]
            a, b, c = points[[before, mid, after]]
            if sign * cross(b - a, c - b) <= epsilon:
                continue
            def inside(p):
                return all(sign * cross(y - x, p - x) >= -epsilon
                           for x, y in ((a, b), (b, c), (c, a)))
            if any(inside(points[i]) for i in remaining if i not in (before, mid, after)):
                continue
            faces.append(tuple(polygon[i] for i in (before, mid, after)))
            remaining.pop(k)
            break
        else:
            raise ValueError('Cannot triangulate FBX polygon without changing its boundary')
    faces.append(tuple(polygon[i] for i in remaining))
    return faces


def read(path):
    data = Path(path).read_bytes()
    assert data[:23] == b'Kaydara FBX Binary  \x00\x1a\x00'
    assert struct.unpack_from('<I', data, 23)[0] == 7400

    def prop(pos):
        tag = chr(data[pos]); pos += 1
        formats = {'Y': 'h', 'C': '?', 'I': 'i', 'F': 'f', 'D': 'd', 'L': 'q'}
        if tag in formats:
            fmt = '<' + formats[tag]
            return struct.unpack_from(fmt, data, pos)[0], pos + struct.calcsize(fmt)
        if tag in 'fdlibc':
            count, encoding, size = struct.unpack_from('<III', data, pos); pos += 12
            assert encoding in (0, 1)
            raw = data[pos:pos + size]
            if encoding:
                raw = zlib.decompress(raw)
            dtype = {'f': '<f4', 'd': '<f8', 'l': '<i8', 'i': '<i4', 'b': 'u1', 'c': 'u1'}[tag]
            array = np.frombuffer(raw, dtype=dtype)
            assert len(array) == count
            return array, pos + size
        if tag in 'SR':
            size = struct.unpack_from('<I', data, pos)[0]; pos += 4
            raw = data[pos:pos + size]
            return raw.decode('utf-8') if tag == 'S' else raw, pos + size
        raise ValueError(f'Unsupported FBX property {tag}')

    def node(pos):
        end, count, _, length = struct.unpack_from('<IIIB', data, pos)
        if end == 0:
            return None, pos + 13
        assert pos < end <= len(data)
        pos += 13
        name = data[pos:pos + length].decode(); pos += length
        props, children = [], []
        for _ in range(count):
            value, pos = prop(pos); props.append(value)
        while pos < end:
            value, pos = node(pos)
            if value is None:
                break
            children.append(value)
        assert pos == end, name
        return (name, props, children), end

    roots, pos = [], 27
    while True:
        value, pos = node(pos)
        if value is None:
            break
        roots.append(value)
    return roots


def properties(node):
    result = {}
    for child in node[2]:
        if child[0] == 'Properties70':
            for _, values, _ in child[2]:
                result[values[0]] = values[4:]
    return result


def meshes(path, names=None, positions_only=False):
    roots = read(path)
    objects = next(n[2] for n in roots if n[0] == 'Objects')
    connections = next(n[2] for n in roots if n[0] == 'Connections')
    assert not any(n[0] in ('Deformer', 'AnimationStack') for n in objects)
    by_id = {n[1][0]: n for n in objects}
    parents, geometry = {}, {}
    for _, values, _ in connections:
        if values[0] != 'OO':
            continue
        child, parent = values[1:3]
        kind = by_id.get(child, ('',))[0]
        if kind == 'Model':
            parents[child] = parent
        elif kind == 'Geometry':
            assert parent not in geometry
            geometry[parent] = child

    def matrix(identity):
        if identity == 0:
            return np.eye(4)
        obj = by_id[identity]
        props = properties(obj)
        for name in ('PreRotation', 'PostRotation', 'RotationOffset', 'RotationPivot',
                     'ScalingOffset', 'ScalingPivot', 'GeometricTranslation', 'GeometricRotation'):
            assert np.allclose(props.get(name, [0, 0, 0]), 0), (obj[1], name)
        assert np.allclose(props.get('GeometricScaling', [1, 1, 1]), 1)
        assert props.get('RotationOrder', [0]) == [0], obj[1]
        # RSrs is ordinary matrix composition; other inheritance is safe here
        # only when the parent scale is uniform (checked below).
        parent = matrix(parents.get(identity, 0))
        if props.get('InheritType', [0]) != [1]:
            gram = parent[:3, :3].T @ parent[:3, :3]
            assert np.allclose(gram, np.eye(3) * gram[0, 0], atol=1e-5), obj[1]
            assert props.get('InheritType', [0]) == [0], obj[1]
        x, y, z = np.radians(props.get('Lcl Rotation', [0, 0, 0]))
        cx, cy, cz = np.cos([x, y, z]); sx, sy, sz = np.sin([x, y, z])
        rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
        ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
        rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
        local = np.eye(4)
        local[:3, :3] = rz @ ry @ rx @ np.diag(props.get('Lcl Scaling', [1, 1, 1]))
        local[:3, 3] = props.get('Lcl Translation', [0, 0, 0])
        return parent @ local

    result = {}
    for model_id, geometry_id in geometry.items():
        obj = by_id[model_id]
        name = obj[1][1].split('\x00')[0]
        if names is not None and name not in names:
            continue
        children = {n[0]: n[1] for n in by_id[geometry_id][2]}
        vertices = children['Vertices'][0].reshape(-1, 3)
        polygons = [] if positions_only else children['PolygonVertexIndex'][0]
        faces, polygon = [], []
        for index in polygons:
            polygon.append(int(index if index >= 0 else -index - 1))
            if index < 0:
                assert len(polygon) >= 3
                faces.extend(triangulate(polygon, vertices))
                polygon = []
        assert not polygon
        transform = matrix(model_id)
        vertices = vertices @ transform[:3, :3].T + transform[:3, 3]
        faces = np.array(faces, dtype=np.int32).reshape(-1, 3)
        if np.linalg.det(transform[:3, :3]) < 0:
            faces = faces[:, [0, 2, 1]]
        assert name not in result
        assert np.isfinite(vertices).all()
        assert positions_only or (faces.min() >= 0 and faces.max() < len(vertices))
        result[name] = (vertices, faces)
    return result
