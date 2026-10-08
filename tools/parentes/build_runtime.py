"""Lossless runtime projection: no JSON object graph or study metadata at startup.

The original JSON in tools/parentes/data remains the auditable source. Integers are big endian;
UTF-8 strings are interned once. Run after content and Wikidata generation.
"""
import io
import json
import struct
from pathlib import Path
from data_paths import SOURCE

DATA = Path(__file__).resolve().parents[2] / 'app/src/main/assets/parentes'


def projection():
    tree = json.loads((SOURCE / 'tree.json').read_text(encoding='utf8'))
    wiki = json.loads((SOURCE / 'wikidata.json').read_text(encoding='utf8'))
    nodes = [[n['id'], n['parent'] or '', n['scientific'], n.get('key', ''),
              str(int(n['species'])), list(n['supported_by']), list(n['conflicts_with']),
              n['tax_sources']] for n in tree['nodes']]
    def records(entries, notes):
        return [[n['id'], n.get('key', '') if notes else '', n['qid'], str(n['revision']),
                 n['aliases']['en'] + n['aliases']['fr']] for n in entries]
    return ([tree[k] for k in ['root', 'synth_id', 'taxonomy_version', 'retrieved']],
            nodes, records(wiki['notes'], True), records(wiki['labels'], False))


def encode(data):
    strings = {'': 0}
    def intern(value):
        if isinstance(value, list) or isinstance(value, tuple):
            for v in value: intern(v)
        else: strings.setdefault(value, len(strings))
    intern(data)
    out = io.BytesIO()
    def integer(n): out.write(struct.pack('>i', n))
    def string(s): integer(strings[s])
    def array(values):
        integer(len(values))
        for s in values: string(s)
    integer(0x50544154)  # PTAT
    integer(1)
    integer(len(strings))
    for s in strings:
        b = s.encode('utf8'); integer(len(b)); out.write(b)
    meta, nodes, notes, labels = data
    for s in meta: string(s)
    integer(len(nodes))
    for n in nodes:
        for s in n[:5]: string(s)
        for a in n[5:]: array(a)
    for entries in [notes, labels]:
        integer(len(entries))
        for n in entries:
            for s in n[:4]: string(s)
            array(n[4])
    return out.getvalue()


def decode(blob):
    src = io.BytesIO(blob)
    def integer(): return struct.unpack('>i', src.read(4))[0]
    assert integer() == 0x50544154 and integer() == 1
    strings = [src.read(integer()).decode('utf8') for _ in range(integer())]
    def string(): return strings[integer()]
    def array(): return [string() for _ in range(integer())]
    meta = [string() for _ in range(4)]
    nodes = [[string() for _ in range(5)] + [array() for _ in range(3)] for _ in range(integer())]
    notes, labels = [[[string() for _ in range(4)] + [array()] for _ in range(integer())] for _ in range(2)]
    assert not src.read(1), 'Trailing data'
    return meta, nodes, notes, labels


if __name__ == '__main__':
    expected = projection()
    blob = encode(expected)
    assert decode(blob) == expected, 'Runtime export loses data'
    (DATA / 'runtime.bin').write_bytes(blob)
    print(f'Runtime: {len(blob):,} bytes; {len(expected[1])} nodes; lossless projection verified')
