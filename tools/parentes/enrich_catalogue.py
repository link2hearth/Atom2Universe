"""CC0 labels, short descriptions and aliases, matched by exact scientific name.
Entity revisions are frozen. Wikidata never determines the tree topology.
"""
import json,hashlib,time,urllib.request,urllib.parse,urllib.error
from pathlib import Path
from data_paths import SOURCE
ROOT=Path(__file__).resolve().parents[2]
CACHE=ROOT/'app/build/parentes-import'
DATA=SOURCE

def get(url):
    req=urllib.request.Request(url,headers={'User-Agent':'Atom2Universe/1.0 (offline educational catalogue)','Accept':'application/json'})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req,timeout=120) as response:return json.load(response)
        except urllib.error.HTTPError as exc:
            if exc.code!=429 or attempt==3:raise
            delay=max(65,int(exc.headers.get('Retry-After','65')))
            print('Wikidata API pause:',delay,'seconds',flush=True)
            while delay>0:
                time.sleep(min(delay,30));delay-=30
        except (urllib.error.URLError,TimeoutError,json.JSONDecodeError):
            if attempt==3:raise
            time.sleep(5*(attempt+1))

def load_entities(ids):
    entities={}
    for p in CACHE.glob('wikidata*entities*.json'):
        entities.update(json.loads(p.read_text(encoding='utf8'))['entities'])
    missing=[id for id in ids if id not in entities]
    for offset in range(0,len(missing),25):
        part=missing[offset:offset+25]
        path=CACHE/('wikidata-entities-'+hashlib.sha256('|'.join(part).encode()).hexdigest()+'.json')
        if not path.exists():
            response=get('https://www.wikidata.org/w/api.php?'+urllib.parse.urlencode({'action':'wbgetentities','ids':'|'.join(part),
                'languages':'en|fr','props':'labels|descriptions|aliases|claims|info','format':'json'}))
            path.write_text(json.dumps(response,ensure_ascii=False),encoding='utf8')
            time.sleep(4)
        entities.update(json.loads(path.read_text(encoding='utf8'))['entities'])
        print('Wikidata entities',min(offset+25,len(missing)),'/',len(missing),flush=True)
    return entities

def main():
    tree=json.loads((DATA/'tree.json').read_text(encoding='utf8'))
    species=[n for n in tree['nodes'] if n['species']]
    selection=json.loads((DATA/'selection.json').read_text(encoding='utf8'))
    byname={r['name']:{r['qid']} for r in selection['accepted'] if r.get('qid')}
    # Retain the exact-name reconciliation of the original editorial seeds.
    original=CACHE/'wikidata_matches.json'
    if original.exists():
        for m in json.loads(original.read_text(encoding='utf8'))['results']['bindings']:
            byname.setdefault(m['name']['value'],set()).add(m['item']['value'].rsplit('/',1)[1])
    # Existing committed notes also carry their IDs when rebuilding without the old query cache.
    previous=json.loads((DATA/'wikidata.json').read_text(encoding='utf8'))
    byid={n['id']:n for n in species}
    for n in previous['notes']:
        if n['id'] in byid:byname.setdefault(byid[n['id']]['scientific'],set()).add(n['qid'])
    ids=sorted({next(iter(v)) for v in byname.values() if len(v)==1})
    entities=load_entities(ids)
    notes=[];rejected=[];label_records=[];extinct=[]
    labels=json.loads((CACHE/'labels.json').read_text(encoding='utf8'))
    excluded={'Q184774','Q3782756'}
    def values(e,prop):return [c.get('mainsnak',{}).get('datavalue',{}).get('value') for c in e.get('claims',{}).get(prop,[]) if c.get('rank')!='deprecated']
    for n in species:
        choices=byname.get(n['scientific'],set())
        if len(choices)!=1:continue
        qid=next(iter(choices));e=entities.get(qid,{})
        if n['scientific'] not in values(e,'P225'):
            rejected.append({'name':n['scientific'],'qid':qid,'reason':'Scientific name mismatch'});continue
        aliases={lang:[a['value'] for a in e.get('aliases',{}).get(lang,[])] for lang in ('en','fr')}
        label_records.append({'id':n['id'],'qid':qid,'revision':e['lastrevid'],'aliases':aliases})
        editorial=next(r['seed'] for r in selection['accepted'] if r['id']==n['id'])
        if not editorial:
            labels[n['key']]={lang:e.get('labels',{}).get(lang,{}).get('value',n['scientific']) for lang in ('en','fr')}
        descriptions=e.get('descriptions',{})
        wording=' '.join(v['value'] for v in descriptions.values()).lower()
        status=[v.get('id') for v in values(e,'P141') if isinstance(v,dict)]
        if not editorial and ('Q237350' in status or any(w in wording for w in ('extinct','éteint','espèce fossile','fossil species'))):
            extinct.append({'name':n['scientific'],'qid':qid,'revision':e['lastrevid'],'reason':'Extinct or fossil taxon indicated by Wikidata; outside this selection'})
            continue
        if qid in excluded or not all(lang in descriptions for lang in ('en','fr')):continue
        if any(w in wording for w in ('ancestor','ancêtre','extinct','éteint','disparu','fossil','fossile')):
            rejected.append({'name':n['scientific'],'qid':qid,'reason':'Description requires editorial review'});continue
        notes.append({'id':n['id'],'qid':qid,'revision':e['lastrevid'],'modified':e['modified'],
            'key':n['key']+'_body','en':descriptions['en']['value'],'fr':descriptions['fr']['value'],
            'aliases':{lang:[a['value'] for a in e.get('aliases',{}).get(lang,[])] for lang in ('en','fr')}})
    if extinct:
        review=ROOT/'tools/parentes/review_exclusions.json'
        records={r['name']:r for r in json.loads(review.read_text(encoding='utf8'))} if review.exists() else {}
        records.update({r['name']:r for r in extinct})
        review.write_text(json.dumps(list(records.values()),ensure_ascii=False,indent=2)+'\n',encoding='utf8')
        print('Excluding additional extinct taxa:',len(extinct),flush=True)
        import import_catalogue
        import_catalogue.main()
        return main()
    (DATA/'wikidata.json').write_text(json.dumps({'license':'CC0-1.0','retrieved':'2026-10-05','notes':notes,'labels':label_records},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (CACHE/'labels.json').write_text(json.dumps(labels,ensure_ascii=False,indent=2),encoding='utf8')
    (CACHE/'description-review.json').write_text(json.dumps(rejected,ensure_ascii=False,indent=2),encoding='utf8')
    # Label sources are retained even when a bilingual description is unavailable.
    for row in selection['accepted']:
        row.update(labels['pt_species_'+str(row['ott'])])
        choices=byname.get(row['name'],set())
        if len(choices)==1:
            qid=next(iter(choices));e=entities.get(qid,{})
            if row['name'] in values(e,'P225'):
                row['label_qid']=qid;row['label_revision']=e['lastrevid']
    (ROOT/'tools/parentes/catalogue.tsv').write_text('\n'.join('\t'.join([r['name'],r['en'],r['fr']]) for r in selection['accepted'])+'\n',encoding='utf8')
    (DATA/'selection.json').write_text(json.dumps(selection,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(f'{len(notes)} exact-name bilingual descriptions; {len(species)-len(notes)} use their group presentation',flush=True)
if __name__=='__main__':main()
