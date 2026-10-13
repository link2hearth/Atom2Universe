package com.Atom2Universe.app.games.hamlet

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Android document picker: no storage permission, network or automatic replacement of a world. */
class HamletWorldFiles(private val host: HamletActivity) {
    private var pendingExport: File? = null
    private var pendingPhoto: File? = null
    private var busy = false
    private val export = host.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val file = pendingExport; pendingExport = null
        writeDocument(uri, file, R.string.hamlet_realms_export_ok)
    }
    private val photo = host.registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val file = pendingPhoto; pendingPhoto = null
        writeDocument(uri, file, R.string.hamlet_photo_saved)
    }
    private val importer = host.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            busy = true
            host.lifecycleScope.launch {
                try {
                    val prepared = withContext(Dispatchers.IO) { runCatching {
                        requireNotNull(host.contentResolver.openInputStream(uri)).use { HamletTransfer.inspect(HamletTransfer.readBounded(it)) }
                    }.getOrElse { HamletTransfer.Prepared(null, false, listOf(HamletTransfer.Issue(if (it.message == "hamlet_realms_import_size") "size" else "format"))) } }
                    val detail = prepared.issues.take(12).joinToString("\n\n") {
                        val label = host.stringByName("hamlet_realms_import_${it.code}")
                        if (it.detail.isEmpty()) label else host.getString(R.string.hamlet_realms_import_detail, label, it.detail)
                    }
                    val dialog = AlertDialog.Builder(host).setTitle(R.string.hamlet_realms_import)
                    if (!prepared.valid) dialog.setMessage(detail).setPositiveButton(android.R.string.ok, null).show()
                    else dialog.setMessage(listOfNotNull(
                        host.getString(R.string.hamlet_realms_import_confirm),
                        host.getString(R.string.hamlet_import_target_mode, host.getString(if (host.game.sandbox) R.string.hamlet_creative_world else R.string.hamlet_progression_world)),
                        if (prepared.legacy) host.getString(R.string.hamlet_realms_import_legacy_confirm) else null,
                        detail.takeIf { it.isNotBlank() },
                    ).joinToString("\n\n"))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.hamlet_realms_import) { _, _ -> host.applyImportedWorld(prepared) }.show()
                } finally { busy = false }
            }
        }
    }

    fun exportWorld() {
        if (pendingExport != null || busy) return
        val snapshot = HamletTransfer.exportWorld(host.game)
        busy = true
        host.lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { runCatching {
                    File.createTempFile("hamlet-export-", ".json", host.cacheDir).also { it.writeText(snapshot, Charsets.UTF_8) }
                }.getOrNull() }
                if (file == null) host.toast(host.getString(R.string.hamlet_realms_file_error))
                else {
                    pendingExport = file
                    runCatching { export.launch("hamlet-${System.currentTimeMillis()}.json") }.onFailure {
                        file.delete(); pendingExport = null; host.toast(host.getString(R.string.hamlet_realms_file_error))
                    }
                }
            } finally { busy = false }
        }
    }
    fun importWorld() {
        if (!busy) runCatching { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            .onFailure { host.toast(host.getString(R.string.hamlet_realms_file_error)) }
    }
    fun capture() {
        if (pendingPhoto != null || busy || host.village.width <= 0 || host.village.height <= 0) return
        val view = host.village
        val scale = minOf(1f, 1920f / maxOf(view.width, view.height))
        val bitmap = Bitmap.createBitmap((view.width * scale).toInt().coerceAtLeast(1), (view.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.scale(scale, scale)
        val previous = view.renderer.photoMode
        try { view.renderer.photoMode = true; view.draw(canvas) } finally { view.renderer.photoMode = previous }
        busy = true
        host.lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { runCatching {
                    File.createTempFile("hamlet-photo-", ".png", host.cacheDir).also { destination ->
                        destination.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    }
                }.getOrNull() }
                if (file == null) host.toast(host.getString(R.string.hamlet_realms_file_error))
                else {
                    pendingPhoto = file
                    runCatching { photo.launch("hamlet-${System.currentTimeMillis()}.png") }.onFailure {
                        file.delete(); pendingPhoto = null; host.toast(host.getString(R.string.hamlet_realms_file_error))
                    }
                }
            } finally { bitmap.recycle(); busy = false }
        }
    }
    private fun writeDocument(uri: android.net.Uri?, file: File?, success: Int) {
        if (uri == null) { file?.delete(); return }
        if (file == null) { host.toast(host.getString(R.string.hamlet_realms_file_error)); return }
        host.lifecycleScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { runCatching {
                    file.inputStream().use { input -> requireNotNull(host.contentResolver.openOutputStream(uri, "wt")).use { input.copyTo(it) } }
                }.isSuccess }
                host.toast(host.getString(if (ok) success else R.string.hamlet_realms_file_error))
            } finally { file.delete() }
        }
    }
    fun saveState(state: Bundle) {
        state.putString("hamlet.export", pendingExport?.name)
        state.putString("hamlet.photo", pendingPhoto?.name)
    }
    fun restoreState(state: Bundle?) {
        fun restore(key: String): File? = state?.getString(key)?.takeIf { it.matches(Regex("hamlet-(export|photo)-[A-Za-z0-9.-]+")) }
            ?.let { File(host.cacheDir, it) }?.takeIf { it.isFile }
        pendingExport = restore("hamlet.export"); pendingPhoto = restore("hamlet.photo")
    }
    fun close() { if (host.isFinishing) { pendingPhoto?.delete(); pendingExport?.delete() } }
}
