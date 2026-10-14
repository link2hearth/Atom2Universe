package com.Atom2Universe.app.games.hamlet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Banc d'aperçu de Hameau : dessine les planches de tous les objets et un petit village entier,
 * dans `app/build/apercu/hamlet/`. C'est la seule façon de juger le dessin sans téléphone.
 * Exclu par défaut (voir `bancsDeMesure` dans `app/build.gradle.kts`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi", manifest = Config.NONE, application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HamletApercuTest {

    private val dossier = File("build/apercu/hamlet").also { it.mkdirs() }

    private fun ecrire(nom: String, bmp: Bitmap) {
        FileOutputStream(File(dossier, "$nom.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun tuileDuHub() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        assertTrue(com.Atom2Universe.app.hub.HubTileArtworks.forActivity(HamletActivity::class.java.name) == HamletHubTileDrawable::class)
        val drawable = HamletHubTileDrawable(context)
        val render = HamletHubTileDrawable::class.java.getDeclaredMethod("render", Canvas::class.java,
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).apply { isAccessible = true }
        for ((width, height) in listOf(768 to 768, 384 to 192, 192 to 384)) {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            render.invoke(drawable, Canvas(bmp), width.toFloat(), height.toFloat())
            ecrire("hub_${width}x$height", bmp)
            assertTrue(Color.alpha(bmp.getPixel(width / 2, height / 2)) == 255)
        }
    }

    private fun planche(nom: String, defs: List<Def>, cols: Int, cell: Int, cropOf: (Def) -> String? = { null }, stage: Int = -1) {
        val rows = (defs.size + cols - 1) / cols
        val bmp = Bitmap.createBitmap(cols * cell, rows * (cell + 24), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF7CC443.toInt())
        val art = HamletArt()
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 18f }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        for ((i, d) in defs.withIndex()) {
            val col = i % cols; val row = i / cols
            val s = art.forDef(d, false, cropOf(d), stage)
            val k = minOf(1f, (cell - 6f) / s.bmp.width, (cell - 6f) / s.bmp.height)
            val m = Matrix()
            m.postScale(k, k)
            m.postTranslate(col * cell + (cell - s.bmp.width * k) / 2f, row * (cell + 24) + (cell - s.bmp.height * k))
            c.drawBitmap(s.bmp, m, paint)
            c.drawText(d.id, col * cell + 4f, row * (cell + 24) + cell + 18f, label)
        }
        ecrire(nom, bmp)
        assertTrue(true)
    }

    @Test
    fun planches() {
        val d = Hamlet.defs
        planche("maisons", d.filter { it.cat == Cat.HOME }, 5, 300)
        planche("ateliers1", d.filter { it.cat == Cat.SHOP }.take(20), 5, 300)
        planche("ateliers2", d.filter { it.cat == Cat.SHOP }.drop(20).take(20), 5, 300)
        planche("ateliers3", d.filter { it.cat == Cat.SHOP }.drop(40), 5, 300)
        planche("decors1", d.filter { it.cat == Cat.DECOR }.take(40), 8, 200)
        d.filter { it.cat == Cat.DECOR }.drop(40).chunked(40).forEachIndexed { i, page -> planche("decors${i + 2}", page, 8, 200) }
        planche("nature", d.filter { it.cat == Cat.NATURE }, 8, 200)
        planche("elevages", d.filter { it.cat == Cat.RANCH }, 3, 300)
        planche("decouvertes", HamletJourney.buildings.keys.map { Hamlet.byId.getValue(it) }, 4, 300)
        // les champs : chaque culture mûre, puis une culture à chaque stade
        val fields = Hamlet.crops.map { Hamlet.byId[if (it.magic) "magicfield" else "field"]!! to it }
        val sheet = Bitmap.createBitmap(8 * 190, ((fields.size + 7) / 8 + 1) * 190, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(0xFF7CC443.toInt())
        val art = HamletArt()
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 16f }
        for ((i, p) in fields.withIndex()) {
            val s = art.forDef(p.first, false, p.second.id, 2)
            c.drawBitmap(s.bmp, (i % 8) * 190f + 20f, (i / 8) * 190f + 30f, null)
            c.drawText(p.second.id, (i % 8) * 190f + 6f, (i / 8) * 190f + 20f, label)
        }
        val rowY = ((fields.size + 7) / 8) * 190f
        for (st in -1..3) {
            val s = art.forDef(Hamlet.byId["field"]!!, false, "carrots", st)
            c.drawBitmap(s.bmp, st.plus(1) * 190f + 20f, rowY + 30f, null)
            c.drawText("stade $st", (st + 1) * 190f + 6f, rowY + 20f, label)
        }
        ecrire("cultures", sheet)
        // la halle à chaque niveau
        val hall = Bitmap.createBitmap(Hamlet.halls.size * 380, 560, Bitmap.Config.ARGB_8888)
        val hc = Canvas(hall)
        hc.drawColor(0xFF7CC443.toInt())
        val g = HamletGame()
        for (lv in 1..Hamlet.halls.size) {
            g.hall = lv
            g.startNew(false)
            g.hall = lv
            val s = art.forObj(g.objs.values.first { it.t == "hall" }, g)
            hc.drawBitmap(s.bmp, (lv - 1) * 380f + 10f, 20f, null)
        }
        ecrire("halle", hall)
    }

    @Test fun carnetEtAtelier() {
        val controller = Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        // Comme les autres bancs du projet : vrais XML lus sur disque, sans charger tout l'APK.
        val strings = HashMap<Int, String>()
        val names = HashMap<String, Int>()
        File("src/main/res/values-fr").listFiles { f -> f.name.startsWith("strings_hamlet") }!!.forEach { f ->
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val nodes = doc.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val key = node.attributes.getNamedItem("name").nodeValue
                val id = com.Atom2Universe.app.R.string::class.java.getField(key).getInt(null)
                names[key] = id; strings[id] = node.textContent.replace("\\n", "\n").replace("\\'", "'")
            }
        }
        val base = activity.resources
        val loaded = object : android.content.res.Resources(base.assets, base.displayMetrics, base.configuration) {
            override fun getString(id: Int): String = strings[id] ?: super.getString(id)
            override fun getString(id: Int, vararg args: Any?): String = String.format(java.util.Locale.FRANCE, getString(id), *args)
            override fun getText(id: Int): CharSequence = strings[id] ?: super.getText(id)
            override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int = names[name] ?: super.getIdentifier(name, defType, defPackage)
        }
        val context = object : android.content.ContextWrapper(activity) { override fun getResources() = loaded }
        val game = HamletGame().apply { startNew(false); journey.chapter = 4; workStep = 9 }
        game.journey.companions[0] = 1; game.journey.companions[1] = 1
        game.journey.assign(0, 0); game.journey.assign(1, 1)
        for ((tab, name) in listOf(0 to "carnet", 1 to "voyages", 2 to "compagnons", 3 to "recherches", 4 to "atelier_gratuit")) {
            HamletJourneyDialog(context, game).show(tab)
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val view = dialog.window!!.decorView
            val width = 1080; val height = 1600
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.AT_MOST))
            view.layout(0, 0, width, view.measuredHeight)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val bitmap = Bitmap.createBitmap(width, view.measuredHeight, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap)); ecrire(name, bitmap)
            assertTrue(view.measuredHeight > 400)
            if (tab == 4) {
                fun button(view: android.view.View, title: String): android.widget.Button? {
                    if (view is android.widget.Button && view.text.toString() == title) return view
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) button(view.getChildAt(i), title)?.let { return it }
                    return null
                }
                repeat(3) {
                    val title = strings.getValue(names.getValue("hamlet_work_tool_${game.workTool}"))
                    val target = button(view, title) ?: error("outil manquant")
                    assertTrue(target.performClick())
                }
                assertTrue(game.workSeconds == 300 && game.workStep == 0)
            }
            dialog.dismiss()
        }
        controller.pause().stop().destroy()
    }

    private fun scene(nom: String, g: HamletGame, w: Int, h: Int, zoom: Float, night: Boolean) {
        val art = HamletArt()
        val r = HamletRenderer(g, art)
        r.width = w; r.height = h; r.zoom = zoom; r.density = 3f
        r.nightEnabled = night
        r.syncVillagers(Random(1))
        val hall = g.objs.values.first { it.t == "hall" }
        r.centerOn(hall.x + 2f, hall.y + 9f)
        for (i in 0 until 400) r.update(0.1f, Random(2 + i))
        if (night) r.daylight = 0f else r.daylight = 1f
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        r.draw(Canvas(bmp))
        ecrire(nom, bmp)
    }

    private fun villageComplet(): HamletGame {
        val g = HamletGame()
        g.startNew(true)
        g.hall = 3
        val o = g.realmOrigin()
        val hx = g.objs.values.first { it.t == "hall" }.x
        val hy = g.objs.values.first { it.t == "hall" }.y
        fun put(id: String, x: Int, y: Int, flip: Boolean = false): Obj? = g.buy(id, x, y, flip)
        // avenues
        for (y in hy + 4..hy + 24) { g.paint("cobble", hx + 1, y); g.paint("cobble", hx + 2, y) }
        for (x in hx - 14..hx + 17) { g.paint("road", x, hy + 12); g.paint("road", x, hy + 22) }
        // rivière et pont
        for (x in hx - 14..hx + 17) { g.paint("river", x, hy + 6) }
        g.paint("bridge", hx + 1, hy + 6); g.paint("bridge", hx + 2, hy + 6)
        // maisons et ateliers
        val homes = listOf("cottage", "hut", "stonecottage", "bluecottage", "farmhouse", "cabin", "villa", "timber", "townhouse", "manor")
        var x = hx - 12
        for (id in homes) { val d = Hamlet.byId[id]!!; put(id, x, hy + 13); x += d.w + 1; if (x > hx + 16) break }
        val shops = listOf("bakery", "florist", "tailor", "blacksmith", "tavern", "windmill", "chapel", "wizard", "clocktower", "lumbermill")
        x = hx - 12
        for (id in shops) { val d = Hamlet.byId[id]!!; put(id, x, hy + 16); x += d.w + 1; if (x > hx + 16) break }
        // champs et cultures
        var fx = hx - 12
        val cr = listOf("corn", "wheat", "carrots", "tomatoes", "pumpkins", "sunflower", "grapes", "cabbage")
        for ((i, c) in cr.withIndex()) {
            val f = put("field", fx, hy + 23) ?: continue
            g.plant(f, c)
            f.t0 -= (Hamlet.cropById[c]!!.time * 1000L * (0.2 + 0.2 * (i % 5))).toLong()
            fx += 3
        }
        // décors et nature
        val dec = listOf("fountain", "statue", "lamp", "bench", "banner_red", "well", "gazebo", "marketstall", "tent")
        var dx = hx - 6
        for (id in dec) { val d = Hamlet.byId[id]!!; put(id, dx, hy + 8); dx += d.w + 1 }
        for (i in 0..16) { put(listOf("treeRound", "treePine", "treeCherry", "willow", "birch", "palm")[i % 6], hx - 14 + i * 2, hy + 9) }
        for (i in 0..12) put(listOf("flowersRed", "flowersYellow", "flowersPink", "flowersBlue")[i % 4], hx + 4 + i, hy + 11)
        for (i in 0..14) put("fence", hx - 12 + i, hy + 21)
        for (i in 0..6) put("hedge", hx + 4 + i, hy + 10)
        x = hx - 12
        for (id in listOf("henyard", "rabbitgarden", "sheepmeadow", "duckpond", "goathill", "dairyfarm")) {
            put(id, x, hy + 26); x += Hamlet.byId.getValue(id).w + 1
        }
        return g
    }

    @Test
    fun village() {
        val g = villageComplet()
        g.objs.values.filter { it.def.cat == Cat.HOME }.forEach { it.t0 = g.now() - it.def.period * 1000L - 1 }
        scene("village_jour", g, 1080, 1700, 0.85f, false)
        scene("village_nuit", g, 1080, 1700, 0.85f, true)
        assertTrue(File(dossier, "village_jour.png").length() > 10_000)
    }

    @Test fun elevageAnime() {
        val g = HamletGame().apply { startNew(true); hall = 2 }
        val hall = g.objs.values.first { it.t == "hall" }
        for (o in g.objs.values.toList()) if (o.def.cat != Cat.HALL) g.sell(o)
        g.paths.fill(0); g.bump()
        for ((i, id) in listOf("henyard", "rabbitgarden", "sheepmeadow", "duckpond", "goathill", "dairyfarm").withIndex()) {
            val x = hall.x - 4 + i % 3 * 4
            val y = hall.y + 9 + i / 3 * 4
            assertTrue("élevage posé : $id", g.buy(id, x, y, false) != null)
            g.buy("flowersRed", x, y + 3, false)
        }
        val r = HamletRenderer(g, HamletArt()).apply { width = 1080; height = 650; zoom = 1.8f; nightEnabled = false; daylight = 1f }
        r.centerOn(hall.x + 1f, hall.y + 12f)
        for (i in 0..2) {
            r.time = i * 2.1f
            val bmp = Bitmap.createBitmap(1080, 650, Bitmap.Config.ARGB_8888)
            r.draw(Canvas(bmp)); ecrire("animaux_$i", bmp)
        }
    }

    @Test fun aventureMenus() {
        val controller = Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val activity = controller.get().apply { setTheme(android.R.style.Theme_Material_Light_NoActionBar) }
        val strings = HashMap<Int, String>(); val names = HashMap<String, Int>()
        File("src/main/res/values-fr").listFiles { f -> f.name.startsWith("strings_hamlet") }!!.forEach { file ->
            val nodes = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i); val key = node.attributes.getNamedItem("name").nodeValue
                val id = com.Atom2Universe.app.R.string::class.java.getField(key).getInt(null)
                names[key] = id; strings[id] = node.textContent.replace("\\n", "\n").replace("\\'", "'")
            }
        }
        val base = activity.resources
        val loaded = object : android.content.res.Resources(base.assets, base.displayMetrics, base.configuration) {
            override fun getString(id: Int) = strings[id] ?: super.getString(id)
            override fun getString(id: Int, vararg args: Any?) = String.format(java.util.Locale.FRANCE, getString(id), *args)
            override fun getText(id: Int): CharSequence = strings[id] ?: super.getText(id)
            override fun getIdentifier(name: String?, defType: String?, defPackage: String?) = names[name] ?: super.getIdentifier(name, defType, defPackage)
        }
        val context = object : android.content.ContextWrapper(activity) { override fun getResources() = loaded }
        val game = HamletGame().apply { startNew(false) }
        fun buttons(view: android.view.View): List<android.widget.Button> = if (view is android.widget.Button) listOf(view)
            else if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) } else emptyList()
        fun snapshot(name: String, view: android.view.View, h: Int = 2000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            fun refresh(v: android.view.View) {
                v.forceLayout(); v.invalidate()
                if (v is android.view.ViewGroup) for (i in 0 until v.childCount) refresh(v.getChildAt(i))
            }
            refresh(view)
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 1080, h)
            val bitmap = Bitmap.createBitmap(1080, h, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap)); ecrire(name, bitmap)
            fun bounds(v: android.view.View, depth: Int = 0): String {
                val pos = IntArray(2); v.getLocationInWindow(pos)
                val label = if (v is android.widget.TextView) v.text.toString().take(40) else ""
                val line = "${" ".repeat(depth)}${v.javaClass.simpleName} ${pos[0]},${pos[1]} ${v.width}x${v.height} scroll=${v.scrollY} $label\n"
                return line + if (v is android.view.ViewGroup) (0 until v.childCount).joinToString("") { bounds(v.getChildAt(it), depth + 1) } else ""
            }
            File(dossier, "$name.layout.txt").writeText(bounds(view))
            val footer = buttons(view).firstOrNull { it.text == loaded.getString(names.getValue("hamlet_a_challenges")) }
            if (footer != null) {
                val position = IntArray(2); footer.getLocationInWindow(position)
                assertTrue("footer $name ${position[1]} / $h", position[1] >= 0 && position[1] + footer.height <= h)
            }
        }
        for (tab in 0..5) {
            HamletAdventureDialog(context, game, onExit = { true }, effectsEnabled = false).show(tab)
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val decor = dialog.window!!.decorView
            snapshot("aventure_menu_$tab", decor)
            assertTrue(buttons(decor).size >= 8)
            if (tab == 0) {
                val combat = buttons(decor).first { it.text == loaded.getString(names.getValue("hamlet_a_battle")) }
                assertTrue(combat.performClick()); snapshot("aventure_combat_debut", decor)
                val battle = game.adventure.activeBattle!!
                battle.advance(5f); snapshot("aventure_combat_effets", decor)
                val cancel = buttons(decor).first { it.text == loaded.getString(names.getValue("hamlet_a_cancel_free")) }
                assertTrue(cancel.performClick()); assertTrue(game.adventure.activeBattle == null)
            }
            dialog.dismiss()
        }
        game.adventure.frontier = 25; game.adventure.heroes[2].owned = true; game.adventure.heroes[4].owned = true; game.adventure.heroes[5].owned = true
        game.adventure.assign(2, 1); game.adventure.assign(4, 4); game.adventure.assign(5, 5)
        HamletAdventureDialog(context, game).show(2)
        val teamDialog = org.robolectric.shadows.ShadowDialog.getLatestDialog(); snapshot("aventure_equipe_six", teamDialog.window!!.decorView); teamDialog.dismiss()
        // Failed saves keep Exit and the current screen available; success closes the dialog.
        var saved = false
        var attempts = 0
        HamletAdventureDialog(context, game, onExit = { attempts++; saved }, effectsEnabled = false).show()
        val exitDialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        val exit = buttons(exitDialog.window!!.decorView).first { it.text == loaded.getString(names.getValue("hamlet_ui_exit")) }
        assertTrue(exit.performClick()); assertTrue(exitDialog.isShowing && attempts == 1)
        saved = true
        assertTrue(exit.performClick()); assertTrue(!exitDialog.isShowing && attempts == 2)
        val g = HamletGame().apply { startNew(true) }
        val hall = g.objs.values.first { it.t == "hall" }; val art = HamletArt()
        g.objs.values.filter { it.def.cat != Cat.HALL }.toList().forEach { g.sell(it) }; g.paths.fill(0); g.bump()
        for ((i, id) in HamletAdventure.FACILITIES.withIndex()) {
            val obj = g.buy(id, hall.x - 5 + (i % 3) * 4, hall.y + 8 + (i / 3) * 4, false)
            assertTrue("placement $id", obj != null)
        }
        val renderer = HamletRenderer(g, art).apply { width = 1080; height = 1000; zoom = 1.5f; density = 3f; nightEnabled = false; daylight = 1f }
        renderer.centerOn(hall.x + 1f, hall.y + 11f)
        val bitmap = Bitmap.createBitmap(1080, 1000, Bitmap.Config.ARGB_8888); renderer.draw(Canvas(bitmap)); ecrire("aventure_batiments_village", bitmap)
        controller.pause().stop().destroy()
    }

    /** Art-only benchmark: exported frames can be opened in Aseprite/LibreSprite.
     * It stays excluded from normal checks with the other preview benchmarks. */
    @Test fun personnagesEtBatimentsAnimes() {
        for (hero in listOf(true, false)) {
            val count = if (hero) 12 else 8
            val poses = HamletCharacterArt.Pose.entries
            val sheet = Bitmap.createBitmap(count * 144, poses.size * 168, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(0xFFF6EDD9.toInt())
            for (id in 0 until count) for (pose in poses) {
                val first = HamletCharacterArt.bitmap(hero, id, pose)
                assertTrue(first === HamletCharacterArt.bitmap(hero, id, pose))
                assertTrue(first.width == 48 && first.height == 56)
                assertTrue(Color.alpha(first.getPixel(0, 0)) == 0)
                if (pose == HamletCharacterArt.Pose.WALK) {
                    assertTrue("walking limbs $hero/$id", !first.sameAs(HamletCharacterArt.bitmap(hero, id, pose, 2f / pose.fps)))
                }
                HamletCharacterArt.draw(canvas, hero, id, id * 144f + 72f, (pose.ordinal + 1) * 168f, 168f, 1f / pose.fps, pose)
            }
            ecrire(if (hero) "personnages_poses" else "creatures_poses", sheet)
        }
        for (frame in 0 until 8) {
            val bmp = Bitmap.createBitmap(1080, 360, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp); c.drawColor(0xFFF6EDD9.toInt())
            for (id in 0 until 12) {
                HamletCharacterArt.draw(c, true, id, id * 90f + 45f, 170f, 130f, frame / 9f, HamletCharacterArt.Pose.WALK)
                HamletCharacterArt.drawResident(c, id, id * 90f + 45f, 340f, 130f, frame / 9f, walking = true)
            }
            ecrire("personnages_animation_$frame", bmp)
        }
        val game = HamletGame().apply { startNew(true) }
        game.objs.values.filter { it.def.cat != Cat.HALL }.toList().forEach { game.sell(it) }
        game.paths.fill(0); game.bump()
        val hall = game.objs.values.first { it.t == "hall" }
        HamletAdventure.FACILITIES.forEachIndexed { i, id ->
            assertTrue(game.buy(id, hall.x - 5 + i % 3 * 4, hall.y + 8 + i / 3 * 4, i % 2 == 1) != null)
        }
        val renderer = HamletRenderer(game, HamletArt()).apply {
            width = 1080; height = 850; zoom = 1.5f; nightEnabled = false; daylight = 1f
            centerOn(hall.x + 1f, hall.y + 11f)
        }
        fun frame(time: Float): Bitmap {
            renderer.time = time
            return Bitmap.createBitmap(1080, 850, Bitmap.Config.ARGB_8888).also { renderer.draw(Canvas(it)) }
        }
        val first = frame(0f)
        assertTrue("mechanisms animate", !first.sameAs(frame(.5f)))
        for (i in 0 until 8) ecrire("batiments_animation_$i", frame(i * .125f))
    }

    @Test fun neighborsRealmsAndPhoto() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val strings = HashMap<Int, String>(); val names = HashMap<String, Int>()
        File("src/main/res/values-fr").listFiles { f -> f.name.startsWith("strings_hamlet") }!!.forEach { file ->
            val nodes = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i); val key = node.attributes.getNamedItem("name").nodeValue
                val id = com.Atom2Universe.app.R.string::class.java.getField(key).getInt(null)
                names[key] = id; strings[id] = node.textContent.replace("\\n", "\n").replace("\\'", "'")
            }
        }
        val base = activity.resources
        val loaded = object : android.content.res.Resources(base.assets, base.displayMetrics, base.configuration) {
            override fun getString(id: Int) = strings[id] ?: super.getString(id)
            override fun getString(id: Int, vararg args: Any?) = String.format(java.util.Locale.FRANCE, getString(id), *args)
            override fun getText(id: Int): CharSequence = strings[id] ?: super.getText(id)
            override fun getIdentifier(name: String?, defType: String?, defPackage: String?) = names[name] ?: super.getIdentifier(name, defType, defPackage)
        }
        val context = object : android.content.ContextWrapper(activity) { override fun getResources() = loaded }
        fun buttons(v: android.view.View): List<android.widget.Button> = if (v is android.widget.Button) listOf(v)
            else if (v is android.view.ViewGroup) (0 until v.childCount).flatMap { buttons(v.getChildAt(it)) } else emptyList()
        fun snapshot(name: String, view: android.view.View) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            fun dirty(v: android.view.View) { v.forceLayout(); v.invalidate(); if (v is android.view.ViewGroup) for (i in 0 until v.childCount) dirty(v.getChildAt(i)) }
            dirty(view)
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2100, android.view.View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 1080, 2100)
            Bitmap.createBitmap(1080, 2100, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)); ecrire(name, it); it.recycle() }
        }
        val game = HamletGame().apply { startNew(true) }
        var closed = false
        HamletNeighborsDialog(context, game, haptics = false, effectsEnabled = false, onClose = { closed = true }).show()
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        val view = dialog.window!!.decorView
        snapshot("voisins_liste", view)
        buttons(view).first { it.text == loaded.getString(names.getValue("hamlet_neighbors_visit")) }.performClick()
        snapshot("voisin_village", view)
        buttons(view).first { it.text == loaded.getString(names.getValue("hamlet_neighbors_place")) }.performClick()
        assertTrue(game.neighbors.orders.size == 1)
        val order = game.neighbors.orders.single()
        repeat(6) {
            val tool = game.neighbors.helpTool(order.neighbor, order.shop)
            buttons(view).first { it.text == loaded.getString(names.getValue("hamlet_neighbors_tool_$tool")) }.performClick()
        }
        assertTrue(game.stats["neighbor_helped"] == 1L)
        snapshot("voisin_commande", view)
        buttons(view).first { it.text == loaded.getString(names.getValue("hamlet_neighbors_home")) }.performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(closed && !dialog.isShowing)
        assertTrue(game.realms.found())
        HamletWorldsDialog(context, game).show()
        val worlds = ShadowAlertDialog.getLatestAlertDialog()
        snapshot("royaumes", worlds.window!!.decorView); worlds.dismiss()
        val renderer = HamletRenderer(game, HamletArt()).apply {
            width = 1080; height = 900; zoom = 1.4f; centerOn(32f, 32f)
            selected = game.objs.values.first(); photoMode = true; daylightOverride = .4f
        }
        renderer.update(.016f, Random(2))
        assertTrue(renderer.daylight == .4f)
        val first = Bitmap.createBitmap(1080, 900, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(first)); ecrire("photo_crepuscule", first)
        renderer.selected = null
        val second = Bitmap.createBitmap(1080, 900, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(second))
        assertTrue("photo hides selection", first.sameAs(second))
        first.recycle(); second.recycle(); activity.finish()
    }

    private val herosParRarete = HamletHeroes.defs.sortedWith(compareBy<HeroDef> { it.rarity.ordinal }.thenBy { it.id }).map { it.id }

    private fun grilleHeros(nom: String, ids: List<Int>, cols: Int, cell: Int, fond: Int, temps: Float = .4f) {
        val rows = (ids.size + cols - 1) / cols
        val bmp = Bitmap.createBitmap(cols * cell, rows * (cell + 26), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF2B3A36.toInt())
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 18f }
        val back = Paint().apply { color = fond }
        for ((i, id) in ids.withIndex()) {
            val x = (i % cols) * cell.toFloat(); val y = (i / cols) * (cell + 26).toFloat()
            c.drawRect(x + 3, y + 3, x + cell - 3, y + cell - 3, back)
            HamletCharacterArt.draw(c, true, id, x + cell / 2f, y + cell - cell * .08f, cell * .74f, temps)
            val d = HamletHeroes.defs[id]
            c.drawText("$id ${d.rarity.name.lowercase()} ${d.role.name.lowercase()}", x + 6, y + cell + 19f, text)
        }
        ecrire(nom, bmp)
    }

    /** Les 36 héros par rareté (jour, nuit, gros plans), leurs poses et leurs cadres de portrait. */
    @Test fun heros() {
        grilleHeros("heros", herosParRarete, 6, 230, 0xFF6E8C6A.toInt())
        grilleHeros("heros_nuit", herosParRarete, 6, 230, 0xFF1C2A33.toInt(), temps = 1.7f)
        herosParRarete.chunked(6).forEachIndexed { i, ids -> grilleHeros("heros_gros_plan_$i", ids, 3, 420, 0xFF6E8C6A.toInt()) }
        val ids = listOf(0, 16, 32, 8, 30, 35, 10)
        val poses = HamletCharacterArt.Pose.entries
        val cell = 170
        val sheet = Bitmap.createBitmap(poses.sumOf { it.frames } * cell, ids.size * cell, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(0xFF6E8C6A.toInt())
        for ((row, id) in ids.withIndex()) {
            var col = 0
            for (pose in poses) for (frame in 0 until pose.frames) {
                val t = (frame + .5f) / pose.fps
                HamletCharacterArt.draw(c, true, id, col * cell + cell / 2f, row * cell + cell * .92f, cell * .7f, t, pose, fx = t)
                col++
            }
        }
        ecrire("heros_poses", sheet)
        val cards = listOf(0, 6, 5, 8, 10)
        val w = 330; val h = 240
        val strip = Bitmap.createBitmap(cards.size * (w + 20), h + 20, Bitmap.Config.ARGB_8888)
        val sc = Canvas(strip)
        sc.drawColor(0xFFF0E4CB.toInt())
        for ((i, id) in cards.withIndex()) {
            sc.save(); sc.translate(10f + i * (w + 20f), 10f)
            HeroBackdrop(HamletHeroes.defs[id].rarity, 2.6f).draw(sc, w.toFloat(), h.toFloat(), 1.1f)
            val size = minOf(w, h) * .86f
            HamletCharacterArt.draw(sc, true, id, w / 2f, h - size * .07f, size, 1.1f)
            sc.restore()
        }
        ecrire("heros_cartes", strip)
    }

    /** A battle on a region's ground: fighters on their arena spots, drawn from the back rows forward. */
    private fun combat(region: Int, w: Int, h: Int, t: Float, heroes: IntArray, foes: IntArray, pet: Int = -1): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val scene = HamletScene(region).apply { layout(w, h) }
        scene.drawBack(c, t)
        class Fighter(val hero: Boolean, val id: Int, val x: Float, val y: Float, val size: Float, val enemy: Boolean)
        val fighters = ArrayList<Fighter>()
        val p = PointF(); val base = HamletArena.size(w, h)
        for ((slot, id) in heroes.withIndex()) { val d = HamletArena.place(false, slot, -1, w, h, p); fighters.add(Fighter(true, id, p.x, p.y, base * d, false)) }
        for ((slot, k) in foes.withIndex()) {
            val d = HamletArena.place(true, slot, -1, w, h, p)
            fighters.add(Fighter(false, HamletEnemies.visual(k, region), p.x, p.y, base * d * (if (k >= 6) 1.25f else 1f), true))
        }
        if (pet >= 0) { val d = HamletArena.pet(w, h, p); fighters.add(Fighter(false, HamletEnemies.pet(pet), p.x, p.y, base * .5f * d, false)) }
        for (f in fighters.sortedBy { it.y }) HamletCharacterArt.draw(c, f.hero, f.id, f.x, f.y, f.size, t, mirrored = f.enemy, fx = t)
        scene.drawFront(c, t)
        return bmp
    }

    /** Les six terrains animés : combat complet au téléphone et à la tablette, carte, et trois instants
     * du décor seul pour voir ce qui bouge. */
    @Test fun decors() {
        for (r in 0..5) {
            val boss = r % 2 == 1
            ecrire("decor_telephone_$r", combat(r, 1080, 1150, 1.3f, intArrayOf(32, 8, 30, 13, 3, 20),
                if (boss) intArrayOf(6, 0, 2) else intArrayOf(0, 3, 1, 2, 4, 5), 2))
            ecrire("decor_tablette_$r", combat(r, 1600, 480, 1.3f, intArrayOf(32, 8, 30, 13, 3, 20),
                if (boss) intArrayOf(0, 3, 1, 2, 4, 5) else intArrayOf(6, 0, 2), 1))
            ecrire("decor_carte_$r", combat(r, 1080, 472, 0f, IntArray(0), IntArray(0)))
            val strip = Bitmap.createBitmap(540 * 3, 575, Bitmap.Config.ARGB_8888)
            val c = Canvas(strip)
            for (k in 0..2) c.drawBitmap(Bitmap.createScaledBitmap(combat(r, 1080, 1150, 2f + k * .7f, IntArray(0), IntArray(0)), 540, 575, false), k * 540f, 0f, null)
            ecrire("decor_animation_$r", strip)
        }
    }

    /** Le bestiaire régional : une rangée par région sur son décor (ennemis en miroir, boss agrandi),
     * quelques poses et deux scènes de combat. */
    @Test fun ennemis() {
        val cell = 240; val label = 24
        val sheet = Bitmap.createBitmap(7 * cell, HamletEnemies.REGIONS * (cell + label), Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(0xFF2B3A36.toInt())
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 17f }
        for (r in 0 until HamletEnemies.REGIONS) {
            c.save(); c.translate(0f, r * (cell + label).toFloat())
            HamletScene(r).apply { layout(7 * cell, cell); drawBack(c, 1.1f) }
            c.restore()
            for (k in 0..6) {
                val x = k * cell.toFloat(); val y = r * (cell + label).toFloat()
                c.save(); c.clipRect(x + 2, y + 2, x + cell - 2, y + cell - 2)
                HamletCharacterArt.draw(c, false, HamletEnemies.visual(k, r), x + cell / 2f, y + cell - 22f,
                    cell * (if (k == 6) .62f else .5f), .4f, mirrored = true, fx = 1.1f)
                c.restore()
                c.drawText(if (k == 6) "region $r boss" else "region $r type $k", x + 6, y + cell + 18f, text)
            }
        }
        ecrire("ennemis", sheet)
        val ids = listOf(1, 8, 23, 28, 36, 39, 40)
        val poses = HamletCharacterArt.Pose.entries
        val pc = 170
        val poseSheet = Bitmap.createBitmap(poses.sumOf { it.frames } * pc, ids.size * pc, Bitmap.Config.ARGB_8888)
        val pcv = Canvas(poseSheet)
        pcv.drawColor(0xFF6E8C6A.toInt())
        for ((row, id) in ids.withIndex()) {
            var col = 0
            for (pose in poses) for (frame in 0 until pose.frames) {
                val t = (frame + .5f) / pose.fps
                HamletCharacterArt.draw(pcv, false, id, col * pc + pc / 2f, row * pc + pc * .9f, pc * .6f, t, pose, mirrored = true, fx = t)
                col++
            }
        }
        ecrire("ennemis_poses", poseSheet)
        for ((nom, r) in listOf("combat_tours" to 3, "combat_marais" to 4))
            ecrire(nom, combat(r, 1080, 520, .4f, intArrayOf(32, 8, 30, 13), intArrayOf(6, 0, 2, 4)))
    }

    /** Le combat image par image (tous les dixièmes de seconde) : compétences, impacts, soins,
     * puis un ultime lancé à la main avec son bandeau. Textes en français, police du téléphone. */
    @Test fun effetsDeCombat() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.1f)
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val strings = HashMap<String, String>()
        File("src/main/res/values-fr").listFiles { f -> f.name.startsWith("strings_hamlet") }!!.forEach { file ->
            val nodes = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            for (i in 0 until nodes.length) strings[nodes.item(i).attributes.getNamedItem("name").nodeValue] =
                nodes.item(i).textContent.replace("\\'", "'").replace("\\u0020", " ")
        }
        val label = { key: String, args: Array<out Any> -> String.format(java.util.Locale.FRANCE, strings.getValue("hamlet_a_$key"), *args) }
        for (region in listOf(1, 3)) {
            val game = HamletGame().apply { startNew(true) }
            val a = game.adventure
            a.frontier = 100
            for ((slot, hero) in intArrayOf(32, 8, 30, 13, 3, 20).withIndex()) a.assign(hero, slot)
            a.pets[2] = 2; a.teamPets[a.selectedTeam] = 2
            val battle = a.beginBattle(BattleMode.CAMPAIGN, region * 20 + 3)!!
            val view = HamletBattleView(activity, battle, label, false) {}
            val w = 1233; val h = 1300
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
            fun frame() = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            val cols = 4; val rows = 6; val sw = w / 3; val sh = h / 3
            val sheet = Bitmap.createBitmap(cols * sw, rows * sh, Bitmap.Config.ARGB_8888)
            val sc = Canvas(sheet)
            battle.advance(3f)
            for (k in 0 until cols * rows) {
                battle.advance(.1f)
                sc.drawBitmap(Bitmap.createScaledBitmap(frame(), sw, sh, true), (k % cols) * sw.toFloat(), (k / cols) * sh.toFloat(), null)
            }
            ecrire("combat_effets_$region", sheet)
            battle.units.firstOrNull { !it.enemy && !it.summoned && it.alive }?.let { it.ultimateLeft = 0f; battle.ultimate(it.index) }
            battle.advance(.25f); ecrire("combat_ultime_$region", frame())
        }
    }
}
