"""Record the immutable source snapshot and the scope of the reused data."""
import hashlib
import json
import urllib.request
from pathlib import Path
from data_paths import SOURCE
ROOT=Path(__file__).resolve().parents[2]
DATA=SOURCE
CACHE=ROOT/'app/build/parentes-import'
tree=json.loads((DATA/'tree.json').read_text(encoding='utf-8'))
base=f"https://files.opentreeoflife.org/synthesis/{tree['synth_id']}/{tree['synth_id']}/"
url=base+'phylo_snapshot/concrete_rank_collection.json'
cache=CACHE/'concrete_rank_collection.json'
if not cache.exists():
    with urllib.request.urlopen(url,timeout=90) as response: cache.write_bytes(response.read())
collection=json.loads(cache.read_text(encoding='utf-8'))
decisions={d['studyID']+'@'+d['treeID']:d for d in collection['decisions']}
for key,source in tree['source_id_map'].items():
    if '@' not in key: continue
    d=decisions[key]
    source['git_object_sha']=d['object_SHA']
    source['snapshot_url']=base+'phylo_snapshot/tree_'+key+'.json'
    source['reference_label']=d['name']
(DATA/'tree.json').write_text(json.dumps(tree,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
manifest={
 'schema':1,'retrieved':'2026-10-05','synth_id':tree['synth_id'],'taxonomy':tree['taxonomy_version'],
 'source_created':tree['source_created'],'snapshot_collection':url,
 'snapshot_collection_sha256':hashlib.sha256(cache.read_bytes()).hexdigest(),
 'scope':'Synthetic topology, node annotations, OTT identifiers and taxonomic cross-identifiers. No source-study trees, articles, images, or supplementary trait datasets are redistributed.',
 'license':'CC0-1.0 for OpenTree-produced synthesis and OTT; general site policy retains prior-terms caveat.',
 'license_evidence':[
  {'url':'https://tree.opentreeoflife.org/opentree/about/licenses','scope':'General data policy, conditional on prior terms'},
  {'url':'https://phylo.bio.ku.edu/ot/2017-OpenTree-ABI-proposal.pdf','section':'6.2 Intellectual property, printed C-12 / PDF page 12','scope':'Explicit CC0 declaration for the synthetic tree and OTT, separately from input-study licences'}],
 'modifications':[f"Selection of {sum(n['species'] for n in tree['nodes'])} terminal species",'Union of full source lineages; no edges rewired','Original EN/FR seed names and group articles; additional Wikidata labels, short descriptions and aliases matched by scientific name','Unary nodes may be hidden only in the view','No dates or branch lengths imported'],
 'selection_report':'selection.json',
 'reviewed_exceptions':[
  'Macropus rufus replaced by accepted Osphranter rufus before import',
  'Latimeria chalumnae accepted as a species despite its unranked OTT record',
  'Synechococcus elongatus excluded because it is suppressed in synthesis; Nostoc commune selected instead',
  'Escherichia coli OTT query maps to an unnamed synthetic MRCA; source-node identity and requested taxon identity both retained'],
 'selection_sha256':hashlib.sha256((DATA/'selection.json').read_bytes()).hexdigest(),
 'tree_sha256':hashlib.sha256((DATA/'tree.json').read_bytes()).hexdigest()
}
(DATA/'provenance.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f"Frozen {len(decisions)} source snapshot records; {len(tree['source_id_map'])} referenced source IDs")
