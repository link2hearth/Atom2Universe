package com.Atom2Universe.app.games.hamlet

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Garde-fou de l'interface : un onglet déjà à l'écran prend bien sa couleur (jamais le gris « désactivé » sous un texte clair). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class HamletUiTest {
    private fun shown(b: Button) = (((b.background as RippleDrawable).getDrawable(0) as StateListDrawable).current as GradientDrawable).color!!.defaultColor

    @Test fun lesOngletsAffichesGardentLeurCouleur() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val row = LinearLayout(activity); activity.setContentView(row)
        val tabs = listOf(HamletUi.button(activity, "a", glyph = "sun") {}, HamletUi.button(activity, "b") {})
        for (tab in tabs) row.addView(tab)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        for (tab in tabs) {
            HamletUi.choice(tab, true); assertEquals(HamletUi.GOLD, shown(tab))
            HamletUi.choice(tab, false); assertEquals(0xFF294942.toInt(), shown(tab))
        }
        tabs[0].isEnabled = false; assertEquals(0xFFD5D9CE.toInt(), shown(tabs[0]))
    }
}
