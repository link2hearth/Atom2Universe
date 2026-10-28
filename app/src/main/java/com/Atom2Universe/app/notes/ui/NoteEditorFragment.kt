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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import com.Atom2Universe.app.notes.sync.NotesSyncStore
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * L'éditeur d'une note. Tout s'enregistre seul : peu après chaque frappe, et en quittant. Une note
 * restée vide disparaît en quittant l'éditeur. Épingler, favori, couleur, groupe et tags passent
 * par la même note en mémoire ([note]) et s'enregistrent aussitôt.
 */
class NoteEditorFragment : Fragment(R.layout.fragment_note_editor) {

    private val notesActivity get() = activity as NotesActivity
    /** Gardé ici : la sauvegarde de sortie peut finir après le détachement de l'éditeur. */
    private lateinit var vm: NotesViewModel
    private lateinit var prefs: NotesPreferences

    /** Les vues de l'écran. Relâchées en onDestroyView : l'éditeur peut rester dans la pile sans elles. */
    private class Views(view: View) {
        val root: View = view.findViewById(R.id.nt_ed_root)
        val titleField: EditText = view.findViewById(R.id.nt_ed_title)
        val editor: MarkdownEditText = view.findViewById(R.id.nt_ed_text)
        val metaRow: LinearLayout = view.findViewById(R.id.nt_ed_meta)
        val info: TextView = view.findViewById(R.id.nt_ed_info)
        val voicePreview: TextView = view.findViewById(R.id.nt_ed_voice)
        val undoBtn: ImageButton = view.findViewById(R.id.nt_ed_undo)
        val redoBtn: ImageButton = view.findViewById(R.id.nt_ed_redo)
        val pinBtn: ImageButton = view.findViewById(R.id.nt_ed_pin)
        val starBtn: ImageButton = view.findViewById(R.id.nt_ed_star)
        val backlinks: View = view.findViewById(R.id.nt_ed_backlinks)
        val backlinksTitle: TextView = view.findViewById(R.id.nt_ed_backlinks_title)
        val backlinksList: ViewGroup = view.findViewById(R.id.nt_ed_backlinks_list)
        val inlineButtons = HashMap<Inline, ImageButton>()
        val blockButtons = HashMap<MarkdownSyntax.Block, ImageButton>()
        var micBtn: ImageButton? = null
    }

    /** Null hors de l'écran : tout ce qui finit après une attente doit le vérifier. */
    private var ui: Views? = null

    /** Le titre et le texte à l'écran, lus avant toute attente : l'écran peut disparaître pendant la sauvegarde. */
    private class Shot(val title: String, val content: String) {
        fun isBlank() = title.isBlank() && content.isBlank()
    }

    /** La note en mémoire ; son id vaut 0 tant qu'elle n'a jamais été enregistrée. */
    private var note = Note()
    private var tagIds: Set<Long> = emptySet()
    private var allTags: List<Tag> = emptyList()
    /** Les groupes, suivis par l'éditeur lui-même : il peut s'ouvrir sans la bibliothèque (écran recréé). */
    private var groups: List<GroupWithCount> = emptyList()
    private var loaded = false
    /** Mise à la corbeille depuis l'éditeur : plus rien à enregistrer. */
    private var discarded = false
    /** La note et ses tags tels qu'ils sont en base : rien n'est réécrit tant qu'ils n'ont pas changé. */
    private var savedNote: Note? = null
    private var savedTagIds: Set<Long> = emptySet()
    /** Texte brut, mots et cases du dernier texte vu : calculés une fois, hors du fil de l'écran, et réutilisés. */
    private var stats: MarkdownSyntax.Stats? = null

    private val stt by lazy { SpeechToTextManager(requireContext()) }
    private var tts: TextToSpeechManager? = null
    private var speaking = false
    private var autoSave: Job? = null
    /** Une seule écriture à la fois : deux insertions d'une note neuve la créeraient en double. */
    private val saveLock = Mutex()

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startDictation() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = notesActivity.viewModel
        prefs = NotesPreferences(requireContext())
        val u = Views(view)
        ui = u

        u.editor.palette = requireContext().notePalette()
        u.editor.textSize = prefs.fontSize.toFloat()
        u.editor.setFontFamily(prefs.fontFamily)
        u.editor.onStateChanged = ::refreshToolbar
        u.editor.onWikiLink = ::openWikiLink
        u.editor.onUrl = ::openUrl

        view.findViewById<View>(R.id.nt_ed_back).setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        u.undoBtn.setOnClickListener { u.editor.undo() }
        u.redoBtn.setOnClickListener { u.editor.redo() }
        u.pinBtn.setOnClickListener { updateMeta { it.copy(isPinned = !it.isPinned) } }
        u.starBtn.setOnClickListener { updateMeta { it.copy(isFavorite = !it.isFavorite) } }
        view.findViewById<View>(R.id.nt_ed_more).setOnClickListener { showMoreMenu() }
        buildToolbar(u, view.findViewById(R.id.nt_ed_toolbar))

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { if (loaded) scheduleSave() }
        }
        u.titleField.addTextChangedListener(watcher)
        u.editor.addTextChangedListener(watcher)
        u.titleField.setOnEditorActionListener { _, _, _ -> u.editor.requestFocus(); u.editor.setSelection(0); true }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.allGroupsWithCount.collect { g ->
                    groups = g
                    if (loaded) refreshMeta()
                }
            }
        }

        load()
    }

    private fun load() {
        val args = requireArguments()
        val id = args.getLong(ARG_NOTE_ID, 0L)
        // Avant de la lire : une synchro qui passerait entre la lecture et ce signal l'écrirait sous nos pieds.
        if (id > 0) NotesSyncStore.editingNoteId = id
        viewLifecycleOwner.lifecycleScope.launch {
            // Écran recréé (thème, rotation) : la sauvegarde de sortie de l'ancien écran doit finir avant qu'on relise la note.
            exitSaves.coroutineContext.job.children.toList().joinAll()
            allTags = vm.getAllTags()
            val existing = if (id > 0) vm.getNoteWithTagsById(id) else null
            if (existing != null) {
                note = existing.note
                tagIds = existing.tags.map { it.id }.toSet()
                savedNote = note
                savedTagIds = tagIds
            } else {
                val now = System.currentTimeMillis()
                note = Note(
                    groupId = args.getLong(ARG_GROUP_ID, 0L).takeIf { it > 0 },
                    isFavorite = args.getBoolean(ARG_FAVORITE, false),
                    dateCreated = now, dateModified = now,
                )
                tagIds = setOfNotNull(args.getLong(ARG_TAG_ID, 0L).takeIf { it > 0 })
            }
            statsOf(note.content)
            val u = ui ?: return@launch
            u.titleField.setText(note.title)
            u.editor.setMarkdown(note.content)
            loaded = true
            applyNoteLook()
            refreshMeta()
            refreshInfo()
            loadBacklinks()
            if (existing == null) {
                u.titleField.requestFocus()
                showKeyboard(u.titleField)
            }
        }
    }

    // ---- Enregistrement ------------------------------------------------------------------------

    /** Ce qu'il y a à enregistrer ; null si la note n'est pas chargée, a été jetée, ou si l'écran n'existe plus. */
    private fun shot(): Shot? {
        val u = ui ?: return null
        if (!loaded || discarded) return null
        return Shot(u.titleField.text.toString().trim(), u.editor.markdown)
    }

    private suspend fun statsOf(content: String): MarkdownSyntax.Stats {
        stats?.let { if (it.markdown == content) return it }
        return withContext(Dispatchers.Default) { MarkdownSyntax.stats(content) }.also { stats = it }
    }

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
        val s = shot() ?: return
        saveLock.withLock { writeNote(s, force) }
    }

    private suspend fun writeNote(s: Shot, force: Boolean) {
        if (discarded) return
        if (note.id == 0L && !force && s.isBlank()) return
        // Texte brut, mots, cases : une seule lecture, hors du fil de l'écran ; rien à refaire si le texte n'a pas bougé.
        val st = statsOf(s.content)
        if (discarded) return
        val titleChanged = s.title != note.title
        val changed = titleChanged || s.content != note.content
        note = note.copy(
            title = s.title,
            content = s.content,
            contentPlainText = st.plainText,
            dateModified = if (changed || note.id == 0L) System.currentTimeMillis() else note.dateModified,
        )
        // Rien de neuf : pas d'écriture, donc pas de synchro programmée pour rien.
        if (note.id != 0L && note == savedNote && tagIds == savedTagIds) return
        // Une frappe annule la sauvegarde en cours : sans NonCancellable, une insertion déjà faite en base
        // pouvait perdre son identifiant en route, et la sauvegarde suivante recréait la note en double.
        withContext(NonCancellable) {
            if (note.id == 0L) {
                note = note.copy(id = vm.insertNote(note))
                NotesSyncStore.editingNoteId = note.id
                requireArguments().putLong(ARG_NOTE_ID, note.id)
            } else {
                vm.updateNote(note)
            }
            if (tagIds != savedTagIds) vm.setTagsForNote(note.id, tagIds.toList())
            savedNote = note
            savedTagIds = tagIds
        }
        // Les liens entrants ne dépendent que du titre.
        if (titleChanged) loadBacklinks()
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
        val leaving = isRemoving || requireActivity().isFinishing
        val heldId = requireArguments().getLong(ARG_NOTE_ID, 0L)
        // Lu maintenant, tant que l'écran existe : l'écriture, elle, peut finir bien après lui.
        val s = shot()
        if (s == null) {
            if (leaving) releaseNote(heldId)
            return
        }
        // En quittant pour de bon, une note restée vide est effacée au lieu d'être gardée.
        val dropBlank = leaving && s.isBlank() && tagIds.isEmpty()
        // Hors du fil de l'écran, dans un scope qui survit à l'éditeur et à l'activité :
        // l'écriture va au bout même si l'écran est détruit entre-temps.
        exitSaves.launch {
            withContext(NonCancellable) {
                saveLock.withLock {
                    if (dropBlank) {
                        if (note.id != 0L) vm.deleteForever(note.id)
                    } else writeNote(s, force = false)
                }
            }
            // La synchro peut reprendre la note, maintenant que sa dernière version est en base.
            if (leaving) releaseNote(note.id.takeIf { it != 0L } ?: heldId)
        }
    }

    /** Rend la note à la synchro, sauf si un autre éditeur l'a déjà prise pour la sienne. */
    private fun releaseNote(id: Long) {
        if (id != 0L && NotesSyncStore.editingNoteId == id) NotesSyncStore.editingNoteId = 0L
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stt.destroy()
        tts?.destroy()
        tts = null
        speaking = false
        ui?.editor?.let {
            it.onStateChanged = null
            it.onWikiLink = null
            it.onUrl = null
        }
        ui = null
        // Au retour dans la pile, la note est relue en base : rien ne s'enregistre avant.
        loaded = false
    }

    // ---- Apparence -----------------------------------------------------------------------------

    private fun applyNoteLook() {
        val u = ui ?: return
        u.root.setBackgroundColor(requireContext().noteBackground(note.colorHex))
        u.pinBtn.setImageResource(if (note.isPinned) R.drawable.ic_nt_pin_filled else R.drawable.ic_nt_pin)
        u.pinBtn.contentDescription = getString(if (note.isPinned) R.string.notes_unpin else R.string.notes_pin)
        u.starBtn.setImageResource(if (note.isFavorite) R.drawable.ic_nt_star_filled else R.drawable.ic_nt_star)
        u.starBtn.contentDescription = getString(if (note.isFavorite) R.string.notes_unfavorite else R.string.notes_favorite)
    }

    /** La rangée sous le titre : le groupe, les tags, et une puce pour en ajouter. */
    private fun refreshMeta() {
        val u = ui ?: return
        val ctx = requireContext()
        u.metaRow.removeAllViews()
        val groupName = groups.firstOrNull { it.group.id == note.groupId }?.group?.name
        u.metaRow.addView(ctx.chip(groupName ?: getString(R.string.notes_no_group), R.drawable.ic_px_folder_open) { pickGroup() }
            .apply { isSelected = groupName != null })
        for (t in allTags.filter { it.id in tagIds }) {
            u.metaRow.addView(ctx.chip(getString(R.string.notes_tag_label, t.name)) { pickTags() })
        }
        u.metaRow.addView(ctx.chip(if (tagIds.isEmpty()) getString(R.string.notes_tags) else null, R.drawable.ic_nt_tag) { pickTags() }
            .apply { contentDescription = getString(R.string.notes_tags) })
    }

    private fun refreshInfo() {
        val u = ui ?: return
        if (!isAdded) return
        val st = stats ?: return
        val parts = arrayListOf<CharSequence>(resources.getQuantityString(R.plurals.notes_words, st.words, st.words))
        if (st.total > 0) parts += getString(R.string.notes_checklist_progress, st.done, st.total)
        if (note.id != 0L) parts += getString(R.string.notes_modified,
            DateUtils.getRelativeTimeSpanString(note.dateModified, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS))
        u.info.text = parts.joinToString(getString(R.string.notes_info_separator))
    }

    // ---- Barre de mise en forme ----------------------------------------------------------------

    private fun buildToolbar(u: Views, bar: LinearLayout) {
        val ctx = requireContext()
        fun btn(icon: Int, desc: Int, action: () -> Unit) = ctx.iconButton(icon, desc, size = 42, pad = 10, onClick = action).also { bar.addView(it) }
        fun inline(icon: Int, desc: Int, style: Inline) {
            u.inlineButtons[style] = btn(icon, desc) { edit { ed -> MarkdownEdits.toggleInline(ed.markdown, selStart(ed), selEnd(ed), style) } }
        }
        fun block(icon: Int, desc: Int, style: BlockStyle, kind: MarkdownSyntax.Block) {
            u.blockButtons[kind] = btn(icon, desc) { edit { ed -> MarkdownEdits.toggleBlock(ed.markdown, selStart(ed), selEnd(ed), style) } }
        }
        u.blockButtons[MarkdownSyntax.Block.HEADING] = btn(R.drawable.ic_nt_heading, R.string.notes_format_heading) {
            edit { ed -> MarkdownEdits.cycleHeading(ed.markdown, selStart(ed)) }
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
        u.micBtn = btn(R.drawable.ic_nt_mic, R.string.notes_dictate) { toggleDictation() }
    }

    /** La sélection, ramenée dans le texte : sans curseur posé, elle vaut -1. */
    private fun selStart(ed: MarkdownEditText) = ed.selectionStart.coerceIn(0, ed.length())
    private fun selEnd(ed: MarkdownEditText) = ed.selectionEnd.coerceIn(0, ed.length())

    /** Applique un geste calculé sur le texte actuel ; sans écran, rien. */
    private fun edit(make: (MarkdownEditText) -> MarkdownEdits.Edit) {
        val ed = ui?.editor ?: return
        if (!ed.hasFocus()) ed.requestFocus()
        ed.applyEdit(make(ed))
    }

    /** Les boutons allumés suivent le curseur ; annuler / rétablir suivent l'historique. Une fois par frappe. */
    private fun refreshToolbar() {
        val u = ui ?: return
        val ed = u.editor
        u.undoBtn.isEnabled = ed.history.canUndo
        u.undoBtn.alpha = if (ed.history.canUndo) 1f else 0.35f
        u.redoBtn.isEnabled = ed.history.canRedo
        u.redoBtn.alpha = if (ed.history.canRedo) 1f else 0.35f
        val s = ed.selectionStart.coerceAtLeast(0)
        val e = ed.selectionEnd.coerceAtLeast(0)
        val text = ed.text ?: return
        // La ligne du curseur, déjà lue par la mise en forme : elle sert aux deux rangées de boutons.
        val (ls, line) = ed.lineAt(s)
        val le = MarkdownEdits.lineEnd(text, s)
        val active = if (maxOf(s, e) > le) emptySet() else MarkdownEdits.activeInline(line, minOf(s, e) - ls, maxOf(s, e) - ls)
        for ((style, b) in u.inlineButtons) b.isSelected = style in active
        for ((kind, b) in u.blockButtons) b.isSelected = kind == line.block
    }

    // ---- Liens ---------------------------------------------------------------------------------

    /** Un lien web (texte + adresse), ou un lien vers une autre note choisie dans la liste. */
    private fun showLinkSheet() {
        val ctx = requireContext()
        val ed = ui?.editor ?: return
        val s = selStart(ed)
        val e = selEnd(ed)
        viewLifecycleOwner.lifecycleScope.launch {
            val titles = vm.getAllTitles().filter { it.id != note.id }
            val markdown = ui?.editor?.markdown ?: return@launch
            ctx.bottomSheet(getString(R.string.notes_insert_link)) { box, dialog ->
                fun field(hint: Int, type: Int) = EditText(ctx).apply {
                    this.hint = getString(hint)
                    inputType = type
                    setSingleLine()
                    setTextColor(ContextCompat.getColor(ctx, R.color.audio_text_primary))
                }.also { box.addView(it) }
                val label = field(R.string.notes_link_text_hint, InputType.TYPE_CLASS_TEXT)
                if (e > s) label.setText(markdown.substring(minOf(s, e), maxOf(s, e)))
                val url = field(R.string.notes_link_url_hint, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
                box.addView(ctx.primaryButton(getString(R.string.notes_insert)) {
                    val u = url.text.toString().trim()
                    dialog.dismiss()
                    if (u.isNotEmpty()) edit { ed2 -> MarkdownEdits.insertLink(ed2.markdown, s, e, label.text.toString().trim(), u) }
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
                        setOnClickListener { dialog.dismiss(); edit { MarkdownEdits.insertWikiLink(s, e, t.title) } }
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

    /** Les notes qui citent celle-ci : lues au chargement, puis seulement quand le titre change. */
    private suspend fun loadBacklinks() {
        val before = ui ?: return
        val links = if (note.id == 0L) emptyList() else vm.getBacklinks(note.title, note.id)
        // Une sauvegarde de sortie peut arriver ici après la fermeture de l'éditeur.
        val u = ui
        if (!isAdded || u == null || u !== before) return
        u.backlinksList.removeAllViews()
        u.backlinks.visibility = if (links.isEmpty()) View.GONE else View.VISIBLE
        if (links.isEmpty()) return
        u.backlinksTitle.text = getString(R.string.notes_backlinks_header, links.size)
        for (l in links) u.backlinksList.addView(requireContext().chip(l.note.title.ifBlank { getString(R.string.notes_untitled) }, R.drawable.ic_nt_note) {
            viewLifecycleOwner.lifecycleScope.launch {
                autoSave?.cancel()
                save()
                notesActivity.openNote(l.note.id)
            }
        })
    }

    // ---- Groupe, tags, couleur -----------------------------------------------------------------

    private fun pickGroup() {
        requireContext().showGroupPicker(groups, note.groupId, onPick = { id -> updateMeta { it.copy(groupId = id) } }) { name ->
            if (ui == null) return@showGroupPicker
            viewLifecycleOwner.lifecycleScope.launch {
                val id = vm.createGroup(name)
                // Le flux des groupes doit connaître le nouveau avant qu'on affiche son nom.
                groups = vm.allGroupsWithCount.first { list -> list.any { it.group.id == id } }
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
                launch = { block -> if (ui != null) viewLifecycleOwner.lifecycleScope.launch { block() } },
            ) { chosen ->
                // La feuille peut se fermer après l'écran (activité recréée) : plus d'écran, plus rien à faire.
                if (ui == null) return@showTagPicker
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
            val s = shot() ?: return@launch
            val blank = s.isBlank()
            if (!blank) saveLock.withLock { writeNote(s, force = false) }
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
                ui?.editor?.textSize = size.toFloat()
            })
            box.addView(ctx.label(getString(R.string.notes_font_family), 12f).apply { setPadding(ctx.dp(2), ctx.dp(16), 0, ctx.dp(6)) })
            val chips = ArrayList<TextView>()
            for ((font, name) in NotesPreferences.FONTS) {
                chips += ctx.chip(getString(name)) { v ->
                    prefs.fontFamily = font
                    ui?.editor?.setFontFamily(font)
                    chips.forEach { it.isSelected = it === v }
                }.apply { isSelected = font == prefs.fontFamily }
            }
            box.addView(ctx.scrollRow(*chips.toTypedArray()))
        }.show()
    }

    // ---- Voix ----------------------------------------------------------------------------------

    private fun toggleDictation() {
        val u = ui ?: return
        if (stt.isActive()) {
            stt.stopListening()
            u.micBtn?.isSelected = false
            u.voicePreview.visibility = View.GONE
            return
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startDictation()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startDictation() {
        val u = ui ?: return
        u.micBtn?.isSelected = true
        stt.startContinuousListening(object : SpeechToTextManager.ContinuousSttListener {
            override fun onSegmentResult(text: String) {
                activity?.runOnUiThread {
                    val v = ui ?: return@runOnUiThread
                    val ed = v.editor
                    if (!ed.hasFocus()) ed.requestFocus()
                    val pos = ed.selectionStart.coerceAtLeast(0)
                    val before = if (pos > 0) ed.markdown[pos - 1] else '\n'
                    ed.insertAtCursor((if (before.isWhitespace()) "" else " ") + text)
                    v.voicePreview.text = text
                    v.voicePreview.visibility = View.VISIBLE
                }
            }

            override fun onContinuousStopped() {
                activity?.runOnUiThread {
                    val v = ui ?: return@runOnUiThread
                    v.micBtn?.isSelected = false
                    v.voicePreview.visibility = View.GONE
                }
            }
        })
    }

    private fun toggleSpeech() {
        val u = ui ?: return
        val engine = tts ?: TextToSpeechManager(requireContext()).also { tts = it }
        if (speaking) {
            engine.stop()
            speaking = false
            return
        }
        val text = listOf(u.titleField.text.toString(), MarkdownSyntax.toPlainText(u.editor.markdown).replace(SPEECH_MARKS, ""))
            .filter { it.isNotBlank() }.joinToString("\n")
        if (text.isBlank()) return
        val speak = {
            engine.setSpeechRate(prefs.ttsSpeechRate)
            speaking = true
            // Fin de lecture ou erreur : le menu repasse sur « Lire ».
            engine.speak(text, onDone = { activity?.runOnUiThread { speaking = false } })
        }
        if (engine.isReady()) speak() else engine.initialize { speak() }
    }

    // ---- Clavier -------------------------------------------------------------------------------

    private fun showKeyboard(v: View) {
        v.post { v.context.getSystemService(InputMethodManager::class.java)?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT) }
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
        /** Les cases et les puces du texte brut : la voix ne les lit pas. */
        private val SPEECH_MARKS = Regex("[☐☑•]")
        /** Sauvegardes lancées en onPause ; jamais annulé. */
        private val exitSaves = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
