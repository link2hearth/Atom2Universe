package com.Atom2Universe.app.notes.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.NotesPreferences
import com.Atom2Universe.app.notes.data.GroupWithCount
import com.Atom2Universe.app.notes.data.Note
import com.Atom2Universe.app.notes.data.NoteWithTags
import com.Atom2Universe.app.notes.data.Tag
import com.Atom2Universe.app.notes.editor.MarkdownEditText
import com.Atom2Universe.app.notes.editor.MarkdownEdits
import com.Atom2Universe.app.notes.editor.MarkdownEdits.BlockStyle
import com.Atom2Universe.app.notes.editor.MarkdownEdits.Inline
import com.Atom2Universe.app.notes.editor.MarkdownSyntax
import com.Atom2Universe.app.notes.speech.SpeechToTextManager
import com.Atom2Universe.app.notes.speech.TextToSpeechManager
import com.Atom2Universe.app.notes.viewmodel.NotesViewModel
import com.Atom2Universe.app.pixelart.ui.LabeledSlider
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.bottomSheet
import com.Atom2Universe.app.pixelart.ui.chip
import com.Atom2Universe.app.pixelart.ui.divider
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.iconButton
import com.Atom2Universe.app.pixelart.ui.label
import com.Atom2Universe.app.pixelart.ui.primaryButton
import com.Atom2Universe.app.pixelart.ui.scrollRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * L'éditeur d'une note. Tout s'enregistre seul : peu après chaque frappe, et en quittant. Une note
 * restée vide disparaît en quittant l'éditeur. Épingler, favori, couleur, groupe et tags passent
 * par la même note en mémoire ([note]) et s'enregistrent aussitôt.
 */
class NoteEditorFragment : Fragment(R.layout.fragment_note_editor) {

    private val notesActivity get() = activity as NotesActivity
    private val vm: NotesViewModel get() = notesActivity.viewModel
    private lateinit var prefs: NotesPreferences

    private lateinit var root: View
    private lateinit var titleField: EditText
    private lateinit var editor: MarkdownEditText
    private lateinit var metaRow: LinearLayout
    private lateinit var info: TextView
    private lateinit var voicePreview: TextView
    private lateinit var undoBtn: ImageButton
    private lateinit var redoBtn: ImageButton
    private lateinit var pinBtn: ImageButton
    private lateinit var starBtn: ImageButton
    private lateinit var micBtn: ImageButton
    private val inlineButtons = HashMap<Inline, ImageButton>()
    private val blockButtons = HashMap<MarkdownSyntax.Block, ImageButton>()

    /** La note en mémoire ; son id vaut 0 tant qu'elle n'a jamais été enregistrée. */
    private var note = Note()
    private var tagIds: Set<Long> = emptySet()
    private var allTags: List<Tag> = emptyList()
    private var loaded = false
    /** Mise à la corbeille depuis l'éditeur : plus rien à enregistrer. */
    private var discarded = false

    private val stt by lazy { SpeechToTextManager(requireContext()) }
    private var tts: TextToSpeechManager? = null
    private var speaking = false
    private var autoSave: Job? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startDictation() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = NotesPreferences(requireContext())
        root = view.findViewById(R.id.nt_ed_root)
        titleField = view.findViewById(R.id.nt_ed_title)
        editor = view.findViewById(R.id.nt_ed_text)
        metaRow = view.findViewById(R.id.nt_ed_meta)
        info = view.findViewById(R.id.nt_ed_info)
        voicePreview = view.findViewById(R.id.nt_ed_voice)
        undoBtn = view.findViewById(R.id.nt_ed_undo)
        redoBtn = view.findViewById(R.id.nt_ed_redo)
        pinBtn = view.findViewById(R.id.nt_ed_pin)
        starBtn = view.findViewById(R.id.nt_ed_star)

        editor.palette = requireContext().notePalette()
        editor.textSize = prefs.fontSize.toFloat()
        editor.setFontFamily(prefs.fontFamily)
        editor.onStateChanged = ::refreshToolbar
        editor.onWikiLink = ::openWikiLink
        editor.onUrl = ::openUrl

        view.findViewById<View>(R.id.nt_ed_back).setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        undoBtn.setOnClickListener { editor.undo() }
        redoBtn.setOnClickListener { editor.redo() }
        pinBtn.setOnClickListener { updateMeta { it.copy(isPinned = !it.isPinned) } }
        starBtn.setOnClickListener { updateMeta { it.copy(isFavorite = !it.isFavorite) } }
        view.findViewById<View>(R.id.nt_ed_more).setOnClickListener { showMoreMenu() }
        buildToolbar(view.findViewById(R.id.nt_ed_toolbar))

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { if (loaded) scheduleSave() }
        }
        titleField.addTextChangedListener(watcher)
        editor.addTextChangedListener(watcher)
        titleField.setOnEditorActionListener { _, _, _ -> editor.requestFocus(); editor.setSelection(0); true }

        load()
    }

    private fun load() {
        val args = requireArguments()
        val id = args.getLong(ARG_NOTE_ID, 0L)
        viewLifecycleOwner.lifecycleScope.launch {
            allTags = vm.getAllTags()
            val existing = if (id > 0) vm.getNoteWithTagsById(id) else null
            if (existing != null) {
                note = existing.note
                tagIds = existing.tags.map { it.id }.toSet()
            } else {
                val now = System.currentTimeMillis()
                note = Note(
                    groupId = args.getLong(ARG_GROUP_ID, 0L).takeIf { it > 0 },
                    isFavorite = args.getBoolean(ARG_FAVORITE, false),
                    dateCreated = now, dateModified = now,
                )
                tagIds = setOfNotNull(args.getLong(ARG_TAG_ID, 0L).takeIf { it > 0 })
            }
            titleField.setText(note.title)
            editor.setMarkdown(note.content)
            loaded = true
            applyNoteLook()
            refreshMeta()
            refreshInfo()
            loadBacklinks()
            if (existing == null) {
                titleField.requestFocus()
                showKeyboard(titleField)
            }
        }
    }

    // ---- Enregistrement ------------------------------------------------------------------------

    private fun isBlank() = titleField.text.isBlank() && editor.markdown.isBlank()

    private fun scheduleSave() {
        autoSave?.cancel()
        autoSave = viewLifecycleOwner.lifecycleScope.launch {
            delay(SAVE_DELAY_MS)
            save()
            refreshInfo()
        }
    }

    /** Écrit la note telle qu'elle est à l'écran. Une note neuve et vide n'est pas créée. */
    private suspend fun save(force: Boolean = false) {
        if (!loaded || discarded) return
        val title = titleField.text.toString().trim()
        val content = editor.markdown
        if (note.id == 0L && !force && isBlank()) return
        val changed = title != note.title || content != note.content
        note = note.copy(
            title = title,
            content = content,
            contentPlainText = MarkdownSyntax.toPlainText(content),
            dateModified = if (changed || note.id == 0L) System.currentTimeMillis() else note.dateModified,
        )
        // Une frappe annule la sauvegarde en cours : sans NonCancellable, une insertion déjà faite en base
        // pouvait perdre son identifiant en route, et la sauvegarde suivante recréait la note en double.
        withContext(NonCancellable) {
            if (note.id == 0L) {
                note = note.copy(id = vm.insertNote(note))
                requireArguments().putLong(ARG_NOTE_ID, note.id)
            } else {
                vm.updateNote(note)
            }
            vm.setTagsForNote(note.id, tagIds.toList())
        }
        if (changed) loadBacklinks()
    }

    private fun saveSoon() = viewLifecycleOwner.lifecycleScope.launch { save(force = true); refreshInfo() }

    /** Pin, favori, couleur, groupe : la note change et s'enregistre tout de suite. */
    private fun updateMeta(change: (Note) -> Note) {
        note = change(note)
        applyNoteLook()
        refreshMeta()
        saveSoon()
    }

    override fun onPause() {
        super.onPause()
        autoSave?.cancel()
        stt.stopListening()
        if (!loaded || discarded) return
        // En quittant pour de bon, une note restée vide est effacée au lieu d'être gardée.
        val leaving = isRemoving || requireActivity().isFinishing
        runBlocking {
            if (leaving && isBlank() && tagIds.isEmpty()) {
                if (note.id != 0L) vm.deleteForever(note.id)
            } else save()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stt.destroy()
        tts?.destroy()
        tts = null
    }

    // ---- Apparence -----------------------------------------------------------------------------

    private fun applyNoteLook() {
        root.setBackgroundColor(requireContext().noteBackground(note.colorHex))
        pinBtn.setImageResource(if (note.isPinned) R.drawable.ic_nt_pin_filled else R.drawable.ic_nt_pin)
        pinBtn.contentDescription = getString(if (note.isPinned) R.string.notes_unpin else R.string.notes_pin)
        starBtn.setImageResource(if (note.isFavorite) R.drawable.ic_nt_star_filled else R.drawable.ic_nt_star)
        starBtn.contentDescription = getString(if (note.isFavorite) R.string.notes_unfavorite else R.string.notes_favorite)
    }

    /** La rangée sous le titre : le groupe, les tags, et une puce pour en ajouter. */
    private fun refreshMeta() {
        val ctx = requireContext()
        metaRow.removeAllViews()
        val groupName = vm.allGroupsWithCount.value.firstOrNull { it.group.id == note.groupId }?.group?.name
        metaRow.addView(ctx.chip(groupName ?: getString(R.string.notes_no_group), R.drawable.ic_px_folder_open) { pickGroup() }
            .apply { isSelected = groupName != null })
        for (t in allTags.filter { it.id in tagIds }) {
            metaRow.addView(ctx.chip(getString(R.string.notes_tag_label, t.name)) { pickTags() })
        }
        metaRow.addView(ctx.chip(if (tagIds.isEmpty()) getString(R.string.notes_tags) else null, R.drawable.ic_nt_tag) { pickTags() }
            .apply { contentDescription = getString(R.string.notes_tags) })
    }

    private fun refreshInfo() {
        if (!isAdded) return
        val words = MarkdownSyntax.wordCount(editor.markdown)
        val parts = arrayListOf<CharSequence>(resources.getQuantityString(R.plurals.notes_words, words, words))
        val (done, total) = MarkdownSyntax.checklistProgress(editor.markdown)
        if (total > 0) parts += getString(R.string.notes_checklist_progress, done, total)
        if (note.id != 0L) parts += getString(R.string.notes_modified,
            DateUtils.getRelativeTimeSpanString(note.dateModified, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS))
        info.text = parts.joinToString(getString(R.string.notes_info_separator))
    }

    // ---- Barre de mise en forme ----------------------------------------------------------------

    private fun buildToolbar(bar: LinearLayout) {
        val ctx = requireContext()
        fun btn(icon: Int, desc: Int, action: () -> Unit) = ctx.iconButton(icon, desc, size = 42, pad = 10, onClick = action).also { bar.addView(it) }
        fun inline(icon: Int, desc: Int, style: Inline) {
            inlineButtons[style] = btn(icon, desc) { edit(MarkdownEdits.toggleInline(editor.markdown, selStart(), selEnd(), style)) }
        }
        fun block(icon: Int, desc: Int, style: BlockStyle, kind: MarkdownSyntax.Block) {
            blockButtons[kind] = btn(icon, desc) { edit(MarkdownEdits.toggleBlock(editor.markdown, selStart(), selEnd(), style)) }
        }
        blockButtons[MarkdownSyntax.Block.HEADING] = btn(R.drawable.ic_nt_heading, R.string.notes_format_heading) {
            edit(MarkdownEdits.cycleHeading(editor.markdown, selStart()))
        }
        inline(R.drawable.ic_nt_bold, R.string.notes_format_bold, Inline.BOLD)
        inline(R.drawable.ic_nt_italic, R.string.notes_format_italic, Inline.ITALIC)
        inline(R.drawable.ic_nt_underline, R.string.notes_format_underline, Inline.UNDERLINE)
        inline(R.drawable.ic_nt_strike, R.string.notes_format_strike, Inline.STRIKE)
        bar.addView(ctx.divider())
        block(R.drawable.ic_nt_checklist, R.string.notes_format_checklist, BlockStyle.CHECK, MarkdownSyntax.Block.CHECK)
        block(R.drawable.ic_nt_list, R.string.notes_format_bullets, BlockStyle.BULLET, MarkdownSyntax.Block.BULLET)
        block(R.drawable.ic_nt_list_ordered, R.string.notes_format_numbers, BlockStyle.NUMBER, MarkdownSyntax.Block.NUMBER)
        block(R.drawable.ic_nt_quote, R.string.notes_format_quote, BlockStyle.QUOTE, MarkdownSyntax.Block.QUOTE)
        bar.addView(ctx.divider())
        inline(R.drawable.ic_nt_code, R.string.notes_format_code, Inline.CODE)
        btn(R.drawable.ic_px_link, R.string.notes_insert_link) { showLinkSheet() }
        bar.addView(ctx.divider())
        micBtn = btn(R.drawable.ic_nt_mic, R.string.notes_dictate) { toggleDictation() }
    }

    /** La sélection, ramenée dans le texte : sans curseur posé, elle vaut -1. */
    private fun selStart() = editor.selectionStart.coerceIn(0, editor.length())
    private fun selEnd() = editor.selectionEnd.coerceIn(0, editor.length())

    private fun edit(e: MarkdownEdits.Edit) {
        if (!editor.hasFocus()) editor.requestFocus()
        editor.applyEdit(e)
    }

    /** Les boutons allumés suivent le curseur ; annuler / rétablir suivent l'historique. */
    private fun refreshToolbar() {
        if (!::undoBtn.isInitialized) return
        undoBtn.isEnabled = editor.history.canUndo
        undoBtn.alpha = if (editor.history.canUndo) 1f else 0.35f
        redoBtn.isEnabled = editor.history.canRedo
        redoBtn.alpha = if (editor.history.canRedo) 1f else 0.35f
        val s = editor.selectionStart.coerceAtLeast(0)
        val e = editor.selectionEnd.coerceAtLeast(0)
        val text = editor.text ?: return
        val active = MarkdownEdits.activeInline(text, s, e)
        for ((style, b) in inlineButtons) b.isSelected = style in active
        val ls = MarkdownEdits.lineStart(text, s)
        val block = MarkdownSyntax.parseLine(text.substring(ls, MarkdownEdits.lineEnd(text, s))).block
        for ((kind, b) in blockButtons) b.isSelected = kind == block
    }

    // ---- Liens ---------------------------------------------------------------------------------

    /** Un lien web (texte + adresse), ou un lien vers une autre note choisie dans la liste. */
    private fun showLinkSheet() {
        val ctx = requireContext()
        val s = selStart()
        val e = selEnd()
        viewLifecycleOwner.lifecycleScope.launch {
            val titles = vm.getAllTitles().filter { it.id != note.id }
            ctx.bottomSheet(getString(R.string.notes_insert_link)) { box, dialog ->
                fun field(hint: Int, type: Int) = EditText(ctx).apply {
                    this.hint = getString(hint)
                    inputType = type
                    setSingleLine()
                    setTextColor(ContextCompat.getColor(ctx, R.color.audio_text_primary))
                }.also { box.addView(it) }
                val label = field(R.string.notes_link_text_hint, InputType.TYPE_CLASS_TEXT)
                if (e > s) label.setText(editor.markdown.substring(minOf(s, e), maxOf(s, e)))
                val url = field(R.string.notes_link_url_hint, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
                box.addView(ctx.primaryButton(getString(R.string.notes_insert)) {
                    val u = url.text.toString().trim()
                    dialog.dismiss()
                    if (u.isNotEmpty()) edit(MarkdownEdits.insertLink(editor.markdown, s, e, label.text.toString().trim(), u))
                })
                if (titles.isNotEmpty()) {
                    box.addView(ctx.label(getString(R.string.notes_link_to_note), 12f).apply { setPadding(ctx.dp(2), ctx.dp(20), 0, ctx.dp(6)) })
                    for (t in titles) box.addView(ctx.label(t.title, 15f, true).apply {
                        setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_nt_note, 0, 0, 0)
                        compoundDrawablePadding = ctx.dp(14)
                        compoundDrawableTintList = ContextCompat.getColorStateList(ctx, R.color.px_icon_tint)
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        minHeight = ctx.dp(44)
                        background = ContextCompat.getDrawable(ctx, R.drawable.bg_px_tool)
                        setPadding(ctx.dp(6), 0, ctx.dp(6), 0)
                        setOnClickListener { dialog.dismiss(); edit(MarkdownEdits.insertWikiLink(s, e, t.title)) }
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            }.show()
        }
    }

    private fun openWikiLink(title: String) {
        if (title.equals(note.title, ignoreCase = true)) return
        viewLifecycleOwner.lifecycleScope.launch {
            autoSave?.cancel()
            save()
            notesActivity.openNote(vm.getOrCreateNoteByTitle(title))
        }
    }

    private fun openUrl(url: String) {
        val uri = Uri.parse(if (url.contains("://") || url.startsWith("mailto:")) url else "https://$url")
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: ActivityNotFoundException) {}
    }

    private suspend fun loadBacklinks() {
        val view = view ?: return
        val box = view.findViewById<View>(R.id.nt_ed_backlinks)
        val list = view.findViewById<ViewGroup>(R.id.nt_ed_backlinks_list)
        val links = if (note.id == 0L) emptyList() else vm.getBacklinks(note.title, note.id)
        list.removeAllViews()
        box.visibility = if (links.isEmpty()) View.GONE else View.VISIBLE
        if (links.isEmpty()) return
        view.findViewById<TextView>(R.id.nt_ed_backlinks_title).text = getString(R.string.notes_backlinks_header, links.size)
        for (l in links) list.addView(requireContext().chip(l.note.title.ifBlank { getString(R.string.notes_untitled) }, R.drawable.ic_nt_note) {
            viewLifecycleOwner.lifecycleScope.launch {
                autoSave?.cancel()
                save()
                notesActivity.openNote(l.note.id)
            }
        })
    }

    // ---- Groupe, tags, couleur -----------------------------------------------------------------

    private fun pickGroup() {
        requireContext().showGroupPicker(vm.allGroupsWithCount.value, note.groupId, onPick = { id -> updateMeta { it.copy(groupId = id) } }) { name ->
            viewLifecycleOwner.lifecycleScope.launch {
                val id = vm.createGroup(name)
                // Le flux des groupes doit connaître le nouveau avant qu'on affiche son nom.
                vm.allGroupsWithCount.first { list -> list.any { it.group.id == id } }
                updateMeta { it.copy(groupId = id) }
            }
        }
    }

    private fun pickTags() {
        viewLifecycleOwner.lifecycleScope.launch {
            allTags = vm.getAllTags()
            requireContext().showTagPicker(
                allTags, tagIds,
                createTag = { vm.getOrCreateTag(it) },
                launch = { block -> viewLifecycleOwner.lifecycleScope.launch { block() } },
            ) { chosen ->
                viewLifecycleOwner.lifecycleScope.launch {
                    allTags = vm.getAllTags()
                    tagIds = chosen.filter { id -> allTags.any { it.id == id } }.toSet()
                    refreshMeta()
                    save(force = true)
                }
            }
        }
    }

    // ---- Menu ----------------------------------------------------------------------------------

    private fun showMoreMenu() {
        val ctx = requireContext()
        ctx.actionSheet(null, listOf(
            SheetItem(R.drawable.ic_px_palette, getString(R.string.notes_color)) {
                ctx.showNoteColorSheet(note.colorHex) { hex -> updateMeta { it.copy(colorHex = hex) } }
            },
            SheetItem(R.drawable.ic_px_folder_open, getString(R.string.notes_move_to)) { pickGroup() },
            SheetItem(R.drawable.ic_nt_tag, getString(R.string.notes_tags)) { pickTags() },
            SheetItem(R.drawable.ic_nt_text_size, getString(R.string.notes_text_settings)) { showTextSettings() },
            SheetItem(R.drawable.ic_nt_speaker, getString(if (speaking) R.string.notes_tts_stop else R.string.notes_tts_speak)) { toggleSpeech() },
            SheetItem(R.drawable.ic_px_share, getString(R.string.notes_share)) {
                viewLifecycleOwner.lifecycleScope.launch { save(); ctx.shareNote(NoteWithTags(note, emptyList(), null)) }
            },
            SheetItem(R.drawable.ic_px_copy, getString(R.string.notes_duplicate)) {
                viewLifecycleOwner.lifecycleScope.launch {
                    save(force = true)
                    vm.duplicate(note.id, if (note.title.isBlank()) "" else getString(R.string.notes_copy_of, note.title))?.let { notesActivity.openNote(it) }
                }
            },
            SheetItem(R.drawable.ic_px_delete, getString(R.string.notes_move_to_trash), destructive = true) { trash() },
        )).show()
    }

    private fun trash() {
        viewLifecycleOwner.lifecycleScope.launch {
            autoSave?.cancel()
            val blank = isBlank()
            if (!blank) save()
            discarded = true
            val id = note.id
            if (id != 0L) {
                if (blank) vm.deleteForever(id) else vm.moveToTrash(id)
            }
            hideKeyboard()
            notesActivity.closeEditor()
            if (id != 0L && !blank) notesActivity.snack(R.string.notes_trashed, R.string.notes_undo) { vm.restore(id) }
        }
    }

    private fun showTextSettings() {
        val ctx = requireContext()
        ctx.bottomSheet(getString(R.string.notes_text_settings)) { box, _ ->
            box.addView(LabeledSlider(ctx, getString(R.string.notes_font_size), NotesPreferences.MIN_FONT, NotesPreferences.MAX_FONT, prefs.fontSize,
                { getString(R.string.notes_font_size_value, it) }) { size ->
                prefs.fontSize = size
                editor.textSize = size.toFloat()
            })
            box.addView(ctx.label(getString(R.string.notes_font_family), 12f).apply { setPadding(ctx.dp(2), ctx.dp(16), 0, ctx.dp(6)) })
            val chips = ArrayList<TextView>()
            for ((font, name) in NotesPreferences.FONTS) {
                chips += ctx.chip(getString(name)) { v ->
                    prefs.fontFamily = font
                    editor.setFontFamily(font)
                    chips.forEach { it.isSelected = it === v }
                }.apply { isSelected = font == prefs.fontFamily }
            }
            box.addView(ctx.scrollRow(*chips.toTypedArray()))
        }.show()
    }

    // ---- Voix ----------------------------------------------------------------------------------

    private fun toggleDictation() {
        if (stt.isActive()) {
            stt.stopListening()
            micBtn.isSelected = false
            voicePreview.visibility = View.GONE
            return
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startDictation()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startDictation() {
        micBtn.isSelected = true
        stt.startContinuousListening(object : SpeechToTextManager.ContinuousSttListener {
            override fun onSegmentResult(text: String) {
                activity?.runOnUiThread {
                    if (view == null) return@runOnUiThread
                    if (!editor.hasFocus()) editor.requestFocus()
                    val pos = editor.selectionStart.coerceAtLeast(0)
                    val before = if (pos > 0) editor.markdown[pos - 1] else '\n'
                    editor.insertAtCursor((if (before.isWhitespace()) "" else " ") + text)
                    voicePreview.text = text
                    voicePreview.visibility = View.VISIBLE
                }
            }

            override fun onContinuousStopped() {
                activity?.runOnUiThread {
                    if (view == null) return@runOnUiThread
                    micBtn.isSelected = false
                    voicePreview.visibility = View.GONE
                }
            }
        })
    }

    private fun toggleSpeech() {
        val engine = tts ?: TextToSpeechManager(requireContext()).also { tts = it }
        if (speaking) {
            engine.stop()
            speaking = false
            return
        }
        val text = listOf(titleField.text.toString(), MarkdownSyntax.toPlainText(editor.markdown).replace(Regex("[☐☑•]"), ""))
            .filter { it.isNotBlank() }.joinToString("\n")
        if (text.isBlank()) return
        val speak = {
            engine.setSpeechRate(prefs.ttsSpeechRate)
            speaking = true
            engine.speak(text, onDone = { activity?.runOnUiThread { speaking = false } })
        }
        if (engine.isReady()) speak() else engine.initialize { speak() }
    }

    // ---- Clavier -------------------------------------------------------------------------------

    private fun showKeyboard(v: View) {
        v.post { (requireContext().getSystemService(InputMethodManager::class.java))?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun hideKeyboard() {
        val v = view ?: return
        requireContext().getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    companion object {
        private const val ARG_NOTE_ID = "note_id"
        private const val ARG_GROUP_ID = "group_id"
        private const val ARG_TAG_ID = "tag_id"
        private const val ARG_FAVORITE = "favorite"
        private const val SAVE_DELAY_MS = 1500L

        fun newInstance(noteId: Long?, groupId: Long? = null, tagId: Long? = null, favorite: Boolean = false) = NoteEditorFragment().apply {
            arguments = Bundle().apply {
                noteId?.let { putLong(ARG_NOTE_ID, it) }
                groupId?.let { putLong(ARG_GROUP_ID, it) }
                tagId?.let { putLong(ARG_TAG_ID, it) }
                putBoolean(ARG_FAVORITE, favorite)
            }
        }
    }
}
