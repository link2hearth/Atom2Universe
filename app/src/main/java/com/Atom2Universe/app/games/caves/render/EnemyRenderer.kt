package com.Atom2Universe.app.games.caves.render

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.entity.EnemyState
import com.Atom2Universe.app.games.caves.entity.ExhibitPose
import com.Atom2Universe.app.games.caves.mode.ShieldPickup
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Rend les mobs comme des modèles voxel 3D ([MobModel]) : un assemblage de
 * boîtes colorées, ombrées par face, animées (marche / attaque / flottement),
 * teintées selon le niveau. Surmonté d'une barre de vie et d'un label de niveau
 * en billboard.
 */
internal class EnemyRenderer {

    private var bodyShader:   ShaderProgram? = null
    private var colorShader:  ShaderProgram? = null
    private var spriteShader: ShaderProgram? = null

    private var bodyAPos = 0; private var bodyAColor = 0; private var bodyUMvp = 0
    private var colorAPos = 0; private var colorAColor = 0; private var colorUMvp = 0
    private var spriteAPos = 0; private var spriteAUv = 0
    private var spriteUMvp = 0; private var spriteUTex = 0; private var spriteUFlash = 0

    private var digitTex = 0
    private var bodyVbo = 0
    private var colorVbo = 0
    private var digitVbo = 0

    // Tous les mobs visibles à la suite plutôt qu'un mob à la fois : voir le commentaire
    // sur bodyOffset dans render() — ça permet un seul draw call pour tous les corps.
    private val boV = FloatArray(MAX_VISIBLE * MAX_BOXES * 36 * 6)
    private val coV = FloatArray(MAX_VISIBLE * 12 * 6)
    private val diV = FloatArray(MAX_VISIBLE * MAX_DIGITS * 6 * 5)

    // Buffer natif réutilisé pour chaque upload GPU (corps, barres de vie, labels) —
    // un allocateDirect() par mob par frame était le vrai foyer du GC dès qu'un seul mob
    // traînait à proximité (pas besoin de combat), voir ProjectileRenderer pour le même bug.
    private val nativeBuf: FloatBuffer =
        ByteBuffer.allocateDirect(boV.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    // 8 coins (x,y,z) du cube en cours d'émission
    private val corners = FloatArray(8 * 3)
    private val visible = ArrayList<Enemy>(100)
    private val planes = FloatArray(24)

    // ── Shaders ───────────────────────────────────────────────────────────────

    private val VERT_BODY = """
        #version 300 es
        in vec3 a_pos;
        in vec3 a_color;
        uniform mat4 u_mvp;
        out vec3 v_color;
        out float v_fog;
        void main() {
            gl_Position = u_mvp * vec4(a_pos, 1.0);
            v_color = a_color;
            v_fog = clamp((gl_Position.w - 28.0) / 32.0, 0.0, 1.0);
        }
    """.trimIndent()

    private val FRAG_BODY = """
        #version 300 es
        precision mediump float;
        in vec3 v_color;
        in float v_fog;
        out vec4 fragColor;
        void main() {
            vec3 fog = vec3(0.008, 0.006, 0.015);
            fragColor = vec4(mix(v_color, fog, v_fog), 1.0);
        }
    """.trimIndent()

    private val VERT_COLOR = """
        #version 300 es
        in vec3 a_pos;
        in vec3 a_color;
        uniform mat4 u_mvp;
        out vec3 v_color;
        void main() {
            gl_Position = u_mvp * vec4(a_pos, 1.0);
            v_color = a_color;
        }
    """.trimIndent()

    private val FRAG_COLOR = """
        #version 300 es
        precision mediump float;
        in vec3 v_color;
        out vec4 fragColor;
        void main() { fragColor = vec4(v_color, 1.0); }
    """.trimIndent()

    private val VERT_SPRITE = """
        #version 300 es
        in vec3 a_pos;
        in vec2 a_uv;
        uniform mat4 u_mvp;
        out vec2 v_uv;
        void main() { gl_Position = u_mvp * vec4(a_pos, 1.0); v_uv = a_uv; }
    """.trimIndent()

    private val FRAG_SPRITE = """
        #version 300 es
        precision mediump float;
        uniform sampler2D u_tex;
        uniform float u_flash;
        in vec2 v_uv;
        out vec4 fragColor;
        void main() {
            vec4 col = texture(u_tex, v_uv);
            if (col.a < 0.1) discard;
            fragColor = col;
        }
    """.trimIndent()

    // ── Init GL ───────────────────────────────────────────────────────────────

    @Suppress("UNUSED_PARAMETER")
    fun onSurfaceCreated(assets: AssetManager) {
        bodyShader = ShaderProgram(VERT_BODY, FRAG_BODY).also {
            it.use()
            bodyAPos = it.attrib("a_pos"); bodyAColor = it.attrib("a_color"); bodyUMvp = it.uniform("u_mvp")
        }
        colorShader = ShaderProgram(VERT_COLOR, FRAG_COLOR).also {
            it.use()
            colorAPos = it.attrib("a_pos"); colorAColor = it.attrib("a_color"); colorUMvp = it.uniform("u_mvp")
        }
        spriteShader = ShaderProgram(VERT_SPRITE, FRAG_SPRITE).also {
            it.use()
            spriteAPos = it.attrib("a_pos"); spriteAUv = it.attrib("a_uv")
            spriteUMvp = it.uniform("u_mvp"); spriteUTex = it.uniform("u_tex")
            spriteUFlash = it.uniform("u_flash")
        }
        digitTex = buildDigitAtlas()

        val ids = IntArray(3); GLES30.glGenBuffers(3, ids, 0)
        bodyVbo = ids[0]; colorVbo = ids[1]; digitVbo = ids[2]
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    /** Batteries cyan : un seul lot, buffers existants, aucune nouvelle source de lumière. */
    fun renderShieldPickups(pickups: List<ShieldPickup>, camX: Double, camY: Double, camZ: Double,
                            vpMatrix: FloatArray, time: Float) {
        if (pickups.isEmpty()) return
        val shader = bodyShader ?: return
        var offset = 0
        val c = cos(time * .9f); val s = sin(time * .9f)
        for (pickup in pickups) {
            val x = (pickup.x - camX).toFloat()
            val y = (pickup.y - camY).toFloat() + .42f + .06f * sin(time * 2f)
            val z = (pickup.z - camZ).toFloat()
            if (x * x + y * y + z * z > 80f * 80f) continue
            if (offset + 3 * 36 * 6 > boV.size) break
            fun box(cy: Float, width: Float, height: Float, depth: Float, r: Float, g: Float, b: Float) {
                for (i in 0..7) {
                    val dx = if (i and 1 == 0) -width / 2 else width / 2
                    val dy = if (i and 2 == 0) -height / 2 else height / 2
                    val dz = if (i and 4 == 0) -depth / 2 else depth / 2
                    corners[i * 3] = x + dx * c - dz * s
                    corners[i * 3 + 1] = y + cy + dy
                    corners[i * 3 + 2] = z + dx * s + dz * c
                }
                offset = emitBox(boV, offset, r, g, b, 0f, true)
            }
            box(0f, .36f, .46f, .24f, .05f, .35f, .65f)
            box(0f, .22f, .28f, .27f, .25f, .9f, 1f)
            box(.27f, .16f, .08f, .14f, .8f, .95f, 1f)
        }
        if (offset == 0) return
        shader.use()
        GLES30.glUniformMatrix4fv(bodyUMvp, 1, false, vpMatrix, 0)
        uploadAndBind(bodyVbo, boV, 0, offset)
        bindBodyAttribs()
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, offset / 6)
        disableBodyAttribs()
    }

    fun render(
        enemies: List<Enemy>, camX: Double, camY: Double, camZ: Double,
        cameraYaw: Float, vpMatrix: FloatArray,
    ) {
        // Ne pas rendre les premiers 32 ennemis arbitrairement : une garnison entière
        // doit rester visible. Éliminer hors-champ, puis réutiliser les buffers par lots.
        for (axis in 0..2) for (side in 0..1) {
            val p = (axis * 2 + side) * 4
            val sign = if (side == 0) 1f else -1f
            for (c in 0..3) planes[p + c] = vpMatrix[c * 4 + 3] + sign * vpMatrix[c * 4 + axis]
            val length = sqrt(planes[p] * planes[p] + planes[p + 1] * planes[p + 1] + planes[p + 2] * planes[p + 2])
            if (length > 0f) for (c in 0..3) planes[p + c] /= length
        }
        visible.clear()
        for (e in enemies) {
            if (e.hp <= 0) continue
            val height = MobModels.bodyHeightWorld(e.def.model, e.baseScale)
            val radius = maxOf(2f, height * 1.5f)
            val x = (e.x - camX).toFloat()
            val y = (e.y - camY).toFloat() + height * .5f
            val z = (e.z - camZ).toFloat()
            var outside = false
            for (p in 0 until 24 step 4) {
                if (planes[p] * x + planes[p + 1] * y + planes[p + 2] * z + planes[p + 3] < -radius) {
                    outside = true
                    break
                }
            }
            if (!outside) visible.add(e)
        }
        var start = 0
        while (start < visible.size) {
            val end = minOf(start + MAX_VISIBLE, visible.size)
            renderBatch(visible.subList(start, end), camX, camY, camZ, cameraYaw, vpMatrix)
            start = end
        }
    }

    private fun renderBatch(
        enemies: List<Enemy>,
        camX: Double, camY: Double, camZ: Double,
        cameraYaw: Float,
        vpMatrix: FloatArray
    ) {
        if (enemies.none { it.hp > 0 }) return

        // Vecteur "droite caméra" pour les billboards (barre de vie + label).
        val yawRad = Math.toRadians(cameraYaw.toDouble())
        val rightX = (-cos(yawRad)).toFloat()
        val rightZ = sin(yawRad).toFloat()

        // ── 1. Corps voxel : tous les mobs dans un seul draw call ───────────────
        // Même shader, aucun uniforme par mob (couleur déjà ombrée/teintée dans les
        // sommets) — rien n'empêchait de les regrouper. Jusqu'à 32 draw calls/frame
        // rien que pour les corps, juste pour l'exploration ambiante sans combat.
        bodyShader?.use()
        GLES30.glUniformMatrix4fv(bodyUMvp, 1, false, vpMatrix, 0)

        var drawn = 0
        var bodyOffset = 0
        for (e in enemies) {
            if (e.hp <= 0) continue
            if (drawn >= MAX_VISIBLE) break
            drawn++
            val weaponVertices = heldWeaponVertices(e)
            val model = if (e.def.behavior == "passive") AnimalModels.get(e.def.model, e.young, e.coat)
                else MobModels.get(e.def.model)
            // Include the detailed weapon and the slime mesh, not just the body boxes.
            val required = model.parts.size * 216 + (weaponVertices?.size ?: 0) +
                if (model.squash) SlimeGeometry.vertices.size / 4 * 6 else 0
            check(required <= boV.size) { "Enemy geometry exceeds render buffer: ${e.def.model}" }
            if (bodyOffset + required > boV.size) {
                uploadAndBind(bodyVbo, boV, 0, bodyOffset)
                bindBodyAttribs()
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, bodyOffset / 6)
                disableBodyAttribs()
                bodyOffset = 0
            }
            bodyOffset = buildBody(e, camX, camY, camZ, bodyOffset, weaponVertices)
        }
        if (bodyOffset > 0) {
            uploadAndBind(bodyVbo, boV, 0, bodyOffset)
            bindBodyAttribs()
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, bodyOffset / 6)
            disableBodyAttribs()
        }

        // ── 2. Barres de vie + labels (accumulés puis dessinés batchés) ─────────
        var ci = 0; var di = 0; var count = 0
        for (e in enemies) {
            if (e.hp <= 0) continue
            if (count >= MAX_VISIBLE) break
            count++
            if (e.exhibitPose != null || e.def.behavior == "passive") continue

            val ex = (e.x - camX).toFloat()
            val ey = (e.y - camY).toFloat()
            val ez = (e.z - camZ).toFloat()
            // Sommet réel du modèle voxel (les gros mobs dépassent baseScale×2).
            val modelTop = MobModels.bodyHeightWorld(e.def.model, e.baseScale)
            val barW = (e.baseScale * 2f) * 0.55f

            val barY0 = ey + modelTop + 0.14f
            val barY1 = barY0 + 0.14f
            val hpFrac = e.hp.toFloat() / e.maxHp.coerceAtLeast(1)
            val fgX1 = -barW * 0.5f + barW * hpFrac
            val (bgR, bgG, bgB) = levelBgColor(e.level)

            fun cv(rx: Float, ry: Float, r: Float, g: Float, b: Float) {
                coV[ci++] = ex + rightX * rx; coV[ci++] = ry; coV[ci++] = ez + rightZ * rx
                coV[ci++] = r; coV[ci++] = g; coV[ci++] = b
            }
            val bx0 = -barW * 0.5f; val bx1 = barW * 0.5f
            cv(bx0, barY1, bgR, bgG, bgB); cv(bx0, barY0, bgR, bgG, bgB); cv(bx1, barY0, bgR, bgG, bgB)
            cv(bx0, barY1, bgR, bgG, bgB); cv(bx1, barY0, bgR, bgG, bgB); cv(bx1, barY1, bgR, bgG, bgB)
            val gr = 1f - hpFrac; val gg = hpFrac * 0.85f
            cv(bx0, barY1, gr, gg, 0f); cv(bx0, barY0, gr, gg, 0f); cv(fgX1, barY0, gr, gg, 0f)
            cv(bx0, barY1, gr, gg, 0f); cv(fgX1, barY0, gr, gg, 0f); cv(fgX1, barY1, gr, gg, 0f)

            val levelStr = if (e.isBoss) "BOSS" else e.level.toString()
            val digitW = if (e.isBoss) 0.20f else 0.16f
            val digitH = if (e.isBoss) 0.28f else 0.22f
            val gap = 0.02f
            val totalW = levelStr.length * (digitW + gap) - gap
            val digitY0 = barY1 + 0.06f
            val digitY1 = digitY0 + digitH

            fun dv(rx: Float, ry: Float, u: Float, v: Float) {
                diV[di++] = ex + rightX * rx; diV[di++] = ry; diV[di++] = ez + rightZ * rx
                diV[di++] = u; diV[di++] = v
            }
            var curX = -totalW * 0.5f
            for (ch in levelStr) {
                val d = if (ch.isDigit()) ch - '0' else 10 + (ch - 'A').coerceIn(0, 5)
                val du0 = d * DIGIT_W.toFloat() / DIGIT_ATLAS_W
                val du1 = (d + 1) * DIGIT_W.toFloat() / DIGIT_ATLAS_W
                dv(curX,          digitY1, du0, 0f)
                dv(curX,          digitY0, du0, DIGIT_H.toFloat() / DIGIT_ATLAS_H)
                dv(curX + digitW, digitY0, du1, DIGIT_H.toFloat() / DIGIT_ATLAS_H)
                dv(curX,          digitY1, du0, 0f)
                dv(curX + digitW, digitY0, du1, DIGIT_H.toFloat() / DIGIT_ATLAS_H)
                dv(curX + digitW, digitY1, du1, 0f)
                curX += digitW + gap
            }
        }

        if (ci > 0) {
            GLES30.glDepthFunc(GLES30.GL_LEQUAL)
            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL); GLES30.glPolygonOffset(-1f, -1f)
            uploadAndBind(colorVbo, coV, 0, ci)
            colorShader?.use()
            GLES30.glUniformMatrix4fv(colorUMvp, 1, false, vpMatrix, 0)
            bindColorAttribs(); GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, ci / 6)
            disableColorAttribs()
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
            GLES30.glDepthFunc(GLES30.GL_LESS)
        }

        if (di > 0) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL); GLES30.glPolygonOffset(-2f, -2f)
            uploadAndBind(digitVbo, diV, 0, di)
            spriteShader?.use()
            GLES30.glUniformMatrix4fv(spriteUMvp, 1, false, vpMatrix, 0)
            GLES30.glUniform1f(spriteUFlash, 0f)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, digitTex)
            GLES30.glUniform1i(spriteUTex, 0)
            bindSpriteAttribs(); GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, di / 5)
            disableSpriteAttribs()
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
            GLES30.glDisable(GLES30.GL_BLEND)
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    // ── Construction de la géométrie voxel d'un mob ─────────────────────────────

    /** Ajoute le modèle de [e] dans [boV] à partir de [offset]. Retourne le nouvel offset. */
    private val heldMesh by lazy { HeldEquipmentMesh() }
    private val heldFrames = HashMap<String, FloatArray>()

    private fun heldWeaponVertices(e: Enemy): FloatArray? {
        val weaponType = e.heldWeaponType ?: return null
        if (e.def.model == "soldier" && e.exhibitPose != ExhibitPose.REFERENCE) {
            val reloadFrame = (e.weaponReload * 8).toInt().coerceIn(0, 8)
            val shotFrame = if (reloadFrame == 0 && e.shotRecoil > 0f)
                ((.16f - e.shotRecoil) / .04f).toInt().coerceIn(0, 3) else -1
            val key = "$weaponType:$reloadFrame:$shotFrame"
            return heldFrames.getOrPut(key) {
                heldMesh.clear()
                heldMesh.weapon(weaponType, 0f, if (shotFrame < 0) -1f else shotFrame * .04f,
                    true, 0xD5BB75, reload = reloadFrame / 8f)
                heldMesh.vertices.copyOf(heldMesh.count)
            }
        }
        return null
    }

    private fun buildBody(e: Enemy, camX: Double, camY: Double, camZ: Double, offset: Int, weaponVertices: FloatArray?): Int {
        val model = if (e.def.behavior == "passive") AnimalModels.get(e.def.model, e.young, e.coat) else MobModels.get(e.def.model)
        val h = e.baseScale * 2f
        val s = h / MobModels.REF_VOX             // unités monde par voxel

        val yawRad = Math.toRadians(e.yaw.toDouble())
        val cosY = cos(yawRad).toFloat(); val sinY = sin(yawRad).toFloat()

        val ex = (e.x - camX).toFloat()
        val ez = (e.z - camZ).toFloat()
        var ey = (e.y - camY).toFloat()

        val reference = e.exhibitPose == ExhibitPose.REFERENCE
        val passive = e.def.behavior == "passive"
        val soldier = e.def.model == "soldier"
        val moving = !e.resting && (e.state == EnemyState.CHASE || e.state == EnemyState.WANDER)
        val gaitPhase = if (passive || soldier) e.animTime*model.gait else e.walkPhase
        val locomotion = if (passive || soldier) { if(moving) 1f else 0f } else e.motionBlend
        val walk = if (reference) 0f else sin(gaitPhase)*locomotion
        val strike = if (reference || passive || soldier) 0f else (e.strikeTime/.55f).coerceIn(0f,1f)
        val breath = if (reference || passive) 0f else sin(e.animTime*1.8f+e.id*.73f)*model.breath
        val flinch = if (reference || passive) 0f else e.hitFlash.coerceIn(0f,.25f)*2f
        // Descente du cou pendant le broutage, commune à toute la tête (yeux,
        // museau, oreilles et cornes), en unités voxel avant l'échelle monde.
        val grazingDrop = if (!reference && e.resting && e.def.behavior == "passive")
            model.feedingDrop * (1f + .09f * sin(e.animTime * if (e.def.model == "chicken") 7f else 2f)) else 0f

        var sxz = 1f; var syY = 1f
        if (model.squash && !reference) {
            val q = sin(if(locomotion>.1f) gaitPhase else e.animTime*2f)
            syY = 1f + 0.14f * q - .18f*strike; sxz = 1f / sqrt(syY)
        }
        if (model.floats && !reference) ey += 0.15f * sin(e.animTime * FLOAT_FREQ).toFloat()

        val tint = levelTint(if (e.def.behavior == "passive") 2 else e.level, e.isBoss)
        // La progression reste lisible sur le label ; elle ne masque plus les matériaux pastel.
        if (!passive) for(i in tint.indices) tint[i]=1f+(tint[i]-1f)*.3f
        val flash = e.hitFlash.coerceIn(0f, 1f) * 0.7f

        var n = offset
        for (part in model.parts) {
            if (soldier && e.heldWeaponType != null &&
                (part.limb == Limb.WEAPON || part.limb == Limb.MUZZLE_FLASH)) continue
            if (model.squash && part === model.parts.first()) {
                val mesh=SlimeGeometry.vertices
                if (n+mesh.size/4*6>boV.size) break
                for(i in mesh.indices step 4) {
                    val px=mesh[i]*sxz*s
                    val py=mesh[i+1]*syY*s
                    val pz=mesh[i+2]*sxz*s
                    boV[n++]=ex+px*cosY+pz*sinY
                    boV[n++]=ey+py
                    boV[n++]=ez-px*sinY+pz*cosY
                    for(channel in 0..2) {
                        val color=((part.color ushr (16-channel*8)) and 255)/255f
                        boV[n++]=(color*tint[channel]*mesh[i+3]*(1f-flash)+
                            (if(channel==0) 1f else .15f)*flash).coerceIn(0f,1f)
                    }
                }
                continue
            }
            if (part.limb == Limb.MUZZLE_FLASH && (reference || e.shotRecoil < .11f)) continue
            if (part.limb == Limb.WEAPON && reference) continue
            if (n + 216 > boV.size) break   // 6 faces × 6 sommets × 6 floats ; boV partagé entre mobs
            // Angle de balancement / pose
            val baseRad = Math.toRadians(part.baseTiltDeg.toDouble()).toFloat()
            val ang = if (reference && part.limb != Limb.NONE) 0f else when (part.limb) {
                Limb.LEG -> baseRad + part.side * walk * model.stride
                Limb.ARM ->
                    if (e.def.model == "soldier") baseRad
                    else baseRad - strike*(if(e.def.model == "ogre" || e.def.model == "golem") 1.15f else if(part.side>0) 1f else .65f) - part.side*walk*model.stride*.65f
                Limb.HEAD -> if (e.resting) .65f + .12f * sin(e.animTime * 2f) else .04f * sin(e.animTime * 2f)
                Limb.TAIL -> .18f * sin(e.animTime * 2.5f)
                Limb.WING -> if (reference) 0f else if (moving) .22f * sin(e.animTime * 12f) else .04f * sin(e.animTime * 2f)
                Limb.LOOK -> if(reference) 0f else .04f*sin(e.animTime*1.8f+e.id*.73f)-strike*.10f
                Limb.CLOTH -> baseRad + if(reference) 0f else .10f*sin(e.animTime*2.3f+part.side)+walk*.12f
                Limb.CRAWL -> if(reference) 0f else part.side*walk*.22f
                Limb.NONE, Limb.WEAPON, Limb.MUZZLE_FLASH -> baseRad
            }
            val cosA = cos(ang); val sinA = sin(ang)

            val hw = part.w * 0.5f; val hh = part.h * 0.5f; val hd = part.d * 0.5f
            var k = 0
            for (zi in 0..1) for (yi in 0..1) for (xi in 0..1) {
                var lx = part.cx + if (xi == 1) hw else -hw
                var ly = part.cy + if (yi == 1) hh else -hh
                var lz = part.cz + if (zi == 1) hd else -hd
                // Écrasement gélatineux (autour des pieds / de l'axe central)
                lx *= sxz; lz *= sxz; ly *= syY
                // Rotation du membre autour de son pivot (axe X)
                val pivotZ = if (part.limb == Limb.HEAD) model.headPivotZ else part.pivotZ
                val dy = ly - part.pivotY; val dz = lz - pivotZ
                var ry = part.pivotY + dy * cosA - dz * sinA
                var rz = pivotZ + dy * sinA + dz * cosA
                if (part.limb == Limb.HEAD) ry -= grazingDrop
                if (part.limb == Limb.LOOK && !reference) {
                    val turn=if(soldier && !e.resting) 0f else .09f*sin(e.animTime*.8f+e.id*.73f)
                    val hx=lx-part.pivotX; val hz=rz-part.pivotZ
                    lx=part.pivotX+hx*cos(turn)+hz*sin(turn)
                    rz=part.pivotZ-hx*sin(turn)+hz*cos(turn)
                }
                if (part.limb == Limb.CRAWL && !reference) {
                    ry += kotlin.math.max(0f,part.side*sin(gaitPhase))*.7f*locomotion
                }
                if (!passive && !reference && part.limb != Limb.LEG && part.limb != Limb.CRAWL) {
                    ry += breath
                    rz += (strike*.6f-flinch*.45f)*(ly/model.heightVox).coerceIn(0f,1f)
                }
                if (part.limb == Limb.WING) {
                    // Aile articulée sur le flanc, autour de Z, vers l'extérieur.
                    val pivotX=if(passive) part.cx-part.side*part.w*.5f else part.pivotX
                    val wingX=lx-pivotX
                    lx=pivotX+wingX*cosA-dy*sinA*part.side
                    ry=part.pivotY+wingX*sinA*part.side+dy*cosA
                    rz=lz
                }
                if (reference && part.limb == Limb.ARM) {
                    val armX = lx - part.cx
                    val armY = ly - part.pivotY
                    lx = part.cx - part.side * armY
                    ry = part.pivotY + part.side * armX
                }
                if (!reference && e.def.model == "soldier" &&
                    (part.limb == Limb.ARM || part.limb == Limb.WEAPON || part.limb == Limb.MUZZLE_FLASH)) {
                    val recoil = (e.shotRecoil / .16f).coerceIn(0f, 1f)
                    rz -= recoil * 1.8f
                    ry += recoil * .45f
                }
                // Échelle voxel→monde
                val px = lx * s; val py = ry * s; val pz = rz * s
                // Orientation (yaw) puis translation au pied du mob
                corners[k++] = ex + (px * cosY + pz * sinY)
                corners[k++] = ey + py
                corners[k++] = ez + (-px * sinY + pz * cosY)
            }

            // Couleur de base (teinte de niveau sauf pièces auto-éclairées)
            val cr = ((part.color ushr 16) and 0xFF) / 255f
            val cg = ((part.color ushr 8) and 0xFF) / 255f
            val cb = (part.color and 0xFF) / 255f
            val pr: Float; val pg: Float; val pb: Float
            if (part.emissive) { pr = cr; pg = cg; pb = cb }
            else { pr = cr * tint[0]; pg = cg * tint[1]; pb = cb * tint[2] }

            n = emitBox(boV, n, pr, pg, pb, flash, part.emissive)
        }
        if (weaponVertices != null) {
            val verts = weaponVertices
            if (n + verts.size <= boV.size) {
                val recoil = e.shotRecoil.coerceIn(0f, .16f) / .16f
                for (i in verts.indices step 6) {
                    // Le mesh joueur pointe vers -Z ; rotation de 180° vers l'avant du soldat.
                    val px = .09f - verts[i]
                    val py = 1.16f + verts[i + 1] + recoil * .027f
                    val pz = .36f - verts[i + 2] - recoil * .108f
                    boV[n++] = ex + px * cosY + pz * sinY
                    boV[n++] = ey + py
                    boV[n++] = ez - px * sinY + pz * cosY
                    boV[n++] = verts[i + 3]; boV[n++] = verts[i + 4]; boV[n++] = verts[i + 5]
                }
            }
        }
        return n
    }

    /** Émet les 6 faces (36 sommets) du cube courant [corners] dans [out]. */
    private fun emitBox(
        out: FloatArray, offset: Int,
        pr: Float, pg: Float, pb: Float, flash: Float,
        emissive: Boolean
    ): Int {
        var o = offset
        for (f in 0 until 6) {
            val shade = if (emissive) 1f else FACE_SHADE[f]
            // Couleur ombrée + flash de dégât
            val fr = pr * shade; val fg = pg * shade; val fb = pb * shade
            val r = (fr * (1f - flash) + 1.0f * flash).coerceIn(0f, 1f)
            val g = (fg * (1f - flash) + 0.15f * flash).coerceIn(0f, 1f)
            val b = (fb * (1f - flash) + 0.15f * flash).coerceIn(0f, 1f)

            val i0 = FACE_IDX[f * 4]; val i1 = FACE_IDX[f * 4 + 1]
            val i2 = FACE_IDX[f * 4 + 2]; val i3 = FACE_IDX[f * 4 + 3]
            o = vtx(out, o, i0, r, g, b); o = vtx(out, o, i1, r, g, b); o = vtx(out, o, i2, r, g, b)
            o = vtx(out, o, i0, r, g, b); o = vtx(out, o, i2, r, g, b); o = vtx(out, o, i3, r, g, b)
        }
        return o
    }

    private fun vtx(out: FloatArray, o: Int, ci: Int, r: Float, g: Float, b: Float): Int {
        out[o]     = corners[ci * 3]
        out[o + 1] = corners[ci * 3 + 1]
        out[o + 2] = corners[ci * 3 + 2]
        out[o + 3] = r; out[o + 4] = g; out[o + 5] = b
        return o + 6
    }

    // ── Teinte selon niveau ─────────────────────────────────────────────────────

    private fun levelTint(level: Int, boss: Boolean): FloatArray {
        val t = when {
            level <= 1  -> floatArrayOf(0.85f, 0.88f, 0.85f)
            level <= 3  -> floatArrayOf(1.0f, 1.0f, 1.0f)
            level <= 5  -> floatArrayOf(0.82f, 1.05f, 0.82f)
            level <= 8  -> floatArrayOf(1.10f, 1.0f, 0.65f)
            level <= 11 -> floatArrayOf(1.15f, 0.78f, 0.55f)
            level <= 14 -> floatArrayOf(1.20f, 0.60f, 0.55f)
            else        -> floatArrayOf(1.0f, 0.55f, 1.15f)
        }
        if (boss) { t[0] *= 0.95f; t[1] *= 0.68f; t[2] *= 0.68f }
        return t
    }

    private fun levelBgColor(level: Int): Triple<Float, Float, Float> = when {
        level <= 1  -> Triple(0.20f, 0.20f, 0.20f)
        level <= 3  -> Triple(0.05f, 0.10f, 0.40f)
        level <= 5  -> Triple(0.05f, 0.30f, 0.05f)
        level <= 8  -> Triple(0.35f, 0.30f, 0.00f)
        level <= 11 -> Triple(0.40f, 0.18f, 0.00f)
        level <= 14 -> Triple(0.40f, 0.02f, 0.02f)
        else        -> Triple(0.30f, 0.00f, 0.35f)
    }

    // ── Helpers GL ──────────────────────────────────────────────────────────────

    private fun uploadAndBind(vbo: Int, data: FloatArray, offset: Int, floatCount: Int) {
        nativeBuf.clear(); nativeBuf.put(data, offset, floatCount); nativeBuf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, floatCount * 4, nativeBuf, GLES30.GL_DYNAMIC_DRAW)
    }

    private fun bindBodyAttribs() {
        val stride = 6 * 4
        GLES30.glEnableVertexAttribArray(bodyAPos)
        GLES30.glVertexAttribPointer(bodyAPos, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(bodyAColor)
        GLES30.glVertexAttribPointer(bodyAColor, 3, GLES30.GL_FLOAT, false, stride, 12)
    }
    private fun disableBodyAttribs() {
        GLES30.glDisableVertexAttribArray(bodyAPos); GLES30.glDisableVertexAttribArray(bodyAColor)
    }
    private fun bindColorAttribs() {
        val stride = 6 * 4
        GLES30.glEnableVertexAttribArray(colorAPos)
        GLES30.glVertexAttribPointer(colorAPos, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(colorAColor)
        GLES30.glVertexAttribPointer(colorAColor, 3, GLES30.GL_FLOAT, false, stride, 12)
    }
    private fun disableColorAttribs() {
        GLES30.glDisableVertexAttribArray(colorAPos); GLES30.glDisableVertexAttribArray(colorAColor)
    }
    private fun bindSpriteAttribs() {
        val stride = 5 * 4
        GLES30.glEnableVertexAttribArray(spriteAPos)
        GLES30.glVertexAttribPointer(spriteAPos, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(spriteAUv)
        GLES30.glVertexAttribPointer(spriteAUv, 2, GLES30.GL_FLOAT, false, stride, 12)
    }
    private fun disableSpriteAttribs() {
        GLES30.glDisableVertexAttribArray(spriteAPos); GLES30.glDisableVertexAttribArray(spriteAUv)
    }

    fun destroy() {
        bodyShader?.destroy(); colorShader?.destroy(); spriteShader?.destroy()
        if (digitTex != 0) GLES30.glDeleteTextures(1, intArrayOf(digitTex), 0)
        GLES30.glDeleteBuffers(3, intArrayOf(bodyVbo, colorVbo, digitVbo), 0)
    }

    // ── Atlas chiffres 0–9 + A–F (pour "BOSS") ─────────────────────────────────

    private fun buildDigitAtlas(): Int {
        val chars = "0123456789ABCDEF"
        val atlasW = chars.length * DIGIT_W
        val bmp = Bitmap.createBitmap(atlasW, DIGIT_ATLAS_H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = DIGIT_H * 0.78f
            textAlign = Paint.Align.CENTER
            color = 0xFFFFFFFF.toInt()
        }
        for ((i, c) in chars.withIndex()) {
            cv.drawText(c.toString(), (i * DIGIT_W + DIGIT_W * 0.5f), DIGIT_H * 0.82f, p)
        }
        return uploadTex2D(bmp)
    }

    private fun uploadTex2D(bmp: Bitmap): Int {
        val ids = IntArray(1); GLES30.glGenTextures(1, ids, 0)
        val tex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        val buf = ByteBuffer.allocateDirect(bmp.width * bmp.height * 4).order(ByteOrder.nativeOrder())
        bmp.copyPixelsToBuffer(buf); buf.position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
            bmp.width, bmp.height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        bmp.recycle()
        return tex
    }

    companion object {
        private const val FLOAT_FREQ  = 2.2f

        private const val MAX_VISIBLE = 32
        private const val MAX_BOXES   = 48
        private const val MAX_DIGITS  = 8

        private const val DIGIT_ATLAS_W = 256
        private const val DIGIT_ATLAS_H = 20
        private const val DIGIT_W       = 16
        private const val DIGIT_H       = 20

        // Ombrage par face : haut clair → bas sombre. Index = ordre de FACE_IDX.
        private val FACE_SHADE = floatArrayOf(1.00f, 0.45f, 0.85f, 0.70f, 0.62f, 0.60f)

        // 6 faces × 4 coins. Encodage coin : bit0=x, bit1=y, bit2=z (0=min,1=max).
        private val FACE_IDX = intArrayOf(
            2, 3, 7, 6,   // +Y haut
            0, 4, 5, 1,   // -Y bas
            4, 6, 7, 5,   // +Z avant
            0, 1, 3, 2,   // -Z arrière
            1, 5, 7, 3,   // +X droite
            0, 2, 6, 4    // -X gauche
        )
    }
}
