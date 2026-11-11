package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.data.Track
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

/** Une rangée par instrument. Le transport reste accessible autour de cette bulle. */
internal class TrackClipsView(
    context: Context,
    private val session: StudioSession,
    private val engine: StudioEngine,
    private val makeButton: (Int?) -> MaterialButton,
    private val trackName: (Track) -> String,
    private val clipActions: (View, Track, Int) -> Unit,
    capture: (View) -> Unit,
) : LinearLayout(context) {
    private val letters = resources.getStringArray(R.array.studio_pattern_names)
    private val rows = LinearLayout(context).apply { orientation = VERTICAL }
    private val cells = mutableMapOf<Int, List<MaterialButton>>()
    private val follow = makeButton(R.string.studio_clips_follow)
    private val save = makeButton(R.string.studio_clips_capture)
    private var lastTracks: List<Track>? = null
    private var scene = 0
    private var enabledForLaunch = true

    init {
        orientation = VERTICAL
        setPadding(dp(10), 0, dp(10), dp(8))
        addView(FlowRow(context).apply {
            hGap = dp(6)
            addView(follow)
            addView(save)
        })
        follow.setOnClickListener {
            engine.followSceneClips()
            refresh(scene, enabledForLaunch)
        }
        save.setOnClickListener(capture)
        addView(rows)
    }

    fun refresh(currentScene: Int, canLaunch: Boolean) {
        scene = currentScene
        enabledForLaunch = canLaunch
        if (lastTracks !== session.project.tracks) {
            rows.removeAllViews()
            cells.clear()
            for (track in session.project.tracks) if (!track.hidden) addRow(track)
            lastTracks = session.project.tracks
        }
        var hasChoice = false
        var hasActive = false
        for ((track, buttons) in cells) {
            val pinned = engine.activeTrackClip(track)
            val pending = engine.pendingTrackClip(track)
            val active = if (pinned >= 0) pinned else scene
            hasChoice = hasChoice || pinned >= 0 || pending != null
            hasActive = hasActive || pinned >= 0
            val name = trackName(session.project.track(track))
            for ((slot, button) in buttons.withIndex()) {
                val waiting = pending == slot || pending == -1 && slot == scene
                val filled = session.hasTrackClip(track, slot)
                button.text = when {
                    waiting -> context.getString(R.string.studio_loop_queued, letters[slot])
                    pinned == slot -> context.getString(R.string.studio_clip_pinned_letter, letters[slot])
                    else -> letters[slot]
                }
                button.isChecked = active == slot
                button.strokeWidth = dp(if (slot == active || waiting) 2 else 1)
                button.alpha = if (filled || waiting) 1f else 0.55f
                button.isEnabled = canLaunch
                val state = when {
                    waiting -> R.string.studio_clip_waiting
                    pinned == slot -> R.string.studio_clip_pinned
                    active == slot -> R.string.studio_clip_following
                    else -> R.string.studio_clip_idle
                }
                button.contentDescription = context.getString(R.string.studio_clip_label,
                    name, letters[slot], context.getString(state),
                    context.getString(if (filled) R.string.studio_clip_filled else R.string.studio_clip_empty))
            }
        }
        follow.isEnabled = canLaunch && hasChoice
        save.isEnabled = canLaunch && hasActive
    }

    private fun addRow(track: Track) {
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(context).apply {
            text = trackName(track)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            textSize = 14f
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        cells[track.id] = letters.mapIndexed { slot, _ ->
            makeButton(null).apply {
                minWidth = 0
                minimumWidth = 0
                setPadding(0, 0, 0, 0)
                isCheckable = true
                setOnClickListener {
                    val pinned = engine.activeTrackClip(track.id)
                    val pending = engine.pendingTrackClip(track.id)
                    val target = when {
                        pending == -1 && slot == scene -> -1
                        slot == pinned && pending != slot -> -1
                        else -> slot
                    }
                    engine.launchTrackClip(track.id, target)
                    refresh(scene, enabledForLaunch)
                }
                setOnLongClickListener { clipActions(it, track, slot); true }
                row.addView(this, LayoutParams(dp(48), dp(48)).apply { marginStart = dp(2) })
            }
        }
        rows.addView(row)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
