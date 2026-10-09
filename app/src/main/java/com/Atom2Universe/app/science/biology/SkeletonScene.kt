package com.Atom2Universe.app.science.biology

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceView
import android.view.View
import com.google.android.filament.Camera
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.View as FilamentView
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/** Toutes les interactions Filament restent sur le thread principal. */
@SuppressLint("ClickableViewAccessibility")
class SkeletonScene(
    private val surface: SurfaceView,
    private val catalog: AnatomyCatalog,
    showExternalGenitals: Boolean = false,
    private val onSelection: (AnatomicalStructure?) -> Unit,
) {
    private val viewer: ModelViewer
    private val handler = Handler(Looper.getMainLooper())
    private val entities = mutableMapOf<Int, AnatomicalStructure>()
    private class RenderEntry(val item: AnatomicalStructure, val instance: Int, val material: MaterialInstance,
        val externalHiddenVariant: Boolean) {
        var visible: Boolean? = null
        var style = -1
    }
    private val renderEntries = mutableListOf<RenderEntry>()
    private val hidden = catalog.defaultHidden.filter { catalog.byId[it]?.groupId in catalog.defaultGroups }.toMutableSet()
    private var isolated: String? = null
    var visibleLayers: Set<AnatomyLayer> = catalog.defaultLayers
        private set
    var visibleGroups: Set<String> = catalog.defaultGroups
        private set
    var selected: AnatomicalStructure? = null
        private set
    var externalGenitalsVisible = showExternalGenitals
        private set
    var coloredLayers: Set<AnatomyLayer> = emptySet()
        private set
    val visibleStructureCount: Int get() = catalog.structures.count(::isVisible)
    val isFiltered: Boolean get() = isolated != null || hidden.isNotEmpty() ||
        visibleLayers != catalog.layers || visibleGroups != catalog.groupIds
    private val history = AnatomyHistory<AnatomyDisplayState>()
    val canUndo get() = history.canUndo
    val canRedo get() = history.canRedo
    private var changing = false
    private var gestureStart: AnatomyDisplayState? = null
    private var interactionVersion = 0L
    private var pendingPicks = 0
    private var running = false
    private var disposed = false
    private var dirtyFrames = 8
    private var yaw = 0.0
    private var pitch = 0.0
    private var distance = 3.0
    private var target = doubleArrayOf(0.0, .85, 0.0)
    private var lastX = 0f
    private var lastY = 0f
    private var previousPointers = 0
    private var multiTouch = false
    private var indirect: IndirectLight? = null
    private var interactiveResolution = false
    private val restoreResolution = Runnable {
        if (!disposed) {
            interactiveResolution = false
            applyRenderResolution()
            invalidate()
        }
    }

    init {
        Utils.init()
        // Caméra contrôlée ici : le recentrage sur un petit os conserve une orbite autour de cet os.
        viewer = ModelViewer(surface, manipulator = null)
        viewer.autoPlayAnimations = false
        viewer.cameraNear = .002f
        viewer.cameraFar = 50f
        viewer.renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(.012, .025, .035, 1.0)
        }
        viewer.view.ambientOcclusionOptions = FilamentView.AmbientOcclusionOptions().apply { enabled = true }
        viewer.view.multiSampleAntiAliasingOptions = FilamentView.MultiSampleAntiAliasingOptions().apply {
            enabled = true
            sampleCount = 2
        }
        viewer.view.antiAliasing = FilamentView.AntiAliasing.FXAA
        val lights = viewer.engine.lightManager
        val lightColor = lights.getColor(lights.getInstance(viewer.light), null)
        // Réutilise l'entité gérée par ModelViewer ; build remplace son composant lumière.
        LightManager.Builder(LightManager.Type.SUN)
            .color(lightColor[0], lightColor[1], lightColor[2])
            .direction(-.5f, -1f, -.8f)
            .intensity(85_000f)
            .castShadows(true)
            .shadowOptions(LightManager.ShadowOptions().apply {
                // Stabilise la projection des ombres pendant les mouvements de caméra.
                stable = true
                mapSize = 2048
            })
            .build(viewer.engine, viewer.light)
        indirect = IndirectLight.Builder().irradiance(1, floatArrayOf(.8f, .85f, .95f))
            .intensity(22_000f).build(viewer.engine)
        viewer.scene.indirectLight = indirect
        surface.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!disposed) { applyRenderResolution(); invalidate() }
        }
        surface.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                // ModelViewer détruit son moteur lors du détachement ; aucun accès natif ensuite.
                disposed = true
                pause()
            }
        })
        val scale = ScaleGestureDetector(surface.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                distance = (distance / detector.scaleFactor).coerceIn(.018, 12.0)
                cameraMoving()
                invalidate()
                return true
            }
        })
        val tap = GestureDetector(surface.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!multiTouch) pick(e.x, e.y)
                surface.performClick()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!multiTouch) selected?.takeIf { it.hasMesh }?.let { focus(it) }
                return true
            }
        })
        surface.setOnTouchListener { _, event ->
            if (disposed) return@setOnTouchListener false
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                interactionVersion++
                multiTouch = false
                gestureStart = snapshot()
            }
            if (event.pointerCount > 1) multiTouch = true
            scale.onTouchEvent(event)
            tap.onTouchEvent(event)
            val count = event.pointerCount
            var x = 0f
            var y = 0f
            for (i in 0 until count) { x += event.getX(i); y += event.getY(i) }
            x /= count
            y /= count
            if (event.actionMasked == MotionEvent.ACTION_MOVE && count == previousPointers) {
                val dx = x - lastX
                val dy = y - lastY
                if (dx != 0f || dy != 0f) cameraMoving()
                if (count == 1 && !multiTouch) {
                    yaw -= dx * .006
                    pitch = (pitch + dy * .006).coerceIn(-1.45, 1.45)
                } else if (count >= 2) {
                    val units = 2 * distance * tan(Math.toRadians(35.0) / 2) / surface.height.coerceAtLeast(1)
                    target[0] -= (dx * cos(yaw) + dy * sin(pitch) * sin(yaw)) * units
                    target[1] += dy * cos(pitch) * units
                    target[2] += (dx * sin(yaw) - dy * sin(pitch) * cos(yaw)) * units
                }
                invalidate()
            }
            lastX = x; lastY = y; previousPointers = count
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                previousPointers = 0
                gestureStart?.let { before ->
                    val after = snapshot()
                    if (before.camera != after.camera) {
                        history.record(before, after)
                        onSelection(selected)
                    }
                }
                gestureStart = null
            }
            true
        }
    }

    fun load(buffer: ByteBuffer) {
        check(!disposed)
        viewer.loadModelGlb(buffer)
        val asset = requireNotNull(viewer.asset) { "Invalid anatomy GLB" }
        val manager = viewer.engine.renderableManager
        for (entity in asset.renderableEntities) {
            val meshName = asset.getName(entity)
            val variantOf = catalog.externalHiddenVariants[meshName]
            val structure = catalog.byId[variantOf ?: meshName] ?: error("Unknown mesh identity")
            entities[entity] = structure
            val instance = manager.getInstance(entity)
            manager.setCastShadows(instance, structure.opacity == 1f)
            renderEntries += RenderEntry(structure, instance, manager.getMaterialInstanceAt(instance, 0), variantOf != null)
        }
        require(entities.size == catalog.structures.count { it.hasMesh } + catalog.externalHiddenVariants.size)
        require(entities.values.map { it.id }.toSet() == catalog.structures.filter { it.hasMesh }.map { it.id }.toSet())
        frameVisible()
        updateAppearance()
    }

    private fun pick(x: Float, y: Float) {
        if (disposed || surface.width <= 0 || surface.height <= 0) return
        // Cornée et cristallin restent sélectionnables malgré leur transparence.
        viewer.view.isTransparentPickingEnabled = catalog.structures.any { it.opacity < 1f && isVisible(it) }
        pendingPicks++
        val version = ++interactionVersion
        viewer.view.pick(x.toInt().coerceIn(0, surface.width - 1),
            (surface.height - 1 - y.toInt()).coerceIn(0, surface.height - 1), handler) { result ->
            if (!disposed) {
                pendingPicks--
                if (pendingPicks == 0) viewer.view.isTransparentPickingEnabled = false
                if (version == interactionVersion) {
                    val item = entities[result.renderable]
                    if (item == null || isVisible(item)) select(item)
                }
            }
        }
        invalidate()
    }

    private fun snapshot() = AnatomyDisplayState(
        hidden.toSet(), isolated, visibleLayers.map { it.name }.toSet(), visibleGroups.toSet(),
        coloredLayers.map { it.name }.toSet(), selected?.id, listOf(yaw, pitch, distance, *target.toTypedArray()),
    )

    private fun applyState(state: AnatomyDisplayState) {
        interactionVersion++
        gestureStart = null
        hidden.clear(); hidden.addAll(state.hidden.filter { catalog.byId[it]?.hasMesh == true })
        val externalIsolation = !externalGenitalsVisible && state.isolated in catalog.externalGenitalIds
        isolated = state.isolated?.takeIf { catalog.byId[it]?.hasMesh == true && !externalIsolation }
        visibleLayers = catalog.layers.filter { it.name in state.layers }.toSet()
        visibleGroups = state.groups intersect catalog.groupIds
        coloredLayers = AnatomyLayer.entries.filter { it.name in state.colored }.toSet()
        if (state.camera.size == 6 && state.camera.all(Double::isFinite)) {
            yaw = state.camera[0]; pitch = state.camera[1]; distance = state.camera[2].coerceIn(.018, 12.0)
            target = state.camera.drop(3).toDoubleArray()
        }
        selected = catalog.byId[state.selected]?.takeIf { !it.hasMesh || isVisible(it) }
        if (externalIsolation) frameVisible()
        updateAppearance()
        onSelection(selected)
    }

    private inline fun change(action: () -> Unit) {
        if (disposed) return
        if (changing) { action(); return }
        interactionVersion++
        gestureStart = null
        val before = snapshot()
        changing = true
        try { action() } finally { changing = false }
        selected = selected?.takeIf { !it.hasMesh || isVisible(it) }
        history.record(before, snapshot())
        updateAppearance()
        onSelection(selected)
    }

    fun undo() { if (!disposed) history.undo()?.let(::applyState) }
    fun redo() { if (!disposed) history.redo()?.let(::applyState) }

    fun select(item: AnatomicalStructure?, reveal: Boolean = false) {
        if (disposed) return
        if (item != null && !externalGenitalsVisible && item.id in catalog.externalGenitalIds) return
        interactionVersion++
        if (reveal && item != null) {
            change {
                hidden.remove(item.id)
                isolated = null
                visibleGroups = visibleGroups + item.groupId
                visibleLayers = visibleLayers + item.layer
                selected = item
                if (item.hasMesh) frame(listOf(item))
            }
        } else {
            selected = item
            updateAppearance()
            onSelection(item)
        }
    }

    fun setVisibleLayers(value: Set<AnatomyLayer>) = change {
        visibleLayers = value.toSet()
        isolated = null
    }

    fun setLayerVisible(layer: AnatomyLayer, enabled: Boolean) = change {
        visibleLayers = if (enabled) visibleLayers + layer else visibleLayers - layer
        val groups = catalog.groups.filter { it.layer == layer }.map { it.id }.toSet()
        if (enabled && visibleGroups.intersect(groups).isEmpty()) visibleGroups = visibleGroups + groups
        isolated = null
    }

    fun setVisibleGroups(layer: AnatomyLayer, value: Set<String>) = change {
        val allowed = catalog.groups.filter { it.layer == layer }.map { it.id }.toSet()
        visibleGroups = updateLayerGroups(visibleGroups, allowed, value)
        if (value.any { it in allowed }) visibleLayers = visibleLayers + layer
        isolated = null
    }

    fun isolateSelection() {
        val item = selected?.takeIf { it.hasMesh } ?: return
        change {
            isolated = item.id
            hidden.remove(item.id)
            frame(listOf(item))
        }
    }

    fun hideSelection() {
        val item = selected?.takeIf { it.hasMesh } ?: return
        change {
            hidden += item.id
            isolated = null
            selected = null
        }
    }

    fun showAll() = change {
        hidden.clear(); isolated = null
        visibleLayers = catalog.layers
        visibleGroups = catalog.groupIds
        yaw = 0.0; pitch = 0.0; frameVisible()
    }

    /** One undoable view change, restricted to real meshes in the current atlas. */
    fun showJourneyStructures(ids: Set<String>) {
        val items = catalog.structures.filter { it.id in ids && it.hasMesh &&
            (externalGenitalsVisible || it.id !in catalog.externalGenitalIds) }
        if (items.isEmpty()) return
        change {
            isolated = null
            hidden.clear()
            hidden.addAll(catalog.structures.map { it.id }.toSet() - items.map { it.id }.toSet())
            visibleLayers = items.map { it.layer }.toSet()
            visibleGroups = items.map { it.groupId }.toSet()
            selected = null
            yaw = 0.0; pitch = 0.0
            frame(items)
        }
    }

    fun setColored(layer: AnatomyLayer, value: Boolean) = change {
        coloredLayers = if (value) coloredLayers + layer else coloredLayers - layer
    }

    fun setExternalGenitalsVisible(visible: Boolean) {
        if (disposed || externalGenitalsVisible == visible) return
        // Option globale hors historique : « Tout afficher », la recherche et
        // Annuler ne doivent pas réactiver l'affichage explicitement désactivé.
        externalGenitalsVisible = visible
        interactionVersion++
        gestureStart = null
        if (!visible && isolated in catalog.externalGenitalIds) {
            isolated = null
            frameVisible()
        }
        selected = selected?.takeIf { !it.hasMesh || isVisible(it) }
        updateAppearance()
        onSelection(selected)
    }

    private fun isVisible(item: AnatomicalStructure) = item.hasMesh && item.id !in hidden &&
        (externalGenitalsVisible || item.id !in catalog.externalGenitalIds) &&
        (isolated == null || isolated == item.id) && item.layer in visibleLayers && item.groupId in visibleGroups

    private fun updateAppearance() {
        if (disposed) return
        val manager = viewer.engine.renderableManager
        for (entry in renderEntries) {
            val item = entry.item
            val visible = isVisible(item) && (item.id !in catalog.structuresWithExternalVariant ||
                entry.externalHiddenVariant != externalGenitalsVisible)
            if (entry.visible != visible) {
                manager.setLayerMask(entry.instance, 0xff, if (visible) 1 else 0)
                entry.visible = visible
            }
            val style = when {
                selected?.id == item.id -> 2
                item.layer in coloredLayers -> 1
                else -> 0
            }
            // Une sélection ne doit mettre à jour que l'ancienne et la nouvelle
            // structure, pas renvoyer des milliers de matériaux au moteur natif.
            if (entry.style != style) {
                val color = when {
                    style == 2 -> floatArrayOf(.16f, .85f, .76f)
                    style == 1 -> item.groupColor
                    item.region == AnatomyRegion.TEETH -> item.region.color
                    else -> item.baseColor ?: item.layer.color
                }
                val alpha = if (style == 2) 1f else item.opacity
                entry.material.setParameter("baseColorFactor", color[0], color[1], color[2], alpha)
                entry.style = style
            }
        }
        invalidate()
    }

    fun focus(item: AnatomicalStructure) {
        if (item.hasMesh) change { frame(listOf(item)) }
    }

    fun viewFrom(angle: Double) = change { yaw = angle; pitch = 0.0 }
    fun resetCamera() = change { yaw = 0.0; pitch = 0.0; frameVisible() }
    private fun frameVisible() = frame(catalog.structures.filter { isVisible(it) })
    private fun frame(items: List<AnatomicalStructure>) {
        if (items.isEmpty()) return
        val bounds = items.map { item ->
            catalog.externalHiddenBounds[item.id]?.takeUnless { externalGenitalsVisible }
                ?: AnatomyBounds(item.minimum, item.maximum)
        }
        val low = DoubleArray(3) { axis -> bounds.minOf { it.minimum[axis].toDouble() } }
        val high = DoubleArray(3) { axis -> bounds.maxOf { it.maximum[axis].toDouble() } }
        target = DoubleArray(3) { (low[it] + high[it]) / 2 }
        val radius = sqrt((0..2).sumOf { ((high[it] - low[it]) / 2).pow(2) })
        val aspect = (surface.width.toDouble() / surface.height.coerceAtLeast(1)).coerceAtLeast(.2)
        val angle = atan(tan(Math.toRadians(35.0) / 2) * min(1.0, aspect))
        // La barre de commandes recouvre 64 dp en haut du viewport, notamment en paysage.
        val toolbarFraction = (64 * surface.resources.displayMetrics.density / surface.height.coerceAtLeast(1)).coerceIn(0f, .35f)
        distance = (radius / sin(angle) * 1.08 / (1 - toolbarFraction)).coerceIn(.025, 12.0)
        invalidate()
    }

    private fun encode(state: AnatomyDisplayState) = JSONObject().apply {
        put("hidden", JSONArray(state.hidden.toList())); put("isolated", state.isolated)
        put("layers", JSONArray(state.layers.toList())); put("groups", JSONArray(state.groups.toList()))
        put("colored", JSONArray(state.colored.toList())); put("selected", state.selected)
        put("camera", JSONArray(state.camera))
    }

    private fun decode(value: JSONObject): AnatomyDisplayState {
        fun strings(key: String): Set<String> = value.optJSONArray(key)?.let { array ->
            (0 until array.length()).map { array.getString(it) }.toSet()
        }.orEmpty()
        fun id(key: String) = value.optString(key).takeIf { it.isNotBlank() }
        val camera = value.getJSONArray("camera")
        return AnatomyDisplayState(strings("hidden"), id("isolated"), strings("layers"), strings("groups"),
            strings("colored"), id("selected"), (0 until camera.length()).map { camera.getDouble(it) })
    }

    fun save(bundle: Bundle) {
        val state = JSONObject().put("current", encode(snapshot()))
        fun changes(entries: List<AnatomyHistory.Change<AnatomyDisplayState>>) = JSONArray().apply {
            entries.forEach { put(JSONObject().put("before", encode(it.before)).put("after", encode(it.after))) }
        }
        state.put("undo", changes(history.undoEntries)).put("redo", changes(history.redoEntries))
        // Compression : conserver l'historique à la rotation sans gonfler le Bundle Android.
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(state.toString().toByteArray(Charsets.UTF_8)) }
        bundle.putByteArray("bio_display_state_v2", bytes.toByteArray())
    }

    fun restore(bundle: Bundle) {
        val bytes = bundle.getByteArray("bio_display_state_v2")
        if (bytes != null) {
            val state = JSONObject(GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) })
            fun changes(key: String): List<AnatomyHistory.Change<AnatomyDisplayState>> {
                val array = state.optJSONArray(key) ?: return emptyList()
                return (0 until array.length()).map { i ->
                    val item = array.getJSONObject(i)
                    AnatomyHistory.Change(decode(item.getJSONObject("before")), decode(item.getJSONObject("after")))
                }
            }
            history.restore(changes("undo"), changes("redo"))
            applyState(decode(state.getJSONObject("current")))
            return
        }
        // Migration des anciens filtres globaux vers les groupes propres à chaque couche.
        fun names(key: String, defaults: Set<String>) = if (bundle.containsKey(key))
            bundle.getStringArrayList(key).orEmpty().toSet() else defaults
        val regions = names("bio_regions", bundle.getString("bio_region")?.let { setOf(it) }
            ?: AnatomyRegion.entries.map { it.name }.toSet())
        val systems = names("bio_organ_systems", OrganSystem.entries.map { it.name }.toSet())
        val groups = catalog.structures.filter { it.region.name in regions &&
            (it.organSystem == null || it.organSystem.name in systems) }.map { it.groupId }.toSet()
        val allLayers = AnatomyLayer.entries.map { it.name }.toSet()
        applyState(AnatomyDisplayState(
            bundle.getStringArrayList("bio_hidden").orEmpty().toSet(), bundle.getString("bio_isolated"),
            names("bio_layers", catalog.defaultLayers.map { it.name }.toSet()), groups,
            names("bio_colored_layers", if (bundle.getBoolean("bio_colored")) allLayers else emptySet()),
            bundle.getString("bio_selected"), bundle.getDoubleArray("bio_camera")?.toList() ?: snapshot().camera,
        ))
    }

    private fun cameraMoving() {
        if (!interactiveResolution) {
            interactiveResolution = true
            applyRenderResolution()
        }
        handler.removeCallbacks(restoreResolution)
        handler.postDelayed(restoreResolution, 180L)
    }

    private fun applyRenderResolution() {
        val pixels = surface.width.toDouble() * surface.height
        val scale = if (interactiveResolution && pixels > 0)
            sqrt(1_500_000.0 / pixels).coerceIn(.60, 1.0).toFloat() else 1f
        viewer.view.dynamicResolutionOptions = FilamentView.DynamicResolutionOptions().apply {
            enabled = scale < 1f
            homogeneousScaling = true
            // Une échelle fixe fonctionne aussi sans mesure du temps GPU.
            minScale = scale
            maxScale = scale
            quality = FilamentView.QualityLevel.LOW
        }
    }

    private fun invalidate() { dirtyFrames = 8 }
    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running || disposed) return
            if (surface.width > 0 && surface.height > 0 && (dirtyFrames > 0 || viewer.progress < 1f)) {
                val camera = viewer.camera
                camera.setProjection(35.0, surface.width.toDouble() / surface.height, .002, 50.0, Camera.Fov.VERTICAL)
                camera.lookAt(target[0] + distance * cos(pitch) * sin(yaw), target[1] + distance * sin(pitch),
                    target[2] + distance * cos(pitch) * cos(yaw), target[0], target[1], target[2], 0.0, 1.0, 0.0)
                if (viewer.render(frameTimeNanos)) dirtyFrames = (dirtyFrames - 1).coerceAtLeast(0)
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun resume() {
        if (running || disposed) return
        running = true
        invalidate()
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(callback)
        handler.removeCallbacks(restoreResolution)
        if (interactiveResolution) {
            interactiveResolution = false
            if (!disposed) applyRenderResolution()
        }
    }

    /** Appelé avant le détachement : ModelViewer reste propriétaire du moteur et du modèle. */
    fun releaseOwnedResources() {
        pause()
        if (disposed) return
        viewer.scene.indirectLight = null
        indirect?.let { viewer.engine.destroyIndirectLight(it) }
        indirect = null
        renderEntries.clear()
        surface.setOnTouchListener(null)
        disposed = true
    }
}
