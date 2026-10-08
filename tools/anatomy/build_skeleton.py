"""Rebuild the anatomical atlas from BodyParts3D and Z-Anatomy.

Inputs (download separately from https://dbarchive.biosciencedbc.jp/en/bodyparts3d/download.html):
  app/build/anatomy-source/{bodyparts.zip,elements.tsv,partof_elements.tsv}
  app/build/anatomy-source/Z-Anatomy-MuscularSystem100.fbx (URL in facial_content.py)
  app/build/anatomy-source/Z-Anatomy-Joints100.fbx (URL in meniscus_content.py)
  app/build/anatomy-source/Z-Anatomy-VisceralSystem100.fbx (URL in visceral_content.py)
  app/build/anatomy-source/Z-Anatomy-NervousSystem100.fbx (URL in nervous_external_content.py)
  app/build/anatomy-source/Z-Anatomy-LymphoidOrgans100.fbx (URL in lymphatic_content.py)
Run from the repository root: python tools/anatomy/build_skeleton.py
Final mobile packaging (after all atlas builds): npm ci --prefix tools/anatomy
then node tools/anatomy/optimize_mobile.mjs --write (see its header for validation).
Python dependencies: numpy; fast-simplification==0.1.13 (can be installed with
pip --target app/build/anatomy-source/python). No missing anatomy is synthesized.
Direct BodyParts3D geometry retains source coordinates; external additions use
documented registration. See each provider for identity corrections and exclusions.
"""
import collections
import csv
import hashlib
import gzip
import json
import math
from pathlib import Path
import re
import struct
import sys
import zipfile
from xml.sax.saxutils import escape

sys.dont_write_bytecode = True
from skeleton_content import describe, summaries
import muscle_content
import facial_content
import cartilage_content
import meniscus_content
import organ_content
import visceral_content
import sensory_content
import nervous_content
import vascular_content
import connective_content
import skin_content
import trunk_muscle_content
import lymphatic_content

PROVIDERS = {'senses': sensory_content, 'nervous': nervous_content,
             'vascular': vascular_content, 'lymphatic': lymphatic_content,
             'connective': connective_content, 'skin': skin_content}

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app/build/anatomy-source"
ASSETS = ROOT / "app/src/main/assets/science/biology"
RES = ROOT / "app/src/main/res"
sys.path.insert(0, str(SOURCE / "python"))
from mesh_reduction import reduce


def xml_string(value):
    return escape(value).replace("'", "\\'").replace('"', '\\"')


def main():
    rows = list(csv.DictReader((SOURCE / "elements.tsv").open(encoding="utf-8"), delimiter="\t"))
    concepts = collections.defaultdict(list)
    for row in rows:
        concepts[row["concept id"]].append(row)
    partof = collections.defaultdict(list)
    for row in csv.DictReader((SOURCE / 'partof_elements.tsv').open(encoding='utf-8'), delimiter='\t'):
        partof[row['concept id']].append(row)
    singles = {v[0]["element file id"]: v[0] for v in concepts.values() if len(v) == 1}
    bones = {r["element file id"] for r in concepts["FMA5018"]}
    teeth = {r["element file id"] for r in concepts["FMA12516"]}
    grouped = {
        "FMA52749": ("hyoid bone", ["FJ2772", "FJ3201"], True, 1),
        "sternum": ("sternum", ["FJ3290", "FJ3178", "FJ3153"], True, 1),
        "FMA45098": ("sesamoid bones of left foot", ["FJ3266", "FJ3270"], False, 2),
        "FMA45097": ("sesamoid bones of right foot", ["FJ3372", "FJ3376"], False, 2),
    }
    grouped_files = {f for _, files, _, _ in grouped.values() for f in files}
    entries = []
    for element in sorted((bones | teeth) - grouped_files):
        row = singles[element]  # Fail rather than assign an uncertain identity.
        entries.append((row["concept id"], row["name"], [element], element in bones, 1))
    entries += [(key, name, files, standard, count) for key, (name, files, standard, count) in grouped.items()]
    for side in ("left", "right"):
        for bone in ("malleus", "incus", "stapes"):
            entries.append((f"reference_{side}_{bone}", f"{side} {bone}", [], True, 1))
    entries.append(("reference_coccyx", "coccyx", [], True, 1))
    assert sum(count for _, _, _, standard, count in entries if standard) == 206
    entries = [(*entry, 'skeleton') for entry in entries]
    entries += [(*entry, 'muscles') for entry in muscle_content.entries(concepts)]
    entries += [(*entry, 'muscles') for entry in facial_content.entries()]
    entries += [(*entry, 'muscles') for entry in trunk_muscle_content.entries()]
    entries += [(*entry, 'cartilage') for entry in cartilage_content.entries(concepts)]
    entries += [(*entry, 'cartilage') for entry in meniscus_content.entries()]
    organ_entries = organ_content.entries(concepts, partof)
    entries += [(*entry, 'organs') for entry in organ_entries]
    entries += [(*entry, 'organs') for entry in visceral_content.entries()]
    added_entries = {layer: provider.entries(concepts, partof) for layer, provider in PROVIDERS.items()}
    for layer, additions in added_entries.items():
        entries += [(*entry, layer) for entry in additions]
    all_summaries = {**summaries, **muscle_content.summaries, **facial_content.summaries,
                     **cartilage_content.summaries, **meniscus_content.summaries,
                     **organ_content.summaries, **visceral_content.summaries, **trunk_muscle_content.summaries}
    for provider in PROVIDERS.values():
        assert not (all_summaries.keys() & provider.summaries.keys()), provider
        all_summaries.update(provider.summaries)
    facial_meshes = facial_content.load(SOURCE)
    meniscus_meshes = meniscus_content.load(SOURCE)
    visceral_meshes = visceral_content.load(SOURCE)
    trunk_meshes = trunk_muscle_content.load(SOURCE)
    added_meshes = {layer: provider.load(SOURCE) if hasattr(provider, 'load') else {}
                    for layer, provider in PROVIDERS.items()}

    archive = zipfile.ZipFile(SOURCE / "bodyparts.zip")
    paths = {Path(n).stem: n for n in archive.namelist() if n.endswith(".obj")}
    print("OBJ files:", len(paths))
    binary = bytearray()
    gltf = {"asset": {"version": "2.0", "generator": "Atom2Universe anatomy importer",
                      "copyright": "Combined atlas: CC BY-SA 4.0. BodyParts3D, © The Database Center for Life Science (CC BY 4.0). Additional named surfaces: Z-Anatomy - The libre 3D atlas of anatomy (CC BY-SA 4.0), derived from BodyParts3D (CC BY-SA 2.1 Japan). See provenance.json and Z-ANATOMY-LICENSE.txt for the selected surfaces and additional attributions."},
            "scene": 0, "scenes": [{"nodes": []}], "nodes": [], "meshes": [],
            "materials": [], "bufferViews": [], "accessors": []}
    strings = {"en": {}, "fr": {}}
    catalog = []
    preview = []
    total_triangles = 0
    original_triangles = 0
    reductions = collections.Counter()

    def accessor(values, component_type, shape, target, bounds=False):
        while len(binary) % 4:
            binary.append(0)
        offset = len(binary)
        fmt = {5126: 'f', 5125: 'I', 5123: 'H'}[component_type]
        flat = [v for row in values for v in row] if shape == "VEC3" else values
        binary.extend(struct.pack(f"<{len(flat)}{fmt}", *flat))
        view = len(gltf["bufferViews"])
        gltf["bufferViews"].append({"buffer": 0, "byteOffset": offset, "byteLength": len(binary) - offset, "target": target})
        item = {"bufferView": view, "componentType": component_type, "count": len(values), "type": shape}
        if bounds:
            item.update(min=[min(p[i] for p in values) for i in range(3)], max=[max(p[i] for p in values) for i in range(3)])
        gltf["accessors"].append(item)
        return len(gltf["accessors"]) - 1

    for identity, source_name, files, standard, count, layer in entries:
        provider = PROVIDERS.get(layer)
        additional = source_name in added_meshes.get(layer, {})
        facial = source_name in facial_meshes
        meniscus = source_name in meniscus_meshes
        visceral = source_name in visceral_meshes
        trunk = source_name in trunk_meshes
        external = facial or meniscus or visceral or additional or trunk
        describe_structure = (trunk_muscle_content.describe if trunk else provider.describe if provider else visceral_content.describe if visceral else
                              organ_content.describe if layer == 'organs' else
                              meniscus_content.describe if meniscus else facial_content.describe if facial else
                              muscle_content.describe if layer == 'muscles' else
                              cartilage_content.describe if layer == 'cartilage' else describe)
        english, french, kind, region, wiki_en, wiki_fr = describe_structure(source_name)
        assert kind in all_summaries, source_name
        key = "bio_name_" + identity.lower()
        strings["en"][key] = english
        strings["fr"][key] = french
        record = {"id": identity, "name": key, "kind": kind, "region": region, "layer": layer,
                  "standard": standard, "boneCount": count if region != "teeth" else 0,
                  "mesh": bool(files), "elements": files, "sourceName": source_name,
                  "wikiEn": wiki_en, "wikiFr": wiki_fr}
        if layer == 'organs':
            record.update((visceral_content if visceral else organ_content).attributes(source_name))
        if provider:
            record.update(provider.attributes(source_name))
            assert record['category'] in provider.groups, source_name
        if trunk:
            record.update(trunk_muscle_content.attributes(source_name))
        if external:
            record['source'] = 'z-anatomy'
            record['sourceObject'] = source_name
            # The imported name remains in provenance; plain left/right names
            # also make bilingual search and independent laterality checks work.
            record['sourceName'] = english.lower()
        if layer == 'cartilage':
            level = cartilage_content.disc_level(source_name)
            if level:
                record['discLevel'] = list(level)
        if files:
            vertices, faces = [], []
            seen_geometry = set()
            for element in ([] if external else files):
                data = archive.read(paths[element]).decode("utf-8-sig")
                vv, ff = [], []
                for line in data.splitlines():
                    fields = line.split()
                    if not fields:
                        continue
                    if fields[0] == "v":
                        x, y, z = map(float, fields[1:4])
                        # Source: Z superior, Y posterior. glTF: Y up, Z anterior.
                        vv.append((x / 1000, z / 1000, -y / 1000))
                    elif fields[0] == "f":
                        face = [int(f.split("/")[0]) for f in fields[1:]]
                        face = [(i - 1 if i > 0 else len(vv) + i) for i in face]
                        for j in range(1, len(face) - 1):
                            ff.append((face[0], face[j], face[j + 1]))
                assert vv and ff, element
                assert all(math.isfinite(value) for vertex in vv for value in vertex), element
                assert all(0 <= i < len(vv) for f in ff for i in f), element
                if provider and hasattr(provider, 'clean_geometry'):
                    vv, ff = provider.clean_geometry(element, vv, ff)
                    if hasattr(vv, 'tolist'):
                        vv, ff = vv.tolist(), ff.tolist()
                signature = hashlib.sha256(repr((vv, ff)).encode()).hexdigest()
                if signature in seen_geometry:
                    continue
                seen_geometry.add(signature)
                offset = len(vertices)
                vertices.extend(vv)
                faces.extend(tuple(i + offset for i in f) for f in ff)
            if facial:
                vertices, faces = facial_meshes[source_name]
            elif meniscus:
                vertices, faces = meniscus_meshes[source_name]
            elif visceral:
                vertices, faces = visceral_meshes[source_name]
            elif additional:
                vertices, faces = added_meshes[layer][source_name]
            elif trunk:
                vertices, faces = trunk_meshes[source_name]
            if hasattr(vertices, 'tolist'):
                vertices, faces = vertices.tolist(), faces.tolist()
            original_triangles += len(faces)
            if layer == 'muscles' and not external:
                vertices, faces, reduction = reduce(vertices, faces)
                reductions[reduction] += 1
            normals = [[0., 0., 0.] for _ in vertices]
            for a, b, c in faces:
                u = [vertices[b][i] - vertices[a][i] for i in range(3)]
                v = [vertices[c][i] - vertices[a][i] for i in range(3)]
                normal = [u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0]]
                for index in (a, b, c):
                    for axis in range(3):
                        normals[index][axis] += normal[axis]
            for n in normals:
                length = math.sqrt(sum(x*x for x in n))
                if length > 1e-20:
                    n[:] = [x / length for x in n]
                else:
                    n[:] = [0., 1., 0.]
            position = accessor(vertices, 5126, "VEC3", 34962, True)
            normal = accessor(normals, 5126, "VEC3", 34962)
            indices = accessor([i for f in faces for i in f], 5123 if len(vertices) <= 65535 else 5125, "SCALAR", 34963)
            # One material per structure: highlighting must not recolor neighbouring bones.
            material = len(gltf["materials"])
            color = (record['color'] + [record.get('opacity', 1.)] if 'color' in record else
                     [0.62, 0.16, 0.14, 1.] if layer == 'muscles' else
                     [0.46, 0.72, 0.84, 1.] if layer == 'cartilage' else
                     [0.94, 0.91, 0.78, 1.] if region != "teeth" else [0.99, 0.98, 0.94, 1.])
            gltf["materials"].append({"name": identity, "pbrMetallicRoughness": {"baseColorFactor": color, "metallicFactor": 0., "roughnessFactor": 0.65}, "doubleSided": True})
            if color[3] < 1:
                gltf['materials'][-1]['alphaMode'] = 'BLEND'
            gltf["meshes"].append({"name": identity, "primitives": [{"attributes": {"POSITION": position, "NORMAL": normal}, "indices": indices, "material": material}]})
            gltf["scenes"][0]["nodes"].append(len(gltf["nodes"]))
            gltf["nodes"].append({"name": identity, "mesh": len(gltf["meshes"]) - 1})
            bounds = gltf["accessors"][position]
            record["min"] = bounds["min"]
            record["max"] = bounds["max"]
            total_triangles += len(faces)
            preview.append({"id": identity, "layer": layer, "color": color[:3], "opacity": color[3], "v": vertices, "f": faces})
        catalog.append(record)

    gltf["buffers"] = [{"byteLength": len(binary)}]
    assert len({r["id"] for r in catalog}) == len(catalog)
    assert len({node["name"] for node in gltf["nodes"]}) == len(gltf["nodes"])
    # Anatomical coverage, independently checked by region, including the seven sheet-only bones.
    expected = {"skull": 28, "neck": 1, "spine": 26, "thorax": 25, "upper": 10, "hands": 54, "pelvis": 2, "lower": 8, "feet": 52}
    actual = {region: sum(r["boneCount"] for r in catalog if r["standard"] and r["region"] == region) for region in expected}
    assert actual == expected, actual
    assert sum(r["region"] == "teeth" for r in catalog) == 28
    # A swapped anatomical side or a wrong axis conversion must not silently produce an atlas.
    for record in catalog:
        if record["mesh"]:
            if record["sourceName"] == "left femur":
                assert record["min"][0] > 0
            if record["sourceName"] == "right femur":
                assert record["max"][0] < 0
    payload = json.dumps(gltf, ensure_ascii=False, separators=(",", ":")).encode()
    payload += b" " * (-len(payload) % 4)
    binary.extend(b"\0" * (-len(binary) % 4))
    ASSETS.mkdir(parents=True, exist_ok=True)
    model = (struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(payload) + 8 + len(binary)) +
             struct.pack("<II", len(payload), 0x4E4F534A) + payload +
             struct.pack("<II", len(binary), 0x004E4942) + binary)
    # Lossless chunks keep individual repository assets below large-file limits.
    # Concatenating the decompressed chunks reproduces the original GLB exactly.
    parts = []
    part_size = 48 * 1024 * 1024
    for start in range(0, len(model), part_size):
        # Android's asset merger decompresses and renames files ending in .gz.
        # Keep gzip bytes under .gzip so the packaged name and format stay intact.
        name = f'atlas-{len(parts):03d}.part.gzip'
        (ASSETS / name).write_bytes(gzip.compress(model[start:start + part_size], compresslevel=9, mtime=0))
        parts.append(name)
    (ASSETS / "catalog.json").write_text(json.dumps({"version": 8, "modelParts": parts,
        "modelBytes": len(model), "structures": catalog}, ensure_ascii=False, indent=2), encoding="utf-8")
    legacy = ASSETS / 'atlas.glb'
    if legacy.is_file():
        legacy.unlink()
    for old_part in ASSETS.glob('atlas-???.part.*'):
        if old_part.name not in parts and re.fullmatch(r'atlas-\d{3}\.part\.(gz|gzip)', old_part.name):
            old_part.unlink()
    for lang, directory in (("en", "values"), ("fr", "values-fr")):
        for kind, pair in all_summaries.items():
            strings[lang]["bio_summary_" + kind] = pair[0 if lang == "en" else 1]
        for provider in [*PROVIDERS.values(), trunk_muscle_content]:
            for category, pair in provider.groups.items():
                key = 'bio_group_' + category
                assert key not in strings[lang], key
                strings[lang][key] = pair[0 if lang == 'en' else 1]
        content = '\n'.join(f'    <string name="{key}">{xml_string(value)}</string>' for key, value in strings[lang].items())
        (RES / directory / "strings_biology_catalog.xml").write_text('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/anatomy/build_skeleton.py. Edit the importer/content, then regenerate. -->\n<resources>\n' + content + '\n</resources>\n', encoding="utf-8")
    (SOURCE / "preview_geometry.json").write_text(json.dumps(preview), encoding="utf-8")
    manifest = {"source": "https://dbarchive.biosciencedbc.jp/data/bodyparts3d/LATEST/isa_BP3D_4.0_obj_99.zip",
                "sourceSha256": hashlib.sha256((SOURCE / "bodyparts.zip").read_bytes()).hexdigest(),
                "metadataSha256": hashlib.sha256((SOURCE / "elements.tsv").read_bytes()).hexdigest(),
                "license": "https://creativecommons.org/licenses/by/4.0/",
                "combinedAtlasLicense": facial_content.LICENSE_URL,
                "facialMuscles": facial_content.provenance(SOURCE),
                "cartilage": cartilage_content.provenance(),
                "menisci": meniscus_content.provenance(),
                "organs": organ_content.provenance(SOURCE, organ_entries),
                "visceralAdditions": visceral_content.provenance(),
                "trunkMuscles": trunk_muscle_content.provenance(SOURCE),
                "licenseDeclaration": "https://dbarchive.biosciencedbc.jp/en/bodyparts3d/lic.html",
                "modifications": "Skeletal/teeth/muscular/cartilage/organ subset; OBJ to GLB; millimetres to metres; axis rotation; smooth normals; merged source parts per anatomical concept; duplicate identical geometry removed within each concept; per-structure materials. Large muscular meshes: exact coincident vertices welded, then quadric error reduction (fast-simplification 0.1.13) per connected component, retaining every component and bounding planes within 0.75 mm; this is not medical validation. Muscle heads and zones retain their source identities. Two source left/right association pairs corrected from geometry coordinates; see muscularLateralityCorrections.",
                "triangles": total_triangles, "renderedStructures": len(gltf["nodes"]),
                "packaging": {"format": "GLB split into independent gzip members; concatenate decompressed data in modelParts order",
                              "modelBytes": len(model), "modelSha256": hashlib.sha256(model).hexdigest(),
                              "parts": [{"file": name, "bytes": (ASSETS / name).stat().st_size,
                                         "sha256": hashlib.sha256((ASSETS / name).read_bytes()).hexdigest()} for name in parts]},
                "originalTriangles": original_triangles, "muscleReductions": dict(reductions),
                "muscularStructures": sum(r['layer'] == 'muscles' for r in catalog),
                "muscularSourceElements": 407,
                "muscularConceptClasses": ['FMA5022', 'FMA85453', 'FMA10474'],
                "muscularAdditionalConcepts": list(muscle_content.EXTRA_CONCEPTS),
                "muscularLateralityCorrections": muscle_content.LATERALITY_CORRECTIONS,
                "educationalReferences": [
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/9-3-cartilaginous-joints",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/9-6-anatomy-of-selected-synovial-joints",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/11-3-axial-muscles-of-the-head-neck-and-back",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/11-4-axial-muscles-of-the-abdominal-wall-and-thorax",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/11-5-muscles-of-the-pectoral-girdle-and-upper-limbs",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/11-6-appendicular-muscles-of-the-pelvic-girdle-and-lower-limbs",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/19-1-heart-anatomy",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/22-1-organs-and-structures-of-the-respiratory-system",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/23-3-the-mouth-pharynx-and-esophagus",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/23-4-the-stomach",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/23-5-the-small-and-large-intestines",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/23-6-accessory-organs-in-digestion-the-liver-pancreas-and-gallbladder",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/25-2-gross-anatomy-of-urine-transport",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/25-3-gross-anatomy-of-the-kidney",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/17-3-the-pituitary-gland-and-hypothalamus",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/17-4-the-thyroid-gland",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/17-6-the-adrenal-glands",
                    "https://openstax.org/books/anatomy-and-physiology-2e/pages/21-1-anatomy-of-the-lymphatic-and-immune-systems",
                    "https://openstax.org/books/anatomy-and-physiology/pages/27-1-anatomy-and-physiology-of-the-male-reproductive-system"],
                "referenceBones": 206, "renderedReferenceBones": sum(r["boneCount"] for r in catalog if r["standard"] and r["mesh"]),
                "teeth": sum(r["region"] == "teeth" for r in catalog),
                "missingReferenceBones": [r["id"] for r in catalog if r["standard"] and not r["mesh"]]}
    for layer, provider in PROVIDERS.items():
        manifest[layer] = provider.provenance(SOURCE, added_entries[layer])
        manifest['educationalReferences'] += provider.references
    manifest['educationalReferences'] = list(dict.fromkeys(manifest['educationalReferences']))
    manifest['educationalReferences'] += trunk_muscle_content.references
    (ASSETS / "provenance.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(manifest, indent=2))
    from external_visibility import apply as add_external_visibility
    add_external_visibility('male')


if __name__ == "__main__":
    main()
