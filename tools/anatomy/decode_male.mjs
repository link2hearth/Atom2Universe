// Decode the packaged (Draco) male atlas GLB into raw float32 positions + uint32 indices per mesh.
// node tools/anatomy/decode_male.mjs app/src/main/assets/science/biology/atlas-000.part.gzip app/build/anatomy-source/male-packaged
import { createRequire } from 'node:module';
import fs from 'node:fs';
import zlib from 'node:zlib';
import path from 'node:path';
const require = createRequire(import.meta.url);
const draco = require('draco3dgltf');
const [, , glbGzip, outDir] = process.argv;
const raw = zlib.gunzipSync(fs.readFileSync(glbGzip));
const jsonLen = raw.readUInt32LE(12);
const gltf = JSON.parse(raw.subarray(20, 20 + jsonLen).toString('utf8'));
const binLen = raw.readUInt32LE(20 + jsonLen);
const bin = raw.subarray(28 + jsonLen, 28 + jsonLen + binLen);
const decoderModule = await draco.createDecoderModule({});
const decoder = new decoderModule.Decoder();
fs.mkdirSync(outDir, { recursive: true });
const index = [];
const chunks = [];
let offset = 0;
for (const mesh of gltf.meshes) {
  for (const prim of mesh.primitives) {
    const ext = prim.extensions.KHR_draco_mesh_compression;
    const view = gltf.bufferViews[ext.bufferView];
    const data = bin.subarray(view.byteOffset || 0, (view.byteOffset || 0) + view.byteLength);
    const buffer = new decoderModule.DecoderBuffer();
    buffer.Init(new Int8Array(data), data.length);
    const type = decoder.GetEncodedGeometryType(buffer);
    if (type !== decoderModule.TRIANGULAR_MESH) throw new Error('not a mesh: ' + mesh.name);
    const dm = new decoderModule.Mesh();
    const status = decoder.DecodeBufferToMesh(buffer, dm);
    if (!status.ok()) throw new Error('decode failed: ' + mesh.name + ' ' + status.error_msg());
    const nv = dm.num_points(), nf = dm.num_faces();
    const posAttr = decoder.GetAttributeByUniqueId(dm, ext.attributes.POSITION);
    const pos = new decoderModule.DracoFloat32Array();
    decoder.GetAttributeFloatForAllPoints(dm, posAttr, pos);
    const positions = new Float32Array(nv * 3);
    for (let i = 0; i < nv * 3; i++) positions[i] = pos.GetValue(i);
    const faces = new Uint32Array(nf * 3);
    const ia = new decoderModule.DracoInt32Array();
    for (let i = 0; i < nf; i++) {
      decoder.GetFaceFromMesh(dm, i, ia);
      faces[i * 3] = ia.GetValue(0); faces[i * 3 + 1] = ia.GetValue(1); faces[i * 3 + 2] = ia.GetValue(2);
    }
    decoderModule.destroy(ia); decoderModule.destroy(pos); decoderModule.destroy(dm); decoderModule.destroy(buffer);
    const material = gltf.materials[prim.material];
    index.push({ name: mesh.name, vertices: nv, faces: nf, posOffset: offset, idxOffset: offset + positions.byteLength,
      color: material.pbrMetallicRoughness.baseColorFactor });
    chunks.push(Buffer.from(positions.buffer), Buffer.from(faces.buffer));
    offset += positions.byteLength + faces.byteLength;
  }
}
fs.writeFileSync(path.join(outDir, 'male_meshes.bin'), Buffer.concat(chunks));
fs.writeFileSync(path.join(outDir, 'male_meshes.json'), JSON.stringify(index));
console.log('meshes', index.length, 'bytes', offset);
