package com.Atom2Universe.app.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.AudioHubActivity
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.cloud.CloudActivity
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.music.lyrics.api.ApiTestResult
import com.Atom2Universe.app.music.lyrics.api.GenericLyricsApiClient
import com.Atom2Universe.app.music.lyrics.api.LyricsApiConfig
import com.Atom2Universe.app.music.navidrome.SubsonicApiClient
import com.Atom2Universe.app.music.sync.CloudSyncManager
import com.Atom2Universe.app.music.sync.GoogleSignInManager
import com.Atom2Universe.app.music.sync.peer.A2USyncService
import com.Atom2Universe.app.music.sync.peer.TrustedNetworkManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activité des préférences du lecteur audio.
 */
class MusicSettingsActivity : ThemedActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var preferences: MusicPreferences
    private lateinit var googleSignInManager: GoogleSignInManager

    private lateinit var switchShowPlayCount: SwitchMaterial
    private lateinit var textPlayCountStatus: TextView
    private lateinit var switchWriteTagsToFiles: SwitchMaterial
    private lateinit var textWriteTagsStatus: TextView
    private lateinit var switchAutoFetchLyrics: SwitchMaterial
    private lateinit var textAutoFetchStatus: TextView
    private lateinit var textAlbumSortValue: TextView

    // Lyrics API Views
    private lateinit var sectionLyricsApi: LinearLayout
    private lateinit var editLyricsApiPrimary: TextInputEditText
    private lateinit var editLyricsApiPrimaryHeaders: TextInputEditText
    private lateinit var editLyricsApiPrimarySyncedPath: TextInputEditText
    private lateinit var editLyricsApiPrimaryLyricsPath: TextInputEditText
    private lateinit var editLyricsApiFallback: TextInputEditText
    private lateinit var editLyricsApiFallbackHeaders: TextInputEditText
    private lateinit var editLyricsApiFallbackSyncedPath: TextInputEditText
    private lateinit var editLyricsApiFallbackLyricsPath: TextInputEditText
    private lateinit var btnTestLyricsApi: MaterialButton
    private lateinit var textApiTestResult: TextView
    private lateinit var toggleApi1Advanced: TextView
    private lateinit var containerApi1Advanced: LinearLayout
    private lateinit var toggleApi2Advanced: TextView
    private lateinit var containerApi2Advanced: LinearLayout

    // Navidrome Views
    private lateinit var editNavidromeServerUrl: TextInputEditText
    private lateinit var editNavidromeUsername: TextInputEditText
    private lateinit var editNavidromePassword: TextInputEditText
    private lateinit var btnTestNavidrome: MaterialButton

    // Porte d'entree vers l'ecran cloud, plus le bouton de remise a plat des compteurs
    private lateinit var optionCloudLink: LinearLayout
    private lateinit var iconCloudLink: ImageView
    private lateinit var textCloudLinkSummary: TextView
    private lateinit var btnResetPlayCounts: MaterialButton

    // LAN Sync Views
    private lateinit var optionTrustedWifi: LinearLayout
    private lateinit var switchTrustedWifi: SwitchMaterial
    private lateinit var textTrustedWifiStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_music_settings)

        preferences = MusicPreferences.getInstance(this)
        googleSignInManager = GoogleSignInManager(this)

        setupToolbar()
        setupViews()
        setupCloudLinkSection()
        setupTrustedWifiSection()
        loadPreferences()
    }

    override fun onResume() {
        super.onResume()
        // Le compte a pu etre lie ou delie depuis l'ecran cloud.
        if (::optionCloudLink.isInitialized) updateCloudLinkUI()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            navigateBackToHub()
        }
    }

    private fun navigateBackToHub() {
        if (isTaskRoot) {
            startActivity(Intent(this, AudioHubActivity::class.java))
        }
        finish()
    }

    private fun setupViews() {
        // Option: Afficher le nombre d'écoutes
        val optionShowPlayCount = findViewById<LinearLayout>(R.id.option_show_play_count)
        switchShowPlayCount = findViewById(R.id.switch_show_play_count)
        textPlayCountStatus = findViewById(R.id.text_play_count_status)

        optionShowPlayCount.setOnClickListener {
            switchShowPlayCount.toggle()
        }

        switchShowPlayCount.setOnCheckedChangeListener { _, isChecked ->
            preferences.showPlayCount = isChecked
            updatePlayCountStatusDisplay(isChecked)
        }

        // Option: Écrire dans les tags ID3/POPM
        val optionWriteTagsToFiles = findViewById<LinearLayout>(R.id.option_write_tags_to_files)
        switchWriteTagsToFiles = findViewById(R.id.switch_write_tags_to_files)
        textWriteTagsStatus = findViewById(R.id.text_write_tags_status)

        optionWriteTagsToFiles.setOnClickListener {
            switchWriteTagsToFiles.toggle()
        }

        switchWriteTagsToFiles.setOnCheckedChangeListener { _, isChecked ->
            preferences.writeTagsToFiles = isChecked
            updateWriteTagsStatusDisplay(isChecked)
        }

        // Option: Recherche auto des paroles
        val optionAutoFetchLyrics = findViewById<LinearLayout>(R.id.option_auto_fetch_lyrics)
        switchAutoFetchLyrics = findViewById(R.id.switch_auto_fetch_lyrics)
        textAutoFetchStatus = findViewById(R.id.text_auto_fetch_status)

        optionAutoFetchLyrics.setOnClickListener {
            switchAutoFetchLyrics.toggle()
        }

        switchAutoFetchLyrics.setOnCheckedChangeListener { _, isChecked ->
            preferences.autoFetchLyrics = isChecked
            updateAutoFetchStatusDisplay(isChecked)
            updateLyricsApiSectionVisibility(isChecked)
        }

        // Setup Lyrics API section
        setupLyricsApiSection()

        // Setup Navidrome section
        setupNavidromeSection()

        // Option: Tri des albums
        val optionAlbumSort = findViewById<LinearLayout>(R.id.option_album_sort)
        textAlbumSortValue = findViewById(R.id.text_album_sort_value)

        optionAlbumSort.setOnClickListener {
            showAlbumSortDialog()
        }

        // Option: Migration des tags ID3
        val optionTagMigration = findViewById<LinearLayout>(R.id.option_tag_migration)
        optionTagMigration.setOnClickListener {
            showTagMigrationConfirmDialog()
        }

        // Option: Sync pending POPM tags
        val optionSyncPendingPopm = findViewById<LinearLayout>(R.id.option_sync_pending_popm)
        optionSyncPendingPopm.setOnClickListener {
            checkAndShowSyncPendingDialog()
        }
    }

    private fun showTagMigrationConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.music_settings_tag_migration_title)
            .setMessage(R.string.music_settings_tag_migration_confirm)
            .setPositiveButton(R.string.music_settings_tag_migration_start) { _, _ ->
                // Retourner à MusicPlayerActivity et lancer la migration
                setResult(RESULT_TAG_MIGRATION_REQUESTED)
                finish()
            }
            .setNegativeButton(R.string.music_cancel, null)
            .show()
    }

    private fun checkAndShowSyncPendingDialog() {
        lifecycleScope.launch {
            val pendingCount = MusicPopmSyncManager.getPendingCount()

            if (pendingCount == 0) {
                // No pending updates
                AlertDialog.Builder(this@MusicSettingsActivity)
                    .setTitle(R.string.music_settings_sync_pending_popm)
                    .setMessage(R.string.music_settings_sync_pending_popm_none)
                    .setPositiveButton(R.string.common_ok, null)
                    .show()
            } else {
                // Show confirmation with count
                AlertDialog.Builder(this@MusicSettingsActivity)
                    .setTitle(R.string.music_settings_sync_pending_popm)
                    .setMessage(getString(R.string.music_settings_sync_pending_popm_confirm, pendingCount))
                    .setPositiveButton(R.string.music_settings_sync_pending_popm_start) { _, _ ->
                        performPendingPopmSync()
                    }
                    .setNegativeButton(R.string.music_cancel, null)
                    .show()
            }
        }
    }

    private fun performPendingPopmSync() {
        // Show progress toast
        Toast.makeText(
            this,
            R.string.music_settings_sync_pending_popm_progress,
            Toast.LENGTH_SHORT
        ).show()

        lifecycleScope.launch {
            // Force process all pending updates
            MusicPopmSyncManager.processPendingUpdates()

            // Show completion toast
            Toast.makeText(
                this@MusicSettingsActivity,
                R.string.music_settings_sync_pending_popm_complete,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        const val RESULT_TAG_MIGRATION_REQUESTED = 101
    }

    private fun updatePlayCountStatusDisplay(enabled: Boolean) {
        textPlayCountStatus.text = if (enabled) {
            getString(R.string.music_settings_enabled)
        } else {
            getString(R.string.music_settings_disabled)
        }
    }

    private fun updateAutoFetchStatusDisplay(enabled: Boolean) {
        textAutoFetchStatus.text = if (enabled) {
            getString(R.string.music_settings_enabled)
        } else {
            getString(R.string.music_settings_disabled)
        }
    }

    private fun updateWriteTagsStatusDisplay(enabled: Boolean) {
        textWriteTagsStatus.text = if (enabled) {
            getString(R.string.music_settings_enabled)
        } else {
            getString(R.string.music_settings_disabled)
        }
    }

    // ==================== Lyrics API Configuration ====================

    private fun setupLyricsApiSection() {
        sectionLyricsApi = findViewById(R.id.section_lyrics_api)
        editLyricsApiPrimary = findViewById(R.id.edit_lyrics_api_primary)
        editLyricsApiPrimaryHeaders = findViewById(R.id.edit_lyrics_api_primary_headers)
        editLyricsApiPrimarySyncedPath = findViewById(R.id.edit_lyrics_api_primary_synced_path)
        editLyricsApiPrimaryLyricsPath = findViewById(R.id.edit_lyrics_api_primary_lyrics_path)
        editLyricsApiFallback = findViewById(R.id.edit_lyrics_api_fallback)
        editLyricsApiFallbackHeaders = findViewById(R.id.edit_lyrics_api_fallback_headers)
        editLyricsApiFallbackSyncedPath = findViewById(R.id.edit_lyrics_api_fallback_synced_path)
        editLyricsApiFallbackLyricsPath = findViewById(R.id.edit_lyrics_api_fallback_lyrics_path)
        btnTestLyricsApi = findViewById(R.id.btn_test_lyrics_api)
        textApiTestResult = findViewById(R.id.text_api_test_result)
        toggleApi1Advanced = findViewById(R.id.toggle_api1_advanced)
        containerApi1Advanced = findViewById(R.id.container_api1_advanced)
        toggleApi2Advanced = findViewById(R.id.toggle_api2_advanced)
        containerApi2Advanced = findViewById(R.id.container_api2_advanced)

        // Setup advanced options toggles
        setupAdvancedOptionsToggle(toggleApi1Advanced, containerApi1Advanced)
        setupAdvancedOptionsToggle(toggleApi2Advanced, containerApi2Advanced)

        // Load saved values
        editLyricsApiPrimary.setText(preferences.lyricsApiPrimary)
        editLyricsApiPrimaryHeaders.setText(preferences.lyricsApiPrimaryHeaders)
        editLyricsApiPrimarySyncedPath.setText(preferences.lyricsApiPrimarySyncedPath)
        editLyricsApiPrimaryLyricsPath.setText(preferences.lyricsApiPrimaryLyricsPath)
        editLyricsApiFallback.setText(preferences.lyricsApiFallback)
        editLyricsApiFallbackHeaders.setText(preferences.lyricsApiFallbackHeaders)
        editLyricsApiFallbackSyncedPath.setText(preferences.lyricsApiFallbackSyncedPath)
        editLyricsApiFallbackLyricsPath.setText(preferences.lyricsApiFallbackLyricsPath)

        // Save on text change (with debounce effect from focus loss)
        editLyricsApiPrimary.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                preferences.lyricsApiPrimary = editLyricsApiPrimary.text?.toString() ?: ""
            }
        }

        editLyricsApiFallback.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                preferences.lyricsApiFallback = editLyricsApiFallback.text?.toString() ?: ""
            }
        }

        editLyricsApiPrimaryHeaders.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                preferences.lyricsApiPrimaryHeaders = editLyricsApiPrimaryHeaders.text?.toString() ?: ""
            }
        }

        editLyricsApiPrimarySyncedPath.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                preferences.lyricsApiPrimarySyncedPath = editLyricsApiPrimarySyncedPath.text?.toString() ?: ""
            }
        }

        editLyricsApiPrimaryLyricsPath.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                preferences.lyricsApiPrimaryLyricsPath = editLyricsApiPrimaryLyricsPath.text?.toString() ?: ""
            }
        }

        editLyricsApiFallbackHeaders.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                preferences.lyricsApiFallbackHeaders = editLyricsApiFallbackHeaders.text?.toString() ?: ""
            }
        }

        editLyricsApiFallbackSyncedPath.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                preferences.lyricsApiFallbackSyncedPath = editLyricsApiFallbackSyncedPath.text?.toString() ?: ""
            }
        }

        editLyricsApiFallbackLyricsPath.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                preferences.lyricsApiFallbackLyricsPath = editLyricsApiFallbackLyricsPath.text?.toString() ?: ""
            }
        }

        // Also save when user presses Done on keyboard
        editLyricsApiPrimary.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                preferences.lyricsApiPrimary = s?.toString() ?: ""
            }
        })

        editLyricsApiPrimaryHeaders.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                preferences.lyricsApiPrimaryHeaders = s?.toString() ?: ""
            }
        })

        editLyricsApiPrimarySyncedPath.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                preferences.lyricsApiPrimarySyncedPath = s?.toString() ?: ""
            }
        })

        editLyricsApiPrimaryLyricsPath.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                preferences.lyricsApiPrimaryLyricsPath = s?.toString() ?: ""
            }
        })

        editLyricsApiFallback.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                preferences.lyricsApiFallback = s?.toString() ?: ""
            }
        })

        editLyricsApiFallbackHeaders.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                preferences.lyricsApiFallbackHeaders = s?.toString() ?: ""
            }
        })

        editLyricsApiFallbackSyncedPath.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                preferences.lyricsApiFallbackSyncedPath = s?.toString() ?: ""
            }
        })

        editLyricsApiFallbackLyricsPath.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                preferences.lyricsApiFallbackLyricsPath = s?.toString() ?: ""
            }
        })

        // Test primary API when user presses Enter/Next
        editLyricsApiPrimary.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_NEXT ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val url = editLyricsApiPrimary.text?.toString()
                if (!url.isNullOrBlank()) {
                    testSingleApi(url, isPrimary = true)
                }
            }
            false // Let the system handle the action
        }

        // Test fallback API when user presses Enter/Done
        editLyricsApiFallback.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val url = editLyricsApiFallback.text?.toString()
                if (!url.isNullOrBlank()) {
                    testSingleApi(url, isPrimary = false)
                }
            }
            false
        }

        // Test both APIs button
        btnTestLyricsApi.setOnClickListener {
            testBothApis()
        }
    }

    /**
     * Test a single API and show toast result
     */
    private fun testSingleApi(url: String, isPrimary: Boolean) {
        val apiName = if (isPrimary) {
            getString(R.string.lyrics_api_primary_label)
        } else {
            getString(R.string.lyrics_api_fallback_label)
        }

        lifecycleScope.launch {
            val config = if (isPrimary) buildPrimaryApiConfig(url) else buildFallbackApiConfig(url)
            if (config == null) {
                Toast.makeText(this@MusicSettingsActivity, R.string.lyrics_api_test_no_urls, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val message = when (val result = GenericLyricsApiClient(config).testConnection()) {
                is ApiTestResult.Success -> getString(R.string.lyrics_api_test_success, apiName)
                is ApiTestResult.RateLimited -> getString(R.string.lyrics_api_test_rate_limited, apiName)
                is ApiTestResult.UnknownHost -> getString(R.string.lyrics_api_test_unknown_host, apiName)
                is ApiTestResult.Timeout -> getString(R.string.lyrics_api_test_timeout, apiName)
                is ApiTestResult.HttpError -> getString(R.string.lyrics_api_test_http_error, apiName, result.code)
                is ApiTestResult.Error -> getString(R.string.lyrics_api_test_error, apiName, result.message)
            }

            Toast.makeText(this@MusicSettingsActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Test both APIs and show detailed result
     */
    private fun testBothApis() {
        val primaryUrl = editLyricsApiPrimary.text?.toString() ?: ""
        val fallbackUrl = editLyricsApiFallback.text?.toString() ?: ""

        if (primaryUrl.isBlank() && fallbackUrl.isBlank()) {
            Toast.makeText(this, R.string.lyrics_api_test_no_urls, Toast.LENGTH_SHORT).show()
            return
        }

        btnTestLyricsApi.isEnabled = false
        btnTestLyricsApi.text = getString(R.string.lyrics_api_test_in_progress)
        textApiTestResult.visibility = View.GONE

        lifecycleScope.launch {
            val results = mutableListOf<String>()
            var allSuccess = true

            // Test primary API if provided
            if (primaryUrl.isNotBlank()) {
                buildPrimaryApiConfig(primaryUrl)?.let { config ->
                    val primaryResult = GenericLyricsApiClient(config).testConnection()
                    val primaryName = getString(R.string.lyrics_api_primary_label)
                    val primaryStatus = formatApiTestResult(primaryResult, primaryName)
                    results.add(primaryStatus)
                    if (!primaryResult.isWorking()) allSuccess = false
                }
            }

            // Test fallback API if provided
            if (fallbackUrl.isNotBlank()) {
                buildFallbackApiConfig(fallbackUrl)?.let { config ->
                    val fallbackResult = GenericLyricsApiClient(config).testConnection()
                    val fallbackName = getString(R.string.lyrics_api_fallback_label)
                    val fallbackStatus = formatApiTestResult(fallbackResult, fallbackName)
                    results.add(fallbackStatus)
                    if (!fallbackResult.isWorking()) allSuccess = false
                }
            }

            // Show result
            btnTestLyricsApi.isEnabled = true
            btnTestLyricsApi.text = getString(R.string.lyrics_api_test_button)

            textApiTestResult.visibility = View.VISIBLE
            textApiTestResult.text = results.joinToString("\n")
            textApiTestResult.setTextColor(
                ContextCompat.getColor(
                    this@MusicSettingsActivity,
                    if (allSuccess) R.color.lyrics_api_test_success else R.color.lyrics_api_test_error
                )
            )
        }
    }

    /**
     * Format API test result for display
     */
    private fun formatApiTestResult(result: ApiTestResult, apiName: String): String {
        return when (result) {
            is ApiTestResult.Success -> "✓ $apiName: ${getString(R.string.lyrics_api_status_ok)}"
            is ApiTestResult.RateLimited -> "⚠ $apiName: ${getString(R.string.lyrics_api_status_rate_limited)}"
            is ApiTestResult.UnknownHost -> "✗ $apiName: ${getString(R.string.lyrics_api_status_unknown_host)}"
            is ApiTestResult.Timeout -> "✗ $apiName: ${getString(R.string.lyrics_api_status_timeout)}"
            is ApiTestResult.HttpError -> "✗ $apiName: HTTP ${result.code}"
            is ApiTestResult.Error -> "✗ $apiName: ${result.message}"
        }
    }

    private fun updateLyricsApiSectionVisibility(autoFetchEnabled: Boolean) {
        sectionLyricsApi.visibility = if (autoFetchEnabled) View.VISIBLE else View.GONE
    }

    private fun setupAdvancedOptionsToggle(toggle: TextView, container: LinearLayout) {
        toggle.setOnClickListener {
            val isExpanded = container.visibility == View.VISIBLE
            if (isExpanded) {
                container.visibility = View.GONE
                toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand_more, 0)
            } else {
                container.visibility = View.VISIBLE
                toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand_less, 0)
            }
        }
    }

    private fun buildPrimaryApiConfig(url: String): LyricsApiConfig? {
        return LyricsApiConfig.fromUserInput(
            rawUrl = url,
            headersText = editLyricsApiPrimaryHeaders.text?.toString(),
            lyricsPath = editLyricsApiPrimaryLyricsPath.text?.toString(),
            syncedLyricsPath = editLyricsApiPrimarySyncedPath.text?.toString(),
            isPrimary = true
        )
    }

    private fun buildFallbackApiConfig(url: String): LyricsApiConfig? {
        return LyricsApiConfig.fromUserInput(
            rawUrl = url,
            headersText = editLyricsApiFallbackHeaders.text?.toString(),
            lyricsPath = editLyricsApiFallbackLyricsPath.text?.toString(),
            syncedLyricsPath = editLyricsApiFallbackSyncedPath.text?.toString(),
            isPrimary = false
        )
    }

    private fun loadPreferences() {
        // Charger l'état du switch
        switchShowPlayCount.isChecked = preferences.showPlayCount
        updatePlayCountStatusDisplay(preferences.showPlayCount)

        // Charger l'état de l'écriture des tags
        switchWriteTagsToFiles.isChecked = preferences.writeTagsToFiles
        updateWriteTagsStatusDisplay(preferences.writeTagsToFiles)

        // Charger l'état de l'auto-fetch des paroles
        switchAutoFetchLyrics.isChecked = preferences.autoFetchLyrics
        updateAutoFetchStatusDisplay(preferences.autoFetchLyrics)
        updateLyricsApiSectionVisibility(preferences.autoFetchLyrics)

        // Afficher la valeur actuelle du tri
        updateAlbumSortDisplay()
    }

    private fun updateAlbumSortDisplay() {
        val sortOrderText = when (preferences.albumSortOrder) {
            MusicLibrary.AlbumSortOrder.NAME_ASC -> getString(R.string.music_sort_name_asc)
            MusicLibrary.AlbumSortOrder.NAME_DESC -> getString(R.string.music_sort_name_desc)
            MusicLibrary.AlbumSortOrder.YEAR_ASC -> getString(R.string.music_sort_year_asc)
            MusicLibrary.AlbumSortOrder.YEAR_DESC -> getString(R.string.music_sort_year_desc)
        }
        textAlbumSortValue.text = sortOrderText
    }

    private fun showAlbumSortDialog() {
        val options = arrayOf(
            getString(R.string.music_sort_name_asc),
            getString(R.string.music_sort_name_desc),
            getString(R.string.music_sort_year_asc),
            getString(R.string.music_sort_year_desc)
        )

        val currentIndex = preferences.albumSortOrder.ordinal

        AlertDialog.Builder(this)
            .setTitle(R.string.music_sort_albums)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val sortOrder = MusicLibrary.AlbumSortOrder.entries[which]
                preferences.albumSortOrder = sortOrder
                MusicLibrary.setAlbumSortOrder(sortOrder)
                updateAlbumSortDisplay()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.music_cancel, null)
            .show()
    }

    // ==================== Cloud Sync ====================

    private fun setupCloudLinkSection() {
        optionCloudLink = findViewById(R.id.option_cloud_link)
        iconCloudLink = findViewById(R.id.icon_cloud_link)
        textCloudLinkSummary = findViewById(R.id.text_cloud_link_summary)
        btnResetPlayCounts = findViewById(R.id.btn_reset_play_counts)

        optionCloudLink.setOnClickListener {
            startActivity(CloudActivity.intent(this))
        }

        btnResetPlayCounts.setOnClickListener {
            showResetPlayCountsDialog()
        }

        updateCloudLinkUI()
    }

    /**
     * La ligne cloud montre le compte lie, et le bouton de remise a plat des
     * compteurs n'apparait que dans ce cas : il touche aussi au cloud.
     */
    private fun updateCloudLinkUI() {
        val email = googleSignInManager.getSignedInEmail()
        val signedIn = email != null

        textCloudLinkSummary.text = email ?: getString(R.string.cloud_settings_entry_desc)
        iconCloudLink.setImageResource(
            if (signedIn) R.drawable.ic_cloud else R.drawable.ic_cloud_off
        )
        btnResetPlayCounts.visibility = if (signedIn) View.VISIBLE else View.GONE
    }

    private fun showResetPlayCountsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.music_settings_reset_play_counts_title)
            .setMessage(R.string.music_settings_reset_play_counts_message)
            .setPositiveButton(R.string.music_settings_reset_play_counts_confirm) { _, _ ->
                performPlayCountReset()
            }
            .setNegativeButton(R.string.music_cancel, null)
            .show()
    }

    private fun performPlayCountReset() {
        btnResetPlayCounts.isEnabled = false
        btnResetPlayCounts.text = getString(R.string.music_settings_reset_in_progress)

        lifecycleScope.launch {
            CloudSyncManager.init(this@MusicSettingsActivity)
            val result = CloudSyncManager.resetPlayCountsAfterBug()

            btnResetPlayCounts.isEnabled = true
            btnResetPlayCounts.text = getString(R.string.music_settings_reset_play_counts)

            if (result.success) {
                Toast.makeText(
                    this@MusicSettingsActivity,
                    getString(
                        R.string.music_settings_reset_success,
                        result.deletedCloudFiles,
                        result.resetPlayCounts
                    ),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@MusicSettingsActivity,
                    getString(R.string.music_settings_reset_error, result.errorMessage),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ==================== LAN Sync Section ====================

    private fun setupTrustedWifiSection() {
        optionTrustedWifi     = findViewById(R.id.option_trusted_wifi)
        switchTrustedWifi     = findViewById(R.id.switch_trusted_wifi)
        textTrustedWifiStatus = findViewById(R.id.text_trusted_wifi_status)

        updateTrustedWifiUI()

        optionTrustedWifi.setOnClickListener { switchTrustedWifi.toggle() }

        switchTrustedWifi.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                TrustedNetworkManager.trustCurrentNetwork(this)
                A2USyncService.startLanSync(this)
            } else {
                TrustedNetworkManager.untrustCurrentNetwork(this)
                A2USyncService.stopLanSync(this)
            }
            updateTrustedWifiUI()
        }
    }

    private fun updateTrustedWifiUI() {
        val networkName = TrustedNetworkManager.getCurrentNetworkName(this)
        val onWifi      = TrustedNetworkManager.isOnWifi(this)
        val isTrusted   = TrustedNetworkManager.isCurrentNetworkTrusted(this)

        // Bloquer le listener pendant la mise à jour programmatique
        switchTrustedWifi.setOnCheckedChangeListener(null)
        switchTrustedWifi.isChecked = isTrusted
        switchTrustedWifi.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                TrustedNetworkManager.trustCurrentNetwork(this)
                A2USyncService.startLanSync(this)
            } else {
                TrustedNetworkManager.untrustCurrentNetwork(this)
                A2USyncService.stopLanSync(this)
            }
            updateTrustedWifiUI()
        }

        textTrustedWifiStatus.text = when {
            !onWifi -> getString(R.string.music_settings_trusted_wifi_no_wifi)
            isTrusted && networkName != null -> getString(R.string.music_settings_trusted_wifi_active, networkName)
            isTrusted -> getString(R.string.music_settings_trusted_wifi_active, getString(R.string.music_settings_trusted_wifi_unknown_network))
            else -> getString(R.string.music_settings_trusted_wifi_inactive)
        }
        switchTrustedWifi.isEnabled = onWifi
    }

    // ==================== Navidrome Configuration ====================

    private fun setupNavidromeSection() {
        editNavidromeServerUrl = findViewById(R.id.edit_navidrome_server_url)
        editNavidromeUsername = findViewById(R.id.edit_navidrome_username)
        editNavidromePassword = findViewById(R.id.edit_navidrome_password)
        btnTestNavidrome = findViewById(R.id.btn_test_navidrome)

        editNavidromeServerUrl.setText(preferences.navidromeServerUrl)
        editNavidromeUsername.setText(preferences.navidromeUsername)
        editNavidromePassword.setText(preferences.navidromePassword)

        editNavidromeServerUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                preferences.navidromeServerUrl = s?.toString()?.trim() ?: ""
            }
        })

        editNavidromeUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                preferences.navidromeUsername = s?.toString()?.trim() ?: ""
            }
        })

        editNavidromePassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                preferences.navidromePassword = s?.toString() ?: ""
            }
        })

        btnTestNavidrome.setOnClickListener {
            val url = preferences.navidromeServerUrl
            val user = preferences.navidromeUsername
            val pass = preferences.navidromePassword
            if (url.isBlank() || user.isBlank()) {
                Toast.makeText(this, R.string.navidrome_not_configured, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnTestNavidrome.isEnabled = false
            lifecycleScope.launch {
                val client = SubsonicApiClient(url, user, pass)
                val ok = client.testConnection()
                btnTestNavidrome.isEnabled = true
                val msgRes = if (ok) R.string.navidrome_connected else R.string.navidrome_connection_failed
                Toast.makeText(this@MusicSettingsActivity, msgRes, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
