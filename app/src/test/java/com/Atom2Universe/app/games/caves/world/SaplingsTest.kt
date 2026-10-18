package com.Atom2Universe.app.games.caves.world

import org.junit.Assert.*
import org.junit.Test

class SaplingsTest {
    private class Garden(val seed: Long = 417L) {
        val blocks = linkedMapOf<Saplings.Pos, Short>()
        val metadata = hashMapOf<Saplings.Pos, Byte>()
        val unloaded = hashSetOf<Saplings.Pos>()
        var brightness = 15
        var trees: Saplings = engine()
        fun read(x: Int,y: Int,z: Int): Short? {
            val p = Saplings.Pos(x,y,z)
            return if (p in unloaded) null else blocks[p] ?: if (y == 0) DIRT else AIR
        }
        fun engine(): Saplings = Saplings(seed, ::read,
            { x,y,z -> metadata[Saplings.Pos(x,y,z)] ?: 0 },
            { x,y,z,id,meta ->
                trees.removed(x,y,z)
                val p = Saplings.Pos(x,y,z)
                if (id == AIR) { blocks.remove(p); metadata.remove(p) }
                else { blocks[p] = id; metadata[p] = meta }
            }, { _,_,_ -> brightness }, { it == DIRT || it == GRASS })
        fun plant(species: Int = 0) {
            blocks[Saplings.Pos(0,1,0)] = TreeSpecies.sapling(species)
            trees.planted(0,1,0,TreeSpecies.sapling(species))
        }
        fun seconds(n: Int) { repeat(n) { trees.advance(1000) } }
        fun reload() { val save = trees.snapshot(); trees = engine().also { it.restore(save) } }
        fun leaf(x: Int, species: String = "birch", persistent: Boolean = false) {
            blocks[Saplings.Pos(x,3,0)] = LEAVES
            metadata[Saplings.Pos(x,3,0)] = TreeSpecies.leafMeta(species,persistent)
        }
    }

    @Test fun everySpeciesGrowsWithItsWorldRecipeAndSurvivesReload() {
        for ((i,type) in TreeSpecies.types.withIndex()) {
            val original = Garden(); original.plant(i)
            val reloaded = Garden(); reloaded.plant(i)
            val duration = (TreeSpecies.duration(i)/1000).toInt()
            original.seconds(duration)
            reloaded.seconds(duration/2); reloaded.reload(); reloaded.seconds(duration/2)
            assertEquals(type,original.blocks,reloaded.blocks)
            assertEquals(type,original.metadata,reloaded.metadata)
            assertFalse(type,reloaded.blocks.values.contains(TreeSpecies.sapling(i)))
            assertTrue(type,reloaded.blocks.values.any(::isWood))
            val leaves = reloaded.blocks.filterValues(::isLeaf)
            assertTrue(type,leaves.isNotEmpty())
            for (p in leaves.keys) assertEquals(type,i,TreeSpecies.fromLeaf(reloaded.metadata.getValue(p)))
        }
    }

    @Test fun lightAndLoadedSoilAreRequiredAndUnloadedTimeIsNotBanked() {
        val g = Garden(); g.plant()
        g.brightness = 7; g.seconds(400)
        assertEquals(TreeSpecies.sapling(0),g.read(0,1,0))
        g.brightness = 8; g.unloaded.add(Saplings.Pos(0,0,0)); g.seconds(400)
        g.unloaded.clear(); g.seconds(299)
        assertEquals(TreeSpecies.sapling(0),g.read(0,1,0))
        g.seconds(1); assertTrue(isWood(g.read(0,1,0)!!))
    }

    @Test fun savingBetweenGrowthTicksDoesNotDelayMaturity() {
        val g = Garden(); g.plant(); g.seconds(299)
        g.trees.advance(550); g.reload(); g.trees.advance(450)
        assertTrue(isWood(g.read(0,1,0)!!))
    }

    @Test fun matureTreeWaitsForWholeSpaceWithoutOverwritingAnything() {
        val g = Garden(); g.plant()
        val obstruction = Saplings.Pos(0,2,0)
        g.blocks[obstruction] = STONE
        g.seconds(300)
        assertEquals(2,g.blocks.size)
        assertEquals(STONE,g.blocks[obstruction])
        g.reload(); g.blocks.remove(obstruction); g.unloaded.add(obstruction); g.seconds(20)
        assertEquals(1,g.blocks.size)
        g.unloaded.clear(); g.seconds(1)
        assertTrue(g.blocks.values.any(::isWood))
    }

    @Test fun wideTrunksNeedSoilAndBaobabCentreRemainsHollow() {
        val g = Garden(); val species = TreeSpecies.types.indexOf("baobab"); g.plant(species)
        g.blocks[Saplings.Pos(2,0,0)] = AIR
        g.seconds(600)
        assertEquals(TreeSpecies.sapling(species),g.read(0,1,0))
        g.blocks.remove(Saplings.Pos(2,0,0)); g.seconds(1)
        assertEquals(AIR,g.read(0,1,0))
        assertTrue(isWood(g.read(2,1,0)!!))
    }

    @Test fun removalCancelsGrowthAndReplantingStartsAgain() {
        val g = Garden(); g.plant(); g.seconds(299)
        g.trees.removed(0,1,0); g.plant(); g.seconds(1)
        assertEquals(TreeSpecies.sapling(0),g.read(0,1,0))
        g.blocks[Saplings.Pos(0,0,0)] = STONE; g.seconds(1)
        assertEquals(AIR,g.read(0,1,0))
        g.blocks.remove(Saplings.Pos(0,0,0)); g.seconds(400)
        assertEquals(AIR,g.read(0,1,0))
    }

    @Test fun onlyTheCutThatRemovesLastSupportOwnsTheLeaves() {
        val g = Garden()
        for (x in 1..5) g.leaf(x)
        g.blocks[Saplings.Pos(6,3,0)] = WOOD
        g.trees.cut(0,3,0,"alice")
        assertNull(g.trees.decayOwner(2,3,0))
        g.blocks.remove(Saplings.Pos(6,3,0)); g.trees.cut(6,3,0,"bob")
        g.reload()
        assertEquals("bob",g.trees.decayOwner(2,3,0))
        assertNull(g.trees.decayOwner(2,3,0)) // Every leaf is awarded at most once.
        g.trees.removed(3,3,0)
        assertNull(g.trees.decayOwner(3,3,0)) // A manually broken/replaced leaf cannot pay twice.
    }

    @Test fun pendingCutCrossesUnloadedBoundaryAfterSaveAndNeverStealsSupportedLeaves() {
        val g = Garden(); for (x in 1..4) g.leaf(x)
        g.unloaded.add(Saplings.Pos(2,3,0))
        g.trees.cut(0,3,0,"alice"); g.reload()
        g.unloaded.clear(); g.trees.resumeCuts()
        assertEquals("alice",g.trees.decayOwner(4,3,0))
        g.blocks[Saplings.Pos(5,3,0)] = WOOD
        assertEquals(true,LeafSupport.state(3,3,0,g::read))
        g.trees.supportedLeaf(3,3,0)
        assertNull(g.trees.decayOwner(3,3,0))
    }

    @Test fun persistentLeavesAndDisconnectedLeavesGetNoPassiveClaim() {
        val g = Garden(); g.leaf(1,persistent=true); g.leaf(3)
        g.trees.cut(0,3,0,"alice")
        assertNull(g.trees.decayOwner(1,3,0))
        assertNull(g.trees.decayOwner(3,3,0))
    }

    @Test fun passiveLootRequiresResponsiblePlayerWithinThirtyTwoBlocks() {
        val g = Garden()
        assertTrue(g.trees.eligible("alice",0,3,0,"alice",32.5,3.5,.5))
        assertFalse(g.trees.eligible("alice",0,3,0,"alice",32.51,3.5,.5))
        assertFalse(g.trees.eligible("alice",0,3,0,"bob",.5,3.5,.5))
        assertFalse(g.trees.eligible(null,0,3,0,"alice",.5,3.5,.5))
    }

    @Test fun fivePercentBoundaryAndSpeciesMetadataIncludingSignedBytes() {
        for ((i,type) in TreeSpecies.types.withIndex()) for (persistent in listOf(false,true)) {
            val meta = TreeSpecies.leafMeta(type,persistent)
            assertEquals(i,TreeSpecies.fromLeaf(meta))
            assertEquals(persistent,TreeSpecies.persistent(meta))
            assertEquals(TreeSpecies.sapling(i),TreeSpecies.drop(meta,.049999f))
            assertNull(TreeSpecies.drop(meta,.05f))
        }
        assertNull(TreeSpecies.drop(0,0f))
        assertNull(TreeSpecies.drop(LeafSupport.PERSISTENT,0f))
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun worldDecayPassesOriginalSpeciesAndHonoursPersistentBit() {
        val world = World()
        val chunks = World::class.java.getDeclaredField("chunks").apply { isAccessible = true }
            .get(world) as java.util.concurrent.ConcurrentHashMap<Long,Chunk>
        val chunk = Chunk(0,0,0).apply { generated = true }
        chunks[world.chunkKey(0,0,0)] = chunk
        val natural = TreeSpecies.leafMeta("autumn")
        val persistent = TreeSpecies.leafMeta("willow",true)
        chunk.setBlock(7,7,7,LEAVES); chunk.setMeta(7,7,7,natural)
        chunk.setBlock(8,7,7,LEAVES); chunk.setMeta(8,7,7,persistent)
        val drops = arrayListOf<Byte>()
        var replacements = 0
        world.onBlockReplaced = { _,_,_ -> replacements++ }
        repeat(20) {
            world.tickLeaves({ _,_,_ -> }) { x,y,z,id,meta ->
                assertEquals(LEAVES,id)
                assertEquals(natural,world.metaAt(x,y,z))
                drops.add(meta)
            }
        }
        assertEquals(listOf(natural),drops)
        assertEquals(AIR,world.blockAt(7,7,7))
        assertEquals(0.toByte(),world.metaAt(7,7,7))
        assertEquals(LEAVES,world.blockAt(8,7,7))
        assertEquals(persistent,world.metaAt(8,7,7))
        assertEquals(1,replacements)
    }
}
