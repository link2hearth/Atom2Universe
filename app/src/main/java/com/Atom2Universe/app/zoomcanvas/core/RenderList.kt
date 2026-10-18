package com.Atom2Universe.app.zoomcanvas.core

/**
 * Ce qu'il faut tracer, dans l'ordre, prêt pour le moteur graphique : des polylignes et des images
 * en coordonnées d'écran (`Float`), déjà découpées au bord de la vue. Réutilisée d'une image à
 * l'autre (aucune allocation une fois les tableaux à la bonne taille).
 *
 * Chaque élément est une « passe » : une polyligne ([runImage] = -1) ou une image ([runImage] =
 * indice dans [imageKeys], [imageSrc], [imageDst]). Les passes d'une même couche se suivent et
 * forment un groupe ([groupStart]..[groupEnd]) : une couche qui contient un coup de gomme, ou qui
 * s'efface, doit être composée à part ([groupIsolated]) pour que la gomme ne creuse qu'elle.
 */
class RenderList {
    /** Points (x, y) de toutes les polylignes, à la suite. */
    var coords = FloatArray(4096)
        private set
    var coordCount = 0
        private set

    var runCount = 0
        private set
    /** Pour chaque polyligne : indice de son premier nombre dans [coords], et son nombre de points. */
    var runStart = IntArray(256)
        private set
    var runPoints = IntArray(256)
        private set
    var runColor = IntArray(256)
        private set
    var runWidth = FloatArray(256)
        private set
    var runAlpha = FloatArray(256)
        private set
    /** -1 pour une polyligne, sinon l'indice de l'image. */
    var runImage = IntArray(256)
        private set
    /** Un coup de gomme : il efface, dans son groupe, ce qui a été tracé avant lui. */
    var runErase = BooleanArray(256)
        private set
    /** L'outil du trait ([Stroke.PEN], [Stroke.BRUSH], [Stroke.MARKER]), ou [KIND_FILL] pour un remplissage. */
    var runKind = ByteArray(256)
        private set
    /**
     * Pour une passe de texte : le texte, et à l'écran le coin haut-gauche et la taille de la police
     * (pixels), dans [runTextX], [runTextY], [runTextSize] ; null pour toute autre passe.
     */
    var runText = arrayOfNulls<TextItem>(256)
        private set
    var runTextX = FloatArray(256)
        private set
    var runTextY = FloatArray(256)
        private set
    var runTextSize = FloatArray(256)
        private set
    /**
     * L'épaisseur à l'écran en chaque point (un nombre par point, dans l'ordre de [coords]) : la
     * même partout sauf pour le pinceau, qui s'affine aux bouts.
     */
    var pointWidth = FloatArray(2048)
        private set

    var groupCount = 0
        private set
    /** Pour chaque couche tracée : ses passes [groupStart, groupEnd[, à la suite. */
    var groupStart = IntArray(4)
        private set
    var groupEnd = IntArray(4)
        private set
    /** Composer la couche à part (gomme ou fondu), puis la poser avec l'opacité [groupAlpha]. */
    var groupIsolated = BooleanArray(4)
        private set
    var groupAlpha = FloatArray(4)
        private set
    /**
     * Une couche trop dense pour être tracée trait par trait : au lieu de ses passes, la vue pose son
     * cache raster (voir `LayerCache`). Ici, la couche et la caméra telle qu'elle la voit (centre dans
     * son repère, zoom) ; null pour un groupe tracé normalement. Les passes du groupe qui suivent (le
     * trait en cours) se tracent par-dessus.
     */
    var groupLayer = arrayOfNulls<Layer>(4)
        private set
    var groupViewX = DoubleArray(4)
        private set
    var groupViewY = DoubleArray(4)
        private set
    var groupViewZoom = DoubleArray(4)
        private set
    private var openGroup = false
    private var openLayer: Layer? = null
    private var openX = 0.0
    private var openY = 0.0
    private var openZoom = 1.0

    var imageCount = 0
        private set
    /** Fichier de l'image (clé), et sa taille en pixels. */
    var imageKeys = arrayOfNulls<String>(16)
        private set
    var imagePx = IntArray(32)
        private set
    /** Rectangle source, en pixels de l'image : gauche, haut, droite, bas. */
    var imageSrc = FloatArray(64)
        private set
    /** Rectangle de destination à l'écran : gauche, haut, droite, bas (toujours dans la vue). */
    var imageDst = FloatArray(64)
        private set

    /** Nombre de couches effectivement dessinées (pour l'indicateur et les tests). */
    var layersDrawn = 0
        internal set

    /**
     * Première passe de la couche du dessus en train de s'effacer, tracée en dernier ([runCount]
     * s'il n'y en a pas). Ses passes sont opaques : l'opacité [fadeAlpha] s'applique d'un bloc à
     * toute la couche, pour que deux traits qui se croisent ne foncent pas.
     */
    var fadeStart = 0
        internal set
    var fadeAlpha = 1f
        internal set

    fun clear() {
        coordCount = 0
        runCount = 0
        imageCount = 0
        layersDrawn = 0
        fadeStart = 0
        fadeAlpha = 1f
        groupCount = 0
        openGroup = false
        openLayer = null
        for (i in groupLayer.indices) groupLayer[i] = null
    }

    internal fun beginGroup(isolated: Boolean, alpha: Float) {
        if (groupCount == groupStart.size) {
            val n = groupCount * 2
            groupStart = groupStart.copyOf(n); groupEnd = groupEnd.copyOf(n)
            groupIsolated = groupIsolated.copyOf(n); groupAlpha = groupAlpha.copyOf(n)
            groupLayer = groupLayer.copyOf(n); groupViewX = groupViewX.copyOf(n)
            groupViewY = groupViewY.copyOf(n); groupViewZoom = groupViewZoom.copyOf(n)
        }
        groupStart[groupCount] = runCount
        groupIsolated[groupCount] = isolated
        groupAlpha[groupCount] = alpha
        openGroup = true
        openLayer = null
    }

    /** Le groupe ouvert se dessine depuis le cache raster de [layer], vu de la caméra ([x], [y], [zoom]). */
    internal fun setGroupCache(layer: Layer, x: Double, y: Double, zoom: Double) {
        openLayer = layer; openX = x; openY = y; openZoom = zoom
    }

    /** Ferme le groupe ouvert ; un groupe sans passe (et sans cache) n'est pas gardé. */
    internal fun endGroup() {
        if (!openGroup) return
        openGroup = false
        if (runCount == groupStart[groupCount] && openLayer == null) return
        groupEnd[groupCount] = runCount
        groupLayer[groupCount] = openLayer
        groupViewX[groupCount] = openX
        groupViewY[groupCount] = openY
        groupViewZoom[groupCount] = openZoom
        openLayer = null
        groupCount++
    }

    internal fun beginRun(color: Int, width: Float, alpha: Float, erase: Boolean = false, kind: Int = Stroke.PEN) {
        if (runCount == runStart.size) {
            val n = runCount * 2
            runStart = runStart.copyOf(n); runPoints = runPoints.copyOf(n)
            runColor = runColor.copyOf(n); runWidth = runWidth.copyOf(n); runAlpha = runAlpha.copyOf(n)
            runImage = runImage.copyOf(n); runErase = runErase.copyOf(n); runKind = runKind.copyOf(n)
            runText = runText.copyOf(n); runTextX = runTextX.copyOf(n); runTextY = runTextY.copyOf(n); runTextSize = runTextSize.copyOf(n)
        }
        runStart[runCount] = coordCount
        runPoints[runCount] = 0
        runColor[runCount] = color
        runWidth[runCount] = width
        runAlpha[runCount] = alpha
        runImage[runCount] = -1
        runErase[runCount] = erase
        runKind[runCount] = kind.toByte()
        runText[runCount] = null
        runCount++
    }

    internal fun addText(item: TextItem, x: Double, y: Double, size: Double, alpha: Float) {
        beginRun(item.color, 0f, alpha)
        val r = runCount - 1
        runText[r] = item
        runTextX[r] = x.toFloat()
        runTextY[r] = y.toFloat()
        runTextSize[r] = size.toFloat()
    }

    internal fun addImage(item: ImageItem, src: DoubleArray, dst: DoubleArray, alpha: Float) {
        if (imageCount == imageKeys.size) {
            val n = imageCount * 2
            imageKeys = imageKeys.copyOf(n); imagePx = imagePx.copyOf(2 * n)
            imageSrc = imageSrc.copyOf(4 * n); imageDst = imageDst.copyOf(4 * n)
        }
        val i = imageCount++
        imageKeys[i] = item.key
        imagePx[2 * i] = item.pxW
        imagePx[2 * i + 1] = item.pxH
        for (k in 0 until 4) {
            imageSrc[4 * i + k] = src[k].toFloat()
            imageDst[4 * i + k] = dst[k].toFloat()
        }
        beginRun(0, 0f, alpha)
        runImage[runCount - 1] = i
    }

    companion object {
        /** Le « outil » d'une passe qui remplit un polygone au lieu de tracer un trait. */
        const val KIND_FILL = 3
    }

    internal fun addPoint(x: Double, y: Double, width: Double = runWidth[runCount - 1].toDouble()) {
        if (coordCount + 2 > coords.size) coords = coords.copyOf(coords.size * 2)
        if (coordCount / 2 >= pointWidth.size) pointWidth = pointWidth.copyOf(pointWidth.size * 2)
        pointWidth[coordCount / 2] = width.toFloat()
        coords[coordCount++] = x.toFloat()
        coords[coordCount++] = y.toFloat()
        runPoints[runCount - 1]++
    }

}

