package com.Atom2Universe.app.notes.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.NotesPreferences
import com.Atom2Universe.app.notes.data.Note
import com.Atom2Universe.app.notes.data.NoteWithTags
import com.Atom2Universe.app.notes.data.Tag
import com.Atom2Universe.app.notes.editor.MarkdownParser
import com.Atom2Universe.app.notes.editor.RichTextEditor
import com.Atom2Universe.app.notes.speech.SpeechToTextManager
import com.Atom2Universe.app.notes.speech.TextToSpeechManager
import kotlinx.coroutines.*

class NoteEditorFragment : Fragment() {

    private var noteId: Long? = null
    private var groupId: Long? = null
    private var currentNote: Note? = null
    private var currentTagIds = mutableListOf<Long>()

    private lateinit var notesActivity: NotesActivity
    private lateinit var prefs: NotesPreferences
    private lateinit var richEditor: RichTextEditor
    private lateinit var titleInput: EditText
    private lateinit var tagsChipGroup: ChipGroup
    private lateinit var sourceEditor: EditText
    private lateinit var voicePreview: TextView
    private var showSource = false

    private val sttManager by lazy { SpeechToTextManager(requireContext()) }
    private val ttsManager by lazy { TextToSpeechManager(requireContext()) }

    private var autoSaveJob: Job? = null
    private val autoSaveDelay = 2000L

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startSTT() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        noteId = arguments?.getLong(ARG_NOTE_ID, -1L)?.takeIf { it >= 0 }
        groupId = arguments?.getLong(ARG_GROUP_ID, -1L)?.takeIf { it >= 0 }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_note_editor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        notesActivity = activity as NotesActivity
        prefs = NotesPreferences(requireContext())

        titleInput = view.findViewById(R.id.note_title_input)
        tagsChipGroup = view.findViewById(R.id.note_tags_chip_group)
        richEditor = view.findViewById(R.id.note_rich_editor)
        sourceEditor = view.findViewById(R.id.note_source_editor)
        voicePreview = view.findViewById(R.id.note_voice_preview)

        richEditor.setFontSizeSp(prefs.fontSize)
        richEditor.setFontFamily(prefs.fontFamily)

        setupToolbar(view)
        setupSTTTTS(view)

        if (noteId != null) {
            lifecycleScope.launch {
                val noteWithTags = notesActivity.viewModel.getNoteWithTagsById(noteId!!)
                noteWithTags?.let { loadNote(it) }
            }
        } else {
            refreshTagChips(emptyList())
        }

        // Auto-save on text change
        richEditor.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { scheduleAutoSave() }
        })
        titleInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { scheduleAutoSave() }
        })
    }

    private fun loadNote(noteWithTags: NoteWithTags) {
        currentNote = noteWithTags.note
        currentTagIds = noteWithTags.tags.map { it.id }.toMutableList()
        titleInput.setText(noteWithTags.note.title)
        richEditor.setMarkdownContent(noteWithTags.note.content)
        sourceEditor.setText(noteWithTags.note.content)
        refreshTagChips(noteWithTags.tags)
    }

    private fun refreshTagChips(tags: List<Tag>) {
        tagsChipGroup.removeAllViews()
        tags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag.name
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    currentTagIds.remove(tag.id)
                    tagsChipGroup.removeView(this)
                    scheduleAutoSave()
                }
            }
            tagsChipGroup.addView(chip)
        }
        // Add tag button
        val addChip = Chip(requireContext()).apply {
            text = getString(R.string.notes_add_tag)
            chipIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_add_24)
            setOnClickListener { showAddTagDialog() }
        }
        tagsChipGroup.addView(addChip)
    }

    private fun showAddTagDialog() {
        lifecycleScope.launch {
            buildTagPickerDialog()
        }
    }

    private suspend fun buildTagPickerDialog() {
        val allTags = notesActivity.viewModel.repositoryRef.getAllTagsList()
        val names = allTags.map { it.name }.toTypedArray()
        val checked = BooleanArray(allTags.size) { currentTagIds.contains(allTags[it].id) }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notes_select_tags)
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                val tagId = allTags[which].id
                if (isChecked) { if (!currentTagIds.contains(tagId)) currentTagIds.add(tagId) }
                else currentTagIds.remove(tagId)
            }
            .setPositiveButton(R.string.notes_ok) { _, _ ->
                lifecycleScope.launch {
                    val selectedTags = allTags.filter { currentTagIds.contains(it.id) }
                    refreshTagChips(selectedTags)
                    scheduleAutoSave()
                }
            }
            .setNeutralButton(R.string.notes_create_tag) { _, _ ->
                showCreateTagInlineDialog()
            }
            .setNegativeButton(R.string.notes_cancel, null)
            .show()
    }

    private fun showCreateTagInlineDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.notes_tag_name_hint)
            setPadding(48, 24, 48, 8)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notes_create_tag)
            .setView(input)
            .setPositiveButton(R.string.notes_create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    lifecycleScope.launch {
                        val newId = notesActivity.viewModel.createTag(name)
                        if (!currentTagIds.contains(newId)) currentTagIds.add(newId)
                        val selectedTags = notesActivity.viewModel.repositoryRef.getAllTagsList()
                            .filter { currentTagIds.contains(it.id) }
                        refreshTagChips(selectedTags)
                        scheduleAutoSave()
                    }
                }
            }
            .setNegativeButton(R.string.notes_cancel) { _, _ ->
                lifecycleScope.launch { buildTagPickerDialog() }
            }
            .show()
    }

    private fun setupToolbar(view: View) {
        view.findViewById<ImageButton>(R.id.btn_toggle_source)?.setOnClickListener { toggleSourceMode() }
        view.findViewById<ImageButton>(R.id.btn_bold)?.setOnClickListener { richEditor.toggleBold() }
        view.findViewById<ImageButton>(R.id.btn_italic)?.setOnClickListener { richEditor.toggleItalic() }
        view.findViewById<ImageButton>(R.id.btn_underline)?.setOnClickListener { richEditor.toggleUnderline() }
        view.findViewById<View>(R.id.btn_h1)?.setOnClickListener { richEditor.setHeaderLevel(1) }
        view.findViewById<View>(R.id.btn_h2)?.setOnClickListener { richEditor.setHeaderLevel(2) }
        view.findViewById<View>(R.id.btn_h3)?.setOnClickListener { richEditor.setHeaderLevel(3) }
        view.findViewById<ImageButton>(R.id.btn_link)?.setOnClickListener { showInsertLinkDialog() }
        view.findViewById<ImageButton>(R.id.btn_font_size)?.setOnClickListener { showFontSizeDialog() }
        view.findViewById<ImageButton>(R.id.btn_font_family)?.setOnClickListener { showFontFamilyDialog() }
    }

    private fun setupSTTTTS(view: View) {
        view.findViewById<ImageButton>(R.id.btn_stt)?.setOnClickListener {
            if (sttManager.isActive()) sttManager.stopListening()
            else requestAudioPermissionAndStartSTT()
        }
        view.findViewById<ImageButton>(R.id.btn_tts)?.setOnClickListener {
            if (!ttsManager.isReady()) {
                ttsManager.initialize {
                    speakCurrentNote()
                }
            } else {
                speakCurrentNote()
            }
        }
    }

    private fun requestAudioPermissionAndStartSTT() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) startSTT()
        else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startSTT() {
        sttManager.startContinuousListening(object : SpeechToTextManager.ContinuousSttListener {
            override fun onSegmentResult(text: String) {
                activity?.runOnUiThread {
                    richEditor.insertTextAtCursor("$text ")
                    voicePreview.text = text
                    voicePreview.visibility = View.VISIBLE
                }
            }
            override fun onContinuousStopped() {
                activity?.runOnUiThread { voicePreview.visibility = View.GONE }
            }
        })
    }

    private fun speakCurrentNote() {
        val text = richEditor.getPlainTextContent()
        ttsManager.setSpeechRate(NotesPreferences(requireContext()).ttsSpeechRate)
        ttsManager.speak(text, onDone = { activity?.runOnUiThread { /* stop indicator */ } })
    }

    private fun toggleSourceMode() {
        showSource = !showSource
        richEditor.visibility = if (showSource) View.GONE else View.VISIBLE
        sourceEditor.visibility = if (showSource) View.VISIBLE else View.GONE
        if (showSource) sourceEditor.setText(richEditor.getMarkdownContent())
        else richEditor.setMarkdownContent(sourceEditor.text.toString())
    }

    private fun showInsertLinkDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_insert_link, null)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notes_insert_link)
            .setView(dialogView)
            .setPositiveButton(R.string.notes_insert) { _, _ ->
                val text = dialogView.findViewById<EditText>(R.id.link_text_input).text.toString()
                val url = dialogView.findViewById<EditText>(R.id.link_url_input).text.toString()
                if (url.isNotBlank()) richEditor.insertLink(text.ifBlank { url }, url)
            }
            .setNegativeButton(R.string.notes_cancel, null)
            .show()
    }

    private fun showFontSizeDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(android.R.layout.activity_list_item, null)
        val seekBar = SeekBar(requireContext()).apply {
            max = 34; progress = prefs.fontSize - 14
        }
        val sizeLabel = TextView(requireContext()).apply { text = "${prefs.fontSize}sp" }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                sizeLabel.text = "${progress + 14}sp"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16)
            addView(sizeLabel)
            addView(seekBar)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notes_font_size)
            .setView(container)
            .setPositiveButton(R.string.notes_ok) { _, _ ->
                val size = seekBar.progress + 14
                prefs.fontSize = size
                richEditor.setFontSizeSp(size)
            }
            .setNegativeButton(R.string.notes_cancel, null)
            .show()
    }

    private fun showFontFamilyDialog() {
        val fonts = NotesPreferences.AVAILABLE_FONTS.toTypedArray()
        val currentIndex = fonts.indexOf(prefs.fontFamily).coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notes_font_family)
            .setSingleChoiceItems(fonts, currentIndex) { dialog, which ->
                prefs.fontFamily = fonts[which]
                richEditor.setFontFamily(fonts[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.notes_cancel, null)
            .show()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = lifecycleScope.launch {
            delay(autoSaveDelay)
            saveNote()
        }
    }

    private suspend fun saveNote() {
        val title = titleInput.text.toString().trim()
        val content = if (showSource) sourceEditor.text.toString() else richEditor.getMarkdownContent()
        val plainText = MarkdownParser.toPlainText(content)
        val existing = currentNote
        val note = if (existing == null) {
            Note(
                title = title, content = content, contentPlainText = plainText,
                groupId = groupId, dateCreated = System.currentTimeMillis(),
                dateModified = System.currentTimeMillis()
            )
        } else {
            existing.copy(title = title, content = content, contentPlainText = plainText,
                dateModified = System.currentTimeMillis())
        }
        val savedId = if (existing == null) {
            notesActivity.viewModel.saveNote(note).also { newId ->
                currentNote = note.copy(id = newId)
                noteId = newId
            }
        } else {
            notesActivity.viewModel.updateNote(note)
            existing.id
        }
        notesActivity.viewModel.setTagsForNote(savedId, currentTagIds)
    }

    override fun onPause() {
        super.onPause()
        autoSaveJob?.cancel()
        runBlocking { saveNote() }
        sttManager.stopListening()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sttManager.destroy()
        ttsManager.destroy()
    }

    companion object {
        private const val ARG_NOTE_ID = "note_id"
        private const val ARG_GROUP_ID = "group_id"

        fun newInstance(noteId: Long? = null, groupId: Long? = null) = NoteEditorFragment().apply {
            arguments = Bundle().apply {
                noteId?.let { putLong(ARG_NOTE_ID, it) }
                groupId?.let { putLong(ARG_GROUP_ID, it) }
            }
        }
    }
}
