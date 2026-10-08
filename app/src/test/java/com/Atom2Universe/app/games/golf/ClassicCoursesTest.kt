package com.Atom2Universe.app.games.golf

import android.content.Context
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.golf.classic.ClassicCourses
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClassicCoursesTest {
    @Test fun coursesHaveCompleteEnglishAndFrenchNamesAndPlayingNotes() {
        // Unit tests do not package Android resources; check the source translations by the
        // generated IDs used by the catalogue. Android's resource compiler checks the references.
        fun resourceName(type: Class<*>, id: Int) = type.fields.first { it.getInt(null) == id }.name
        for (directory in listOf("values", "values-fr")) {
            val documents = listOf("strings_classic_golf.xml", "strings_wild_golf.xml").map {
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$directory/$it"))
            }
            fun element(tag: String, name: String): Element {
                return documents.flatMap { document ->
                    val nodes = document.getElementsByTagName(tag)
                    (0 until nodes.length).map { nodes.item(it) as Element }
                }
                    .single { it.getAttribute("name") == name }
            }
            fun string(id: Int) = element("string", resourceName(R.string::class.java, id)).textContent
            fun array(id: Int): List<String> {
                val items = element("string-array", resourceName(R.array::class.java, id))
                    .getElementsByTagName("item")
                return (0 until items.length).map { items.item(it).textContent }
            }
            for (course in ClassicCourses.all) {
                assertTrue(string(course.titleRes).isNotBlank())
                assertTrue(string(course.descriptionRes).isNotBlank())
                val names = array(course.holeNamesRes)
                val notes = array(course.tipsRes)
                assertEquals(course.holes.size, names.size)
                assertEquals(course.holes.size, notes.size)
                assertEquals(course.holes.size, names.distinct().size)
                assertTrue(notes.all { it.isNotBlank() })
            }
        }
    }

    @Test fun legacyRoundAndRecordSurviveSwitchingToAnotherCourseAndBack() {
        val app = RuntimeEnvironment.getApplication()
        val legacy = app.getSharedPreferences("classic_golf_v2", Context.MODE_PRIVATE)
        legacy.edit().clear().putBoolean("active",true).putInt("hole",7)
            .putString("scores","4,5,3,6,4,4,3").putFloat("ball_x",12.5f)
            .putFloat("ball_z",85f).putInt("strokes",2).putInt("best",75)
            .putBoolean("muted",true).apply()
        val gardens = ClassicCourses.find(null)
        assertEquals("classic_golf_v2",gardens.progressName)
        assertEquals(gardens,ClassicCourses.find("missing-course"))
        assertEquals(ClassicCourses.all.size,ClassicCourses.all.map { it.id }.distinct().size)
        assertEquals(ClassicCourses.all.size,ClassicCourses.all.map { it.progressName }.distinct().size)
        val heather = ClassicCourses.find("heather")
        val second = app.getSharedPreferences(heather.progressName,Context.MODE_PRIVATE)
        second.edit().clear().putBoolean("active",true).putInt("hole",2)
            .putString("scores","5,4").putFloat("ball_x",-8f).putFloat("ball_z",110f)
            .putInt("strokes",3).putInt("best",81).apply()
        legacy.edit().putString("selected_course",heather.id).apply()
        val selected = ClassicCourses.find(legacy.getString("selected_course",null))
        assertEquals(heather,selected)
        assertEquals(81,app.getSharedPreferences(selected.progressName,Context.MODE_PRIVATE).getInt("best",0))
        // Completing/restarting the second round must not erase the first active ball or record.
        second.edit().putBoolean("active",false).remove("ball_x").remove("ball_z").apply()
        val firstAgain = app.getSharedPreferences(ClassicCourses.find(gardens.id).progressName,Context.MODE_PRIVATE)
        assertTrue(firstAgain.getBoolean("active",false))
        assertEquals(7,firstAgain.getInt("hole",-1))
        assertEquals("4,5,3,6,4,4,3",firstAgain.getString("scores",null))
        assertEquals(12.5f,firstAgain.getFloat("ball_x",0f),0f)
        assertEquals(85f,firstAgain.getFloat("ball_z",0f),0f)
        assertEquals(2,firstAgain.getInt("strokes",0))
        assertEquals(75,firstAgain.getInt("best",0))
        assertTrue(legacy.getBoolean("muted",false))
    }
}
