package com.Atom2Universe.app.games.billiards

import com.Atom2Universe.app.games.billiards.core.*
import org.junit.Assert.*
import org.junit.Test

class BilliardTableSizeTest {
    @Test fun everySizeFitsEveryRackWithoutOverlaps() {
        for(d in Discipline.entries) for(size in BilliardTableSize.forFamily(d.family)) {
            val table=BilliardTable(d.family,discipline=d,size=size)
            val balls=table.rack(d)
            balls.forEach { assertTrue("$d / $size / ${it.id}",table.inside(it.p)) }
            for(i in balls.indices) for(j in i+1 until balls.size) {
                assertTrue("$d / $size: ${balls[i].id} overlaps ${balls[j].id}",
                    (balls[i].p-balls[j].p).length()>=balls[i].radius+balls[j].radius-1e-8)
            }
            table.pins(d).forEach { assertTrue("$d / $size pin",table.inside(it.p)) }
        }
    }

    @Test fun namedSizesUsePlayingAreasAndPreserveBallAndPocketDimensions() {
        val seven=BilliardTable(TableFamily.POOL,size=BilliardTableSize.POOL_7)
        val eight=BilliardTable(TableFamily.POOL,size=BilliardTableSize.POOL_8)
        val nine=BilliardTable(TableFamily.POOL)
        assertEquals(2.032,seven.length,1e-9)
        assertEquals(2.2352,eight.length,1e-9)
        assertEquals(2.54,nine.length,1e-9)
        assertEquals(nine.radius,seven.radius,0.0)
        assertEquals(nine.mass,seven.mass,0.0)
        assertEquals(nine.cornerOpening,seven.cornerOpening,0.0)
        assertEquals(nine.sideOpening,seven.sideOpening,0.0)
        assertEquals(seven.length/2,seven.pockets[1].center.x,1e-9)
        val uk=BilliardTable(TableFamily.BLACKBALL,size=BilliardTableSize.BLACKBALL_6)
        assertEquals(.826,uk.width,1e-9) // Do not force every manufacturer's bed to 2:1.
    }

    @Test fun caromMarkingsFollowTheFfbBedDimensions() {
        val small=BilliardTable(TableFamily.CAROM,discipline=Discipline.CADRE_47_2,size=BilliardTableSize.CAROM_230)
        val medium=BilliardTable(TableFamily.CAROM,discipline=Discipline.CADRE_47_2,size=BilliardTableSize.CAROM_252)
        val full=BilliardTable(TableFamily.CAROM,discipline=Discipline.CADRE_47_2)
        assertEquals(.383,small.cadreInset,1e-9)
        assertEquals(.148,small.startSpotOffset,1e-9)
        assertEquals(2.52,medium.length,1e-9)
        assertEquals(.420,medium.cadreInset,1e-9)
        assertEquals(.162,medium.startSpotOffset,1e-9)
        assertEquals(.473,full.cadreInset,1e-9)
        assertEquals(.1825,full.startSpotOffset,1e-9)
        assertEquals(.383,small.regions.first().x1,1e-9)
        val s=BilliardSession(Discipline.FREE,PlayMode.LOCAL,tableSize=BilliardTableSize.CAROM_230)
        s.match.ballInHand=true; s.match.handRegion=HandRegion.START_SPOTS
        assertTrue(s.moveBall(s.cueId,V3(s.table.length/4,s.table.width/2+.15)))
        assertEquals(.148,s.cue!!.p.y-s.table.width/2,1e-9)
    }

    @Test fun smallSnookerKeepsTheDAndColourSpotsConsistent() {
        val full=BilliardTable(TableFamily.SNOOKER)
        val small=BilliardTable(TableFamily.SNOOKER,size=BilliardTableSize.SNOOKER_9)
        assertEquals(3.569,full.length,1e-9)
        assertEquals(1.778,full.width,1e-9)
        assertEquals(.737,full.headLine,1e-9)
        assertEquals(.292,full.dRadius,1e-9)
        assertEquals(full.headLine/full.length,small.headLine/small.length,1e-9)
        assertEquals(small.width/2+small.dRadius,small.colorSpot(16).y,1e-9)
        assertEquals(small.length-small.blackSpotInset,small.colorSpot(21).x,1e-9)
        assertEquals(small.colorSpot(21).x,small.rack(Discipline.ENGLISH).first { it.id==2 }.p.x,1e-9)
        assertEquals(full.radius,small.radius,0.0)
    }

    @Test fun legacyAndCrossFamilyPreferencesRestoreTheOriginalDimensions() {
        for(family in TableFamily.entries) {
            assertEquals(BilliardTableSize.defaultFor(family),BilliardTableSize.restore(null,family))
            assertEquals(BilliardTableSize.defaultFor(family),BilliardTableSize.restore("unknown",family))
        }
        assertEquals(BilliardTableSize.SNOOKER_12,BilliardTableSize.restore("POOL_7",TableFamily.SNOOKER))
        assertEquals(BilliardTableSize.POOL_7,BilliardTableSize.restore("POOL_7",TableFamily.POOL))
        assertEquals(BilliardTableSize.CAROM_284,BilliardTableSize.restore("CAROM_254",TableFamily.CAROM))
    }
}
