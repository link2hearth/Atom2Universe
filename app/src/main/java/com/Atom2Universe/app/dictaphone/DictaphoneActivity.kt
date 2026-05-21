package com.Atom2Universe.app.dictaphone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.audioeditor.MicRecordingService
import com.Atom2Universe.app.util.enableImmersiveMode
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.TimeUnit
import android.os.Bundle

class DictaphoneActivity : ThemedActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var viewModel: DictaphoneViewModel
    private lateinit var adapter: DictaphoneAdapter

    private lateinit var btnRecord: ImageButton
    private lateinit var btnStop: MaterialButton
    private lateinit var btnQuality: TextView
    private lateinit var tvTimer: TextView
    private lateinit var pbAmplitude: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var rvRecordings: RecyclerView

    private val micStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MicRecordingService.ACTION_STOP_FROM_NOTIFICATION) {
                viewModel.stopRecording()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_dictaphone)

        viewModel = ViewModelProvider(this)[DictaphoneViewModel::class.java]

        bindViews()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        val filter = IntentFilter(MicRecordingService.ACTION_STOP_FROM_NOTIFICATION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(micStopReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(micStopReceiver, filter)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(micStopReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun bindViews() {
        btnRecord = findViewById(R.id.dictaphone_btn_record)
        btnStop = findViewById(R.id.dictaphone_btn_stop)
        btnQuality = findViewById(R.id.dictaphone_btn_quality)
        tvTimer = findViewById(R.id.dictaphone_timer)
        pbAmplitude = findViewById(R.id.dictaphone_amplitude)
        tvStatus = findViewById(R.id.dictaphone_status)
        tvEmpty = findViewById(R.id.dictaphone_empty)
        rvRecordings = findViewById(R.id.dictaphone_list)
        findViewById<ImageButton>(R.id.dictaphone_btn_back).setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = DictaphoneAdapter(
            onPlayPause = { viewModel.togglePlayback(it) },
            onRename = { showRenameDialog(it) },
            onDelete = { showDeleteDialog(it) }
        )
        rvRecordings.layoutManager = LinearLayoutManager(this)
        rvRecordings.adapter = adapter
    }

    private fun setupListeners() {
        btnRecord.setOnClickListener { viewModel.startRecording() }
        btnStop.setOnClickListener { viewModel.stopRecording() }
        btnQuality.setOnClickListener { viewModel.toggleQuality() }
    }

    private fun observeViewModel() {
        viewModel.isRecording.observe(this) { recording -> updateRecordingUi(recording) }
        viewModel.isSaving.observe(this) { saving -> updateSavingUi(saving) }

        viewModel.durationMs.observe(this) { ms ->
            tvTimer.text = formatDuration(ms)
        }
        viewModel.amplitude.observe(this) { amp ->
            pbAmplitude.progress = (amp * 100).toInt().coerceIn(0, 100)
        }
        viewModel.quality.observe(this) { q ->
            btnQuality.text = q.name
        }
        viewModel.recordings.observe(this) { list ->
            adapter.submitList(list)
            tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.playingUri.observe(this) { uri ->
            adapter.updatePlayback(uri, viewModel.isPlaying.value == true)
        }
        viewModel.isPlaying.observe(this) { playing ->
            adapter.updatePlayback(viewModel.playingUri.value, playing)
        }
        viewModel.errorMessage.observe(this) { msg ->
            msg?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateRecordingUi(recording: Boolean) {
        btnRecord.setImageResource(if (recording) R.drawable.ic_record else R.drawable.ic_mic)
        btnRecord.isEnabled = !recording
        btnStop.visibility = if (recording) View.VISIBLE else View.GONE
        tvTimer.visibility = if (recording) View.VISIBLE else View.INVISIBLE
        pbAmplitude.visibility = if (recording) View.VISIBLE else View.INVISIBLE
        tvStatus.visibility = View.GONE
        if (!recording) {
            tvTimer.text = "00:00"
            pbAmplitude.progress = 0
        }
    }

    private fun updateSavingUi(saving: Boolean) {
        btnRecord.isEnabled = !saving
        if (saving) {
            tvStatus.setText(R.string.dictaphone_status_saving)
            tvStatus.visibility = View.VISIBLE
            tvTimer.visibility = View.INVISIBLE
            pbAmplitude.visibility = View.INVISIBLE
            btnStop.visibility = View.GONE
        } else {
            tvStatus.visibility = View.GONE
        }
    }

    private fun showRenameDialog(recording: DictaphoneRecording) {
        val input = TextInputEditText(this).apply {
            setText(recording.displayName.substringBeforeLast('.'))
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dictaphone_rename_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString() ?: return@setPositiveButton
                viewModel.renameRecording(recording, name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        input.requestFocus()
    }

    private fun showDeleteDialog(recording: DictaphoneRecording) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dictaphone_delete_title)
            .setMessage(getString(R.string.dictaphone_delete_message, recording.displayName))
            .setPositiveButton(R.string.dictaphone_delete_confirm) { _, _ ->
                viewModel.deleteRecording(recording)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun formatDuration(ms: Long): String {
        val min = TimeUnit.MILLISECONDS.toMinutes(ms)
        val sec = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return "%02d:%02d".format(min, sec)
    }
}
