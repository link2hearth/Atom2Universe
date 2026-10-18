"""Check the shipped narratives against source edges, translations and native art."""
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from data_paths import SOURCE

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT/'app/src/main/assets/parentes'
nodes = {n['id']: n for n in json.loads((SOURCE/'tree.json').read_text(encoding='utf8'))['nodes']}
data = json.loads((DATA/'stories.json').read_text(encoding='utf8'))
content = json.loads((DATA/'content.json').read_text(encoding='utf8'))
sources = {s['id'] for s in data['sources'] + content['sources']}
art = (ROOT/'app/src/main/java/com/Atom2Universe/app/science/parentes/ParentesIllustrationView.kt').read_text(encoding='utf8')
translations = []
for folder in ['values','values-fr']:
    elements = ET.parse(ROOT/f'app/src/main/res/{folder}/strings_parentes_stories.xml').getroot()
    values = {s.attrib['name']: s.text for s in elements}
    assert len(values) == len(elements)
    assert all(v and v.strip() for v in values.values())
    translations.append(values)
assert translations[0].keys() == translations[1].keys()
for key in translations[0]:
    placeholders = lambda s: sorted(re.findall(r'%\d+\$[ds]', s))
    assert placeholders(translations[0][key]) == placeholders(translations[1][key])


def path(id):
    result = []
    while id is not None:
        result.append(id); id = nodes[id]['parent']
    return result


count = 0
assert len({s['id'] for s in data['stories']}) == len(data['stories'])
for story in data['stories']:
    assert story['title'] in translations[0] and story['subtitle'] in translations[0]
    endpoint = story['endpoint']
    assert nodes[endpoint]['species']
    lineage = path(endpoint)
    assert story['steps'][0]['node'] == lineage[-1]
    assert story['steps'][-1]['node'] == endpoint
    previous = None
    for step in story['steps']:
        assert step['node'] in lineage
        if previous: assert previous in path(step['node'])[1:]
        previous = step['node']
        assert nodes[step['cousin']]['species'] and step['cousin'] != endpoint
        other = set(path(step['cousin']))
        assert step['fork'] == next(n for n in lineage if n in other)
        assert all(step[k] in translations[0] for k in ['title','body','caption'])
        assert step['sources'] and set(step['sources']) <= sources
        assert '"'+step['art']+'"' in art
        count += 1
print(f'Stories OK: {len(data["stories"])} lineages, {count} ordered chapters, exact source forks, sources, EN/FR and illustration coverage')
