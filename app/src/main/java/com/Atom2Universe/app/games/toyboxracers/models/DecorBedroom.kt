package com.Atom2Universe.app.games.toyboxracers.models

import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.WOOD
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.CREAM
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.LILAC
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.BLUE
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.ROSE
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.GOLD

/** Bedroom furniture in the same model units as the existing shared furniture. */
internal object DecorBedroom {
    private fun model(id: String, build: DecorBuilder.() -> Unit) =
        DecorModel(id, DecorRoom.BEDROOM, DecorBuilder().apply(build).parts.toList())

    val all by lazy { listOf(
        model("bedroom.double_bed") {
            legs(14f,21f,2.4f,1.1f)
            box(0f,2.8f,0f,16.5f,1.5f,24f,WOOD)
            box(0f,4.1f,0f,15.8f,1.3f,23f,CREAM)
            // Padded headboard, two pillows and a folded blanket at the foot.
            box(0f,6.4f,-11.6f,17f,9f,1.1f,WOOD)
            oval(0f,7f,-10.94f,15.6f,6.2f,.7f,LILAC)
            box(0f,5f,2.6f,16f,.55f,16.7f,BLUE)
            for(x in floatArrayOf(-7.9f,7.9f)) box(x,4.2f,2.6f,.3f,1.5f,16.7f,BLUE)
            box(0f,5.34f,-5.4f,16f,.24f,1.1f,CREAM)
            for(x in floatArrayOf(-4f,4f)) {
                oval(x,5.35f,-8.1f,6.7f,1.4f,4.4f,CREAM)
                oval(x,5.9f,-8.1f,5.6f,.35f,3.3f,0xF2E5D0)
            }
            box(0f,5.4f,7.4f,16.2f,.4f,4.2f,ROSE)
            for(x in floatArrayOf(-7.4f,-6.7f,6.7f,7.4f)) box(x,5.62f,7.4f,.16f,.04f,4.1f,CREAM,false)
        },
        model("bedroom.nightstand") {
            legs(4.7f,4.7f,1.2f,.6f)
            box(0f,3.1f,0f,6f,4.4f,6f,WOOD)
            box(0f,5.5f,0f,6.5f,.4f,6.5f,CREAM)
            for(y in floatArrayOf(2f,4f)) {
                box(0f,y,3.05f,5.4f,1.7f,.2f,LILAC)
                handle(0f,y,3.25f,1.5f)
            }
        },
        model("bedroom.table_lamp") {
            cylinder(0f,.18f,0f,1.35f,.36f,WOOD)
            cylinder(0f,1.65f,0f,.18f,2.7f,GOLD)
            cone(0f,2.55f,0f,1.8f,2.4f,CREAM)
            cylinder(0f,2.6f,0f,1.8f,.15f,LILAC)
            oval(0f,5f,0f,.35f,.35f,.35f,GOLD,false)
        }
    ) }
}
