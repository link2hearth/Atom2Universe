package com.Atom2Universe.app.cloud

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.music.sync.BackupManager
import com.Atom2Universe.app.music.sync.CloudSyncManager
import com.Atom2Universe.app.music.sync.GoogleSignInManager
import com.Atom2Universe.app.music.sync.SyncResult
import com.Atom2Universe.app.util.enableImmersiveMode
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Le point unique pour lier son compte Google et tenir le cloud propre.
 *
 * Avant cet écran, la connexion ne se faisait que depuis les réglages de la
 * musique — alors que les jeux, la lecture et les statistiques en dépendent
 * tout autant. Tout est ici, et les réglages musique n'y renvoient plus qu'un lien.
 *
 * Les suppressions sont irréversibles, donc chacune passe par deux écrans : ce
 * qu'on perd, puis le mot à taper. Seul le résiduel obsolète y échappe, parce
 * qu'un garde-fou qu'on franchit sans lire ne garde plus rien.
 */
class CloudActivity : ThemedActivity() {

    private lateinit var signInManager: GoogleSignInManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var accountIcon: ImageView
    private lateinit var accountEmail: TextView
    private lateinit var signInHint: TextView
    private lateinit var btnSignInOut: MaterialButton

    private lateinit var syncSection: View
    private lateinit var switchSync: SwitchMaterial
    private lateinit var lastSyncText: TextView
    private lateinit var btnSyncNow: MaterialButton

    private lateinit var backupSection: View
    private lateinit var optionPrimaryDevice: View
    private lateinit var switchPrimaryDevice: SwitchMaterial
    private lateinit var lastBackupText: TextView
    private lateinit var btnBackupRestore: MaterialButton

    private lateinit var usageSection: View
    private lateinit var btnRefresh: ImageButton
    private lateinit var usageTotal: TextView
    private lateinit var usageBar: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var categoryContainer: LinearLayout
    private lateinit var journalsSection: View
    private lateinit var journalContainer: LinearLayout
    private lateinit var btnDeleteAll: MaterialButton

    /** Empêche deux opérations Drive de se chevaucher sur un double appui. */
    private var isBusy = false

    /**
     * Vrai pendant qu'on repose l'état de l'interrupteur « appareil principal ».
     *
     * Sans ce drapeau, régler la position de départ déclencherait l'écouteur et
     * réécrirait dans le cloud un choix que personne n'a fait.
     */
    private var isUpdatingPrimarySwitch = false

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = signInManager.handleSignInResult(result.data)
        if (account == null) {
            toast(getString(R.string.cloud_sign_in_failed))
            scope.launch { refreshAccountUi() }
            return@registerForActivityResult
        }
        scope.launch {
            CloudSyncManager.init(this@CloudActivity)
            CloudSyncManager.scheduleNightlySync()
            refreshAccountUi()
            offerRestoreIfBackupExists()
            loadInventory()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_cloud)

        signInManager = GoogleSignInManager(this)
        bindViews()
        wireActions()

        scope.launch {
            CloudSyncManager.init(this@CloudActivity)
            refreshAccountUi()
            loadInventory()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun bindViews() {
        accountIcon = findViewById(R.id.cloud_account_icon)
        accountEmail = findViewById(R.id.cloud_account_email)
        signInHint = findViewById(R.id.cloud_sign_in_hint)
        btnSignInOut = findViewById(R.id.cloud_btn_sign_in_out)

        syncSection = findViewById(R.id.cloud_sync_section)
        switchSync = findViewById(R.id.cloud_switch_sync)
        lastSyncText = findViewById(R.id.cloud_last_sync)
        btnSyncNow = findViewById(R.id.cloud_btn_sync_now)

        backupSection = findViewById(R.id.cloud_backup_section)
        optionPrimaryDevice = findViewById(R.id.cloud_option_primary_device)
        switchPrimaryDevice = findViewById(R.id.cloud_switch_primary_device)
        lastBackupText = findViewById(R.id.cloud_last_backup)
        btnBackupRestore = findViewById(R.id.cloud_btn_backup_restore)

        usageSection = findViewById(R.id.cloud_usage_section)
        btnRefresh = findViewById(R.id.cloud_btn_refresh)
        usageTotal = findViewById(R.id.cloud_usage_total)
        usageBar = findViewById(R.id.cloud_usage_bar)
        progress = findViewById(R.id.cloud_progress)
        emptyText = findViewById(R.id.cloud_empty_text)
        categoryContainer = findViewById(R.id.cloud_category_container)
        journalsSection = findViewById(R.id.cloud_journals_section)
        journalContainer = findViewById(R.id.cloud_journal_container)
        btnDeleteAll = findViewById(R.id.cloud_btn_delete_all)
    }

    private fun wireActions() {
        findViewById<ImageButton>(R.id.back_button).setOnClickListener { finish() }

        btnSignInOut.setOnClickListener {
            if (signInManager.isSignedIn()) confirmSignOut()
            else signInLauncher.launch(signInManager.getSignInIntent())
        }

        switchSync.setOnCheckedChangeListener { _, checked ->
            // Pas de garde sur isBusy : l'interrupteur ne touche pas à Drive.
            scope.launch { CloudSyncManager.setSyncEnabled(checked) }
        }

        btnSyncNow.setOnClickListener { syncNow() }

        optionPrimaryDevice.setOnClickListener { switchPrimaryDevice.toggle() }
        switchPrimaryDevice.setOnCheckedChangeListener { _, checked ->
            if (isUpdatingPrimarySwitch) return@setOnCheckedChangeListener
            onPrimaryDeviceToggled(checked)
        }
        btnBackupRestore.setOnClickListener {
            if (switchPrimaryDevice.isChecked) performBackup() else performRestore()
        }

        btnRefresh.setOnClickListener { loadInventory() }
        btnDeleteAll.setOnClickListener { confirmDeleteAll() }
    }

    // ==================== Compte ====================

    private suspend fun refreshAccountUi() {
        val signedIn = signInManager.isSignedIn()

        accountEmail.text = if (signedIn) {
            signInManager.getSignedInEmail() ?: getString(R.string.cloud_not_signed_in)
        } else {
            getString(R.string.cloud_not_signed_in)
        }
        accountIcon.setImageResource(if (signedIn) R.drawable.ic_cloud else R.drawable.ic_cloud_off)
        accountIcon.imageTintList = ContextCompat.getColorStateList(
            this,
            if (signedIn) R.color.cloud_cat_music_color else R.color.startup_text_secondary
        )
        btnSignInOut.setText(if (signedIn) R.string.cloud_sign_out else R.string.cloud_sign_in)
        signInHint.visibility = if (signedIn) View.GONE else View.VISIBLE

        syncSection.visibility = if (signedIn) View.VISIBLE else View.GONE
        backupSection.visibility = if (signedIn) View.VISIBLE else View.GONE
        usageSection.visibility = if (signedIn) View.VISIBLE else View.GONE

        if (!signedIn) return

        // L'écouteur est reposé après coup : sans cela, régler l'état de départ
        // déclencherait une écriture et écraserait le choix de l'utilisateur.
        switchSync.setOnCheckedChangeListener(null)
        switchSync.isChecked = CloudSyncManager.isSyncEnabled()
        switchSync.setOnCheckedChangeListener { _, checked ->
            scope.launch { CloudSyncManager.setSyncEnabled(checked) }
        }

        updateBackupUi()

        val last = CloudSyncManager.getLastSyncTimestamp()
        lastSyncText.text = if (last <= 0L) {
            getString(R.string.cloud_last_sync_never)
        } else {
            getString(R.string.cloud_last_sync, formatDate(last))
        }
    }

    private fun confirmSignOut() {
        AlertDialog.Builder(this)
            .setTitle(R.string.cloud_sign_out_title)
            .setMessage(R.string.cloud_sign_out_message)
            .setNegativeButton(R.string.cloud_cancel, null)
            .setPositiveButton(R.string.cloud_sign_out) { _, _ ->
                scope.launch {
                    signInManager.signOut()
                    CloudSyncManager.setSyncEnabled(false)
                    CloudSyncManager.cancelDebouncedSync()
                    refreshAccountUi()
                }
            }
            .show()
    }

    // ==================== Synchronisation ====================

    private fun syncNow() {
        if (isBusy) return
        if (!signInManager.isSignedIn()) {
            toast(getString(R.string.cloud_sync_not_signed_in))
            return
        }
        scope.launch {
            setBusy(true, R.string.cloud_syncing)
            val result = CloudSyncManager.syncNow()
            setBusy(false, null)

            when (result) {
                is SyncResult.Success -> toast(getString(R.string.cloud_sync_success))
                is SyncResult.Error -> toast(getString(R.string.cloud_sync_failed, result.message))
                is SyncResult.NotSignedIn -> toast(getString(R.string.cloud_sync_not_signed_in))
                // NotInitialized / NotEnabled : rien d'actionnable à raconter,
                // le nom de classe anglais ne servirait personne.
                else -> toast(getString(R.string.cloud_sync_unavailable))
            }

            refreshAccountUi()
            loadInventory()
        }
    }

    // ==================== Sauvegarde ====================

    /**
     * L'instantané complet du cloud, publié par un seul appareil.
     *
     * Le même bouton sert à sauvegarder ou à restaurer selon le rôle : un
     * appareil principal publie, les autres relisent. Deux boutons côte à côte
     * inviteraient à restaurer par-dessus une sauvegarde plus fraîche.
     */
    private suspend fun updateBackupUi() {
        val isPrimary = CloudSyncManager.isPrimaryDevice()
        isUpdatingPrimarySwitch = true
        switchPrimaryDevice.isChecked = isPrimary
        isUpdatingPrimarySwitch = false
        updateBackupButtonLabel(isPrimary)

        val manifest = BackupManager.checkBackupExists(this)
        lastBackupText.text = if (manifest != null) {
            getString(
                R.string.music_settings_last_backup_format,
                formatDate(manifest.createdAt),
                manifest.deviceName
            )
        } else {
            getString(R.string.music_settings_last_backup_never)
        }
    }

    private fun updateBackupButtonLabel(isPrimary: Boolean) {
        btnBackupRestore.setText(
            if (isPrimary) R.string.music_settings_backup_now
            else R.string.music_settings_restore_backup
        )
    }

    private fun onPrimaryDeviceToggled(checked: Boolean) {
        scope.launch {
            val success = CloudSyncManager.setPrimaryDevice(checked)
            if (success) {
                toast(
                    getString(
                        if (checked) R.string.music_settings_primary_device_set
                        else R.string.music_settings_primary_device_unset
                    )
                )
                updateBackupButtonLabel(checked)
            } else {
                // Remettre l'interrupteur là où il était, sans rejouer l'écouteur.
                isUpdatingPrimarySwitch = true
                switchPrimaryDevice.isChecked = !checked
                isUpdatingPrimarySwitch = false
                toast(getString(R.string.music_settings_sync_error))
            }
        }
    }

    /**
     * Au moment ou l'on vient de lier un compte, proposer la restauration si le
     * cloud contient deja une sauvegarde.
     *
     * C'est le seul instant ou la question a du sens : plus tard, l'appareil a
     * ses propres donnees et restaurer par-dessus serait un piege.
     */
    private suspend fun offerRestoreIfBackupExists() {
        val manifest = BackupManager.checkBackupExists(this) ?: return
        if (manifest.contents.playCountsCount <= 0) return

        val totalFavorites =
            manifest.contents.trackFavoritesCount + manifest.contents.albumFavoritesCount

        AlertDialog.Builder(this)
            .setTitle(R.string.music_backup_found_title)
            .setMessage(
                getString(
                    R.string.music_backup_found_message,
                    manifest.contents.playCountsCount,
                    totalFavorites,
                    manifest.contents.playlistsCount,
                    manifest.contents.artistImagesCount,
                    manifest.contents.lyricsCount,
                    formatDate(manifest.createdAt),
                    manifest.deviceName
                )
            )
            .setPositiveButton(R.string.music_backup_restore) { _, _ -> performRestore() }
            .setNegativeButton(R.string.music_backup_ignore, null)
            .show()
    }

    private fun performBackup() {
        if (isBusy) return
        scope.launch {
            btnBackupRestore.isEnabled = false
            btnBackupRestore.setText(R.string.music_settings_backup_in_progress)

            val result = BackupManager.performBackup(this@CloudActivity)

            btnBackupRestore.isEnabled = true
            updateBackupButtonLabel(switchPrimaryDevice.isChecked)

            when (result) {
                is BackupManager.BackupResult.Success ->
                    toast(getString(R.string.music_settings_backup_success))
                is BackupManager.BackupResult.Error ->
                    toast(getString(R.string.music_settings_backup_error, result.message))
                is BackupManager.BackupResult.NotSignedIn ->
                    toast(getString(R.string.cloud_sync_not_signed_in))
                is BackupManager.BackupResult.NotPrimaryDevice ->
                    toast(getString(R.string.music_settings_primary_device_unset))
            }

            updateBackupUi()
            loadInventory()
        }
    }

    private fun performRestore() {
        if (isBusy) return
        scope.launch {
            btnBackupRestore.isEnabled = false
            btnBackupRestore.setText(R.string.music_settings_restore_in_progress)

            val result = BackupManager.performRestore(this@CloudActivity)

            btnBackupRestore.isEnabled = true
            updateBackupButtonLabel(switchPrimaryDevice.isChecked)

            when (result) {
                is BackupManager.RestoreResult.Success -> {
                    val summary = result.summary
                    AlertDialog.Builder(this@CloudActivity)
                        .setTitle(R.string.music_restore_summary_title)
                        .setMessage(
                            getString(
                                R.string.music_restore_summary_message,
                                summary.playCountsRestored,
                                summary.trackFavoritesRestored,
                                summary.albumFavoritesRestored,
                                summary.artistCustomizationsRestored,
                                summary.playlistsRestored,
                                summary.artistImagesRestored,
                                summary.lyricsRestored,
                                summary.listenEventsRestored
                            )
                        )
                        .setPositiveButton(R.string.common_ok, null)
                        .show()
                }
                is BackupManager.RestoreResult.NoBackupFound ->
                    toast(getString(R.string.music_settings_no_backup_found))
                is BackupManager.RestoreResult.Error ->
                    toast(getString(R.string.music_settings_restore_error, result.message))
                is BackupManager.RestoreResult.NotSignedIn ->
                    toast(getString(R.string.cloud_sync_not_signed_in))
            }
        }
    }

    // ==================== Inventaire ====================

    private fun loadInventory() {
        if (!signInManager.isSignedIn()) return
        scope.launch {
            setBusy(true, R.string.cloud_loading)
            val report = CloudInventory.load(this@CloudActivity)
            setBusy(false, null)
            if (report == null) {
                toast(getString(R.string.cloud_delete_failed))
                return@launch
            }
            renderInventory(report)
        }
    }

    private fun renderInventory(report: CloudInventory.Report) {
        usageTotal.text = getString(
            R.string.cloud_usage_total,
            CloudInventory.formatSize(this, report.totalBytes),
            report.fileCount
        )

        renderUsageBar(report)
        renderCategories(report)
        renderJournals(report)

        val empty = report.fileCount == 0
        emptyText.visibility = if (empty) View.VISIBLE else View.GONE
        usageBar.visibility = if (empty) View.GONE else View.VISIBLE
        btnDeleteAll.isEnabled = !empty
    }

    /**
     * La barre de proportion : un segment par domaine, pondéré par sa taille.
     *
     * On ne pose aucune largeur minimale. Un domaine minuscule doit paraître
     * minuscule — lui accorder quelques pixels « pour qu'il se voie » ferait
     * mentir la seule chose que la barre sait dire.
     */
    private fun renderUsageBar(report: CloudInventory.Report) {
        usageBar.removeAllViews()
        if (report.totalBytes <= 0L) return

        for (usage in report.categories) {
            if (usage.bytes <= 0L) continue
            val segment = View(this)
            segment.layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                usage.bytes.toFloat()
            )
            segment.setBackgroundColor(
                ContextCompat.getColor(this, usage.category.colorRes)
            )
            usageBar.addView(segment)
        }
    }

    private fun renderCategories(report: CloudInventory.Report) {
        categoryContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (usage in report.categories) {
            val row = inflater.inflate(R.layout.item_cloud_category, categoryContainer, false)
            val category = usage.category

            row.findViewById<View>(R.id.cloud_cat_dot).background
                ?.mutate()
                ?.setTint(ContextCompat.getColor(this, category.colorRes))

            row.findViewById<TextView>(R.id.cloud_cat_label).setText(category.labelRes)
            row.findViewById<TextView>(R.id.cloud_cat_desc).setText(category.descRes)
            row.findViewById<TextView>(R.id.cloud_cat_size).text = getString(
                R.string.cloud_usage_line,
                CloudInventory.formatSize(this, usage.bytes),
                usage.fileCount
            )

            val delete = row.findViewById<ImageButton>(R.id.cloud_cat_delete)
            if (category.deleteWarningRes == null) {
                delete.visibility = View.INVISIBLE
            } else {
                delete.visibility = View.VISIBLE
                delete.setOnClickListener { confirmDeleteCategory(usage) }
            }

            categoryContainer.addView(row)
        }
    }

    private fun renderJournals(report: CloudInventory.Report) {
        journalContainer.removeAllViews()
        journalsSection.visibility = if (report.journals.isEmpty()) View.GONE else View.VISIBLE
        val inflater = LayoutInflater.from(this)

        for (journal in report.journals) {
            val row = inflater.inflate(R.layout.item_cloud_journal, journalContainer, false)

            row.findViewById<TextView>(R.id.cloud_journal_name).text = if (journal.isThisDevice) {
                getString(R.string.cloud_journal_this_device)
            } else {
                // L'identifiant complet est un UUID : illisible et inutile ici.
                // Ses premiers caractères suffisent à distinguer deux appareils.
                getString(R.string.cloud_journal_other, journal.deviceId.take(8))
            }

            row.findViewById<TextView>(R.id.cloud_journal_meta).text = getString(
                R.string.cloud_journal_meta,
                CloudInventory.formatSize(this, journal.file.size),
                formatDate(journal.file.modifiedTime)
            )

            row.findViewById<ImageButton>(R.id.cloud_journal_delete).setOnClickListener {
                confirmDeleteJournal(journal)
            }

            journalContainer.addView(row)
        }
    }

    // ==================== Suppressions ====================

    private fun confirmDeleteCategory(usage: CloudInventory.CategoryUsage) {
        val category = usage.category
        val warningRes = category.deleteWarningRes ?: return
        val label = getString(category.labelRes)

        val body = getString(warningRes) + "\n\n" + getString(
            R.string.cloud_delete_size,
            CloudInventory.formatSize(this, usage.bytes),
            usage.fileCount
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.cloud_delete_title, label))
            .setMessage(body)
            .setNegativeButton(R.string.cloud_cancel, null)
            .setPositiveButton(R.string.cloud_delete_continue) { _, _ ->
                val run = { deleteFiles(usage.ids, touchesListens(category)) }
                if (category.requiresTypedConfirm) askTypedConfirmation(run) else run()
            }
            .show()
    }

    private fun confirmDeleteJournal(journal: CloudInventory.DeviceJournal) {
        val title: String
        val message: String

        if (journal.isThisDevice) {
            title = getString(R.string.cloud_journal_delete_self_title)
            message = getString(R.string.cloud_journal_delete_self_message)
        } else {
            title = getString(R.string.cloud_delete_title, getString(R.string.cloud_cat_listens))
            message = getString(R.string.cloud_cat_listens_warning) + "\n\n" + getString(
                R.string.cloud_delete_size,
                CloudInventory.formatSize(this, journal.file.size),
                1
            )
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.cloud_cancel, null)
            .setPositiveButton(R.string.cloud_delete) { _, _ ->
                deleteFiles(listOf(journal.file.id), touchesListens = true)
            }
            .show()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.cloud_delete_all)
            .setMessage(R.string.cloud_delete_all_desc)
            .setNegativeButton(R.string.cloud_cancel, null)
            .setPositiveButton(R.string.cloud_delete_continue) { _, _ ->
                askTypedConfirmation {
                    scope.launch {
                        setBusy(true, R.string.cloud_loading)
                        val result = CloudSyncManager.deleteAllCloudData()
                        setBusy(false, null)
                        if (result.success) {
                            toast(getString(R.string.cloud_deleted, result.deletedFilesCount))
                        } else {
                            toast(getString(R.string.cloud_delete_failed))
                        }
                        refreshAccountUi()
                        loadInventory()
                    }
                }
            }
            .show()
    }

    /**
     * Le second garde-fou : recopier le mot. Le bouton reste inactif tant que
     * ce n'est pas exact, de sorte qu'un appui distrait ne suffise jamais.
     */
    private fun askTypedConfirmation(onConfirmed: () -> Unit) {
        val word = getString(R.string.cloud_delete_confirm_word)
        val input = EditText(this).apply {
            hint = getString(R.string.cloud_delete_confirm_hint, word)
            setSingleLine()
        }
        val padding = (24 * resources.displayMetrics.density).toInt()
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.cloud_delete_confirm_title)
            .setMessage(R.string.cloud_delete_confirm_message)
            .setView(wrapper)
            .setNegativeButton(R.string.cloud_cancel, null)
            .setPositiveButton(R.string.cloud_delete) { _, _ -> onConfirmed() }
            .create()

        dialog.setOnShowListener {
            val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ok.isEnabled = false
            input.doAfterTextChanged { text ->
                ok.isEnabled = text?.toString()?.trim().equals(word, ignoreCase = true)
            }
        }
        dialog.show()
    }

    /**
     * @param touchesListens vrai si la suppression emporte des journaux d'écoutes.
     *   Dans ce cas il faut aussi oublier ce que cet appareil croit avoir publié,
     *   sinon son raccourci d'envoi le tiendrait pour à jour et ne republierait
     *   rien avant la prochaine écoute.
     */
    private fun deleteFiles(ids: List<String>, touchesListens: Boolean) {
        if (isBusy || ids.isEmpty()) return
        scope.launch {
            setBusy(true, R.string.cloud_loading)
            val deleted = CloudInventory.delete(this@CloudActivity, ids)
            if (touchesListens) CloudSyncManager.forgetUploadedEventsState()
            setBusy(false, null)

            if (deleted > 0) toast(getString(R.string.cloud_deleted, deleted))
            else toast(getString(R.string.cloud_delete_failed))

            loadInventory()
        }
    }

    private fun touchesListens(category: CloudInventory.CloudCategory): Boolean =
        category == CloudInventory.CloudCategory.LISTENS

    // ==================== Menus ====================

    private fun setBusy(busy: Boolean, labelRes: Int?) {
        isBusy = busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        btnSyncNow.isEnabled = !busy
        btnRefresh.isEnabled = !busy
        btnDeleteAll.isEnabled = !busy
        if (busy && labelRes != null) usageTotal.setText(labelRes)
    }

    private fun formatDate(epochMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(epochMs))

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, CloudActivity::class.java)
    }
}
