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
    private val labelBg = FloatArray(3)

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
        for (index in enemies.indices) {
            val e = enemies[index]
            // Masqué par un mur plein (voir AssaultMode.updateOcclusion) : ni construit ni dessiné.
            // Le cône de vue seul laissait passer toute une garnison à travers six étages de dalles.
            if (e.hp <= 0 || e.occluded) continue
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
            renderBatch(start, end, camX, camY, camZ, cameraYaw, vpMatrix)
            start = end
        }
    }

    /** Dessine [visible] de [from] inclus à [to] exclu (déjà filtrés : vivants et visibles). */
    private fun renderBatch(
        from: Int, to: Int,
        camX: Double, camY: Double, camZ: Double,
        cameraYaw: Float,
        vpMatrix: FloatArray
    ) {
        if (from >= to) return

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
        for (index in from until to) {
            val e = visible[index]
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
        for (index in from until to) {
            val e = visible[index]
            if (e.hp <= 0) continue
            if (count >= MAX_VISIBLE) break
            count++
            if (e.exhibitPose != null || e.def.behavior in setOf("passive", "settler", "machine")) continue

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
            levelBgColor(e.level, labelBg)
            val bgR = labelBg[0]; val bgG = labelBg[1]; val bgB = labelBg[2]

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

            val levelStr = if (e.isBoss) "BOSS" else if (e.level in LEVEL_LABELS.indices)
                LEVEL_LABELS[e.level] else e.level.toString()
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

    private val heldMesh by lazy { HeldEquipmentMesh() }
    /** Images d'arme par type : 9 étapes de rechargement × (repos + 4 étapes de recul). */
    private val heldFrames = HashMap<String, Array<FloatArray?>>()

    private fun heldWeaponVertices(e: Enemy): FloatArray? {
        if(e.exploration && e.def.model=="skeleton") {
            val stage=if(e.attackWindup>0f) 1+((1f-e.attackWindup/.8f)*8).toInt().coerceIn(0,8) else 0
            val frames=heldFrames.getOrPut("exploration_bow") { arrayOfNulls(10) }
            frames[stage]?.let { return it }
            heldMesh.clear()
            heldMesh.weapon("bow",if(stage==0) 0f else (stage-1)/8f,-1f,stage>0,0x9EAEB5,showSlingHand=false)
            return heldMesh.vertices.copyOf(heldMesh.count).also { frames[stage]=it }
        }
        val weaponType = e.heldWeaponType ?: return null
        if (e.def.model == "soldier" && e.exhibitPose != ExhibitPose.REFERENCE) {
            val reloadFrame = (e.weaponReload * 8).toInt().coerceIn(0, 8)
            val shotFrame = if (reloadFrame == 0 && e.shotRecoil > 0f)
                ((.16f - e.shotRecoil) / .04f).toInt().coerceIn(0, 3) else -1
            // Pas de clé texte fabriquée à neuf : c'était une chaîne par soldat et par image.
            val frames = heldFrames.getOrPut(weaponType) { arrayOfNulls(9 * 5) }
            val slot = reloadFrame * 5 + shotFrame + 1
            frames[slot]?.let { return it }
            heldMesh.clear()
            heldMesh.weapon(weaponType, 0f, if (shotFrame < 0) -1f else shotFrame * .04f,
                true, 0xD5BB75, reload = reloadFrame / 8f)
            return heldMesh.vertices.copyOf(heldMesh.count).also { frames[slot] = it }
        }
        return null
    }

    // ── Pose du mob en cours de construction ──────────────────────────────────
    //
    // Calculée une fois par mob, puis lue par de petites méthodes. Mesuré sur tablette le
    // 16/09/2026 : l'ancien buildBody, une seule méthode de 170 lignes à boucles imbriquées, ne se
    // faisait pas compiler par le JIT et tournait **interprété** — 60 à 67 % du processeur de
    // l'appli dès qu'une garnison était dans le champ, dont une grosse part dans un crochet de
    // débogage appelé à chaque tour de boucle. Des méthodes courtes se compilent, elles.
    private var pS = 0f
    private var pCosY = 0f; private var pSinY = 0f
    private var pEx = 0f; private var pEy = 0f; private var pEz = 0f
    private var pReference = false; private var pPassive = false; private var pSoldier = false
    private var pHeavyArms = false; private var pResting = false; private var pMoving = false
    private var pGait = 0f; private var pLocomotion = 0f; private var pWalk = 0f
    private var pStrike = 0f; private var pBreath = 0f; private var pFlinch = 0f
    private var pArcher = false
    private var pGrazingDrop = 0f; private var pSxz = 1f; private var pSyY = 1f
    private var pAnimTime = 0f; private var pId = 0; private var pRecoil = 0f; private var pFlash = 0f
    private val pTint = FloatArray(3)

    /** Ajoute le modèle de [e] dans [boV] à partir de [offset]. Retourne le nouvel offset. */
    private fun buildBody(e: Enemy, camX: Double, camY: Double, camZ: Double, offset: Int, weaponVertices: FloatArray?): Int {
        val model = if (e.def.behavior == "passive") AnimalModels.get(e.def.model, e.young, e.coat) else MobModels.get(e.def.model)
        preparePose(e, model, camX, camY, camZ)
        val skipModelWeapon = pSoldier && e.heldWeaponType != null
        val parts = model.parts
        var n = offset
        for (i in parts.indices) {
            val part = parts[i]
            if (skipModelWeapon && (part.limb == Limb.WEAPON || part.limb == Limb.MUZZLE_FLASH)) continue
            if (model.squash && i == 0) {
                if (n + SlimeGeometry.vertices.size / 4 * 6 > boV.size) break
                n = emitSlime(part, n)
                continue
            }
            if (part.limb == Limb.MUZZLE_FLASH && (pReference || pRecoil < .11f)) continue
            if (part.limb == Limb.WEAPON && pReference) continue
            if (n + 216 > boV.size) break   // 6 faces × 6 sommets × 6 floats ; boV partagé entre mobs
            placeCorners(model, part)
            n = emitPart(part, n)
        }
        if (weaponVertices != null && n + weaponVertices.size <= boV.size) n = emitWeapon(weaponVertices, n)
        return n
    }

    private fun preparePose(e: Enemy, model: MobModel, camX: Double, camY: Double, camZ: Double) {
        pS = e.baseScale * 2f / MobModels.REF_VOX             // unités monde par voxel
        val yawRad = Math.toRadians(e.yaw.toDouble())
        pCosY = cos(yawRad).toFloat(); pSinY = sin(yawRad).toFloat()
        pEx = (e.x - camX).toFloat()
        pEz = (e.z - camZ).toFloat()
        pEy = (e.y - camY).toFloat()

        pReference = e.exhibitPose == ExhibitPose.REFERENCE
        pPassive = e.def.behavior == "passive"
        pSoldier = e.def.model == "soldier"
        pArcher = e.exploration && e.def.model=="skeleton"
        pHeavyArms = e.def.model == "ogre" || e.def.model == "golem"
        pResting = e.resting
        pAnimTime = e.animTime
        pId = e.id
        pRecoil = e.shotRecoil
        pMoving = !e.resting && (e.state == EnemyState.CHASE || e.state == EnemyState.WANDER)
        pGait = if (pPassive || pSoldier || e.def.behavior=="settler") e.animTime * model.gait else e.walkPhase
        pLocomotion = if (pPassive || pSoldier || e.def.behavior=="settler") { if (pMoving) 1f else 0f } else e.motionBlend
        pWalk = if (pReference) 0f else sin(pGait) * pLocomotion
        pStrike = if (pReference || pPassive || pSoldier) 0f else (e.strikeTime / .55f).coerceIn(0f, 1f)
        pBreath = if (pReference || pPassive) 0f else sin(e.animTime * 1.8f + e.id * .73f) * model.breath
        pFlinch = if (pReference || pPassive) 0f else e.hitFlash.coerceIn(0f, .25f) * 2f
        // Descente du cou pendant le broutage, commune à toute la tête (yeux,
        // museau, oreilles et cornes), en unités voxel avant l'échelle monde.
        pGrazingDrop = if (!pReference && e.resting && pPassive)
            model.feedingDrop * (1f + .09f * sin(e.animTime * if (e.def.model == "chicken") 7f else 2f)) else 0f

        pSxz = 1f; pSyY = 1f
        if (model.squash && !pReference) {
            val q = sin(if (pLocomotion > .1f) pGait else e.animTime * 2f)
            pSyY = 1f + 0.14f * q - .18f * pStrike; pSxz = 1f / sqrt(pSyY)
        }
        if (model.floats && !pReference) pEy += 0.15f * sin(e.animTime * FLOAT_FREQ)

        levelTint(if (pPassive) 2 else e.level, e.isBoss, pTint)
        // La progression reste lisible sur le label ; elle ne masque plus les matériaux pastel.
        if (!pPassive) for (i in 0..2) pTint[i] = 1f + (pTint[i] - 1f) * .3f
        pFlash = e.hitFlash.coerceIn(0f, 1f) * 0.7f
    }

    private fun emitSlime(part: MobPart, offset: Int): Int {
        val mesh = SlimeGeometry.vertices
        var n = offset
        var i = 0
        while (i < mesh.size) {
            val px = mesh[i] * pSxz * pS
            val py = mesh[i + 1] * pSyY * pS
            val pz = mesh[i + 2] * pSxz * pS
            boV[n++] = pEx + px * pCosY + pz * pSinY
            boV[n++] = pEy + py
            boV[n++] = pEz - px * pSinY + pz * pCosY
            for (channel in 0..2) {
                val color = ((part.color ushr (16 - channel * 8)) and 255) / 255f
                boV[n++] = (color * pTint[channel] * mesh[i + 3] * (1f - pFlash) +
                    (if (channel == 0) 1f else .15f) * pFlash).coerceIn(0f, 1f)
            }
            i += 4
        }
        return n
    }

    /** Angle de balancement / pose du membre. */
    private fun limbAngle(model: MobModel, part: MobPart): Float {
        val baseRad = Math.toRadians(part.baseTiltDeg.toDouble()).toFloat()
        if (pReference && part.limb != Limb.NONE) return 0f
        return when (part.limb) {
            Limb.ROTOR -> baseRad + pAnimTime * 2f
            Limb.LEG -> baseRad + part.side * pWalk * model.stride
            Limb.ARM ->
                if (pSoldier) baseRad
                else if(pArcher) baseRad - if(part.side>0) 1.3f else .7f
                else baseRad - pStrike * (if (pHeavyArms) 1.15f else if (part.side > 0) 1f else .65f) -
                    part.side * pWalk * model.stride * .65f
            Limb.HEAD -> if (pResting) .65f + .12f * sin(pAnimTime * 2f) else .04f * sin(pAnimTime * 2f)
            Limb.TAIL -> .18f * sin(pAnimTime * 2.5f)
            Limb.WING -> if (pMoving) .22f * sin(pAnimTime * 12f) else .04f * sin(pAnimTime * 2f)
            Limb.LOOK -> .04f * sin(pAnimTime * 1.8f + pId * .73f) - pStrike * .10f
            Limb.CLOTH -> baseRad + .10f * sin(pAnimTime * 2.3f + part.side) + pWalk * .12f
            Limb.CRAWL -> part.side * pWalk * .22f
            Limb.NONE, Limb.WEAPON, Limb.MUZZLE_FLASH -> baseRad
        }
    }

    /** Remplit [corners] avec les 8 coins de [part], posés, animés et orientés. */
    private fun placeCorners(model: MobModel, part: MobPart) {
        val ang = limbAngle(model, part)
        val cosA = cos(ang); val sinA = sin(ang)
        for (corner in 0 until 8) placeCorner(model, part, corner, cosA, sinA)
    }

    /** Coin [corner] (bit0 = x, bit1 = y, bit2 = z ; 0 = min, 1 = max), voir [FACE_IDX]. */
    private fun placeCorner(model: MobModel, part: MobPart, corner: Int, cosA: Float, sinA: Float) {
        val hw = part.w * 0.5f; val hh = part.h * 0.5f; val hd = part.d * 0.5f
        var lx = part.cx + if (corner and 1 == 1) hw else -hw
        var ly = part.cy + if ((corner shr 1) and 1 == 1) hh else -hh
        var lz = part.cz + if ((corner shr 2) and 1 == 1) hd else -hd
        // Écrasement gélatineux (autour des pieds / de l'axe central)
        lx *= pSxz; lz *= pSxz; ly *= pSyY
        // Rotation du membre autour de son pivot (axe X)
        val pivotZ = if (part.limb == Limb.HEAD) model.headPivotZ else part.pivotZ
        val dy = ly - part.pivotY; val dz = lz - pivotZ
        var ry = part.pivotY + dy * cosA - dz * sinA
        var rz = pivotZ + dy * sinA + dz * cosA
        if (part.limb == Limb.HEAD) ry -= pGrazingDrop
        if (part.limb == Limb.LOOK && !pReference) {
            val turn = if (pSoldier && !pResting) 0f else .09f * sin(pAnimTime * .8f + pId * .73f)
            val hx = lx - part.pivotX; val hz = rz - part.pivotZ
            lx = part.pivotX + hx * cos(turn) + hz * sin(turn)
            rz = part.pivotZ - hx * sin(turn) + hz * cos(turn)
        }
        if (part.limb == Limb.CRAWL && !pReference) {
            ry += max(0f, part.side * sin(pGait)) * .7f * pLocomotion
        }
        if (!pPassive && !pReference && part.limb != Limb.LEG && part.limb != Limb.CRAWL) {
            ry += pBreath
            rz += (pStrike * .6f - pFlinch * .45f) * (ly / model.heightVox).coerceIn(0f, 1f)
        }
        if(part.limb==Limb.ROTOR) {
            val rx=lx-part.pivotX
            lx=part.pivotX+rx*cosA-dy*sinA
            ry=part.pivotY+rx*sinA+dy*cosA
            rz=lz
        }
        if (part.limb == Limb.WING) {
            // Aile articulée sur le flanc, autour de Z, vers l'extérieur.
            val pivotX = if (pPassive) part.cx - part.side * part.w * .5f else part.pivotX
            val wingX = lx - pivotX
            lx = pivotX + wingX * cosA - dy * sinA * part.side
            ry = part.pivotY + wingX * sinA * part.side + dy * cosA
            rz = lz
        }
        if (pReference && part.limb == Limb.ARM) {
            val armX = lx - part.cx
            val armY = ly - part.pivotY
            lx = part.cx - part.side * armY
            ry = part.pivotY + part.side * armX
        }
        if (!pReference && pSoldier &&
            (part.limb == Limb.ARM || part.limb == Limb.WEAPON || part.limb == Limb.MUZZLE_FLASH)) {
            val recoil = (pRecoil / .16f).coerceIn(0f, 1f)
            rz -= recoil * 1.8f
            ry += recoil * .45f
        }
        // Échelle voxel→monde, orientation (yaw) puis translation au pied du mob
        val px = lx * pS; val py = ry * pS; val pz = rz * pS
        val k = corner * 3
        corners[k] = pEx + (px * pCosY + pz * pSinY)
        corners[k + 1] = pEy + py
        corners[k + 2] = pEz + (-px * pSinY + pz * pCosY)
    }

    /** Couleur de la pièce (teinte de niveau sauf pièces auto-éclairées), puis ses 6 faces. */
    private fun emitPart(part: MobPart, offset: Int): Int {
        val cr = ((part.color ushr 16) and 0xFF) / 255f
        val cg = ((part.color ushr 8) and 0xFF) / 255f
        val cb = (part.color and 0xFF) / 255f
        return if (part.emissive) emitBox(boV, offset, cr, cg, cb, pFlash, true)
            else emitBox(boV, offset, cr * pTint[0], cg * pTint[1], cb * pTint[2], pFlash, false)
    }

    private fun emitWeapon(verts: FloatArray, offset: Int): Int {
        var n = offset
        val recoil = pRecoil.coerceIn(0f, .16f) / .16f
        var i = 0
        while (i < verts.size) {
            // Le mesh joueur pointe vers -Z ; rotation de 180° vers l'avant du soldat.
            val px = (if(pArcher) 6f*pS else .09f) - verts[i]
            val py = (if(pArcher) 19f*pS else 1.16f) + verts[i + 1] + recoil * .027f
            val pz = (if(pArcher) 10.5f*pS else .36f) - verts[i + 2] - recoil * .108f
            boV[n++] = pEx + px * pCosY + pz * pSinY
            boV[n++] = pEy + py
            boV[n++] = pEz - px * pSinY + pz * pCosY
            boV[n++] = verts[i + 3]; boV[n++] = verts[i + 4]; boV[n++] = verts[i + 5]
            i += 6
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

    // Écrites dans des tableaux fournis : ces deux-là allouaient un tableau et un Triple par mob
    // et par image.
    private fun levelTint(level: Int, boss: Boolean, out: FloatArray) {
        val row = when {
            level <= 1  -> 0
            level <= 3  -> 1
            level <= 5  -> 2
            level <= 8  -> 3
            level <= 11 -> 4
            level <= 14 -> 5
            else        -> 6
        }
        out[0] = LEVEL_TINTS[row * 3]; out[1] = LEVEL_TINTS[row * 3 + 1]; out[2] = LEVEL_TINTS[row * 3 + 2]
        if (boss) { out[0] *= 0.95f; out[1] *= 0.68f; out[2] *= 0.68f }
    }

    private fun levelBgColor(level: Int, out: FloatArray) {
        val row = when {
            level <= 1  -> 0
            level <= 3  -> 1
            level <= 5  -> 2
            level <= 8  -> 3
            level <= 11 -> 4
            level <= 14 -> 5
            else        -> 6
        }
        out[0] = LEVEL_BACKGROUNDS[row * 3]; out[1] = LEVEL_BACKGROUNDS[row * 3 + 1]
        out[2] = LEVEL_BACKGROUNDS[row * 3 + 2]
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

        // Teinte et fond de label par tranche de niveau (voir levelTint / levelBgColor).
        private val LEVEL_TINTS = floatArrayOf(
            0.85f, 0.88f, 0.85f,  1.0f, 1.0f, 1.0f,  0.82f, 1.05f, 0.82f,  1.10f, 1.0f, 0.65f,
            1.15f, 0.78f, 0.55f,  1.20f, 0.60f, 0.55f,  1.0f, 0.55f, 1.15f,
        )
        private val LEVEL_BACKGROUNDS = floatArrayOf(
            0.20f, 0.20f, 0.20f,  0.05f, 0.10f, 0.40f,  0.05f, 0.30f, 0.05f,  0.35f, 0.30f, 0.00f,
            0.40f, 0.18f, 0.00f,  0.40f, 0.02f, 0.02f,  0.30f, 0.00f, 0.35f,
        )
        /** Étiquettes de niveau prêtes : pas de `toString()` par mob et par image. */
        private val LEVEL_LABELS = Array(100) { it.toString() }

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
