package com.Atom2Universe.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.cloud.CloudActivity
import com.Atom2Universe.app.games.cards.CardBacks
import com.Atom2Universe.app.games.cards.CardBackGalleryDialog
import com.Atom2Universe.app.music.sync.GoogleSignInManager
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
import kotlinx.coroutines.withContext
import androidx.core.content.edit

/**
 * Écran de paramètres du Hub.
 * Centralise les options globales de l'application.
 */
class HubSettingsActivity : com.Atom2Universe.app.audio.AudioThemedActivity() {

    companion object {
        private const val PREFS_NAME = "audio_hub_prefs"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var backButton: ImageButton
    private lateinit var autoResumeSwitch: SwitchMaterial
    private lateinit var autoResumeSetting: LinearLayout
    private lateinit var systemBarsSwitch: SwitchMaterial
    private lateinit var systemBarsSetting: LinearLayout
    private lateinit var autoCleanupSwitch: SwitchMaterial
    private lateinit var autoCleanupSetting: LinearLayout
    private lateinit var cleanupNowButton: Button
    private lateinit var cleanupStatusText: TextView
    private lateinit var aboutButton: LinearLayout
    private lateinit var cloudSetting: LinearLayout
    private lateinit var cloudSettingIcon: ImageView
    private lateinit var cloudSettingSummary: TextView

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
        buildCardBacks()
    }

    /**
     * Un contour coloré indique les familles gardées. Un appui change la sélection ;
     * la dernière famille gardée ne peut pas être désactivée.
     */
    private fun buildCardBacks() {
        val row = findViewById<LinearLayout>(R.id.card_backs_row)
        val availableIndices = CardBacks.availableIndices(this)
        if (availableIndices.isEmpty()) return
        listOf(R.id.card_backs_title, R.id.card_backs_container, R.id.card_backs_hint).forEach {
            findViewById<android.view.View>(it).visibility = android.view.View.VISIBLE
        }
        row.orientation = LinearLayout.HORIZONTAL
        val density = resources.displayMetrics.density
        for (index in availableIndices) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPadding((4 * density).toInt(), 0, (4 * density).toInt(), (8 * density).toInt())
            }
            val familyName = getString(CardBacks.families[index].labelRes)
            var enabled = CardBacks.isEnabled(this, index)
            val selectionBorder = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(android.graphics.Color.TRANSPARENT)
            }
            val picture = object : ImageView(this) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val width = resolveSize((72 * density).toInt(), widthMeasureSpec)
                    setMeasuredDimension(width, resolveSize(width * 3 / 2, heightMeasureSpec))
                }
            }.apply {
                contentDescription = familyName
                foreground = selectionBorder
                accessibilityDelegate = object : android.view.View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: android.view.View, info: android.view.accessibility.AccessibilityNodeInfo
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.isCheckable = true
                        info.isChecked = enabled
                        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                            android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK,
                            getString(R.string.settings_card_gallery_open)
                        ))
                    }
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, 8 * density)
                    }
                }
            }
            val label = TextView(this).apply {
                text = familyName
                textSize = 12f
                minLines = 2
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = android.view.Gravity.CENTER
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setTextColor(com.Atom2Universe.app.audio.AudioStyle.primaryText(this@HubSettingsActivity))
            }
            fun showState() {
                picture.alpha = if (enabled) 1f else 0.45f
                picture.isSelected = enabled
                selectionBorder.setStroke(
                    (3 * density).toInt().coerceAtLeast(1),
                    if (enabled) com.Atom2Universe.app.audio.AudioStyle.accent(this)
                    else android.graphics.Color.TRANSPARENT
                )
            }
            showState()
            val toggle = {
                val wanted = !enabled
                if (CardBacks.setEnabled(this, index, wanted)) enabled = wanted
                showState()
            }
            picture.setOnClickListener { toggle() }
            label.setOnClickListener { toggle() }
            val preview = android.view.View.OnLongClickListener {
                if (supportFragmentManager.findFragmentByTag(CardBackGalleryDialog.TAG) == null) {
                    CardBackGalleryDialog.forFamily(CardBacks.families[index])
                        .show(supportFragmentManager, CardBackGalleryDialog.TAG)
                }
                true
            }
            picture.setOnLongClickListener(preview)
            label.setOnLongClickListener(preview)
            cell.setOnLongClickListener(preview)
            cell.addView(picture, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            cell.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            activityScope.launch {
                val bitmap = withContext(Dispatchers.Default) { CardBacks.loadSample(this@HubSettingsActivity, index) }
                if (bitmap != null) picture.setImageBitmap(bitmap)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateCloudSummary()
    }

    /**
     * La ligne cloud affiche le compte lie plutot que sa description generique
     * des qu'il y en a un : c'est l'information qu'on vient verifier.
     */
    private fun updateCloudSummary() {
        val email = GoogleSignInManager(this).getSignedInEmail()
        val signedIn = email != null
        cloudSettingSummary.text = email ?: getString(R.string.cloud_settings_entry_desc)
        cloudSettingIcon.setImageResource(
            if (signedIn) R.drawable.ic_cloud else R.drawable.ic_cloud_off
        )
        cloudSettingIcon.imageTintList = ContextCompat.getColorStateList(
            this,
            if (signedIn) R.color.cloud_cat_music_color else R.color.startup_text_secondary
        )
    }

    private fun setupViews() {
        backButton = findViewById(R.id.back_button)
        autoResumeSwitch = findViewById(R.id.auto_resume_switch)
        autoResumeSetting = findViewById(R.id.auto_resume_setting)
        systemBarsSwitch = findViewById(R.id.system_bars_switch)
        systemBarsSetting = findViewById(R.id.system_bars_setting)
        autoCleanupSwitch = findViewById(R.id.auto_cleanup_switch)
        autoCleanupSetting = findViewById(R.id.auto_cleanup_setting)
        cleanupNowButton = findViewById(R.id.cleanup_now_button)
        cleanupStatusText = findViewById(R.id.cleanup_status_text)
        aboutButton = findViewById(R.id.about_button)
        cloudSetting = findViewById(R.id.cloud_setting)
        cloudSettingIcon = findViewById(R.id.cloud_setting_icon)
        cloudSettingSummary = findViewById(R.id.cloud_setting_summary)

        backButton.setOnClickListener {
            navigateBackToHub()
        }

        // Compte Google et cloud : l'ecran dedie porte tout, ici on n'a qu'une porte
        cloudSetting.setOnClickListener {
            startActivity(CloudActivity.intent(this))
        }

        // Auto-resume setting
        autoResumeSetting.setOnClickListener {
            autoResumeSwitch.isChecked = !autoResumeSwitch.isChecked
        }
        autoResumeSwitch.setOnCheckedChangeListener { _, isChecked ->
            AudioFocusManager.setAutoResumeEnabled(isChecked)
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
        updateCleanupStatus()
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
