import com.Atom2Universe.app.games.caves.world.*;
import java.util.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Production generator checks. Input TSV is exported from natural_generation.json. */
public class NaturalTerrainCheck {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 @SuppressWarnings("unchecked")
 public static void main(String[] args)throws Exception {
  List<NaturalBiomeProfile> profiles=new ArrayList<>();
  Field field=BiomeRegistry.class.getDeclaredField("_surface");field.setAccessible(true);
  List<SurfaceBiomeDef> biomes=(List<SurfaceBiomeDef>)field.get(null);
  Constructor<?> ctor=Arrays.stream(SurfaceBiomeDef.class.getConstructors()).filter(c->c.getParameterCount()==28).findFirst().orElseThrow();
  for(String line:Files.readAllLines(Path.of(args[0]))) {
   String[] p=line.split("\t");
   profiles.add(new NaturalBiomeProfile(p[0],Double.parseDouble(p[1]),Double.parseDouble(p[2]),Double.parseDouble(p[3]),Double.parseDouble(p[4]),Double.parseDouble(p[5])));
   Object[] a=new Object[28];Class<?>[] types=ctor.getParameterTypes();
   for(int i=0;i<a.length;i++){Class<?> t=types[i];a[i]=t==double.class?0d:t==float.class?0f:t==int.class?0:t==short.class?(short)0:t==boolean.class?false:t==String.class?"none":t==double[].class?new double[3]:t==Short.class?null:List.of();}
   a[0]=p[0];a[14]=p[6];a[12]=Float.parseFloat(p[7]);a[15]=4;a[16]=7;
   biomes.add((SurfaceBiomeDef)ctor.newInstance(a));
  }
  Field settings=NaturalTerrainSettings.class.getDeclaredField("profiles");settings.setAccessible(true);settings.set(null,profiles);
  Set<String> seen=new TreeSet<>();Set<Short> materials=new TreeSet<>();
  for(long seed:new long[]{42,71893,20260914}) {
   NaturalTerrain terrain=new NaturalTerrain(seed,profiles);double min=1e9,max=-1e9,slope=0;
   for(int z=-4096;z<=4096;z+=32)for(int x=-4096;x<=4096;x+=32){double h=terrain.height(x,z);min=Math.min(min,h);max=Math.max(max,h);slope=Math.max(slope,Math.abs(h-terrain.height(x+1,z)));String id=terrain.biomeIdAt(x,z);seen.add(id);SurfaceBiomeDef b=biomes.stream().filter(v->v.getId().equals(id)).findFirst().orElseThrow();materials.add(terrain.topBlock(b,x,z,(int)h));check(h==terrain.height(x,z),"Deterministic height");}
   check(min<74 && max>500 && max<4096,"Sea and high mountains");
   CozyLandscape decor=new CozyLandscape(seed,(x,z)->terrain.caveAt(x,(int)terrain.height(x,z),z),terrain);
   int carved=0,solid=0;
   for(int cy:new int[]{-1,-59,-60,-61,-69,-70,-71,-129})for(int cx=-1;cx<=1;cx++){
    Chunk c=new Chunk(cx,cy,-1);terrain.generate(c,decor);
    for(int z=0;z<16;z++)for(int y=0;y<16;y++)for(int x=0;x<16;x++){
     boolean air=c.blockAt(x,y,z)==0;
     check(air==terrain.caveAt(c.getWorldX()+x,c.getWorldY()+y,c.getWorldZ()+z),"Shared cave lattice at negative coordinates");
     if(air)carved++;else solid++;
    }
    Chunk again=new Chunk(cx,cy,-1);terrain.generate(again,decor);check(Arrays.equals(c.getBlocks(),again.getBlocks()),"Load-order independent chunk");
   }
   check(carved>500 && solid>carved,"Caves with solid mass around them");
   for(int cy:new int[]{256,625,1000}){Chunk sky=new Chunk(0,cy,0);terrain.generate(sky,decor);for(short b:sky.getBlocks())check(b==0,"No sky islands");}
   World world=new World(seed,null,3,null);float[] spawn=world.findSpawnPoint();int sx=(int)Math.floor(spawn[0]),sy=Math.round(spawn[1]-1.62f),sz=(int)Math.floor(spawn[2]);
   check(sy>74 && world.blockAt(sx,sy,sz,null)==0 && world.blockAt(sx,sy+1,sz,null)==0 && world.blockAt(sx,sy-1,sz,null)!=0,"Safe natural spawn");
   System.out.printf(Locale.ROOT,"Seed %d: height %.0f..%.0f, sampled slope %.2f, caves %.1f%%, spawn %d/%d/%d%n",seed,min,max,slope,carved*100.0/(carved+solid),sx,sy,sz);
  }
  check(seen.size()>=18,"Biome diversity");
  for(short id:new short[]{2303,2304,2305,2306,2307,2308})check(materials.contains(id),"Natural material "+id);
  NaturalTerrain t=new NaturalTerrain(42,profiles);
  BufferedImage image=new BufferedImage(1200,800,BufferedImage.TYPE_INT_RGB);
  for(int px=0;px<1200;px++)for(int py=0;py<800;py++){
   int x=px-600,y=500-py;int h=(int)t.height(x,0);int color=y>h?(y<=74?0x7daac0:0xc6dfe4):t.caveAt(x,y,0)?0x29333d:0xafa998;
   image.setRGB(px,py,color);
  }
  ImageIO.write(image,"png",Path.of(args[1]).toFile());
  System.out.println("Biomes: "+seen+"; natural deposits, sky, spawn, chunk boundaries and determinism verified.");
 }
}
