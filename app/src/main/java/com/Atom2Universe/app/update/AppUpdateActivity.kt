package com.Atom2Universe.app.update

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.Atom2Universe.app.BuildConfig
import com.Atom2Universe.app.R
import com.Atom2Universe.app.audio.AudioThemedActivity
import com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.launch

class AppUpdateActivity : AudioThemedActivity() {
    private lateinit var model: AppUpdateViewModel
    private var awaitingPermission = false
    private var offeredFile: String? = null
    private val unknownSources = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (awaitingPermission) {
            awaitingPermission = false
            if (packageManager.canRequestPackageInstalls()) install()
            else Toast.makeText(this, R.string.update_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_app_update)
        awaitingPermission = savedInstanceState?.getBoolean("awaiting_permission") ?: false
        offeredFile = savedInstanceState?.getString("offered_file")
        model = ViewModelProvider(this)[AppUpdateViewModel::class.java]
        findViewById<View>(R.id.update_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.update_installed).text = getString(R.string.update_installed, BuildConfig.VERSION_NAME)
        findViewById<Button>(R.id.update_check).setOnClickListener { model.check() }
        findViewById<Button>(R.id.update_action).setOnClickListener {
            if (model.state.value.file != null) install()
            else ImmersiveAlertDialogBuilder(this)
                .setTitle(R.string.update_title)
                .setMessage(getString(R.string.update_confirm_download,
                    Formatter.formatFileSize(this, model.state.value.release?.size ?: 0)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.update_action) { _, _ -> model.download() }.show()
        }
        findViewById<Button>(R.id.update_releases).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdateRepository.RELEASES_URL))) }
            catch (_: ActivityNotFoundException) { showInstallError() }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    findViewById<TextView>(R.id.update_status).text = if (state.percent != null)
                        getString(R.string.update_progress, state.percent) else getString(state.message)
                    findViewById<TextView>(R.id.update_notes).apply {
                        text = state.release?.let { getString(R.string.update_release_details, it.tag, it.notes) }.orEmpty()
                        visibility = if (state.release != null) View.VISIBLE else View.GONE
                    }
                    findViewById<ProgressBar>(R.id.update_progress).apply {
                        visibility = if (state.busy) View.VISIBLE else View.GONE
                        isIndeterminate = state.percent == null
                        progress = state.percent ?: 0
                    }
                    findViewById<Button>(R.id.update_check).isEnabled = !state.busy && !BuildConfig.DEBUG
                    findViewById<Button>(R.id.update_action).apply {
                        visibility = if (state.release != null) View.VISIBLE else View.GONE
                        isEnabled = !state.busy
                        setText(if (state.file != null) R.string.update_install else R.string.update_action)
                    }
                    // Une seule ouverture automatique à la fin du téléchargement ; annuler reste possible.
                    if (state.file == null) offeredFile = null
                    if (state.file != null && offeredFile != state.file.absolutePath) {
                        offeredFile = state.file.absolutePath
                        install()
                    }
                }
            }
        }
    }

    private fun install() {
        val file = model.state.value.file ?: return
        if (!file.exists()) { showInstallError(); return }
        try {
            if (!packageManager.canRequestPackageInstalls()) {
                ImmersiveAlertDialogBuilder(this)
                    .setTitle(R.string.update_permission_title)
                    .setMessage(R.string.update_permission_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.update_open_settings) { _, _ ->
                        try {
                            awaitingPermission = true
                            unknownSources.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:$packageName")))
                        } catch (_: Exception) { awaitingPermission = false; showInstallError() }
                    }.show()
                return
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) { showInstallError() }
    }

    private fun showInstallError() = Toast.makeText(this, R.string.update_install_error, Toast.LENGTH_LONG).show()

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("awaiting_permission", awaitingPermission)
        outState.putString("offered_file", offeredFile)
        super.onSaveInstanceState(outState)
    }
}
