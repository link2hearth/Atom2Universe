package com.Atom2Universe.app.crypto

import android.animation.ValueAnimator
import android.graphics.*
import android.content.Context
import android.util.AttributeSet
import android.view.View
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.*

/**
 * Vue Canvas 2D qui affiche la Terre et la Lune avec rendu sphérique réel.
 *
 * Deux executors séparés :
 *  - renderExecutor : rendu sphérique pixel par pixel (SphereRenderer)
 *  - networkExecutor : téléchargements HTTP (photo EPIC, texture sans nuages)
 */
class EarthMoonCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class CameraView(val label: String) {
        FROM_SUN("Depuis le Soleil ☀"),
        SUN_RIGHT("Soleil à droite"),
        OPPOSITE_SUN("Face à la nuit"),
        SUN_LEFT("Soleil à gauche"),
        FROM_ABOVE("Vue du dessus"),
        FROM_BELOW("Vue du dessous"),
        EPIC_REALTIME("Photo NASA • direct"),
        FIXED_LOCATION("Vue fixe 📌")
    }

    companion object {
        data class LocationPreset(val name: String, val latDeg: Double, val lonDeg: Double)

        val LOCATION_PRESETS = listOf(
            LocationPreset("Greenwich / Équateur", 0.0, 0.0),
            LocationPreset("Paris", 48.9, 2.4),
            LocationPreset("New York", 40.7, -74.0),
            LocationPreset("Los Angeles", 34.1, -118.2),
            LocationPreset("La Havane / Cuba", 23.1, -82.4),
            LocationPreset("Guyane française", 4.9, -52.3),
            LocationPreset("Lima / Pérou", -12.0, -77.0),
            LocationPreset("São Paulo", -23.5, -46.6),
            LocationPreset("Moscou", 55.8, 37.6),
            LocationPreset("Le Caire / Afrique du Nord", 30.1, 31.2),
            LocationPreset("Nairobi / Afrique centrale", -1.3, 36.8),
            LocationPreset("Le Cap / Afrique du Sud", -33.9, 18.4),
            LocationPreset("Dubaï", 25.2, 55.3),
            LocationPreset("New Delhi / Inde", 28.6, 77.2),
            LocationPreset("Mumbai / Inde", 19.1, 72.9),
            LocationPreset("Beijing", 39.9, 116.4),
            LocationPreset("Tokyo", 35.7, 139.7),
            LocationPreset("Sydney", -33.9, 151.2),
            LocationPreset("Auckland", -36.9, 174.8),
            LocationPreset("Hawaï", 21.3, -157.8),
            LocationPreset("Pacifique Central", 0.0, -180.0)
        )
    }

    // ── Textures sources (équirectangulaires) ───────────────────────────────
    private var earthTexture: Bitmap? = null
    private var earthNightTexture: Bitmap? = null
    private var moonTexture: Bitmap? = null

    // ── Texture sans nuages (assets/textures/earth_clear.jpg) ───────────────
    private var clearEarthTexture: Bitmap? = null

    // ── Cache photo NASA EPIC ────────────────────────────────────────────────
    private var epicBitmap: Bitmap? = null
    private var epicFetchedAt: Long = 0L
    private var epicIsLoading = false
    private val EPIC_REFRESH_MS = 2 * 60 * 60 * 1000L

    // ── État astronomique ────────────────────────────────────────────────────
    private var snapshot: AstronomyCalculator.AstroSnapshot? = null

    // ── Position projetée de la Lune ─────────────────────────────────────────
    private var moonScreenX = 0f
    private var moonScreenY = 0f
    private var moonInFront = true

    // ── Bitmaps rendus ───────────────────────────────────────────────────────
    private var renderedEarth: Bitmap? = null
    private var renderedMoon:  Bitmap? = null

    // ── Vue caméra ───────────────────────────────────────────────────────────
    var cameraView: CameraView = CameraView.FROM_SUN
        set(value) { field = value; triggerRender() }

    // ── Mode Terre seule ─────────────────────────────────────────────────────
    var showMoon: Boolean = true
        set(value) { field = value; triggerRender() }

    // ── Mode sans nuages ─────────────────────────────────────────────────────
    var showClouds: Boolean = true
        set(value) { field = value; triggerRender() }

    // ── Ligne de terminateur ─────────────────────────────────────────────────
    var showTerminator: Boolean = true
        set(value) { field = value; triggerRender() }

    // ── Fond avec étoiles ────────────────────────────────────────────────────
    var drawBackground: Boolean = true

    // ── Multiplicateur distance Lune ─────────────────────────────────────────
    var moonDistanceMultiplier: Float = 1.3f
        set(value) { field = value; triggerRender() }

    // ── Échelle Terre personnalisée (0f = automatique) ───────────────────────
    var earthScaleOverride: Float = 0f
        set(value) { field = value; triggerRender() }

    // ── Localisation fixe (pour CameraView.FIXED_LOCATION) ──────────────────
    var fixedLatDeg: Double = 51.5  // Greenwich par défaut
        set(value) { field = value; triggerRender() }
    var fixedLonDeg: Double = 0.0
        set(value) { field = value; triggerRender() }

    // ── Client HTTP (OkHttp, réutilisé pour toutes les requêtes) ────────────
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Executors ────────────────────────────────────────────────────────────
    private val renderExecutor  = Executors.newSingleThreadExecutor()
    private val networkExecutor = Executors.newSingleThreadExecutor() // EPIC uniquement
    private var pendingRender:  Future<*>? = null
    private var pendingNetwork: Future<*>? = null

    // ── Peints ───────────────────────────────────────────────────────────────
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val orbitPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 1.2f
    }
    private val starPaint    = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Animation orbite lunaire ─────────────────────────────────────────────
    private var orbitPulseAlpha = 80
    private var orbitDashPhase  = 0f
    private val orbitAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2500
        repeatCount = ValueAnimator.INFINITE
        interpolator = null
        addUpdateListener { anim ->
            val t = anim.animatedValue as Float
            orbitPulseAlpha = (30 + 140 * (0.5f + 0.5f * sin(PI * 2 * t)).toFloat()).toInt()
            orbitDashPhase  = t * 24f
            if (showMoon) invalidate()
        }
    }
    private val epicClipPath = Path()

    // ── Étoiles de fond ──────────────────────────────────────────────────────
    private val stars: List<FloatArray> = (0 until 130).map { i ->
        val rng = java.util.Random(i * 6364136L + 1442695040L)
        floatArrayOf(rng.nextFloat(), rng.nextFloat(), 0.5f + rng.nextFloat() * 1.5f)
    }

    init {
        loadTextures()
    }

    // ── Chargement des textures ──────────────────────────────────────────────

    private fun loadTextures() {
        try {
            val assets = context.assets
            earthTexture      = loadScaled(assets, "textures/earth.jpg",       512)
            earthNightTexture = loadScaled(assets, "textures/earth_night.jpg", 512)
            moonTexture       = loadScaled(assets, "textures/moon.jpg",        256)
            // Texture sans nuages optionnelle — place earth_clear.jpg dans assets/textures/
            clearEarthTexture = loadScaled(assets, "textures/earth_clear.jpg", 512)
        } catch (_: Exception) { }

        triggerRender()
    }

    private fun loadScaled(assets: android.content.res.AssetManager, path: String, maxSide: Int): Bitmap? {
        return try {
            assets.open(path).use { BitmapFactory.decodeStream(it)?.let { bmp -> scaleDown(bmp, maxSide) } }
        } catch (_: Exception) { null }
    }

    private fun scaleDown(raw: Bitmap, maxSide: Int): Bitmap {
        val w = raw.width; val h = raw.height
        if (w <= maxSide && h <= maxSide) return raw
        val scale = maxSide.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(raw, (w * scale).toInt(), (h * scale).toInt(), true)
            .also { if (it !== raw) raw.recycle() }
    }

    // ── API publique ─────────────────────────────────────────────────────────

    fun updateSnapshot(snap: AstronomyCalculator.AstroSnapshot) {
        snapshot = snap
        triggerRender()
    }

    // ── Déclenchement du rendu ───────────────────────────────────────────────

    private fun triggerRender() {
        val view = cameraView
        val w = width; val h = height
        if (w <= 0 || h <= 0) return

        pendingRender?.cancel(false)

        // ── Mode photo NASA EPIC ─────────────────────────────────────────────
        if (view == CameraView.EPIC_REALTIME) {
            val stale = System.currentTimeMillis() - epicFetchedAt > EPIC_REFRESH_MS
            if (!epicIsLoading && (epicBitmap == null || stale)) {
                epicIsLoading = true
                pendingNetwork?.cancel(false)
                pendingNetwork = networkExecutor.submit { fetchEpicImage() }
            }
            invalidate()
            return
        }

        val snap = snapshot ?: return

        // Texture du jour : sans nuages si disponible et option activée
        val earthTex = if (!showClouds) clearEarthTexture ?: earthTexture else earthTexture
        val earthNightTex = earthNightTexture
        val moonTex = moonTexture

        val earthScale = if (earthScaleOverride > 0f) earthScaleOverride else if (showMoon) 0.25f else 0.46f
        val earthR = (minOf(w, h) * earthScale).toInt().coerceAtLeast(4)
        val moonR  = (earthR * 0.273f).toInt().coerceAtLeast(2)

        pendingRender = renderExecutor.submit {
            val (right, up, forward) = cameraVectors(snap, view)

            val earth = earthTex?.let {
                SphereRenderer.renderEarth(it, earthNightTex, snap, right, up, forward, earthR, showTerminator)
            }
            val moon = if (showMoon) moonTex?.let {
                SphereRenderer.renderMoon(it, snap, right, up, forward, moonR)
            } else null

            val norm = 384400.0
            val mu = snap.moonPos.dot(right)
            val mv = snap.moonPos.dot(up)
            val md = snap.moonPos.dot(forward)
            val cx = w / 2f; val cy = h / 2f
            val orbitScale = minOf(w, h) * 0.42f * moonDistanceMultiplier
            val mx = cx + (mu / norm * orbitScale).toFloat()
            val my = cy - (mv / norm * orbitScale).toFloat()
            val inFront = md <= 0 || hypot(mx - cx, my - cy) > earthR * 0.85f

            post {
                renderedEarth = earth
                renderedMoon  = moon
                moonScreenX   = mx
                moonScreenY   = my
                moonInFront   = inFront
                invalidate()
            }
        }
    }

    // ── Téléchargement photo NASA EPIC ───────────────────────────────────────

    private fun fetchEpicImage() {
        try {
            // 1) Métadonnées : image la plus récente
            val metaResp = httpClient.newCall(
                Request.Builder()
                    .url("https://epic.gsfc.nasa.gov/api/natural")
                    .build()
            ).execute()

            val metaBody = metaResp.body?.string()
            val metaCode = metaResp.code
            metaResp.close()

            android.util.Log.d("EpicFetch", "meta HTTP $metaCode | body=${metaBody?.take(200)}")

            if (metaCode != 200 || metaBody.isNullOrBlank()) {
                post { epicIsLoading = false; invalidate() }
                return
            }

            val arr = try { JSONArray(metaBody) } catch (e: Exception) {
                android.util.Log.w("EpicFetch", "Parse JSON failed: ${e.message}")
                post { epicIsLoading = false; invalidate() }
                return
            }
            if (arr.length() == 0) {
                android.util.Log.w("EpicFetch", "Empty image array")
                post { epicIsLoading = false; invalidate() }
                return
            }

            val latest    = arr.getJSONObject(0)
            val imageName = latest.getString("image")
            val dateStr   = latest.getString("date").substring(0, 10) // "YYYY-MM-DD"
            val parts     = dateStr.split("-")
            val y = parts[0]; val m = parts[1]; val d = parts[2]

            // 2) Miniature JPEG 256×256
            val thumbUrl =
                "https://epic.gsfc.nasa.gov/archive/natural/$y/$m/$d/thumbs/$imageName.jpg"
            android.util.Log.d("EpicFetch", "Fetching thumb: $thumbUrl")

            val imgResp = httpClient.newCall(
                Request.Builder().url(thumbUrl).build()
            ).execute()
            val imgCode = imgResp.code
            val bmp = imgResp.body?.byteStream()?.let { BitmapFactory.decodeStream(it) }
            imgResp.close()

            android.util.Log.d("EpicFetch", "thumb HTTP $imgCode | bmp=$bmp")

            post {
                epicBitmap    = bmp
                epicFetchedAt = System.currentTimeMillis()
                epicIsLoading = false
                invalidate()
            }
        } catch (e: Exception) {
            android.util.Log.e("EpicFetch", "Fetch error", e)
            post { epicIsLoading = false; invalidate() }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        orbitAnimator.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) triggerRender()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        orbitAnimator.cancel()
        pendingRender?.cancel(true)
        pendingNetwork?.cancel(true)
        renderExecutor.shutdownNow()
        networkExecutor.shutdownNow()
    }

    // ── Dessin ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f; val cy = h / 2f
        val minDim = minOf(w, h)

        if (drawBackground) {
            canvas.drawColor(Color.BLACK)
            for (s in stars) {
                starPaint.alpha = (120 + s[2].toInt() * 40).coerceIn(60, 220)
                starPaint.color = Color.WHITE
                canvas.drawCircle(s[0] * w, s[1] * h, s[2], starPaint)
            }
        }

        if (cameraView == CameraView.EPIC_REALTIME) {
            drawEpicImage(canvas, cx, cy, minDim * 0.46f)
            return
        }

        val earthR = minDim * (if (earthScaleOverride > 0f) earthScaleOverride else if (showMoon) 0.25f else 0.46f)
        val moonR  = earthR * 0.273f

        if (showMoon) {
            snapshot?.let { drawOrbit(canvas, it, cx, cy, minDim * 0.42f * moonDistanceMultiplier) }
        }
        drawEarth(canvas, cx, cy, earthR)
        if (showMoon && moonInFront) drawMoon(canvas, moonScreenX, moonScreenY, moonR)
    }

    // ── Affichage photo EPIC ─────────────────────────────────────────────────

    private fun drawEpicImage(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val bmp = epicBitmap
        if (bmp == null) {
            canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0D1B2A")
            })
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.parseColor("#2A4A7A")
                strokeWidth = 2f
            }.also {
                canvas.drawCircle(cx, cy, radius * 0.65f, it)
                canvas.drawCircle(cx, cy, radius * 0.35f, it)
            }
            return
        }
        epicClipPath.reset()
        epicClipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(epicClipPath)
        canvas.drawBitmap(bmp, null,
            RectF(cx - radius, cy - radius, cx + radius, cy + radius), bitmapPaint)
        canvas.restore()
    }

    // ── Dessin Terre & Lune ──────────────────────────────────────────────────

    private fun drawEarth(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val bmp = renderedEarth
        if (bmp != null) {
            canvas.drawBitmap(bmp, cx - radius, cy - radius, bitmapPaint)
        } else {
            canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx - radius * 0.3f, cy - radius * 0.3f, radius * 1.2f,
                    intArrayOf(Color.parseColor("#4FC3F7"), Color.parseColor("#1565C0"), Color.parseColor("#0A237A")),
                    floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            })
        }
    }

    private fun drawMoon(canvas: Canvas, mx: Float, my: Float, radius: Float) {
        val bmp = renderedMoon
        if (bmp != null) {
            canvas.drawBitmap(bmp, mx - radius, my - radius, bitmapPaint)
        } else {
            canvas.drawCircle(mx, my, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#A0A0A0")
            })
        }
    }

    private fun drawOrbit(
        canvas: Canvas, snap: AstronomyCalculator.AstroSnapshot,
        cx: Float, cy: Float, orbitScale: Float
    ) {
        val (right, up, _) = cameraVectors(snap, cameraView)
        val Om = snap.moonAscendingNodeRad; val i = snap.moonInclinationRad
        val e1 = AstronomyCalculator.Vec3(cos(Om), sin(Om), 0.0)
        val e2 = AstronomyCalculator.Vec3(-cos(i) * sin(Om), cos(i) * cos(Om), sin(i))
        val moonDist = snap.moonPos.length(); val norm = 384400.0
        val path = Path()
        for (step in 0..64) {
            val angle = step * 2.0 * PI / 64.0
            val p = e1 * (cos(angle) * moonDist) + e2 * (sin(angle) * moonDist)
            val pu = (p.dot(right) / norm * orbitScale + cx).toFloat()
            val pv = (-(p.dot(up)  / norm * orbitScale) + cy).toFloat()
            if (step == 0) path.moveTo(pu, pv) else path.lineTo(pu, pv)
        }
        path.close()
        orbitPaint.alpha = orbitPulseAlpha
        orbitPaint.pathEffect = DashPathEffect(floatArrayOf(5f, 7f), orbitDashPhase)
        canvas.drawPath(path, orbitPaint)
    }

    // ── Calcul des vecteurs caméra ───────────────────────────────────────────

    fun cameraVectors(
        snap: AstronomyCalculator.AstroSnapshot,
        view: CameraView
    ): Triple<AstronomyCalculator.Vec3, AstronomyCalculator.Vec3, AstronomyCalculator.Vec3> {
        val sunDir = snap.sunDir; val eclNorth = AstronomyCalculator.Vec3(0.0, 0.0, 1.0)
        val forward: AstronomyCalculator.Vec3; val upHint: AstronomyCalculator.Vec3
        when (view) {
            CameraView.FROM_SUN      -> { forward = sunDir;                                                       upHint = eclNorth }
            CameraView.SUN_RIGHT     -> { forward = sunDir.cross(eclNorth).normalized();                          upHint = eclNorth }
            CameraView.OPPOSITE_SUN  -> { forward = AstronomyCalculator.Vec3(-sunDir.x, -sunDir.y, -sunDir.z);   upHint = eclNorth }
            CameraView.SUN_LEFT      -> { forward = eclNorth.cross(sunDir).normalized();                          upHint = eclNorth }
            CameraView.FROM_ABOVE    -> { forward = AstronomyCalculator.Vec3(0.0, 0.0, -1.0);                     upHint = AstronomyCalculator.Vec3(-sunDir.x, -sunDir.y, 0.0).normalized() }
            CameraView.FROM_BELOW    -> { forward = AstronomyCalculator.Vec3(0.0, 0.0,  1.0);                     upHint = AstronomyCalculator.Vec3(-sunDir.x, -sunDir.y, 0.0).normalized() }
            CameraView.EPIC_REALTIME -> { forward = sunDir;                                                       upHint = eclNorth }
            CameraView.FIXED_LOCATION -> {
                // Convertit les coordonnées géographiques (lat/lon) en vecteur écliptique
                // pour que la caméra pointe toujours vers ce lieu, mais le terminateur bouge.
                val latR = Math.toRadians(fixedLatDeg)
                val lonR = Math.toRadians(fixedLonDeg)
                val cosLat = cos(latR); val sinLat = sin(latR)
                val cosLon = cos(lonR); val sinLon = sin(lonR)
                // Repère corps terrestre
                val xBody = cosLat * cosLon
                val yBody = cosLat * sinLon
                val zBody = sinLat
                // Corps → Équatorial (rotation inverse GMST autour de Z)
                val cG = cos(snap.gmstRad); val sG = sin(snap.gmstRad)
                val xEq = xBody * cG - yBody * sG
                val yEq = xBody * sG + yBody * cG
                val zEq = zBody
                // Équatorial → Écliptique (rotation inverse obliquité autour de X)
                val cE = cos(snap.obliquityRad); val sE = sin(snap.obliquityRad)
                // Negate : le pixel central a pour normale n = -forward (voir SphereRenderer),
                // donc forward doit pointer à l'OPPOSÉ du lieu pour que le centre affiche le bon endroit.
                forward = AstronomyCalculator.Vec3(-xEq, -yEq * cE - zEq * sE, yEq * sE - zEq * cE).normalized()
                // Pôle nord terrestre en écliptique = upHint pour garder le nord en haut
                upHint = AstronomyCalculator.Vec3(0.0, sE, cE)
            }
        }
        val right = forward.cross(upHint).normalized()
        val upFinal = right.cross(forward).normalized()
        return Triple(right, upFinal, forward)
    }

    private operator fun AstronomyCalculator.Vec3.plus(other: AstronomyCalculator.Vec3) =
        AstronomyCalculator.Vec3(x + other.x, y + other.y, z + other.z)
}
