package com.Atom2Universe.app.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.AudioHubActivity
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.music.lyrics.api.ApiTestResult
import com.Atom2Universe.app.music.lyrics.api.GenericLyricsApiClient
import com.Atom2Universe.app.music.lyrics.api.LyricsApiConfig
import com.Atom2Universe.app.music.navidrome.SubsonicApiClient
import com.Atom2Universe.app.music.sync.BackupManager
import com.Atom2Universe.app.music.sync.CloudSyncManager
import com.Atom2Universe.app.music.sync.GoogleSignInManager
import com.Atom2Universe.app.music.sync.SyncResult
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

    // Cloud Sync Views
    private lateinit var textGoogleEmail: TextView
    private lateinit var btnSignInOut: MaterialButton
    private lateinit var optionEnableSync: LinearLayout
    private lateinit var switchEnableSync: SwitchMaterial
    private lateinit var textLastSync: TextView
    private lateinit var btnSyncNow: MaterialButton
    private lateinit var btnResetPlayCounts: MaterialButton

    // LAN Sync Views
    private lateinit var optionTrustedWifi: LinearLayout
    private lateinit var switchTrustedWifi: SwitchMaterial
    private lateinit var textTrustedWifiStatus: TextView

    // Backup Views
    private lateinit var dividerBackup: View
    private lateinit var textBackupSection: TextView
    private lateinit var optionPrimaryDevice: LinearLayout
    private lateinit var switchPrimaryDevice: SwitchMaterial
    private lateinit var textLastBackup: TextView
    private lateinit var btnRestoreBackup: MaterialButton
    private lateinit var btnDeleteCloudData: MaterialButton
    private var isUpdatingPrimarySwitch = false // Prevent listener trigger on programmatic update

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = googleSignInManager.handleSignInResult(result.data)
        if (account != null) {
            updateCloudSyncUI()
            lifecycleScope.launch {
                CloudSyncManager.init(this@MusicSettingsActivity)
                CloudSyncManager.scheduleNightlySync()
                // Check for existing backup on first sign-in
                checkForExistingBackup()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_music_settings)

        preferences = MusicPreferences.getInstance(this)
        googleSignInManager = GoogleSignInManager(this)

        setupToolbar()
        setupViews()
        setupCloudSyncSection()
        setupTrustedWifiSection()
        setupBackupSection()
        loadPreferences()
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

    private fun setupCloudSyncSection() {
        textGoogleEmail = findViewById(R.id.text_google_email)
        btnSignInOut = findViewById(R.id.btn_sign_in_out)
        optionEnableSync = findViewById(R.id.option_enable_sync)
        switchEnableSync = findViewById(R.id.switch_enable_sync)
        textLastSync = findViewById(R.id.text_last_sync)
        btnSyncNow = findViewById(R.id.btn_sync_now)
        btnResetPlayCounts = findViewById(R.id.btn_reset_play_counts)

        btnSignInOut.setOnClickListener {
            if (googleSignInManager.isSignedIn()) {
                // Sign out
                lifecycleScope.launch {
                    googleSignInManager.signOut()
                    CloudSyncManager.setSyncEnabled(false)
                    updateCloudSyncUI()
                }
            } else {
                // Sign in
                signInLauncher.launch(googleSignInManager.getSignInIntent())
            }
        }

        optionEnableSync.setOnClickListener {
            switchEnableSync.toggle()
        }

        switchEnableSync.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                CloudSyncManager.init(this@MusicSettingsActivity)
                CloudSyncManager.setSyncEnabled(isChecked)
                updateSyncControlsVisibility(isChecked)
            }
        }

        btnSyncNow.setOnClickListener {
            performManualSync()
        }

        btnResetPlayCounts.setOnClickListener {
            showResetPlayCountsDialog()
        }

        // Initial UI update
        updateCloudSyncUI()
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

    private fun updateCloudSyncUI() {
        val isSignedIn = googleSignInManager.isSignedIn()

        if (isSignedIn) {
            textGoogleEmail.text = googleSignInManager.getSignedInEmail()
            btnSignInOut.text = getString(R.string.music_settings_sign_out)
            optionEnableSync.visibility = View.VISIBLE

            lifecycleScope.launch {
                CloudSyncManager.init(this@MusicSettingsActivity)
                val syncEnabled = CloudSyncManager.isSyncEnabled()
                switchEnableSync.isChecked = syncEnabled
                updateSyncControlsVisibility(syncEnabled)
                updateLastSyncDisplay()
            }
        } else {
            textGoogleEmail.text = getString(R.string.music_settings_not_signed_in)
            btnSignInOut.text = getString(R.string.music_settings_sign_in)
            optionEnableSync.visibility = View.GONE
            textLastSync.visibility = View.GONE
            btnSyncNow.visibility = View.GONE
            btnResetPlayCounts.visibility = View.GONE
        }
    }

    private fun updateSyncControlsVisibility(syncEnabled: Boolean) {
        textLastSync.visibility = if (syncEnabled) View.VISIBLE else View.GONE
        btnSyncNow.visibility = if (syncEnabled) View.VISIBLE else View.GONE
        // Reset button hidden but kept in code for future use if needed
        // btnResetPlayCounts.visibility = if (syncEnabled) View.VISIBLE else View.GONE
        // Also show/hide backup section when sync is enabled/disabled
        updateBackupSectionVisibility(syncEnabled && googleSignInManager.isSignedIn())
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

    // ==================== Backup Section ====================

    private fun setupBackupSection() {
        dividerBackup = findViewById(R.id.divider_backup)
        textBackupSection = findViewById(R.id.text_backup_section)
        optionPrimaryDevice = findViewById(R.id.option_primary_device)
        switchPrimaryDevice = findViewById(R.id.switch_primary_device)
        textLastBackup = findViewById(R.id.text_last_backup)
        btnRestoreBackup = findViewById(R.id.btn_restore_backup)
        btnDeleteCloudData = findViewById(R.id.btn_delete_cloud_data)

        btnDeleteCloudData.setOnClickListener {
            showDeleteCloudDataWarning()
        }

        optionPrimaryDevice.setOnClickListener {
            switchPrimaryDevice.toggle()
        }

        switchPrimaryDevice.setOnCheckedChangeListener { _, isChecked ->
            // Skip if this is a programmatic update (not user action)
            if (isUpdatingPrimarySwitch) return@setOnCheckedChangeListener

            lifecycleScope.launch {
                CloudSyncManager.init(this@MusicSettingsActivity)
                val success = CloudSyncManager.setPrimaryDevice(isChecked)
                if (success) {
                    val messageRes = if (isChecked) {
                        R.string.music_settings_primary_device_set
                    } else {
                        R.string.music_settings_primary_device_unset
                    }
                    Toast.makeText(this@MusicSettingsActivity, messageRes, Toast.LENGTH_SHORT).show()
                    // Update button text based on new primary status
                    updateBackupButtonState(isChecked)
                } else {
                    // Revert switch on failure
                    isUpdatingPrimarySwitch = true
                    switchPrimaryDevice.isChecked = !isChecked
                    isUpdatingPrimarySwitch = false
                    Toast.makeText(
                        this@MusicSettingsActivity,
                        R.string.music_settings_sync_error,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        btnRestoreBackup.setOnClickListener {
            if (switchPrimaryDevice.isChecked) {
                performBackup()
            } else {
                performRestore()
            }
        }

        // Initial state hidden
        updateBackupSectionVisibility(false)
    }

    private fun updateBackupButtonState(isPrimary: Boolean) {
        if (isPrimary) {
            btnRestoreBackup.text = getString(R.string.music_settings_backup_now)
        } else {
            btnRestoreBackup.text = getString(R.string.music_settings_restore_backup)
        }
    }

    private fun updateBackupSectionVisibility(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        dividerBackup.visibility = visibility
        textBackupSection.visibility = visibility
        optionPrimaryDevice.visibility = visibility
        textLastBackup.visibility = visibility
        btnRestoreBackup.visibility = visibility
        btnDeleteCloudData.visibility = visibility

        if (visible) {
            updateBackupUI()
        }
    }

    /**
     * First step: Show warning dialog about deleting cloud data
     */
    private fun showDeleteCloudDataWarning() {
        AlertDialog.Builder(this)
            .setTitle(R.string.music_settings_delete_cloud_data_title)
            .setMessage(R.string.music_settings_delete_cloud_data_warning)
            .setPositiveButton(R.string.music_settings_delete_cloud_data_continue) { _, _ ->
                showDeleteCloudDataConfirmation()
            }
            .setNegativeButton(R.string.music_cancel, null)
            .show()
    }

    /**
     * Second step: Require user to type confirmation word
     */
    private fun showDeleteCloudDataConfirmation() {
        val confirmWord = getString(R.string.music_settings_delete_cloud_data_confirm_word)

        val inputLayout = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = getString(R.string.music_settings_delete_cloud_data_confirm_hint, confirmWord)
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(48, 32, 48, 0)
        }

        val editText = TextInputEditText(inputLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        inputLayout.addView(editText)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.music_settings_delete_cloud_data_confirm_title)
            .setMessage(getString(R.string.music_settings_delete_cloud_data_confirm_message, confirmWord))
            .setView(inputLayout)
            .setPositiveButton(R.string.music_settings_delete_cloud_data_delete, null) // Set later
            .setNegativeButton(R.string.music_cancel, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.isEnabled = false

            // Enable button only when correct word is typed
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    positiveButton.isEnabled = s.toString().equals(confirmWord, ignoreCase = true)
                }
            })

            positiveButton.setOnClickListener {
                dialog.dismiss()
                performDeleteCloudData()
            }
        }

        dialog.show()
    }

    /**
     * Actually delete all cloud data
     */
    private fun performDeleteCloudData() {
        btnDeleteCloudData.isEnabled = false
        btnDeleteCloudData.text = getString(R.string.music_settings_delete_cloud_data_in_progress)

        lifecycleScope.launch {
            CloudSyncManager.init(this@MusicSettingsActivity)
            val result = CloudSyncManager.deleteAllCloudData()

            btnDeleteCloudData.isEnabled = true
            btnDeleteCloudData.text = getString(R.string.music_settings_delete_cloud_data)

            if (result.success) {
                Toast.makeText(
                    this@MusicSettingsActivity,
                    getString(R.string.music_settings_delete_cloud_data_success, result.deletedFilesCount),
                    Toast.LENGTH_LONG
                ).show()
                // Refresh UI
                updateBackupUI()
                updateLastSyncDisplay()
            } else {
                Toast.makeText(
                    this@MusicSettingsActivity,
                    getString(R.string.music_settings_delete_cloud_data_error, result.errorMessage),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateBackupUI() {
        lifecycleScope.launch {
            CloudSyncManager.init(this@MusicSettingsActivity)

            // Check if this device is primary
            val isPrimary = CloudSyncManager.isPrimaryDevice()
            // Prevent listener from firing during programmatic update
            isUpdatingPrimarySwitch = true
            switchPrimaryDevice.isChecked = isPrimary
            isUpdatingPrimarySwitch = false
            updateBackupButtonState(isPrimary)

            // Check for backup info
            val manifest = BackupManager.checkBackupExists(this@MusicSettingsActivity)
            if (manifest != null) {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val dateStr = dateFormat.format(Date(manifest.createdAt))
                textLastBackup.text = getString(
                    R.string.music_settings_last_backup_format,
                    dateStr,
                    manifest.deviceName
                )
            } else {
                textLastBackup.text = getString(R.string.music_settings_last_backup_never)
            }
        }
    }

    private suspend fun checkForExistingBackup() {
        val manifest = BackupManager.checkBackupExists(this@MusicSettingsActivity)
        if (manifest != null && manifest.contents.playCountsCount > 0) {
            // Found a backup, show dialog on main thread
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val dateStr = dateFormat.format(Date(manifest.createdAt))

            val totalFavorites = manifest.contents.trackFavoritesCount + manifest.contents.albumFavoritesCount

            runOnUiThread {
                AlertDialog.Builder(this@MusicSettingsActivity)
                    .setTitle(R.string.music_backup_found_title)
                    .setMessage(getString(
                        R.string.music_backup_found_message,
                        manifest.contents.playCountsCount,
                        totalFavorites,
                        manifest.contents.playlistsCount,
                        manifest.contents.artistImagesCount,
                        manifest.contents.lyricsCount,
                        dateStr,
                        manifest.deviceName
                    ))
                    .setPositiveButton(R.string.music_backup_restore) { _, _ ->
                        performRestore()
                    }
                    .setNegativeButton(R.string.music_backup_ignore, null)
                    .show()
            }
        }
    }

    private fun performBackup() {
        btnRestoreBackup.isEnabled = false
        btnRestoreBackup.text = getString(R.string.music_settings_backup_in_progress)

        lifecycleScope.launch {
            val result = BackupManager.performBackup(this@MusicSettingsActivity)

            btnRestoreBackup.isEnabled = true
            updateBackupButtonState(switchPrimaryDevice.isChecked)

            when (result) {
                is BackupManager.BackupResult.Success -> {
                    Toast.makeText(
                        this@MusicSettingsActivity,
                        R.string.music_settings_backup_success,
                        Toast.LENGTH_SHORT
                    ).show()
                    // Refresh backup info
                    updateBackupUI()
                }
                is BackupManager.BackupResult.Error -> {
                    Toast.makeText(
                        this@MusicSettingsActivity,
                        getString(R.string.music_settings_backup_error, result.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
                is BackupManager.BackupResult.NotSignedIn -> {
                    Toast.makeText(
                        this@MusicSettingsActivity,
                        R.string.music_settings_not_signed_in,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is BackupManager.BackupResult.NotPrimaryDevice -> {
                    Toast.makeText(
                        this@MusicSettingsActivity,
                        "Not primary device",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun performRestore() {
        btnRestoreBackup.isEnabled = false
        btnRestoreBackup.text = getString(R.string.music_settings_restore_in_progress)

        lifecycleScope.launch {
            val result = BackupManager.performRestore(this@MusicSettingsActivity)

            btnRestoreBackup.isEnabled = true
            updateBackupButtonState(switchPrimaryDevice.isChecked)

            when (result) {
                is BackupManager.RestoreResult.Success -> {
                    val summary = result.summary
                    AlertDialog.Builder(this@MusicSettingsActivity)
                        .setTitle(R.string.music_restore_summary_title)
                        .setMessage(getString(
                            R.string.music_restore_summary_message,
                            summary.playCountsRestored,
                            summary.trackFavoritesRestored,
                            summary.albumFavoritesRestored,
                            summary.artistCustomizationsRestored,
                            summary.playlistsRestored,
                            summary.artistImagesRestored,
                            summary.lyricsRestored
                        ))
                        .setPositiveButton(R.string.common_ok, null)
                        .show()
                }
                is BackupManager.RestoreResult.NoBackupFound -> {
                    Toast.makeText(
                        this@MusicSettingsActivity,
                        R.string.music_settings_no_backup_found,
                        Toast.LENGTH_LONG
                    ).show()
                }
                is BackupManager.RestoreResult.Error -> {
                    Toast.makeText(
                        this@MusicSettingsActivity,
                        getString(R.string.music_settings_restore_error, result.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
                is BackupManager.RestoreResult.NotSignedIn -> {
                    Toast.makeText(
                        this@MusicSettingsActivity,
                        R.string.music_settings_not_signed_in,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
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

    private fun updateLastSyncDisplay() {
        lifecycleScope.launch {
            val lastSync = CloudSyncManager.getLastSyncTimestamp()
            if (lastSync > 0) {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val dateStr = dateFormat.format(Date(lastSync))
                textLastSync.text = getString(R.string.music_settings_last_sync_format, dateStr)
            } else {
                textLastSync.text = getString(R.string.music_settings_last_sync_never)
            }
        }
    }

    private fun performManualSync() {
        btnSyncNow.isEnabled = false
        btnSyncNow.text = getString(R.string.music_settings_sync_in_progress)

        lifecycleScope.launch {
            CloudSyncManager.init(this@MusicSettingsActivity)
            val result = CloudSyncManager.syncNow()

            btnSyncNow.isEnabled = true
            btnSyncNow.text = getString(R.string.music_settings_sync_now)

            when (result) {
                is SyncResult.Success -> {
                    Toast.makeText(
                        this@MusicSettingsActivity,
                        R.string.music_settings_sync_success,
                        Toast.LENGTH_SHORT
                    ).show()
                    updateLastSyncDisplay()
                }
                is SyncResult.Error -> {
                    Toast.makeText(
                        this@MusicSettingsActivity,
                        getString(R.string.music_settings_sync_error, result.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
                is SyncResult.NotSignedIn -> {
                    Toast.makeText(
                        this@MusicSettingsActivity,
                        R.string.music_settings_not_signed_in,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {}
            }
        }
    }
}
