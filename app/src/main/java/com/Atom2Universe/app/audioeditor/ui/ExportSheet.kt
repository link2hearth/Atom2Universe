package com.Atom2Universe.app.audioeditor.ui

import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.audioeditor.io.ExportFormat
import com.Atom2Universe.app.audioeditor.io.ExportOptions
import com.Atom2Universe.app.audioeditor.io.ExportTags
import com.Atom2Universe.app.pixelart.ui.LabeledSlider
import com.Atom2Universe.app.pixelart.ui.bottomSheet
import com.Atom2Universe.app.pixelart.ui.chip
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.label
import com.Atom2Universe.app.pixelart.ui.primaryButton
import com.Atom2Universe.app.pixelart.ui.scrollRow

/** Ce que l'utilisateur a demandé : les réglages, un fichier par piste ou non, et le nom de base proposé. */
class ExportRequest(val options: ExportOptions, val perTrack: Boolean, val baseName: String)

/** Le type MIME à donner au sélecteur de fichiers pour ce format. */
val ExportFormat.mime: String
    get() = when (this) {
        ExportFormat.WAV16, ExportFormat.WAV24, ExportFormat.WAV32F -> "audio/x-wav"
        ExportFormat.MP3 -> "audio/mpeg"
        ExportFormat.AAC -> "audio/mp4"
        ExportFormat.FLAC -> "audio/flac"
        ExportFormat.OGG -> "audio/ogg"
    }

/**
 * La feuille d'export : format, débit, plage, canaux, fréquence, un fichier par piste, métadonnées. Elle ne
 * fait que composer une [ExportRequest] ; choisir la destination et lancer le rendu revient à l'écran.
 */
class ExportUi(private val activity: AppCompatActivity, private val vm: EditorViewModel, private val onRequest: (ExportRequest) -> Unit) {

    /** Les débits proposés, en kb/s : ce sont les crans du curseur. */
    private val BITRATES = listOf(64, 96, 128, 160, 192, 224, 256, 320)

    private val formats = listOf(
        ExportFormat.WAV16 to R.string.ae_fmt_wav16,
        ExportFormat.WAV24 to R.string.ae_fmt_wav24,
        ExportFormat.WAV32F to R.string.ae_fmt_wav32f,
        ExportFormat.MP3 to R.string.ae_fmt_mp3,
        ExportFormat.AAC to R.string.ae_fmt_aac,
        ExportFormat.FLAC to R.string.ae_fmt_flac,
        ExportFormat.OGG to R.string.ae_fmt_ogg,
    )

    /** Une ligne de puces exclusives sous un intitulé ; [onChange] reçoit la valeur choisie. */
    private fun <T> choiceRow(root: LinearLayout, title: Int, options: List<Pair<T, String>>, initial: T, onChange: (T) -> Unit) {
        root.addView(activity.label(activity.getString(title), 12f).apply { setPadding(activity.dp(2), activity.dp(14), 0, activity.dp(6)) })
        val chips = ArrayList<TextView>()
        options.forEachIndexed { i, (value, text) ->
            chips.add(activity.chip(text) {
                onChange(value)
                chips.forEachIndexed { k, c -> c.isSelected = k == i }
            })
        }
        chips.forEachIndexed { k, c -> c.isSelected = options[k].first == initial }
        root.addView(activity.scrollRow(*chips.toTypedArray()))
    }

    private fun tagField(hint: Int, initial: String, number: Boolean = false) = EditText(activity).apply {
        setText(initial)
        setSingleLine()
        this.hint = activity.getString(hint)
        inputType = if (number) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
    }

    fun show() {
        var format = ExportFormat.WAV16
        var bitrate = 192
        var vbr = false
        var selectionOnly = vm.ui.hasSelection
        var mono = false
        var rate = 0
        var perTrack = false

        activity.bottomSheet(activity.getString(R.string.ae_export_title)) { root, dlg ->
            // Les réglages propres aux formats compressés (débit) et aux formats qui portent des métadonnées (tout sauf WAV).
            lateinit var bitrateRow: LabeledSlider
            lateinit var vbrBox: LinearLayout
            lateinit var tagsBox: LinearLayout
            fun refreshFormat() {
                bitrateRow.visibility = if (format.lossy) android.view.View.VISIBLE else android.view.View.GONE
                vbrBox.visibility = if (format.hasVbr) android.view.View.VISIBLE else android.view.View.GONE
                tagsBox.visibility = if (format.extension != "wav") android.view.View.VISIBLE else android.view.View.GONE
                bitrateRow.value = bitrateRow.value // relit l'intitulé : « ≈ » seulement en débit variable
            }

            choiceRow(root, R.string.ae_export_format, formats.map { (f, t) -> f to activity.getString(t) }, format) { format = it; refreshFormat() }

            // Le curseur parcourt une liste de crans (les débits usuels) : on le lit comme un rang, pas comme une valeur.
            bitrateRow = LabeledSlider(
                activity, activity.getString(R.string.ae_export_bitrate), 0, BITRATES.lastIndex, BITRATES.indexOf(bitrate),
                { activity.getString(if (vbr && format.hasVbr) R.string.ae_unit_kbps_avg else R.string.ae_unit_kbps, BITRATES[it.coerceIn(0, BITRATES.lastIndex)]) },
            ) { i -> bitrate = BITRATES[i.coerceIn(0, BITRATES.lastIndex)] }
            root.addView(bitrateRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = activity.dp(6) })

            vbrBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            choiceRow(
                vbrBox, R.string.ae_export_bitrate_mode,
                listOf(false to activity.getString(R.string.ae_export_cbr), true to activity.getString(R.string.ae_export_vbr)),
                vbr,
            ) { vbr = it; refreshFormat() }
            root.addView(vbrBox)

            if (vm.ui.hasSelection) {
                choiceRow(
                    root, R.string.ae_export_range,
                    listOf(false to activity.getString(R.string.ae_export_whole), true to activity.getString(R.string.ae_export_selection)),
                    selectionOnly,
                ) { selectionOnly = it }
            }
            choiceRow(root, R.string.ae_export_channels, listOf(false to activity.getString(R.string.ae_export_stereo), true to activity.getString(R.string.ae_export_mono)), mono) { mono = it }
            choiceRow(
                root, R.string.ae_sample_rate,
                listOf(0 to activity.getString(R.string.ae_export_rate_project), 44100 to activity.getString(R.string.ae_rate_44100), 48000 to activity.getString(R.string.ae_rate_48000)),
                rate,
            ) { rate = it }
            if (vm.project.tracks.count { it.clips.isNotEmpty() } > 1) {
                choiceRow(
                    root, R.string.ae_export_files,
                    listOf(false to activity.getString(R.string.ae_export_one_file), true to activity.getString(R.string.ae_export_per_track)),
                    perTrack,
                ) { perTrack = it }
            }

            tagsBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            val title = tagField(R.string.ae_tag_title, vm.name)
            val artist = tagField(R.string.ae_tag_artist, "")
            val album = tagField(R.string.ae_tag_album, "")
            val year = tagField(R.string.ae_tag_year, "", number = true)
            for (f in listOf(title, artist, album, year)) tagsBox.addView(f, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(tagsBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = activity.dp(10) })
            refreshFormat()

            root.addView(activity.primaryButton(activity.getString(R.string.ae_export_go)) {
                val ui = vm.ui
                val options = ExportOptions(
                    format = format,
                    bitrateKbps = bitrate,
                    // Tout le projet commence au premier clip : le vide laissé avant par un rognage n'est pas exporté.
                    from = if (selectionOnly && ui.hasSelection) ui.selStart else vm.project.start,
                    to = if (selectionOnly && ui.hasSelection) ui.selEnd else -1L,
                    tags = if (format.extension == "wav") ExportTags() else ExportTags(
                        title.text.toString(), artist.text.toString(), album.text.toString(), year.text.toString(),
                    ),
                    sampleRate = rate,
                    mono = mono,
                    vbr = vbr && format.hasVbr,
                )
                dlg.dismiss()
                onRequest(ExportRequest(options, perTrack, vm.name))
            })
        }.show()
    }
}
