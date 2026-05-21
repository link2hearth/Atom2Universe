package com.Atom2Universe.app.audio

import android.content.Intent
import com.Atom2Universe.app.R
import com.Atom2Universe.app.audioeditor.AudioEditorActivity
import com.Atom2Universe.app.dictaphone.DictaphoneActivity
import com.Atom2Universe.app.hub.BaseHubActivity
import com.Atom2Universe.app.hub.HubTile
import com.Atom2Universe.app.midi.ui.MidiPlayerActivity
import com.Atom2Universe.app.music.MusicPlayerActivity
import com.Atom2Universe.app.radio.RadioActivity
import com.Atom2Universe.app.sf2creator.Sf2CreatorActivity
import com.Atom2Universe.app.stats.ui.StatsActivity

class AudioSubHubActivity : BaseHubActivity() {

    override fun getLayoutResId(): Int = R.layout.activity_base_hub

    override fun getPrefsName(): String = "audio_sub_hub_prefs"

    override fun getHubTitle(): Int = R.string.audio_sub_hub_title

    override fun getHubSubtitle(): Int = R.string.audio_sub_hub_subtitle

    override fun getDefaultTiles(): List<HubTile> = listOf(
        HubTile(
            id = "music",
            titleRes = R.string.audio_hub_music_title,
            descriptionRes = R.string.audio_hub_music_desc,
            iconRes = R.drawable.ic_music_note,
            defaultColorRes = R.color.audio_hub_tile_music,
            activityClass = MusicPlayerActivity::class.java
        ),
        HubTile(
            id = "midi",
            titleRes = R.string.audio_hub_midi_title,
            descriptionRes = R.string.audio_hub_midi_desc,
            iconRes = R.drawable.ic_midi,
            defaultColorRes = R.color.audio_hub_tile_midi,
            activityClass = MidiPlayerActivity::class.java
        ),
        HubTile(
            id = "radio",
            titleRes = R.string.audio_hub_radio_title,
            descriptionRes = R.string.audio_hub_radio_desc,
            iconRes = R.drawable.ic_radio,
            defaultColorRes = R.color.audio_hub_tile_radio,
            activityClass = RadioActivity::class.java
        ),
        HubTile(
            id = "editor",
            titleRes = R.string.audio_hub_editor_title,
            descriptionRes = R.string.audio_hub_editor_desc,
            iconRes = R.drawable.ic_content_cut,
            defaultColorRes = R.color.audio_hub_tile_editor,
            activityClass = AudioEditorActivity::class.java
        ),
        HubTile(
            id = "sf2_creator",
            titleRes = R.string.sf2_creator_title,
            descriptionRes = R.string.sf2_creator_desc,
            iconRes = R.drawable.ic_sf2_creator,
            defaultColorRes = R.color.audio_hub_tile_sf2_creator,
            activityClass = Sf2CreatorActivity::class.java
        ),
        HubTile(
            id = "stats",
            titleRes = R.string.audio_hub_stats_title,
            descriptionRes = R.string.audio_hub_stats_desc,
            iconRes = R.drawable.ic_stats,
            defaultColorRes = R.color.audio_hub_tile_stats,
            activityClass = StatsActivity::class.java
        ),
        HubTile(
            id = "dictaphone",
            titleRes = R.string.audio_hub_dictaphone_title,
            descriptionRes = R.string.audio_hub_dictaphone_desc,
            iconRes = R.drawable.ic_mic,
            defaultColorRes = R.color.audio_hub_tile_dictaphone,
            activityClass = DictaphoneActivity::class.java
        )
    )

    override fun getParentHubPrefsName(): String = "audio_hub_prefs"

    override fun getParentTileId(): String = "audio"

    override fun onTileClicked(tile: HubTile) {
        tile.activityClass?.let {
            startActivity(Intent(this, it))
        }
    }
}
