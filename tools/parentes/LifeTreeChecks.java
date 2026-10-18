import com.Atom2Universe.app.science.parentes.*;
import java.nio.file.*;
import java.util.*;

/** Run against compileDebugKotlin output, without Gradle test tasks or an APK. */
class LifeTreeChecks {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static LifeNode node(String id, String parent, boolean species) {
        return new LifeNode(id,parent,"",species,"",List.of(),List.of(),List.of());
    }
    static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid tree accepted");
    }
    public static void main(String[] args) throws Exception {
        Path fixtures=Path.of(args[0]);
        Map<String,LifeNode> nodes=new LinkedHashMap<>();
        Set<String> groups=new HashSet<>();
        String root=null;
        for (String line:Files.readAllLines(fixtures.resolve("nodes.tsv"))) {
            String[] n=line.split("\t",-1);
            String parent=n[1].isEmpty()?null:n[1];
            nodes.put(n[0],new LifeNode(n[0],parent,n[2],Boolean.parseBoolean(n[3]),"",List.of(),List.of(),List.of()));
            if (parent==null) root=n[0];
            if (Boolean.parseBoolean(n[4])) groups.add(n[0]);
        }
        LifeTree tree=new LifeTree(nodes,root,groups);
        // Exercise the actual Android archive reader, not just the Python writer.
        Path archivePath=Path.of("app/src/main/assets/parentes/runtime.bin");
        LifeArchive archive=LifeArchive.Companion.read(Files.newInputStream(archivePath));
        check(archive.getNodes().keySet().equals(nodes.keySet()),"Archive loses node IDs");
        for(var n:archive.getNodes().values()) {
            var expected=nodes.get(n.getId());
            check(Objects.equals(n.getParent(),expected.getParent()),"Archive changes an edge");
            check(n.getScientific().equals(expected.getScientific()) && n.getSpecies()==expected.getSpecies(),"Archive changes a taxon");
        }
        byte[] truncated=Arrays.copyOf(Files.readAllBytes(archivePath),20);
        try {
            LifeArchive.Companion.read(new java.io.ByteArrayInputStream(truncated));
            throw new AssertionError("Truncated archive accepted");
        } catch(Exception expected) { check(expected instanceof java.io.EOFException,"Wrong truncated-archive failure"); }
        // Independent parent-walk oracle for the new interval index, including
        // terminal nodes and every unnamed source junction.
        Map<String,Integer> counts=new HashMap<>();
        for(var n:nodes.values()) if(n.getSpecies()) {
            String at=n.getId();
            while(at!=null) { counts.merge(at,1,Integer::sum); at=nodes.get(at).getParent(); }
        }
        for(String id:nodes.keySet()) {
            List<String> path=new ArrayList<>();
            String at=id;
            while(at!=null) { path.add(at); at=nodes.get(at).getParent(); }
            check(tree.ancestors(id).equals(path),"On-demand ancestry differs");
            check(tree.descendants(id).size()==counts.getOrDefault(id,0),"Incorrect descendant interval");
        }
        int pairs=0;
        try(var pairLines=Files.lines(fixtures.resolve("pairs.tsv"))) {
        var iterator=pairLines.iterator();
        while(iterator.hasNext()) {
            String line=iterator.next();
            String[] p=line.split("\t");
            check(tree.lca(p[0],p[1]).equals(p[2]),"LCA differs from oracle");
            TreeScene scene=tree.compare(p[0],p[1]);
            check(scene.getCommon().equals(p[2]),"Wrong highlighted ancestor");
            check(scene.getParents().containsKey(p[2]),"Exact ancestor hidden");
            check(scene.getParents().get(p[2])==null,"Comparison root mismatch");
            check(scene.getFirst().equals(new HashSet<>(tree.pathTo(p[0],p[2]))),"First full path lost");
            check(scene.getSecond().equals(new HashSet<>(tree.pathTo(p[1],p[2]))),"Second full path lost");
            var layout=TreeLayout.INSTANCE.place(scene,76f);
            check(layout.keySet().equals(scene.getParents().keySet()),"Layout omits nodes");
            for (var edge:scene.getParents().entrySet()) if(edge.getValue()!=null) {
                check(tree.ancestors(edge.getKey()).contains(edge.getValue()),"Rendered edge changes ancestry");
                check(layout.get(edge.getKey()).getX()>layout.get(edge.getValue()).getX(),"Reversed layout");
            }
            pairs++;
        }
        }
        for (String id:nodes.keySet()) {
            TreeScene scene=tree.explore(id,2);
            check(scene.getParents().get(id)==null,"Wrong exploration root");
            String parent=tree.displayParent(id);
            if(parent!=null) {
                check(!parent.equals(id) && tree.ancestors(id).contains(parent),"Browse parent changes ancestry");
                check(tree.isBrowsable(parent),"Browse parent is not accessible");
            }
            for (String child:tree.displayChildren(id)) check(tree.ancestors(child).contains(id),"Display child not a descendant");
        }
        // Regression for the first screen stopping two forks above the species:
        // every species must be a visible terminal of its group's full atlas.
        for (String group:groups) {
            List<String> tips=tree.descendants(group).stream().map(LifeNode::getId).toList();
            TreeScene atlas=tree.atlas(group,tips);
            Set<String> terminals=new HashSet<>(atlas.getParents().keySet());
            terminals.removeAll(atlas.getParents().values());
            check(terminals.equals(new HashSet<>(tips)),"Atlas hides species in "+group);
            var positions=TreeLayout.INSTANCE.place(atlas,76f);
            check(tips.stream().map(t->positions.get(t).getX()).distinct().count()==1,"Species column not aligned");
            var radial=TreeLayout.INSTANCE.radial(atlas);
            check(radial.keySet().equals(atlas.getParents().keySet()),"Circular layout loses nodes");
            check(radial.get(group).getRadius()==0f,"Circular root off center");
            check(tips.stream().map(t->radial.get(t).getAngle()).distinct().count()==tips.size(),"Leaves overlap in circular layout");
            for(var edge:atlas.getParents().entrySet()) {
                var p=radial.get(edge.getKey());
                check(Float.isFinite(p.getX()) && Float.isFinite(p.getY()),"Non-finite circular position");
                if(edge.getValue()!=null) check(p.getRadius()>radial.get(edge.getValue()).getRadius(),"Circular edge points inward");
            }
            for(var edge:atlas.getParents().entrySet()) if(edge.getValue()!=null)
                check(tree.ancestors(edge.getKey()).contains(edge.getValue()),"Atlas invents a relationship");
            for(String a:tips) for(String b:tips) {
                String mrca=tree.lca(a,b);
                check(atlas.getParents().containsKey(mrca),"Atlas collapses an exact common ancestor");
            }
        }
        TreeScene full=tree.atlas(root,tree.getSpecies().stream().map(LifeNode::getId).toList());
        var radial=TreeLayout.INSTANCE.radial(full);
        List<String> geometry=new ArrayList<>();
        for(var entry:full.getParents().entrySet()) {
            var p=radial.get(entry.getKey());
            geometry.add(String.join("\t",entry.getKey(),Objects.toString(entry.getValue(),""),
                Float.toString(p.getX()),Float.toString(p.getY()),Float.toString(p.getAngle()),Float.toString(p.getRadius())));
        }
        Files.write(fixtures.resolve("radial.tsv"),geometry);
        // A hidden named group must never replace the more recent unnamed LCA.
        var fixture=Map.of("root",node("root",null,false),"named",node("named","root",false),
            "hidden",node("hidden","named",false),"a",node("a","hidden",true),"b",node("b","hidden",true),
            "c",node("c","named",true));
        var small=new LifeTree(fixture,"root",Set.of("named"));
        check(small.lca("a","b").equals("hidden"),"Named group substituted for exact LCA");
        check(small.compare("a","b").getParents().containsKey("hidden"),"Unnamed LCA not drawn");
        check(small.lca("a","a").equals("a"),"Same-species handling");
        check(LifeTree.Companion.normalize(" ÉLÉPHANT ").equals("elephant"),"Accent insensitive search");
        rejects(()->new LifeTree(Map.of("root",node("root",null,false),"a",node("a","missing",true)),"root",Set.of()));
        rejects(()->new LifeTree(Map.of("root",node("root",null,false),"a",node("a","b",false),"b",node("b","a",false)),"root",Set.of()));
        rejects(()->new LifeTree(Map.of("root",node("root",null,false),"other",node("other",null,true)),"root",Set.of()));
        rejects(()->new LifeTree(Map.of("root",node("root",null,false)),"root",Set.of("missing")));
        System.out.println("Kotlin core OK: "+pairs+" comparisons, "+nodes.size()+" exploration roots, complete species atlases for "+groups.size()+" groups, exact unnamed LCA, layout, invalid graphs, normalization.");
    }
}
