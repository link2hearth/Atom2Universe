// Four persistent coats per sex/age, sharing articulated pivots with the native rig.
module.exports=({sprite,rect,oval,line,poly})=>{
  const wool=[['#a59a85','#d8ceb4','#f5ecd5','#fff7e5'],['#a2918b','#d6bfb2','#f0dcc9','#fff0dc'],['#928998','#c9c0d3','#e6ddec','#f7eef8'],['#868986','#b9c3b9','#dde4d4','#f1f3df']];
  const skins=[['#ac7c86','#daa5aa','#f2c2bd','#ffdad0'],['#ac806f','#d8ab8c','#f0c9a3','#ffe2bd'],['#977c8e','#c7a6be','#e4c3d8','#f5daea'],['#8e8184','#bba9ad','#dfcacc','#f6dfe0']];
  function legs(p){return {
    upper:sprite(15,23,()=>{poly([[3,0],[12,0],[12,13],[10,21],[4,21],[2,13]],p[0]);rect(4,1,7,17,p[2]);rect(4,2,2,13,p[3])}),
    lower:sprite(16,19,()=>{rect(4,0,7,13,p[0]);rect(5,0,5,12,p[2]);rect(5,1,2,8,p[3]);rect(3,12,11,5,p[0]);rect(4,12,8,1,p[1]);rect(9,14,1,3,'#796b75')})
  }}
  function sheep(v,male,young){const p=wool[v];const skin=['#88797b','#b5a09c','#d7c2b2','#efddca'];
    function puff(x,y,r){oval(x,y+1,r,r*.8,p[0]);oval(x-.5,y-1,r*.92,r*.77,p[1]);oval(x-1,y-2,r*.8,r*.67,p[2]);oval(x-r*.25,y-r*.35,r*.42,r*.28,p[3])}
    return {
      body:sprite(79,57,()=>{
        oval(38,28,33,23,p[0]);oval(38,26,32,22,p[1]);
        for(let row=0;row<3;row++)for(let col=0;col<5;col++){
          let x=13+col*12+(row%2)*2,y=13+row*14+(col%2)*2;
          puff(x,y,9+(v+col)%3)}
        for(let i=0;i<4;i++){rect(20+i*12,23+(i%2)*12,2,1,p[1]);rect(21+i*12,25+(i%2)*12,3,1,p[1])}
      }),
      head:sprite(50,54,()=>{
        oval(24,27,14,19,skin[0]);oval(24,26,13,18,skin[2]);oval(21,22,9,12,skin[3]);
        oval(25,39,12,9,skin[0]);oval(25,38,11,8,skin[2]);oval(23,35,8,4,skin[3]);
        for(let i=0;i<3;i++)puff(15+i*9,12+(i%2)*-2,7);
        if(male){for(const x of [7,42]){
          oval(x,23,7,10,'#9d8067');oval(x,22,6,9,'#d1ad7e');oval(x,21,4,6,'#ecd0a0');
          oval(x,22,3,5,'#a1846d');oval(x,23,2,3,'#d7b88c');
          line(x-4,17,x-2,16,1,'#f6dfb3');line(x+2,28,x+4,26,1,'#b18e6e');}}
        rect(21,37,3,2,'#a47b85');rect(25,37,3,2,'#a47b85');line(24,39,24,42,1,'#a47b85');
        rect(12,31,4,2,'#e6bcb5');rect(32,31,4,2,'#e6bcb5');
      }),
      ear:sprite(22,16,()=>{poly([[21,7],[15,3],[5,3],[1,6],[4,11],[12,14],[18,12]],skin[0]);poly([[18,8],[13,5],[4,5],[6,9],[12,11]],skin[2]);line(6,6,12,9,2,'#e6b8b8')}),
      ...legs(skin),
      tuft:sprite(14,17,()=>{puff(7,8,5);rect(5,12,5,3,p[1])}),
      udder:sprite(1,1,()=>{})
    };
  }
  function pig(v,male,young){const p=skins[v];return {
    body:sprite(79,57,()=>{
      oval(38,29,34,23,p[0]);oval(38,28,33,22,p[1]);oval(37,25,32,20,p[2]);oval(33,19,24,12,p[3]);
      if(v===3){poly([[13,16],[21,9],[31,11],[35,20],[29,29],[19,30],[12,24]],p[0]);oval(58,38,9,7,p[1])}
      if(young&&v===1)for(let i=0;i<3;i++)line(16,15+i*6,57,16+i*6,2,p[1]);
      if(male)for(let i=0;i<6;i++)line(21+i*5,7,22+i*5,4+(i%2),1,p[0]);
      if(!male&&!young)for(let i=0;i<4;i++)rect(25+i*8,49,2,2,p[1]);
    }),
    head:sprite(50,54,()=>{
      oval(24,26,18,19,p[0]);oval(24,25,17,18,p[1]);oval(23,23,16,16,p[2]);oval(19,19,11,10,p[3]);
      oval(25,38,17,12,p[0]);oval(25,37,16,11,p[1]);oval(24,35,15,9,p[2]);oval(22,32,11,5,p[3]);
      oval(18,36,2,3,p[0]);oval(31,36,2,3,p[0]);rect(16,34,1,2,p[1]);rect(29,34,1,2,p[1]);
      line(22,45,28,45,1,p[0]);
      if(male){poly([[9,40],[7,35],[8,31],[11,36],[13,40]],'#b59b80');poly([[10,38],[9,33],[11,37]],'#fff0cb');
        poly([[38,40],[41,34],[41,30],[38,34],[36,40]],'#b59b80');poly([[38,38],[40,33],[39,38]],'#fff0cb');}
      if(v===3)poly([[27,11],[34,14],[38,21],[33,24],[28,20]],p[1]);
    }),
    ear:sprite(22,16,()=>{poly([[20,11],[16,4],[7,0],[2,2],[3,9],[9,15],[17,14]],p[0]);poly([[18,11],[13,5],[5,2],[5,8],[10,12]],p[2]);poly([[14,10],[8,4],[6,4],[8,9]],p[1])}),
    ...legs(p),
    tuft:sprite(14,17,()=>{line(10,3,5,4,2,p[0]);line(5,4,2,7,2,p[0]);line(2,7,3,11,2,p[0]);line(3,11,8,12,2,p[0]);line(8,12,10,9,2,p[0]);line(10,9,8,7,2,p[0]);line(8,7,6,8,2,p[2]);line(5,5,3,7,1,p[3]);line(4,11,7,11,1,p[2])}),
    udder:sprite(1,1,()=>{})
  }}
  const output=[];
  for(const draw of [sheep,pig])for(const stage of ['female','male','young'])for(let v=0;v<4;v++)output.push(draw(v,stage==='male',stage==='young'));
  return output;
};
