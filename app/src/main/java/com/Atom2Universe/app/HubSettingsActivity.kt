package com.Atom2Universe.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.SystemBarsManager
import com.Atom2Universe.app.util.updateSystemBarsVisibility
import com.Atom2Universe.app.util.CacheCleanerManager
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.core.content.edit

/**
 * Écran de paramètres du Hub.
 * Centralise les options globales de l'application.
 */
class HubSettingsActivity : ThemedActivity() {

    companion object {
        private const val PREFS_NAME = "audio_hub_prefs"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var backButton: ImageButton
    private lateinit var autoResumeSwitch: SwitchMaterial
    private lateinit var autoResumeSetting: LinearLayout
    private lateinit var themeSetting: LinearLayout
    private lateinit var themeValue: TextView
    private lateinit var systemBarsSwitch: SwitchMaterial
    private lateinit var systemBarsSetting: LinearLayout
    private lateinit var autoCleanupSwitch: SwitchMaterial
    private lateinit var autoCleanupSetting: LinearLayout
    private lateinit var cleanupNowButton: Button
    private lateinit var cleanupStatusText: TextView
    private lateinit var aboutButton: LinearLayout

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_hub_settings)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        setupViews()
        loadSettings()
    }

    override fun onResume() {
        super.onResume()
        updateThemeValue()
    }

    private fun setupViews() {
        backButton = findViewById(R.id.back_button)
        autoResumeSwitch = findViewById(R.id.auto_resume_switch)
        autoResumeSetting = findViewById(R.id.auto_resume_setting)
        themeSetting = findViewById(R.id.theme_setting)
        themeValue = findViewById(R.id.theme_value)
        systemBarsSwitch = findViewById(R.id.system_bars_switch)
        systemBarsSetting = findViewById(R.id.system_bars_setting)
        autoCleanupSwitch = findViewById(R.id.auto_cleanup_switch)
        autoCleanupSetting = findViewById(R.id.auto_cleanup_setting)
        cleanupNowButton = findViewById(R.id.cleanup_now_button)
        cleanupStatusText = findViewById(R.id.cleanup_status_text)
        aboutButton = findViewById(R.id.about_button)

        backButton.setOnClickListener {
            navigateBackToHub()
        }

        // Auto-resume setting
        autoResumeSetting.setOnClickListener {
            autoResumeSwitch.isChecked = !autoResumeSwitch.isChecked
        }
        autoResumeSwitch.setOnCheckedChangeListener { _, isChecked ->
            AudioFocusManager.setAutoResumeEnabled(isChecked)
        }

        // Theme setting
        themeSetting.setOnClickListener {
            showThemeSelectionDialog()
        }

        // System bars setting
        systemBarsSetting.setOnClickListener {
            systemBarsSwitch.isChecked = !systemBarsSwitch.isChecked
        }
        systemBarsSwitch.setOnCheckedChangeListener { _, isChecked ->
            SystemBarsManager.setShowSystemBars(this, isChecked)
            updateSystemBarsVisibility()
        }

        // Auto cleanup setting
        autoCleanupSetting.setOnClickListener {
            autoCleanupSwitch.isChecked = !autoCleanupSwitch.isChecked
        }
        autoCleanupSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("auto_cleanup_enabled", isChecked) }
            Toast.makeText(
                this,
                if (isChecked) getString(R.string.settings_cleanup_enabled)
                else getString(R.string.settings_cleanup_disabled),
                Toast.LENGTH_SHORT
            ).show()
        }

        // Cleanup now button
        cleanupNowButton.setOnClickListener {
            performManualCleanup()
        }

        // About button
        aboutButton.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun loadSettings() {
        autoResumeSwitch.isChecked = AudioFocusManager.isAutoResumeEnabled()
        systemBarsSwitch.isChecked = SystemBarsManager.shouldShowSystemBars(this)
        autoCleanupSwitch.isChecked = prefs.getBoolean("auto_cleanup_enabled", false)
        updateThemeValue()
        updateCleanupStatus()
    }

    private fun updateThemeValue() {
        val currentTheme = AppThemeManager.getSelectedTheme(this)
        themeValue.text = getString(currentTheme.labelRes)
    }

    private fun showThemeSelectionDialog() {
        val themes = AppThemeManager.getAvailableThemes()
        val themeLabels = themes.map { getString(it.labelRes) }.toTypedArray()
        val currentTheme = AppThemeManager.getSelectedTheme(this)
        val checkedIndex = themes.indexOf(currentTheme).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.theme_select_title)
            .setSingleChoiceItems(themeLabels, checkedIndex) { dialog, which ->
                val selectedTheme = themes[which]
                if (selectedTheme != currentTheme) {
                    AppThemeManager.setSelectedTheme(this, selectedTheme)
                    dialog.dismiss()
                    recreate()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateCleanupStatus() {
        val lastCleanup = prefs.getLong("last_cleanup_timestamp", 0)
        if (lastCleanup > 0) {
            val now = System.currentTimeMillis()
            val hours = (now - lastCleanup) / (1000 * 60 * 60)
            val days = hours / 24

            val statusText = when {
                days > 0 -> getString(R.string.settings_cleanup_last_day, days.toInt(), if (days > 1) "s" else "")
                hours > 0 -> getString(R.string.settings_cleanup_last_hour, hours.toInt(), if (hours > 1) "s" else "")
                else -> getString(R.string.settings_cleanup_last_now)
            }
            cleanupStatusText.text = statusText
        } else {
            cleanupStatusText.text = getString(R.string.settings_cleanup_status_none)
        }
    }

    private fun performManualCleanup() {
        cleanupNowButton.isEnabled = false
        cleanupNowButton.text = getString(R.string.settings_cleanup_in_progress)
        cleanupStatusText.text = getString(R.string.settings_cleanup_analyzing)

        activityScope.launch {
            try {
                Log.i("HubSettingsActivity", "🧹 Lancement du nettoyage manuel...")

                val cleanerManager = CacheCleanerManager(this@HubSettingsActivity)
                val report = cleanerManager.cleanOrphanedFiles(dryRun = false)

                // Sauvegarder le timestamp
                prefs.edit { putLong("last_cleanup_timestamp", System.currentTimeMillis()) }

                // Mettre à jour l'UI
                runOnUiThread {
                    cleanupNowButton.isEnabled = true
                    cleanupNowButton.text = getString(R.string.settings_cleanup_now_button)

                    if (report.deletedFiles > 0) {
                        val message = "✅ ${report.deletedFiles} fichiers supprimés\n" +
                                "${String.format("%.2f", report.freedSpaceMB)} MB libérés"
                        cleanupStatusText.text = message

                        Toast.makeText(
                            this@HubSettingsActivity,
                            message.replace("\n", ", "),
                            Toast.LENGTH_LONG
                        ).show()

                        Log.i("HubSettingsActivity", "✅ Nettoyage terminé: $message")
                    } else {
                        cleanupStatusText.text = getString(R.string.settings_cleanup_no_orphans)
                        Toast.makeText(
                            this@HubSettingsActivity,
                            getString(R.string.settings_cleanup_nothing_to_clean),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    updateCleanupStatus()
                }
            } catch (e: Exception) {
                Log.e("HubSettingsActivity", "❌ Erreur lors du nettoyage", e)

                runOnUiThread {
                    cleanupNowButton.isEnabled = true
                    cleanupNowButton.text = getString(R.string.settings_cleanup_now_button)
                    cleanupStatusText.text = getString(R.string.settings_cleanup_error)

                    Toast.makeText(
                        this@HubSettingsActivity,
                        getString(R.string.settings_cleanup_error_short),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun navigateBackToHub() {
        if (isTaskRoot) {
            startActivity(Intent(this, AudioHubActivity::class.java))
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
