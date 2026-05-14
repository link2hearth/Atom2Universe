package com.Atom2Universe.app.sf2creator.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.R

/**
 * A reusable expandable section component for SF2 parameter editing.
 * Shows a header with title and expand/collapse arrow.
 * Content is revealed/hidden with smooth animation.
 *
 * Usage:
 * 1. Add to layout XML
 * 2. Call setTitle() and addContentView() or setContentLayout()
 * 3. Optionally set initial expanded state
 */
class ExpandableSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val headerLayout: LinearLayout
    private val titleText: TextView
    private val arrowIcon: ImageView
    private val resetButton: ImageView
    private val contentContainer: LinearLayout

    private var isExpanded: Boolean = false
    private var animationDuration: Long = 200L
    private var isInflating: Boolean = true

    private var onResetClickListener: (() -> Unit)? = null

    init {
        orientation = VERTICAL

        // Inflate header
        LayoutInflater.from(context).inflate(R.layout.view_expandable_section, this, true)

        headerLayout = findViewById(R.id.section_header)
        titleText = findViewById(R.id.section_title)
        arrowIcon = findViewById(R.id.section_arrow)
        resetButton = findViewById(R.id.section_reset_button)
        contentContainer = findViewById(R.id.section_content)

        // Initially collapsed
        contentContainer.visibility = View.GONE
        arrowIcon.rotation = 0f

        // Header click toggles expansion
        headerLayout.setOnClickListener {
            toggleExpanded()
        }

        // Reset button click
        resetButton.setOnClickListener {
            onResetClickListener?.invoke()
        }

        // Apply custom attributes if any
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.ExpandableSectionView)
            val title = typedArray.getString(R.styleable.ExpandableSectionView_sectionTitle)
            val startExpanded = typedArray.getBoolean(R.styleable.ExpandableSectionView_startExpanded, false)
            val showReset = typedArray.getBoolean(R.styleable.ExpandableSectionView_showResetButton, false)
            typedArray.recycle()

            title?.let { setTitle(it) }
            setExpanded(startExpanded, animate = false)
            setResetButtonVisible(showReset)
        }

        isInflating = false
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        // Move any child views added in XML (after the merge content) to contentContainer
        post {
            moveChildrenToContent()
        }
    }

    /**
     * Move children that were added in XML to the content container.
     * The header and content container are at index 0 and 1.
     */
    private fun moveChildrenToContent() {
        // Collect children to move (skip header at index 0, contentContainer at index 1)
        val childrenToMove = mutableListOf<View>()
        for (i in 2 until childCount) {
            childrenToMove.add(getChildAt(i))
        }

        // Move them to contentContainer
        for (child in childrenToMove) {
            removeView(child)
            contentContainer.addView(child)
        }
    }

    /**
     * Set the section title.
     */
    fun setTitle(title: String) {
        titleText.text = title
    }

    /**
     * Set the section title from resource.
     */
    fun setTitle(titleResId: Int) {
        titleText.setText(titleResId)
    }

    /**
     * Add a view to the content area.
     */
    fun addContentView(view: View) {
        contentContainer.addView(view)
    }

    /**
     * Add a view to the content area with layout params.
     */
    fun addContentView(view: View, params: ViewGroup.LayoutParams) {
        contentContainer.addView(view, params)
    }

    /**
     * Inflate a layout into the content area.
     * @return The inflated view
     */
    fun setContentLayout(layoutResId: Int): View {
        contentContainer.removeAllViews()
        return LayoutInflater.from(context).inflate(layoutResId, contentContainer, true)
    }

    /**
     * Get the content container for direct access.
     */
    fun getContentContainer(): LinearLayout = contentContainer

    /**
     * Set expanded state.
     */
    fun setExpanded(expanded: Boolean, animate: Boolean = true) {
        if (isExpanded == expanded) return

        isExpanded = expanded

        if (animate) {
            animateExpansion(expanded)
        } else {
            contentContainer.visibility = if (expanded) View.VISIBLE else View.GONE
            arrowIcon.rotation = if (expanded) 180f else 0f
        }
    }

    /**
     * Toggle expanded state.
     */
    fun toggleExpanded() {
        setExpanded(!isExpanded, animate = true)
    }

    /**
     * Check if currently expanded.
     */
    fun isExpanded(): Boolean = isExpanded

    /**
     * Set reset button visibility.
     */
    fun setResetButtonVisible(visible: Boolean) {
        resetButton.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Set reset button click listener.
     */
    fun setOnResetClickListener(listener: () -> Unit) {
        onResetClickListener = listener
        resetButton.visibility = View.VISIBLE
    }

    private fun animateExpansion(expand: Boolean) {
        // Rotate arrow
        val startRotation = if (expand) 0f else 180f
        val endRotation = if (expand) 180f else 0f

        ValueAnimator.ofFloat(startRotation, endRotation).apply {
            duration = animationDuration
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                arrowIcon.rotation = animator.animatedValue as Float
            }
            start()
        }

        // Animate content visibility
        if (expand) {
            contentContainer.visibility = View.VISIBLE
            contentContainer.alpha = 0f
            contentContainer.animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .start()
        } else {
            contentContainer.animate()
                .alpha(0f)
                .setDuration(animationDuration)
                .withEndAction {
                    contentContainer.visibility = View.GONE
                }
                .start()
        }
    }
}
