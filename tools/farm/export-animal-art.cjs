// Bake the approved procedural part drawings into native Canvas scanline recipes, not PNGs.
// Run from the repository root after updating the approved animal study.
const fs=require('fs'),vm=require('vm');
const source='app/src/main/assets/My Farm/ma-ferme-animaux.html';
const html=fs.readFileSync(source,'utf8');
let script=html.match(/<script>([\s\S]*?)<\/script>/)[1];
script=script.slice(0,script.indexOf('  const backdrop='))+'capture(henArts,cowArts,{sprite,rect,oval,line,poly,hens,cows});})();';
function surface(){const c={width:0,height:0,pixels:[]};let tx=0,ty=0;const g={fillStyle:'#000000',translate(x,y){tx+=x;ty+=y},fillRect(x,y,w,h){for(let yy=Math.round(y+ty);yy<Math.round(y+ty+h);yy++)for(let xx=Math.round(x+tx);xx<Math.round(x+tx+w);xx++)if(xx>=0&&xx<c.width&&yy>=0&&yy<c.height)c.pixels[yy*c.width+xx]=this.fillStyle}};c.getContext=()=>g;return c}
let birds,bovines,young,others;
vm.runInNewContext(script,{document:{getElementById:()=>({querySelector:()=>surface()}),createElement:surface},capture:(h,c,primitives)=>{birds=h;bovines=c;young=require('./young-animal-art.cjs')(primitives,h,c);others=require('./sheep-pig-art.cjs')(primitives)},Math});
const palette=[];
function pack(c){const runs=[];for(let y=0;y<c.height;y++)for(let x=0;x<c.width;){let color=c.pixels[y*c.width+x];if(!color){x++;continue}let end=x+1;while(end<c.width&&c.pixels[y*c.width+end]===color)end++;let i=palette.indexOf(color);if(i<0){i=palette.length;palette.push(color)}if(i>255)throw Error('Palette too large');runs.push(x,y,end-x,i);x=end}return `Part(${c.width}, ${c.height}, "${Buffer.from(runs).toString('base64')}")`}
const groups=[...birds,...bovines,...young,...others].map(a=>'        arrayOf('+Object.values(a).map(pack).join(',\n            ')+')');
const code=`package com.Atom2Universe.app.games.farm

// Generated from the approved Canvas study by tools/farm/export-animal-art.cjs.
// Each four-byte run is x, y, width, palette index. No external image is loaded.
internal object FarmAnimalPixels {
    data class Part(val width: Int, val height: Int, val runs: String)
    val colors = intArrayOf(${palette.map(c=>'0xFF'+c.slice(1).toUpperCase()+'.toInt()').join(', ')})
    // Eight adult birds, eight adult bovines, four chicks, four calves;
    // then twelve sheep and twelve pigs (female, male, young; four coats each).
    val models = arrayOf(\n${groups.join(',\n')}\n    )
}
`;
fs.writeFileSync('app/src/main/java/com/Atom2Universe/app/games/farm/FarmAnimalPixels.kt',code);
console.log('Exported 48 models, '+palette.length+' colors, '+code.length+' source bytes.');
