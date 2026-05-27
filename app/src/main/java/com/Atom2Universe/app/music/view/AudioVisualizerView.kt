package com.Atom2Universe.app.music.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Vue personnalisée pour afficher différents types de visualisation audio.
 *
 * OPTIMISÉ pour la performance:
 * - Lookup tables pour sin/cos
 * - Paint pré-alloués (pas d'allocation dans onDraw)
 * - Smoothing des données audio entre frames
 * - Hardware acceleration compatible
 */
class AudioVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class VisualizationMode {
        NONE,       // Désactivé
        // Modes "plats" (1-5)
        BARS,       // 1 - Barres verticales psychédéliques
        WAVE,       // 2 - Forme d'onde
        MIRROR,     // 3 - Barres miroir depuis le centre
        SPECTRUM,   // 4 - Courbe de spectre lissée
        FIRE,       // 5 - Effet de flammes
        // Modes "centraux" (6-15) - supportent fullscreen
        CIRCLE,     // 6 - Visualisation circulaire stéréo
        PARTICLES,  // 7 - Particules explosives
        RADIAL,     // 8 - Rayons depuis le centre (style DJ)
        BLOB,       // 9 - Liquide non-newtonien
        PARTICLES_MONO, // 10 - Particules en niveaux de gris
        KALEIDOSCOPE, // 11 - Mandala psychédélique symétrique
        BOIDS,      // 12 - Flocking organique + fond plasma
        JULIA,      // 13 - Fractale de Julia animée
        MANDELBROT_ZOOM, // 14 - Zoom infini dans Mandelbrot
        JULIA_GRAYSCALE, // 15 - Fractale de Julia en nuances de gris
        DELAUNAY_MESH,   // 16 - Maillage Delaunay réactif à l'audio
        DELAUNAY_GRAYSCALE, // 17 - Maillage Delaunay en noir et blanc
        VORONOI,            // 18 - Diagramme de Voronoï pondéré réactif
        VORONOI_GRAYSCALE,  // 19 - Voronoï en niveaux de gris
        PENROSE_RHOMBUS,    // 20 - Losanges flottants
        PENROSE_TRUE        // 21 - Pavage de Penrose géométriquement correct
    }

    // Modes qui supportent l'affichage fullscreen (quand pochette cachée)
    fun isCentralMode(): Boolean = mode in listOf(
        VisualizationMode.FIRE,        // fullscreen = feu en bas + flocons de neige en haut
        VisualizationMode.CIRCLE,
        VisualizationMode.PARTICLES,
        VisualizationMode.RADIAL,
        VisualizationMode.BLOB,
        VisualizationMode.PARTICLES_MONO,
        VisualizationMode.KALEIDOSCOPE,
        VisualizationMode.BOIDS,
        VisualizationMode.JULIA,
        VisualizationMode.MANDELBROT_ZOOM,
        VisualizationMode.JULIA_GRAYSCALE,
        VisualizationMode.DELAUNAY_MESH,
        VisualizationMode.DELAUNAY_GRAYSCALE,
        VisualizationMode.VORONOI,
        VisualizationMode.VORONOI_GRAYSCALE,
        VisualizationMode.PENROSE_RHOMBUS,
        VisualizationMode.PENROSE_TRUE
    )

    companion object {
        // PERFORMANCE: Lookup tables pour sin/cos (évite les calculs à chaque frame)
        private const val TABLE_SIZE = 360
        private val SIN_TABLE = FloatArray(TABLE_SIZE) { i ->
            sin(i * Math.PI * 2 / TABLE_SIZE).toFloat()
        }
        private val COS_TABLE = FloatArray(TABLE_SIZE) { i ->
            cos(i * Math.PI * 2 / TABLE_SIZE).toFloat()
        }

        // Constantes pour les visualisations
        private const val BAR_COUNT = 32
        private const val MIRROR_BAR_COUNT = 96  // Plus de barres pour un look pro
        private const val CIRCLE_POINTS = 48
        private const val PARTICLE_COUNT = 120  // Augmenté pour l'effet starfield
        private const val RADIAL_RAYS = 64
        private const val BLOB_POINTS = 32
        private const val FIRE_COLUMNS = 40

        // Constantes flocons de neige (mode FIRE fullscreen)
        private const val SNOW_BUCKETS   = 10
        private const val SNOW_MIN_SIZE  = 4f
        private const val SNOW_MAX_SIZE  = 14f
        private const val SNOW_MAX_COUNT = 500

        // Smoothing factor (0 = pas de smoothing, 1 = pas de changement)
        private const val SMOOTHING = 0.3f
    }

    // PERFORMANCE: Fonctions sin/cos rapides
    private fun fastSin(degrees: Float): Float {
        val index = ((degrees % 360f + 360f) % 360f).toInt()
        return SIN_TABLE[index.coerceIn(0, TABLE_SIZE - 1)]
    }

    private fun fastCos(degrees: Float): Float {
        val index = ((degrees % 360f + 360f) % 360f).toInt()
        return COS_TABLE[index.coerceIn(0, TABLE_SIZE - 1)]
    }

    var mode: VisualizationMode = VisualizationMode.BARS
        set(value) {
            field = value
            // Reset particles when changing mode
            if (value == VisualizationMode.PARTICLES || value == VisualizationMode.PARTICLES_MONO) {
                initParticles()
            }
            // Initialize kaleidoscope facets
            if (value == VisualizationMode.KALEIDOSCOPE) {
                initFacets()
            }
            // Initialize boids
            if (value == VisualizationMode.BOIDS) {
                initBoids()
            }
            // Initialize Delaunay mesh
            if (value == VisualizationMode.DELAUNAY_MESH || value == VisualizationMode.DELAUNAY_GRAYSCALE) {
                if (cachedWidth > 0 && cachedHeight > 0) initDelaunayMesh()
            }
            // Initialize Voronoi
            if (value == VisualizationMode.VORONOI || value == VisualizationMode.VORONOI_GRAYSCALE) {
                voronoiSeeds.clear()
            }
            // Initialize Penrose
            if (value == VisualizationMode.PENROSE_RHOMBUS) {
                penroseInitialized = false
            }
            // Initialize Penrose True
            if (value == VisualizationMode.PENROSE_TRUE) {
                penroseTrueInitialized = false
            }
            invalidate()
        }

    // Données audio brutes et lissées
    // PERFORMANCE: Buffers pré-alloués (réutilisés, pas de copyOf())
    private val waveformBuffer = ByteArray(256)
    private val fftBuffer = ByteArray(128)
    private var waveformSize = 0
    private var fftSize = 0

    // PERFORMANCE: Données lissées pour transitions fluides
    private val smoothedFft = FloatArray(128)
    private val smoothedWaveform = FloatArray(256) { 0.5f }

    // === PAINTS PRÉ-ALLOUÉS (évite allocations dans onDraw) ===

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val circleLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val circleRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val mirrorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val spectrumFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Paints pour les nouveaux modes
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val radialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val blobOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val firePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // PERFORMANCE: Pré-allouer le paint pour les labels (évite création dans drawCircle)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val innerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // === PATHS PRÉ-ALLOUÉS ===

    private val wavePath = Path()
    private val circlePath = Path()
    private val circlePathLeft = Path()
    private val circlePathRight = Path()
    private val spectrumPath = Path()
    private val blobPath = Path()

    // PERFORMANCE: Pré-allouer les tableaux de points pour le lissage cubique (CIRCLE)
    private val circlePointsX = FloatArray(48)
    private val circlePointsY = FloatArray(48)

    // === RECTANGLES PRÉ-ALLOUÉS ===
    private val tempRect = RectF()

    // === COULEURS ===

    private var accentColor: Int = 0
    private var secondaryColor: Int = 0
    private var stereoLeftColor: Int = 0
    private var stereoRightColor: Int = 0

    // Animation idle (quand pas de musique)
    private var animationPhase = 0f
    private val animationSpeed = 0.05f

    // Color cycling psychédélique
    private var colorPhase = 0f
    private val colorSpeed = 0.5f

    // PERFORMANCE: Arrays HSV pré-alloués
    private val hsvTemp = floatArrayOf(0f, 0.8f, 1f)
    private val hsvLeft = floatArrayOf(180f, 0.8f, 1f)
    private val hsvRight = floatArrayOf(30f, 0.8f, 1f)

    // Circle layers config (inner → outer) : rayon, intensité FFT, alpha fill, alpha stroke
    private val circleLayerScales = floatArrayOf(0.45f, 0.70f, 1.0f)
    private val circleLayerFftScales = floatArrayOf(0.15f, 0.30f, 0.50f)
    private val circleLayerFillAlphas = intArrayOf(25, 30, 35)
    private val circleLayerStrokeAlphas = intArrayOf(0, 80, 255)

    // === DONNÉES POUR NOUVEAUX MODES ===

    // Particles
    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        var life: Float,
        var hue: Float
    )
    private val particles = ArrayList<Particle>(PARTICLE_COUNT)

    // Flocons de neige (mode FIRE en fullscreen)
    private data class Snowflake(
        var x: Float,
        var y: Float,
        var vy: Float,          // vitesse de chute (px/frame)
        var swayPhase: Float,   // phase de l'oscillation latérale
        var swayAmp: Float,     // amplitude latérale (px)
        var swaySpeed: Float,   // vitesse de l'oscillation
        val bucketIdx: Int      // index dans snowBitmaps (taille pré-rendue)
    )
    private val snowflakes = ArrayList<Snowflake>(500)

    // Bitmaps pré-rendus des flocons (10 tailles discrètes, constantes dans companion object)
    private val snowBitmaps       = arrayOfNulls<Bitmap>(SNOW_BUCKETS)
    private val snowBitmapPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private var snowBitmapsReady  = false
    private var snowLastFrameTime = 0L   // pour le delta-time des flocons

    // Kaleidoscope facets
    private data class Facet(
        var x: Float,      // Position dans le secteur (0-1)
        var y: Float,      // Position dans le secteur (0-1)
        var size: Float,   // Taille (0-1)
        var hue: Float,    // Couleur (0-360)
        var shape: Int,    // Type de forme (0-2)
        var rotation: Float, // Rotation propre
        var speed: Float   // Vitesse de changement
    )
    private val facets = ArrayList<Facet>(60) // 60 facettes pour remplir l'espace

    // Boids (flocking algorithm)
    private data class Boid(
        var x: Float,
        var y: Float,
        var vx: Float,     // Vélocité X
        var vy: Float,     // Vélocité Y
        var angle: Float   // Angle de direction (pour le dessin)
    )
    private val boids = ArrayList<Boid>(100)
    private val boidCount = 60 // Nombre de boids (optimisé pour performance)

    // Flying diamonds for kaleidoscope (particles ejected on beats)
    private data class FlyingDiamond(
        var x: Float,
        var y: Float,
        var vx: Float,     // Vitesse X
        var vy: Float,     // Vitesse Y
        var rotation: Float,
        var rotationSpeed: Float,
        var size: Float,
        var alpha: Float,  // Transparence (1.0 = opaque, 0.0 = invisible)
        var hue: Float,
        var age: Float     // Age en frames (pour fade out)
    )
    private val flyingDiamonds = ArrayList<FlyingDiamond>(50)
    private var lastBeatTime = 0f  // Pour éviter trop de beats rapprochés
    private var bassHistory = FloatArray(10)  // Historique des niveaux de basse
    private var bassHistoryIndex = 0

    // Fire columns heights
    private val fireHeights = FloatArray(FIRE_COLUMNS)

    // === DONNÉES POUR DELAUNAY_MESH ===
    private data class MeshPoint(
        val restX: Float,
        val restY: Float,
        var x: Float,
        var y: Float,
        val distRatio: Float,
        val driftAngle: Float,
        val isRing: Boolean = false,
        val isBall: Boolean = false, // Bille rebondissante à l'intérieur de l'anneau
        var vx: Float = 0f,          // Vélocité X (physique)
        var vy: Float = 0f,          // Vélocité Y (physique)
        // Pré-calculé à l'init — évite trig/sqrt par frame dans la boucle de mise à jour
        val restNdx: Float = 0f,   // (restX-cx)/restNorm  [ring : direction radiale normalisée X]
        val restNdy: Float = 0f,   // (restY-cy)/restNorm  [ring : direction radiale normalisée Y]
        val restNorm: Float = 1f,  // sqrt((restX-cx)²+(restY-cy)²) [ring : rayon de repos]
        val restAngle: Float = 0f, // atan2(restY-cy, restX-cx)    [ring : angle de repos]
        val gravX: Float = 0f,     // cos(driftAngle)               [bille : direction gravité X]
        val gravY: Float = 0f,     // sin(driftAngle)               [bille : direction gravité Y]
        val isCenter: Boolean = false  // Point fixe au centre (ne bouge pas)
    )
    private val meshPoints = ArrayList<MeshPoint>(80)
    private val meshTriangles = ArrayList<IntArray>()   // [a, b, c] indices
    private val meshEdges = ArrayList<IntArray>()       // [a, b] arêtes uniques
    private val meshPath = Path()
    private val meshEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.9f
    }
    private val meshFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var meshFrameSkip = 0
    private val MESH_RETRI_INTERVAL = 4

    // === FOUDRE pour DELAUNAY_MESH ===
    private data class LightningSegment(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val isBranch: Boolean
    )
    private data class LightningBolt(val segments: ArrayList<LightningSegment>, val hue: Float, var alpha: Float)
    private val lightningBolts = ArrayList<LightningBolt>(3)
    private val lightningGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val lightningCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private var lightningCooldown = 0
    private var prevBassAmpMesh = 0f

    // Bass detection for beat-reactive effects
    private var bassLevel = 0f
    private var lastBassLevel = 0f
    private var beatDetected = false
    private var beatEnvelope = 0f      // Enveloppe lisse : montée instantanée, décroissance exponentielle
    private var beatCooldown = 0       // Cooldown anti-double détection (~66ms)
    private var smoothedVolume = 0f       // Moyenne glissante du volume (pour détection de pics)
    private var lastFrameTimeNanos = 0L   // Pour calcul delta-time (vitesse indépendante du framerate)
    private var deltaTime = 1f            // Ratio temps réel / temps cible (1.0 = 60fps)

    // === VORONOÏ PONDÉRÉ ===
    private data class VoronoiSeed(
        var x: Float, var y: Float,
        val ax: Float, val ay: Float,   // position d'ancrage (point de repos)
        var vx: Float = 0f, var vy: Float = 0f,
        val hue: Float,
        var weight: Float,
        val freqBin: Int,
        var smoothedAmp: Float = 0f   // amplitude lissée pour la couleur (anti-stroboscope)
    )
    private val voronoiSeeds = ArrayList<VoronoiSeed>(32)
    private var voronoiBitmap: Bitmap? = null
    private var voronoiPixels: IntArray? = null
    private var voronoiHueOffset = 0f
    private var voronoiLastTimeNs = 0L
    private val voronoiPaint = Paint().apply { isAntiAlias = false }
    private val voronoiRng = java.util.Random()
    // Tableaux plats pré-alloués pour la boucle pixel (pas d'allocation par frame, cache-friendly)
    private val vSnapX     = FloatArray(64)
    private val vSnapY     = FloatArray(64)
    private val vSnapW2    = FloatArray(64)   // weight² précalculé
    private val vSnapColor = IntArray(64)
    private val vHsvTmp    = FloatArray(3)

    // === LOSANGES DE PENROSE (mode 20) ===
    private data class PenroseRhombus(
        val vx: FloatArray,    // 4 coordonnées x (espace écran au repos)
        val vy: FloatArray,    // 4 coordonnées y (espace écran au repos)
        val type: Int,         // 0 = gras (fat, 72°), 1 = fin (thin, 36°)
        val distScaled: Float, // dist_centre * 0.011f : déphasage ripple pré-calculé à l'init
        val ndx: Float,        // direction radiale normalisée X (pré-calculée à l'init)
        val ndy: Float         // direction radiale normalisée Y (pré-calculée à l'init)
    )
    private val penroseRhombuses = ArrayList<PenroseRhombus>(400)
    private var penroseInitialized = false
    private var penrosePhase = 0f        // Phase d'animation (flow temporel)
    private var penrosePulse = 0f        // Enveloppe pulsation beat (0→1, décroît)
    private var penroseHueFat = 30f      // Teinte des losanges gras (dérive lentement)
    private var penroseHueThin = 195f    // Teinte des losanges fins (dérive indépendamment)
    private val penrosePath = Path()     // Path pré-alloué (reset à chaque losange)
    private val penroseFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val penroseStrokePaint = Paint().apply {  // pas d'ANTI_ALIAS : contour 0.8px décoratif, gain GPU
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
    }
    private val penroseHsvTmp = FloatArray(3)

    // === PAVAGE DE PENROSE GÉOMÉTRIQUE (mode 21) ===
    // Les sommets sont stockés en ESPACE TUILE (unité = 1 côté de losange).
    // MODE 21 : Grille de losanges animés (style Delaunay en losange)
    // Grille oblique à 60° → losanges cube-like couvrant tout l'écran.
    // Chaque sommet flotte indépendamment → angles des losanges se déforment doucement.
    // Chaque losange garde sa propre couleur fixe assignée à l'init.
    private data class GridVertex(
        val bx: Float, val by: Float,       // position de repos (pixels)
        val px: Float,  val py: Float,       // phases sin aléatoires (X et Y indépendants)
        val freqX: Float, val freqY: Float   // fréquences légèrement différentes → mouvement non synchronisé
    )
    private data class GridRhombus(val i0: Int, val i1: Int, val i2: Int, val i3: Int, val hue: Float)
    private val gridVertices  = ArrayList<GridVertex>(900)
    private val gridRhombuses = ArrayList<GridRhombus>(700)
    private var gridVx = FloatArray(0)   // positions courantes (recalculées chaque frame)
    private var gridVy = FloatArray(0)
    private var gridStep = 1f            // taille d'une cellule (px) — mémorisée pour les ondes
    private var penroseTrueInitialized = false
    private var penroseTruePhase = 0f
    private var penroseTruePulse = 0f
    private val penroseTruePath = Path()
    private val penroseTrueFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val penroseTrueStrokePaint = Paint().apply {  // pas d'ANTI_ALIAS : contour décoratif, gain GPU
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val penroseTrueHsvTmp = FloatArray(3)

    // === SHADERS PRÉ-ALLOUÉS (recréés uniquement dans onSizeChanged) ===
    // Zero allocation dans onDraw() - réutilisation maximale

    // Shaders pour fond et effets tunnel
    private var tunnelShader: RadialGradient? = null
    private var tunnelShaderMono: RadialGradient? = null
    private var blobBackgroundShader: RadialGradient? = null

    // Pool de shaders pour particules (réutilisés en rotation)
    private val particleTrailShaders = arrayOfNulls<LinearGradient>(8)
    private var particleShaderIndex = 0

    // Pool de shaders pour fire (40 colonnes, réutilisés)
    private val fireGradientShaders = arrayOfNulls<LinearGradient>(FIRE_COLUMNS)
    private val fireGlowShader: LinearGradient? = null

    // (paints flocons supprimés : rendu bitmap pré-calculé)

    // Shaders pour spectrum et radial
    private var spectrumShader: LinearGradient? = null
    private var radialCoreGlowShader: RadialGradient? = null

    // Cache des dimensions pour détecter les changements
    private var cachedWidth = 0
    private var cachedHeight = 0

    init {
        accentColor = ContextCompat.getColor(context, R.color.music_accent)
        secondaryColor = ContextCompat.getColor(context, R.color.music_text_secondary)
        stereoLeftColor = ContextCompat.getColor(context, R.color.music_stereo_left)
        stereoRightColor = ContextCompat.getColor(context, R.color.music_stereo_right)

        barPaint.color = accentColor
        wavePaint.color = accentColor
        circlePaint.color = accentColor
        circleLeftPaint.color = stereoLeftColor
        circleRightPaint.color = stereoRightColor

        // Initialiser les particules
        initParticles()
    }

    private var particlesNeedPrewarm = true

    private fun initParticles() {
        particles.clear()
        for (i in 0 until PARTICLE_COUNT) {
            particles.add(Particle(
                x = 0f, y = 0f,
                vx = 0f, vy = 0f,
                size = 3f + (i % 5),
                life = 0f,
                hue = (i * 360f / PARTICLE_COUNT)
            ))
        }
        particlesNeedPrewarm = true
    }

    private fun initFacets() {
        facets.clear()
        // Créer 60 facettes avec positions/propriétés aléatoires
        for (i in 0 until 60) {
            facets.add(Facet(
                x = (Math.random() * 0.9f).toFloat() + 0.05f, // 5-95% du secteur
                y = (Math.random() * 0.9f).toFloat() + 0.05f,
                size = (Math.random() * 0.08f + 0.02f).toFloat(), // 2-10% de la taille
                hue = (Math.random() * 360).toFloat(),
                shape = (Math.random() * 3).toInt(), // 3 types de formes
                rotation = (Math.random() * 360).toFloat(),
                speed = (Math.random() * 0.5f + 0.3f).toFloat() // Vitesse de rotation
            ))
        }
    }

    fun updateWaveform(data: ByteArray) {
        // PERFORMANCE: Réutiliser le buffer au lieu de copyOf() - zero allocation
        waveformSize = minOf(data.size, waveformBuffer.size)
        System.arraycopy(data, 0, waveformBuffer, 0, waveformSize)

        // PERFORMANCE: Smooth waveform data
        val size = minOf(waveformSize, smoothedWaveform.size)
        for (i in 0 until size) {
            val newValue = (waveformBuffer[i].toInt() + 128) / 256f
            smoothedWaveform[i] = smoothedWaveform[i] * SMOOTHING + newValue * (1 - SMOOTHING)
        }

        invalidate()
    }

    fun updateFft(data: ByteArray) {
        // PERFORMANCE: Réutiliser le buffer au lieu de copyOf() - zero allocation
        fftSize = minOf(data.size, fftBuffer.size)
        System.arraycopy(data, 0, fftBuffer, 0, fftSize)

        // PERFORMANCE: Smooth FFT data + detect bass
        val size = minOf(fftSize - 1, smoothedFft.size)
        var bassSum = 0f

        for (i in 0 until size) {
            val magnitude = abs(fftBuffer[i + 1].toInt()) / 128f
            smoothedFft[i] = smoothedFft[i] * SMOOTHING + magnitude * (1 - SMOOTHING)

            // Accumuler les basses (premiers bins FFT)
            if (i < 8) {
                bassSum += smoothedFft[i]
            }
        }

        // Détection de beat adaptative (historique glissant)
        lastBassLevel = bassLevel
        bassLevel = bassSum / 8f
        bassHistory[bassHistoryIndex] = bassLevel
        bassHistoryIndex = (bassHistoryIndex + 1) % bassHistory.size
        var bassAvg = 0f
        for (v in bassHistory) bassAvg += v
        bassAvg /= bassHistory.size
        if (beatCooldown > 0) beatCooldown--
        beatDetected = beatCooldown == 0 &&
                bassLevel > bassAvg * 1.4f &&
                bassLevel > 0.3f &&
                bassLevel > lastBassLevel * 1.15f
        if (beatDetected) beatCooldown = 4

        invalidate()
    }

    fun clear() {
        // Reset buffers
        waveformSize = 0
        fftSize = 0
        waveformBuffer.fill(0)
        fftBuffer.fill(0)
        // Reset smoothed data
        smoothedFft.fill(0f)
        smoothedWaveform.fill(0.5f)
        bassLevel = 0f
        lastBassLevel = 0f
        invalidate()
    }

    // Mode fullscreen (pour les modes centraux quand pochette cachée)
    var isFullscreen: Boolean = false
        set(value) {
            if (field != value) {
                if (!value) {
                    snowflakes.clear()       // retour bande → flocons disparus
                    snowLastFrameTime = 0L   // reset du timer pour le prochain passage
                } else {
                    if (!snowBitmapsReady) initSnowBitmaps()
                    snowLastFrameTime = 0L   // évite un delta géant à la première frame
                    prewarmSnowflakes()
                }
            }
            field = value
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // PERFORMANCE: Initialiser les shaders uniquement quand les dimensions changent
        cachedWidth = w
        cachedHeight = h
        initShaders()
    }

    /**
     * PERFORMANCE: Initialise tous les shaders réutilisables.
     * Appelé uniquement dans onSizeChanged() - zero allocation dans onDraw().
     */
    private fun initShaders() {
        if (cachedWidth == 0 || cachedHeight == 0) return

        val centerX = cachedWidth / 2f
        val centerY = cachedHeight / 2f
        val maxDistance = sqrt((cachedWidth.toFloat() * cachedWidth + cachedHeight.toFloat() * cachedHeight).toDouble()).toFloat() / 2f

        // Tunnel shaders pour particules
        tunnelShader = RadialGradient(
            centerX, centerY, maxDistance,
            intArrayOf(
                Color.argb(40, 80, 40, 120),
                Color.argb(20, 40, 15, 60),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )

        tunnelShaderMono = RadialGradient(
            centerX, centerY, maxDistance,
            intArrayOf(
                Color.argb(40, 70, 70, 70),
                Color.argb(20, 40, 40, 40),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )

        // Blob background shader
        val maxRadius = min(cachedWidth, cachedHeight) / 2f * 0.92f
        blobBackgroundShader = RadialGradient(
            centerX, centerY, maxRadius,
            intArrayOf(
                Color.argb(255, 20, 20, 30),
                Color.argb(255, 10, 10, 15),
                Color.argb(255, 5, 5, 8)
            ),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )

        // Pool de shaders pour traînées de particules (réutilisés en rotation)
        // Les coordonnées seront mises à jour dynamiquement mais le shader reste alloué
        for (i in particleTrailShaders.indices) {
            particleTrailShaders[i] = LinearGradient(
                0f, 0f, 100f, 100f,  // Coordonnées temporaires
                Color.TRANSPARENT,
                Color.WHITE,
                Shader.TileMode.CLAMP
            )
        }

        // Réinitialiser le mesh Delaunay si le mode est actif (dimensions changées)
        if (mode == VisualizationMode.DELAUNAY_MESH || mode == VisualizationMode.DELAUNAY_GRAYSCALE) initDelaunayMesh()
        // Réinitialiser le pavage de Penrose si le mode est actif (dimensions changées)
        if (mode == VisualizationMode.PENROSE_RHOMBUS) initPenroseRhombuses()
        // Réinitialiser le pavage de Penrose géométrique si le mode est actif
        if (mode == VisualizationMode.PENROSE_TRUE) initPenroseTrueRhombuses()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // PERFORMANCE: Arrêter l'animation en mode NONE (zero allocation, zero CPU)
        if (mode == VisualizationMode.NONE) {
            return
        }

        // Delta-time : compense les variations de framerate (1.0 = 60fps nominal)
        val now = System.nanoTime()
        deltaTime = if (lastFrameTimeNanos > 0L) {
            ((now - lastFrameTimeNanos) / 16_666_667f).coerceIn(0.5f, 3f)
        } else 1f
        lastFrameTimeNanos = now

        when (mode) {
            VisualizationMode.NONE -> {
                // Ne jamais arriver ici
            }
            // Modes plats (1-5)
            VisualizationMode.BARS -> drawBars(canvas)
            VisualizationMode.WAVE -> drawWave(canvas)
            VisualizationMode.MIRROR -> drawMirror(canvas)
            VisualizationMode.SPECTRUM -> drawSpectrum(canvas)
            VisualizationMode.FIRE -> drawFire(canvas)
            // Modes centraux (6-9)
            VisualizationMode.CIRCLE -> drawCircle(canvas)
            VisualizationMode.PARTICLES -> drawParticles(canvas)
            VisualizationMode.RADIAL -> drawRadial(canvas)
            VisualizationMode.BLOB -> drawBlob(canvas)
            VisualizationMode.PARTICLES_MONO -> drawParticlesMono(canvas)
            VisualizationMode.KALEIDOSCOPE -> drawKaleidoscope(canvas)
            VisualizationMode.BOIDS -> drawBoids(canvas)
            VisualizationMode.JULIA -> drawJulia(canvas)
            VisualizationMode.MANDELBROT_ZOOM -> drawMandelbrotZoom(canvas)
            VisualizationMode.JULIA_GRAYSCALE -> drawJuliaGrayscale(canvas)
            VisualizationMode.DELAUNAY_MESH -> drawDelaunayMesh(canvas)
            VisualizationMode.DELAUNAY_GRAYSCALE -> drawDelaunayMesh(canvas, grayscale = true)
            VisualizationMode.VORONOI -> drawVoronoi(canvas)
            VisualizationMode.VORONOI_GRAYSCALE -> drawVoronoi(canvas, grayscale = true)
            VisualizationMode.PENROSE_RHOMBUS -> drawPenroseRhombus(canvas)
            VisualizationMode.PENROSE_TRUE -> drawPenroseTrue(canvas)
        }

        // Continue animation - PAS DE RESET pour éviter les lags
        animationPhase += animationSpeed
        // Pas de reset, laisse aller à l'infini

        // Update color phase for psychedelic effect
        colorPhase = (colorPhase + colorSpeed) % 360f

        postInvalidateDelayed(16) // ~60fps
    }

    private val hsvBar = floatArrayOf(0f, 0.8f, 1f)  // For bar color cycling

    private fun drawBars(canvas: Canvas) {
        val barCount = 32
        val barWidth = width.toFloat() / barCount * 0.7f
        val barSpacing = width.toFloat() / barCount * 0.3f
        val maxHeight = height.toFloat() * 0.9f

        val data = if (fftSize > 0) fftBuffer else null

        for (i in 0 until barCount) {
            val barHeight = if (data != null && data.size > i + 1) {
                // Use FFT data
                val magnitude = Math.abs(data[i + 1].toInt()) / 128f
                magnitude * maxHeight
            } else {
                // Animated idle state
                val phase = animationPhase + (i.toFloat() / barCount) * Math.PI.toFloat() * 2f
                ((sin(phase.toDouble()).toFloat() + 1f) / 2f * 0.3f + 0.1f) * maxHeight
            }

            val left = i * (barWidth + barSpacing) + barSpacing / 2
            val top = height - barHeight
            val right = left + barWidth
            val bottom = height.toFloat()

            // Psychedelic color effect - each bar has a different hue based on position + phase
            val barHue = (colorPhase + (i.toFloat() / barCount) * 360f) % 360f
            hsvBar[0] = barHue
            barPaint.color = Color.HSVToColor(hsvBar)

            // Gradient effect based on height
            val alpha = (150 + (barHeight / maxHeight * 105)).toInt().coerceIn(150, 255)
            barPaint.alpha = alpha

            canvas.drawRoundRect(
                left, top, right, bottom,
                barWidth / 2, barWidth / 2,
                barPaint
            )
        }

        barPaint.alpha = 255
    }

    private fun drawWave(canvas: Canvas) {
        wavePath.reset()

        val data = if (waveformSize > 0) waveformBuffer else null
        val centerY = height / 2f
        val amplitude = height / 2f * 0.8f

        if (data != null && data.isNotEmpty()) {
            val step = data.size.toFloat() / width
            wavePath.moveTo(0f, centerY)

            for (x in 0 until width) {
                val dataIndex = (x * step).toInt().coerceIn(0, data.size - 1)
                val value = (data[dataIndex].toInt() + 128) / 256f
                val y = centerY - (value - 0.5f) * amplitude * 2
                wavePath.lineTo(x.toFloat(), y)
            }
        } else {
            // Animated idle state
            wavePath.moveTo(0f, centerY)
            for (x in 0 until width step 2) {
                val phase = animationPhase + (x.toFloat() / width) * Math.PI.toFloat() * 4f
                val y = centerY + sin(phase.toDouble()).toFloat() * amplitude * 0.3f
                wavePath.lineTo(x.toFloat(), y)
            }
        }

        canvas.drawPath(wavePath, wavePaint)
    }

    /** Construit un path fermé lissé via conversion Catmull-Rom → cubique Bézier */
    private fun buildSmoothClosedPath(path: Path, px: FloatArray, py: FloatArray, n: Int) {
        path.moveTo(px[0], py[0])
        for (i in 0 until n) {
            val i0 = (i - 1 + n) % n
            val i2 = (i + 1) % n
            val i3 = (i + 2) % n
            val cp1x = px[i] + (px[i2] - px[i0]) / 6f
            val cp1y = py[i] + (py[i2] - py[i0]) / 6f
            val cp2x = px[i2] - (px[i3] - px[i]) / 6f
            val cp2y = py[i2] - (py[i3] - py[i]) / 6f
            path.cubicTo(cp1x, cp1y, cp2x, cp2y, px[i2], py[i2])
        }
    }

    private fun drawCircle(canvas: Canvas) {
        // Update colors with psychedelic cycling effect
        hsvLeft[0] = colorPhase
        hsvRight[0] = (colorPhase + 150f) % 360f
        val leftColor = Color.HSVToColor(hsvLeft)
        val rightColor = Color.HSVToColor(hsvRight)
        circleLeftPaint.color = leftColor
        circleRightPaint.color = rightColor

        val spacing = width * 0.05f
        val circleWidth = (width - spacing) / 2f
        val centerLeftX = circleWidth / 2f
        val centerRightX = width - circleWidth / 2f
        val centerY = height / 2f
        val baseRadius = min(circleWidth, height.toFloat()) / 2f * 0.56f

        val data = if (fftSize > 0) fftBuffer else null
        val points = 48

        // 3 couches concentriques (inner → outer) avec lissage cubique
        for (layer in 0..2) {
            val layerRadius = baseRadius * circleLayerScales[layer]
            val fftScale = circleLayerFftScales[layer]

            // --- Points du cercle gauche (bins FFT pairs) ---
            for (i in 0 until points) {
                val angle = (i.toFloat() / points) * Math.PI.toFloat() * 2f - Math.PI.toFloat() / 2f
                val radiusOffset = if (data != null && data.size > i + 2) {
                    val dataIndex = (i * 2) % (data.size - 2) + 1
                    val magnitude = Math.abs(data[dataIndex].toInt()) / 128f
                    magnitude * layerRadius * fftScale
                } else {
                    val phase = animationPhase + (i.toFloat() / points) * Math.PI.toFloat() * 4f
                    ((sin(phase.toDouble()).toFloat() + 1f) / 2f * 0.15f) * layerRadius
                }
                val radius = layerRadius + radiusOffset
                circlePointsX[i] = centerLeftX + cos(angle.toDouble()).toFloat() * radius
                circlePointsY[i] = centerY + sin(angle.toDouble()).toFloat() * radius
            }

            circlePathLeft.reset()
            buildSmoothClosedPath(circlePathLeft, circlePointsX, circlePointsY, points)

            // Remplissage semi-transparent
            circleFillPaint.color = leftColor
            circleFillPaint.alpha = circleLayerFillAlphas[layer]
            canvas.drawPath(circlePathLeft, circleFillPaint)

            // Contour (sauf couche intérieure)
            if (circleLayerStrokeAlphas[layer] > 0) {
                circleLeftPaint.alpha = circleLayerStrokeAlphas[layer]
                circleLeftPaint.strokeWidth = if (layer == 2) 3f else 1.5f
                canvas.drawPath(circlePathLeft, circleLeftPaint)
            }

            // --- Points du cercle droit (bins FFT impairs) ---
            for (i in 0 until points) {
                val angle = (i.toFloat() / points) * Math.PI.toFloat() * 2f - Math.PI.toFloat() / 2f
                val radiusOffset = if (data != null && data.size > i + 3) {
                    val dataIndex = (i * 2 + 1) % (data.size - 2) + 2
                    val magnitude = Math.abs(data[dataIndex].toInt()) / 128f
                    magnitude * layerRadius * fftScale
                } else {
                    val phase = animationPhase + (i.toFloat() / points) * Math.PI.toFloat() * 4f + Math.PI.toFloat() / 4f
                    ((sin(phase.toDouble()).toFloat() + 1f) / 2f * 0.15f) * layerRadius
                }
                val radius = layerRadius + radiusOffset
                circlePointsX[i] = centerRightX + cos(angle.toDouble()).toFloat() * radius
                circlePointsY[i] = centerY + sin(angle.toDouble()).toFloat() * radius
            }

            circlePathRight.reset()
            buildSmoothClosedPath(circlePathRight, circlePointsX, circlePointsY, points)

            circleFillPaint.color = rightColor
            circleFillPaint.alpha = circleLayerFillAlphas[layer]
            canvas.drawPath(circlePathRight, circleFillPaint)

            if (circleLayerStrokeAlphas[layer] > 0) {
                circleRightPaint.alpha = circleLayerStrokeAlphas[layer]
                circleRightPaint.strokeWidth = if (layer == 2) 3f else 1.5f
                canvas.drawPath(circlePathRight, circleRightPaint)
            }
        }
    }

    private fun drawMirror(canvas: Canvas) {
        val barCount = MIRROR_BAR_COUNT
        val totalWidth = width.toFloat()
        // Barres plus fines et plus serrées pour un look pro
        val barWidth = totalWidth / barCount * 0.75f
        val gap = totalWidth / barCount * 0.25f
        val maxHeight = height.toFloat() * 0.45f
        val minBarHeight = 1f
        val centerY = height / 2f

        for (i in 0 until barCount) {
            val barHeight = if (smoothedFft.isNotEmpty() && fftSize > 0) {
                // Mapping logarithmique pour mieux répartir les fréquences
                val logIndex = (Math.pow((i + 1).toDouble() / barCount, 0.6) * (smoothedFft.size - 1)).toInt()
                val fftIndex = logIndex.coerceIn(0, smoothedFft.size - 1)

                // Moyenne avec les voisins pour lisser
                val value = if (fftIndex > 0 && fftIndex < smoothedFft.size - 1) {
                    (smoothedFft[fftIndex - 1] + smoothedFft[fftIndex] * 2 + smoothedFft[fftIndex + 1]) / 4f
                } else {
                    smoothedFft[fftIndex]
                }

                // Boost + échelle pour amplifier les petites valeurs
                val boosted = value * 4f
                val scaled = Math.pow(boosted.toDouble().coerceAtMost(1.0), 0.6).toFloat()

                (scaled * maxHeight).coerceIn(minBarHeight, maxHeight)
            } else {
                // Animation idle
                val phase = animationPhase + (i.toFloat() / barCount) * Math.PI.toFloat() * 4f
                ((sin(phase.toDouble()).toFloat() + 1f) / 2f * 0.3f + 0.1f) * maxHeight
            }

            val left = i * (barWidth + gap) + gap / 2
            val right = left + barWidth

            // Gris-blanc avec alpha basé sur la hauteur
            val intensity = (barHeight / maxHeight).coerceIn(0f, 1f)
            val grayValue = (200 + intensity * 55).toInt().coerceIn(200, 255)
            val alpha = (180 + intensity * 75).toInt().coerceIn(180, 255)
            mirrorPaint.color = Color.argb(alpha, grayValue, grayValue, grayValue)

            // Barre vers le haut depuis le centre
            canvas.drawRect(
                left, centerY - barHeight - 1f,
                right, centerY - 1f,
                mirrorPaint
            )

            // Barre vers le bas depuis le centre (miroir)
            canvas.drawRect(
                left, centerY + 1f,
                right, centerY + barHeight + 1f,
                mirrorPaint
            )
        }
    }

    private fun drawSpectrum(canvas: Canvas) {
        spectrumPath.reset()

        val data = if (fftSize > 0) fftBuffer else null
        val barCount = 32
        val maxHeight = height.toFloat() * 0.85f

        // Build smooth curve
        spectrumPath.moveTo(0f, height.toFloat())

        var lastY = height.toFloat()

        for (i in 0 until barCount) {
            val barHeight = if (data != null && data.size > i + 1) {
                val magnitude = Math.abs(data[i + 1].toInt()) / 128f
                magnitude * maxHeight
            } else {
                val phase = animationPhase + (i.toFloat() / barCount) * Math.PI.toFloat() * 2f
                ((sin(phase.toDouble()).toFloat() + 1f) / 2f * 0.25f + 0.05f) * maxHeight
            }

            val x = (i.toFloat() / (barCount - 1)) * width
            val y = height - barHeight

            if (i == 0) {
                spectrumPath.lineTo(x, y)
            } else {
                // Smooth curve using quadratic bezier
                val prevX = ((i - 1).toFloat() / (barCount - 1)) * width
                val midX = (prevX + x) / 2
                spectrumPath.quadTo(prevX, lastY, midX, y)
            }
            lastY = y
        }

        spectrumPath.lineTo(width.toFloat(), height.toFloat())
        spectrumPath.close()

        // PERFORMANCE: Remplissage avec couleur simple au lieu de gradient (zero allocation)
        // L'effet psychédélique vient déjà du colorPhase qui change
        hsvBar[0] = colorPhase
        hsvBar[1] = 0.7f
        hsvBar[2] = 0.9f
        spectrumFillPaint.color = Color.HSVToColor(150, hsvBar)
        canvas.drawPath(spectrumPath, spectrumFillPaint)

        // Draw outline
        hsvBar[0] = colorPhase
        hsvBar[1] = 0.8f
        hsvBar[2] = 1f
        wavePaint.color = Color.HSVToColor(hsvBar)
        wavePaint.strokeWidth = 3f
        canvas.drawPath(spectrumPath, wavePaint)
    }

    // ===========================================
    // MODES AVANCÉS
    // ===========================================

    /**
     * Mode PARTICLES - Starfield / Warp Speed
     * Style: voyage spatial, particules jaillissant du centre
     * Vitesse et nombre de particules réactifs à la musique
     */
    private fun drawParticles(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        // Utiliser toute la surface disponible (diagonale complète)
        val maxDistance = sqrt((width.toFloat() * width + height.toFloat() * height).toDouble()).toFloat() / 2f

        // Pre-warm : distribue les particules sur toute la zone dès le premier frame
        if (particlesNeedPrewarm && maxDistance > 0f) {
            repeat(PARTICLE_COUNT) {
                val dist = (Math.random() * maxDistance * 0.85f).toFloat()
                spawnStarParticle(centerX, centerY, dist)
            }
            particlesNeedPrewarm = false
        }

        // Calculer l'intensité audio globale
        val audioIntensity = if (smoothedFft.isNotEmpty() && fftSize > 0) {
            var sum = 0f
            for (i in 0 until minOf(32, smoothedFft.size)) {
                sum += smoothedFft[i]
            }
            (sum / 32f * 2.5f).coerceIn(0.1f, 1.2f)
        } else {
            0.3f + (sin(animationPhase.toDouble()).toFloat() + 1f) / 6f
        }

        // Détection de pics de volume (spectre complet, comme visu 11)
        smoothedVolume = smoothedVolume * 0.95f + audioIntensity * 0.05f
        val volumePeak = audioIntensity > smoothedVolume * 1.5f && audioIntensity > 0.3f
        if (volumePeak) beatEnvelope = 1f
        beatEnvelope *= 0.88f

        // Nombre de particules basé sur l'intensité + boost sur les beats
        // Plus de musique = plus de particules, beat = explosion de particules
        val beatMultiplier = if (beatDetected) 3f else 1f
        val spawnRate = ((1 + audioIntensity * 4) * beatMultiplier).toInt()
        repeat(spawnRate) {
            spawnStarParticle(centerX, centerY)
        }

        // PERFORMANCE: Fond subtil avec shader pré-alloué
        // On réutilise le shader, pas de nouvelle allocation
        blobPaint.shader = tunnelShader
        blobPaint.alpha = (20 + audioIntensity * 30).toInt().coerceIn(15, 50)
        canvas.drawCircle(centerX, centerY, maxDistance, blobPaint)
        blobPaint.shader = null
        blobPaint.alpha = 255

        // Compter les particules actives (pour ajuster le spawn)
        var activeParticles = 0

        // Update et draw de toutes les particules
        for (p in particles) {
            if (p.life <= 0f) continue
            activeParticles++

            // Calculer la distance du centre
            val dx = p.x - centerX
            val dy = p.y - centerY
            val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

            // Normaliser la direction
            val dirX = if (distance > 0.1f) dx / distance else 0f
            val dirY = if (distance > 0.1f) dy / distance else 0f

            // Vitesse strictement constante, compensée par delta-time
            val speed = p.vx * deltaTime

            // Update position (mouvement radial vers l'extérieur)
            p.x += dirX * speed
            p.y += dirY * speed

            // Réduire la vie quand sort de l'écran
            if (p.x < -30 || p.x > width + 30 || p.y < -30 || p.y > height + 30) {
                p.life = 0f
                continue
            }

            // La taille augmente avec la distance (effet perspective)
            val perspectiveSize = p.size * (0.3f + (distance / maxDistance) * 2.5f)

            // Alpha basé sur la distance (fade in au début, plein au milieu, fade out aux bords)
            val distanceRatio = distance / maxDistance
            val alpha = when {
                distanceRatio < 0.15f -> (distanceRatio / 0.15f * 255).toInt()
                distanceRatio > 0.75f -> ((1f - distanceRatio) / 0.25f * 255).toInt()
                else -> 255
            }.coerceIn(0, 255)

            // Couleur avec décalage basé sur l'angle pour effet arc-en-ciel spiral
            val angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
            hsvTemp[0] = (colorPhase + angle * 0.5f + distance * 0.3f) % 360f
            hsvTemp[1] = 0.75f
            hsvTemp[2] = 0.85f + audioIntensity * 0.15f

            // PERFORMANCE: Dessiner la traînée (motion blur) - optimisé
            // Traînée avec shader uniquement pour les grosses particules (plus visibles)
            // Petites particules = traînée simple sans shader (zero allocation)
            val trailLength = speed * 2.5f
            val trailStartX = p.x - dirX * trailLength
            val trailStartY = p.y - dirY * trailLength

            if (perspectiveSize > 4f) {
                // Grosse particule visible = traînée avec gradient (belle qualité)
                radialPaint.shader = LinearGradient(
                    trailStartX, trailStartY, p.x, p.y,
                    Color.TRANSPARENT,
                    Color.HSVToColor(alpha, hsvTemp),
                    Shader.TileMode.CLAMP
                )
                radialPaint.strokeWidth = perspectiveSize * 0.7f
                radialPaint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(trailStartX, trailStartY, p.x, p.y, radialPaint)
                radialPaint.shader = null
            } else {
                // Petite particule = traînée simple (zero allocation)
                radialPaint.color = Color.HSVToColor(alpha / 2, hsvTemp)
                radialPaint.strokeWidth = perspectiveSize * 0.7f
                radialPaint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(trailStartX, trailStartY, p.x, p.y, radialPaint)
            }

            // Dessiner le coeur de la particule (plus brillant)
            hsvTemp[2] = 1f
            particlePaint.color = Color.HSVToColor(alpha, hsvTemp)
            canvas.drawCircle(p.x, p.y, perspectiveSize * 0.5f, particlePaint)

            // Glow sur les grosses particules
            if (perspectiveSize > 5f) {
                particlePaint.color = Color.HSVToColor(alpha / 4, hsvTemp)
                canvas.drawCircle(p.x, p.y, perspectiveSize * 1.1f, particlePaint)
            }
        }

        // === MINI BLOB AU CENTRE ===
        // Taille du blob pilotée par pics de volume (pas les basses)
        val blobBaseSize = min(width, height) * 0.06f
        val blobPulse = 1f + beatEnvelope * 0.35f
        val blobSize = blobBaseSize * blobPulse

        // Dessiner quelques anneaux ondulants (comme le blob mais en mini)
        val ringCount = 4
        for (ring in 0 until ringCount) {
            val ringProgress = (ring + 1).toFloat() / ringCount
            val baseRingRadius = blobSize * ringProgress

            // Ondulation
            val ringPhase = animationPhase * 2f - ring * 0.4f
            val waveAmount = sin(ringPhase.toDouble()).toFloat() * audioIntensity * 0.3f

            // Couleur de l'anneau
            hsvTemp[0] = (colorPhase + ring * 20f) % 360f
            hsvTemp[1] = 0.5f
            hsvTemp[2] = 0.8f + waveAmount * 0.2f
            val ringAlpha = (180 - ring * 30).coerceIn(80, 180)

            blobOutlinePaint.color = Color.HSVToColor(ringAlpha, hsvTemp)
            blobOutlinePaint.strokeWidth = 2f + audioIntensity * 2f

            // Dessiner l'anneau déformé
            blobPath.reset()
            val points = 32
            for (i in 0 until points) {
                val a = (i * 360f / points)
                val angleRad = Math.toRadians(a.toDouble())

                // Déformation basée sur FFT
                val fftDeform = if (smoothedFft.isNotEmpty() && fftSize > 0) {
                    val fftIdx = (i * smoothedFft.size / points).coerceIn(0, smoothedFft.size - 1)
                    smoothedFft[fftIdx] * blobSize * 0.2f
                } else {
                    0f
                }

                val radius = baseRingRadius * (1f + waveAmount) + fftDeform
                val x = centerX + cos(angleRad).toFloat() * radius
                val y = centerY + sin(angleRad).toFloat() * radius

                if (i == 0) blobPath.moveTo(x, y) else blobPath.lineTo(x, y)
            }
            blobPath.close()
            canvas.drawPath(blobPath, blobOutlinePaint)
        }

        // PERFORMANCE: Coeur central brillant avec glow optimisé
        // Glow par cercles multiples au lieu de RadialGradient (zero allocation)
        val coreSize = blobSize * (0.25f + beatEnvelope * 0.15f)
        hsvTemp[0] = colorPhase
        hsvTemp[1] = 0.2f
        hsvTemp[2] = 1f

        // Glow avec 3 cercles concentriques — alpha boost sur le beat
        val beatAlphaBoost = (beatEnvelope * 40).toInt()
        val coreColor = Color.HSVToColor(hsvTemp)
        blobPaint.color = coreColor
        blobPaint.alpha = 50 + beatAlphaBoost
        canvas.drawCircle(centerX, centerY, coreSize * 2.5f, blobPaint)
        blobPaint.alpha = 100 + beatAlphaBoost
        canvas.drawCircle(centerX, centerY, coreSize * 1.8f, blobPaint)
        blobPaint.alpha = (150 + beatAlphaBoost).coerceAtMost(255)
        canvas.drawCircle(centerX, centerY, coreSize * 1.2f, blobPaint)

        // Point central très brillant
        particlePaint.color = coreColor
        canvas.drawCircle(centerX, centerY, coreSize, particlePaint)
    }

    /**
     * Mode PARTICLES_MONO - Variante niveaux de gris du mode particules.
     * Style: starfield monochrome avec blob central réduit.
     */
    private fun drawParticlesMono(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val maxDistance = sqrt((width.toFloat() * width + height.toFloat() * height).toDouble()).toFloat() / 2f

        // Pre-warm : même distribution initiale que drawParticles
        if (particlesNeedPrewarm && maxDistance > 0f) {
            repeat(PARTICLE_COUNT) {
                val dist = (Math.random() * maxDistance * 0.85f).toFloat()
                spawnStarParticle(centerX, centerY, dist)
            }
            particlesNeedPrewarm = false
        }

        val audioIntensity = if (smoothedFft.isNotEmpty() && fftSize > 0) {
            var sum = 0f
            for (i in 0 until minOf(32, smoothedFft.size)) {
                sum += smoothedFft[i]
            }
            (sum / 32f * 2.5f).coerceIn(0.1f, 1.2f)
        } else {
            0.3f + (sin(animationPhase.toDouble()).toFloat() + 1f) / 6f
        }

        val beatMultiplier = if (beatDetected) 3f else 1f
        val spawnRate = ((1 + audioIntensity * 4) * beatMultiplier).toInt()
        repeat(spawnRate) {
            spawnStarParticle(centerX, centerY)
        }

        // PERFORMANCE: Tunnel shader pré-alloué (mono)
        blobPaint.shader = tunnelShaderMono
        blobPaint.alpha = (20 + audioIntensity * 30).toInt().coerceIn(15, 50)
        canvas.drawCircle(centerX, centerY, maxDistance, blobPaint)
        blobPaint.shader = null
        blobPaint.alpha = 255

        for (p in particles) {
            if (p.life <= 0f) continue

            val dx = p.x - centerX
            val dy = p.y - centerY
            val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

            val dirX = if (distance > 0.1f) dx / distance else 0f
            val dirY = if (distance > 0.1f) dy / distance else 0f

            val speed = p.vx * deltaTime

            p.x += dirX * speed
            p.y += dirY * speed

            if (p.x < -30 || p.x > width + 30 || p.y < -30 || p.y > height + 30) {
                p.life = 0f
                continue
            }

            val perspectiveSize = p.size * (0.3f + (distance / maxDistance) * 2.5f)
            val distanceRatio = distance / maxDistance
            val alpha = when {
                distanceRatio < 0.15f -> (distanceRatio / 0.15f * 255).toInt()
                distanceRatio > 0.75f -> ((1f - distanceRatio) / 0.25f * 255).toInt()
                else -> 255
            }.coerceIn(0, 255)

            val grayValue = (180 + (1f - distanceRatio) * 55 + audioIntensity * 20).toInt().coerceIn(140, 255)

            // PERFORMANCE: Traînée optimisée (shader uniquement pour grosses particules)
            val trailLength = speed * 2.5f
            val trailStartX = p.x - dirX * trailLength
            val trailStartY = p.y - dirY * trailLength

            if (perspectiveSize > 4f) {
                // Grosse particule = traînée avec gradient
                radialPaint.shader = LinearGradient(
                    trailStartX, trailStartY, p.x, p.y,
                    Color.TRANSPARENT,
                    Color.argb(alpha, grayValue, grayValue, grayValue),
                    Shader.TileMode.CLAMP
                )
                radialPaint.strokeWidth = perspectiveSize * 0.7f
                radialPaint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(trailStartX, trailStartY, p.x, p.y, radialPaint)
                radialPaint.shader = null
            } else {
                // Petite particule = traînée simple (zero allocation)
                radialPaint.color = Color.argb(alpha / 2, grayValue, grayValue, grayValue)
                radialPaint.strokeWidth = perspectiveSize * 0.7f
                radialPaint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(trailStartX, trailStartY, p.x, p.y, radialPaint)
            }

            particlePaint.color = Color.argb(alpha, grayValue, grayValue, grayValue)
            canvas.drawCircle(p.x, p.y, perspectiveSize * 0.5f, particlePaint)

            if (perspectiveSize > 5f) {
                particlePaint.color = Color.argb(alpha / 4, grayValue, grayValue, grayValue)
                canvas.drawCircle(p.x, p.y, perspectiveSize * 1.1f, particlePaint)
            }
        }

        val blobBaseSize = min(width, height) * 0.04f
        val blobPulse = 1f + audioIntensity * 0.4f + (if (beatDetected) 0.3f else 0f)
        val blobSize = blobBaseSize * blobPulse

        val ringCount = 4
        for (ring in 0 until ringCount) {
            val ringProgress = (ring + 1).toFloat() / ringCount
            val baseRingRadius = blobSize * ringProgress

            val ringPhase = animationPhase * 2f - ring * 0.4f
            val waveAmount = sin(ringPhase.toDouble()).toFloat() * audioIntensity * 0.3f

            val ringAlpha = (180 - ring * 30).coerceIn(80, 180)
            val ringGray = (200 + waveAmount * 40 + audioIntensity * 20).toInt().coerceIn(160, 255)

            blobOutlinePaint.color = Color.argb(ringAlpha, ringGray, ringGray, ringGray)
            blobOutlinePaint.strokeWidth = 2f + audioIntensity * 2f

            blobPath.reset()
            val points = 32
            for (i in 0 until points) {
                val angleRad = Math.toRadians((i * 360f / points).toDouble())
                val fftDeform = if (smoothedFft.isNotEmpty() && fftSize > 0) {
                    val fftIdx = (i * smoothedFft.size / points).coerceIn(0, smoothedFft.size - 1)
                    smoothedFft[fftIdx] * blobSize * 0.2f
                } else {
                    0f
                }

                val radius = baseRingRadius * (1f + waveAmount) + fftDeform
                val x = centerX + cos(angleRad).toFloat() * radius
                val y = centerY + sin(angleRad).toFloat() * radius

                if (i == 0) blobPath.moveTo(x, y) else blobPath.lineTo(x, y)
            }
            blobPath.close()
            canvas.drawPath(blobPath, blobOutlinePaint)
        }

        // PERFORMANCE: Glow optimisé avec cercles multiples (zero allocation)
        val coreSize = blobSize * 0.25f + bassLevel * blobSize * 0.15f
        val coreGray = (220 + audioIntensity * 30).toInt().coerceIn(180, 255)
        val coreColor = Color.argb(255, coreGray, coreGray, coreGray)

        // Glow avec cercles concentriques (zero allocation)
        blobPaint.color = coreColor
        blobPaint.alpha = 50
        canvas.drawCircle(centerX, centerY, coreSize * 2.5f, blobPaint)
        blobPaint.alpha = 100
        canvas.drawCircle(centerX, centerY, coreSize * 1.8f, blobPaint)
        blobPaint.alpha = 150
        canvas.drawCircle(centerX, centerY, coreSize * 1.2f, blobPaint)

        particlePaint.color = coreColor
        canvas.drawCircle(centerX, centerY, coreSize, particlePaint)
    }

    private fun spawnStarParticle(centerX: Float, centerY: Float, startDist: Float = -1f) {
        // Trouver une particule morte à réutiliser
        for (p in particles) {
            if (p.life <= 0f) {
                val spawnRadius = if (startDist < 0f) 8f + (Math.random() * 20).toFloat() else startDist
                val angle = (Math.random() * 360).toFloat()
                p.x = centerX + fastCos(angle) * spawnRadius
                p.y = centerY + fastSin(angle) * spawnRadius
                p.size = 2f + (Math.random() * 6).toFloat()
                p.vx = 1.0f + (Math.random() * 0.4f).toFloat()
                p.vy = 0f
                p.life = 1f
                p.hue = (colorPhase + (Math.random() * 120 - 60)).toFloat()
                break
            }
        }
    }

    /**
     * Mode RADIAL - Rayons depuis le centre style DJ/club
     * Style: néons pulsants, stroboscope moderne
     */
    private fun drawRadial(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = sqrt((centerX * centerX + centerY * centerY).toDouble()).toFloat()

        val data = if (fftSize > 0) fftBuffer else null
        val rayCount = RADIAL_RAYS

        // Fond avec cercles concentriques subtils
        for (ring in 1..4) {
            val ringRadius = maxRadius * (ring / 5f)
            innerCirclePaint.color = Color.argb(20, 255, 255, 255)
            canvas.drawCircle(centerX, centerY, ringRadius, innerCirclePaint)
        }

        // Rayons principaux
        for (i in 0 until rayCount) {
            val baseAngle = (i * 360f / rayCount + colorPhase * 0.5f) % 360f

            val magnitude = if (data != null && data.size > 1) {
                val dataIndex = ((i % (data.size - 2)) + 1).coerceIn(1, data.size - 1)
                smoothedFft[dataIndex.coerceIn(0, smoothedFft.size - 1)]
            } else {
                val phase = animationPhase + (i.toFloat() / rayCount) * Math.PI.toFloat() * 4
                (fastSin(phase * 57.3f) + 1f) / 2f * 0.5f
            }

            val length = maxRadius * (0.2f + magnitude * 0.8f)

            // Calcul des points
            val startX = centerX
            val startY = centerY
            val endX = centerX + fastCos(baseAngle) * length
            val endY = centerY + fastSin(baseAngle) * length

            // Couleur avec gradient le long du rayon
            hsvTemp[0] = (baseAngle + colorPhase) % 360f
            hsvTemp[1] = 0.8f
            hsvTemp[2] = 0.5f + magnitude * 0.5f

            // Épaisseur variable selon l'intensité
            radialPaint.strokeWidth = 2f + magnitude * 6f
            radialPaint.color = Color.HSVToColor((180 + magnitude * 75).toInt(), hsvTemp)

            canvas.drawLine(startX, startY, endX, endY, radialPaint)

            // Pointe brillante
            if (magnitude > 0.3f) {
                particlePaint.color = Color.HSVToColor(255, hsvTemp)
                canvas.drawCircle(endX, endY, 3f + magnitude * 4f, particlePaint)
            }
        }

        // Cercle central pulsant
        val centerPulse = if (beatDetected) 1.5f else 1f + bassLevel * 0.3f
        val centerRadius = 20f * centerPulse

        hsvTemp[0] = colorPhase
        hsvTemp[1] = 0.6f
        hsvTemp[2] = 1f
        blobPaint.color = Color.HSVToColor(200, hsvTemp)
        canvas.drawCircle(centerX, centerY, centerRadius, blobPaint)

        // Glow du centre
        blobPaint.color = Color.HSVToColor(50, hsvTemp)
        canvas.drawCircle(centerX, centerY, centerRadius * 2f, blobPaint)
    }

    /**
     * Mode BLOB - Liquide non-newtonien sur haut-parleur
     * Style: vue du dessus, ondulations concentriques qui rebondissent
     */
    private fun drawBlob(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        // En fullscreen, utiliser plus d'espace (92% au lieu de 85%)
        val maxRadius = if (isFullscreen) {
            min(width, height) / 2f * 0.92f
        } else {
            min(width, height) / 2f * 0.85f
        }

        // PERFORMANCE: Fond sombre avec shader pré-alloué
        blobPaint.shader = blobBackgroundShader
        canvas.drawCircle(centerX, centerY, maxRadius, blobPaint)
        blobPaint.shader = null

        // Calculer l'amplitude globale basée sur les basses
        val globalAmplitude = if (smoothedFft.isNotEmpty() && fftSize > 0) {
            // Moyenne des basses (premiers bins FFT)
            var bassSum = 0f
            for (i in 0 until minOf(16, smoothedFft.size)) {
                bassSum += smoothedFft[i]
            }
            (bassSum / 16f * 3f).coerceIn(0f, 1f)
        } else {
            (sin((animationPhase * 2).toDouble()).toFloat() + 1f) / 2f * 0.5f
        }

        // Dessiner plusieurs anneaux concentriques qui ondulent
        val ringCount = 8
        val waveSpeed = animationPhase * 3f

        for (ring in 0 until ringCount) {
            val ringProgress = (ring + 1).toFloat() / ringCount
            val baseRingRadius = maxRadius * ringProgress * 0.85f

            // Chaque anneau a une phase différente pour créer l'effet d'ondulation
            val ringPhase = waveSpeed - ring * 0.5f

            // L'amplitude diminue vers l'extérieur
            val ringAmplitude = globalAmplitude * (1f - ringProgress * 0.5f)

            // Hauteur simulée de l'anneau (pour l'effet 3D)
            val waveHeight = sin(ringPhase.toDouble()).toFloat() * ringAmplitude

            // Couleur basée sur la hauteur (plus clair quand "haut")
            val brightness = 0.3f + (waveHeight + 1f) / 2f * 0.5f
            hsvTemp[0] = (colorPhase + ring * 15f) % 360f
            hsvTemp[1] = 0.6f - waveHeight * 0.2f
            hsvTemp[2] = brightness + ringAmplitude * 0.3f

            val ringColor = Color.HSVToColor((180 + waveHeight * 75).toInt().coerceIn(100, 255), hsvTemp)

            // Dessiner l'anneau déformé
            blobPath.reset()
            val points = 64
            for (i in 0 until points) {
                val angle = (i * 360f / points)
                val angleRad = Math.toRadians(angle.toDouble())

                // Déformation basée sur FFT pour chaque angle
                val fftDeform = if (smoothedFft.isNotEmpty() && fftSize > 0) {
                    val fftIdx = (i * smoothedFft.size / points).coerceIn(0, smoothedFft.size - 1)
                    smoothedFft[fftIdx] * maxRadius * 0.15f * (1f - ringProgress * 0.5f)
                } else {
                    0f
                }

                // Perturbation ondulante
                val wavePerturbation = sin((angle * 3f + ringPhase * 60f).toDouble() * Math.PI / 180).toFloat() *
                        maxRadius * 0.05f * ringAmplitude

                val radius = baseRingRadius + fftDeform + wavePerturbation
                val x = centerX + cos(angleRad).toFloat() * radius
                val y = centerY + sin(angleRad).toFloat() * radius

                if (i == 0) {
                    blobPath.moveTo(x, y)
                } else {
                    blobPath.lineTo(x, y)
                }
            }
            blobPath.close()

            // Style de l'anneau
            blobOutlinePaint.color = ringColor
            blobOutlinePaint.strokeWidth = 3f + ringAmplitude * 4f
            blobOutlinePaint.alpha = (200 - ring * 15).coerceIn(80, 200)
            canvas.drawPath(blobPath, blobOutlinePaint)
        }

        // Point central qui pulse fortement avec les basses
        val centerPulse = 10f + globalAmplitude * 30f + (if (beatDetected) 20f else 0f)
        hsvTemp[0] = colorPhase
        hsvTemp[1] = 0.5f
        hsvTemp[2] = 1f
        blobPaint.color = Color.HSVToColor(220, hsvTemp)
        canvas.drawCircle(centerX, centerY, centerPulse, blobPaint)

        // Glow autour du centre
        blobPaint.color = Color.HSVToColor(60, hsvTemp)
        canvas.drawCircle(centerX, centerY, centerPulse * 2f, blobPaint)

        // Reflets de lumière (effet 3D)
        val highlightOffset = globalAmplitude * 15f
        particlePaint.color = Color.argb(60, 255, 255, 255)
        canvas.drawCircle(centerX - highlightOffset, centerY - highlightOffset, 8f + globalAmplitude * 10f, particlePaint)
    }

    /**
     * Mode FIRE - Effet de flammes avec base bleue chalumeau
     * Style: feu réactif à la musique, flammes intenses = base bleue
     */
    private fun drawFire(canvas: Canvas) {
        val columnWidth = width.toFloat() / FIRE_COLUMNS
        // En fullscreen le feu est limité au tiers inférieur pour laisser place aux flocons
        val maxHeight      = if (isFullscreen) height * 0.35f else height * 0.85f
        val minFlameHeight = if (isFullscreen) height * 0.05f else height * 0.15f

        // Mettre à jour les hauteurs des flammes
        for (i in 0 until FIRE_COLUMNS) {
            val targetHeight = if (fftSize > 0 && smoothedFft.isNotEmpty()) {
                // Mapping logarithmique pour mieux répartir les fréquences
                val logIndex = (Math.pow((i + 1).toDouble() / FIRE_COLUMNS, 0.7) * (smoothedFft.size - 1)).toInt()
                val fftIndex = logIndex.coerceIn(0, smoothedFft.size - 1)

                // Moyenne avec voisins pour lisser
                val value = if (fftIndex > 0 && fftIndex < smoothedFft.size - 1) {
                    (smoothedFft[fftIndex - 1] + smoothedFft[fftIndex] * 2 + smoothedFft[fftIndex + 1]) / 4f
                } else {
                    smoothedFft[fftIndex]
                }

                // Boost x5 + hauteur de base
                val boosted = (value * 5f).coerceAtMost(1f)
                minFlameHeight + boosted * (maxHeight - minFlameHeight)
            } else {
                // Animation idle
                val phase = animationPhase * 2 + (i.toFloat() / FIRE_COLUMNS) * Math.PI.toFloat() * 4
                minFlameHeight + (fastSin(phase * 57.3f) + 1f) / 2f * maxHeight * 0.4f
            }

            // Smooth transition + random flicker plus prononcé
            val flicker = (Math.random() * 0.2f - 0.1f).toFloat()
            fireHeights[i] = fireHeights[i] * 0.6f + (targetHeight * (1f + flicker)) * 0.4f
        }

        // Dessiner les flammes
        for (i in 0 until FIRE_COLUMNS) {
            val flameHeight = fireHeights[i].coerceAtLeast(minFlameHeight)
            val x = i * columnWidth + columnWidth / 2
            val bottom = height.toFloat()
            val top = bottom - flameHeight

            // PERFORMANCE: Dessiner la flamme en couches avec couleurs solides (zero allocation)
            // Au lieu de créer 40 gradients par frame, on utilise des couleurs solides superposées

            val flameIntensity = ((flameHeight - minFlameHeight) / (maxHeight - minFlameHeight)).coerceIn(0f, 1f)
            val baseWidth = columnWidth * 0.8f
            val sway = fastSin((animationPhase * 60f + i * 20f) % 360f) * columnWidth * 0.15f

            // Forme de flamme réutilisable
            blobPath.reset()
            blobPath.moveTo(x - baseWidth / 2, bottom)
            blobPath.quadTo(
                x - baseWidth / 3 + sway * 0.5f, bottom - flameHeight * 0.5f,
                x + sway, top
            )
            blobPath.quadTo(
                x + baseWidth / 3 + sway * 0.5f, bottom - flameHeight * 0.5f,
                x + baseWidth / 2, bottom
            )
            blobPath.close()

            // Dessiner en 4 couches pour simuler le gradient (effet similaire, zero allocation)
            // Couche 1 (base): Jaune/Bleu selon intensité
            if (flameIntensity > 0.5f) {
                val blueIntensity = ((flameIntensity - 0.5f) * 2f * 255).toInt()
                firePaint.color = Color.argb(255, 50 + blueIntensity / 3, 100 + blueIntensity / 2, 200 + blueIntensity / 4)
            } else {
                firePaint.color = Color.argb(255, 255, 220, 80)
            }
            canvas.drawPath(blobPath, firePaint)

            // Couche 2 (milieu-bas): Orange-jaune
            canvas.save()
            canvas.clipRect(x - baseWidth / 2, bottom - flameHeight * 0.7f, x + baseWidth / 2, bottom)
            firePaint.color = Color.argb(230, 255, 150, 30)
            canvas.drawPath(blobPath, firePaint)
            canvas.restore()

            // Couche 3 (milieu-haut): Orange-rouge
            canvas.save()
            canvas.clipRect(x - baseWidth / 2, top, x + baseWidth / 2, bottom - flameHeight * 0.4f)
            firePaint.color = Color.argb(200, 255, 80, 15)
            canvas.drawPath(blobPath, firePaint)
            canvas.restore()

            // Couche 4 (sommet): Rouge sombre avec fade
            canvas.save()
            canvas.clipRect(x - baseWidth / 2, top, x + baseWidth / 2, bottom - flameHeight * 0.7f)
            firePaint.color = Color.argb(120, 180, 30, 5)
            canvas.drawPath(blobPath, firePaint)
            canvas.restore()
        }

        firePaint.shader = null

        // Particules de braise qui montent - plus fréquentes !
        // Spawn régulier + extra sur les beats
        val emberSpawnChance = if (beatDetected) 0.8f else 0.25f
        if (Math.random() < emberSpawnChance) {
            // Spawn 1-3 braises selon l'intensité
            val spawnCount = if (beatDetected) 3 else 1
            repeat(spawnCount) {
                val col = (Math.random() * FIRE_COLUMNS).toInt().coerceIn(0, FIRE_COLUMNS - 1)
                val flameTop = height - fireHeights[col]
                // Spawn à différentes hauteurs dans la flamme
                val spawnY = flameTop + (Math.random() * fireHeights[col] * 0.3f).toFloat()
                spawnEmber(col * columnWidth + columnWidth / 2, spawnY)
            }
        }

        // Update et draw embers (réutilise le système de particules)
        for (p in particles) {
            if (p.life > 0f && p.vy < 0) {  // Les braises montent (vy négatif)
                p.y += p.vy
                p.x += p.vx
                p.life -= 0.025f  // Durent un peu plus longtemps
                p.vy += 0.04f  // Ralentissement progressif

                val alpha = (p.life * 220).toInt().coerceIn(0, 220)

                // Couleur braise: varie entre orange vif et rouge selon la vie
                val red = 255
                val green = (180 * p.life + 50).toInt().coerceIn(50, 200)
                val blue = (40 * p.life).toInt().coerceIn(0, 40)
                particlePaint.color = Color.argb(alpha, red, green, blue)

                // Taille qui diminue avec la vie
                val size = p.size * (0.5f + p.life * 0.5f)
                canvas.drawCircle(p.x, p.y, size, particlePaint)

                // Petit glow pour les grosses braises
                if (size > 2.5f) {
                    particlePaint.color = Color.argb(alpha / 3, red, green, blue)
                    canvas.drawCircle(p.x, p.y, size * 1.8f, particlePaint)
                }
            }
        }

        // PERFORMANCE: Glow au sol optimisé (rectangles avec alpha, zero allocation)
        val avgIntensity = fireHeights.average().toFloat() / maxHeight
        val glowBlue = if (avgIntensity > 0.4f) ((avgIntensity - 0.4f) * 100).toInt().coerceIn(0, 60) else 0
        val glowAlpha = (70 + avgIntensity * 40).toInt().coerceIn(70, 110)
        val glowColor = Color.argb(glowAlpha, 255, 120 - glowBlue, 20 + glowBlue * 2)

        // Glow en 3 couches pour simuler le gradient (zero allocation)
        firePaint.color = glowColor
        firePaint.alpha = glowAlpha
        canvas.drawRect(0f, height - 20f, width.toFloat(), height.toFloat(), firePaint)
        firePaint.alpha = glowAlpha * 2 / 3
        canvas.drawRect(0f, height - 40f, width.toFloat(), height.toFloat(), firePaint)
        firePaint.alpha = glowAlpha / 3
        canvas.drawRect(0f, height - 60f, width.toFloat(), height.toFloat(), firePaint)

        // === Flocons de neige (uniquement en fullscreen) ===
        if (isFullscreen) {
            val h = height.toFloat()
            val w = width.toFloat()
            val meltStart = h * 0.48f   // début de la fonte (moitié écran)
            val meltEnd   = h * 0.73f   // complètement fondu avant le feu

            // Delta-time : normalise le mouvement à 16ms (60fps) quelle que soit la cadence réelle
            val nowMs = android.os.SystemClock.elapsedRealtime()
            val dt = if (snowLastFrameTime == 0L) 1f
                     else ((nowMs - snowLastFrameTime).coerceIn(1L, 50L) / 16f)
            snowLastFrameTime = nowMs

            // Spawn ~8/sec indépendamment du framerate (prob corrigée par dt)
            if (snowflakes.size < SNOW_MAX_COUNT && Math.random() < (0.13f * dt).coerceAtMost(0.5f)) {
                spawnSnowflake(w)
            }

            // Mise à jour + dessin
            val iter = snowflakes.iterator()
            while (iter.hasNext()) {
                val sf = iter.next()
                sf.x += sin(sf.swayPhase) * sf.swayAmp * dt
                sf.swayPhase += sf.swaySpeed * dt
                sf.y += sf.vy * dt

                if (sf.y > meltEnd) { iter.remove(); continue }

                val alpha = if (sf.y < meltStart) 1f
                            else (1f - (sf.y - meltStart) / (meltEnd - meltStart)).coerceIn(0f, 1f)
                drawSnowflake(canvas, sf.x, sf.y, sf.bucketIdx, (alpha * 230f).toInt())
            }
        }
    }

    private fun spawnSnowflake(screenWidth: Float, startY: Float = -(5f + (Math.random() * 15f).toFloat())) {
        if (snowflakes.size >= SNOW_MAX_COUNT) return
        val bucket = (Math.random() * SNOW_BUCKETS).toInt().coerceIn(0, SNOW_BUCKETS - 1)
        // Vitesse étagée : 75% lents, 20% moyens, 5% rapides
        val r = Math.random()
        val vy = when {
            r < 0.05 -> 2.0f + (Math.random() * 1.5f).toFloat()   // rapides (5%)
            r < 0.25 -> 0.9f + (Math.random() * 0.9f).toFloat()   // moyens  (20%)
            else     -> 0.4f + (Math.random() * 0.5f).toFloat()   // lents   (75%)
        }
        snowflakes.add(Snowflake(
            x          = (Math.random() * screenWidth).toFloat(),
            y          = startY,
            vy         = vy,
            swayPhase  = (Math.random() * 2.0 * Math.PI).toFloat(),
            swayAmp    = 0.3f + (Math.random() * 1.1f).toFloat(),
            swaySpeed  = 0.005f + (Math.random() * 0.007f).toFloat(),
            bucketIdx  = bucket
        ))
    }

    private fun drawSnowflake(canvas: Canvas, x: Float, y: Float, bucketIdx: Int, alpha: Int) {
        val bmp = snowBitmaps[bucketIdx] ?: return
        snowBitmapPaint.alpha = alpha
        canvas.drawBitmap(bmp, x - bmp.width * 0.5f, y - bmp.height * 0.5f, snowBitmapPaint)
    }

    /**
     * Pré-rend 10 bitmaps de flocons (tailles SNOW_MIN_SIZE→SNOW_MAX_SIZE).
     * Chaque bitmap contient : halo à 20% opacité + 3 branches + cœur à 100% opacité.
     * Au dessin, paint.alpha scale l'ensemble → halo reste à alpha/5 naturellement.
     */
    private fun initSnowBitmaps() {
        val branchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = Color.argb(255, 200, 230, 255)
        }
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(255, 230, 245, 255)
        }
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(51, 180, 215, 255)  // 51 ≈ 255/5
        }
        for (b in 0 until SNOW_BUCKETS) {
            val size = SNOW_MIN_SIZE + b * (SNOW_MAX_SIZE - SNOW_MIN_SIZE) / (SNOW_BUCKETS - 1).toFloat()
            val bmpR = (size * 1.6f + 3f)
            val bmpSide = (bmpR * 2f + 1f).toInt().coerceAtLeast(10)
            val cx = bmpSide / 2f; val cy = bmpSide / 2f
            val bmp = Bitmap.createBitmap(bmpSide, bmpSide, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawCircle(cx, cy, size * 1.6f, haloPaint)
            branchPaint.strokeWidth = (size * 0.20f).coerceAtLeast(1.2f)
            for (i in 0 until 3) {
                val a = Math.toRadians(i * 60.0)
                val dx = (cos(a) * size).toFloat(); val dy = (sin(a) * size).toFloat()
                c.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, branchPaint)
            }
            c.drawCircle(cx, cy, size * 0.25f, corePaint)
            snowBitmaps[b] = bmp
        }
        snowBitmapsReady = true
    }

    /** Sème quelques flocons dans la moitié supérieure pour éviter un écran vide au démarrage. */
    private fun prewarmSnowflakes() {
        snowflakes.clear()
        val w = width.toFloat().takeIf { it > 0f } ?: return
        val topZone = height * 0.40f
        repeat(15) {
            val y = (Math.random() * topZone).toFloat()
            spawnSnowflake(w, startY = y)
        }
    }

    private fun spawnEmber(x: Float, y: Float) {
        for (p in particles) {
            if (p.life <= 0f || p.vy >= 0) {
                p.x = x + (Math.random() * 30 - 15).toFloat()  // Plus de spread horizontal
                p.y = y
                p.vx = (Math.random() * 3 - 1.5f).toFloat()  // Mouvement latéral
                p.vy = -2.5f - (Math.random() * 4).toFloat()  // Monte
                p.life = 0.7f + (Math.random() * 0.5f).toFloat()  // Durent plus longtemps
                p.size = 1.5f + (Math.random() * 3.5f).toFloat()
                break
            }
        }
    }

    /**
     * Mode KALEIDOSCOPE - Vrai kaléidoscope avec effet MIROIR
     * LA CLÉ: Symétrie miroir + rotation = vrai effet kaléidoscope
     */
    private fun drawKaleidoscope(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = min(width, height) / 2f

        // KALÉIDOSCOPE avec 12 miroirs (symétrie dense)
        val sectors = 12
        val sectorAngle = 360f / sectors

        // Rotation globale lente
        val time = animationPhase * 0.5f

        // Pulsation globale (toutes les formes respirent ensemble)
        val pulse = (sin((animationPhase * 0.3f).toDouble()).toFloat() + 1f) * 0.15f + 0.7f

        // DÉTECTION DES BEATS par variation RELATIVE (s'adapte au volume)
        var beatDetected = false

        if (fftSize > 4) {
            // Calculer le niveau de basse actuel
            var bassSum = 0f
            val bassCount = min(8, fftSize / 2)
            for (i in 0 until bassCount) {
                bassSum += abs(fftBuffer[i].toInt())
            }
            val currentBass = bassSum / bassCount

            // Calculer la moyenne des niveaux récents
            var bassAvg = 0f
            for (level in bassHistory) {
                bassAvg += level
            }
            bassAvg /= bassHistory.size

            // BEAT = pic significatif par rapport à la moyenne récente
            // Si niveau actuel > 1.8x la moyenne ET cooldown respecté
            if (currentBass > bassAvg * 1.8f && currentBass > 20f && (time - lastBeatTime) > 0.25f) {
                beatDetected = true
            }

            // Mettre à jour l'historique (moyenne mobile)
            bassHistory[bassHistoryIndex] = currentBass
            bassHistoryIndex = (bassHistoryIndex + 1) % bassHistory.size
        }

        // Si beat détecté, éjecter des losanges
        if (beatDetected) {
            lastBeatTime = time

            // Éjecter 2-3 losanges (réduit) dans des directions aléatoires
            val ejectCount = (2 + (Math.random() * 2).toInt())
            for (i in 0 until ejectCount) {
                val angle = Math.random() * 2 * Math.PI
                // Vitesse modérée - la friction réduite fera le reste
                val speed = 7f + (Math.random() * 5).toFloat()  // 7-12

                flyingDiamonds.add(FlyingDiamond(
                    x = centerX,
                    y = centerY,
                    vx = cos(angle).toFloat() * speed,
                    vy = sin(angle).toFloat() * speed,
                    rotation = (Math.random() * 360).toFloat(),
                    rotationSpeed = ((Math.random() * 10 - 5).toFloat()),
                    size = maxRadius * (0.08f + (Math.random() * 0.06f).toFloat()),
                    alpha = 1.0f,
                    hue = (Math.random() * 360).toFloat(),
                    age = 0f
                ))
            }
        }

        // METTRE À JOUR les losanges volants
        val iterator = flyingDiamonds.iterator()
        while (iterator.hasNext()) {
            val diamond = iterator.next()

            // Vieillissement et fade out
            diamond.age += 1f
            diamond.alpha = 1f - (diamond.age / 300f) // Disparaît en 300 frames (~5 sec)

            // Mouvement
            diamond.x += diamond.vx
            diamond.y += diamond.vy
            diamond.rotation += diamond.rotationSpeed

            // Rebond sur les bords avec peu de perte d'énergie
            if (diamond.x < 0 || diamond.x > width) {
                diamond.vx = -diamond.vx * 0.85f
                diamond.x = diamond.x.coerceIn(0f, width.toFloat())
            }
            if (diamond.y < 0 || diamond.y > height) {
                diamond.vy = -diamond.vy * 0.85f
                diamond.y = diamond.y.coerceIn(0f, height.toFloat())
            }

            // Supprimer si totalement transparent
            if (diamond.alpha <= 0f) {
                iterator.remove()
            }
        }

        // Dessiner chaque secteur avec symétrie miroir
        for (sector in 0 until sectors) {
            canvas.save()
            canvas.rotate(sector * sectorAngle, centerX, centerY)

            // Alternance de miroirs pour effet kaléidoscope authentique
            if (sector % 2 == 1) {
                canvas.scale(-1f, 1f, centerX, centerY)
            }

            // COUCHES CONCENTRIQUES de losanges
            // Couche 1: Losanges du centre (6 losanges, rotation lente)
            val layer1Rotation = time * 8f
            val layer1Count = 6
            val layer1Radius = maxRadius * 0.25f * pulse
            val layer1Size = maxRadius * 0.15f

            for (i in 0 until layer1Count) {
                val angle = (i * 360f / layer1Count + layer1Rotation) * Math.PI / 180f
                val x = centerX + cos(angle).toFloat() * layer1Radius
                val y = centerY + sin(angle).toFloat() * layer1Radius

                hsvTemp[0] = (time * 20f + i * 60f) % 360f
                hsvTemp[1] = 0.9f
                hsvTemp[2] = 0.95f
                blobPaint.color = Color.HSVToColor(hsvTemp)

                canvas.save()
                canvas.rotate(layer1Rotation * 2f + i * 30f, x, y)
                drawDiamond(canvas, x, y, layer1Size, blobPaint)
                canvas.restore()
            }

            // Couche 2: Losanges moyens (8 losanges, rotation opposée)
            val layer2Rotation = -time * 6f
            val layer2Count = 8
            val layer2Radius = maxRadius * 0.5f * pulse
            val layer2Size = maxRadius * 0.12f

            for (i in 0 until layer2Count) {
                val angle = (i * 360f / layer2Count + layer2Rotation) * Math.PI / 180f
                val x = centerX + cos(angle).toFloat() * layer2Radius
                val y = centerY + sin(angle).toFloat() * layer2Radius

                hsvTemp[0] = (time * 25f + i * 45f + 180f) % 360f
                hsvTemp[1] = 0.85f
                hsvTemp[2] = 0.9f
                blobPaint.color = Color.HSVToColor(hsvTemp)

                canvas.save()
                canvas.rotate(layer2Rotation * 1.5f + i * 22.5f, x, y)
                drawDiamond(canvas, x, y, layer2Size, blobPaint)
                canvas.restore()
            }

            // Couche 3: Losanges externes (10 losanges, rotation lente)
            val layer3Rotation = time * 4f
            val layer3Count = 10
            val layer3Radius = maxRadius * 0.75f * pulse
            val layer3Size = maxRadius * 0.1f

            for (i in 0 until layer3Count) {
                val angle = (i * 360f / layer3Count + layer3Rotation) * Math.PI / 180f
                val x = centerX + cos(angle).toFloat() * layer3Radius
                val y = centerY + sin(angle).toFloat() * layer3Radius

                hsvTemp[0] = (time * 15f + i * 36f + 120f) % 360f
                hsvTemp[1] = 0.8f
                hsvTemp[2] = 0.85f
                blobPaint.color = Color.HSVToColor(hsvTemp)

                canvas.save()
                canvas.rotate(layer3Rotation + i * 18f, x, y)
                drawDiamond(canvas, x, y, layer3Size, blobPaint)
                canvas.restore()
            }

            // Couche 4: Petits losanges intermédiaires (effet de densité)
            val layer4Rotation = -time * 10f
            val layer4Count = 12
            val layer4Radius = maxRadius * 0.37f * pulse
            val layer4Size = maxRadius * 0.06f

            for (i in 0 until layer4Count) {
                val angle = (i * 360f / layer4Count + layer4Rotation) * Math.PI / 180f
                val x = centerX + cos(angle).toFloat() * layer4Radius
                val y = centerY + sin(angle).toFloat() * layer4Radius

                hsvTemp[0] = (time * 30f + i * 30f + 240f) % 360f
                hsvTemp[1] = 0.95f
                hsvTemp[2] = 1.0f
                blobPaint.color = Color.HSVToColor(hsvTemp)

                canvas.save()
                canvas.rotate(layer4Rotation * 3f, x, y)
                drawDiamond(canvas, x, y, layer4Size, blobPaint)
                canvas.restore()
            }

            canvas.restore()
        }

        // Centre brillant pulsant
        val centerSize = maxRadius * 0.08f * pulse
        hsvTemp[0] = (time * 40f) % 360f
        hsvTemp[1] = 1.0f
        hsvTemp[2] = 1.0f
        blobPaint.color = Color.HSVToColor(hsvTemp)
        canvas.drawCircle(centerX, centerY, centerSize, blobPaint)

        // DESSINER les losanges volants (par-dessus tout)
        for (diamond in flyingDiamonds) {
            canvas.save()
            canvas.rotate(diamond.rotation, diamond.x, diamond.y)

            hsvTemp[0] = diamond.hue
            hsvTemp[1] = 0.9f
            hsvTemp[2] = 0.95f
            val color = Color.HSVToColor(hsvTemp)

            // Appliquer l'alpha pour le fade out
            val alpha = (diamond.alpha * 255).toInt().coerceIn(0, 255)
            blobPaint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

            drawDiamond(canvas, diamond.x, diamond.y, diamond.size, blobPaint)

            canvas.restore()
        }
    }

    // Fonction helper pour dessiner un losange (diamant)
    private fun drawDiamond(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        blobPath.reset()
        blobPath.moveTo(cx, cy - size)           // Haut
        blobPath.lineTo(cx + size * 0.6f, cy)    // Droite
        blobPath.lineTo(cx, cy + size)           // Bas
        blobPath.lineTo(cx - size * 0.6f, cy)    // Gauche
        blobPath.close()
        canvas.drawPath(blobPath, paint)
    }

    private fun initBoids() {
        boids.clear()
        for (i in 0 until boidCount) {
            boids.add(Boid(
                x = (Math.random() * width).toFloat(),
                y = (Math.random() * height).toFloat(),
                vx = (Math.random() * 2 - 1).toFloat(),  // Vitesse réduite de moitié
                vy = (Math.random() * 2 - 1).toFloat(),  // Vitesse réduite de moitié
                angle = 0f
            ))
        }
    }

    /**
     * Mode BOIDS - Algorithme de flocking + fond plasma
     * 3 règles simples: Séparation + Alignement + Cohésion = comportement organique
     */
    private fun drawBoids(canvas: Canvas) {
        // PLASMA BACKGROUND - Vagues de couleurs ondulantes
        val time = animationPhase * 0.3f

        // Adapter le pas selon la taille du canvas pour maintenir la performance
        // Petit écran : step 4, Grand écran : step 12-20
        val plasmaStep = when {
            width * height < 100000 -> 4  // Petit (~300x300)
            width * height < 500000 -> 8  // Moyen (~700x700)
            else -> 16                     // Fullscreen (1920x1080)
        }

        // Dessiner le plasma par bandes (optimisé selon taille)
        for (y in 0 until height step plasmaStep) {
            for (x in 0 until width step plasmaStep) {
                // Plasma = combinaison de plusieurs ondes sinusoïdales
                val wave1 = sin((x * 0.01f + time).toDouble()).toFloat()
                val wave2 = cos((y * 0.01f + time * 1.3f).toDouble()).toFloat()
                val wave3 = sin((x * 0.008f + y * 0.008f + time * 0.8f).toDouble()).toFloat()

                val plasma = (wave1 + wave2 + wave3) / 3f

                // Mapper à une couleur arc-en-ciel
                val hue = ((plasma + 1f) * 180f + colorPhase) % 360f
                hsvTemp[0] = hue
                hsvTemp[1] = 0.6f
                hsvTemp[2] = 0.3f // Sombre pour que les boids ressortent

                blobPaint.color = Color.HSVToColor(hsvTemp)
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + plasmaStep).toFloat(), (y + plasmaStep).toFloat(), blobPaint)
            }
        }

        // BOIDS - Algorithme de flocking (optimisé)
        val perceptionRadius = 50f      // Réduit pour moins de comparaisons
        val separationRadius = 20f      // Réduit proportionnellement
        val maxSpeed = 2f               // Vitesse réduite de moitié
        val maxForce = 0.08f            // Force réduite proportionnellement

        for (boid in boids) {
            var separationX = 0f
            var separationY = 0f
            var alignmentX = 0f
            var alignmentY = 0f
            var cohesionX = 0f
            var cohesionY = 0f
            var nearbyCount = 0
            var tooCloseCount = 0

            // Trouver les voisins et calculer les forces
            for (other in boids) {
                if (other === boid) continue

                val dx = other.x - boid.x
                val dy = other.y - boid.y
                val distSq = dx * dx + dy * dy

                if (distSq < perceptionRadius * perceptionRadius && distSq > 0) {
                    nearbyCount++

                    // COHÉSION: Aller vers le centre du groupe
                    cohesionX += other.x
                    cohesionY += other.y

                    // ALIGNEMENT: Copier la direction du groupe
                    alignmentX += other.vx
                    alignmentY += other.vy

                    // SÉPARATION: Éviter les trop proches
                    if (distSq < separationRadius * separationRadius) {
                        tooCloseCount++
                        val dist = sqrt(distSq.toDouble()).toFloat()
                        separationX -= dx / dist
                        separationY -= dy / dist
                    }
                }
            }

            // Appliquer les forces
            if (nearbyCount > 0) {
                // Cohésion
                cohesionX = cohesionX / nearbyCount - boid.x
                cohesionY = cohesionY / nearbyCount - boid.y
                val cohesionMag = sqrt((cohesionX * cohesionX + cohesionY * cohesionY).toDouble()).toFloat()
                if (cohesionMag > 0) {
                    cohesionX = (cohesionX / cohesionMag) * maxForce * 0.5f
                    cohesionY = (cohesionY / cohesionMag) * maxForce * 0.5f
                }

                // Alignement
                alignmentX = alignmentX / nearbyCount
                alignmentY = alignmentY / nearbyCount
                val alignMag = sqrt((alignmentX * alignmentX + alignmentY * alignmentY).toDouble()).toFloat()
                if (alignMag > 0) {
                    alignmentX = (alignmentX / alignMag) * maxForce * 0.8f
                    alignmentY = (alignmentY / alignMag) * maxForce * 0.8f
                }
            }

            if (tooCloseCount > 0) {
                // Séparation
                val sepMag = sqrt((separationX * separationX + separationY * separationY).toDouble()).toFloat()
                if (sepMag > 0) {
                    separationX = (separationX / sepMag) * maxForce * 1.5f
                    separationY = (separationY / sepMag) * maxForce * 1.5f
                }
            }

            // Ajouter les forces à la vélocité
            boid.vx += separationX + alignmentX + cohesionX
            boid.vy += separationY + alignmentY + cohesionY

            // Limiter la vitesse
            val speed = sqrt((boid.vx * boid.vx + boid.vy * boid.vy).toDouble()).toFloat()
            if (speed > maxSpeed) {
                boid.vx = (boid.vx / speed) * maxSpeed
                boid.vy = (boid.vy / speed) * maxSpeed
            }

            // Mettre à jour position
            boid.x += boid.vx
            boid.y += boid.vy

            // Wrap around (téléportation aux bords)
            if (boid.x < 0) boid.x = width.toFloat()
            if (boid.x > width) boid.x = 0f
            if (boid.y < 0) boid.y = height.toFloat()
            if (boid.y > height) boid.y = 0f

            // Angle de direction pour le dessin
            boid.angle = Math.toDegrees(Math.atan2(boid.vy.toDouble(), boid.vx.toDouble())).toFloat()

            // DESSINER LE BOID - Triangle pointu dans la direction du mouvement
            canvas.save()
            canvas.rotate(boid.angle, boid.x, boid.y)

            // Couleur basée sur la vitesse
            val speedRatio = speed / maxSpeed
            hsvTemp[0] = (180f + speedRatio * 180f) % 360f
            hsvTemp[1] = 0.8f
            hsvTemp[2] = 0.9f
            particlePaint.color = Color.HSVToColor(hsvTemp)

            // Triangle
            val size = 6f
            blobPath.reset()
            blobPath.moveTo(boid.x + size * 2, boid.y)
            blobPath.lineTo(boid.x - size, boid.y - size)
            blobPath.lineTo(boid.x - size, boid.y + size)
            blobPath.close()
            canvas.drawPath(blobPath, particlePaint)

            canvas.restore()
        }
    }

    // === MODE 13: JULIA - Fractale de Julia animée ===
    private var juliaPhase = 0f
    private var juliaBitmap: Bitmap? = null
    private val juliaResolution = 180 // Résolution optimisée (était 200)
    private val juliaMaxIterations = 48 // Réduit de 64 à 48 pour meilleures perfs
    private val juliaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true // Interpolation pour un rendu lisse
    }
    private var juliaFrameSkip = 0 // Pour ne recalculer que tous les N frames
    private val juliaUpdateInterval = 4 // Recalcule tous les 4 frames (60fps → 15fps calcul)

    // Cache des valeurs précalculées
    private var cachedCReal = 0f
    private var cachedCImag = 0f
    private var cachedRotation = 0f
    private var cachedZoom = 1.5f

    private fun drawJulia(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Optimisation: ne recalculer la fractale que tous les N frames
        juliaFrameSkip++
        val shouldRecalculate = juliaFrameSkip >= juliaUpdateInterval

        if (shouldRecalculate) {
            juliaFrameSkip = 0

            // Calculer l'amplitude moyenne pour la pulsation (optimisé)
            var sum = 0f
            var bassSum = 0f
            val fftSize = smoothedFft.size
            val bassLimit = fftSize / 10

            for (i in 0 until fftSize) {
                sum += smoothedFft[i]
                if (i < bassLimit) bassSum += smoothedFft[i]
            }
            val avgAmplitude = sum / fftSize
            val bassEnergy = if (bassLimit > 0) bassSum / bassLimit else 0f

            // Paramètre c de la fractale de Julia qui varie avec la musique
            // Vitesse divisée par 4 pour mieux apprécier les formes
            val musicInfluence = avgAmplitude * 0.3f
            val baseAngle = animationPhase * 0.0125f + musicInfluence // 0.05 / 4 = 0.0125
            val radiusVariation = 0.7885f + bassEnergy * 0.1f

            // Précalculer les valeurs qui seront utilisées dans la boucle
            cachedCReal = radiusVariation * cos(baseAngle)
            cachedCImag = radiusVariation * sin(baseAngle)
            cachedRotation = animationPhase * 0.005f // 0.02 / 4 = 0.005
            cachedZoom = 1.5f / (1.0f + bassEnergy * 0.3f)

            // Créer/recréer le bitmap si nécessaire
            if (juliaBitmap == null || juliaBitmap!!.width != juliaResolution || juliaBitmap!!.height != juliaResolution) {
                juliaBitmap?.recycle()
                juliaBitmap = Bitmap.createBitmap(
                    juliaResolution,
                    juliaResolution,
                    Bitmap.Config.ARGB_8888
                )
            }

            val bitmap = juliaBitmap!!

            // Précalculer les constantes trigonométriques
            val cosR = cos(cachedRotation)
            val sinR = sin(cachedRotation)
            val halfRes = juliaResolution / 2f
            val resScale = juliaResolution / 4f
            val colorPhaseInt = colorPhase.toInt() // Éviter les conversions répétées

            // Calculer la fractale (boucle optimisée)
            for (py in 0 until juliaResolution) {
                val yBase = (py - halfRes) / resScale * cachedZoom

                for (px in 0 until juliaResolution) {
                    val xBase = (px - halfRes) / resScale * cachedZoom

                    // Appliquer la rotation (précalculée)
                    val x = xBase * cosR - yBase * sinR
                    val y = xBase * sinR + yBase * cosR
                    // Itération de Julia: z = z² + c (optimisée)
                    var zx = x
                    var zy = y
                    var iteration = 0
                    var zx2 = zx * zx
                    var zy2 = zy * zy

                    // Boucle optimisée: précalcul de zx² et zy²
                    while (zx2 + zy2 < 4.0f && iteration < juliaMaxIterations) {
                        zy = 2.0f * zx * zy + cachedCImag
                        zx = zx2 - zy2 + cachedCReal
                        zx2 = zx * zx
                        zy2 = zy * zy
                        iteration++
                    }

                    // Magnitude finale pour smooth coloring (optimisée)
                    val magnitude = sqrt(zx2 + zy2)

                    // Calculer la couleur avec smooth coloring
                    val color = if (iteration == juliaMaxIterations) {
                        // Point dans l'ensemble: utiliser la magnitude pour créer des variations
                        val normalizedMag = (magnitude * 0.5f).coerceIn(0f, 1f)
                        val distSq = x * x + y * y // Éviter sqrt si possible
                        val distFactor = distSq / (cachedZoom * cachedZoom)

                        // Créer un gradient basé sur la magnitude et la distance
                        val hue = ((colorPhaseInt + distFactor * 60f + normalizedMag * 120f) % 360f).toInt()
                        val saturation = 0.6f + normalizedMag * 0.3f
                        val value = 0.1f + normalizedMag * 0.4f + avgAmplitude * 0.2f
                        Color.HSVToColor(floatArrayOf(hue.toFloat(), saturation, value))
                    } else {
                        // Point hors de l'ensemble: smooth coloring
                        val smoothIter = if (magnitude > 1f) {
                            iteration + 1f - (kotlin.math.ln(kotlin.math.ln(magnitude.toDouble())) / 0.6931471805599453).toFloat() // ln(2) précalculé
                        } else {
                            iteration.toFloat()
                        }
                        val normalizedIter = (smoothIter / juliaMaxIterations).coerceIn(0f, 1f)

                        val hue = ((normalizedIter * 720f + colorPhaseInt) % 360f).toInt()
                        val saturation = 0.7f + avgAmplitude * 0.3f
                        val value = 0.5f + normalizedIter * 0.5f
                        Color.HSVToColor(floatArrayOf(hue.toFloat(), saturation, value))
                    }

                    bitmap.setPixel(px, py, color)
                }
            }
        }

        // Toujours dessiner le bitmap (même si pas recalculé)
        if (juliaBitmap != null) {
            val destRect = RectF(0f, 0f, w, h)
            canvas.drawBitmap(juliaBitmap!!, null, destRect, juliaPaint)
        }
    }

    // === MODE 15: JULIA_GRAYSCALE - Fractale de Julia en nuances de gris (gratuit) ===
    private var juliaGrayBitmap: Bitmap? = null
    private val juliaGrayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private var juliaGrayFrameSkip = 0

    private fun drawJuliaGrayscale(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Optimisation: ne recalculer la fractale que tous les N frames
        juliaGrayFrameSkip++
        val shouldRecalculate = juliaGrayFrameSkip >= juliaUpdateInterval

        if (shouldRecalculate) {
            juliaGrayFrameSkip = 0

            // Calculer l'amplitude moyenne pour la pulsation (optimisé)
            var sum = 0f
            var bassSum = 0f
            val fftSize = smoothedFft.size
            val bassLimit = fftSize / 10

            for (i in 0 until fftSize) {
                sum += smoothedFft[i]
                if (i < bassLimit) bassSum += smoothedFft[i]
            }
            val avgAmplitude = sum / fftSize
            val bassEnergy = if (bassLimit > 0) bassSum / bassLimit else 0f

            // Paramètre c de la fractale de Julia qui varie avec la musique
            val musicInfluence = avgAmplitude * 0.3f
            val baseAngle = animationPhase * 0.0125f + musicInfluence
            val radiusVariation = 0.7885f + bassEnergy * 0.1f

            // Précalculer les valeurs
            cachedCReal = radiusVariation * cos(baseAngle)
            cachedCImag = radiusVariation * sin(baseAngle)
            cachedRotation = animationPhase * 0.005f
            cachedZoom = 1.5f / (1.0f + bassEnergy * 0.3f)

            // Créer/recréer le bitmap si nécessaire
            if (juliaGrayBitmap == null || juliaGrayBitmap!!.width != juliaResolution || juliaGrayBitmap!!.height != juliaResolution) {
                juliaGrayBitmap?.recycle()
                juliaGrayBitmap = Bitmap.createBitmap(
                    juliaResolution,
                    juliaResolution,
                    Bitmap.Config.ARGB_8888
                )
            }

            val bitmap = juliaGrayBitmap!!

            // Précalculer les constantes trigonométriques
            val cosR = cos(cachedRotation)
            val sinR = sin(cachedRotation)
            val halfRes = juliaResolution / 2f
            val resScale = juliaResolution / 4f

            // Calculer la fractale
            for (py in 0 until juliaResolution) {
                val yBase = (py - halfRes) / resScale * cachedZoom

                for (px in 0 until juliaResolution) {
                    val xBase = (px - halfRes) / resScale * cachedZoom

                    // Appliquer la rotation
                    val x = xBase * cosR - yBase * sinR
                    val y = xBase * sinR + yBase * cosR

                    // Itération de Julia: z = z² + c
                    var zx = x
                    var zy = y
                    var iteration = 0
                    var zx2 = zx * zx
                    var zy2 = zy * zy

                    while (zx2 + zy2 < 4.0f && iteration < juliaMaxIterations) {
                        zy = 2.0f * zx * zy + cachedCImag
                        zx = zx2 - zy2 + cachedCReal
                        zx2 = zx * zx
                        zy2 = zy * zy
                        iteration++
                    }

                    // Magnitude finale pour smooth coloring
                    val magnitude = sqrt(zx2 + zy2)

                    // Calculer la couleur en NUANCES DE GRIS
                    val color = if (iteration == juliaMaxIterations) {
                        // Point dans l'ensemble (la figure): gris moyen/clair - TOUJOURS visible
                        val normalizedMag = (magnitude * 0.5f).coerceIn(0f, 1f)

                        // Figure: plage fixe 0.55-0.75 pour contraste garanti avec le fond (max 0.35)
                        val intensity = (0.55f + normalizedMag * 0.15f + avgAmplitude * 0.05f).coerceIn(0.55f, 0.75f)
                        val gray = (intensity * 255f).toInt()
                        Color.rgb(gray, gray, gray)
                    } else {
                        // Point hors de l'ensemble (le fond): dégradé radial noir→gris foncé
                        val smoothIter = if (magnitude > 1f) {
                            iteration + 1f - (kotlin.math.ln(kotlin.math.ln(magnitude.toDouble())) / 0.6931471805599453).toFloat()
                        } else {
                            iteration.toFloat()
                        }
                        val normalizedIter = (smoothIter / juliaMaxIterations).coerceIn(0f, 1f)

                        // Distance radiale du centre pour gradient (0 = centre, 1 = bord)
                        val distFromCenter = sqrt(x * x + y * y) / (cachedZoom * 2f)
                        val radialFactor = (1f - distFromCenter.coerceIn(0f, 1f)) // Inverse: 1 au centre, 0 aux bords

                        // Fond: plage fixe 0-0.35 pour contraste garanti avec figure (min 0.55)
                        val intensity = (normalizedIter * 0.12f + radialFactor * 0.20f + avgAmplitude * 0.03f).coerceIn(0f, 0.35f)
                        val gray = (intensity * 255f).toInt()
                        Color.rgb(gray, gray, gray)
                    }

                    bitmap.setPixel(px, py, color)
                }
            }
        }

        // Toujours dessiner le bitmap
        if (juliaGrayBitmap != null) {
            val destRect = RectF(0f, 0f, w, h)
            canvas.drawBitmap(juliaGrayBitmap!!, null, destRect, juliaGrayPaint)
        }
    }

    // === MODE 14: MANDELBROT_ZOOM - Zoom hypnotique avec polynômes de Bernstein ===
    // Coloring par polynômes de Bernstein: dégradés mathématiquement lisses, zéro clignotement
    // Ref: solarianprogrammer.com/2013/02/28/mandelbrot-set-cpp-11/
    private var mandelbrotBitmap: Bitmap? = null
    private val mandelbrotResolution = 180
    private val mandelbrotPixels = IntArray(mandelbrotResolution * mandelbrotResolution)
    private var mandelbrotZoom = 200.0
    private var mandelbrotCenterX = -0.7436438870371587
    private var mandelbrotCenterY = 0.1318259043124515
    private var mandelbrotFrameSkip = 0
    private val mandelbrotUpdateInterval = 6
    private val mandelbrotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private var mandelbrotColorOffset = 0.0

    // Cibles de zoom sur le bord du Mandelbrot
    private val mandelbrotTargets = listOf(
        doubleArrayOf(-0.7436438870371587, 0.1318259043124515, 5e12),
        doubleArrayOf(0.281717921930775, 0.5771052841488505, 1e12),
        doubleArrayOf(-0.04524078208, 0.98681620434, 5e9),
        doubleArrayOf(-1.768528969208280, 0.001741366958326, 5e9),
        doubleArrayOf(-0.10109636384562, 0.9562865108091415, 1e11)
    )
    private var mandelbrotTargetIdx = 0
    private var mandelbrotLogZoom = kotlin.math.ln(200.0)

    private fun drawMandelbrotZoom(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        mandelbrotFrameSkip++
        if (mandelbrotFrameSkip < mandelbrotUpdateInterval) {
            if (mandelbrotBitmap != null) {
                canvas.drawBitmap(mandelbrotBitmap!!, null, RectF(0f, 0f, w, h), mandelbrotPaint)
            }
            return
        }
        mandelbrotFrameSkip = 0

        var sum = 0f
        var bassSum = 0f
        val fftSize = smoothedFft.size
        val bassLimit = fftSize / 10
        for (i in 0 until fftSize) {
            sum += smoothedFft[i]
            if (i < bassLimit) bassSum += smoothedFft[i]
        }
        val avgAmplitude = sum / fftSize
        val bassEnergy = if (bassLimit > 0) bassSum / bassLimit else 0f

        // Décalage de couleur ultra lent (les teintes glissent imperceptiblement)
        mandelbrotColorOffset += 0.0005 + avgAmplitude * 0.001
        if (mandelbrotColorOffset > 1000.0) mandelbrotColorOffset -= 1000.0

        // Zoom lent et contemplatif
        mandelbrotLogZoom += 0.003 + bassEnergy * 0.001
        mandelbrotZoom = kotlin.math.exp(mandelbrotLogZoom)

        val target = mandelbrotTargets[mandelbrotTargetIdx]
        if (mandelbrotZoom > target[2]) {
            mandelbrotTargetIdx = (mandelbrotTargetIdx + 1) % mandelbrotTargets.size
            val nt = mandelbrotTargets[mandelbrotTargetIdx]
            mandelbrotCenterX = nt[0]
            mandelbrotCenterY = nt[1]
            mandelbrotLogZoom = kotlin.math.ln(200.0)
            mandelbrotZoom = 200.0
        }

        if (mandelbrotBitmap == null || mandelbrotBitmap!!.width != mandelbrotResolution) {
            mandelbrotBitmap?.recycle()
            mandelbrotBitmap = Bitmap.createBitmap(
                mandelbrotResolution, mandelbrotResolution,
                Bitmap.Config.ARGB_8888
            )
        }

        val maxIter = (150 + kotlin.math.log10(mandelbrotZoom) * 40).toInt().coerceIn(150, 500)
        val halfRes = mandelbrotResolution / 2.0
        val scale = 1.5 / mandelbrotZoom
        val colorOff = mandelbrotColorOffset
        val pixels = mandelbrotPixels
        val cx = mandelbrotCenterX
        val cy = mandelbrotCenterY
        val res = mandelbrotResolution
        val ln2 = 0.6931471805599453

        for (py in 0 until res) {
            val y0 = (py - halfRes) / halfRes * scale + cy
            val rowOff = py * res

            for (px in 0 until res) {
                val x0 = (px - halfRes) / halfRes * scale + cx

                var zx = 0.0
                var zy = 0.0
                var zx2 = 0.0
                var zy2 = 0.0
                var iter = 0

                // Bailout à 256² pour un smooth count plus précis
                while (zx2 + zy2 < 65536.0 && iter < maxIter) {
                    zy = 2.0 * zx * zy + y0
                    zx = zx2 - zy2 + x0
                    zx2 = zx * zx
                    zy2 = zy * zy
                    iter++
                }

                if (iter == maxIter) {
                    pixels[rowOff + px] = 0xFF000000.toInt()
                } else {
                    // Smooth iteration count: valeur continue (pas de sauts discrets)
                    val smoothIter = iter + 1.0 -
                            kotlin.math.ln(kotlin.math.ln(sqrt(zx2 + zy2))) / ln2

                    // Polynômes de Bernstein: noir→bleu→vert→jaune→orange→noir
                    // Boucle avec frac() → bandes de couleur lisses le long du bord
                    val raw = smoothIter * 0.04 + colorOff
                    val t = raw - floor(raw)
                    val ti = 1.0 - t

                    val r = (9.0 * ti * t * t * t * 255.0).toInt()
                    val g = (15.0 * ti * ti * t * t * 255.0).toInt()
                    val b = (8.5 * ti * ti * ti * t * 255.0).toInt()

                    pixels[rowOff + px] = (0xFF shl 24) or
                            (r.coerceIn(0, 255) shl 16) or
                            (g.coerceIn(0, 255) shl 8) or
                            b.coerceIn(0, 255)
                }
            }
        }

        // --- Pousser le noir vers le bord de l'écran ---
        // Trouve le centre de gravité du noir et le pousse vers le côté le plus proche
        val black = 0xFF000000.toInt()
        var blackSumX = 0.0
        var blackSumY = 0.0
        var blackTotal = 0

        for (py2 in 0 until res) {
            val ro = py2 * res
            for (px2 in 0 until res) {
                if (pixels[ro + px2] == black) {
                    blackSumX += px2.toDouble()
                    blackSumY += py2.toDouble()
                    blackTotal++
                }
            }
        }

        val totalPixels = res * res

        if (blackTotal >= totalPixels) {
            // 100% noir → cible suivante
            mandelbrotTargetIdx = (mandelbrotTargetIdx + 1) % mandelbrotTargets.size
            val nt = mandelbrotTargets[mandelbrotTargetIdx]
            mandelbrotCenterX = nt[0]
            mandelbrotCenterY = nt[1]
            mandelbrotLogZoom = kotlin.math.ln(200.0)
            mandelbrotZoom = 200.0
        } else if (blackTotal > totalPixels * 7 / 10) {
            // Centre de gravité du noir par rapport au centre de l'image
            val blackCx = blackSumX / blackTotal - halfRes
            val blackCy = blackSumY / blackTotal - halfRes
            val blackDist = sqrt(blackCx * blackCx + blackCy * blackCy) / halfRes

            if (blackDist > 0.02) {
                // Le noir est légèrement décentré → le pousser encore plus loin
                // (plus il est proche du centre, plus on pousse fort)
                val strength = 0.008 * (1.0 - blackDist).coerceAtLeast(0.0)
                mandelbrotCenterX -= blackCx / halfRes * scale * strength
                mandelbrotCenterY -= blackCy / halfRes * scale * strength
            } else {
                // Le noir est pile au centre → micro poussée pour casser la symétrie
                mandelbrotCenterX += scale * 0.001
            }
        }

        mandelbrotBitmap!!.setPixels(pixels, 0, res, 0, 0, res, res)
        canvas.drawBitmap(mandelbrotBitmap!!, null, RectF(0f, 0f, w, h), mandelbrotPaint)
    }

    // === MODE 16: DELAUNAY_MESH - Maillage Delaunay réactif à l'audio ===

    private fun initDelaunayMesh() {
        meshPoints.clear()
        meshTriangles.clear()
        meshEdges.clear()
        meshFrameSkip = 0
        if (cachedWidth == 0 || cachedHeight == 0) return

        val w = cachedWidth.toFloat()
        val h = cachedHeight.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val maxDist = sqrt(cx * cx + cy * cy)
        val rng = java.util.Random(99991L)

        val ringRadius = minOf(w, h) * 0.22f

        // === Grille régulière avec légère perturbation — triangles homogènes ===
        // Les points clairement à l'intérieur de l'anneau sont exclus (les billes les remplacent)
        val targetCellSize = minOf(w, h) / 6.5f
        val cols = (w / targetCellSize + 1).toInt().coerceIn(6, 13)
        val rows = (h / targetCellSize + 1).toInt().coerceIn(6, 22)
        val cellW = w / (cols - 1).coerceAtLeast(1)
        val cellH = h / (rows - 1).coerceAtLeast(1)
        val jitter = minOf(cellW, cellH) * 0.30f
        val innerExcludeR = ringRadius * 0.88f   // même seuil que le mur des billes

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val bx = col * cellW + (rng.nextFloat() - 0.5f) * 2f * jitter
                val by = row * cellH + (rng.nextFloat() - 0.5f) * 2f * jitter
                val px = bx.coerceIn(0f, w)
                val py = by.coerceIn(0f, h)
                val dFromC = sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
                if (dFromC < innerExcludeR) continue   // trop à l'intérieur → ignoré
                val distRatio = dFromC / maxDist
                meshPoints.add(MeshPoint(
                    restX = px, restY = py, x = px, y = py,
                    distRatio = distRatio.coerceIn(0f, 1f),
                    driftAngle = (rng.nextDouble() * 2.0 * Math.PI).toFloat(),
                    isRing = false
                ))
            }
        }

        // === Anneau central — réactif à l'audio, pulse radialement ===
        val ringCount = 24
        for (i in 0 until ringCount) {
            val angle = (i.toFloat() / ringCount) * 2f * Math.PI.toFloat()
            val px = cx + cos(angle) * ringRadius
            val py = cy + sin(angle) * ringRadius
            val distRatio = ringRadius / maxDist
            // cos/sin(angle) = direction normalisée (ringRadius s'annule), angle = atan2 exact
            meshPoints.add(MeshPoint(
                restX = px, restY = py, x = px, y = py,
                distRatio = distRatio.coerceIn(0f, 1f),
                driftAngle = angle, isRing = true,
                restNdx = cos(angle), restNdy = sin(angle),
                restNorm = ringRadius, restAngle = angle
            ))
        }

        // === 8 billes — une par point cardinal/intercardinal, attirée vers son mur ===
        // driftAngle = direction de gravité propre à chaque bille (son "sol")
        // E=0, SE=π/4, S=π/2, SW=3π/4, W=π, NW=5π/4, N=3π/2, NE=7π/4
        for (i in 0 until 8) {
            val compassAngle = (i.toFloat() / 8f) * 2f * Math.PI.toFloat()
            // Départ : légèrement dans la direction opposée (côté opposé au sol)
            val startR = ringRadius * 0.28f
            val bpx = cx + cos(compassAngle) * startR
            val bpy = cy + sin(compassAngle) * startR
            meshPoints.add(MeshPoint(
                restX = bpx, restY = bpy, x = bpx, y = bpy,
                distRatio = (startR / maxDist).coerceIn(0f, 1f),
                driftAngle = compassAngle, isRing = false, isBall = true,
                vx = 0f, vy = 0f,
                gravX = cos(compassAngle), gravY = sin(compassAngle)
            ))
        }

        // === Point fixe au centre — ancrage de la triangulation ===
        meshPoints.add(MeshPoint(
            restX = cx, restY = cy, x = cx, y = cy,
            distRatio = 0f, driftAngle = 0f,
            isCenter = true
        ))

        meshTriangles.addAll(bowyerWatson(meshPoints, w, h))
        buildMeshEdges()
    }

    private fun buildMeshEdges() {
        meshEdges.clear()
        val edgeSet = HashSet<Long>(meshTriangles.size * 3)
        for (tri in meshTriangles) {
            val edges = arrayOf(
                intArrayOf(minOf(tri[0], tri[1]), maxOf(tri[0], tri[1])),
                intArrayOf(minOf(tri[1], tri[2]), maxOf(tri[1], tri[2])),
                intArrayOf(minOf(tri[0], tri[2]), maxOf(tri[0], tri[2]))
            )
            for (e in edges) {
                val key = e[0].toLong() * 100000L + e[1]
                if (edgeSet.add(key)) meshEdges.add(e)
            }
        }
    }

    /** Algorithme de Bowyer-Watson pour la triangulation de Delaunay. */
    private fun bowyerWatson(pts: ArrayList<MeshPoint>, w: Float, h: Float): ArrayList<IntArray> {
        val n = pts.size
        val margin = maxOf(w, h) * 4f

        // Coordonnées étendues : points réels + 3 sommets du super-triangle
        val sx = FloatArray(n + 3)
        val sy = FloatArray(n + 3)
        for (i in 0 until n) { sx[i] = pts[i].x; sy[i] = pts[i].y }
        sx[n] = w / 2f;       sy[n] = -margin
        sx[n + 1] = -margin;  sy[n + 1] = h + margin
        sx[n + 2] = w + margin; sy[n + 2] = h + margin

        val tris = ArrayList<IntArray>(n * 2 + 4)
        tris.add(intArrayOf(n, n + 1, n + 2))

        val bad = ArrayList<IntArray>(32)
        val polygon = ArrayList<IntArray>(64)

        for (pi in 0 until n) {
            val px = sx[pi]; val py = sy[pi]

            // Chercher les triangles dont le circumcercle contient le point
            bad.clear()
            for (t in tris) {
                if (inCircumcircle(sx[t[0]], sy[t[0]], sx[t[1]], sy[t[1]], sx[t[2]], sy[t[2]], px, py)) {
                    bad.add(t)
                }
            }

            // Construire le polygone frontière (arêtes non partagées entre mauvais triangles)
            polygon.clear()
            for (t in bad) {
                val edges = arrayOf(
                    intArrayOf(t[0], t[1]), intArrayOf(t[1], t[2]), intArrayOf(t[0], t[2])
                )
                for (e in edges) {
                    var shared = false
                    for (other in bad) {
                        if (other === t) continue
                        if ((other[0] == e[0] || other[1] == e[0] || other[2] == e[0]) &&
                            (other[0] == e[1] || other[1] == e[1] || other[2] == e[1])) {
                            shared = true; break
                        }
                    }
                    if (!shared) polygon.add(e)
                }
            }

            // Supprimer les mauvais triangles (comparaison par référence)
            tris.removeIf { t -> bad.any { b -> b === t } }

            // Ajouter de nouveaux triangles reliant le polygone au point
            for (e in polygon) tris.add(intArrayOf(e[0], e[1], pi))
        }

        // Supprimer les triangles qui touchent le super-triangle
        tris.removeIf { it[0] >= n || it[1] >= n || it[2] >= n }
        return tris
    }

    /** Test d'appartenance au circumcercle via circumcentre (indépendant de l'orientation CW/CCW). */
    private fun inCircumcircle(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, px: Float, py: Float
    ): Boolean {
        val d = 2f * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by))
        if (d == 0f) return false  // Points colinéaires
        val a2 = ax * ax + ay * ay
        val b2 = bx * bx + by * by
        val c2 = cx * cx + cy * cy
        val ux = (a2 * (by - cy) + b2 * (cy - ay) + c2 * (ay - by)) / d
        val uy = (a2 * (cx - bx) + b2 * (ax - cx) + c2 * (bx - ax)) / d
        val r2 = (ax - ux) * (ax - ux) + (ay - uy) * (ay - uy)
        val dp2 = (px - ux) * (px - ux) + (py - uy) * (py - uy)
        return dp2 < r2 + 0.01f
    }

    /** Génère récursivement les segments d'un éclair par déplacement de point milieu. */
    private fun generateLightningSegs(
        x1: Float, y1: Float, x2: Float, y2: Float,
        roughness: Float, depth: Int, isBranch: Boolean,
        out: ArrayList<LightningSegment>, rng: java.util.Random
    ) {
        if (depth == 0 || roughness < 3f) {
            out.add(LightningSegment(x1, y1, x2, y2, isBranch))
            return
        }
        // Déplacement perpendiculaire aléatoire au milieu du segment
        val mx = (x1 + x2) / 2f
        val my = (y1 + y2) / 2f
        val perpX = -(y2 - y1)
        val perpY = x2 - x1
        val len = sqrt(perpX * perpX + perpY * perpY).coerceAtLeast(0.001f)
        val midX = mx + (perpX / len) * (rng.nextFloat() - 0.5f) * roughness
        val midY = my + (perpY / len) * (rng.nextFloat() - 0.5f) * roughness
        generateLightningSegs(x1, y1, midX, midY, roughness * 0.56f, depth - 1, isBranch, out, rng)
        generateLightningSegs(midX, midY, x2, y2, roughness * 0.56f, depth - 1, isBranch, out, rng)
        // Branche aléatoire depuis le point milieu
        if (!isBranch && depth >= 3 && rng.nextFloat() < 0.42f) {
            val angle = (rng.nextFloat() - 0.5f) * 1.1f
            val cs = Math.cos(angle.toDouble()).toFloat()
            val sn = Math.sin(angle.toDouble()).toFloat()
            val dx = x2 - x1; val dy = y2 - y1
            val bdx = dx * cs - dy * sn
            val bdy = dx * sn + dy * cs
            val bNorm = sqrt(bdx * bdx + bdy * bdy).coerceAtLeast(0.001f)
            val bLen = roughness * (1.1f + rng.nextFloat() * 0.9f)
            generateLightningSegs(
                midX, midY,
                midX + (bdx / bNorm) * bLen, midY + (bdy / bNorm) * bLen,
                roughness * 0.40f, depth - 2, true, out, rng
            )
        }
    }

    private fun drawDelaunayMesh(canvas: Canvas, grayscale: Boolean = false) {
        if (meshPoints.isEmpty()) { initDelaunayMesh(); return }

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        sqrt(cx * cx + cy * cy)
        val ringRadius = minOf(w, h) * 0.22f

        // Calcul de l'amplitude audio
        var amplitude = 0f
        var bassAmp = 0f
        if (fftSize > 0) {
            var sum = 0f
            var bSum = 0f
            val limit = minOf(smoothedFft.size, fftSize)
            val bassLimit = maxOf(1, limit / 8)
            for (i in 0 until limit) {
                sum += smoothedFft[i]
                if (i < bassLimit) bSum += smoothedFft[i]
            }
            amplitude = (sum / limit).coerceIn(0f, 1f)
            bassAmp = (bSum / bassLimit).coerceIn(0f, 1.5f)
        } else {
            amplitude = (sin(animationPhase) + 1f) * 0.12f
        }

        // === Déclenchement de la foudre — marche sur les arêtes Delaunay ===
        if (lightningCooldown > 0) lightningCooldown--
        if (lightningCooldown == 0 && beatDetected && meshEdges.isNotEmpty()) {
            val rng = java.util.Random()
            val n = meshPoints.size
            if (n > 0) {
                // Adjacence : vertex → liste de ses voisins
                val adj = ArrayList<ArrayList<Int>>(n)
                for (i in 0 until n) adj.add(ArrayList(6))
                for (e in meshEdges) {
                    if (e[0] < n && e[1] < n) { adj[e[0]].add(e[1]); adj[e[1]].add(e[0]) }
                }
                // Marche aléatoire avec branchements depuis un vertex aléatoire
                val startIdx = rng.nextInt(n)
                val visitedKeys = HashSet<Long>(48)
                val visitedVerts = HashSet<Int>(48)
                val allSegs = ArrayList<LightningSegment>(256)
                val queue = ArrayDeque<Int>()
                queue.add(startIdx); visitedVerts.add(startIdx)
                val maxEdges = 16 + rng.nextInt(10)
                var edgesDone = 0
                while (queue.isNotEmpty() && edgesDone < maxEdges) {
                    val curr = queue.removeFirst()
                    val neighbors = adj[curr]
                    val perm = (0 until neighbors.size).toMutableList()
                    java.util.Collections.shuffle(perm, rng)
                    val maxBranch = if (rng.nextFloat() < 0.38f) 2 else 1
                    var taken = 0
                    for (pi in perm) {
                        if (taken >= maxBranch || edgesDone >= maxEdges) break
                        val next = neighbors[pi]
                        val key = minOf(curr, next).toLong() * 10000L + maxOf(curr, next)
                        if (visitedKeys.add(key)) {
                            val p1 = meshPoints[curr]; val p2 = meshPoints[next]
                            val eLen = sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y))
                            generateLightningSegs(p1.x, p1.y, p2.x, p2.y, eLen * 0.30f, 3, true, allSegs, rng)
                            queue.add(next); visitedVerts.add(next)
                            edgesDone++; taken++
                        }
                    }
                }
                // Garantir que l'éclair atteint au moins un bord de l'écran
                // → trouver le sommet visité le plus proche d'un bord
                val borderThresh = minOf(w, h) * 0.07f
                var closestBorderDist = Float.MAX_VALUE
                var closestBorderVert = startIdx
                for (vi in visitedVerts) {
                    val pt = meshPoints[vi]
                    val d = minOf(pt.x, w - pt.x, pt.y, h - pt.y)
                    if (d < closestBorderDist) { closestBorderDist = d; closestBorderVert = vi }
                }
                // Si pas assez proche → marche gloutonne vers le bord
                if (closestBorderDist > borderThresh) {
                    var cur = closestBorderVert
                    repeat(25) {
                        val pt = meshPoints[cur]
                        if (minOf(pt.x, w - pt.x, pt.y, h - pt.y) <= borderThresh) return@repeat
                        var bestNext = -1; var bestD = Float.MAX_VALUE
                        for (nb in adj[cur]) {
                            if (nb >= n) continue
                            val key = minOf(cur, nb).toLong() * 10000L + maxOf(cur, nb)
                            if (!visitedKeys.contains(key)) {
                                val np = meshPoints[nb]
                                val nd = minOf(np.x, w - np.x, np.y, h - np.y)
                                if (nd < bestD) { bestD = nd; bestNext = nb }
                            }
                        }
                        if (bestNext == -1) return@repeat
                        val key = minOf(cur, bestNext).toLong() * 10000L + maxOf(cur, bestNext)
                        visitedKeys.add(key)
                        val p1 = meshPoints[cur]; val p2 = meshPoints[bestNext]
                        val eLen = sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y))
                        generateLightningSegs(p1.x, p1.y, p2.x, p2.y, eLen * 0.30f, 3, true, allSegs, rng)
                        cur = bestNext
                    }
                }
                // Couleur aléatoire unique pour cet éclair
                val boltHue = rng.nextFloat() * 360f
                if (allSegs.isNotEmpty()) {
                    if (lightningBolts.size >= 3) lightningBolts.removeAt(0)
                    lightningBolts.add(LightningBolt(allSegs, boltHue, 1.0f))
                }
            }
            lightningCooldown = 240 + rng.nextInt(200) // ~4–7 s @ 60 fps
        }

        // Fond transparent — laisse le thème sombre de l'app apparaître

        // Pré-pass anneau : rayon courant + angles/rayons pour collision billes (une seule itération)
        // Évite 8 billes × 24 ring points × (atan2+sqrt) = 384 ops → 24 atan2 + 24 sqrt
        var currentRingR = minOf(w, h) * 0.22f
        val ringAnglesArr = FloatArray(24)  // 24 = ringCount fixé à l'init
        val ringRadiiArr  = FloatArray(24)
        run {
            var rSum = 0f; var ri = 0
            for (pt in meshPoints) {
                if (pt.isRing) {
                    val rdx = pt.x - cx; val rdy = pt.y - cy
                    val rd = sqrt(rdx * rdx + rdy * rdy)
                    rSum += rd
                    ringAnglesArr[ri] = Math.atan2(rdy.toDouble(), rdx.toDouble()).toFloat()
                    ringRadiiArr[ri]  = rd
                    ri++
                }
            }
            if (ri > 0) currentRingR = rSum / ri
        }
        // Mur légèrement en retrait pour que les billes ne dépassent pas les sommets de l'anneau
        currentRingR * 0.88f

        // === Mise à jour positions : anneau (volume) vs billes (physique) vs grille (basses) ===
        for (pt in meshPoints) {
            if (pt.isCenter) continue  // point fixe — ne bouge jamais
            if (pt.isRing) {
                // Anneau : pulse par fréquence — direction et angle pré-calculés à l'init (0 sqrt/atan2)
                val normalizedAngle = (pt.restAngle / (2f * Math.PI.toFloat())).coerceIn(0f, 0.9999f)
                val fftBinsToUse = minOf(smoothedFft.size, 64).coerceAtLeast(1)
                val fftIdx = (normalizedAngle * fftBinsToUse).toInt().coerceIn(0, fftBinsToUse - 1)
                val freqAmp = if (fftSize > 0 && smoothedFft.isNotEmpty()) smoothedFft[fftIdx].coerceIn(0f, 1f) else amplitude
                val displaceAmt = freqAmp * minOf(w, h) * 0.48f
                val targetX = pt.restX + pt.restNdx * displaceAmt
                val targetY = pt.restY + pt.restNdy * displaceAmt
                val lf = 0.22f * deltaTime
                pt.x += (targetX - pt.x) * lf
                pt.y += (targetY - pt.y) * lf
            } else if (pt.isBall) {
                // === Gravité directionnelle vers le point cardinal de cette bille ===
                val gravStr = minOf(w, h) * 0.00010f * deltaTime
                pt.vx += pt.gravX * gravStr   // direction pré-calculée à l'init
                pt.vy += pt.gravY * gravStr

                // Friction de l'air
                pt.vx *= 0.991f
                pt.vy *= 0.991f

                // Clamping vitesse max
                val maxSpd = minOf(w, h) * 0.006f
                val spd = sqrt(pt.vx * pt.vx + pt.vy * pt.vy)
                if (spd > maxSpd) { pt.vx = pt.vx / spd * maxSpd; pt.vy = pt.vy / spd * maxSpd }

                // Mise à jour position
                pt.x += pt.vx * deltaTime
                pt.y += pt.vy * deltaTime

                // === Rebond sur la paroi déformante de l'anneau ===
                val bdx = pt.x - cx; val bdy = pt.y - cy
                val dFromC = sqrt(bdx * bdx + bdy * bdy)

                // Rayon local du mur à l'angle de la bille : sommet d'anneau le plus proche par angle
                // Utilise ringAnglesArr/ringRadiiArr pré-calculés (évite atan2+sqrt × 24 ring points)
                val ballAngle = Math.atan2(bdy.toDouble(), bdx.toDouble()).toFloat()
                var localR = minOf(w, h) * 0.22f
                var minAngDiff = Float.MAX_VALUE
                val piF = Math.PI.toFloat()
                for (ri in ringAnglesArr.indices) {
                    var diff = abs(ringAnglesArr[ri] - ballAngle)
                    if (diff > piF) diff = 2f * piF - diff
                    if (diff < minAngDiff) { minAngDiff = diff; localR = ringRadiiArr[ri] }
                }
                val localWallR = (localR * 0.86f).coerceAtLeast(20f)

                if (dFromC > localWallR && dFromC > 0.1f) {
                    val nx = bdx / dFromC; val ny = bdy / dFromC
                    val dot = pt.vx * nx + pt.vy * ny
                    if (dot > 0f) {
                        // Réflexion avec restitution 0.78 (légère perte d'énergie à chaque rebond)
                        pt.vx -= 1.78f * dot * nx
                        pt.vy -= 1.78f * dot * ny
                    }
                    // Impulsion de la paroi : quand les basses frappent, le mur "écrase" les billes
                    val wallKick = bassAmp * minOf(w, h) * 0.0022f
                    pt.vx -= nx * wallKick
                    pt.vy -= ny * wallKick
                    // Repositionner strictement à l'intérieur
                    pt.x = cx + nx * (localWallR - 1f)
                    pt.y = cy + ny * (localWallR - 1f)
                }
            } else {
                // Grille : réagit aux basses + dérive lente pour que les triangles changent de forme
                val bassDisplace = bassAmp * minOf(w, h) * 0.11f
                val driftAmt = 14f + bassDisplace
                val driftX = cos(animationPhase * 0.016f + pt.driftAngle) * driftAmt
                val driftY = sin(animationPhase * 0.013f + pt.driftAngle * 1.3f) * driftAmt
                val targetX = pt.restX + driftX
                val targetY = pt.restY + driftY
                val lf = 0.018f * deltaTime
                pt.x += (targetX - pt.x) * lf
                pt.y += (targetY - pt.y) * lf
            }
        }

        // Re-triangulation périodique
        meshFrameSkip++
        if (meshFrameSkip >= MESH_RETRI_INTERVAL) {
            meshFrameSkip = 0
            meshTriangles.clear()
            meshTriangles.addAll(bowyerWatson(meshPoints, w, h))
            buildMeshEdges()
        }

        val n = meshPoints.size

        // === 1. Remplissage très subtil — met en valeur la zone de l'anneau ===
        // hsvTemp[1/2] constants dans ce frame → fixés une fois hors de la boucle triangles
        if (!grayscale) {
            hsvTemp[1] = 0.70f
            hsvTemp[2] = 0.40f + amplitude * 0.30f
        }
        for (tri in meshTriangles) {
            if (tri[0] >= n || tri[1] >= n || tri[2] >= n) continue
            val a = meshPoints[tri[0]]
            val b = meshPoints[tri[1]]
            val c = meshPoints[tri[2]]

            val gx = (a.x + b.x + c.x) / 3f
            val gy = (a.y + b.y + c.y) / 3f
            val ringDist = sqrt((gx - cx) * (gx - cx) + (gy - cy) * (gy - cy))
            // Proximité normalisée à l'anneau (1 = sur l'anneau, 0 = loin)
            val nearRing = (1f - (abs(ringDist - ringRadius) / (ringRadius * 0.6f)).coerceIn(0f, 1f))

            val alpha = (12 + nearRing * 35f + amplitude * 28f).toInt().coerceIn(8, 75)
            if (grayscale) {
                val gray = (50 + nearRing * 90f + amplitude * 55f).toInt().coerceIn(40, 195)
                meshFillPaint.color = Color.argb(alpha, gray, gray, gray)
            } else {
                hsvTemp[0] = (colorPhase + nearRing * 70f) % 360f
                meshFillPaint.color = Color.HSVToColor(alpha, hsvTemp)
            }

            meshPath.reset()
            meshPath.moveTo(a.x, a.y)
            meshPath.lineTo(b.x, b.y)
            meshPath.lineTo(c.x, c.y)
            meshPath.close()
            canvas.drawPath(meshPath, meshFillPaint)
        }

        // === 2. Arêtes — élément dominant, couleur uniforme et nette ===
        val edgeAlpha = (125 + amplitude * 80f).toInt().coerceIn(105, 205)
        if (grayscale) {
            val edgeGray = (155 + amplitude * 70f).toInt().coerceIn(140, 225)
            meshEdgePaint.color = Color.argb(edgeAlpha, edgeGray, edgeGray, edgeGray)
        } else {
            hsvTemp[0] = (colorPhase + 185f) % 360f
            hsvTemp[1] = 0.45f
            hsvTemp[2] = 1f
            meshEdgePaint.color = Color.HSVToColor(edgeAlpha, hsvTemp)
        }
        meshEdgePaint.strokeWidth = 1.2f

        for (e in meshEdges) {
            if (e[0] >= n || e[1] >= n) continue
            val p1 = meshPoints[e[0]]
            val p2 = meshPoints[e[1]]
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, meshEdgePaint)
        }

        // === 3. Sommets de la grille — petits points discrets ===
        val dotAlpha = (120 + amplitude * 60f).toInt().coerceIn(90, 180)
        particlePaint.color = if (grayscale) Color.argb(dotAlpha, 145, 145, 145)
                              else Color.HSVToColor(dotAlpha, hsvTemp)
        for (pt in meshPoints) {
            if (!pt.isRing && !pt.isBall) {
                canvas.drawCircle(pt.x, pt.y, 2.0f, particlePaint)
            }
        }

        // === 4. Points de l'anneau — plus grands, lumineux, pulsent avec l'audio ===
        val ringGlow = 0.55f + amplitude * 0.45f
        for (pt in meshPoints) {
            if (pt.isRing) {
                if (grayscale) {
                    particlePaint.color = Color.argb((ringGlow * 70f).toInt().coerceIn(25, 70), 190, 190, 190)
                    canvas.drawCircle(pt.x, pt.y, 5f + ringGlow * 5f, particlePaint)
                    particlePaint.color = Color.argb((ringGlow * 230f).toInt().coerceIn(160, 230), 255, 255, 255)
                    canvas.drawCircle(pt.x, pt.y, 2.8f, particlePaint)
                } else {
                    particlePaint.color = Color.argb((ringGlow * 65f).toInt().coerceIn(25, 65), 70, 190, 255)
                    canvas.drawCircle(pt.x, pt.y, 5f + ringGlow * 5f, particlePaint)
                    particlePaint.color = Color.argb((ringGlow * 230f).toInt().coerceIn(160, 230), 130, 230, 255)
                    canvas.drawCircle(pt.x, pt.y, 2.8f, particlePaint)
                }
            }
        }

        // === 5. Billes — rebondissent à l'intérieur de l'anneau ===
        val ballMaxSpd = minOf(w, h) * 0.007f
        for (pt in meshPoints) {
            if (pt.isBall) {
                val spd = sqrt(pt.vx * pt.vx + pt.vy * pt.vy)
                val spdRatio = (spd / ballMaxSpd.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                if (grayscale) {
                    val ballGray = (120 + spdRatio * 135f).toInt().coerceIn(120, 255)
                    particlePaint.color = Color.argb((ringGlow * 80f).toInt().coerceIn(35, 80), ballGray, ballGray, ballGray)
                    canvas.drawCircle(pt.x, pt.y, 5f + spdRatio * 4f, particlePaint)
                    particlePaint.color = Color.argb((ringGlow * 240f).toInt().coerceIn(170, 240), 255, 255, 255)
                    canvas.drawCircle(pt.x, pt.y, 2.5f + spdRatio * 1.5f, particlePaint)
                } else {
                    val hue = 25f + spdRatio * 35f
                    hsvTemp[0] = hue; hsvTemp[1] = 0.85f; hsvTemp[2] = 1f
                    particlePaint.color = Color.HSVToColor((ringGlow * 75f).toInt().coerceIn(35, 75), hsvTemp)
                    canvas.drawCircle(pt.x, pt.y, 5f + spdRatio * 4f, particlePaint)
                    hsvTemp[0] = hue; hsvTemp[1] = 0.55f; hsvTemp[2] = 1f
                    particlePaint.color = Color.HSVToColor((ringGlow * 240f).toInt().coerceIn(170, 240), hsvTemp)
                    canvas.drawCircle(pt.x, pt.y, 2.5f + spdRatio * 1.5f, particlePaint)
                }
            }
        }

        // === 6. Point central fixe ===
        for (pt in meshPoints) {
            if (!pt.isCenter) continue
            if (grayscale) {
                particlePaint.color = Color.argb((ringGlow * 70f).toInt().coerceIn(25, 70), 200, 200, 200)
                canvas.drawCircle(pt.x, pt.y, 7f + ringGlow * 4f, particlePaint)
                particlePaint.color = Color.argb((ringGlow * 240f).toInt().coerceIn(160, 240), 255, 255, 255)
                canvas.drawCircle(pt.x, pt.y, 3.5f, particlePaint)
            } else {
                particlePaint.color = Color.argb((ringGlow * 70f).toInt().coerceIn(25, 70), 70, 190, 255)
                canvas.drawCircle(pt.x, pt.y, 7f + ringGlow * 4f, particlePaint)
                particlePaint.color = Color.argb((ringGlow * 240f).toInt().coerceIn(160, 240), 200, 240, 255)
                canvas.drawCircle(pt.x, pt.y, 3.5f, particlePaint)
            }
        }

        // === 7. Éclairs — couleur HSV aléatoire par bolt, parcourent les arêtes Delaunay ===
        val boltIter = lightningBolts.iterator()
        while (boltIter.hasNext()) {
            val bolt = boltIter.next()
            bolt.alpha -= 0.018f * deltaTime   // ~0.9 s de durée @ 60 fps
            if (bolt.alpha <= 0f) { boltIter.remove(); continue }
            val sa = bolt.alpha.coerceIn(0f, 1f)
            for (seg in bolt.segments) {
                if (grayscale) {
                    lightningGlowPaint.strokeWidth = 22f + bolt.alpha * 10f
                    lightningGlowPaint.color = Color.argb((sa * 155f).toInt().coerceIn(0, 155), 175, 175, 175)
                    canvas.drawLine(seg.x1, seg.y1, seg.x2, seg.y2, lightningGlowPaint)
                    lightningGlowPaint.strokeWidth = 11f + bolt.alpha * 5f
                    lightningGlowPaint.color = Color.argb((sa * 230f).toInt().coerceIn(0, 230), 230, 230, 230)
                    canvas.drawLine(seg.x1, seg.y1, seg.x2, seg.y2, lightningGlowPaint)
                    lightningCorePaint.strokeWidth = 3.5f
                    lightningCorePaint.color = Color.argb((sa * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
                    canvas.drawLine(seg.x1, seg.y1, seg.x2, seg.y2, lightningCorePaint)
                } else {
                    hsvTemp[0] = bolt.hue; hsvTemp[1] = 1f; hsvTemp[2] = 1f
                    lightningGlowPaint.strokeWidth = 22f + bolt.alpha * 10f
                    lightningGlowPaint.color = Color.HSVToColor((sa * 190f).toInt().coerceIn(0, 190), hsvTemp)
                    canvas.drawLine(seg.x1, seg.y1, seg.x2, seg.y2, lightningGlowPaint)
                    hsvTemp[1] = 0.50f
                    lightningGlowPaint.strokeWidth = 11f + bolt.alpha * 5f
                    lightningGlowPaint.color = Color.HSVToColor((sa * 248f).toInt().coerceIn(0, 248), hsvTemp)
                    canvas.drawLine(seg.x1, seg.y1, seg.x2, seg.y2, lightningGlowPaint)
                    hsvTemp[1] = 0.12f
                    lightningCorePaint.strokeWidth = 3.5f
                    lightningCorePaint.color = Color.HSVToColor((sa * 255f).toInt().coerceIn(0, 255), hsvTemp)
                    canvas.drawLine(seg.x1, seg.y1, seg.x2, seg.y2, lightningCorePaint)
                }
            }
        }
    }

    // ========================================================================
    // VORONOÏ PONDÉRÉ (power diagram) — visuel 18
    // ========================================================================

    private fun initVoronoi(w: Int, h: Int) {
        voronoiSeeds.clear()
        val rng = java.util.Random()
        val n = 32          // 2 graines par bande de fréquence (16 bandes d'origine)
        val nBands = 16
        for (i in 0 until n) {
            val bandIndex = i / 2   // 0..15 — les deux graines d'une paire partagent la même bande
            val px = rng.nextFloat() * w
            val py = rng.nextFloat() * h
            voronoiSeeds.add(VoronoiSeed(
                x = px, y = py,
                ax = px, ay = py,   // ancrage = position initiale
                vx = (rng.nextFloat() - 0.5f) * w * 0.002f,
                vy = (rng.nextFloat() - 0.5f) * h * 0.002f,
                hue = (i.toFloat() / n) * 360f,
                weight = minOf(w, h) * 0.10f,
                freqBin = (bandIndex.toFloat() / nBands * 60f).toInt().coerceIn(0, 59)
            ))
        }
        val scale = 8
        val bmpW = (w / scale).coerceAtLeast(1)
        val bmpH = (h / scale).coerceAtLeast(1)
        voronoiBitmap?.recycle()
        voronoiBitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        voronoiPixels = IntArray(bmpW * bmpH)
        voronoiLastTimeNs = System.nanoTime()
    }

    private fun drawVoronoi(canvas: Canvas, grayscale: Boolean = false) {
        val w = width; val h = height
        if (w <= 0 || h <= 0) return
        if (voronoiSeeds.isEmpty()) initVoronoi(w, h)

        val scale = 8   // bitmap 1/8 → ~2,5× moins de pixels qu'à 1/5, gain de perf majeur
        val bmpW = (w / scale).coerceAtLeast(1)
        val bmpH = (h / scale).coerceAtLeast(1)

        val curBmp = voronoiBitmap
        val curPixels = voronoiPixels
        if (curBmp == null || curBmp.width != bmpW || curBmp.height != bmpH || curPixels == null) {
            initVoronoi(w, h); return
        }

        // Delta time
        val nowNs = System.nanoTime()
        val dt = ((nowNs - voronoiLastTimeNs) / 1_000_000_000f * 60f).coerceIn(0.5f, 3f)
        voronoiLastTimeNs = nowNs

        // Amplitude locale depuis smoothedFft
        var amplitude = 0f
        val fftSize = smoothedFft.size
        if (fftSize > 0) {
            var sum = 0f
            val count = minOf(fftSize, 64)
            for (i in 0 until count) sum += smoothedFft[i]
            amplitude = sum / count
        }

        val n = voronoiSeeds.size
        val minDim = minOf(w, h).toFloat()

        // Mise à jour des poids : chaque graine suit sa bande de fréquence
        val minW = minDim * 0.04f
        val maxW = minDim * 0.16f   // petites formes → 32 graines ont assez d'espace
        for (seed in voronoiSeeds) {
            val binAmp = if (fftSize > 0) smoothedFft[seed.freqBin.coerceIn(0, fftSize - 1)] else amplitude
            val targetW = minW + binAmp * (maxW - minW)
            seed.weight += (targetW - seed.weight) * 0.07f
            // Lissage lent pour la couleur : évite les changements stroboscopiques
            seed.smoothedAmp += (binAmp - seed.smoothedAmp) * 0.04f
        }

        // Répulsion : les graines se repoussent proportionnellement à leurs poids combinés
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val si = voronoiSeeds[i]; val sj = voronoiSeeds[j]
                val dx = si.x - sj.x; val dy = si.y - sj.y
                val desired = (si.weight + sj.weight) * 0.52f
                val dist2 = dx * dx + dy * dy
                if (dist2 < desired * desired) {   // sqrt uniquement si proches (évite ~496 sqrt/frame)
                    val dist = sqrt(dist2).coerceAtLeast(1f)
                    val push = (desired - dist) / desired * minDim * 0.0018f
                    val nx = dx / dist; val ny = dy / dist
                    si.vx += nx * push; si.vy += ny * push
                    sj.vx -= nx * push; sj.vy -= ny * push
                }
            }
        }

        // Beat : impulsion radiale depuis le centre
        if (beatDetected) {
            val cx = w * 0.5f; val cy = h * 0.5f
            val impulse = minDim * 0.016f
            for (seed in voronoiSeeds) {
                val dx = seed.x - cx; val dy = seed.y - cy
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                seed.vx += (dx / dist) * impulse
                seed.vy += (dy / dist) * impulse
            }
        }

        // Mise à jour des positions + rebond doux sur les bords
        val wanderStr = minDim * (0.00008f + amplitude * 0.0003f)  // turbulence réduite
        val maxSpd    = minDim * 0.008f                              // plafond plus bas
        val springK   = 0.007f                                       // force de rappel vers l'ancrage
        for (seed in voronoiSeeds) {
            seed.vx *= 0.975f; seed.vy *= 0.975f
            // Turbulence légère
            seed.vx += (voronoiRng.nextFloat() - 0.5f) * wanderStr
            seed.vy += (voronoiRng.nextFloat() - 0.5f) * wanderStr
            // Rappel doux vers la position d'ancrage (ressort)
            seed.vx += (seed.ax - seed.x) * springK
            seed.vy += (seed.ay - seed.y) * springK
            // Plafond de vitesse (sqrt seulement si dépassé)
            val spd2 = seed.vx * seed.vx + seed.vy * seed.vy
            if (spd2 > maxSpd * maxSpd) { val spd = sqrt(spd2); seed.vx = seed.vx / spd * maxSpd; seed.vy = seed.vy / spd * maxSpd }
            seed.x += seed.vx * dt
            seed.y += seed.vy * dt
            val margin = seed.weight * 0.22f
            if (seed.x < margin)              { seed.x = margin;                 seed.vx =  abs(seed.vx) }
            if (seed.x > w.toFloat() - margin){ seed.x = w.toFloat() - margin;   seed.vx = -abs(seed.vx) }
            if (seed.y < margin)              { seed.y = margin;                 seed.vy =  abs(seed.vy) }
            if (seed.y > h.toFloat() - margin){ seed.y = h.toFloat() - margin;   seed.vy = -abs(seed.vy) }
        }

        // Rotation lente des teintes
        voronoiHueOffset = (voronoiHueOffset + 0.16f * dt) % 360f

        // Seuil de bordure : ≈ 3-4 pixels d'espace transparent entre les cellules
        val borderThresh = sqrt(w.toFloat() * h / n) * 4.8f
        val scaleF = scale.toFloat()

        // Snapshot plat : copie des données graines en tableaux primitifs avant la boucle pixel.
        // Évite le déreferencement ArrayList + accès objet dans la boucle interne (×86 000 fois).
        // Couleurs pré-calculées par graine : 32 appels HSVToColor au lieu de ~34 000.
        for (i in 0 until n) {
            val s = voronoiSeeds[i]
            vSnapX[i]  = s.x
            vSnapY[i]  = s.y
            val sw = s.weight; vSnapW2[i] = sw * sw
            if (grayscale) {
                // Luminosité de base différente par graine (via hue) → cellules distinguables
                val baseV = 0.28f + (s.hue / 360f) * 0.22f   // 0.28..0.50 fixe par cellule
                val v = (baseV + s.smoothedAmp * 0.45f).coerceIn(0f, 1f)
                val g = (v * 255f).toInt()
                vSnapColor[i] = Color.rgb(g, g, g)
            } else {
                vHsvTmp[0] = (s.hue + voronoiHueOffset) % 360f
                vHsvTmp[1] = 0.68f + s.smoothedAmp * 0.32f
                vHsvTmp[2] = 0.54f + s.smoothedAmp * 0.46f
                vSnapColor[i] = Color.HSVToColor(vHsvTmp)
            }
        }

        // Rendu pixel par pixel — Power diagram (Voronoï pondéré)
        for (py in 0 until bmpH) {
            val wy = py * scaleF + scaleF * 0.5f
            val rowOff = py * bmpW
            for (px in 0 until bmpW) {
                val wx = px * scaleF + scaleF * 0.5f
                var minPow  = Float.MAX_VALUE
                var minPow2 = Float.MAX_VALUE
                var closest = 0
                for (i in 0 until n) {
                    val sdx = wx - vSnapX[i]
                    val sdy = wy - vSnapY[i]
                    val pow = sdx * sdx + sdy * sdy - vSnapW2[i]
                    if (pow < minPow) { minPow2 = minPow; minPow = pow; closest = i }
                    else if (pow < minPow2) { minPow2 = pow }
                }
                curPixels[rowOff + px] = if (minPow2 - minPow < borderThresh) Color.TRANSPARENT
                                         else vSnapColor[closest]
            }
        }

        curBmp.setPixels(curPixels, 0, bmpW, 0, 0, bmpW, bmpH)
        canvas.drawBitmap(curBmp, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), voronoiPaint)
    }

    // ===================================================================
    // MODE 20 : LOSANGES DE PENROSE
    // ===================================================================

    /**
     * Génère le pavage de Penrose P3 (losanges gras/fins) via la méthode pentagrille de de Bruijn.
     * 5 familles de droites parallèles à 72° d'intervalle — leur dual donne un pavage de Penrose exact.
     * Calcul unique à l'initialisation, stocké en coordonnées écran au repos.
     */
    private fun initPenroseRhombuses() {
        penroseRhombuses.clear()
        if (cachedWidth == 0 || cachedHeight == 0) { penroseInitialized = false; return }

        val cx = cachedWidth / 2f
        val cy = cachedHeight / 2f
        // Taille d'un côté de losange en pixels — ajustée pour que le pavage couvre l'écran
        val rawTile = min(cachedWidth, cachedHeight) / 20f
        // En mode "bande étroite" (visualiseur sous la pochette), zoomer x2 pour mieux voir les losanges
        val tileScale = if (cachedHeight * 3 < cachedWidth) rawTile * 2f else rawTile

        val N = 9  // Plage d'indices de grille à explorer de chaque côté de l'origine

        // 5 vecteurs directeurs e_k = (cos(2πk/5), sin(2πk/5))
        val ex = FloatArray(5) { k -> cos(2.0 * Math.PI * k / 5).toFloat() }
        val ey = FloatArray(5) { k -> sin(2.0 * Math.PI * k / 5).toFloat() }
        // 5 normales n_k = (-sin(2πk/5), cos(2πk/5))
        val nx = FloatArray(5) { k -> -sin(2.0 * Math.PI * k / 5).toFloat() }
        val ny = FloatArray(5) { k -> cos(2.0 * Math.PI * k / 5).toFloat() }

        // Décalages identiques pour préserver la symétrie 5-fold du pavage au centre.
        // γ = 0.5 évite l'intersection quintuple au centre (Σ jk = -2.5 impossible en entiers).
        val gamma = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)

        // Marges de sélection : garder les losanges partiellement visibles
        val margin = tileScale * 3f
        val xMin = -margin;  val xMax = cachedWidth + margin
        val yMin = -margin;  val yMax = cachedHeight + margin

        // Ordre des coins du losange : (Δjr, Δks) ∈ {(0,0),(1,0),(1,1),(0,1)}
        val djrArr = intArrayOf(0, 1, 1, 0)
        val dksArr = intArrayOf(0, 0, 1, 1)

        for (r in 0 until 5) {
            for (s in r + 1 until 5) {
                // Déterminant du système 2×2 (n_r, n_s)
                val det = nx[r] * ny[s] - nx[s] * ny[r]
                if (abs(det) < 1e-6f) continue

                // Type de losange : diff=1 ou 4 → gras (72°), diff=2 ou 3 → fin (36°)
                val diff = s - r
                val type = if (diff == 1 || diff == 4) 0 else 1

                for (jr in -N..N) {
                    for (ks in -N..N) {
                        val br = jr + gamma[r]
                        val bs = ks + gamma[s]

                        // Point d'intersection de la droite jr de famille r avec la droite ks de famille s
                        val zx = (br * ny[s] - bs * ny[r]) / det
                        val zy = (nx[r] * bs - nx[s] * br) / det

                        // m_i = ⌊n_i · z_intersection − γ_i⌋ pour les 3 autres familles
                        val m0 = if (0 == r || 0 == s) 0 else floor((nx[0] * zx + ny[0] * zy - gamma[0]).toDouble()).toInt()
                        val m1 = if (1 == r || 1 == s) 0 else floor((nx[1] * zx + ny[1] * zy - gamma[1]).toDouble()).toInt()
                        val m2 = if (2 == r || 2 == s) 0 else floor((nx[2] * zx + ny[2] * zy - gamma[2]).toDouble()).toInt()
                        val m3 = if (3 == r || 3 == s) 0 else floor((nx[3] * zx + ny[3] * zy - gamma[3]).toDouble()).toInt()
                        val m4 = if (4 == r || 4 == s) 0 else floor((nx[4] * zx + ny[4] * zy - gamma[4]).toDouble()).toInt()

                        // Calcul des 4 sommets du losange
                        val vxArr = FloatArray(4)
                        val vyArr = FloatArray(4)
                        var anyVisible = false

                        for (idx in 0..3) {
                            val djr = djrArr[idx]; val dks = dksArr[idx]
                            // V = Σ_k m_k·e_k + (jr+Δjr)·e_r + (ks+Δks)·e_s
                            var x = (jr + djr) * ex[r] + (ks + dks) * ex[s]
                            var y = (jr + djr) * ey[r] + (ks + dks) * ey[s]
                            x += m0 * ex[0] + m1 * ex[1] + m2 * ex[2] + m3 * ex[3] + m4 * ex[4]
                            y += m0 * ey[0] + m1 * ey[1] + m2 * ey[2] + m3 * ey[3] + m4 * ey[4]
                            vxArr[idx] = cx + x * tileScale
                            vyArr[idx] = cy + y * tileScale
                            if (vxArr[idx] in xMin..xMax && vyArr[idx] in yMin..yMax) anyVisible = true
                        }

                        if (!anyVisible) continue
                        // Pré-calcul une fois à l'init : évite sqrt() + division par frame
                        val rcx0 = (vxArr[0] + vxArr[1] + vxArr[2] + vxArr[3]) * 0.25f
                        val rcy0 = (vyArr[0] + vyArr[1] + vyArr[2] + vyArr[3]) * 0.25f
                        val ddx0 = rcx0 - cx
                        val ddy0 = rcy0 - cy
                        val dist0 = sqrt(ddx0 * ddx0 + ddy0 * ddy0)
                        val len0 = if (dist0 > 0.5f) dist0 else 1f
                        penroseRhombuses.add(PenroseRhombus(vxArr, vyArr, type,
                            dist0 * 0.011f, ddx0 / len0, ddy0 / len0))
                    }
                }
            }
        }
        penroseInitialized = true
    }

    /**
     * Dessin du visuel Penrose :
     * - Fond sombre
     * - Chaque losange respire à l'unisson au beat (scale global depuis le centre)
     * - Une onde radiale se propage vers l'extérieur (ripple spatial)
     * - Losanges gras : une teinte qui dérive lentement
     * - Losanges fins : une autre teinte qui dérive à vitesse différente
     */
    private fun drawPenroseRhombus(canvas: Canvas) {
        if (!penroseInitialized) {
            if (cachedWidth > 0 && cachedHeight > 0) initPenroseRhombuses()
            return
        }

        // --- Animation temporelle ---
        penrosePhase += 0.022f * deltaTime

        // Pulsation beat : montée instantanée, décroissance exponentielle
        if (beatDetected) penrosePulse = 1f
        penrosePulse *= 0.91f

        // Dérive de couleur (deux vitesses différentes pour les deux types)
        penroseHueFat  = (penroseHueFat  + 0.20f * deltaTime) % 360f
        penroseHueThin = (penroseHueThin + 0.31f * deltaTime) % 360f

        // Réactivité audio
        val bassAmp = (bassLevel * 2f).coerceIn(0f, 1f)
        val beatBoost = penrosePulse

        // Respiration globale : oscillation lente + coup de pouce au beat
        val breathe = 1f + sin(penrosePhase) * 0.018f + beatBoost * 0.09f

        // Amplitude de l'onde radiale (ripple spatial)
        val rippleAmp = (0.006f + bassAmp * 0.014f + beatBoost * 0.022f) *
                        min(cachedWidth, cachedHeight)

        val cx = cachedWidth / 2f
        val cy = cachedHeight / 2f

        // Pré-calcul des 4 couleurs du frame (sat et value sont globaux, indépendants du losange)
        // → réduit Color.HSVToColor de 2×N appels/frame à 4 appels/frame
        val sat = 0.72f + bassAmp * 0.22f
        val value = 0.62f + beatBoost * 0.28f
        val fillAlpha = (170 + (beatBoost * 55f).toInt()).coerceIn(155, 225)
        val strokeValue = value * 0.45f

        penroseHsvTmp[1] = sat
        penroseHsvTmp[2] = value
        penroseHsvTmp[0] = penroseHueFat
        val colorFatFill = Color.HSVToColor(fillAlpha, penroseHsvTmp)
        penroseHsvTmp[2] = strokeValue
        val colorFatStroke = Color.HSVToColor(90, penroseHsvTmp)

        penroseHsvTmp[2] = value
        penroseHsvTmp[0] = penroseHueThin
        val colorThinFill = Color.HSVToColor(fillAlpha, penroseHsvTmp)
        penroseHsvTmp[2] = strokeValue
        val colorThinStroke = Color.HSVToColor(90, penroseHsvTmp)

        val phaseX35 = penrosePhase * 3.5f

        for (rhombus in penroseRhombuses) {
            // Onde radiale : distScaled et direction normalisée pré-calculés à l'init
            val ripple = sin(phaseX35 - rhombus.distScaled) * rippleAmp
            val rdx = rhombus.ndx * ripple
            val rdy = rhombus.ndy * ripple

            // Construire le path avec les sommets déformés
            penrosePath.reset()
            for (idx in 0..3) {
                val vx = cx + (rhombus.vx[idx] - cx) * breathe + rdx
                val vy = cy + (rhombus.vy[idx] - cy) * breathe + rdy
                if (idx == 0) penrosePath.moveTo(vx, vy) else penrosePath.lineTo(vx, vy)
            }
            penrosePath.close()

            if (rhombus.type == 0) {
                penroseFillPaint.color = colorFatFill
                penroseStrokePaint.color = colorFatStroke
            } else {
                penroseFillPaint.color = colorThinFill
                penroseStrokePaint.color = colorThinStroke
            }
            canvas.drawPath(penrosePath, penroseFillPaint)
            canvas.drawPath(penrosePath, penroseStrokePaint)
        }
    }

    // ===================================================================
    // MODE 21 : PAVAGE DE PENROSE GÉOMÉTRIQUE
    // ===================================================================

    /**
     * Construit la grille oblique à 60° qui génère des losanges cube-like (angles 60°/120°).
     *
     * Deux vecteurs de base :
     *   e1 = (step, 0)                 → direction horizontale
     *   e2 = (step*0.5, step*sin60°)   → direction à 60°
     *
     * Chaque sommet reçoit des phases et fréquences aléatoires pour son animation.
     * La grille est élargie d'une maille de chaque côté pour éviter les bords vides.
     */
    private fun initPenroseTrueRhombuses() {
        gridVertices.clear()
        gridRhombuses.clear()

        val W = cachedWidth.toFloat()
        val H = cachedHeight.toFloat()
        val step = min(W, H) / 7f   // taille d'une cellule : ~8 losanges en largeur
        gridStep = step

        // Vecteurs de base de la grille oblique 60°
        val e1x = step;            val e1y = 0f
        val e2x = step * 0.5f;    val e2y = step * 0.866f   // sin(60°) ≈ 0.866

        // Nombre de lignes pour couvrir la hauteur
        val rows = (H / e2y + 4).toInt()

        // Problème de couverture : chaque ligne supplémentaire dérive de +e2x vers la droite.
        // Au bas de l'écran (row=rows), col=0 se retrouve à x = startX + rows*e2x.
        // Pour que ce point soit toujours hors écran à gauche, on recule le départ de toute
        // la dérive maximale : startX = -(e1x + e2x + rows*e2x).
        val maxDrift = rows * e2x
        val startX   = -(e1x + e2x + maxDrift)
        val startY   = -(e1y + e2y)

        // Colonnes nécessaires pour couvrir de startX jusqu'à W + marge
        val cols = ((W - startX) / e1x + 3).toInt()

        val rng = java.util.Random(7391L)

        // Tableau d'index [row][col] → index dans gridVertices
        val idxMap = Array(rows + 1) { IntArray(cols + 1) { -1 } }

        for (row in 0..rows) {
            for (col in 0..cols) {
                val bx = startX + col * e1x + row * e2x
                val by = startY + col * e1y + row * e2y
                idxMap[row][col] = gridVertices.size
                gridVertices.add(GridVertex(
                    bx = bx, by = by,
                    px    = rng.nextFloat() * (2f * Math.PI.toFloat()),
                    py    = rng.nextFloat() * (2f * Math.PI.toFloat()),
                    freqX = 0.70f + rng.nextFloat() * 0.60f,
                    freqY = 0.70f + rng.nextFloat() * 0.60f
                ))
            }
        }

        // Allouer les buffers de positions courantes (taille connue maintenant)
        gridVx = FloatArray(gridVertices.size)
        gridVy = FloatArray(gridVertices.size)

        // Créer les losanges : chaque cellule (row,col) → 4 sommets adjacents
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val i0 = idxMap[row    ][col    ]
                val i1 = idxMap[row    ][col + 1]
                val i2 = idxMap[row + 1][col + 1]
                val i3 = idxMap[row + 1][col    ]
                if (i0 < 0 || i1 < 0 || i2 < 0 || i3 < 0) continue
                gridRhombuses.add(GridRhombus(i0, i1, i2, i3, hue = rng.nextFloat() * 360f))
            }
        }

        penroseTrueInitialized = true
    }

    /**
     * Dessin de la grille de losanges animés.
     *
     * Chaque sommet flotte selon une sinusoïde propre (phase + fréquence individuelles).
     * Amplitude au repos : quelques pixels → déformation subtile, losanges reconnaissables.
     * Sur le beat : impulsion qui s'atténue exponentiellement → coup de pouce visible.
     * Chaque losange garde sa couleur fixe ; saturation et luminosité réagissent au beat.
     */
    private fun drawPenroseTrue(canvas: Canvas) {
        if (!penroseTrueInitialized) {
            if (cachedWidth > 0 && cachedHeight > 0) initPenroseTrueRhombuses()
            return
        }

        penroseTruePhase += 0.010f * deltaTime

        if (beatDetected) penroseTruePulse = 1f
        penroseTruePulse *= 0.88f

        val bassAmp = (bassLevel * 2f).coerceIn(0f, 1f)

        // Fréquence spatiale des ondes (longueur d'onde ≈ 4.5 mailles)
        val waveK = (2.0 * Math.PI / (gridStep * 4.5)).toFloat()

        // Amplitude individuelle (flottement aléatoire propre à chaque sommet)
        val indivAmp = 3f + bassAmp * 4f

        // Amplitude des ondes (spatiales, voyagent sur toute la grille)
        // Le beat injecte une impulsion sur les ondes aussi
        val waveAmp = 7f + bassAmp * 11f + penroseTruePulse * 18f

        // Constantes pré-calculées une fois hors de la boucle sommets
        val waveAmpV = waveAmp * 0.65f
        val phase26  = penroseTruePhase * 2.6f
        val phase19  = penroseTruePhase * 1.9f

        // Recalcul des positions de tous les sommets pour cette frame
        // Chaque sommet = flottement individuel  +  onde horizontale  +  onde verticale
        for (i in gridVertices.indices) {
            val v = gridVertices[i]

            // Flottement individuel : fréquence et phase propres → texture organique
            val fx = indivAmp * sin(penroseTruePhase * v.freqX + v.px)
            val fy = indivAmp * cos(penroseTruePhase * v.freqY + v.py)

            // Onde 1 : se propage vers la droite (sur x), déplace verticalement (en y)
            val wH = waveAmp * sin(v.bx * waveK - phase26)

            // Onde 2 : se propage vers le bas (sur y), déplace horizontalement (en x)
            // Légèrement décalée en phase pour éviter la symétrie
            val wV = waveAmpV * sin(v.by * waveK - phase19 + 1.1f)

            gridVx[i] = v.bx + fx + wV
            gridVy[i] = v.by + fy + wH
        }

        canvas.drawColor(Color.argb(255, 10, 10, 15))

        val sat   = (0.45f + bassAmp * 0.30f).coerceIn(0f, 1f)
        val value = (0.70f + penroseTruePulse * 0.22f).coerceIn(0f, 1f)
        val alpha = (228 + (penroseTruePulse * 22f).toInt()).coerceIn(215, 255)

        // sat et value sont constants dans ce frame → fixés une fois (seul hue change par losange)
        penroseTrueHsvTmp[1] = sat
        penroseTrueHsvTmp[2] = value
        // couleur du contour : constante, inutile de rappeler Color.argb() à chaque losange
        penroseTrueStrokePaint.color = Color.argb(210, 12, 12, 18)

        for (rhombus in gridRhombuses) {
            val x0 = gridVx[rhombus.i0]; val y0 = gridVy[rhombus.i0]
            val x1 = gridVx[rhombus.i1]; val y1 = gridVy[rhombus.i1]
            val x2 = gridVx[rhombus.i2]; val y2 = gridVy[rhombus.i2]
            val x3 = gridVx[rhombus.i3]; val y3 = gridVy[rhombus.i3]

            val xMin = minOf(x0, x1, x2, x3);  val xMax = maxOf(x0, x1, x2, x3)
            val yMin = minOf(y0, y1, y2, y3);  val yMax = maxOf(y0, y1, y2, y3)
            if (xMax < 0f || xMin > cachedWidth || yMax < 0f || yMin > cachedHeight) continue

            penroseTruePath.reset()
            penroseTruePath.moveTo(x0, y0)
            penroseTruePath.lineTo(x1, y1)
            penroseTruePath.lineTo(x2, y2)
            penroseTruePath.lineTo(x3, y3)
            penroseTruePath.close()

            penroseTrueHsvTmp[0] = rhombus.hue
            penroseTrueFillPaint.color = Color.HSVToColor(alpha, penroseTrueHsvTmp)
            canvas.drawPath(penroseTruePath, penroseTrueFillPaint)
            canvas.drawPath(penroseTruePath, penroseTrueStrokePaint)
        }
    }

    fun setColor(color: Int) {
        accentColor = color
        barPaint.color = color
        wavePaint.color = color
        circlePaint.color = color
        invalidate()
    }
}
