package com.Atom2Universe.app.games.hamlet

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Garde-fous des effets de combat : chaque rôle a sa compétence, et tout s'éteint ensuite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HamletBattleFxTest {
    private val fx = HamletBattleFx().apply { px = 3f }

    private fun pixels(draw: (Canvas) -> Unit): IntArray {
        val bmp = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp))
        return IntArray(300 * 200).also { bmp.getPixels(it, 0, 300, 0, 0, 300, 200) }
    }

    /** The whole flight and landing of a skill, overlaid. */
    private fun skill(role: HeroRole, ultimate: Boolean) = pixels { c ->
        var age = 0f
        while (age < 1f) { fx.skill(c, role, 60f, 110f, 230f, 110f, 150f, age, ultimate); age += .04f }
    }

    @Test fun chaqueRoleASaCompetence() {
        val looks = HeroRole.entries.map { role -> skill(role, false).toList() }
        for (look in looks) assertTrue(look.count { it ushr 24 != 0 } > 30)
        assertEquals(HeroRole.entries.size, looks.toSet().size)
        for (role in HeroRole.entries)
            assertTrue("$role ultime", skill(role, true).count { it ushr 24 != 0 } >= skill(role, false).count { it ushr 24 != 0 })
    }

    @Test fun lesEffetsSEteignent() {
        val late = pixels { c ->
            for (role in HeroRole.entries) {
                fx.cast(c, role, 150f, 180f, 150f, 2f, true)
                fx.skill(c, role, 60f, 110f, 230f, 110f, 150f, 2f, true)
                fx.attack(c, role, 60f, 110f, 230f, 110f, 150f, 2f)
            }
            fx.heal(c, 150f, 100f, 150f, 2f); fx.shield(c, 150f, 100f, 150f, 2f); fx.summon(c, 150f, 180f, 150f, 2f)
        }
        assertEquals(0, late.count { it ushr 24 != 0 })
        val early = pixels { c -> fx.heal(c, 150f, 100f, 150f, .2f); fx.shield(c, 150f, 100f, 150f, .2f); fx.summon(c, 150f, 180f, 150f, .2f) }
        assertTrue(early.count { it ushr 24 != 0 } > 30)
    }
}
