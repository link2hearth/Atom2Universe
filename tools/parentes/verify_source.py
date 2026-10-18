"""Optional online regression check against Open Tree's independent MRCA endpoint."""
import json
import sys
sys.dont_write_bytecode = True
from import_catalogue import request, OUT
data=json.loads((OUT/'tree.json').read_text(encoding='utf-8'))
nodes={n['id']:n for n in data['nodes']}
names={n['scientific']:n['id'] for n in nodes.values() if n['scientific']}
def ancestors(n):
    result=[]
    while n:
        result.append(n)
        n=nodes[n]['parent']
    return result
pairs=[('Apis mellifera','Bombus terrestris'),('Tursiops truncatus','Hippopotamus amphibius'),
 ('Tursiops truncatus','Carcharodon carcharias'),('Columba livia','Myotis myotis'),
 ('Escherichia coli','Halobacterium salinarum'),('Euglena gracilis','Thalassiosira pseudonana')]
for a,b in pairs:
    first,second=names[a],names[b]
    expected=next(n for n in ancestors(first) if n in ancestors(second))
    reply=request('tree_of_life/mrca',{'node_ids':[first,second]})
    assert reply['synth_id']==data['synth_id']
    actual=reply['mrca']['node_id']
    assert expected==actual,(a,b,expected,actual)
    print(a,'+',b,'=',actual)
print('All 6 independent source-MRCA checks passed')
