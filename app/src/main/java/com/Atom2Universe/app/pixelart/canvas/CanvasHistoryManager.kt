package com.Atom2Universe.app.pixelart.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Types d'actions pour l'historique du canvas
 */
enum class CanvasActionType {
    ADD_OBJECT,         // Ajout d'un objet
    DELETE_OBJECT,      // Suppression d'un objet
    MODIFY_OBJECT,      // Modification d'un objet
    MOVE_OBJECT,        // Déplacement d'un objet
    CLEAR_ALL,          // Effacement de tous les objets
    IMPORT_IMAGE,       // Import d'une image
    BATCH_ACTION        // Action groupée (plusieurs modifications)
}

/**
 * Représente une action dans l'historique
 * Stocke l'état avant et après pour pouvoir undo/redo
 */
data class CanvasAction(
    val id: Long,
    val type: CanvasActionType,
    val timestamp: Long = System.currentTimeMillis(),
    val objectId: String? = null,
    val beforeState: String? = null,    // JSON de l'objet avant modification
    val afterState: String? = null,     // JSON de l'objet après modification
    val allObjectsBefore: String? = null, // Pour CLEAR_ALL : tous les objets avant
    val estimatedMemoryBytes: Long = 0
)

/**
 * Gestionnaire d'historique pour le canvas infini.
 *
 * Fonctionnalités :
 * - Undo/Redo avec limite de 100Mo de cache
 * - Sauvegarde dans un fichier séparé (pas dans SaveCore)
 * - Sérialisation JSON des objets canvas
 * - Gestion mémoire automatique
 */
class CanvasHistoryManager(context: Context) {

    private val appContext = context.applicationContext
    private val saveLock = Any()

    // Pile d'actions pour undo/redo
    private val undoStack = mutableListOf<CanvasAction>()
    private val redoStack = mutableListOf<CanvasAction>()

    // Compteur d'actions
    private var nextActionId = 1L

    // Mémoire utilisée par l'historique
    private var totalMemoryBytes = 0L

    // Limite de mémoire (100 Mo)
    companion object {
        private const val MAX_MEMORY_BYTES = 100L * 1024 * 1024  // 100 Mo
        private const val SAVE_FILE_NAME = "canvas_history.json"
        private const val CANVAS_DATA_FILE_NAME = "canvas_data.json"
    }

    // Listener pour changements d'historique
    var onHistoryChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    // ========== ENREGISTREMENT DES ACTIONS ==========

    /**
     * Enregistre l'ajout d'un objet
     */
    fun recordAddObject(obj: CanvasObject) {
        val json = serializeObject(obj)
        val memSize = estimateJsonMemory(json)

        val action = CanvasAction(
            id = nextActionId++,
            type = CanvasActionType.ADD_OBJECT,
            objectId = obj.id,
            afterState = json,
            estimatedMemoryBytes = memSize
        )

        pushAction(action)
    }

    /**
     * Enregistre la suppression d'un objet
     */
    fun recordDeleteObject(obj: CanvasObject) {
        val json = serializeObject(obj)
        val memSize = estimateJsonMemory(json)

        val action = CanvasAction(
            id = nextActionId++,
            type = CanvasActionType.DELETE_OBJECT,
            objectId = obj.id,
            beforeState = json,
            estimatedMemoryBytes = memSize
        )

        pushAction(action)
    }

    /**
     * Enregistre la modification d'un objet
     * @param beforeObj L'objet avant modification (copie)
     * @param afterObj L'objet après modification
     */
    fun recordModifyObject(beforeObj: CanvasObject, afterObj: CanvasObject) {
        val beforeJson = serializeObject(beforeObj)
        val afterJson = serializeObject(afterObj)
        val memSize = estimateJsonMemory(beforeJson) + estimateJsonMemory(afterJson)

        val action = CanvasAction(
            id = nextActionId++,
            type = CanvasActionType.MODIFY_OBJECT,
            objectId = afterObj.id,
            beforeState = beforeJson,
            afterState = afterJson,
            estimatedMemoryBytes = memSize
        )

        pushAction(action)
    }

    /**
     * Enregistre l'effacement de tous les objets
     */
    fun recordClearAll(allObjects: List<CanvasObject>) {
        val allJson = serializeAllObjects(allObjects)
        val memSize = estimateJsonMemory(allJson)

        val action = CanvasAction(
            id = nextActionId++,
            type = CanvasActionType.CLEAR_ALL,
            allObjectsBefore = allJson,
            estimatedMemoryBytes = memSize
        )

        pushAction(action)
    }

    /**
     * Ajoute une action à la pile et gère la mémoire
     */
    private fun pushAction(action: CanvasAction) {
        // Vider la pile redo (nouvelle action invalide le redo)
        clearRedoStack()

        // Ajouter l'action
        undoStack.add(action)
        totalMemoryBytes += action.estimatedMemoryBytes

        // Nettoyer si dépassement mémoire
        trimToMemoryLimit()

        notifyHistoryChanged()
    }

    /**
     * Supprime les actions les plus anciennes pour respecter la limite mémoire
     */
    private fun trimToMemoryLimit() {
        while (totalMemoryBytes > MAX_MEMORY_BYTES && undoStack.isNotEmpty()) {
            val removed = undoStack.removeAt(0)
            totalMemoryBytes -= removed.estimatedMemoryBytes
        }

        if (totalMemoryBytes < 0) totalMemoryBytes = 0
    }

    /**
     * Vide la pile redo
     */
    private fun clearRedoStack() {
        for (action in redoStack) {
            totalMemoryBytes -= action.estimatedMemoryBytes
        }
        redoStack.clear()
        if (totalMemoryBytes < 0) totalMemoryBytes = 0
    }

    // ========== UNDO / REDO ==========

    /**
     * Vérifie si undo est possible
     */
    fun canUndo(): Boolean = undoStack.isNotEmpty()

    /**
     * Vérifie si redo est possible
     */
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * Nombre d'actions dans la pile undo
     */
    fun getUndoCount(): Int = undoStack.size

    /**
     * Nombre d'actions dans la pile redo
     */
    fun getRedoCount(): Int = redoStack.size

    /**
     * Exécute un undo et retourne l'action à appliquer
     */
    fun undo(): CanvasAction? {
        if (undoStack.isEmpty()) return null

        val action = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(action)

        notifyHistoryChanged()
        return action
    }

    /**
     * Exécute un redo et retourne l'action à appliquer
     */
    fun redo(): CanvasAction? {
        if (redoStack.isEmpty()) return null

        val action = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(action)

        notifyHistoryChanged()
        return action
    }

    /**
     * Efface tout l'historique
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        totalMemoryBytes = 0
        notifyHistoryChanged()
    }

    /**
     * Retourne la mémoire utilisée en bytes
     */
    fun getMemoryUsage(): Long = totalMemoryBytes

    /**
     * Retourne la mémoire utilisée formatée (ex: "45.2 Mo")
     */
    fun getFormattedMemoryUsage(): String {
        val mb = totalMemoryBytes / (1024.0 * 1024.0)
        return String.format("%.1f Mo", mb)
    }

    private fun notifyHistoryChanged() {
        onHistoryChanged?.invoke(canUndo(), canRedo())
    }

    // ========== SERIALISATION DES OBJETS ==========

    /**
     * Sérialise un objet canvas en JSON
     */
    fun serializeObject(obj: CanvasObject): String {
        val json = JSONObject()

        // Propriétés communes
        json.put("id", obj.id)
        json.put("type", obj.type.name)
        json.put("x", obj.x.toDouble())
        json.put("y", obj.y.toDouble())
        json.put("width", obj.width.toDouble())
        json.put("height", obj.height.toDouble())
        json.put("rotation", obj.rotation.toDouble())
        json.put("scaleX", obj.scaleX.toDouble())
        json.put("scaleY", obj.scaleY.toDouble())
        json.put("opacity", obj.opacity.toDouble())
        json.put("visible", obj.visible)
        json.put("locked", obj.locked)
        json.put("zIndex", obj.zIndex)
        json.put("name", obj.name)

        // Propriétés spécifiques selon le type
        when (obj) {
            is ShapeObject -> serializeShapeObject(obj, json)
            is TextObject -> serializeTextObject(obj, json)
            is DrawingObject -> serializeDrawingObject(obj, json)
            is ImageObject -> serializeImageObject(obj, json)
        }

        return json.toString()
    }

    private fun serializeShapeObject(obj: ShapeObject, json: JSONObject) {
        json.put("shapeType", obj.shapeType.name)
        json.put("fillColor", obj.fillColor ?: JSONObject.NULL)
        json.put("strokeColor", obj.strokeColor)
        json.put("strokeWidth", obj.strokeWidth.toDouble())
        json.put("cornerRadius", obj.cornerRadius.toDouble())
        json.put("sides", obj.sides)
        json.put("starPoints", obj.starPoints)
        json.put("starInnerRatio", obj.starInnerRatio.toDouble())
        json.put("endX", obj.endX.toDouble())
        json.put("endY", obj.endY.toDouble())
        json.put("arrowHeadSize", obj.arrowHeadSize.toDouble())
    }

    private fun serializeTextObject(obj: TextObject, json: JSONObject) {
        json.put("text", obj.text)
        json.put("fontFamily", obj.fontFamily)
        json.put("fontSize", obj.fontSize.toDouble())
        json.put("fontStyle", obj.fontStyle.name)
        json.put("textColor", obj.textColor)
        json.put("backgroundColor", obj.backgroundColor ?: JSONObject.NULL)
        json.put("outlineColor", obj.outlineColor ?: JSONObject.NULL)
        json.put("outlineWidth", obj.outlineWidth.toDouble())
        json.put("alignment", obj.alignment.name)
        json.put("lineSpacing", obj.lineSpacing.toDouble())
        json.put("maxWidth", obj.maxWidth.toDouble())
        json.put("paddingHorizontal", obj.paddingHorizontal.toDouble())
        json.put("paddingVertical", obj.paddingVertical.toDouble())
    }

    private fun serializeDrawingObject(obj: DrawingObject, json: JSONObject) {
        json.put("brushSize", obj.brushSize.toDouble())
        json.put("brushColor", obj.brushColor)

        // Sérialiser tous les segments de trait
        val strokesArray = JSONArray()
        for (stroke in obj.getStrokes()) {
            val strokeJson = JSONObject()
            strokeJson.put("x1", stroke.x1.toDouble())
            strokeJson.put("y1", stroke.y1.toDouble())
            strokeJson.put("x2", stroke.x2.toDouble())
            strokeJson.put("y2", stroke.y2.toDouble())
            strokeJson.put("color", stroke.color)
            strokeJson.put("width", stroke.strokeWidth.toDouble())
            strokesArray.put(strokeJson)
        }
        json.put("strokes", strokesArray)
    }

    private fun serializeImageObject(obj: ImageObject, json: JSONObject) {
        json.put("sourceUri", obj.sourceUri?.toString() ?: JSONObject.NULL)
        json.put("relativePath", obj.relativePath ?: JSONObject.NULL)
        json.put("originalWidth", obj.originalWidth)
        json.put("originalHeight", obj.originalHeight)
        json.put("filterBitmap", obj.filterBitmap)
        json.put("tintColor", obj.tintColor)

        // Sauvegarder l'image COMPLÈTE en Base64 (pas juste une miniature)
        obj.getBitmap()?.let { bitmap ->
            // Utiliser PNG pour les petites images (pixel art), JPEG pour les grandes
            val isSmallImage = bitmap.width <= 512 && bitmap.height <= 512
            val base64 = if (isSmallImage) {
                bitmapToBase64(bitmap, Bitmap.CompressFormat.PNG, 100)
            } else {
                bitmapToBase64(bitmap, Bitmap.CompressFormat.JPEG, 90)
            }
            json.put("imageBase64", base64)
            json.put("imageFormat", if (isSmallImage) "PNG" else "JPEG")
        }
    }

    /**
     * Sérialise tous les objets en JSON
     */
    fun serializeAllObjects(objects: List<CanvasObject>): String {
        val array = JSONArray()
        for (obj in objects) {
            array.put(JSONObject(serializeObject(obj)))
        }
        return array.toString()
    }

    /**
     * Désérialise un objet depuis JSON
     */
    fun deserializeObject(jsonStr: String): CanvasObject? {
        return try {
            val json = JSONObject(jsonStr)
            val type = CanvasObjectType.valueOf(json.getString("type"))

            val obj: CanvasObject = when (type) {
                CanvasObjectType.SHAPE -> deserializeShapeObject(json)
                CanvasObjectType.TEXT -> deserializeTextObject(json)
                CanvasObjectType.DRAWING -> deserializeDrawingObject(json)
                CanvasObjectType.IMAGE -> deserializeImageObject(json)
            }

            // Propriétés communes
            obj.x = json.getDouble("x").toFloat()
            obj.y = json.getDouble("y").toFloat()
            obj.width = json.getDouble("width").toFloat()
            obj.height = json.getDouble("height").toFloat()
            obj.rotation = json.optDouble("rotation", 0.0).toFloat()
            obj.scaleX = json.optDouble("scaleX", 1.0).toFloat()
            obj.scaleY = json.optDouble("scaleY", 1.0).toFloat()
            obj.opacity = json.optDouble("opacity", 1.0).toFloat()
            obj.visible = json.optBoolean("visible", true)
            obj.locked = json.optBoolean("locked", false)
            obj.zIndex = json.optInt("zIndex", 0)
            obj.name = json.optString("name", "")

            obj
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun deserializeShapeObject(json: JSONObject): ShapeObject {
        // Utiliser l'ID sauvegardé
        val id = json.getString("id")
        val obj = ShapeObject(id)

        obj.shapeType = ShapeType.valueOf(json.optString("shapeType", "RECTANGLE"))

        val fillColor = json.opt("fillColor")
        obj.fillColor = if (fillColor == JSONObject.NULL || fillColor == null) null else (fillColor as Number).toInt()

        obj.strokeColor = json.optInt("strokeColor", Color.BLACK)
        obj.strokeWidth = json.optDouble("strokeWidth", 2.0).toFloat()
        obj.cornerRadius = json.optDouble("cornerRadius", 0.0).toFloat()
        obj.sides = json.optInt("sides", 5)
        obj.starPoints = json.optInt("starPoints", 5)
        obj.starInnerRatio = json.optDouble("starInnerRatio", 0.5).toFloat()
        obj.endX = json.optDouble("endX", 100.0).toFloat()
        obj.endY = json.optDouble("endY", 0.0).toFloat()
        obj.arrowHeadSize = json.optDouble("arrowHeadSize", 20.0).toFloat()

        return obj
    }

    private fun deserializeTextObject(json: JSONObject): TextObject {
        val id = json.getString("id")
        val obj = TextObject(id)

        obj.text = json.optString("text", "")
        obj.fontFamily = json.optString("fontFamily", "sans-serif")
        obj.fontSize = json.optDouble("fontSize", 24.0).toFloat()
        obj.fontStyle = FontStyle.valueOf(json.optString("fontStyle", "NORMAL"))
        obj.textColor = json.optInt("textColor", Color.BLACK)

        val bgColor = json.opt("backgroundColor")
        obj.backgroundColor = if (bgColor == JSONObject.NULL || bgColor == null) null else (bgColor as Number).toInt()

        val outColor = json.opt("outlineColor")
        obj.outlineColor = if (outColor == JSONObject.NULL || outColor == null) null else (outColor as Number).toInt()

        obj.outlineWidth = json.optDouble("outlineWidth", 2.0).toFloat()
        obj.alignment = TextAlignment.valueOf(json.optString("alignment", "LEFT"))
        obj.lineSpacing = json.optDouble("lineSpacing", 1.2).toFloat()
        obj.maxWidth = json.optDouble("maxWidth", 0.0).toFloat()
        obj.paddingHorizontal = json.optDouble("paddingHorizontal", 8.0).toFloat()
        obj.paddingVertical = json.optDouble("paddingVertical", 4.0).toFloat()

        return obj
    }

    private fun deserializeDrawingObject(json: JSONObject): DrawingObject {
        val id = json.getString("id")
        val obj = DrawingObject(id)

        obj.brushSize = json.optDouble("brushSize", 8.0).toFloat()
        obj.brushColor = json.optInt("brushColor", Color.BLACK)

        // Restaurer tous les segments de trait
        val strokesArray = json.optJSONArray("strokes")
        if (strokesArray != null) {
            for (i in 0 until strokesArray.length()) {
                val strokeJson = strokesArray.getJSONObject(i)
                val x1 = strokeJson.getDouble("x1").toFloat()
                val y1 = strokeJson.getDouble("y1").toFloat()
                val x2 = strokeJson.getDouble("x2").toFloat()
                val y2 = strokeJson.getDouble("y2").toFloat()
                val color = strokeJson.getInt("color")
                val width = strokeJson.getDouble("width").toFloat()
                obj.addStrokeSegment(x1, y1, x2, y2, color, width)
            }
            // Finaliser les bounds après import
            obj.finalizeBounds()
        }

        return obj
    }

    private fun deserializeImageObject(json: JSONObject): ImageObject {
        val id = json.getString("id")
        val obj = ImageObject(id)

        val uriStr = json.opt("sourceUri")
        if (uriStr != null && uriStr != JSONObject.NULL) {
            obj.sourceUri = android.net.Uri.parse(uriStr.toString())
        }

        val relPath = json.opt("relativePath")
        if (relPath != null && relPath != JSONObject.NULL) {
            obj.relativePath = relPath.toString()
        }

        obj.filterBitmap = json.optBoolean("filterBitmap", false)
        obj.tintColor = json.optInt("tintColor", Color.WHITE)

        // Restaurer l'image complète depuis Base64
        val imageBase64 = json.optString("imageBase64", "")
        if (imageBase64.isNotEmpty()) {
            base64ToBitmap(imageBase64)?.let { bitmap ->
                obj.setBitmap(bitmap)
            }
        } else {
            // Fallback: ancienne version avec thumbnailBase64
            val thumbBase64 = json.optString("thumbnailBase64", "")
            if (thumbBase64.isNotEmpty()) {
                base64ToBitmap(thumbBase64)?.let { bitmap ->
                    obj.setBitmap(bitmap)
                }
            }
        }

        return obj
    }

    /**
     * Désérialise tous les objets depuis JSON
     */
    fun deserializeAllObjects(jsonStr: String): List<CanvasObject> {
        val objects = mutableListOf<CanvasObject>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val objJson = array.getJSONObject(i)
                deserializeObject(objJson.toString())?.let { objects.add(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return objects
    }

    // ========== UTILITAIRES ==========

    private fun estimateJsonMemory(json: String): Long {
        // Estimation : 2 bytes par caractère UTF-16 + overhead
        return (json.length * 2L) + 100
    }

    private fun bitmapToBase64(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100
    ): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(format, quality, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    // ========== PERSISTENCE ==========

    /**
     * Sauvegarde le canvas dans un fichier séparé
     */
    fun saveCanvasToFile(objects: List<CanvasObject>, viewportX: Float, viewportY: Float, viewportZoom: Float) {
        synchronized(saveLock) {
            try {
                val json = JSONObject()
                json.put("version", 1)
                json.put("timestamp", System.currentTimeMillis())
                json.put("viewportX", viewportX.toDouble())
                json.put("viewportY", viewportY.toDouble())
                json.put("viewportZoom", viewportZoom.toDouble())
                json.put("objects", JSONArray(serializeAllObjects(objects)))

                val file = File(appContext.filesDir, CANVAS_DATA_FILE_NAME)
                file.writeText(json.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Charge le canvas depuis le fichier
     * @return Triple(objects, viewportX, viewportY, viewportZoom) ou null
     */
    fun loadCanvasFromFile(): CanvasLoadResult? {
        synchronized(saveLock) {
            try {
                val file = File(appContext.filesDir, CANVAS_DATA_FILE_NAME)
                if (!file.exists()) return null

                val jsonStr = file.readText()
                val json = JSONObject(jsonStr)

                val viewportX = json.optDouble("viewportX", 0.0).toFloat()
                val viewportY = json.optDouble("viewportY", 0.0).toFloat()
                val viewportZoom = json.optDouble("viewportZoom", 1.0).toFloat()

                val objectsArray = json.optJSONArray("objects")
                val objects = if (objectsArray != null) {
                    deserializeAllObjects(objectsArray.toString())
                } else {
                    emptyList()
                }

                return CanvasLoadResult(objects, viewportX, viewportY, viewportZoom)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }

    /**
     * Supprime le fichier de sauvegarde du canvas
     */
    fun deleteCanvasFile() {
        synchronized(saveLock) {
            val file = File(appContext.filesDir, CANVAS_DATA_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    /**
     * Sauvegarde l'historique (optionnel, pour reprise après crash)
     */
    fun saveHistoryToFile() {
        synchronized(saveLock) {
            try {
                val json = JSONObject()
                json.put("version", 1)
                json.put("nextActionId", nextActionId)

                // Sauvegarder seulement les dernières actions (limité pour perf)
                val actionsToSave = undoStack.takeLast(50)
                val actionsArray = JSONArray()
                for (action in actionsToSave) {
                    val actionJson = JSONObject()
                    actionJson.put("id", action.id)
                    actionJson.put("type", action.type.name)
                    actionJson.put("timestamp", action.timestamp)
                    actionJson.put("objectId", action.objectId ?: JSONObject.NULL)
                    actionJson.put("beforeState", action.beforeState ?: JSONObject.NULL)
                    actionJson.put("afterState", action.afterState ?: JSONObject.NULL)
                    actionJson.put("allObjectsBefore", action.allObjectsBefore ?: JSONObject.NULL)
                    actionsArray.put(actionJson)
                }
                json.put("undoStack", actionsArray)

                val file = File(appContext.filesDir, SAVE_FILE_NAME)
                file.writeText(json.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Charge l'historique depuis le fichier
     */
    fun loadHistoryFromFile() {
        synchronized(saveLock) {
            try {
                val file = File(appContext.filesDir, SAVE_FILE_NAME)
                if (!file.exists()) return

                val jsonStr = file.readText()
                val json = JSONObject(jsonStr)

                nextActionId = json.optLong("nextActionId", 1L)

                undoStack.clear()
                redoStack.clear()
                totalMemoryBytes = 0

                val actionsArray = json.optJSONArray("undoStack")
                if (actionsArray != null) {
                    for (i in 0 until actionsArray.length()) {
                        val actionJson = actionsArray.getJSONObject(i)
                        val action = CanvasAction(
                            id = actionJson.getLong("id"),
                            type = CanvasActionType.valueOf(actionJson.getString("type")),
                            timestamp = actionJson.optLong("timestamp", 0),
                            objectId = actionJson.opt("objectId")?.takeIf { it != JSONObject.NULL }?.toString(),
                            beforeState = actionJson.opt("beforeState")?.takeIf { it != JSONObject.NULL }?.toString(),
                            afterState = actionJson.opt("afterState")?.takeIf { it != JSONObject.NULL }?.toString(),
                            allObjectsBefore = actionJson.opt("allObjectsBefore")?.takeIf { it != JSONObject.NULL }?.toString(),
                            estimatedMemoryBytes = 0 // Sera recalculé
                        )
                        undoStack.add(action)
                    }
                }

                // Recalculer la mémoire
                recalculateTotalMemory()

                notifyHistoryChanged()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun recalculateTotalMemory() {
        totalMemoryBytes = 0
        for (action in undoStack) {
            var mem = 0L
            action.beforeState?.let { mem += estimateJsonMemory(it) }
            action.afterState?.let { mem += estimateJsonMemory(it) }
            action.allObjectsBefore?.let { mem += estimateJsonMemory(it) }
            totalMemoryBytes += mem
        }
        for (action in redoStack) {
            var mem = 0L
            action.beforeState?.let { mem += estimateJsonMemory(it) }
            action.afterState?.let { mem += estimateJsonMemory(it) }
            action.allObjectsBefore?.let { mem += estimateJsonMemory(it) }
            totalMemoryBytes += mem
        }
    }
}

/**
 * Résultat du chargement d'un canvas
 */
data class CanvasLoadResult(
    val objects: List<CanvasObject>,
    val viewportX: Float,
    val viewportY: Float,
    val viewportZoom: Float
)
