package com.Atom2Universe.app.games.toyboxracers.editor

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

internal class ToyboxWorldEditorActivity : ThemedActivity(), ToyboxWorldEditorView.Listener {
    private lateinit var editorView: ToyboxWorldEditorView
    private lateinit var store: ToyboxWorldStore
    private lateinit var title: TextView
    private lateinit var details: TextView
    private lateinit var kindButton: Button
    private lateinit var solidButton: Button
    private lateinit var selectionBubble: LinearLayout
    private lateinit var selectionTitle: TextView
    private lateinit var selectionDetails: TextView
    private var currentKind = ToyboxVolumeKind.WALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        store = ToyboxWorldStore(this)

        val root = FrameLayout(this).apply { setBackgroundColor(0xFF263042.toInt()) }
        editorView = ToyboxWorldEditorView(this).apply {
            listener = this@ToyboxWorldEditorActivity
            setWorld(store.load())
        }
        root.addView(editorView, FrameLayout.LayoutParams(-1, -1))
        addTopBar(root)
        addSelectionBubble(root)
        addToolBar(root)
        setContentView(root)
        refreshLabels()
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
    }

    override fun onSelectionChanged(volume: ToyboxVolume?) = refreshLabels()

    override fun onWorldChanged(world: ToyboxWorld) {
        // Sauvegarde douce : chaque geste produit un fichier recuperable.
        store.save(world)
        refreshLabels()
    }

    private fun addTopBar(root: FrameLayout) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(0xD83B4055.toInt(), 16f)
        }
        title = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        details = TextView(this).apply {
            setTextColor(0xFFFFE7A8.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
        }
        panel.addView(title)
        panel.addView(details)
        root.addView(panel, FrameLayout.LayoutParams(dp(360), -2).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = dp(12)
            topMargin = dp(10)
        })

        val close = button("RETOUR", 0xAA4B617A.toInt()).apply { setOnClickListener { finish() } }
        root.addView(close, FrameLayout.LayoutParams(dp(92), dp(42)).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(12)
            rightMargin = dp(12)
        })

        val export = button("EXPORT", 0xAA735D91.toInt()).apply { setOnClickListener { exportWorld() } }
        root.addView(export, FrameLayout.LayoutParams(dp(92), dp(42)).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(60)
            rightMargin = dp(12)
        })
    }

    private fun addToolBar(root: FrameLayout) {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            background = rounded(0xB83B4055.toInt(), 18f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroll.addView(row)
        fun add(label: String, color: Int = 0xAA4B617A.toInt(), action: () -> Unit) {
            row.addView(button(label, color).apply { setOnClickListener { action() } },
                LinearLayout.LayoutParams(dp(86), dp(44)).apply { rightMargin = dp(8) })
        }

        kindButton = button("", 0xAA4B8F6E.toInt()).apply {
            setOnClickListener {
                val values = ToyboxVolumeKind.entries
                currentKind = values[(currentKind.ordinal + 1) % values.size]
                refreshLabels()
            }
        }
        row.addView(kindButton, LinearLayout.LayoutParams(dp(96), dp(44)).apply { rightMargin = dp(8) })
        add("Ajouter", 0xAA4B8F6E.toInt()) { editorView.addVolume(currentKind) }
        add("Checkpoint", 0xAA9A7C3D.toInt()) { editorView.addCheckpoint() }
        add("Vue 3D") { editorView.toggleViewMode(); refreshLabels() }
        add("Zoom -") { editorView.zoomBy(0.85f) }
        add("Zoom +") { editorView.zoomBy(1.18f) }
        solidButton = button("", 0xAA735D91.toInt()).apply {
            setOnClickListener { editorView.updateSelected { it.toggleSolid() } }
        }
        row.addView(solidButton, LinearLayout.LayoutParams(dp(86), dp(44)).apply { rightMargin = dp(8) })
        add("Etage -") { editorView.floorIndex -= 1; refreshLabels() }
        add("Etage +") { editorView.floorIndex += 1; refreshLabels() }
        add("Sauver", 0xAA4B8F6E.toInt()) {
            val file = store.save(editorView.world)
            Toast.makeText(this, "Sauve: ${file.name}", Toast.LENGTH_SHORT).show()
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, dp(62)).apply {
            gravity = Gravity.BOTTOM
            leftMargin = dp(12)
            rightMargin = dp(12)
            bottomMargin = dp(12)
        })
    }

    private fun refreshLabels() {
        if (!::title.isInitialized) return
        val selected = editorView.selected
        title.text = "Createur de monde - etage ${editorView.floorIndex}"
        details.text = if (selected == null) {
            "${editorView.world.volumes.size} volumes  |  ${editorView.world.checkpoints.size} checkpoints  |  touche un objet"
        } else {
            "${selected.kind.label} x=${selected.x.toInt()} z=${selected.z.toInt()}  " +
                "L=${selected.width.toInt()} P=${selected.depth.toInt()} H=${selected.height.toInt()}  " +
                if (selected.solid) "solide" else "decor"
        }
        selectionBubble.visibility = if (selected == null) View.GONE else View.VISIBLE
        if (selected != null) {
            selectionTitle.text = selected.kind.label.uppercase()
            selectionDetails.text =
                "Position  X ${selected.x.toInt()}  Z ${selected.z.toInt()}  Y ${selected.y.toInt()}\n" +
                    "Taille  largeur ${selected.width.toInt()}  profondeur ${selected.depth.toInt()}  hauteur ${selected.height.toInt()}\n" +
                    if (selected.solid) "Collision: solide" else "Collision: decor seulement"
        }
        kindButton.text = currentKind.label.uppercase()
        solidButton.text = if (selected?.solid == true) "SOLIDE" else "DECOR"
        solidButton.isEnabled = selected != null
        solidButton.alpha = if (selected != null) 0.82f else 0.35f
    }

    private fun addSelectionBubble(root: FrameLayout) {
        selectionBubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(0xEA30364A.toInt(), 18f)
            visibility = View.GONE
        }
        selectionTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        selectionDetails = TextView(this).apply {
            setTextColor(0xFFFFE7A8.toInt())
            textSize = 13f
            typeface = Typeface.MONOSPACE
        }
        selectionBubble.addView(selectionTitle)
        selectionBubble.addView(selectionDetails, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        fun row(vararg entries: Pair<String, () -> Unit>) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for ((label, action) in entries) {
                line.addView(button(label, 0xAA4B617A.toInt()).apply { setOnClickListener { action() } },
                    LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(6) })
            }
            selectionBubble.addView(line, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }

        row(
            "Largeur -" to { editorView.updateSelected { it.resize(-4f, 0f) } },
            "Largeur +" to { editorView.updateSelected { it.resize(4f, 0f) } }
        )
        row(
            "Profondeur -" to { editorView.updateSelected { it.resize(0f, -4f) } },
            "Profondeur +" to { editorView.updateSelected { it.resize(0f, 4f) } }
        )
        row(
            "Hauteur -" to { editorView.updateSelected { it.taller(-1f) } },
            "Hauteur +" to { editorView.updateSelected { it.taller(1f) } }
        )
        row(
            "Descendre" to { editorView.updateSelected { it.lift(-1f) } },
            "Monter" to { editorView.updateSelected { it.lift(1f) } }
        )
        row(
            "Tourner" to { editorView.updateSelected { it.copy(width = it.depth, depth = it.width) } },
            "Supprimer" to { editorView.deleteSelected() }
        )
        root.addView(selectionBubble, FrameLayout.LayoutParams(dp(350), -2).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            rightMargin = dp(12)
        })
    }

    private fun exportWorld() {
        val uri = store.exportCopy(editorView.world)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, ToyboxWorldStore.FILE_NAME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Exporter la maison"))
    }

    private fun button(label: String, color: Int): Button = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        isAllCaps = false
        includeFontPadding = false
        alpha = 0.82f
        setPadding(dp(4), dp(2), dp(4), dp(2))
        background = rounded(color, 14f)
        stateListAnimator = null
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
        setStroke(dp(1), 0x55FFFFFF)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
