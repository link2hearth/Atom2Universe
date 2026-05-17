package com.Atom2Universe.app.periodic

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.View
import android.app.Dialog
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.Atom2Universe.app.R
import com.Atom2Universe.app.AudioHubActivity
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.crypto.gacha.GachaRarity
import com.Atom2Universe.app.crypto.gacha.rarityOf
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PeriodicTableActivity : AppCompatActivity() {

  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(LocaleHelper.applyLocale(newBase))
  }
  private lateinit var gridLayout: GridLayout
  private lateinit var descriptionProvider: PeriodicElementDescriptionProvider
  private lateinit var collectionStore: PeriodicCollectionStore
  private var selectedElementView: LinearLayout? = null
  private var selectedElement: PeriodicElement? = null
  private var lastSelectedElementCell: LinearLayout? = null
  private var launchedFromGacha: Boolean = false
  private var selectedElementCopiesView: TextView? = null

  private val rarityCornerViews = mutableListOf<View>()
  private var rarityVisible = true

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_periodic_table)

    gridLayout = findViewById(R.id.periodic_grid)
    descriptionProvider = PeriodicElementDescriptionProvider(this)
    collectionStore = PeriodicCollectionStore(this)
    launchedFromGacha = intent.getStringExtra(EXTRA_SOURCE) == SOURCE_GACHA

    findViewById<ImageButton>(R.id.back_button).setOnClickListener {
      navigateBack()
    }

    findViewById<ImageButton>(R.id.toggle_rarity_button).setOnClickListener {
      rarityVisible = !rarityVisible
      val targetVisibility = if (rarityVisible) View.VISIBLE else View.INVISIBLE
      rarityCornerViews.forEach { corner ->
        // Ne montrer que les coins déjà débloqués (tag = true si possédé)
        if (rarityVisible && corner.tag == true) corner.visibility = View.VISIBLE
        else if (!rarityVisible) corner.visibility = View.INVISIBLE
      }
      it.alpha = if (rarityVisible) 1f else 0.4f
    }

    CoroutineScope(Dispatchers.IO).launch {
      PeriodicElementJsonRepository.load(applicationContext)
    }

    populatePeriodicTable()
    createInfoPanel()
  }

  private fun dpToPx(dp: Int): Int {
    return (dp * resources.displayMetrics.density).toInt()
  }

  private fun createInfoLine(label: String, value: String): LinearLayout {
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      setPadding(0, 0, 0, 8)

      val labelView = TextView(this@PeriodicTableActivity).apply {
        text = label
        textSize = 12f
        setTextColor(0xFFB0B0B0.toInt())
      }
      addView(labelView)

      val valueView = TextView(this@PeriodicTableActivity).apply {
        text = value
        textSize = 18f
        setTextColor(0xFFFFFFFF.toInt())
        setTypeface(null, android.graphics.Typeface.BOLD)
      }
      addView(valueView)

      tag = Pair(labelView, valueView)
    }
  }

  private fun createInfoPanel() {
    selectedElementView = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(16, 16, 16, 16)

      // En-tête : nom + copies gacha
      val headerRow = LinearLayout(this@PeriodicTableActivity).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setPadding(0, 0, 0, 12)
      }

      val nameView = TextView(this@PeriodicTableActivity).apply {
        textSize = 28f
        setTextColor(0xFFFFFFFF.toInt())
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener { showDescriptionDialog() }
      }
      headerRow.addView(nameView)

      selectedElementCopiesView = TextView(this@PeriodicTableActivity).apply {
        text = getString(R.string.periodic_info_copies_badge, 0)
        textSize = 11f
        setTextColor(0xCCFFFFFF.toInt())
        setPadding(10, 6, 10, 6)
        background = resources.getDrawable(R.drawable.gacha_rarity_badge, null)
      }
      headerRow.addView(selectedElementCopiesView)
      addView(headerRow)

      // Container pour les deux colonnes
      val infosContainer = LinearLayout(this@PeriodicTableActivity).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )

        // Colonne gauche
        val leftColumn = LinearLayout(this@PeriodicTableActivity).apply {
          orientation = LinearLayout.VERTICAL
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

          addView(createInfoLine(getString(R.string.periodic_info_atomic_number), ""))
          addView(createInfoLine(getString(R.string.periodic_info_mass), ""))
          addView(createInfoLine(getString(R.string.periodic_info_period), ""))
        }
        addView(leftColumn)

        // Colonne droite
        val rightColumn = LinearLayout(this@PeriodicTableActivity).apply {
          orientation = LinearLayout.VERTICAL
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
          setPadding(16, 0, 0, 0)

          addView(createInfoLine(getString(R.string.periodic_info_symbol), ""))
          addView(createInfoLine(getString(R.string.periodic_info_category), ""))
          addView(createInfoLine(getString(R.string.periodic_info_group), ""))
        }
        addView(rightColumn)
      }
      addView(infosContainer)

      // Texte d'aide
      val hintView = TextView(this@PeriodicTableActivity).apply {
        text = getString(R.string.periodic_info_hint)
        textSize = 11f
        setTextColor(0xFFB0B0B0.toInt())
        setPadding(0, 12, 0, 0)
      }
      addView(hintView)
    }

    val scrollView = ScrollView(this).apply {
      isVerticalScrollBarEnabled = false
      overScrollMode = ScrollView.OVER_SCROLL_NEVER
      background = resources.getDrawable(R.drawable.element_background, null)
      background.setTint(0xFF3A3A3A.toInt())
    }
    scrollView.addView(selectedElementView)

    val params = GridLayout.LayoutParams().apply {
      columnSpec = GridLayout.spec(2, 10)  // Colonnes 3 à 12
      rowSpec = GridLayout.spec(0, 3)      // Lignes 1 à 3
      width = dpToPx(70 * 10 - 6)
      height = dpToPx(70 * 3 - 3)
      setMargins(1, 1, 1, 1)
    }
    gridLayout.addView(scrollView, params)
  }

  private fun updateInfoPanel(element: PeriodicElement, cellView: LinearLayout) {
    selectedElement = element

    // Réinitialiser le cell précédemment sélectionné
    lastSelectedElementCell?.let { prevCell ->
      val prevElement = prevCell.tag as? PeriodicElement
      prevElement?.let {
        val prevColor = getCategoryColor(it.category)
        prevCell.background.setTint(prevColor)
      }
    }

    // Appliquer la couleur grise au cell sélectionné
    cellView.tag = element
    cellView.background.setTint(0xFF555555.toInt())  // Gris sélectionné
    lastSelectedElementCell = cellView

    selectedElementView?.let { view ->
      val headerRow = view.getChildAt(0) as LinearLayout
      val nameView = headerRow.getChildAt(0) as TextView
      val infosContainer = view.getChildAt(1) as LinearLayout
      val leftColumn = infosContainer.getChildAt(0) as LinearLayout
      val rightColumn = infosContainer.getChildAt(1) as LinearLayout

      nameView.text = element.localizedName(this@PeriodicTableActivity)

      // Colonne gauche
      updateInfoValue(leftColumn.getChildAt(0), element.atomicNumber.toString())
      updateInfoValue(leftColumn.getChildAt(1), "%.3f".format(element.atomicMass))
      updateInfoValue(leftColumn.getChildAt(2), element.period.toString())

      // Colonne droite
      updateInfoValue(rightColumn.getChildAt(0), element.symbol)
      updateInfoValue(rightColumn.getChildAt(1), element.category)
      updateInfoValue(rightColumn.getChildAt(2), element.group.toString())
      selectedElementCopiesView?.text = getString(
        R.string.periodic_info_copies_badge,
        collectionStore.getCopyCount(element.atomicNumber)
      )

    }
  }

  private fun updateInfoValue(infoLine: android.view.View, value: String) {
    val pair = infoLine.tag as? Pair<*, *>
    pair?.let {
      val valueView = it.second as? TextView
      valueView?.text = value
    }
  }

  private fun showDescriptionDialog() {
    selectedElement?.let { element ->
      val description = descriptionProvider.getDescription(element)
      val jsonData = PeriodicElementJsonRepository.get(element.atomicNumber)

      val dialog = Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar)

      val rootLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFF121212.toInt())
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )
      }

      // Header avec bouton retour
      val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        setBackgroundColor(0xFF1E1E1E.toInt())
      }

      val backBtn = TextView(this).apply {
        text = "←"
        textSize = 28f
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(0, 0, dpToPx(20), 0)
        setOnClickListener { dialog.dismiss() }
      }
      header.addView(backBtn)

      val titleView = TextView(this).apply {
        text = "${element.atomicNumber} • ${element.symbol} • ${element.localizedName(this@PeriodicTableActivity)}"
        textSize = 20f
        setTextColor(0xFFFFFFFF.toInt())
        setTypeface(null, android.graphics.Typeface.BOLD)
      }
      header.addView(titleView)
      rootLayout.addView(header)

      // Corps scrollable — taille naturelle, plafonnée post-layout
      val scrollView = ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
      }

      val contentLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
      }

      val summaryView = TextView(this).apply {
        text = description.summary
        textSize = 28f
        setTextColor(0xFFFFFFFF.toInt())
        setTextIsSelectable(true)
        setPadding(0, 0, 0, dpToPx(16))
      }
      contentLayout.addView(summaryView)

      if (jsonData != null && jsonData.shells.isNotEmpty()) {
        contentLayout.addView(buildAtomSection(element, jsonData.shells))
      }

      if (jsonData != null) {
        contentLayout.addView(buildPropertiesCard(jsonData))
      }

      description.paragraphs.forEach { para ->
        val paraView = TextView(this).apply {
          text = para
          textSize = 24f
          setTextColor(0xFFE0E0E0.toInt())
          setTextIsSelectable(true)
          setPadding(0, 0, 0, dpToPx(12))
        }
        contentLayout.addView(paraView)
      }

      scrollView.addView(contentLayout)
      rootLayout.addView(scrollView)

      dialog.setContentView(rootLayout)
      dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
      dialog.window?.setLayout(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      dialog.show()

      // Re-mesurer le contenu sans contrainte de hauteur pour obtenir la taille naturelle,
      // puis forcer la fenêtre à cette taille (plafonnée à 88 % de l'écran).
      scrollView.post {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(scrollView.width, View.MeasureSpec.EXACTLY)
        contentLayout.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val naturalH = contentLayout.measuredHeight
        val headerH = header.height
        val maxH = (resources.displayMetrics.heightPixels * 0.88).toInt()
        val neededH = naturalH + headerH

        if (neededH <= maxH) {
          dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, neededH)
        } else {
          dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxH)
          scrollView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            maxH - headerH
          )
          scrollView.requestLayout()
        }
      }
    }
  }

  private fun buildAtomSection(element: PeriodicElement, shells: List<Int>): LinearLayout {
    val neutrons = (kotlin.math.round(element.atomicMass) - element.atomicNumber).toInt()
      .coerceAtLeast(0)

    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(0xFF0E0E1C.toInt())
      val lp = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      lp.setMargins(0, 0, 0, dpToPx(20))
      layoutParams = lp
    }

    // ── En-tête cliquable ──────────────────────────────────────────────────
    val header = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = android.view.Gravity.CENTER_VERTICAL
      setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
      isClickable = true
      isFocusable = true
      background = android.util.TypedValue().also {
        theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
      }.resourceId.let { resId ->
        if (resId != 0) getDrawable(resId)
        else null
      }
    }

    val titleBlock = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val titleView = TextView(this).apply {
      text = "⚛  ${getString(R.string.periodic_atom_section_title)}"
      textSize = 13f
      setTextColor(0xFFCCCCEE.toInt())
      typeface = Typeface.DEFAULT_BOLD
      isAllCaps = true
      letterSpacing = 0.08f
    }
    titleBlock.addView(titleView)

    // Ligne p / n / e⁻
    val statsRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      val lp = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      lp.setMargins(0, dpToPx(5), 0, 0)
      layoutParams = lp
    }

    fun statChip(label: String, value: Int, labelColor: Int, valueColor: Int): LinearLayout {
      return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        val lp = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, dpToPx(18), 0)
        layoutParams = lp
        addView(TextView(this@PeriodicTableActivity).apply {
          text = label
          textSize = 13f
          setTextColor(labelColor)
          typeface = Typeface.DEFAULT_BOLD
          setPadding(0, 0, dpToPx(4), 0)
        })
        addView(TextView(this@PeriodicTableActivity).apply {
          text = value.toString()
          textSize = 13f
          setTextColor(valueColor)
          typeface = Typeface.DEFAULT_BOLD
        })
      }
    }

    statsRow.addView(statChip(
      getString(R.string.periodic_atom_protons), element.atomicNumber,
      0xFFFF9955.toInt(), 0xFFFFCC99.toInt()
    ))
    statsRow.addView(statChip(
      getString(R.string.periodic_atom_neutrons), neutrons,
      0xFF9999AA.toInt(), 0xFFCCCCDD.toInt()
    ))
    statsRow.addView(statChip(
      getString(R.string.periodic_atom_electrons), element.atomicNumber,
      0xFF55CCFF.toInt(), 0xFFAAEEFF.toInt()
    ))
    titleBlock.addView(statsRow)
    header.addView(titleBlock)

    val chevron = TextView(this).apply {
      text = "▼"
      textSize = 14f
      setTextColor(0xFF6666AA.toInt())
    }
    header.addView(chevron)
    container.addView(header)

    // ── Contenu repliable ─────────────────────────────────────────────────
    val content = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      visibility = View.GONE
      setPadding(dpToPx(16), 0, dpToPx(16), dpToPx(16))
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    val atomView = AtomDiagramView(this).apply {
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      this.shells = shells
      atomicNumber = element.atomicNumber
      neutronCount = neutrons
    }
    content.addView(atomView)
    container.addView(content)

    // ── Logique expand / collapse ─────────────────────────────────────────
    var expanded = false

    header.setOnClickListener {
      expanded = !expanded
      if (expanded) {
        content.visibility = View.VISIBLE
        content.measure(
          View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val targetH = content.measuredHeight
        content.layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, 0
        )

        ValueAnimator.ofInt(0, targetH).apply {
          duration = 380
          interpolator = DecelerateInterpolator()
          addUpdateListener { va ->
            content.layoutParams = LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, va.animatedValue as Int
            )
          }
          addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
              content.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
              )
              atomView.startAnimation()
            }
          })
          start()
        }
        chevron.animate().rotation(180f).setDuration(350).start()

      } else {
        atomView.stopAnimation()
        val startH = content.height

        ValueAnimator.ofInt(startH, 0).apply {
          duration = 270
          interpolator = AccelerateInterpolator()
          addUpdateListener { va ->
            content.layoutParams = LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, va.animatedValue as Int
            )
          }
          addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
              content.visibility = View.GONE
              content.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
              )
            }
          })
          start()
        }
        chevron.animate().rotation(0f).setDuration(270).start()
      }
    }

    return container
  }

  private fun buildPropertiesCard(data: ElementJsonData): LinearLayout {
    val unknown = getString(R.string.periodic_prop_unknown)
    val kSuffix = getString(R.string.periodic_prop_kelvin_suffix)
    val gSuffix = getString(R.string.periodic_prop_density_suffix)

    fun fmt(value: Double?, decimals: Int = 3, suffix: String = ""): String =
      if (value == null) unknown else "%.${decimals}f$suffix".format(value)

    val props = listOfNotNull(
      data.phase?.let { Pair(getString(R.string.periodic_prop_phase), it) },
      Pair(getString(R.string.periodic_prop_density), fmt(data.density, 4, gSuffix)),
      Pair(getString(R.string.periodic_prop_melt), fmt(data.melt, 2, kSuffix)),
      Pair(getString(R.string.periodic_prop_boil), fmt(data.boil, 2, kSuffix)),
      data.block?.let { Pair(getString(R.string.periodic_prop_block), it) },
      data.electronegativityPauling?.let { Pair(getString(R.string.periodic_prop_electronegativity), "%.2f".format(it)) },
      data.discoveredBy?.let { Pair(getString(R.string.periodic_prop_discovered_by), it) },
      data.namedBy?.let { Pair(getString(R.string.periodic_prop_named_by), it) },
      data.electronConfiguration?.let { Pair(getString(R.string.periodic_prop_electron_config), it) },
      data.appearance?.let { Pair(getString(R.string.periodic_prop_appearance), it) }
    )

    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(0xFF1A1A2E.toInt())
      setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
      val lp = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      lp.setMargins(0, 0, 0, dpToPx(20))
      layoutParams = lp

      val titleView = TextView(this@PeriodicTableActivity).apply {
        text = getString(R.string.periodic_prop_properties_title)
        textSize = 13f
        setTextColor(0xFF8888AA.toInt())
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dpToPx(12))
        isAllCaps = true
        letterSpacing = 0.1f
      }
      addView(titleView)

      // Grille 2 colonnes
      var rowLayout: LinearLayout? = null
      props.forEachIndexed { index, (label, value) ->
        val isFullWidth = label == getString(R.string.periodic_prop_electron_config) ||
            label == getString(R.string.periodic_prop_appearance)

        if (isFullWidth) {
          if (rowLayout != null) {
            addView(rowLayout)
            rowLayout = null
          }
          addView(buildPropCell(label, value, fullWidth = true))
        } else {
          if (rowLayout == null) {
            rowLayout = LinearLayout(this@PeriodicTableActivity).apply {
              orientation = LinearLayout.HORIZONTAL
              layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
              )
            }
          }
          rowLayout!!.addView(buildPropCell(label, value, fullWidth = false))
          if (rowLayout!!.childCount == 2) {
            addView(rowLayout)
            rowLayout = null
          }
        }
      }
      rowLayout?.let { addView(it) }
    }
  }

  private fun buildPropCell(label: String, value: String, fullWidth: Boolean): LinearLayout {
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      // fullWidth=true → dans un parent VERTICAL, MATCH_PARENT en largeur
      // fullWidth=false → dans un parent HORIZONTAL, weight=1f pour partager la largeur
      val lp = if (fullWidth) {
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
      } else {
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }
      lp.setMargins(0, 0, dpToPx(8), dpToPx(10))
      layoutParams = lp

      val labelView = TextView(this@PeriodicTableActivity).apply {
        text = label
        textSize = 11f
        setTextColor(0xFF8888AA.toInt())
      }
      addView(labelView)

      val valueView = TextView(this@PeriodicTableActivity).apply {
        text = value
        textSize = 15f
        setTextColor(0xFFE8E8F0.toInt())
        setTypeface(null, android.graphics.Typeface.BOLD)
      }
      addView(valueView)
    }
  }

  private fun populatePeriodicTable() {
    val elements = getPeriodicElements()

    for (element in elements) {
      val cellView = createElementCell(element)
      val params = GridLayout.LayoutParams().apply {
        columnSpec = GridLayout.spec(element.column - 1, 1)
        rowSpec = GridLayout.spec(element.row - 1, 1)
        width = dpToPx(70)
        height = dpToPx(70)
      }
      gridLayout.addView(cellView, params)
    }

    addLegendCells()
  }

  // Positions col/row 1-indexées : (1,8)(2,8)(3,8) / (1,9)(2,9)(3,9)
  private val legendPositions = listOf(
    GachaRarity.COMMUN    to Pair(1, 8),
    GachaRarity.ESSENTIEL to Pair(2, 8),
    GachaRarity.STELLAIRE to Pair(3, 8),
    GachaRarity.MYTHIQUE  to Pair(1, 9),
    GachaRarity.SINGULIER to Pair(2, 9),
    GachaRarity.IRREEL    to Pair(3, 9)
  )

  private fun addLegendCells() {
    for ((rarity, pos) in legendPositions) {
      val (col, row) = pos
      val cell = createLegendCell(rarity)
      val params = GridLayout.LayoutParams().apply {
        columnSpec = GridLayout.spec(col - 1, 1)
        rowSpec = GridLayout.spec(row - 1, 1)
        width = dpToPx(70)
        height = dpToPx(70)
        setMargins(2, 2, 2, 2)
      }
      gridLayout.addView(cell, params)
    }
  }

  private fun createLegendCell(rarity: GachaRarity): View {
    val rarityColor = getRarityColor(rarity)
    val cell = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = android.view.Gravity.CENTER
      setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6))
      background = resources.getDrawable(R.drawable.element_background, null)
      background.setTint(rarityColor)
    }

    val dot = TextView(this).apply {
      text = "◆"
      textSize = 10f
      setTextColor(0xCCFFFFFF.toInt())
      gravity = android.view.Gravity.CENTER
    }
    cell.addView(dot)

    val label = TextView(this).apply {
      text = rarity.label
      textSize = 8.5f
      setTextColor(0xFFFFFFFF.toInt())
      gravity = android.view.Gravity.CENTER
      setPadding(0, dpToPx(2), 0, 0)
    }
    cell.addView(label)

    cell.setOnClickListener { showRarityDialog(rarity) }
    return cell
  }

  private fun rarityKey(rarity: GachaRarity) = when (rarity) {
    GachaRarity.COMMUN    -> "commun"
    GachaRarity.ESSENTIEL -> "essentiel"
    GachaRarity.STELLAIRE -> "stellaire"
    GachaRarity.MYTHIQUE  -> "mythique"
    GachaRarity.SINGULIER -> "singulier"
    GachaRarity.IRREEL    -> "irreel"
  }

  private fun showRarityDialog(rarity: GachaRarity) {
    val rarityColor = getRarityColor(rarity)
    val key = rarityKey(rarity)
    val processId = resources.getIdentifier("rarity_${key}_process", "string", packageName)
    val rangeId   = resources.getIdentifier("rarity_${key}_range",   "string", packageName)
    val bodyId    = resources.getIdentifier("rarity_${key}_body",    "string", packageName)
    val process = if (processId != 0) getString(processId) else ""
    val range   = if (rangeId   != 0) getString(rangeId)   else ""
    val body    = if (bodyId    != 0) getString(bodyId)    else ""

    val dialog = Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar)

    val rootLayout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(0xFF121212.toInt())
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    }

    // Header
    val header = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = android.view.Gravity.CENTER_VERTICAL
      setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
      setBackgroundColor(0xFF1E1E1E.toInt())
    }
    val backBtn = TextView(this).apply {
      text = "←"
      textSize = 28f
      setTextColor(0xFFFFFFFF.toInt())
      setPadding(0, 0, dpToPx(20), 0)
      setOnClickListener { dialog.dismiss() }
    }
    header.addView(backBtn)
    val titleView = TextView(this).apply {
      text = rarity.label
      textSize = 22f
      setTextColor(rarityColor)
      setTypeface(null, android.graphics.Typeface.BOLD)
    }
    header.addView(titleView)
    rootLayout.addView(header)

    // Barre colorée sous le header
    val colorBar = View(this).apply {
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(3)
      )
      setBackgroundColor(rarityColor)
    }
    rootLayout.addView(colorBar)

    // Corps
    val scrollView = ScrollView(this).apply {
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }
    val content = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(32))
    }

    // Processus
    if (process.isNotEmpty()) {
      content.addView(TextView(this).apply {
        text = process
        textSize = 18f
        setTextColor(rarityColor)
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dpToPx(6))
      })
    }
    // Plage d'éléments
    if (range.isNotEmpty()) {
      content.addView(TextView(this).apply {
        text = range
        textSize = 13f
        setTextColor(0xFF888899.toInt())
        setPadding(0, 0, 0, dpToPx(20))
      })
    }
    // Corps
    if (body.isNotEmpty()) {
      content.addView(TextView(this).apply {
        text = body
        textSize = 17f
        setTextColor(0xFFE0E0E0.toInt())
        setTextIsSelectable(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          lineHeight = (textSize * 1.55f * resources.displayMetrics.density * resources.configuration.fontScale).toInt()
        }
      })
    }

    scrollView.addView(content)
    rootLayout.addView(scrollView)

    dialog.setContentView(rootLayout)
    dialog.window?.setBackgroundDrawable(
      android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
    )
    dialog.window?.setLayout(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    dialog.show()

    scrollView.post {
      val widthSpec = View.MeasureSpec.makeMeasureSpec(scrollView.width, View.MeasureSpec.EXACTLY)
      content.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
      val naturalH = content.measuredHeight
      val headerH = header.height + colorBar.height
      val maxH = (resources.displayMetrics.heightPixels * 0.88).toInt()
      val neededH = naturalH + headerH
      if (neededH <= maxH) {
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, neededH)
      } else {
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxH)
        scrollView.layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, maxH - headerH
        )
        scrollView.requestLayout()
      }
    }
  }

  private fun navigateBack() {
    if (launchedFromGacha) {
      finish()
      return
    }

    if (isTaskRoot) {
      startActivity(Intent(this, AudioHubActivity::class.java))
    }
    finish()
  }

  private fun createElementCell(element: PeriodicElement): View {
    val frameLayout = FrameLayout(this)

    val cell = LinearLayout(this)
    cell.orientation = LinearLayout.VERTICAL
    cell.gravity = android.view.Gravity.CENTER
    cell.setPadding(6, 6, 6, 6)
    cell.background = resources.getDrawable(R.drawable.element_background, null)

    // Dimensions de la cellule
    val cellSize = 70  // dp
    val layoutParams = FrameLayout.LayoutParams(
      dpToPx(cellSize),
      dpToPx(cellSize)
    )
    layoutParams.setMargins(2, 2, 2, 2)
    cell.layoutParams = layoutParams

    // Appliquer la couleur
    val categoryColor = getCategoryColor(element.category)
    cell.background.setTint(categoryColor)

    // Symbole atomique
    val symbolView = TextView(this).apply {
      text = element.symbol
      textSize = 24f
      setTextColor(0xFFFFFFFF.toInt())
      setTypeface(null, android.graphics.Typeface.BOLD)
    }
    cell.addView(symbolView)

    frameLayout.addView(cell, layoutParams)

    // Coin de rareté — visible si jamais obtenu ET toggle activé
    val owned = collectionStore.hasEverObtained(element.atomicNumber)
    val rarityColor = getRarityCornerColor(element.atomicNumber)
    val cornerSize = dpToPx(25)
    val rarityCorner = View(this)
    rarityCorner.setBackgroundColor(rarityColor)
    rarityCorner.tag = owned  // true = possédé, utilisé par le toggle
    rarityCorner.visibility = if (owned && rarityVisible) View.VISIBLE else View.INVISIBLE
    val params = FrameLayout.LayoutParams(cornerSize, cornerSize)
    params.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
    rarityCorner.layoutParams = params
    frameLayout.addView(rarityCorner)
    rarityCornerViews.add(rarityCorner)

    frameLayout.setOnClickListener { updateInfoPanel(element, cell) }
    frameLayout.tag = element
    return frameLayout
  }

  private fun getRarityColor(rarity: GachaRarity): Int = when (rarity) {
    GachaRarity.COMMUN    -> getColor(R.color.rarity_commun)
    GachaRarity.ESSENTIEL -> getColor(R.color.rarity_essentiel)
    GachaRarity.STELLAIRE -> getColor(R.color.rarity_stellaire)
    GachaRarity.MYTHIQUE  -> getColor(R.color.rarity_mythique)
    GachaRarity.SINGULIER -> getColor(R.color.rarity_singulier)
    GachaRarity.IRREEL    -> getColor(R.color.rarity_irreel)
  }

  private fun getRarityCornerColor(atomicNumber: Int): Int =
    getRarityColor(rarityOf(atomicNumber))

  private fun getCategoryColor(category: String): Int = when (category) {
    "alkali-metal" -> getColor(R.color.category_alkali_metal)
    "alkaline-earth-metal" -> getColor(R.color.category_alkaline_earth_metal)
    "transition-metal" -> getColor(R.color.category_transition_metal)
    "post-transition-metal" -> getColor(R.color.category_post_transition_metal)
    "metalloid" -> getColor(R.color.category_metalloid)
    "nonmetal" -> getColor(R.color.category_nonmetal)
    "halogen" -> getColor(R.color.category_halogen)
    "noble-gas" -> getColor(R.color.category_noble_gas)
    "lanthanide" -> getColor(R.color.category_lanthanide)
    "actinide" -> getColor(R.color.category_actinide)
    else -> getColor(R.color.category_default)
  }

  companion object {
    const val EXTRA_SOURCE = "periodic_source"
    const val SOURCE_GACHA = "gacha"
  }

}
