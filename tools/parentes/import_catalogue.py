"""Import accepted terminal species with complete, version-pinned OpenTree lineages.
Candidate names never define ancestry. Ambiguous, suppressed and nonterminal additions
are recorded and excluded. Existing editorial seed species remain mandatory.
"""
import json,hashlib,time,urllib.request,urllib.error
from pathlib import Path
from data_paths import SOURCE
from concurrent.futures import ThreadPoolExecutor
ROOT=Path(__file__).resolve().parents[2]
CACHE=ROOT/'app/build/parentes-import'
OUT=SOURCE
CACHE.mkdir(parents=True,exist_ok=True)
OUT.mkdir(parents=True,exist_ok=True)

def request(endpoint,body):
    key=hashlib.sha256((endpoint+json.dumps(body,sort_keys=True)).encode()).hexdigest()
    path=CACHE/(key+'.json')
    if path.exists():return json.loads(path.read_text(encoding='utf8'))
    req=urllib.request.Request('https://api.opentreeoflife.org/v3/'+endpoint,data=json.dumps(body).encode(),
        headers={'Content-Type':'application/json','User-Agent':'Atom2Universe-offline-catalogue/1.0'})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req,timeout=90) as response: result=json.load(response)
            path.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
            return result
        except urllib.error.HTTPError as exc:
            if exc.code not in (429,500,502,503,504) or attempt==3:raise
            delay=max(5,int(exc.headers.get('Retry-After','5')))
            while delay>0:
                time.sleep(min(delay,30));delay-=30
        except (TimeoutError,urllib.error.URLError):
            if attempt==3:raise
            time.sleep(3*(attempt+1))

def main():
    seedfile=ROOT/'tools/parentes/seed_catalogue.tsv'
    if not seedfile.exists():seedfile=ROOT/'tools/parentes/catalogue.tsv'
    rows={r[0]:{'name':r[0],'en':r[1],'fr':r[2],'seed':True} for line in seedfile.read_text(encoding='utf8').splitlines() if line for r in [line.split('\t')]}
    candidatefile=ROOT/'tools/parentes/candidates.json'
    if candidatefile.exists():
        for entry in json.loads(candidatefile.read_text(encoding='utf8'))['candidates']:
            rows.setdefault(entry['name'],dict(entry,seed=False))
    about=request('tree_of_life/about',{})
    if (OUT/'tree.json').exists():
        previous=json.loads((OUT/'tree.json').read_text(encoding='utf8'))
        assert about['synth_id']==previous['synth_id'],'An explicit source-version migration is required'
    resolved={};excluded=[]
    names=list(rows)
    for offset in range(0,len(names),100):
        reply=request('tnrs/match_names',{'names':names[offset:offset+100],'do_approximate_matching':False})
        for result in reply['results']:
            row=rows[result['name']]
            exact=[m['taxon'] for m in result['matches'] if not m['is_approximate_match'] and not m['is_synonym']
                and not m['taxon']['is_suppressed_from_synth'] and m['taxon']['name']==row['name']
                and (m['taxon']['rank']=='species' or (row['seed'] and row['name']=='Latimeria chalumnae'))]
            reason=None
            if len(exact)!=1:reason='No unique accepted species in synthesis'
            elif not row['seed'] and 'extinct' in exact[0].get('flags',[]):reason='Extinction flag; not added to the living-species selection'
            if reason:
                if row['seed']:raise ValueError((row['name'],reason))
                excluded.append({'name':row['name'],'reason':reason})
            else:resolved[row['name']]=exact[0]
        print('Resolved names',min(offset+100,len(names)),'/',len(names),flush=True)
    reviewfile=ROOT/'tools/parentes/review_exclusions.json'
    if reviewfile.exists():
        for record in json.loads(reviewfile.read_text(encoding='utf8')):
            name=record['name']
            if name in resolved:
                assert not rows[name]['seed'],'Editorial seed requires individual review'
                del resolved[name]
                excluded.append(record)
    def lineage(name):
        missing=CACHE/('absent-'+about['synth_id']+'-'+str(resolved[name]['ott_id'])+'.json')
        if missing.exists():return name,None,json.loads(missing.read_text(encoding='utf8'))['reason']
        try:return name,request('tree_of_life/node_info',{'node_id':'ott'+str(resolved[name]['ott_id']),'include_lineage':True}),None
        except urllib.error.HTTPError as exc:
            if exc.code!=400:raise
            reason='Taxon absent or not uniquely placed in synthesis'
            missing.write_text(json.dumps({'reason':reason,'synth_id':about['synth_id']}),encoding='utf8')
            return name,None,reason
    nodes={};sources={};labels={};accepted=[];selected=set();ancestors_of_selected=set()
    for count,(name,reply,reason) in enumerate(ThreadPoolExecutor(max_workers=3).map(lineage,resolved),1):
        row=rows[name]
        if reply:
            assert reply['synth_id']==about['synth_id'],'Source changed during import'
            chain=[reply]+reply['lineage'];chainids={n['node_id'] for n in reply['lineage']}
            if reply['node_id'] in selected:reason='Another selected species resolves to the same source node'
            elif reply['node_id'] in ancestors_of_selected or chainids & selected:reason='Not a distinct terminal relative to the existing selection'
        if reason:
            if row['seed']:raise ValueError((name,reason))
            excluded.append({'name':name,'reason':reason});continue
        sources.update(reply['source_id_map'])
        for i,n in enumerate(chain):
            tax=n.get('taxon',{})
            item={'id':n['node_id'],'parent':chain[i+1]['node_id'] if i+1<len(chain) else None,
                'scientific':tax.get('name',''),'ott':tax.get('ott_id'),'tax_sources':tax.get('tax_sources',[]),
                'supported_by':n.get('supported_by',{}),'conflicts_with':n.get('conflicts_with',{}),'species':False}
            if item['id'] in nodes:assert nodes[item['id']]['parent']==item['parent']
            else:nodes[item['id']]=item
        taxon=resolved[name];key='pt_species_'+str(taxon['ott_id'])
        nodes[reply['node_id']].update(species=True,key=key,scientific=taxon['name'],ott=taxon['ott_id'],
            tax_sources=taxon['tax_sources'],requested_node_id='ott'+str(taxon['ott_id']))
        selected.add(reply['node_id']);ancestors_of_selected.update(chainids)
        labels[key]={'en':row['en'],'fr':row['fr']}
        accepted.append(dict(row,id=reply['node_id'],ott=taxon['ott_id']))
        if count%100==0:print('Imported lineages',count,'/',len(resolved),flush=True)
    assert len(accepted)>=sum(r['seed'] for r in rows.values())
    catalogue={'schema':1,'retrieved':'2026-10-05','synth_id':about['synth_id'],'taxonomy_version':about['taxonomy_version'],
        'source_created':about['date_created'],'root':about['root']['node_id'],'source_id_map':sources,'nodes':list(nodes.values())}
    (OUT/'tree.json').write_text(json.dumps(catalogue,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (CACHE/'labels.json').write_text(json.dumps(labels,ensure_ascii=False,indent=2),encoding='utf8')
    (ROOT/'tools/parentes/catalogue.tsv').write_text('\n'.join('\t'.join([r['name'],r['en'],r['fr']]) for r in accepted)+'\n',encoding='utf8')
    report={'retrieved':'2026-10-05','synth_id':about['synth_id'],'candidate_count':len(rows),'accepted':accepted,'excluded':excluded}
    (OUT/'selection.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(f'Imported {len(accepted)} species, {len(nodes)} nodes; {len(excluded)} candidates excluded; {about["synth_id"]}',flush=True)
if __name__=='__main__':main()
