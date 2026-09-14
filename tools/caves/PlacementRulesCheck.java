import java.lang.reflect.*;
import java.util.*;
import com.Atom2Universe.app.games.caves.node.*;
import com.Atom2Universe.app.games.caves.world.BlockPlacement;

/** Runs with lightweight Android class stubs; tests the actual Kotlin placement predicate. */
public class PlacementRulesCheck {
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 @SuppressWarnings("unchecked")
 static void block(int id, String rule, Set<String> tags, boolean decoration, boolean water)throws Exception{
  Constructor<?> c=Arrays.stream(BlockDef.class.getConstructors()).filter(k->Arrays.stream(k.getParameterTypes()).noneMatch(t->t.getName().contains("DefaultConstructorMarker"))).findFirst().orElseThrow();
  Class<?>[] types=c.getParameterTypes();Object[] a=new Object[types.length];
  for(int i=0;i<a.length;i++){Class<?> t=types[i];a[i]=t==int.class?0:t==short.class?(short)0:t==byte.class?(byte)0:t==float.class?0f:t==boolean.class?false:t==String.class?"":Set.of();}
  a[0]=(short)id;a[1]="test";a[10]=1f;a[11]=decoration;a[13]=water;a[19]=.1f;a[20]=.9f;a[22]=true;a[23]="recoverable";a[24]=1;a[25]=tags;a[26]=rule;
  Field defs=BlockRegistry.class.getDeclaredField("defs");defs.setAccessible(true);((Map<Short,BlockDef>)defs.get(null)).put((short)id,(BlockDef)c.newInstance(a));
  for(String field:List.of("decorationTable","waterTable")){Field f=BlockRegistry.class.getDeclaredField(field);f.setAccessible(true);((boolean[])f.get(null))[id]=field.equals("decorationTable")?decoration:water;}
 }
 static final Map<String,Short> world=new HashMap<>();
 static void at(int x,int y,int z,int id){world.put(x+","+y+","+z,(short)id);}
 static boolean fits(int id){return BlockPlacement.INSTANCE.supported((short)id,0,1,0,(x,y,z)->world.getOrDefault(x+","+y+","+z,(short)0));}
 public static void main(String[] args)throws Exception{
  block(100,"any",Set.of("soil"),false,false);block(2000,"any",Set.of("stone"),false,false);
  block(4000,"any",Set.of("sand"),false,false);block(6001,"any",Set.of(),false,true);
  block(7040,"soil",Set.of(),true,false);block(8002,"solid",Set.of(),true,false);
  block(4010,"cactus",Set.of(),false,false);block(7052,"reeds",Set.of(),true,false);
  at(0,0,0,100);check(fits(7040),"Flower on soil");at(0,0,0,2000);check(!fits(7040),"Flower rejects stone");
  check(fits(8002),"Torch on solid support");at(0,0,0,0);check(!fits(8002),"Torch rejects air");
  at(0,0,0,4000);check(fits(4010),"Cactus on sand");at(1,1,0,2000);check(!fits(4010),"Cactus rejects solid neighbour");
  at(1,1,0,0);check(!fits(7052),"Reeds require water");at(1,0,0,6001);check(fits(7052),"Reeds near water");
  check(fits(2000),"Full blocks do not require ground support");
  System.out.println("9 production placement checks passed.");
 }
}
