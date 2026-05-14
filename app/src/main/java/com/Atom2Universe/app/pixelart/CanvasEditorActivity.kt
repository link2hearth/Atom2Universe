package com.Atom2Universe.app.pixelart

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import com.Atom2Universe.app.LocaleHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.canvas.*
import com.Atom2Universe.app.pixelart.data.CanvasRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import com.Atom2Universe.app.util.enableImmersiveMode

/**
 * Activity pour l'éditeur de toile infinie.
 * Permet de créer des compositions avec des objets vectoriels,
 * des images, du texte et du dessin libre.
 */
class CanvasEditorActivity : AppCompatActivity(), InfiniteCanvasListener {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var infiniteCanvas: InfiniteCanvas
    private lateinit var zoomIndicator: TextView
    private lateinit var colorButton: View

    // Outils
    private lateinit var toolButtons: Map<CanvasInteractionMode, ImageButton>
    private var currentColor = Color.BLACK
    private var currentShapeType = ShapeType.RECTANGLE
    private var currentBrushSize = 12f  // Taille du pinceau

    // Objet en cours de création (preview)
    private var previewObject: CanvasObject? = null

    // Dessin en cours
    private var currentDrawing: DrawingObject? = null

    // Indicateur de taille de pinceau
    private lateinit var brushSizeIndicator: TextView

    // Historique et boutons undo/redo/delete
    private lateinit var historyManager: CanvasHistoryManager
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnDelete: ImageButton

    // Room database repository pour sauvegarde persistante
    private lateinit var canvasRepository: CanvasRepository

    // Coroutine scope pour les opérations Room
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Image picker
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importImage(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_canvas_editor)

        // Initialiser le gestionnaire d'historique
        historyManager = CanvasHistoryManager(this)
        historyManager.onHistoryChanged = { canUndo, canRedo ->
            updateHistoryButtons(canUndo, canRedo)
        }

        // Initialiser le repository Room pour sauvegarde persistante
        canvasRepository = CanvasRepository(this)

        // Initialiser le gestionnaire de polices
        FontManager.init(this)

        setupViews()
        setupToolbar()
        setupTools()
        setupHistoryButtons()
        setupCanvas()

        // Charger le canvas sauvegardé si disponible
        loadCanvasState()
    }

    override fun onPause() {
        super.onPause()
        // Sauvegarder le canvas
        saveCanvasState()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Annuler les jobs en cours
        canvasRepository.cancel()
    }

    private fun setupViews() {
        infiniteCanvas = findViewById(R.id.infinite_canvas)
        zoomIndicator = findViewById(R.id.zoom_indicator)
        colorButton = findViewById(R.id.btn_color)
        brushSizeIndicator = findViewById(R.id.btn_brush_size)

        // Configurer le bouton de taille de pinceau
        brushSizeIndicator.setOnClickListener {
            showBrushSizeDialog()
        }
        updateBrushSizeIndicator()
    }

    private fun setupToolbar() {
        // Bouton retour
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            showReturnToMenuConfirmation()
        }

        // Bouton ajuster au contenu
        findViewById<ImageButton>(R.id.btn_fit_content).setOnClickListener {
            infiniteCanvas.fitToContent()
        }

        // Bouton grille
        val gridButton = findViewById<ImageButton>(R.id.btn_grid)
        gridButton.setOnClickListener {
            infiniteCanvas.showGrid = !infiniteCanvas.showGrid
            updateGridButtonState(gridButton)
        }

        // Bouton menu
        findViewById<ImageButton>(R.id.btn_menu).setOnClickListener { view ->
            showMenu(view)
        }

        // Bouton layers (z-index)
        findViewById<ImageButton>(R.id.btn_layers).setOnClickListener { view ->
            showLayersMenu(view)
        }
    }

    private fun updateGridButtonState(button: ImageButton) {
        button.alpha = if (infiniteCanvas.showGrid) 1f else 0.5f
    }

    private fun showLayersMenu(anchor: View) {
        val selected = infiniteCanvas.getSelectedObject()
        if (selected == null) {
            Toast.makeText(this, R.string.canvas_layer_no_selection, Toast.LENGTH_SHORT).show()
            return
        }

        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, R.string.canvas_layer_bring_front)
        popup.menu.add(0, 1, 1, R.string.canvas_layer_bring_forward)
        popup.menu.add(0, 2, 2, R.string.canvas_layer_send_backward)
        popup.menu.add(0, 3, 3, R.string.canvas_layer_send_back)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> infiniteCanvas.bringToFront(selected)
                1 -> infiniteCanvas.bringForward(selected)
                2 -> infiniteCanvas.sendBackward(selected)
                3 -> infiniteCanvas.sendToBack(selected)
            }
            true
        }

        popup.show()
    }

    private fun setupTools() {
        // Mapper les boutons aux modes
        toolButtons = mapOf(
            CanvasInteractionMode.PAN to findViewById(R.id.tool_pan),
            CanvasInteractionMode.SELECT to findViewById(R.id.tool_select),
            CanvasInteractionMode.DRAW to findViewById(R.id.tool_draw),
            CanvasInteractionMode.SHAPE to findViewById(R.id.tool_shape),
            CanvasInteractionMode.TEXT to findViewById(R.id.tool_text),
            CanvasInteractionMode.IMAGE to findViewById(R.id.tool_image)
        )

        // Configurer les listeners
        toolButtons.forEach { (mode, button) ->
            button.setOnClickListener {
                selectTool(mode)
            }
        }

        // Sélectionner l'outil par défaut
        selectTool(CanvasInteractionMode.SELECT)

        // Color picker
        colorButton.setOnClickListener {
            showColorPicker()
        }
        updateColorButton()
    }

    private fun setupHistoryButtons() {
        btnUndo = findViewById(R.id.btn_undo)
        btnRedo = findViewById(R.id.btn_redo)
        btnDelete = findViewById(R.id.btn_delete)

        btnUndo.setOnClickListener {
            performUndo()
        }

        btnRedo.setOnClickListener {
            performRedo()
        }

        btnDelete.setOnClickListener {
            deleteSelectedWithUndo()
        }

        // État initial
        updateHistoryButtons(canUndo = false, canRedo = false)
    }

    private fun updateHistoryButtons(canUndo: Boolean, canRedo: Boolean) {
        btnUndo.alpha = if (canUndo) 1f else 0.5f
        btnUndo.isEnabled = canUndo
        btnRedo.alpha = if (canRedo) 1f else 0.5f
        btnRedo.isEnabled = canRedo
    }

    private fun updateDeleteButton(hasSelection: Boolean) {
        btnDelete.visibility = if (hasSelection &&
            infiniteCanvas.interactionMode == CanvasInteractionMode.SELECT) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun performUndo() {
        val action = historyManager.undo()
        if (action == null) {
            Toast.makeText(this, R.string.canvas_nothing_to_undo, Toast.LENGTH_SHORT).show()
            return
        }

        when (action.type) {
            CanvasActionType.ADD_OBJECT -> {
                // Undo d'un ajout = supprimer l'objet
                action.objectId?.let { id ->
                    infiniteCanvas.removeObject(id)
                }
            }
            CanvasActionType.DELETE_OBJECT -> {
                // Undo d'une suppression = restaurer l'objet
                action.beforeState?.let { json ->
                    historyManager.deserializeObject(json)?.let { obj ->
                        infiniteCanvas.addObject(obj)
                        Toast.makeText(this, R.string.canvas_object_restored, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            CanvasActionType.MODIFY_OBJECT -> {
                // Undo d'une modification = restaurer l'état précédent
                action.objectId?.let { id ->
                    action.beforeState?.let { json ->
                        val oldObj = infiniteCanvas.findObject(id)
                        if (oldObj != null) {
                            infiniteCanvas.removeObject(id)
                        }
                        historyManager.deserializeObject(json)?.let { obj ->
                            infiniteCanvas.addObject(obj)
                        }
                    }
                }
            }
            CanvasActionType.CLEAR_ALL -> {
                // Undo d'un clear = restaurer tous les objets
                action.allObjectsBefore?.let { json ->
                    val objects = historyManager.deserializeAllObjects(json)
                    for (obj in objects) {
                        infiniteCanvas.addObject(obj)
                    }
                }
            }
            else -> {}
        }

        Toast.makeText(this, R.string.canvas_undo_action, Toast.LENGTH_SHORT).show()
    }

    private fun performRedo() {
        val action = historyManager.redo()
        if (action == null) {
            Toast.makeText(this, R.string.canvas_nothing_to_redo, Toast.LENGTH_SHORT).show()
            return
        }

        when (action.type) {
            CanvasActionType.ADD_OBJECT -> {
                // Redo d'un ajout = re-ajouter l'objet
                action.afterState?.let { json ->
                    historyManager.deserializeObject(json)?.let { obj ->
                        infiniteCanvas.addObject(obj)
                    }
                }
            }
            CanvasActionType.DELETE_OBJECT -> {
                // Redo d'une suppression = re-supprimer l'objet
                action.objectId?.let { id ->
                    infiniteCanvas.removeObject(id)
                }
            }
            CanvasActionType.MODIFY_OBJECT -> {
                // Redo d'une modification = appliquer le nouvel état
                action.objectId?.let { id ->
                    action.afterState?.let { json ->
                        val oldObj = infiniteCanvas.findObject(id)
                        if (oldObj != null) {
                            infiniteCanvas.removeObject(id)
                        }
                        historyManager.deserializeObject(json)?.let { obj ->
                            infiniteCanvas.addObject(obj)
                        }
                    }
                }
            }
            CanvasActionType.CLEAR_ALL -> {
                // Redo d'un clear = re-effacer tous les objets
                infiniteCanvas.clearObjects()
            }
            else -> {}
        }

        Toast.makeText(this, R.string.canvas_redo_action, Toast.LENGTH_SHORT).show()
    }

    private fun deleteSelectedWithUndo() {
        val selected = infiniteCanvas.getSelectedObject()
        if (selected == null) {
            Toast.makeText(this, R.string.pixel_art_no_selection, Toast.LENGTH_SHORT).show()
            return
        }

        // Enregistrer dans l'historique avant suppression
        historyManager.recordDeleteObject(selected)

        // Supprimer
        infiniteCanvas.deleteSelectedObject()
        Toast.makeText(this, R.string.canvas_object_deleted, Toast.LENGTH_SHORT).show()
    }

    private fun saveCanvasState() {
        // Sauvegarde immédiate via Room (appelé depuis onPause)
        activityScope.launch {
            try {
                canvasRepository.saveImmediately(
                    infiniteCanvas.getObjects(),
                    infiniteCanvas.getViewportX(),
                    infiniteCanvas.getViewportY(),
                    infiniteCanvas.getViewportZoom()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Conserver aussi la sauvegarde de l'historique (undo/redo)
        historyManager.saveHistoryToFile()
    }

    private fun loadCanvasState() {
        // Charger depuis Room en priorité
        activityScope.launch {
            try {
                val loadedProject = withContext(Dispatchers.IO) {
                    canvasRepository.loadLastProject()
                }

                if (loadedProject != null) {
                    // Ne pas utiliser addObject ici pour éviter les notifications d'auto-save
                    for (obj in loadedProject.objects) {
                        infiniteCanvas.addObjectSilently(obj)
                    }
                    infiniteCanvas.setViewport(loadedProject.viewportX, loadedProject.viewportY, loadedProject.viewportZoom)
                } else {
                    // Fallback: charger depuis les fichiers legacy
                    loadLegacyCanvasState()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback en cas d'erreur
                loadLegacyCanvasState()
            }

            // Charger l'historique undo/redo
            historyManager.loadHistoryFromFile()
        }
    }

    /**
     * Charge le canvas depuis le système de fichiers legacy (migration)
     */
    private fun loadLegacyCanvasState() {
        val result = historyManager.loadCanvasFromFile()
        if (result != null) {
            for (obj in result.objects) {
                infiniteCanvas.addObjectSilently(obj)
            }
            infiniteCanvas.setViewport(result.viewportX, result.viewportY, result.viewportZoom)
            // Migrer vers Room
            triggerAutoSave()
        }
    }

    /**
     * Déclenche une sauvegarde automatique débounced vers Room
     */
    private fun triggerAutoSave() {
        canvasRepository.triggerAutoSave(
            infiniteCanvas.getObjects(),
            infiniteCanvas.getViewportX(),
            infiniteCanvas.getViewportY(),
            infiniteCanvas.getViewportZoom()
        )
    }

    private fun selectTool(mode: CanvasInteractionMode) {
        // Actions spéciales selon le mode
        when (mode) {
            CanvasInteractionMode.SHAPE -> {
                // Afficher le menu de sélection de forme
                showShapeMenu()
                // Désélectionner l'objet actuel pour créer une nouvelle forme
                infiniteCanvas.selectObject(null)
            }
            CanvasInteractionMode.IMAGE -> {
                // Ouvrir le picker d'image
                openImagePicker()
                return  // Ne pas changer de mode
            }
            CanvasInteractionMode.TEXT -> {
                // Le texte sera créé au tap
                // Désélectionner pour éviter confusion
                infiniteCanvas.selectObject(null)
            }
            CanvasInteractionMode.DRAW -> {
                // Désélectionner pour dessiner librement
                infiniteCanvas.selectObject(null)
            }
            CanvasInteractionMode.PAN -> {
                // Garder la sélection en mode navigation
            }
            CanvasInteractionMode.SELECT -> {
                // Mode sélection normal
            }
        }

        infiniteCanvas.interactionMode = mode

        // Mettre à jour l'état visuel des boutons
        toolButtons.forEach { (buttonMode, button) ->
            button.isSelected = (buttonMode == mode)

            // Gérer le background spécial du bouton forme
            if (buttonMode == CanvasInteractionMode.SHAPE) {
                if (mode == CanvasInteractionMode.SHAPE) {
                    button.setBackgroundResource(R.drawable.shape_button_selected_bg)
                } else {
                    button.setBackgroundResource(R.drawable.tool_button_background)
                }
            }
        }
    }

    /**
     * Liste des formes disponibles avec leurs icônes
     */
    private data class ShapeItem(val type: ShapeType, val iconRes: Int)

    private val shapeItems = listOf(
        ShapeItem(ShapeType.RECTANGLE, R.drawable.ic_shape_rectangle),
        ShapeItem(ShapeType.ROUNDED_RECTANGLE, R.drawable.ic_shape_rounded_rect),
        ShapeItem(ShapeType.CIRCLE, R.drawable.ic_shape_circle),
        ShapeItem(ShapeType.OVAL, R.drawable.ic_shape_oval),
        ShapeItem(ShapeType.LINE, R.drawable.ic_shape_line),
        ShapeItem(ShapeType.DASHED_LINE, R.drawable.ic_shape_dashed_line),
        ShapeItem(ShapeType.ARROW, R.drawable.ic_shape_arrow),
        ShapeItem(ShapeType.TRIANGLE, R.drawable.ic_shape_triangle),
        ShapeItem(ShapeType.DIAMOND, R.drawable.ic_shape_diamond),
        ShapeItem(ShapeType.HEXAGON, R.drawable.ic_shape_hexagon),
        ShapeItem(ShapeType.STAR, R.drawable.ic_shape_star),
        ShapeItem(ShapeType.POLYGON, R.drawable.ic_shape_polygon)
    )

    private fun showShapeMenu() {
        val shapeButton = toolButtons[CanvasInteractionMode.SHAPE] ?: return

        // Créer le conteneur de la popup
        val gridLayout = android.widget.GridLayout(this).apply {
            columnCount = 4
            setPadding(16, 16, 16, 16)
            setBackgroundResource(R.drawable.shape_popup_bg)
        }

        // Créer la PopupWindow
        val popupWindow = android.widget.PopupWindow(
            gridLayout,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 16f
            isOutsideTouchable = true
        }

        // Ajouter les boutons de formes
        val buttonSize = (48 * resources.displayMetrics.density).toInt()
        val margin = (4 * resources.displayMetrics.density).toInt()

        shapeItems.forEach { shapeItem ->
            val button = ImageButton(this).apply {
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = buttonSize
                    height = buttonSize
                    setMargins(margin, margin, margin, margin)
                }
                setImageResource(shapeItem.iconRes)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                setPadding(8, 8, 8, 8)

                // Indicateur visuel pour la forme actuellement sélectionnée
                if (shapeItem.type == currentShapeType) {
                    setBackgroundResource(R.drawable.shape_item_bg)
                    isSelected = true
                } else {
                    setBackgroundResource(R.drawable.shape_item_bg)
                    isSelected = false
                }

                setOnClickListener {
                    currentShapeType = shapeItem.type
                    infiniteCanvas.interactionMode = CanvasInteractionMode.SHAPE
                    updateShapeButtonIcon()
                    toolButtons.forEach { (buttonMode, btn) ->
                        btn.isSelected = (buttonMode == CanvasInteractionMode.SHAPE)
                    }
                    popupWindow.dismiss()
                }
            }
            gridLayout.addView(button)
        }

        // Afficher la popup au-dessus du bouton
        popupWindow.showAsDropDown(shapeButton, 0, -shapeButton.height - gridLayout.height - 200)
    }

    /**
     * Met à jour l'icône du bouton forme avec la forme actuellement sélectionnée
     */
    private fun updateShapeButtonIcon() {
        val shapeButton = toolButtons[CanvasInteractionMode.SHAPE] ?: return
        val iconRes = shapeItems.find { it.type == currentShapeType }?.iconRes
            ?: R.drawable.ic_shape_rectangle
        shapeButton.setImageResource(iconRes)

        // Ajouter un indicateur vert quand l'outil forme est actif
        if (infiniteCanvas.interactionMode == CanvasInteractionMode.SHAPE) {
            shapeButton.setBackgroundResource(R.drawable.shape_button_selected_bg)
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        imagePickerLauncher.launch(intent)
    }

    private fun importImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                var bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    // Compression automatique si > 2Mo
                    bitmap = compressImageIfNeeded(bitmap)

                    val imageObj = ImageObject().apply {
                        setBitmap(bitmap)
                        sourceUri = uri
                        x = infiniteCanvas.getViewportX()
                        y = infiniteCanvas.getViewportY()
                        name = "Image"
                    }
                    infiniteCanvas.addObject(imageObj)
                    infiniteCanvas.selectObject(imageObj)
                    // Enregistrer dans l'historique
                    historyManager.recordAddObject(imageObj)
                    Toast.makeText(this, R.string.canvas_image_imported, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.canvas_import_error, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Compresse automatiquement une image si elle dépasse 2Mo
     * Réduit progressivement jusqu'à atteindre une taille acceptable
     */
    private fun compressImageIfNeeded(original: Bitmap): Bitmap {
        val maxSizeBytes = 2 * 1024 * 1024  // 2 Mo
        val currentSizeBytes = original.allocationByteCount

        if (currentSizeBytes <= maxSizeBytes) {
            return original  // Pas besoin de compression
        }

        // Calculer le ratio de réduction nécessaire
        val ratio = kotlin.math.sqrt(maxSizeBytes.toFloat() / currentSizeBytes)
        var newWidth = (original.width * ratio).toInt().coerceAtLeast(100)
        var newHeight = (original.height * ratio).toInt().coerceAtLeast(100)

        // S'assurer de garder le ratio d'aspect
        val aspectRatio = original.width.toFloat() / original.height
        if (newWidth.toFloat() / newHeight > aspectRatio) {
            newWidth = (newHeight * aspectRatio).toInt()
        } else {
            newHeight = (newWidth / aspectRatio).toInt()
        }

        val compressed = Bitmap.createScaledBitmap(original, newWidth, newHeight, true)

        // Si l'original était différent, on peut le recycler
        if (compressed != original) {
            original.recycle()
        }

        return compressed
    }

    private fun setupCanvas() {
        infiniteCanvas.listener = this
        // Toile vierge au démarrage - pas d'objets de démo
        // L'utilisateur peut créer ses propres objets
    }

    // ========== MENU ==========

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.canvas_editor_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_new -> {
                    newCanvas()
                    true
                }
                R.id.menu_export -> {
                    exportCanvas()
                    true
                }
                R.id.menu_delete_selected -> {
                    deleteSelected()
                    true
                }
                R.id.menu_clear_all -> {
                    clearAll()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun newCanvas() {
        val objects = infiniteCanvas.getObjects()
        if (objects.isNotEmpty()) {
            // Enregistrer dans l'historique avant d'effacer
            historyManager.recordClearAll(objects)
        }
        infiniteCanvas.clearObjects()
        infiniteCanvas.setViewport(0f, 0f, 1f)
        // Aussi supprimer le fichier de sauvegarde
        historyManager.deleteCanvasFile()
        Toast.makeText(this, R.string.canvas_new_created, Toast.LENGTH_SHORT).show()
    }

    private fun exportCanvas() {
        val bitmap = infiniteCanvas.exportToBitmap()
        if (bitmap != null) {
            // TODO: Implémenter l'export réel (save to gallery, share, etc.)
            Toast.makeText(this, R.string.canvas_exported, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.canvas_empty, Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteSelected() {
        if (infiniteCanvas.deleteSelectedObject()) {
            Toast.makeText(this, R.string.canvas_object_deleted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearAll() {
        val objects = infiniteCanvas.getObjects()
        if (objects.isNotEmpty()) {
            // Enregistrer dans l'historique avant d'effacer
            historyManager.recordClearAll(objects)
        }
        infiniteCanvas.clearObjects()
        Toast.makeText(this, R.string.canvas_cleared, Toast.LENGTH_SHORT).show()
    }

    // ========== COLOR PICKER ==========

    private fun showColorPicker() {
        val colors = arrayOf(
            // Ligne 1 - Couleurs vives
            Color.parseColor("#F44336"),  // Rouge
            Color.parseColor("#E91E63"),  // Rose
            Color.parseColor("#9C27B0"),  // Violet
            Color.parseColor("#673AB7"),  // Violet foncé
            Color.parseColor("#3F51B5"),  // Indigo
            Color.parseColor("#2196F3"),  // Bleu
            // Ligne 2 - Couleurs nature
            Color.parseColor("#03A9F4"),  // Bleu clair
            Color.parseColor("#00BCD4"),  // Cyan
            Color.parseColor("#009688"),  // Teal
            Color.parseColor("#4CAF50"),  // Vert
            Color.parseColor("#8BC34A"),  // Vert clair
            Color.parseColor("#CDDC39"),  // Lime
            // Ligne 3 - Couleurs chaudes
            Color.parseColor("#FFEB3B"),  // Jaune
            Color.parseColor("#FFC107"),  // Ambre
            Color.parseColor("#FF9800"),  // Orange
            Color.parseColor("#FF5722"),  // Orange foncé
            Color.parseColor("#795548"),  // Marron
            Color.parseColor("#607D8B"),  // Gris bleu
            // Ligne 4 - Neutres
            Color.parseColor("#000000"),  // Noir
            Color.parseColor("#424242"),  // Gris foncé
            Color.parseColor("#757575"),  // Gris
            Color.parseColor("#BDBDBD"),  // Gris clair
            Color.parseColor("#E0E0E0"),  // Gris très clair
            Color.parseColor("#FFFFFF")   // Blanc
        )

        val colorNames = arrayOf(
            "Rouge", "Rose", "Violet", "Violet foncé", "Indigo", "Bleu",
            "Bleu clair", "Cyan", "Teal", "Vert", "Vert clair", "Lime",
            "Jaune", "Ambre", "Orange", "Orange foncé", "Marron", "Gris bleu",
            "Noir", "Gris foncé", "Gris", "Gris clair", "Gris très clair", "Blanc"
        )

        // Créer une grille de couleurs
        val gridLayout = android.widget.GridLayout(this).apply {
            columnCount = 6
            setPadding(16, 16, 16, 16)
        }

        // Créer le dialog d'abord pour pouvoir le fermer depuis les listeners
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.canvas_color_picker_title)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        colors.forEachIndexed { index, color ->
            val colorView = View(this).apply {
                val size = (48 * resources.displayMetrics.density).toInt()
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(4, 4, 4, 4)
                }

                val isSelected = color == currentColor
                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color)
                    setStroke(
                        if (isSelected) 6 else 2,
                        if (isSelected) Color.parseColor("#2196F3") else Color.parseColor("#424242")
                    )
                }
                background = drawable
                contentDescription = colorNames[index]

                setOnClickListener {
                    currentColor = color
                    updateColorButton()
                    dialog.dismiss()
                }
            }
            gridLayout.addView(colorView)
        }

        val scrollView = android.widget.ScrollView(this).apply {
            addView(gridLayout)
        }

        dialog.setView(scrollView)
        dialog.show()
    }

    private fun updateColorButton() {
        // Le background est un LayerDrawable, on doit accéder au shape à l'intérieur
        val layerDrawable = colorButton.background as? android.graphics.drawable.LayerDrawable
        if (layerDrawable != null && layerDrawable.numberOfLayers > 0) {
            val shapeDrawable = layerDrawable.getDrawable(0) as? GradientDrawable
            shapeDrawable?.setColor(currentColor)
        } else {
            // Fallback : créer un nouveau drawable
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(currentColor)
                setStroke(2, Color.WHITE)
            }
            colorButton.background = drawable
        }
    }

    // ========== BRUSH SIZE ==========

    private fun showBrushSizeDialog() {
        val sizes = arrayOf(4f, 8f, 12f, 16f, 24f, 32f, 48f, 64f)
        val sizeNames = sizes.map { "${it.toInt()}px" }.toTypedArray()

        val currentIndex = sizes.indexOfFirst { it == currentBrushSize }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.canvas_brush_size_title)
            .setSingleChoiceItems(sizeNames, currentIndex) { dialog, which ->
                currentBrushSize = sizes[which]
                updateBrushSizeIndicator()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateBrushSizeIndicator() {
        brushSizeIndicator.text = currentBrushSize.toInt().toString()
    }

    // ========== CANVAS LISTENER ==========

    override fun onObjectSelected(obj: CanvasObject?) {
        if (obj != null) {
            // Afficher info sur l'objet sélectionné
            val name = obj.name.ifEmpty { obj.type.name }
            Toast.makeText(this, getString(R.string.canvas_selected, name), Toast.LENGTH_SHORT).show()
        }
        // Mettre à jour la visibilité du bouton delete
        updateDeleteButton(obj != null)
    }

    override fun onObjectModified(obj: CanvasObject) {
        // L'objet a été modifié (déplacé, redimensionné, etc.)
        // TODO: Ajouter à l'historique
    }

    override fun onViewportChanged(x: Float, y: Float, zoom: Float) {
        // Mettre à jour l'indicateur de zoom
        zoomIndicator.text = "${(zoom * 100).toInt()}%"
    }

    override fun onCanvasModified() {
        // Déclencher sauvegarde automatique débounced
        triggerAutoSave()
    }

    override fun onTap(worldX: Float, worldY: Float) {
        when (infiniteCanvas.interactionMode) {
            CanvasInteractionMode.TEXT -> {
                // Afficher dialog pour saisir le texte
                showTextInputDialog(worldX, worldY)
            }
            else -> {}
        }
    }

    override fun onLongPress(worldX: Float, worldY: Float) {
        // Long press : afficher menu contextuel pour l'objet sous le doigt
        val obj = infiniteCanvas.hitTest(worldX, worldY)
        if (obj != null) {
            infiniteCanvas.selectObject(obj)
        }
    }

    // ========== DRAG CALLBACKS ==========

    override fun onDragStart(worldX: Float, worldY: Float) {
        when (infiniteCanvas.interactionMode) {
            CanvasInteractionMode.SHAPE -> {
                // Créer un objet de prévisualisation
                previewObject = ShapeObject().apply {
                    shapeType = currentShapeType
                    fillColor = currentColor
                    strokeColor = Color.BLACK
                    strokeWidth = 2f
                    opacity = 0.5f
                    x = worldX
                    y = worldY
                    width = 0f
                    height = 0f
                }
                infiniteCanvas.addObject(previewObject!!)
            }
            CanvasInteractionMode.DRAW -> {
                // Créer un nouvel objet de dessin
                currentDrawing = DrawingObject()
                infiniteCanvas.addObject(currentDrawing!!)
                // Commencer le tracé avec la couleur et taille actuelles
                currentDrawing!!.startStroke(worldX, worldY, currentColor, currentBrushSize)
            }
            else -> {}
        }
    }

    override fun onDragMove(startX: Float, startY: Float, currentX: Float, currentY: Float) {
        when (infiniteCanvas.interactionMode) {
            CanvasInteractionMode.SHAPE -> {
                // Mettre à jour la taille de la forme
                (previewObject as? ShapeObject)?.let { shape ->
                    val left = min(startX, currentX)
                    val top = min(startY, currentY)
                    val w = abs(currentX - startX)
                    val h = abs(currentY - startY)

                    // Centrer sur le rectangle défini
                    shape.x = left + w / 2f
                    shape.y = top + h / 2f
                    shape.width = w
                    shape.height = h
                }
            }
            CanvasInteractionMode.DRAW -> {
                // Continuer le tracé
                currentDrawing?.continueStroke(currentX, currentY, currentColor, currentBrushSize)
            }
            else -> {}
        }
    }

    override fun onDragEnd(startX: Float, startY: Float, endX: Float, endY: Float) {
        when (infiniteCanvas.interactionMode) {
            CanvasInteractionMode.SHAPE -> {
                (previewObject as? ShapeObject)?.let { shape ->
                    // Vérifier que la forme a une taille minimale
                    if (shape.width > 10f || shape.height > 10f) {
                        shape.opacity = 1f
                        // Enregistrer dans l'historique
                        historyManager.recordAddObject(shape)
                    } else {
                        // Trop petit : supprimer
                        infiniteCanvas.removeObject(shape.id)
                    }
                }
                previewObject = null
            }
            CanvasInteractionMode.DRAW -> {
                currentDrawing?.let { drawing ->
                    if (drawing.getStrokeCount() > 0) {
                        drawing.endStroke()
                        // Enregistrer dans l'historique
                        historyManager.recordAddObject(drawing)
                    } else {
                        infiniteCanvas.removeObject(drawing.id)
                    }
                }
                currentDrawing = null
            }
            else -> {}
        }
    }

    override fun onDragCancel() {
        // Annuler le dessin/forme en cours (ex: passage en mode zoom)
        when (infiniteCanvas.interactionMode) {
            CanvasInteractionMode.SHAPE -> {
                previewObject?.let { shape ->
                    infiniteCanvas.removeObject(shape.id)
                }
                previewObject = null
            }
            CanvasInteractionMode.DRAW -> {
                currentDrawing?.let { drawing ->
                    infiniteCanvas.removeObject(drawing.id)
                }
                currentDrawing = null
            }
            else -> {}
        }
    }

    // ========== CRÉATION D'OBJETS ==========

    private fun showTextInputDialog(x: Float, y: Float) {
        // Créer le layout principal
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        // Champ de texte
        val editText = EditText(this).apply {
            hint = getString(R.string.canvas_text_hint)
            setSingleLine(false)
            minLines = 2
        }
        container.addView(editText)

        // Espacement
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24
            )
        })

        // Label police
        container.addView(TextView(this).apply {
            text = getString(R.string.canvas_text_font)
            setTextColor(Color.GRAY)
        })

        // Sélecteur de police
        val fontSpinner = Spinner(this)
        val fontNames = FontManager.getAvailableFontNames()
        val fontAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fontNames)
        fontAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fontSpinner.adapter = fontAdapter
        // Sélectionner "Sans Serif" par défaut
        val defaultIndex = fontNames.indexOf("Sans Serif").coerceAtLeast(0)
        fontSpinner.setSelection(defaultIndex)
        container.addView(fontSpinner)

        // Espacement
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 16
            )
        })

        // Label taille
        val sizeLabel = TextView(this).apply {
            text = getString(R.string.canvas_text_size, 24)
            setTextColor(Color.GRAY)
        }
        container.addView(sizeLabel)

        // Slider taille
        val sizeSeekBar = SeekBar(this).apply {
            max = 200 - 12  // Taille min 12, max 200
            progress = 24 - 12  // Défaut 24
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    sizeLabel.text = getString(R.string.canvas_text_size, progress + 12)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(sizeSeekBar)

        // Espacement
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 16
            )
        })

        // Style (gras/italique)
        val styleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val boldCheckBox = android.widget.CheckBox(this).apply {
            text = getString(R.string.canvas_text_bold)
        }
        styleContainer.addView(boldCheckBox)

        val italicCheckBox = android.widget.CheckBox(this).apply {
            text = getString(R.string.canvas_text_italic)
        }
        styleContainer.addView(italicCheckBox)

        container.addView(styleContainer)

        // Wrapper scrollable
        val scrollView = ScrollView(this).apply {
            addView(container)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.canvas_text_dialog_title)
            .setView(scrollView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val inputText = editText.text.toString()
                if (inputText.isNotBlank()) {
                    val selectedFontName = fontSpinner.selectedItem as String
                    val fontSize = (sizeSeekBar.progress + 12).toFloat()
                    val isBold = boldCheckBox.isChecked
                    val isItalic = italicCheckBox.isChecked

                    val fontStyle = when {
                        isBold && isItalic -> FontStyle.BOLD_ITALIC
                        isBold -> FontStyle.BOLD
                        isItalic -> FontStyle.ITALIC
                        else -> FontStyle.NORMAL
                    }

                    val textObj = TextObject().apply {
                        text = inputText
                        this.fontSize = fontSize
                        this.fontStyle = fontStyle
                        this.fontDisplayName = selectedFontName
                        this.customTypeface = FontManager.getTypeface(this@CanvasEditorActivity, selectedFontName)
                        textColor = currentColor
                        this.x = x
                        this.y = y
                    }
                    infiniteCanvas.addObject(textObj)
                    infiniteCanvas.selectObject(textObj)
                    // Enregistrer dans l'historique
                    historyManager.recordAddObject(textObj)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showReturnToMenuConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_return_menu_title)
            .setMessage(R.string.confirm_return_menu_message)
            .setPositiveButton(R.string.confirm_return_menu_yes) { _, _ ->
                onBackPressedDispatcher.onBackPressed()
            }
            .setNegativeButton(R.string.confirm_return_menu_no, null)
            .show()
    }
}
