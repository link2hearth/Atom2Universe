"""Offline data checks; no Android build or APK. Run from any working directory."""
import json
import itertools
import collections
import hashlib
import xml.etree.ElementTree as ET
from pathlib import Path
from data_paths import SOURCE

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT/'app/src/main/assets/parentes'
# Import reports must never be packaged alongside their runtime projection.
assert {p.name for p in DATA.iterdir()} == {'runtime.bin', 'content.json', 'stories.json'}
data = json.loads((SOURCE/'tree.json').read_text(encoding='utf-8'))
content = json.loads((DATA/'content.json').read_text(encoding='utf-8'))
provenance = json.loads((SOURCE/'provenance.json').read_text(encoding='utf-8'))
assert provenance['selection_sha256']==hashlib.sha256((SOURCE/'selection.json').read_bytes()).hexdigest()
assert provenance['tree_sha256'] == hashlib.sha256((SOURCE/'tree.json').read_bytes()).hexdigest()
assert all(source.get('git_object_sha') for key,source in data['source_id_map'].items() if '@' in key)
nodes = {n['id']:n for n in data['nodes']}
assert len(nodes) == len(data['nodes']), 'Duplicate node IDs'
assert [n['id'] for n in nodes.values() if n['parent'] is None] == [data['root']]
children = collections.defaultdict(list)
for n in nodes.values():
    if n['parent'] is not None:
        assert n['parent'] in nodes
        children[n['parent']].append(n['id'])
paths = {}
for start in nodes:
    p=[]
    at=start
    while at is not None:
        assert at not in p, ('Cycle',at)
        p.append(at)
        at=nodes[at]['parent']
    assert p[-1] == data['root']
    paths[start] = p
species = [n['id'] for n in nodes.values() if n['species']]
selection=json.loads((SOURCE/'selection.json').read_text(encoding='utf8'))
assert len(species)==len(selection['accepted'])==len({n['id'] for n in selection['accepted']})
assert {n['id'] for n in selection['accepted']}==set(species)
assert len(species)>=100
seeds={line.split('\t')[0] for line in (ROOT/'tools/parentes/seed_catalogue.tsv').read_text(encoding='utf8').splitlines() if line}
assert seeds<={nodes[id]['scientific'] for id in species},'An original species was lost'
assert len({nodes[id]['scientific'] for id in species})==len(species),'Repeated scientific name'
assert all(not children[s] for s in species), 'Selected species is not terminal'
assert all(nodes[s]['scientific'] and nodes[s]['key'] for s in species)
groups = {g['id'] for g in content['groups']}
assert len(groups) == len(content['groups']) == 25
assert groups <= nodes.keys()
assert not (groups & set(species))

def lca(a,b):
    common=set(paths[a]) & set(paths[b])
    # Independent oracle: deepest common ancestor measured from the root.
    return max(common,key=lambda n:len(paths[n]))

for a,b in itertools.combinations_with_replacement(species,2):
    common=lca(a,b)
    assert common == lca(b,a)
    if a==b: assert common==a
    else: assert common not in (a,b)
    # Suppressing unary nodes in the display must preserve the exact meeting node.
    keep=groups | {a,b,common}
    pa=[n for n in paths[a][:paths[a].index(common)+1] if n in keep]
    pb=[n for n in paths[b][:paths[b].index(common)+1] if n in keep]
    assert set(pa)&set(pb)=={common}

resources=[]
for folder in ['values','values-fr']:
    entries=ET.parse(ROOT/f'app/src/main/res/{folder}/strings_parentes.xml').getroot()
    strings={e.attrib['name']:e.text for e in entries}
    assert len(strings)==len(list(entries))
    assert all(v and v.strip() for v in strings.values())
    resources.append(strings)
assert resources[0].keys()==resources[1].keys()
keys={nodes[s]['key'] for s in species}
wiki=json.loads((SOURCE/'wikidata.json').read_text(encoding='utf-8'))
assert wiki['license']=='CC0-1.0'
assert len({n['id'] for n in wiki['notes']})==len(wiki['notes'])
assert len({n['id'] for n in wiki['labels']})==len(wiki['labels'])
for label in wiki['labels']:
    assert label['id'] in species and label['revision']>0
for note in wiki['notes']:
    assert note['id'] in species and note['revision']>0
    assert note['en'] and note['fr']
    keys.add(note['key'])
for g in content['groups']:
    keys |= {g['key'],g['key']+'_body'}
sources={s['id'] for s in content['sources']}
for g in content['groups']: assert set(g['sources'])<=sources
for trail in content['heritages']+content['tours']:
    keys.add(trail['key'])
    assert trail['steps']
    for step in trail['steps']:
        keys.add(step['text'])
        assert 1<=len(step['targets'])<=2
        assert all(n in nodes for n in step['targets'])
        assert set(step['sources'])<=sources
assert keys<=resources[0].keys()

# Check every imported edge against cached primary API replies if available.
cache=ROOT/'app/build/parentes-import'
checked=0
for file in cache.glob('*.json'):
    reply=json.loads(file.read_text(encoding='utf-8'))
    if 'lineage' not in reply or 'node_id' not in reply: continue
    assert reply['synth_id']==data['synth_id']
    chain=[reply]+reply['lineage']
    for child,parent in zip(chain,chain[1:]):
        if child['node_id'] in nodes:
            assert nodes[child['node_id']]['parent']==parent['node_id']
            checked+=1

byname={n['scientific']:n['id'] for n in nodes.values() if n['scientific']}
pairs=[('Apis mellifera','Bombus terrestris'),('Tursiops truncatus','Hippopotamus amphibius'),
 ('Tursiops truncatus','Carcharodon carcharias'),('Columba livia','Myotis myotis'),
 ('Escherichia coli','Halobacterium salinarum'),('Euglena gracilis','Thalassiosira pseudonana')]
for a,b in pairs:
    common=lca(byname[a],byname[b])
    print(a,'+',b,'=>',common,nodes[common]['scientific'])
print(f'OK: {len(nodes)} acyclic nodes, {len(species)} terminal species, {len(groups)} groups, {len(species)*(len(species)+1)//2} pairs, {len(keys)} bilingual content keys, {checked} source-edge checks')
print('Maximum lineage length:',max(map(len,paths.values())))

# Interchange fixtures for the actual Kotlin core (Java source-mode runner).
fixtures=ROOT/'app/build/parentes-check'
fixtures.mkdir(parents=True,exist_ok=True)
(fixtures/'nodes.tsv').write_text('\n'.join('\t'.join([n['id'],n['parent'] or '',n['scientific'],str(n['species']).lower(),str(n['id'] in groups).lower()]) for n in nodes.values()),encoding='utf-8')
(fixtures/'pairs.tsv').write_text('\n'.join('\t'.join([a,b,lca(a,b)]) for a,b in itertools.combinations_with_replacement(species,2)),encoding='utf-8')

from build_runtime import decode, projection
assert decode((DATA/'runtime.bin').read_bytes()) == projection(), 'Stale runtime archive'
print('Runtime archive matches all source fields used by Android')
