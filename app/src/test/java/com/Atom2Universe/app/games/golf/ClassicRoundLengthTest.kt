package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.ClassicCourses
import com.Atom2Universe.app.games.golf.classic.ClassicRoundLength
import com.Atom2Universe.app.games.golf.classic.core.RandomGolfSpec
import org.junit.Assert.*
import org.junit.Test

class ClassicRoundLengthTest {
    @Test fun frontNineEndsAtNineAndUsesOnlyItsOwnPar() {
        for (course in ClassicCourses.all) {
            val holes = ClassicRoundLength.NINE.holes(course)
            assertEquals((1..9).toList(), holes.map { it.number })
            assertEquals(course.holes.take(9).sumOf { it.par }, holes.sumOf { it.par })
            assertFalse(ClassicRoundLength.NINE.finished(List(8) { 4 }, course))
            assertTrue(ClassicRoundLength.NINE.finished(List(9) { 4 }, course))
            assertFalse(ClassicRoundLength.FULL.finished(List(9) { 4 }, course))
            assertTrue(ClassicRoundLength.FULL.finished(List(18) { 4 }, course))
        }
    }

    @Test fun backNineStartsAtTenAndEndsAtEighteen() {
        val section = ClassicRoundLength.BACK_NINE
        for (course in ClassicCourses.all) {
            assertEquals((10..18).toList(), section.holes(course).map { it.number })
            assertEquals(course.holes.drop(9).sumOf { it.par }, section.holes(course).sumOf { it.par })
            assertEquals(9, section.nextIndex(0))
            assertEquals(13, section.nextIndex(4)) // Resume after holes 10 through 13.
            assertEquals(17, section.nextIndex(8))
            assertFalse(section.finished(List(8) { 4 }, course))
            assertTrue(section.finished(List(9) { 4 }, course))
            assertEquals(17, section.lastIndex(course))
        }
    }

    @Test fun existingEighteenHoleSavesAreKeptAndAllFormatsAreIsolated() {
        val names = ClassicCourses.all.flatMap { course ->
            assertEquals(course.progressName, ClassicRoundLength.FULL.progressName(course))
            assertEquals(course.holes, ClassicRoundLength.FULL.holes(course))
            assertEquals(course.holes, ClassicRoundLength.WHOLE.holes(course))
            ClassicRoundLength.entries.map { it.progressName(course) }
        }
        assertEquals(ClassicCourses.all.size * ClassicRoundLength.entries.size, names.toSet().size)
    }

    @Test fun generatedCoursesPlayTheirWholeLengthWhateverItIs() {
        for (count in RandomGolfSpec.HOLE_COUNTS) {
            val course = ClassicCourses.random(RandomGolfSpec(321, count, null))
            assertEquals(count, course.holes.size)
            assertEquals(course.holes, ClassicRoundLength.WHOLE.holes(course))
            assertEquals(count - 1, ClassicRoundLength.WHOLE.lastIndex(course))
            assertFalse(ClassicRoundLength.WHOLE.finished(List(count - 1) { 4 }, course))
            assertTrue(ClassicRoundLength.WHOLE.finished(List(count) { 4 }, course))
            assertSame(course, ClassicCourses.find(course.id))
        }
    }
}
