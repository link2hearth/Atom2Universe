package com.Atom2Universe.app.games.infernale.art

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

/** Contrôle et export JVM du raster de production, sans Android ni construction d'APK. */
fun main(args: Array<String>) {
    val root = File(args[0]); val out = File(args[1]).apply { mkdirs() }
    val catalog = File(root, "app/src/main/java/com/Atom2Universe/app/games/physics/catalog/MachinePartCatalog.kt").readText()
    val parts = Regex("definition\\(\"([^\"]+)\", \"([^\"]+)\"").findAll(catalog)
        .map { it.groupValues[1] to it.groupValues[2] }.toList()
    check(parts.map { it.first }.distinct().size == parts.size)
    val renderer = InfernalePixels()
    fun digest(pixels: IntArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (p in pixels) { md.update((p ushr 24).toByte()); md.update((p ushr 16).toByte()); md.update((p ushr 8).toByte()); md.update(p.toByte()) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    val atlas = BufferedImage(64 * 16, 64 * parts.size, BufferedImage.TYPE_INT_ARGB)
    val static = mutableListOf<BufferedImage>()
    val frameHashes = mutableMapOf<String, MutableSet<String>>()
    for (frame in 0..15) {
        val hashes = mutableMapOf<String, String>()
        val grayHashes = mutableMapOf<String, String>()
        val angle = (frame * 2 * PI / 16).toFloat()
        val wave = (sin(angle) + 1) / 2
        val travel = (1 - cos(angle)) / 2
        for ((i, part) in parts.withIndex()) {
            val pixels = renderer.render(part.first, angle, travel, wave, frame >= 8, frame / 16f)
            check(pixels.any { it != 0 }) { "Empty sprite: ${part.first}" }
            val hash = digest(pixels)
            check(hashes.put(hash, part.first) == null) { "Identical sprites, frame $frame: ${part.first}" }
            val gray = pixels.map { p ->
                if (p == 0) 0 else {
                    val value = (((p ushr 16) and 255) * 299 + ((p ushr 8) and 255) * 587 + (p and 255) * 114) / 1000
                    (255 shl 24) or (value shl 16) or (value shl 8) or value
                }
            }.toIntArray()
            check(grayHashes.put(digest(gray), part.first) == null) { "Identical grayscale sprites, frame $frame: ${part.first}" }
            frameHashes.getOrPut(part.first) { mutableSetOf() }.add(hash)
            atlas.setRGB(frame * 64, i * 64, 64, 64, pixels, 0, 64)
            if (frame == 0) static.add(BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB).apply { setRGB(0,0,64,64,pixels,0,64) })
            val copy = pixels.copyOf()
            check(copy.contentEquals(renderer.render(part.first, angle, travel, wave, frame >= 8, frame / 16f))) { "Non deterministic sprite: ${part.first}" }
        }
    }
    // Les entrées non finies doivent rester dessinables, sans valeurs hors limites.
    for ((id, _) in parts) check(renderer.render(id, Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, false, Float.NEGATIVE_INFINITY).any { it != 0 })
    ImageIO.write(atlas,"png",File(out,"animation.png"))
    val moving = listOf("rotary.crank", "rotary.cam", "energy.human_crank", "linear.spring",
        "linear.scissor_lift", "hydraulic.cylinder", "hydraulic.ram", "control.governor",
        "sensor.limit_switch", "transmission.clutch", "safety.rupture_disk", "safety.fuse")
    val poses = BufferedImage(850, 50 + moving.size * 134, BufferedImage.TYPE_INT_RGB)
    val pg = poses.createGraphics()
    pg.color = Color(LIGHT); pg.fillRect(0,0,poses.width,poses.height)
    pg.font = Font("SansSerif",Font.BOLD,20); pg.color=Color(INK); pg.drawString("INFERNALE / quatre poses par mécanisme",18,30)
    pg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
    for ((row,id) in moving.withIndex()) {
        val index = parts.indexOfFirst { it.first == id }
        pg.font=Font("SansSerif",Font.PLAIN,14); pg.color=Color(INK); pg.drawString(parts[index].second,15,50+row*134+65)
        for ((col,frame) in listOf(0,4,8,12).withIndex()) pg.drawImage(atlas,300+col*134,50+row*134,428+col*134,178+row*134,frame*64,index*64,frame*64+64,index*64+64,null)
    }
    pg.dispose(); ImageIO.write(poses,"png",File(out,"mouvements.png"))
    val groups = linkedMapOf(
        "mecanique" to setOf("structure","rotary","transmission","linear"),
        "fluides" to setOf("pneumatic","hydraulic"),
        "dispositifs" to setOf("energy","projectile","control","sensor","safety")
    )
    for ((name, prefixes) in groups) {
        val indices = parts.indices.filter { parts[it].first.substringBefore('.') in prefixes }
        val sheet = BufferedImage(1152, 72 + ((indices.size + 5) / 6) * 178, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.color = Color(LIGHT); g.fillRect(0,0,sheet.width,sheet.height)
        g.color = Color(INK); g.font = Font("SansSerif",Font.BOLD,26); g.drawString("INFERNALE / $name",24,43)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        for ((position, index) in indices.withIndex()) {
            val x = (position % 6) * 192; val y = 72 + position / 6 * 178
            g.drawImage(static[index], x+32, y, 128,128,null)
            g.color=Color(INK); g.font=Font("SansSerif",Font.PLAIN,13)
            val label=parts[index].second
            g.drawString(label,x+(192-g.fontMetrics.stringWidth(label))/2,y+146)
            g.color=Color(SHADE); g.font=Font("Monospaced",Font.PLAIN,10)
            val id=parts[index].first
            g.drawString(id,x+(192-g.fontMetrics.stringWidth(id))/2,y+163)
        }
        g.dispose(); ImageIO.write(sheet,"png",File(out,"$name.png"))
    }
    fun escape(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;")
    val cards = parts.mapIndexed { i, (id,name) -> "<article data-id=\"${escape(id)}\"><div class=\"sprite\" style=\"--row:$i\" role=\"img\" aria-label=\"${escape(name)}\"></div><strong>${escape(name)}</strong><small>${escape(id)}</small></article>" }.joinToString("\n")
    File(out,"index.html").writeText("""
        <!doctype html><html lang="fr"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Infernale — les ${parts.size} pièces</title>
        <style>
        *{box-sizing:border-box}body{margin:0;background:#fff6e7;color:#59637d;font:16px system-ui,sans-serif}header{padding:24px;position:sticky;top:0;background:#fff6e7f5;z-index:1;border-bottom:2px solid #bfdcec}h1{margin:0 0 8px}p{margin:8px 0 16px;max-width:850px}button,input{font:inherit;padding:10px 16px;border:2px solid #91a4bd;border-radius:8px;color:#59637d;background:#fff}button{cursor:pointer;background:#acddc5}main{display:grid;grid-template-columns:repeat(auto-fill,minmax(185px,1fr));gap:12px;padding:24px}article{display:flex;align-items:center;flex-direction:column;padding:12px 6px;border:1px solid #e4dcca;border-radius:10px;background:#fffaf1;min-height:198px;text-align:center}article[hidden]{display:none}small{margin-top:6px;font-size:11px;color:#647992}.sprite{width:128px;height:128px;image-rendering:pixelated;background-image:url(animation.png);background-size:2048px ${parts.size*128}px;background-position:calc(var(--frame,0)*-128px) calc(var(--row)*-128px)}
        </style><header><h1>Infernale — ${parts.size} pièces distinctes</h1><p>Pixels du renderer Kotlin utilisé par Android. Les animations illustrent les états possibles ; cette planche ne simule pas la physique.</p><button id="toggle" aria-pressed="true">Mettre en pause</button> <label>Filtrer <input id="filter" type="search" placeholder="Ressort, moteur, hydraulic…"></label></header><main>$cards</main>
        <script>
        let running=!matchMedia('(prefers-reduced-motion: reduce)').matches,frame=0,last=0;const btn=document.querySelector('#toggle');function label(){btn.textContent=running?'Mettre en pause':'Animer';btn.setAttribute('aria-pressed',String(running))}label();btn.onclick=()=>{running=!running;label()};document.querySelector('#filter').oninput=e=>{const q=e.target.value.toLocaleLowerCase();document.querySelectorAll('article').forEach(card=>card.hidden=!card.textContent.toLocaleLowerCase().includes(q))};function tick(t){if(running&&!document.hidden&&t-last>125){frame=(frame+1)%16;document.documentElement.style.setProperty('--frame',frame);last=t}requestAnimationFrame(tick)}requestAnimationFrame(tick);
        </script></html>
    """.trimIndent())
    val animated = frameHashes.filterValues { it.size > 1 }.keys
    val report = "${parts.size} sprites non vides et distincts à chacune des 16 étapes, en couleur et en niveaux de gris.\nRendu déterministe et entrées non finies vérifiés.\n${animated.size} sprites réagissent aux états d'animation.\n" + animated.joinToString("\n")
    File(out,"verification.txt").writeText(report)
    println(report.substringBefore("\nstructure" ).take(600))
    println("Planches exportées : ${out.absolutePath}")
}
