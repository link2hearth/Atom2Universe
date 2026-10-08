import java.util.*;
import kotlin.Pair;
import com.Atom2Universe.app.games.caves.node.CraftDef;
import com.Atom2Universe.app.games.caves.node.CraftGroup;

/** Standalone regression check against compiled production Kotlin; no Android runtime required. */
public class CraftRulesCheck {
    static short id(int n) { return (short)n; }
    static Pair<Short,Integer> ingredient(int id, int count) { return new Pair<>((short)id,count); }
    static void check(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        CraftDef sticks = new CraftDef(List.of(),id(3110),4,null,
            List.of(new CraftGroup("planks",List.of(id(1010),id(1011)),2)),List.of());
        Map<Short,Integer> mixed = Map.of(id(1010),3,id(1011),7);
        check(sticks.maxCraftable(mixed)==5,"Mixed woods combine across stacks");
        check(sticks.consumption(mixed,5).equals(mixed),"Five batches debit exactly ten planks");
        check(sticks.consumption(mixed,6)==null,"Insufficient batch rejected");
        check(sticks.consumption(mixed,0)==null,"Zero batch rejected");
        check(sticks.maxCraftable(Map.of(id(1010),-1))==0,"Negative stocks never craft");
        CraftDef smelt = new CraftDef(List.of(ingredient(3101,4)),id(3114),4,null,
            List.of(new CraftGroup("fuel",List.of(id(3100),id(3113)),1)),List.of(id(8000)));
        Map<Short,Integer> inv=Map.of(id(3101),20,id(3100),2,id(3113),3,id(8000),1);
        check(smelt.maxCraftable(inv)==5,"Fuel alternatives combine");
        Map<Short,Integer> debit=smelt.consumption(inv,5);
        check(debit.get(id(3101))==20 && debit.get(id(3100))==2 && debit.get(id(3113))==3,"Exact smelting debit");
        check(!debit.containsKey(id(8000)),"Furnace retained");
        check(smelt.maxCraftable(Map.of(id(3101),20,id(3100),20))==0,"Furnace required");
        check(sticks.maxCraftable(Map.of(id(1010),Integer.MAX_VALUE,id(1011),Integer.MAX_VALUE))==Integer.MAX_VALUE,"Large inventory does not overflow");
        boolean rejected=false;
        try {new CraftDef(List.of(ingredient(1010,1)),id(3110),1,null,sticks.getGroups(),List.of());}
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Overlapping exact and tag ingredients rejected");
        System.out.println("11 production crafting checks passed.");
    }
}
