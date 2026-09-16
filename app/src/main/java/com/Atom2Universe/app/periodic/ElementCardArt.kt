package com.Atom2Universe.app.periodic

import android.graphics.*
import com.Atom2Universe.app.crypto.gacha.GachaRarity
import com.Atom2Universe.app.crypto.gacha.rarityOf
import kotlin.math.*
import kotlin.random.Random

/** Composable art direction: birth-process frame × material scene × stable element seed.
 * Usage motifs are identified in the studio. The H-2 / He-4 atom vignette counts
 * real particles, but its orbit is a schematic, not a quantum trajectory.
 */
internal class ElementCardArt {
    enum class Scene { STAR, VAPOUR, DISCHARGE, FORGE, GEARS, FOUNDRY, TREASURE, GARDEN, MINERAL, LIQUID, CIRCUIT, AURORA, REACTOR, ACCELERATOR, BATTERY, SALT, BONES, AIRCRAFT, CHROME, SHIELD, MAGNET, COIL, SPECIMEN, BALLOON, TELESCOPE, WHEAT, LUNGS, TOOTH, FLASH, FOIL, WATER, BULB, FRUIT, BICYCLE, SPRING, DRYCELL, CERAMIC, FLAME, MELTING, COINS, CAN, BARS, RING,
        INFRARED, ARSENIC, LIGHT_METER, BROMINE, CAMERA_FLASH, ATOMIC_CLOCK, SIGNAL_FLARE, YAG_LASER, CERAMIC_KNIFE, SUPERCONDUCTOR, DRILL,
        CYCLOTRON, CONTACTS, CATALYTIC_CONVERTER, HYDROGEN_MEMBRANE, PIGMENTS, TOUCHSCREEN }
    private val earlyArt = EarlyElementCardArt()
    private val revisedArt = RevisedElementCardArt()
    private val metalArt = MetalElementCardArt()
    private val scientificArt = ScientificElementCardArt()
    private val industrialArt = IndustrialElementCardArt()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var samples = FloatArray(160)
    private var z = 0
    private var a = Color.CYAN
    private var b = Color.MAGENTA
    var scene = Scene.STAR
        private set

    fun configure(e: PeriodicElement) {
        z = e.atomicNumber
        val random = Random(z * 7919)
        samples = FloatArray(160) { random.nextFloat() }
        scene = when {
            z == 2 -> Scene.BALLOON
            z == 4 -> Scene.TELESCOPE
            z == 7 -> Scene.WHEAT
            z == 8 -> Scene.LUNGS
            z == 9 -> Scene.TOOTH
            z == 12 -> Scene.FLASH
            z == 13 -> Scene.FOIL
            z == 17 -> Scene.WATER
            z == 18 -> Scene.BULB
            z == 19 -> Scene.FLAME
            z == 31 -> Scene.MELTING
            z == 32 -> Scene.INFRARED
            z == 33 -> Scene.ARSENIC
            z == 34 -> Scene.LIGHT_METER
            z == 35 -> Scene.BROMINE
            z == 36 -> Scene.CAMERA_FLASH
            z == 37 -> Scene.ATOMIC_CLOCK
            z == 38 -> Scene.SIGNAL_FLARE
            z == 39 -> Scene.YAG_LASER
            z == 40 -> Scene.CERAMIC_KNIFE
            z == 41 -> Scene.SUPERCONDUCTOR
            z == 42 -> Scene.DRILL
            z == 43 -> Scene.CYCLOTRON
            z == 44 -> Scene.CONTACTS
            z == 45 -> Scene.CATALYTIC_CONVERTER
            z == 46 -> Scene.HYDROGEN_MEMBRANE
            z == 48 -> Scene.PIGMENTS
            z == 49 -> Scene.TOUCHSCREEN
            z == 47 -> Scene.COINS
            z == 50 -> Scene.CAN
            z == 78 -> Scene.RING
            z == 79 -> Scene.BARS
            z == 21 -> Scene.BICYCLE
            z == 23 -> Scene.SPRING
            z == 25 -> Scene.DRYCELL
            z == 27 -> Scene.CERAMIC
            z <= 2 -> Scene.STAR
            z == 3 -> Scene.BATTERY
            z == 11 || z == 17 -> Scene.SALT
            z == 20 -> Scene.BONES
            z == 22 -> Scene.AIRCRAFT
            z == 24 -> Scene.CHROME
            z == 30 -> Scene.SHIELD
            z == 60 || z == 62 -> Scene.MAGNET
            z == 29 -> Scene.COIL
            z == 6 -> Scene.GARDEN
            z == 80 -> Scene.LIQUID
            z in listOf(47, 78, 79) -> Scene.TREASURE
            z in listOf(7, 8, 9) -> Scene.VAPOUR
            // Group 18 does not mean a usable discharge tube: Og is a research element.
            z in listOf(10, 18, 54) -> Scene.DISCHARGE
            z == 92 || z == 94 -> Scene.REACTOR
            // Avoid invented visible bulk samples of short-lived elements (Fr, At, Rn…).
            z >= 84 -> Scene.ACCELERATOR
            rarityOf(z) == GachaRarity.SYNTHETIQUE -> Scene.ACCELERATOR
            z == 14 -> Scene.CIRCUIT
            z == 26 -> Scene.FORGE
            z == 28 -> Scene.GEARS // Mechanical alloys, not pure nickel gears.
            z == 13 || z == 50 -> Scene.FOUNDRY
            z in listOf(5, 15, 16, 51, 52, 53, 83) -> Scene.MINERAL
            // No arbitrary equipment/aurora assignment to every metal or lanthanide.
            else -> Scene.SPECIMEN
        }
    }

    private fun ink(color: Int, stroke: Float = 0f) {
        p.reset(); p.isAntiAlias = true; p.color = color
        p.style = if (stroke > 0) Paint.Style.STROKE else Paint.Style.FILL
        p.strokeWidth = stroke; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND
    }
    private fun fade(c: Int, alpha: Int) = (c and 0xffffff) or (alpha.coerceIn(0, 255) shl 24)
    private fun line(c: Canvas, x: Float, y: Float, xx: Float, yy: Float, color: Int, w: Float = 1f) {
        ink(color, w); c.drawLine(x, y, xx, yy, p)
    }
    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int, w: Float = 0f) {
        ink(color, w); c.drawCircle(x, y, r, p)
    }
    private fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int, w: Float = 0f) {
        ink(color, w); c.drawOval(x-rx, y-ry, x+rx, y+ry, p)
    }
    private fun poly(c: Canvas, color: Int, vararg xy: Float, stroke: Float = 0f) {
        path.reset(); path.moveTo(xy[0], xy[1])
        for (i in 2 until xy.size step 2) path.lineTo(xy[i], xy[i+1])
        path.close(); ink(color, stroke); c.drawPath(path, p)
    }
    private fun glow(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        ink(color); p.shader = RadialGradient(x,y,r,color,fade(color,0),Shader.TileMode.CLAMP)
        c.drawCircle(x,y,r,p)
    }
    private fun gem(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        poly(c,color,x,y-r,x+r*.6f,y,x,y+r,x-r*.6f,y)
        poly(c,fade(Color.WHITE,135),x,y-r,x+r*.6f,y,x,y)
    }

    fun badges(c: Canvas) {
        // Separate positions in the header; neither badge replaces the rarity ornament.
        if(ElementCardHazards.isToxic(z)) {
            c.save(); c.translate(99f,48f)
            circle(c,0f,0f,14f,0xFF17141E.toInt())
            circle(c,0f,0f,14f,0xFFE7C68A.toInt(),1f)
            val ivory=0xFFF4DFBD.toInt()
            for(s in floatArrayOf(-1f,1f)) {
                line(c,-7f,s*7,7f,-s*7,ivory,2.3f)
                circle(c,-7f,s*7,1.8f,ivory); circle(c,7f,-s*7,1.8f,ivory)
            }
            oval(c,0f,-3f,7f,6.5f,ivory)
            ink(ivory); c.drawRoundRect(-4f,0f,4f,6f,1.5f,1.5f,p)
            circle(c,-2.8f,-3f,2f,0xFF17141E.toInt()); circle(c,2.8f,-3f,2f,0xFF17141E.toInt())
            poly(c,0xFF17141E.toInt(),0f,-1f,-1.4f,1.5f,1.4f,1.5f)
            for(x in floatArrayOf(-2f,0f,2f)) line(c,x,3f,x,5.5f,0xFF17141E.toInt(),.7f)
            c.restore()
        }
        if(ElementCardHazards.hasNoStableIsotope(z)) {
            c.save(); c.translate(261f,48f)
            circle(c,0f,0f,14f,0xFFFFD454.toInt())
            circle(c,0f,0f,14f,0xFFE7C68A.toInt(),1f)
            val black=0xFF16151B.toInt()
            for(i in 0..2) {
                ink(black); c.drawArc(-10f,-10f,10f,10f,-90f+i*120,60f,true,p)
            }
            circle(c,0f,0f,4.8f,0xFFFFD454.toInt())
            circle(c,0f,0f,2.8f,black)
            c.restore()
        }
    }

    fun frame(c: Canvas, rarity: GachaRarity, t: Float) {
        if (IndustrialElementCardArt.supports(z)) {
            industrialArt.frame(c, z, rarity.color, t)
            return
        }
        if (ScientificElementCardArt.supports(z)) {
            scientificArt.frame(c, z, rarity.color, t)
            return
        }
        val color = rarity.color
        val gold = 0xFFE7C68A.toInt()
        val round = rarity == GachaRarity.PRIMORDIAL || rarity == GachaRarity.NEUTRONIQUE
        for (inset in floatArrayOf(9f, 16f, 25f)) {
            ink(gold, if (inset == 9f) 3f else 1f)
            p.shader = LinearGradient(0f,0f,360f,520f,
                intArrayOf(gold,fade(color,210),0xFF433446.toInt(),gold),null,Shader.TileMode.CLAMP)
            if (round) c.drawRoundRect(inset,inset,360-inset,520-inset,22f,22f,p)
            else {
                val cut = if (rarity == GachaRarity.SYNTHETIQUE) 36f else 18f
                path.reset(); path.moveTo(inset+cut,inset); path.lineTo(360-inset-cut,inset)
                path.lineTo(360-inset,inset+cut); path.lineTo(360-inset,520-inset-cut)
                path.lineTo(360-inset-cut,520-inset); path.lineTo(inset+cut,520-inset)
                path.lineTo(inset,520-inset-cut); path.lineTo(inset,inset+cut); path.close(); c.drawPath(path,p)
            }
        }
        // Each process has its own silhouette, corner ornament and edge rhythm.
        for (sx in listOf(1f,-1f)) for (sy in listOf(1f,-1f)) {
            c.save(); c.translate(if(sx>0) 0f else 360f,if(sy>0) 0f else 520f); c.scale(sx,sy)
            when(rarity) {
                GachaRarity.PRIMORDIAL -> {
                    for (r in floatArrayOf(12f,20f,28f)) {
                        ink(fade(gold,150),1f); c.drawArc(33-r,33-r,33+r,33+r,0f,90f,false,p)
                    }
                    circle(c,34f,34f,5f,gold)
                    line(c,30f,75f,30f,101f,gold)
                }
                GachaRarity.FUSION -> {
                    for(i in 0..2) {
                        ink(gold,1.4f); path.reset(); path.moveTo(28f+i*7,92f)
                        path.cubicTo(78f+i*4,61f,12f,55f,64f+i*9,28f); c.drawPath(path,p)
                    }
                    gem(c,37f,37f,10f,color)
                }
                GachaRarity.SUPERNOVA -> {
                    for(i in 0..6) {
                        val angle = i*PI/12
                        line(c,32f,32f,32+cos(angle).toFloat()*49,32+sin(angle).toFloat()*49,gold,if(i%2==0) 2f else .7f)
                    }
                    gem(c,32f,32f,12f,color)
                }
                GachaRarity.NEUTRONIQUE -> {
                    ink(gold,1.5f); path.reset(); path.moveTo(29f,106f)
                    path.cubicTo(61f,72f,11f,47f,45f,34f)
                    path.cubicTo(79f,22f,82f,62f,60f,57f)
                    path.cubicTo(45f,55f,48f,40f,59f,43f); c.drawPath(path,p)
                    gem(c,32f,32f,9f,color); gem(c,78f,30f,4f,gold)
                }
                GachaRarity.SPALLATION -> {
                    poly(c,gold,25f,76f,31f,45f,69f,25f,44f,55f,stroke=1.3f)
                    poly(c,color,34f,34f,51f,28f,43f,44f)
                    for(i in 0..3) line(c,32f+i*9,73f-i*9,38f+i*9,67f-i*9,gold,2f)
                }
                GachaRarity.SYNTHETIQUE -> {
                    poly(c,fade(color,80),26f,83f,26f,48f,48f,26f,84f,26f,66f,36f,46f,46f)
                    line(c,31f,93f,31f,58f,gold,2f); line(c,31f,58f,58f,31f,gold,2f)
                    circle(c,57f,31f,3f,color); circle(c,31f,93f,3f,color)
                }
            }
            c.restore()
        }
        for (side in floatArrayOf(20f,340f)) for(i in 0..13) {
            val y=117f+i*20
            when(rarity) {
                GachaRarity.PRIMORDIAL -> circle(c,side,y,1.3f,gold)
                GachaRarity.FUSION -> oval(c,side,y,3f,8f,fade(color,170),1f)
                GachaRarity.SUPERNOVA -> gem(c,side,y,if(i%3==0) 5f else 2f,gold)
                GachaRarity.NEUTRONIQUE -> { oval(c,side,y,3f,7f,gold,1f); gem(c,side,y,2f,color) }
                GachaRarity.SPALLATION -> line(c,side-3,y+5,side+3,y-5,color,1.5f)
                GachaRarity.SYNTHETIQUE -> {
                    line(c,side,y,side,y+12,fade(color,180),2f)
                    if(i%3==0) circle(c,side,y,2f,gold)
                }
            }
        }
        glow(c,180f,19f,27f,fade(color,(80+40*sin(t*2)).toInt()))
        gem(c,180f,20f,7f,gold)
    }

    fun draw(c: Canvas, accent: Int, secondary: Int, t: Float) {
        a=accent; b=secondary
        c.save(); c.clipRect(38f,94f,322f,327f)
        // Family texture behind the subject: gas currents, metal hatching, mineral veins.
        val hasCustomScene = ScientificElementCardArt.supports(z) || IndustrialElementCardArt.supports(z)
        for(i in 0 until if (hasCustomScene) 0 else 14) {
            val x=48f+samples[i]*264; val y=105f+samples[i+16]*205
            when(scene) {
                Scene.STAR -> Unit // Keep the atomic diagram free of particle-like ornaments.
                Scene.VAPOUR,Scene.DISCHARGE,Scene.AURORA -> {
                    ink(fade(a,35),.8f); path.reset(); path.moveTo(42f,y)
                    path.cubicTo(100f,y-25,240f,y+25,318f,y+sin(t+i)*10); c.drawPath(path,p)
                }
                Scene.FORGE,Scene.GEARS,Scene.FOUNDRY,Scene.TREASURE,Scene.CIRCUIT -> line(c,x,y,x+15,y-15,fade(a,30))
                else -> { line(c,x,y,x+8,y+11,fade(a,40)); line(c,x+8,y+11,x+4,y+19,fade(a,40)) }
            }
        }
        when(scene) {
            Scene.CYCLOTRON, Scene.CONTACTS, Scene.CATALYTIC_CONVERTER,
            Scene.HYDROGEN_MEMBRANE, Scene.PIGMENTS, Scene.TOUCHSCREEN -> industrialArt.draw(c,z,t)
            Scene.INFRARED, Scene.ARSENIC, Scene.LIGHT_METER, Scene.BROMINE,
            Scene.CAMERA_FLASH, Scene.ATOMIC_CLOCK, Scene.SIGNAL_FLARE, Scene.YAG_LASER,
            Scene.CERAMIC_KNIFE, Scene.SUPERCONDUCTOR, Scene.DRILL -> scientificArt.draw(c,z,t)
            Scene.FLAME, Scene.MELTING, Scene.COINS, Scene.CAN, Scene.BARS, Scene.RING -> metalArt.draw(c,z,t)
            Scene.STAR -> star(c,t)
            Scene.VAPOUR -> vapour(c,t)
            Scene.DISCHARGE -> discharge(c,t)
            Scene.FORGE -> forge(c,t)
            Scene.GEARS -> gears(c,t)
            Scene.FOUNDRY -> foundry(c,t)
            Scene.TREASURE -> treasure(c,t)
            Scene.GARDEN -> garden(c,t)
            Scene.MINERAL -> mineral(c,t)
            Scene.LIQUID -> liquid(c,t)
            Scene.CIRCUIT -> circuit(c,t)
            Scene.AURORA -> aurora(c,t)
            Scene.REACTOR -> reactor(c,t)
            Scene.ACCELERATOR -> accelerator(c,t)
            Scene.BATTERY -> battery(c,t)
            Scene.SALT -> revisedArt.draw(c,z,t)
            Scene.BONES -> bones(c,t)
            Scene.AIRCRAFT -> aircraft(c,t)
            Scene.CHROME -> chrome(c,t)
            Scene.SHIELD -> shield(c,t)
            Scene.BALLOON, Scene.TELESCOPE, Scene.WHEAT, Scene.LUNGS, Scene.TOOTH,
            Scene.FOIL, Scene.WATER -> earlyArt.draw(c,z,t)
            Scene.FLASH, Scene.BULB, Scene.FRUIT, Scene.BICYCLE, Scene.SPRING,
            Scene.DRYCELL, Scene.CERAMIC -> revisedArt.draw(c,z,t)
            Scene.MAGNET -> magnet(c,t)
            Scene.COIL -> coil(c,t)
            Scene.SPECIMEN -> specimen(c,t)
        }
        c.restore()
    }

    private fun star(c: Canvas,t: Float) {
        // Neutral deuterium: 1 proton, 1 neutron, 1 electron; explicitly labelled H-2.
        // Neutral helium-4: 2 protons, 2 neutrons, 2 electrons, one occupied shell.
        val proton = 0xFFFFBA80.toInt()
        val neutron = 0xFF9DA9BF.toInt()
        glow(c,180f,208f,50f,fade(proton,60))
        if(z==1) {
            circle(c,172f,208f,12f,proton)
            circle(c,189f,208f,12f,neutron)
            oval(c,168f,204f,4f,2f,fade(Color.WHITE,180))
            oval(c,185f,204f,4f,2f,fade(Color.WHITE,150))
        } else {
            for(i in 0..3) {
                val x=180f+(if(i%2==0) -8 else 8)
                val y=208f+(if(i<2) -8 else 8)
                circle(c,x,y,10f,if(i==0 || i==3) proton else neutron)
                oval(c,x-3,y-4,3f,1.5f,fade(Color.WHITE,150))
            }
        }
        oval(c,180f,208f,98f,75f,fade(a,135),1.2f)
        for(i in 0 until z) {
            val angle=t+i*PI.toFloat()
            val x=180+cos(angle)*98; val y=208+sin(angle)*75
            glow(c,x,y,13f,fade(a,160))
            circle(c,x,y,4.5f,0xFFE3F7FF.toInt())
        }
    }

    private fun vapour(c: Canvas,t: Float) {
        // Glass flask containing a living cloud and linked molecular motifs.
        path.reset(); path.moveTo(155f,122f); path.lineTo(155f,161f)
        path.cubicTo(62f,228f,102f,291f,180f,291f)
        path.cubicTo(258f,291f,298f,228f,205f,161f); path.lineTo(205f,122f); path.close()
        ink(fade(a,25)); c.drawPath(path,p); ink(fade(a,180),2f); c.drawPath(path,p)
        c.save(); c.clipPath(path)
        for(i in 0..13) glow(c,125+samples[i]*112+sin(t+i)*9,205+samples[i+20]*65,30f,fade(a,45))
        for(i in 0..5) {
            val x=128+samples[i]*100; val y=200+samples[i+40]*60+sin(t+i)*9
            // N2, O2 and F2 are homonuclear molecules. Same size and colour;
            // three, two and one schematic bond strokes respectively.
            val bonds=if(z==7) 3 else if(z==8) 2 else 1
            for(j in 0 until bonds) {
                val d=(j-(bonds-1)/2f)*3
                line(c,x,y+d,x+18,y+d,fade(Color.WHITE,140),1f)
            }
            circle(c,x,y,5f,a); circle(c,x+18,y,5f,a)
        }
        c.restore(); oval(c,180f,122f,26f,6f,a,2f)
        ink(fade(Color.WHITE,140),2f); c.drawArc(106f,173f,252f,281f,125f,55f,false,p)
        oval(c,180f,301f,77f,7f,fade(a,50),1f)
    }

    private fun discharge(c: Canvas,t: Float) {
        val tubes=3+z%3
        for(i in 0 until tubes) {
            val x=180+(i-(tubes-1)/2f)*34; val top=129+samples[i]*27
            ink(fade(a,25)); c.drawRoundRect(x-11,top,x+11,280f,10f,10f,p)
            ink(fade(a,150),1f); c.drawRoundRect(x-11,top,x+11,280f,10f,10f,p)
            line(c,x,top+14,x,267f,fade(a,60),9f)
            path.reset(); path.moveTo(x,top+12)
            for(j in 1..15) path.lineTo(x+sin(t*3+j*.9f+i)*5,top+12+j*(251-top)/15)
            ink(a,2f); c.drawPath(path,p)
            ink(0xFF9B849D.toInt()); c.drawRect(x-13,276f,x+13,287f,p)
            glow(c,x,205f,30f,fade(a,50))
        }
        poly(c,0xFF343145.toInt(),95f,290f,264f,290f,281f,303f,80f,303f)
        line(c,95f,289f,264f,289f,a)
    }

    private fun forge(c: Canvas,t: Float) {
        // Anvil, hammer, ingot and a shower of embers; proportions vary with the element.
        val shift=(samples[0]-.5f)*28
        c.save(); c.translate(shift,0f)
        glow(c,184f,241f,95f,fade(a,90))
        poly(c,0xFF657C8D.toInt(),96f,217f,244f,217f,273f,226f,226f,242f,153f,242f,111f,231f)
        poly(c,0xFF253747.toInt(),153f,242f,221f,242f,211f,272f,232f,286f,130f,286f,162f,270f)
        line(c,99f,217f,243f,217f,0xFFE0E7DC.toInt(),2f)
        poly(c,a,156f,212f,172f,198f,206f,198f,223f,212f)
        line(c,174f,201f,203f,201f,Color.WHITE,2f)
        c.save(); c.rotate(-27f+sin(t*2)*7,225f,186f)
        line(c,224f,200f,224f,131f,0xFF9A6D44.toInt(),10f)
        poly(c,0xFF8195A2.toInt(),194f,125f,248f,125f,252f,148f,191f,148f)
        line(c,196f,126f,247f,126f,Color.WHITE,2f); c.restore()
        for(i in 0..17) {
            val f=(sin(t*2+i)+1)/2
            val x=187+(samples[i]-.5f)*140*f; val y=205-f*(25+samples[i+20]*55)
            line(c,x,y,x+3,y-5,fade(a,(180*(1-f)).toInt()),1.5f)
        }
        c.restore()
    }

    private fun treasure(c: Canvas,t: Float) {
        val metal=if(z==79) 0xFFEAC56A.toInt() else if(z==29) 0xFFEBA17E.toInt() else 0xFFD3E3EE.toInt()
        for(i in 0..6) {
            val x=105+samples[i]*145; val y=263+samples[i+20]*24
            oval(c,x,y,19f,6f,metal); oval(c,x,y-4,19f,6f,metal,2f)
            line(c,x-12,y-5,x+8,y-5,fade(Color.WHITE,170))
        }
        poly(c,metal,117f,241f,102f,172f,144f,201f,180f,146f,216f,201f,258f,172f,241f,241f)
        poly(c,0xFF8A6138.toInt(),124f,231f,236f,231f,241f,247f,118f,247f)
        for(i in 0..4) gem(c,132+i*24f,232f,7f,if(i%2==0) a else b)
        for(x in floatArrayOf(103f,180f,257f)) {
            circle(c,x,if(x==180f) 145f else 171f,5f,metal)
            glow(c,x,if(x==180f) 145f else 171f,14f,fade(metal,(130+60*sin(t*2+x)).toInt()))
        }
        line(c,118f,248f,240f,248f,metal,3f)
    }

    private fun gears(c: Canvas,t: Float) {
        for(i in 0..2) {
            val x=if(i==0) 153f else if(i==1) 226f else 208f
            val y=if(i==0) 211f else if(i==1) 171f else 273f
            val radius=if(i==0) 53f else 31f+samples[i]*6
            val teeth=10+(z+i)%5
            c.save(); c.rotate(t*180/PI.toFloat()*(if(i==0) 1 else -1),x,y)
            path.reset()
            for(j in 0 until teeth*4) {
                val angle=j*6.283185f/(teeth*4)
                val r=radius*(if(j%4==1 || j%4==2) 1.13f else 1f)
                val xx=x+cos(angle)*r; val yy=y+sin(angle)*r
                if(j==0) path.moveTo(xx,yy) else path.lineTo(xx,yy)
            }
            path.close(); ink(0xFF657583.toInt()); c.drawPath(path,p)
            ink(a,1.3f); c.drawPath(path,p)
            circle(c,x,y,radius*.73f,0xFF152637.toInt())
            for(j in 0..4) {
                val angle=j*6.283185f/5
                line(c,x,y,x+cos(angle)*radius*.8f,y+sin(angle)*radius*.8f,0xFFA4AEB0.toInt(),7f)
            }
            circle(c,x,y,10f,a); circle(c,x,y,4f,0xFF192432.toInt()); c.restore()
        }
    }

    private fun foundry(c: Canvas,t: Float) {
        // Suspended crucible pouring into a patterned mould.
        val shift=(samples[0]-.5f)*25
        c.save(); c.translate(shift,0f)
        line(c,122f,114f,138f,155f,0xFFAD9F85.toInt(),3f)
        line(c,206f,112f,200f,143f,0xFFAD9F85.toInt(),3f)
        poly(c,0xFF687481.toInt(),114f,154f,197f,138f,217f,178f,171f,208f,130f,192f)
        oval(c,156f,151f,41f,11f,a)
        line(c,178f,152f,215f,177f,a,5f)
        path.reset(); path.moveTo(214f,177f); path.cubicTo(220f,212f,203f+sin(t*2)*3,241f,205f,266f)
        ink(fade(a,70),12f); c.drawPath(path,p); ink(a,4f); c.drawPath(path,p)
        poly(c,0xFF5E6367.toInt(),106f,263f,247f,263f,267f,290f,94f,290f)
        poly(c,a,121f,270f,234f,270f,245f,281f,112f,281f)
        glow(c,205f,267f,38f,fade(a,130))
        for(i in 0..8) circle(c,173+samples[i]*61,250+sin(t*2+i)*18,1.5f,a)
        c.restore()
    }

    private fun garden(c: Canvas,t: Float) {
        // Carbon's branching life motif, roots and hexagonal molecular structure.
        fun branch(x: Float,y: Float,length: Float,angle: Float,depth: Int) {
            val xx=x+cos(angle)*length; val yy=y+sin(angle)*length
            line(c,x,y,xx,yy,if(depth>1) 0xFFBF9B71.toInt() else a,depth.toFloat()+.5f)
            if(depth>0) {
                branch(xx,yy,length*.71f,angle-.48f+sin(t)*.025f,depth-1)
                branch(xx,yy,length*.73f,angle+.53f+sin(t)*.025f,depth-1)
            } else { oval(c,xx,yy,6f,3f,fade(a,200)); glow(c,xx,yy,9f,fade(a,70)) }
        }
        branch(180f,277f,47f,-PI.toFloat()/2,4)
        for(i in 0..6) line(c,180f,275f,132f+i*16,296f+sin(i.toFloat())*5,0xFFBF9B71.toInt(),1.5f)
        for(i in 0..5) {
            val angle=i*PI.toFloat()/3; val next=(i+1)*PI.toFloat()/3
            line(c,180+cos(angle)*100,211+sin(angle)*88,180+cos(next)*100,211+sin(next)*88,fade(a,65))
        }
    }

    private fun mineral(c: Canvas,t: Float) {
        oval(c,180f,295f,104f,14f,0xFF151D2C.toInt())
        when(z) {
            5 -> boron(c,t)
            15 -> phosphorus(c,t)
            16 -> sulfur(c,t)
            33 -> plates(c,t,0xFFA9B4BE.toInt(),false)
            34 -> selenium(c,t)
            51 -> needles(c,t,true)
            52 -> needles(c,t,false)
            53 -> iodine(c,t)
            83 -> bismuth(c,t)
        }
    }

    private fun sulfur(c: Canvas,t: Float) {
        // Chunky bipyramidal silhouettes, warm shadows and fine growth lines.
        poly(c,0xFF71602B.toInt(),77f,285f,113f,262f,214f,270f,281f,284f,238f,304f,119f,303f)
        for(i in 0..7) {
            val x=100f+i*22; val base=272+samples[i]*18
            val h=42+samples[i+20]*62; val w=17+samples[i+40]*12
            poly(c,0xFFE8B91E.toInt(),x,base-h,x+w,base-h*.38f,x+8,base+9,x-w,base-h*.3f)
            poly(c,0xFFFFE452.toInt(),x,base-h,x+4,base-h*.32f,x-w,base-h*.3f)
            poly(c,0xFFF9D732.toInt(),x,base-h,x+w,base-h*.38f,x+4,base-h*.32f)
            poly(c,0xFFAB7711.toInt(),x+4,base-h*.32f,x+w,base-h*.38f,x+8,base+9)
            line(c,x,base-h,x+4,base-h*.32f,0xFFFFF6A7.toInt(),1.2f)
            for(j in 1..3) line(c,x-5,base-h+j*h*.13f,x+3,base-h+j*h*.13f+8,fade(0xFFFFF7A0.toInt(),80))
        }
        glow(c,172f+sin(t)*55,217f,45f,fade(0xFFFFEF80.toInt(),25))
    }

    private fun boron(c: Canvas,t: Float) {
        // Dark, angular aggregate; no implied quartz habit.
        for(i in 0..8) {
            val x=104+samples[i]*147; val y=192+samples[i+20]*85; val r=22+samples[i+40]*23
            poly(c,0xFF27232D.toInt(),x-r,y,x-r*.5f,y-r,x+r*.5f,y-r*.8f,x+r,y+8,x+6,y+r*.5f)
            poly(c,0xFF53404A.toInt(),x-r*.5f,y-r,x+r*.5f,y-r*.8f,x+2,y-3)
            poly(c,0xFF78616A.toInt(),x-r,y,x-r*.5f,y-r,x+2,y-3)
            line(c,x-r*.5f,y-r,x+r*.5f,y-r*.8f,0xFFA59393.toInt())
            line(c,x+2,y-3,x+6,y+r*.5f,0xFF483941.toInt())
        }
        glow(c,180f+sin(t)*38,189f,36f,fade(Color.WHITE,16))
    }

    private fun phosphorus(c: Canvas,t: Float) {
        // Red phosphorus is amorphous: granular mound, no tall crystal pillars.
        oval(c,180f,278f,98f,19f,0xFF392635.toInt())
        for(i in 0..65) {
            val x=99+samples[i]*162
            val top=212f+abs(x-180)*.5f
            val y=top+samples[i+70]*(280-top)
            val r=3+samples[(i+32)%samples.size]*9
            poly(c,if(i%2==0) 0xFFAA443B.toInt() else 0xFF742D30.toInt(),x-r,y,x-r*.4f,y-r,x+r*.6f,y-r*.7f,x+r,y+3,x,y+r*.5f)
            line(c,x-r*.4f,y-r,x+r*.6f,y-r*.7f,0xFFCD7060.toInt(),.7f)
        }
        glow(c,174f+sin(t)*30,239f,30f,fade(0xFFFFAD81.toInt(),18))
    }

    private fun plates(c: Canvas,t: Float,color: Int,dark: Boolean) {
        // Fractured metallic slabs; angular material illustration, not a lattice diagram.
        for(i in 0..6) {
            val x=105+samples[i]*142; val y=192+i*14f; val w=24+samples[i+20]*32
            val lift=14+samples[i+40]*22
            poly(c,if(dark) 0xFF171522.toInt() else 0xFF485461.toInt(),x-w,y,x+w*.6f,y-lift,x+w,y+12,x-w*.6f,y+lift)
            poly(c,color,x-w,y,x-4,y-lift-12,x+w*.6f,y-lift,x+w,y+5,x-w*.6f,y+lift-7)
            line(c,x-w,y,x-4,y-lift-12,fade(Color.WHITE,170),1f)
            line(c,x-4,y-lift-12,x+w*.6f,y-lift,fade(Color.WHITE,105),1f)
            for(j in 0..3) line(c,x-w*.5f+j*7,y-1,x+4+j*7,y-lift*.4f,fade(Color.WHITE,50),.6f)
        }
        glow(c,180f+sin(t)*42,221f,45f,fade(Color.WHITE,22))
    }

    private fun selenium(c: Canvas,t: Float) {
        // Grey selenium: a ridged, metallic-looking aggregate.
        for(i in 0..11) {
            val x=95+i*15f; val h=44+samples[i]*79; val y=281+samples[i+20]*9
            poly(c,0xFF7F8694.toInt(),x-7,y,x-9,y-h+8,x+1,y-h,x+9,y-h+13,x+7,y)
            poly(c,0xFF424B5C.toInt(),x+1,y-h,x+9,y-h+13,x+7,y,x+1,y-9)
            line(c,x-5,y-h+11,x-4,y-8,0xFFC5C8D0.toInt())
            for(j in 0..2) line(c,x-5+j*3,y-h+16,x-4+j*3,y-12,fade(Color.WHITE,55),.5f)
        }
        glow(c,172f+sin(t)*45,193f,36f,fade(Color.WHITE,20))
    }

    private fun needles(c: Canvas,t: Float,ore: Boolean) {
        // Stibnite (Sb2S3) needles for antimony; a coarser silver fan for tellurium.
        poly(c,0xFF41434C.toInt(),95f,284f,139f,263f,222f,266f,269f,290f,228f,305f,126f,300f)
        val count=if(ore) 18 else 10
        for(i in 0 until count) {
            val rootX=151+samples[i]*55; val rootY=287f+samples[i+20]*8
            val angle=(-157+samples[i+40]*126)*PI.toFloat()/180
            val length=63+samples[i+60]*108
            val tipX=(rootX+cos(angle)*length).coerceIn(64f,296f)
            val tipY=(rootY+sin(angle)*length).coerceAtLeast(111f)
            val w=if(ore) 3+samples[i+80]*4 else 7+samples[i+80]*6
            poly(c,0xFF98A7B6.toInt(),rootX-w,rootY,tipX-w*.3f,tipY+5,tipX,tipY,tipX+w*.6f,tipY+10,rootX+w,rootY)
            poly(c,0xFF495564.toInt(),rootX,rootY,tipX,tipY,tipX+w*.6f,tipY+10,rootX+w,rootY)
            line(c,rootX-w*.5f,rootY-5,tipX,tipY,0xFFE0E4E8.toInt(),.8f)
        }
        glow(c,166f+sin(t)*35,203f,32f,fade(Color.WHITE,20))
    }

    private fun iodine(c: Canvas,t: Float) {
        // Heated iodine in a bell jar: dark solid plates and violet sublimation vapour.
        ink(fade(0xFFC7B8EE.toInt(),12)); c.drawRoundRect(87f,125f,273f,293f,38f,38f,p)
        ink(fade(0xFFC7B8EE.toInt(),100),1f); c.drawRoundRect(87f,125f,273f,293f,38f,38f,p)
        c.save()
        path.reset(); path.addRoundRect(89f,127f,271f,291f,36f,36f,Path.Direction.CW)
        c.clipPath(path)
        for(i in 0..9) {
            val x=132+samples[i]*97+sin(t+i)*8; val y=157+samples[i+20]*48+sin(t+i*.7f)*9
            glow(c,x,y,30f,fade(0xFFAA73DC.toInt(),36))
        }
        c.save(); c.translate(0f,29f); c.scale(1f,.9f)
        plates(c,t,0xFF51475F.toInt(),true); c.restore()
        c.restore()
        oval(c,180f,295f,96f,9f,0xFF656071.toInt(),3f)
        line(c,97f,169f,97f,246f,fade(Color.WHITE,80))
    }

    private fun bismuth(c: Canvas,t: Float) {
        // Nested square terraces emulate hopper growth; oxide interference colours.
        c.save(); c.translate(180f,214f); c.scale(1f,.74f); c.rotate(-27f)
        for(i in 0..8) {
            val half=83f-i*8; val offset=i*2f
            val color=Color.HSVToColor(floatArrayOf((35f+i*31+sin(t)*9)%360,.44f,.91f))
            val shadow=Color.HSVToColor(floatArrayOf((35f+i*31)%360,.55f,.43f))
            poly(c,shadow,-half,-half+offset,half,-half+offset,half,half+offset+8,-half,half+offset+8)
            ink(color,7f); c.drawRect(-half,-half+offset,half,half+offset,p)
            line(c,-half,-half+offset,half,-half+offset,fade(Color.WHITE,160),1f)
            line(c,-half,-half+offset,-half,half+offset,fade(Color.WHITE,105),1f)
        }
        c.restore()
        // A smaller intergrown terrace beside the main hollow crystal.
        c.save(); c.translate(257f,277f); c.scale(.38f,.29f); c.rotate(15f)
        for(i in 0..4) {
            val r=64f-i*11
            ink(Color.HSVToColor(floatArrayOf(180f+i*25,.4f,.8f)),9f); c.drawRect(-r,-r,r,r,p)
        }
        c.restore()
    }

    private fun liquid(c: Canvas,t: Float) {
        val metal=if(z==80) 0xFFD3E6F2.toInt() else 0xFFE47D51.toInt()
        for(i in 4 downTo 0) oval(c,180f,272f+i*4,76f-i*6,18f,fade(metal,35+i*25))
        for(i in 0..6) {
            val x=116+samples[i]*129; val y=161+samples[i+20]*94+sin(t+i)*9; val r=7+samples[i+40]*15
            glow(c,x,y,r*2,fade(metal,80)); circle(c,x,y,r,metal)
            oval(c,x-r*.3f,y-r*.4f,r*.4f,r*.18f,Color.WHITE)
        }
        for(i in 0..2) oval(c,180f,272f,33f+i*18+sin(t)*5,7f+i*3,fade(metal,120),1f)
    }

    private fun circuit(c: Canvas,t: Float) {
        for(i in 0..7) {
            val y=156f+i*17
            for(s in floatArrayOf(-1f,1f)) {
                val end=180+s*(86+samples[i]*30)
                line(c,180+s*42,y,end,y,a,1.2f)
                line(c,end,y,end,y-12,fade(a,130)); circle(c,end,y-12,2f,a)
                glow(c,180+s*(43+(sin(t+i)+1)*25),y,5f,fade(a,140))
            }
        }
        poly(c,0xFF526578.toInt(),132f,157f,215f,142f,236f,231f,151f,249f)
        poly(c,0xFF172A3B.toInt(),143f,165f,209f,154f,224f,221f,159f,236f)
        for(i in 0..3) for(j in 0..3) {
            val x=158f+i*15+j*2; val y=173f+j*15-i*2
            poly(c,fade(if((i+j+z)%2==0) a else b,160),x,y,x+8,y-1,x+9,y+7,x+1,y+8)
        }
        oval(c,180f,288f,77f,10f,fade(a,80),1f)
    }

    private fun aurora(c: Canvas,t: Float) {
        for(i in 0..28) {
            val x=74+i*7f; val y=156+sin(i*.22f+t)*25
            line(c,x,y,x+sin(t+i*.1f)*12,258f,fade(if(i%2==0) a else b,60),4f)
        }
        for(layer in 0..2) {
            path.reset(); path.moveTo(50f,312f)
            for(i in 0..10) path.lineTo(50f+i*26,262f+layer*13-samples[i+layer*12]*35)
            path.lineTo(315f,320f); path.close(); ink(if(layer==0) 0xFF536479.toInt() else 0xFF192737.toInt()); c.drawPath(path,p)
        }
        for(i in 0..6) gem(c,110f+i*24,280+samples[i]*13,5f,a)
    }

    private fun reactor(c: Canvas,t: Float) {
        glow(c,180f,221f,95f,fade(a,120))
        for(i in 0..4) {
            val x=126+i*27f; val top=153+samples[i]*18
            ink(0xFF435958.toInt()); c.drawRoundRect(x-9,top,x+9,280f,5f,5f,p)
            line(c,x,top+8,x,271f,a,4f)
            for(j in 0..3) line(c,x-11,182+j*24f,x+11,182+j*24f,0xFFB8C7AC.toInt(),3f)
            glow(c,x,220+sin(t*2+i)*37,19f,fade(a,150))
        }
        oval(c,180f,285f,88f,15f,0xFF526757.toInt()); oval(c,180f,281f,88f,15f,a,1.3f)
        for(i in 0..9) circle(c,112+samples[i]*136,130+samples[i+20]*140+sin(t+i)*10,2f,fade(a,170))
    }

    private fun accelerator(c: Canvas,t: Float) {
        c.save(); c.translate(180f,212f); c.rotate(-18f+(z%5)*7)
        oval(c,0f,0f,107f,67f,0xFF526077.toInt(),12f)
        oval(c,0f,0f,107f,67f,a,1.5f)
        val modules=8+z%5
        for(i in 0 until modules) {
            val angle=i*6.283f/modules
            c.save(); c.translate(cos(angle)*107,sin(angle)*67); c.rotate(angle*180/PI.toFloat())
            ink(0xFF887A83.toInt()); c.drawRoundRect(-12f,-9f,12f,9f,3f,3f,p)
            line(c,-8f,-5f,8f,-5f,a,2f); c.restore()
        }
        for(s in floatArrayOf(-1f,1f)) {
            val angle=t*2*s
            glow(c,cos(angle)*107,sin(angle)*67,14f,a)
            line(c,s*100,0f,0f,0f,fade(a,130),2f)
        }
        glow(c,0f,0f,36f,fade(b,200))
        for(i in 0..11) {
            val angle=samples[i]*6.283f+t
            val r=12+samples[i+20]*35
            line(c,0f,0f,cos(angle)*r,sin(angle)*r,fade(a,170))
        }
        circle(c,0f,0f,5f,Color.WHITE); c.restore()
    }

    private fun battery(c: Canvas,t: Float) {
        // Rechargeable battery motif, not a literal diagram of ion transport.
        for(i in 0..2) {
            val x=114f+i*44; val y=154f+abs(i-1)*14
            ink(0xFF435468.toInt()); c.drawRoundRect(x-16,y,x+16,277f,7f,7f,p)
            ink(fade(a,150),1.5f); c.drawRoundRect(x-16,y,x+16,277f,7f,7f,p)
            ink(0xFFCAD2D9.toInt()); c.drawRect(x-7,y-6,x+7,y,p)
            for(j in 0..4) {
                val alpha=(90+60*sin(t*2-j*.7f+i)).toInt()
                ink(fade(a,alpha)); c.drawRoundRect(x-10,260f-j*18,x+10,271f-j*18,2f,2f,p)
            }
            line(c,x-4,y+14,x+4,y+14,Color.WHITE)
            line(c,x,y+10,x,y+18,Color.WHITE)
        }
        line(c,98f,289f,253f,289f,fade(a,160),2f)
        poly(c,0xFFFFDC9A.toInt(),259f,182f,239f,216f,254f,216f,243f,246f,274f,205f,257f,205f)
        glow(c,253f,216f,28f,fade(a,50))
    }

    private fun salt(c: Canvas,t: Float) {
        // A small NaCl lattice fragment: alternating ion species, no molecular pairs.
        val sodium=0xFFF3D39E.toInt(); val chlorine=0xFF91D6BD.toInt()
        fun x(i: Int,k: Int)=117f+i*40+k*22
        fun y(j: Int,k: Int)=164f+j*43-k*13
        for(k in 0..1) for(j in 0..2) for(i in 0..2) {
            val xx=x(i,k); val yy=y(j,k)
            if(i<2) line(c,xx,yy,x(i+1,k),yy,0xFF87979E.toInt(),2f)
            if(j<2) line(c,xx,yy,xx,y(j+1,k),0xFF87979E.toInt(),2f)
            if(k==0) line(c,xx,yy,x(i,1),y(j,1),fade(Color.WHITE,80))
        }
        for(k in 1 downTo 0) for(j in 0..2) for(i in 0..2) {
            val xx=x(i,k); val yy=y(j,k); val isSodium=(i+j+k)%2==0
            val color=if(isSodium) sodium else chlorine
            circle(c,xx,yy,if(isSodium) 7f else 10f,color)
            oval(c,xx-2,yy-3,2.5f,1.5f,fade(Color.WHITE,170))
        }
        oval(c,180f,285f,90f,10f,fade(a,60),1f)
        glow(c,180f,220f,110f,fade(a,(12+8*sin(t)).toInt()))
    }

    private fun magnet(c: Canvas,t: Float) {
        // Horseshoe symbol for permanent magnetic alloys containing Nd or Sm.
        for(i in 0..4) {
            ink(fade(a,75),1f); path.reset(); path.moveTo(130f,178f)
            path.cubicTo(94f-i*12,103f-i*3,268f+i*12,103f-i*3,230f,178f)
            c.drawPath(path,p)
        }
        ink(0xFFA7B3C3.toInt(),28f)
        path.reset(); path.moveTo(130f,168f); path.lineTo(130f,235f)
        path.cubicTo(130f,292f,230f,292f,230f,235f); path.lineTo(230f,168f)
        c.drawPath(path,p)
        line(c,130f,166f,130f,195f,0xFFEA8E7B.toInt(),28f)
        line(c,230f,166f,230f,195f,0xFF80BCE1.toInt(),28f)
        for(i in 0..17) {
            val x=113f+samples[i]*136; val y=113f+samples[i+30]*30
            val angle=atan2(y-160,x-180)+PI.toFloat()/2
            line(c,x,y,x+cos(angle)*5,y+sin(angle)*5,fade(a,160),1.3f)
        }
        glow(c,130f,170f,24f,fade(a,(65+25*sin(t*2)).toInt()))
        glow(c,230f,170f,24f,fade(b,(65-25*sin(t*2)).toInt()))
    }

    private fun coil(c: Canvas,t: Float) {
        val copper=0xFFDF9871.toInt()
        line(c,94f,269f,116f,269f,copper,5f)
        line(c,244f,171f,272f,171f,copper,5f)
        for(i in 0..9) {
            val x=115f+i*14
            oval(c,x,215f,13f,51f,copper,5f)
            ink(fade(Color.WHITE,135),1.5f); c.drawArc(x-13,164f,x+13,266f,190f,100f,false,p)
        }
        line(c,87f,272f,100f,272f,0xFFFFD8AB.toInt(),8f)
        line(c,265f,168f,278f,168f,0xFFFFD8AB.toInt(),8f)
        glow(c,181f+sin(t)*66,208f,52f,fade(copper,40))
        // Insulation rings on the terminals distinguish a wire from an atomic orbit.
        line(c,102f,269f,110f,269f,0xFF425B76.toInt(),9f)
        line(c,251f,171f,261f,171f,0xFF425B76.toInt(),9f)
    }

    private fun specimen(c: Canvas,t: Float) {
        // Conservative fallback: a sealed laboratory specimen, no invented application.
        ink(fade(a,16)); c.drawRoundRect(111f,143f,249f,282f,14f,14f,p)
        ink(fade(Color.WHITE,110),1.3f); c.drawRoundRect(111f,143f,249f,282f,14f,14f,p)
        ink(0xFF73808D.toInt()); c.drawRoundRect(106f,132f,254f,151f,4f,4f,p)
        for(i in 0..10) line(c,114f+i*12,134f,114f+i*12,149f,0xFFCED3D6.toInt())
        for(i in 0..4) {
            val x=133f+i*23; val y=256f-samples[i]*14
            poly(c,0xFFA7AFB5.toInt(),x-10,y,x-7,y-19-samples[i+20]*24,x+7,y-25,x+12,y+4)
            line(c,x-7,y-19-samples[i+20]*24,x+7,y-25,fade(Color.WHITE,150))
        }
        line(c,122f,164f,122f,241f,fade(Color.WHITE,105),2f)
        oval(c,180f,291f,84f,9f,fade(a,60),1f)
        glow(c,218f,185f,42f,fade(a,(20+8*sin(t)).toInt()))
    }

    private fun bones(c: Canvas,t: Float) {
        // Two long-bone silhouettes, with sculpted ends and an ivory shaft.
        // The mineral component is a calcium phosphate, not metallic calcium.
        fun bone(x: Float,y: Float,angle: Float,scale: Float) {
            c.save(); c.translate(x,y); c.rotate(angle); c.scale(scale,scale)
            path.reset(); path.moveTo(-10f,-60f)
            path.cubicTo(-40f,-48f,-45f,-89f,-22f,-95f)
            path.cubicTo(-9f,-99f,-4f,-88f,0f,-85f)
            path.cubicTo(16f,-108f,42f,-91f,34f,-72f)
            path.cubicTo(31f,-62f,17f,-60f,13f,-53f)
            path.cubicTo(5f,-22f,6f,27f,16f,57f)
            path.cubicTo(43f,52f,46f,86f,27f,91f)
            path.cubicTo(12f,98f,4f,89f,0f,85f)
            path.cubicTo(-17f,105f,-42f,89f,-34f,70f)
            path.cubicTo(-29f,59f,-17f,62f,-14f,52f)
            path.cubicTo(-6f,21f,-6f,-27f,-10f,-60f); path.close()
            ink(Color.WHITE)
            p.shader=LinearGradient(-36f,0f,36f,0f,
                intArrayOf(0xFF9C8766.toInt(),0xFFF5E4BF.toInt(),0xFFFFF8E1.toInt(),0xFFB39F7E.toInt()),
                floatArrayOf(0f,.35f,.58f,1f),Shader.TileMode.CLAMP)
            c.drawPath(path,p); ink(0xFFD8C5A0.toInt(),.8f); c.drawPath(path,p)
            ink(fade(Color.WHITE,150),2f); path.reset(); path.moveTo(-3f,-53f)
            path.cubicTo(-6f,-12f,-3f,29f,3f,53f); c.drawPath(path,p)
            for(i in 0..9) {
                val xx=-20+samples[i]*43; val yy=if(i%2==0) -78f else 76f
                oval(c,xx,yy+samples[i+20]*8,1.4f,2f,fade(0xFF7E6749.toInt(),65))
            }
            c.restore()
        }
        glow(c,177f,213f,112f,fade(0xFFF3DBAA.toInt(),30))
        bone(222f,215f,24f,.72f)
        bone(161f,211f,24f,1f)
        // A soft moving light on the surface; the bones themselves do not glow.
        glow(c,152f+sin(t)*14,177f,34f,fade(Color.WHITE,20))
    }

    private fun aircraft(c: Canvas,t: Float) {
        val metal=0xFFBDC8D6.toInt()
        // Swept-wing aircraft seen from above. No claim that the whole plane is titanium.
        c.save(); c.translate(180f,212f); c.rotate(21f+sin(t)*2)
        for(s in floatArrayOf(-1f,1f)) {
            ink(fade(a,50),1f); path.reset(); path.moveTo(s*42,-24f)
            path.cubicTo(s*72,3f,s*95,34f,s*108,78f); c.drawPath(path,p)
            poly(c,metal,s*9,-45f,s*106,43f,s*104,59f,s*10,25f)
            poly(c,0xFF687B96.toInt(),s*12,8f,s*104,59f,s*39,43f,s*13,31f)
            line(c,s*14,-32f,s*96,43f,0xFFF3F6FF.toInt(),1.2f)
            // Engine nacelles, wing seams and fasteners.
            ink(0xFF4A5E78.toInt()); c.drawRoundRect(s*38-6,-1f,s*38+6,37f,6f,6f,p)
            oval(c,s*38,1f,4f,2.5f,0xFF0B1729.toInt())
            line(c,s*39,11f,s*39,29f,metal)
            for(i in 0..5) circle(c,s*(24+i*9f),-12f+i*6,0.8f,0xFF455A75.toInt())
            poly(c,metal,s*7,52f,s*40,80f,s*39,88f,s*7,76f)
        }
        path.reset(); path.moveTo(0f,-102f)
        path.cubicTo(-10f,-87f,-13f,-55f,-12f,-23f)
        path.lineTo(-9f,65f); path.lineTo(0f,96f); path.lineTo(9f,65f); path.lineTo(12f,-23f)
        path.cubicTo(13f,-55f,10f,-87f,0f,-102f); path.close()
        ink(metal); p.shader=LinearGradient(-13f,0f,13f,0f,
            intArrayOf(0xFF61728B.toInt(),0xFFF4F3EB.toInt(),0xFF9CAABD.toInt()),null,Shader.TileMode.CLAMP)
        c.drawPath(path,p)
        poly(c,0xFF18354F.toInt(),0f,-75f,-7f,-54f,-6f,-32f,6f,-32f,7f,-54f)
        line(c,-3f,-57f,-3f,-37f,0xFF87B1CD.toInt(),1.4f)
        line(c,0f,30f,0f,83f,0xFF6A7790.toInt())
        c.restore()
    }

    private fun chrome(c: Canvas,t: Float) {
        // A plated curved fitting: its reflection bands reveal the mirror finish.
        oval(c,180f,293f,82f,11f,0xFF293647.toInt())
        oval(c,180f,288f,82f,10f,0xFFAEBCC9.toInt(),2f)
        path.reset(); path.moveTo(147f,277f); path.lineTo(147f,181f)
        path.cubicTo(147f,118f,238f,118f,238f,177f); path.lineTo(238f,190f)
        ink(Color.WHITE,25f)
        val shift=sin(t)*8
        p.shader=LinearGradient(122f+shift,0f,251f+shift,0f,
            intArrayOf(0xFF1B3045.toInt(),0xFFF9FCFF.toInt(),0xFF566A82.toInt(),0xFFEBF2F6.toInt(),0xFF182638.toInt(),0xFFDCE9F2.toInt()),
            floatArrayOf(0f,.18f,.31f,.50f,.69f,1f),Shader.TileMode.CLAMP)
        c.drawPath(path,p)
        ink(fade(Color.WHITE,200),1.2f); path.reset(); path.moveTo(138f,253f)
        path.lineTo(138f,178f); path.cubicTo(138f,128f,224f,114f,243f,159f); c.drawPath(path,p)
        // Hexagonal coupling and collar make this a manufactured object.
        poly(c,0xFFB9C7D6.toInt(),129f,252f,146f,245f,164f,252f,164f,273f,146f,280f,129f,273f)
        poly(c,0xFF435771.toInt(),146f,256f,164f,252f,164f,273f,146f,280f)
        line(c,131f,254f,143f,259f,Color.WHITE,2f)
        oval(c,238f,191f,13f,5f,0xFFDBE6EE.toInt())
        oval(c,238f,191f,9f,3f,0xFF152536.toInt())
        for(i in 0..3) line(c,137f,231f+i*4,157f,231f+i*4,fade(Color.WHITE,130))
        val x=196f+sin(t)*9; val y=133f
        line(c,x-6,y,x+6,y,fade(Color.WHITE,190))
        line(c,x,y-6,x,y+6,fade(Color.WHITE,190))
    }

    private fun shield(c: Canvas,t: Float) {
        // Symbol of zinc protecting steel from corrosion, not a literal force field.
        path.reset(); path.moveTo(180f,123f); path.lineTo(264f,149f); path.lineTo(255f,228f)
        path.cubicTo(246f,263f,209f,288f,180f,304f)
        path.cubicTo(151f,288f,114f,263f,105f,228f)
        path.lineTo(96f,149f); path.close()
        ink(Color.WHITE); p.shader=LinearGradient(98f,130f,262f,282f,
            intArrayOf(0xFFE2EBEC.toInt(),0xFF819BAB.toInt(),0xFFBCCFD5.toInt(),0xFF526B80.toInt()),null,Shader.TileMode.CLAMP)
        c.drawPath(path,p)
        c.save(); c.clipPath(path)
        // Stylised spangle texture of a galvanised surface, with no atomic interpretation.
        for(i in 0..35) {
            val x=103+samples[i]*153; val y=136+samples[i+40]*157
            val r=8+samples[i+80]*16
            poly(c,fade(if(i%2==0) Color.WHITE else 0xFF405B76.toInt(),40),x-r,y,x,y-r*.6f,x+r,y+r*.3f,x+4,y+r)
        }
        c.restore()
        // An inset rim and seam evoke a plated metal surface.
        ink(0xFFDFE9EF.toInt(),3f); path.reset(); path.moveTo(180f,134f)
        path.lineTo(252f,157f); path.lineTo(244f,226f)
        path.quadTo(230f,265f,180f,291f); path.quadTo(130f,265f,116f,226f)
        path.lineTo(108f,157f); path.close(); c.drawPath(path,p)
        line(c,180f,140f,180f,277f,fade(Color.WHITE,130),1f)
        for(i in 0..6) {
            val side=if(i%2==0) -1 else 1
            val y=151+samples[i]*99+sin(t*2+i)*7
            val x=180+side*(96+samples[i+30]*23)
            line(c,x,y,x+side*4,y+8,fade(0xFF9CCADD.toInt(),170),2f)
        }
    }
}
