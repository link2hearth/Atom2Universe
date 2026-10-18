// Offline GLB/Draco helpers. npm ci --prefix tools/anatomy
import assert from 'node:assert/strict';
import draco from 'draco3dgltf';

export const decoder = await draco.createDecoderModule({});
export const EXTENSION = 'KHR_draco_mesh_compression';

export function readGlb(raw) {
  assert.equal(raw.readUInt32LE(0), 0x46546c67);
  assert.equal(raw.readUInt32LE(4), 2);
  assert.equal(raw.readUInt32LE(8), raw.length);
  const size = raw.readUInt32LE(12);
  assert.equal(raw.readUInt32LE(16), 0x4e4f534a);
  assert.equal(raw.readUInt32LE(24 + size), 0x004e4942);
  return { gltf: JSON.parse(raw.subarray(20, 20 + size)), binary: raw.subarray(28 + size) };
}

export function writeGlb(gltf, binary) {
  const json = Buffer.from(JSON.stringify(gltf));
  const padded = Buffer.concat([json, Buffer.alloc((-json.length >>> 0) % 4, 32)]);
  const bin = Buffer.concat([binary, Buffer.alloc((-binary.length >>> 0) % 4)]);
  const header = Buffer.alloc(20);
  [0x46546c67, 2, 28 + padded.length + bin.length, padded.length, 0x4e4f534a]
    .forEach((n, i) => header.writeUInt32LE(n, i * 4));
  const binHeader = Buffer.alloc(8);
  binHeader.writeUInt32LE(bin.length); binHeader.writeUInt32LE(0x004e4942, 4);
  return Buffer.concat([header, padded, binHeader, bin]);
}

export function bounds(p) {
  const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
  for (let i = 0; i < p.length; i++) {
    assert(Number.isFinite(p[i]));
    min[i % 3] = Math.min(min[i % 3], p[i]); max[i % 3] = Math.max(max[i % 3], p[i]);
  }
  return { min, max };
}

export function accessor(gltf, binary, index) {
  const a = gltf.accessors[index], view = gltf.bufferViews[a.bufferView];
  assert(!a.sparse && !a.normalized);
  const Type = { 5126: Float32Array, 5125: Uint32Array, 5123: Uint16Array }[a.componentType];
  const width = { VEC3: 3, SCALAR: 1 }[a.type], step = Type.BYTES_PER_ELEMENT;
  assert(Type && width);
  const result = new Type(a.count * width);
  const bytes = new DataView(binary.buffer, binary.byteOffset, binary.byteLength);
  const read = { 5126: 'getFloat32', 5125: 'getUint32', 5123: 'getUint16' }[a.componentType];
  const start = (view.byteOffset || 0) + (a.byteOffset || 0);
  for (let i = 0; i < a.count; i++) for (let j = 0; j < width; j++)
    result[i * width + j] = bytes[read](start + i * (view.byteStride || width * step) + j * step, true);
  return result;
}

export function decodeMesh(data, attributes) {
  const d = new decoder.Decoder(), input = new decoder.DecoderBuffer(), mesh = new decoder.Mesh();
  input.Init(new Int8Array(data.buffer, data.byteOffset, data.byteLength), data.length);
  const status = d.DecodeBufferToMesh(input, mesh);
  try {
    assert(status.ok(), status.error_msg());
    const result = {};
    for (const [semantic, id] of Object.entries(attributes)) {
      const attribute = d.GetAttributeByUniqueId(mesh, id);
      assert(attribute.ptr && attribute.num_components() === 3);
      const count = mesh.num_points() * 3, ptr = decoder._malloc(count * 4);
      try {
        assert(d.GetAttributeDataArrayForAllPoints(mesh, attribute, decoder.DT_FLOAT32, count * 4, ptr));
        result[semantic] = new Float32Array(decoder.HEAPF32.buffer, ptr, count).slice();
      } finally { decoder._free(ptr); }
    }
    const count = mesh.num_faces() * 3, ptr = decoder._malloc(count * 4);
    try {
      assert(d.GetTrianglesUInt32Array(mesh, count * 4, ptr));
      result.indices = new Uint32Array(decoder.HEAPU32.buffer, ptr, count).slice();
    } finally { decoder._free(ptr); }
    return result;
  } finally {
    decoder.destroy(status); decoder.destroy(mesh); decoder.destroy(input); decoder.destroy(d);
  }
}

export function geometry(gltf, binary, primitive) {
  const extension = primitive.extensions?.[EXTENSION];
  if (extension) {
    const view = gltf.bufferViews[extension.bufferView];
    return decodeMesh(binary.subarray(view.byteOffset || 0, (view.byteOffset || 0) + view.byteLength), extension.attributes);
  }
  return { POSITION: accessor(gltf, binary, primitive.attributes.POSITION),
    NORMAL: accessor(gltf, binary, primitive.attributes.NORMAL),
    indices: Uint32Array.from(accessor(gltf, binary, primitive.indices)) };
}

// Component identities use positions, so split lighting normals do not create fake islands.
export function connectivity(p, indices) {
  const parent = Int32Array.from({ length: p.length / 3 }, (_, i) => i), exact = new Map();
  function root(i) {
    while (parent[i] !== i) { parent[i] = parent[parent[i]]; i = parent[i]; }
    return i;
  }
  for (let i = 0; i < parent.length; i++) {
    const key = `${p[i * 3]},${p[i * 3 + 1]},${p[i * 3 + 2]}`;
    if (exact.has(key)) parent[i] = exact.get(key); else exact.set(key, i);
  }
  for (let i = 0; i < indices.length; i += 3) {
    const a = root(indices[i]);
    parent[root(indices[i + 1])] = a; parent[root(indices[i + 2])] = a;
  }
  const faces = new Map();
  for (let i = 0; i < indices.length; i += 3) {
    const r = root(indices[i]);
    if (!faces.has(r)) faces.set(r, i);
  }
  return { roots: Int32Array.from(parent, (_, i) => root(i)), faces };
}

export function decodedGlb(raw) {
  const { gltf, binary } = readGlb(raw);
  if (!gltf.extensionsUsed?.includes(EXTENSION)) return raw;
  const chunks = []; let size = 0;
  gltf.bufferViews = [];
  function add(a, values, target) {
    const padding = Buffer.alloc((-size >>> 0) % 4); chunks.push(padding); size += padding.length;
    a.bufferView = gltf.bufferViews.length; delete a.byteOffset;
    a.componentType = values instanceof Float32Array ? 5126 : 5125;
    gltf.bufferViews.push({ buffer: 0, byteOffset: size, byteLength: values.byteLength, target });
    chunks.push(Buffer.from(values.buffer, values.byteOffset, values.byteLength)); size += values.byteLength;
  }
  // Preserve original views while decoding; accessors of compressed meshes have no view.
  const original = readGlb(raw).gltf;
  for (const mesh of gltf.meshes) for (const primitive of mesh.primitives) {
    const data = geometry(original, binary, primitive);
    add(gltf.accessors[primitive.attributes.POSITION], data.POSITION, 34962);
    add(gltf.accessors[primitive.attributes.NORMAL], data.NORMAL, 34962);
    add(gltf.accessors[primitive.indices], data.indices, 34963);
    delete primitive.extensions;
  }
  delete gltf.extensionsUsed; delete gltf.extensionsRequired;
  gltf.buffers = [{ byteLength: size }];
  return writeGlb(gltf, Buffer.concat(chunks));
}
