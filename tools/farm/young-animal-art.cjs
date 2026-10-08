// Native-part source for the babies. Same pivots as the adults, with distinct faces and down.
module.exports=({sprite,rect,oval,line,poly,hens,cows},birds,bovines)=>{
  const chicks=hens.slice(0,4).map((adult,v)=>{
    const p=[
      {edge:'#b79863',shade:'#e2c482',base:'#f8dfa0',light:'#fff0bf'},
      {edge:'#aa846c',shade:'#ddb18c',base:'#f2cea4',light:'#ffe6c0'},
      {edge:'#98869c',shade:'#c5b1cd',base:'#e6d4e8',light:'#f7e8f5'},
      {edge:'#a49478',shade:'#ddd0af',base:'#f4e8ca',light:'#fff6dd'}
    ][v];
    return {
      body:sprite(50,40,()=>{
        oval(25,21,20,16,p.edge);oval(25,20,19,15,p.shade);oval(24,17,18,13,p.base);oval(22,13,12,8,p.light);
        for(let i=0;i<6;i++){rect(11+i*5,29+i%2,3,2,p.base);rect(13+i*4,10-i%2,2,1,p.light)}
      }),
      wing:sprite(29,25,()=>{oval(14,12,9,7,p.shade);oval(13,10,8,6,p.base);oval(11,8,5,3,p.light);line(13,14,16,13,1,p.shade)}),
      tail:sprite(28,32,()=>{poly([[24,26],[17,25],[12,19],[12,13],[16,15],[18,11],[21,16],[24,15],[26,21]],p.edge);poly([[23,24],[18,23],[14,17],[17,18],[19,15],[21,19],[24,18]],p.base)}),
      head:sprite(36,42,()=>{
        oval(18,21,13,16,p.edge);oval(18,20,12,15,p.base);oval(17,16,10,11,p.light);
        poly([[10,11],[11,6],[15,8],[17,4],[20,7],[23,6],[24,11]],p.base);
        rect(15,8,2,2,p.light);rect(9,28,5,8,p.base);rect(22,25,4,2,'#efbdb1');
        poly([[28,21],[34,23],[34,25],[28,26]],'#bb9365');poly([[28,22],[32,24],[28,24]],'#f7cc83');
      }),
      leg:birds[v].leg
    };
  });
  const calves=cows.slice(0,4).map((p,v)=>({
    body:bovines[v].body,
    head:sprite(50,54,()=>{
      oval(24,26,18,19,p.edge);oval(24,25,17,18,p.base);oval(20,19,12,11,p.light);
      poly([[27,10],[35,15],[39,22],[36,28],[30,26],[26,21]],p.spot);
      poly([[29,13],[34,17],[35,21],[30,20]],p.spotHi);
      poly([[14,11],[16,6],[19,8],[22,4],[25,7],[28,7],[28,11],[23,10],[20,12],[18,10]],p.shade);
      rect(20,8,3,2,p.light);rect(10,29,6,3,'#efc3bb');rect(33,29,5,3,'#efc3bb');
      oval(25,38,16,11,'#c69a9b');oval(25,37,15,10,'#edc0bc');oval(23,34,12,6,'#ffdbd0');
      oval(15,37,2,1.5,'#bc8d93');oval(32,37,2,1.5,'#bc8d93');line(22,43,28,43,1,'#cb979f');
    }),
    ear:sprite(22,16,()=>{
      poly([[21,9],[17,3],[8,0],[1,2],[0,7],[6,13],[15,15],[20,12]],p.edge);
      poly([[19,9],[15,5],[7,2],[2,3],[3,7],[8,11],[15,13]],p.base);
      poly([[16,10],[11,5],[4,4],[5,8],[11,11]],'#edbdbe');line(6,5,11,7,1,'#f9d6cf');
    }),
    upper:bovines[v].upper,lower:bovines[v].lower,tuft:bovines[v].tuft,
    udder:sprite(1,1,()=>{})
  }));
  return [...chicks,...calves];
};
