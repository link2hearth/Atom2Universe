"""Expand the editorial seeds with an auditable sample of bilingual Wikidata taxa.
Wikidata supplies candidate names only. OpenTree alone resolves their placement.
No media, articles or external executable code are downloaded.
"""
import argparse,json,hashlib,time,urllib.request,urllib.parse
import urllib.error
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
CACHE=ROOT/'app/build/parentes-import'
QUERY='SELECT ?item ?name ?en ?fr WHERE { ?item wdt:P105 wd:Q7432; wdt:P225 ?name; rdfs:label ?fr. FILTER(LANG(?fr)="fr") OPTIONAL { ?item rdfs:label ?en. FILTER(LANG(?en)="en") } } LIMIT 500'

def fetch(offset):
    query=QUERY+(' OFFSET '+str(offset) if offset else '')
    path=CACHE/('candidates-'+hashlib.sha256(query.encode()).hexdigest()+'.json')
    if path.exists():return json.loads(path.read_text(encoding='utf8'))['results']['bindings']
    url='https://query.wikidata.org/sparql?'+urllib.parse.urlencode({'query':query,'format':'json'})
    for attempt in range(4):
        try:
            req=urllib.request.Request(url,headers={'User-Agent':'Atom2Universe/1.0 (offline educational catalogue)','Accept':'application/sparql-results+json'})
            with urllib.request.urlopen(req,timeout=90) as response: data=json.load(response)
            path.write_text(json.dumps(data,ensure_ascii=False),encoding='utf8')
            print('Candidate page',offset,flush=True)
            return data['results']['bindings']
        except urllib.error.HTTPError as exc:
            if exc.code!=429 or attempt==3: raise
            delay=max(65,int(exc.headers.get('Retry-After','65')))
            print('Wikidata requested a pause:',delay,'seconds',flush=True)
            while delay>0:
                time.sleep(min(delay,30));delay-=30
        except Exception:
            if attempt==3:raise
            time.sleep(5)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--limit',type=int,default=2000,help='Candidate sample size, rounded up to a batch of 500')
    args=parser.parse_args()
    if args.limit<1:parser.error('--limit must be positive')
    offsets=list(range(0,args.limit,500))
    names={}
    for offset in offsets:
        if offset:
            for _ in range(3):time.sleep(22)
        page=fetch(offset)
        for row in page:
            name=row['name']['value']
            item=row['item']['value'].rsplit('/',1)[1]
            entry={'name':name,'en':row.get('en',row['name'])['value'],'fr':row['fr']['value'],'qid':item}
            names.setdefault(name,{})[item]=entry
    candidates=[next(iter(items.values())) for items in names.values() if len(items)==1]
    output={'retrieved':'2026-10-05','license':'CC0-1.0','query':QUERY,'offsets':offsets,
        'scope':'Candidate sample only; not a full Wikidata export. Names must be accepted by OpenTree before inclusion.',
        'candidates':sorted(candidates,key=lambda r:r['name'])}
    (ROOT/'tools/parentes/candidates.json').write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(len(candidates),'unambiguous candidate names',flush=True)
if __name__=='__main__':main()
