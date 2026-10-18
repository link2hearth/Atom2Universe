/**
 * Final packaging pass, AFTER build_skeleton.py / build_detail_atlases.py (and display variants).
 * npm ci --prefix tools/anatomy
 * node tools/anatomy/optimize_mobile.mjs                 # stage, decode and validate all atlases
 * node tools/anatomy/optimize_mobile.mjs --write         # then publish validated assets
 * node tools/anatomy/optimize_mobile.mjs --validate      # validate current packaged assets
 * node tools/anatomy/optimize_mobile.mjs --decode input.glb output.glb
 *
 * Originals, decoded previews and measurements stay in app/build/anatomy-source/mobile.
 * Never simplify a previously simplified atlas. Re-running uses the hash-checked original,
 * or requires rebuilding the source atlas if that local backup has been removed.
 * Filament 1.75.1 gltfio-android already includes the Draco decoder.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { gzipSync, gunzipSync } from 'node:zlib';
import draco from 'draco3dgltf';
import { MeshoptSimplifier } from 'meshoptimizer/simplifier';
import validator from 'gltf-validator';
import { EXTENSION, readGlb, writeGlb, bounds, geometry, decodeMesh, connectivity, decodedGlb } from './mobile_geometry.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const ASSETS = path.join(ROOT, 'app/src/main/assets/science/biology');
const WORK = path.join(ROOT, 'app/build/anatomy-source/mobile');
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const readJson = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const writeJson = (p, value) => fs.writeFileSync(p, JSON.stringify(value, null, 2) + '\n');
const args = process.argv.slice(2);
if (args[0] === '--decode') {
  assert.equal(args.length, 3); fs.writeFileSync(args[2], decodedGlb(fs.readFileSync(args[1]))); process.exit(0);
}
assert(args.every(a => ['--write', '--validate'].includes(a)));
const encoder = await draco.createEncoderModule({});
await MeshoptSimplifier.ready;

function settings(atlas, record, p) {
  const b = bounds(p), extent = Math.max(...b.max.map((v, i) => v - b.min[i]));
  const delicate = ['nervous', 'vascular', 'lymphatic', 'senses', 'cartilage'].includes(record.layer);
  const cap = atlas === 'ear' ? .000025 : delicate ? .00012 : record.layer === 'skeleton' ? .0003 : .0007;
  return { ratio: delicate ? .35 : .20, error: Math.min(cap, extent * (delicate ? .0015 : .003)),
    positionBits: delicate || atlas === 'ear' ? 16 : 14, normalBits: 10 };
}

function reduce(data, config) {
  let { POSITION: p, NORMAL: n, indices } = data;
  // Exact attribute welding only: hard lighting edges keep their separate normals.
  const keys = new Map(), remap = new Uint32Array(p.length / 3), positions = [], normals = [];
  for (let i = 0; i < remap.length; i++) {
    const key = `${p[i*3]},${p[i*3+1]},${p[i*3+2]},${n[i*3]},${n[i*3+1]},${n[i*3+2]}`;
    let j = keys.get(key);
    if (j === undefined) {
      j = positions.length / 3; keys.set(key, j);
      positions.push(p[i*3], p[i*3+1], p[i*3+2]); normals.push(n[i*3], n[i*3+1], n[i*3+2]);
    }
    remap[i] = j;
  }
  p = Float32Array.from(positions); n = Float32Array.from(normals);
  indices = Uint32Array.from(indices, i => remap[i]);
  const components = connectivity(p, indices), locks = new Uint8Array(p.length / 3);
  // Keep a triangle in every disconnected island, including tiny anatomical branches.
  for (const offset of components.faces.values()) for (let j = 0; j < 3; j++) locks[indices[offset + j]] = 1;
  // Lock extrema; removal of degenerate triangles can still change bounds slightly.
  for (let axis = 0; axis < 3; axis++) {
    let lo = 0, hi = 0;
    for (let i = 1; i < locks.length; i++) {
      if (p[i*3+axis] < p[lo*3+axis]) lo = i;
      if (p[i*3+axis] > p[hi*3+axis]) hi = i;
    }
    locks[lo] = locks[hi] = 1;
  }
  let error = 0;
  const originalIndices = indices;
  if (indices.length > 512 * 3) {
    const target = Math.max(512, Math.floor(indices.length / 3 * config.ratio)) * 3;
    [indices, error] = MeshoptSimplifier.simplifyWithAttributes(indices, p, 3, n, 3, [.5,.5,.5], locks,
      target, config.error, ['LockBorder', 'ErrorAbsolute']);
  }
  const retained = new Set(Array.from(indices, i => components.roots[i]));
  if (retained.size !== components.faces.size) {
    // Degenerate tiny islands may be discarded despite locks. Restore those islands in full.
    const restore = [];
    for (let i = 0; i < originalIndices.length; i += 3) if (!retained.has(components.roots[originalIndices[i]]))
      restore.push(originalIndices[i], originalIndices[i+1], originalIndices[i+2]);
    const joined = new Uint32Array(indices.length + restore.length);
    joined.set(indices); joined.set(restore, indices.length); indices = joined;
  }
  assert.equal(new Set(Array.from(indices, i => components.roots[i])).size, components.faces.size);
  const [compact, count] = MeshoptSimplifier.compactMesh(indices);
  const vp = new Float32Array(count * 3), vn = new Float32Array(count * 3);
  for (let i = 0; i < compact.length; i++) if (compact[i] !== 0xffffffff) {
    vp.set(p.subarray(i*3, i*3+3), compact[i]*3); vn.set(n.subarray(i*3, i*3+3), compact[i]*3);
  }
  return { POSITION: vp, NORMAL: vn, indices, error, components: components.faces.size };
}

function encode(data, config) {
  const e = new encoder.Encoder(), builder = new encoder.MeshBuilder(), mesh = new encoder.Mesh();
  const output = new encoder.DracoInt8Array();
  try {
    builder.AddFacesToMesh(mesh, data.indices.length / 3, data.indices);
    const attributes = {
      POSITION: builder.AddFloatAttributeToMesh(mesh, encoder.POSITION, data.POSITION.length / 3, 3, data.POSITION),
      NORMAL: builder.AddFloatAttributeToMesh(mesh, encoder.NORMAL, data.NORMAL.length / 3, 3, data.NORMAL),
    };
    e.SetSpeedOptions(5, 5);
    e.SetEncodingMethod(config.sequential ? encoder.MESH_SEQUENTIAL_ENCODING : encoder.MESH_EDGEBREAKER_ENCODING);
    if (config.positionBits) e.SetAttributeQuantization(encoder.POSITION, config.positionBits);
    e.SetAttributeQuantization(encoder.NORMAL, config.normalBits);
    const length = e.EncodeMeshToDracoBuffer(mesh, output); assert(length > 0);
    const bytes = Buffer.alloc(length);
    for (let i = 0; i < length; i++) bytes[i] = output.GetValue(i);
    return { bytes, attributes };
  } finally { for (const obj of [output, mesh, builder, e]) encoder.destroy(obj); }
}

function unpack(directory) {
  const catalog = readJson(path.join(directory, 'catalog.json'));
  const provenance = readJson(path.join(directory, 'provenance.json'));
  const raw = Buffer.concat(catalog.modelParts.map((name, i) => {
    assert.equal(name, `atlas-${String(i).padStart(3, '0')}.part.gzip`);
    const part = fs.readFileSync(path.join(directory, name)), manifest = provenance.packaging.parts[i];
    assert.equal(name, manifest.file); assert.equal(part.length, manifest.bytes); assert.equal(hash(part), manifest.sha256);
    return gunzipSync(part);
  }));
  assert.equal(raw.length, catalog.modelBytes); assert.equal(hash(raw), provenance.packaging.modelSha256);
  return { raw, catalog, provenance };
}

async function checkGlb(raw) {
  // Khronos does not decode Draco: also validate the fully decoded counterpart below.
  const report = await validator.validateBytes(new Uint8Array(raw), { maxIssues: 40 });
  assert.equal(report.issues.numErrors, 0, JSON.stringify(report.issues));
  return report.issues;
}

async function validate(directory, atlas) {
  const { raw, catalog, provenance } = unpack(directory), { gltf, binary } = readGlb(raw);
  const mapping = catalog.externalHiddenVariants || {}, records = new Map(catalog.structures.map(s => [s.id, s]));
  const expected = [...catalog.structures.filter(s => s.mesh).map(s => s.id), ...Object.keys(mapping)].sort();
  assert.deepEqual(gltf.nodes.map(n => n.name).sort(), expected);
  assert.equal(gltf.nodes.length, gltf.meshes.length); assert.equal(gltf.materials.length, gltf.meshes.length);
  assert.deepEqual(gltf.scenes[gltf.scene].nodes, gltf.nodes.map((_, i) => i));
  let triangles = 0, allTriangles = 0, vertices = 0;
  for (const node of gltf.nodes) {
    assert(!['matrix','translation','rotation','scale','children'].some(k => k in node));
    const record = records.get(mapping[node.name] || node.name), primitive = gltf.meshes[node.mesh].primitives[0];
    assert.equal(gltf.meshes[node.mesh].primitives.length, 1);
    assert.equal(primitive.material, node.mesh); // Independent highlighting must survive.
    const data = geometry(gltf, binary, primitive), b = bounds(data.POSITION), count = data.POSITION.length / 3;
    assert(count > 0 && data.indices.length > 0 && data.indices.length % 3 === 0);
    assert.equal(data.NORMAL.length, data.POSITION.length);
    for (const i of data.indices) assert(i < count);
    for (let i = 0; i < data.NORMAL.length; i += 3)
      assert(Math.abs(Math.hypot(...data.NORMAL.subarray(i, i+3)) - 1) < 1e-5);
    const expectedBounds = mapping[node.name] ? catalog.externalHiddenBounds[mapping[node.name]] : record;
    assert.deepEqual(b.min, expectedBounds.min); assert.deepEqual(b.max, expectedBounds.max);
    const material = gltf.materials[primitive.material];
    if (record.color) assert.deepEqual(material.pbrMetallicRoughness.baseColorFactor, [...record.color, record.opacity ?? 1]);
    if (mapping[node.name]) {
      const manifest = provenance.externalVisibility.variants[node.name];
      assert.equal(hash(Buffer.from(data.POSITION.buffer)), manifest.verticesSha256);
      assert.equal(hash(Buffer.from(data.indices.buffer)), manifest.indicesSha256);
    } else triangles += data.indices.length / 3;
    allTriangles += data.indices.length / 3; vertices += count;
  }
  assert.equal(triangles, provenance.triangles);
  assert.equal(allTriangles, provenance.mobileOptimization.outputTriangles);
  const packedValidation = await checkGlb(raw), decoded = decodedGlb(raw), decodedValidation = await checkGlb(decoded);
  fs.mkdirSync(path.join(WORK, atlas), { recursive: true });
  fs.writeFileSync(path.join(WORK, atlas, 'decoded.glb'), decoded);
  writeJson(path.join(WORK, atlas, 'validation.json'), { packedValidation, decodedValidation });
  console.log(`${atlas}: validated ${gltf.nodes.length} meshes, ${allTriangles} triangles, ${vertices} vertices`);
}

async function optimize(atlas) {
  const directory = path.join(ASSETS, atlas === 'male' ? '' : atlas);
  const current = unpack(directory), work = path.join(WORK, atlas), original = path.join(work, 'original');
  fs.mkdirSync(original, { recursive: true });
  if (current.provenance.mobileOptimization) {
    assert(fs.existsSync(path.join(original, 'catalog.json')), 'Rebuild the source atlas first: local original is missing');
    assert.equal(unpack(original).provenance.packaging.modelSha256, current.provenance.mobileOptimization.sourceModelSha256);
  } else {
    for (const name of ['catalog.json', 'provenance.json', ...current.catalog.modelParts])
      fs.copyFileSync(path.join(directory, name), path.join(original, name));
  }
  const { raw, catalog, provenance } = unpack(original), { gltf, binary } = readGlb(raw);
  assert(!gltf.extensionsUsed?.length && !gltf.animations && !gltf.skins && !gltf.images);
  const output = structuredClone(gltf); output.bufferViews = []; output.accessors = [];
  const records = new Map(catalog.structures.map(s => [s.id, s])), variants = catalog.externalHiddenVariants || {};
  const chunks = [], measurements = []; let offset = 0, triangles = 0;
  function addAccessor(values, type) {
    const a = { componentType: values instanceof Float32Array ? 5126 : 5125,
      count: values.length / (type === 'VEC3' ? 3 : 1), type };
    if (type === 'VEC3') Object.assign(a, bounds(values));
    output.accessors.push(a); return output.accessors.length - 1;
  }
  for (const [i, mesh] of gltf.meshes.entries()) {
    const primitive = mesh.primitives[0]; assert.equal(mesh.primitives.length, 1);
    const identity = variants[mesh.name] || mesh.name, record = records.get(identity); assert(record);
    const source = geometry(gltf, binary, primitive), config = settings(atlas, record, source.POSITION);
    const reduced = reduce(source, config);
    let encoded = encode(reduced, config), decoded = decodeMesh(encoded.bytes, encoded.attributes);
    if (decoded.indices.length !== reduced.indices.length) {
      // Edgebreaker drops degenerate faces; sequential encoding retains the complete topology.
      config.sequential = true; encoded = encode(reduced, config); decoded = decodeMesh(encoded.bytes, encoded.attributes);
    }
    assert.equal(decoded.indices.length, reduced.indices.length, mesh.name);
    let decodedComponents = connectivity(decoded.POSITION, decoded.indices).faces.size;
    for (const bits of [20, 0]) {
      if (decodedComponents === reduced.components) break;
      // Close but separate islands must not be joined by the quantization grid.
      config.positionBits = bits;
      encoded = encode(reduced, config); decoded = decodeMesh(encoded.bytes, encoded.attributes);
      decodedComponents = connectivity(decoded.POSITION, decoded.indices).faces.size;
    }
    assert.equal(decodedComponents, reduced.components, mesh.name + ' disconnected islands');
    const b = bounds(decoded.POSITION), oldBounds = bounds(source.POSITION);
    const span = Math.max(...oldBounds.max.map((v, axis) => v - oldBounds.min[axis]));
    const quantizationBound = (config.positionBits ? span / (2 ** config.positionBits - 1) : 0) + 2e-7;
    for (const key of ['min','max']) for (let axis = 0; axis < 3; axis++)
      assert(Math.abs(b[key][axis] - oldBounds[key][axis]) <= config.error + quantizationBound,
        JSON.stringify({ id: mesh.name, key, axis, before: oldBounds, reduced: bounds(reduced.POSITION), after: b, quantizationBound }));
    const padding = Buffer.alloc((-offset >>> 0) % 4); chunks.push(padding); offset += padding.length;
    const view = output.bufferViews.length;
    output.bufferViews.push({ buffer: 0, byteOffset: offset, byteLength: encoded.bytes.length });
    chunks.push(encoded.bytes); offset += encoded.bytes.length;
    output.meshes[i].primitives = [{ ...primitive, attributes: {
      POSITION: addAccessor(decoded.POSITION, 'VEC3'), NORMAL: addAccessor(decoded.NORMAL, 'VEC3') },
      indices: addAccessor(decoded.indices, 'SCALAR'),
      extensions: { [EXTENSION]: { bufferView: view, attributes: encoded.attributes } } }];
    if (variants[mesh.name]) {
      catalog.externalHiddenBounds[identity] = b;
      provenance.externalVisibility.variants[mesh.name] = { verticesSha256: hash(Buffer.from(decoded.POSITION.buffer)),
        indicesSha256: hash(Buffer.from(decoded.indices.buffer)), triangles: decoded.indices.length / 3 };
    } else { Object.assign(record, b); triangles += decoded.indices.length / 3; }
    measurements.push({ id: mesh.name, inputTriangles: source.indices.length / 3, outputTriangles: decoded.indices.length / 3,
      inputVertices: source.POSITION.length / 3, outputVertices: decoded.POSITION.length / 3,
      components: reduced.components, simplifierErrorMetres: reduced.error, ...config });
    if ((i + 1) % 200 === 0) console.log(`${atlas}: ${i + 1}/${gltf.meshes.length} meshes`);
  }
  output.buffers = [{ byteLength: offset }]; output.extensionsUsed = [EXTENSION]; output.extensionsRequired = [EXTENSION];
  output.asset.generator = 'Atom2Universe mobile anatomy (meshoptimizer + Draco)';
  const result = writeGlb(output, Buffer.concat(chunks));
  const stage = path.join(work, 'optimized'); fs.mkdirSync(stage, { recursive: true });
  const parts = [];
  for (let start = 0; start < result.length; start += 48 * 1024 * 1024) {
    const file = `atlas-${String(parts.length).padStart(3, '0')}.part.gzip`;
    const bytes = gzipSync(result.subarray(start, start + 48 * 1024 * 1024), { level: 9 });
    fs.writeFileSync(path.join(stage, file), bytes); parts.push({ file, bytes: bytes.length, sha256: hash(bytes) });
  }
  const sum = key => measurements.reduce((n, row) => n + row[key], 0);
  provenance.mobileOptimization = { version: 1, sourceModelSha256: hash(raw),
    toolSha256: hash(fs.readFileSync(fileURLToPath(import.meta.url))),
    dependencies: readJson(path.join(ROOT, 'tools/anatomy/package.json')).dependencies,
    inputBytes: provenance.packaging.parts.reduce((n, p) => n + p.bytes, 0),
    outputBytes: parts.reduce((n, p) => n + p.bytes, 0), inputTriangles: sum('inputTriangles'),
    outputTriangles: sum('outputTriangles'), inputVertices: sum('inputVertices'), outputVertices: sum('outputVertices'),
    method: 'Mobile display derivative: exact attribute welding; error-bounded meshoptimizer simplification with locked borders, extrema and a triangle in every connected island; per-mesh Draco position quantization (14/16 bits, 20 or lossless when needed to keep islands separate) and normals (10 bits). Names, materials, layers and display variants retained.',
    maximumSimplifierErrorMetres: Math.max(...measurements.map(m => m.simplifierErrorMetres)),
    sourceGeometryNote: 'Source descriptions and registrations above describe the imported originals. Packaged surfaces are now simplified mobile derivatives, not byte-identical source geometry.' };
  provenance.triangles = triangles;
  provenance.packaging = { ...provenance.packaging, format: 'Draco-compressed GLB in independent gzip members; concatenate decompressed parts in modelParts order',
    modelBytes: result.length, modelSha256: hash(result), parts };
  catalog.modelBytes = result.length; catalog.modelParts = parts.map(p => p.file);
  writeJson(path.join(stage, 'catalog.json'), catalog); writeJson(path.join(stage, 'provenance.json'), provenance);
  writeJson(path.join(work, 'meshes.json'), measurements);
  await validate(stage, atlas);
  console.log(`${atlas}: ${(provenance.mobileOptimization.inputBytes / 1e6).toFixed(2)} -> ${(provenance.mobileOptimization.outputBytes / 1e6).toFixed(2)} MB; ${sum('inputTriangles')} -> ${sum('outputTriangles')} triangles`);
  return { directory, stage, catalog, oldParts: current.catalog.modelParts };
}

const atlases = ['male', 'female', 'ear'];
if (args.includes('--validate')) {
  for (const atlas of atlases) await validate(path.join(ASSETS, atlas === 'male' ? '' : atlas), atlas);
} else {
  const results = [];
  for (const atlas of atlases) results.push(await optimize(atlas));
  // Publish only once all three staged atlases have decoded and passed validation.
  if (args.includes('--write')) for (const { directory, stage, catalog, oldParts } of results) {
    for (const name of [...catalog.modelParts, 'catalog.json', 'provenance.json'])
      fs.copyFileSync(path.join(stage, name), path.join(directory, name));
    for (const name of oldParts) if (!catalog.modelParts.includes(name)) {
      assert(/^atlas-\d{3}\.part\.gzip$/.test(name)); fs.unlinkSync(path.join(directory, name));
    }
  }
}
