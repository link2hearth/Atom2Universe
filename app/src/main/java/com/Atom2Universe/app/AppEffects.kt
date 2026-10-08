package com.Atom2Universe.app

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.StringRes
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.Atom2Universe.app.effects.EffectsDecoration
import com.Atom2Universe.app.effects.EffectsPalette
import com.Atom2Universe.app.effects.EffectsSoundPlayer

enum class AppEffectPack(val id: String, @param:StringRes val labelRes: Int, val hasSounds: Boolean = false) {
    SKY("sky", R.string.theme_effect_sky),
    CHRISTMAS("christmas", R.string.theme_effect_christmas, hasSounds = true)
}

/** Saved independently of shapes, brightness, gradients and accent color. Sounds are opt-in. */
data class AppEffectsSettings(
    val enabled: Boolean = false,
    val sounds: Boolean = false,
    val pack: AppEffectPack? = null,
    val animations: Boolean = true
)

/**
 * Only explicitly marked backgrounds/banners receive decorations. Games, canvases and media
 * content keep their own identity. Drawables never consume touches or enter the accessibility
 * tree. One controller per activity follows fragments/layout changes and releases on destroy.
 */
internal object AppEffects {
    private val controllers = mutableMapOf<Activity, Controller>()

    fun bind(activity: Activity) {
        val owner = activity as? LifecycleOwner ?: return
        val existing = controllers[activity]
        if (existing != null) existing.refresh()
        else Controller(activity).also {
            controllers[activity] = it
            owner.lifecycle.addObserver(it)
        }
    }

    private class Controller(private val activity: Activity) : DefaultLifecycleObserver,
        ViewTreeObserver.OnGlobalLayoutListener {
        private val decorations = mutableMapOf<View, EffectsDecoration>()
        private var content: ViewGroup? = null
        private var active = false
        private val sound = EffectsSoundPlayer(activity)

        override fun onStart(owner: LifecycleOwner) {
            content = activity.findViewById(android.R.id.content)
            content?.viewTreeObserver?.addOnGlobalLayoutListener(this)
            refresh()
        }

        override fun onResume(owner: LifecycleOwner) {
            active = true
            refresh()
        }

        override fun onPause(owner: LifecycleOwner) {
            active = false
            decorations.values.forEach { it.setActive(false) }
            sound.stop()
        }

        override fun onStop(owner: LifecycleOwner) {
            content?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(this)
            decorations.values.forEach { it.close() }
            decorations.clear()
            content = null
        }

        override fun onDestroy(owner: LifecycleOwner) {
            onStop(owner)
            sound.stop()
            controllers.remove(activity)
            owner.lifecycle.removeObserver(this)
        }

        override fun onGlobalLayout() = refresh()

        fun refresh() {
            val root = content ?: return
            // Static ornamentation is independent of whether an animated effect pack is enabled.
            OrnamentStyle.refreshCards(root, activity)
            val settings = AppThemeManager.getEffects(activity)
            val targets = mutableListOf<View>()
            if (settings.enabled && settings.pack != null) collectTargets(root, targets)
            val obsolete = decorations.keys.filter { it !in targets }
            obsolete.forEach { decorations.remove(it)?.close() }
            val palette = EffectsPalette.from(activity)
            targets.forEach { target ->
                val decoration = decorations.getOrPut(target) { EffectsDecoration(target) }
                decoration.update(settings, palette)
                decoration.setActive(active)
            }
            // Bells belong to hubs, never to a recording, practice, drawing or game screen.
            val soundHost = activity is AudioHubActivity || activity is com.Atom2Universe.app.hub.BaseHubActivity
            sound.update(settings, active && targets.isNotEmpty() && soundHost)
        }

        private fun collectTargets(view: View, targets: MutableList<View>) {
            if (view.visibility != View.VISIBLE) return
            if (view.getTag(R.id.app_effects_scene) != null) targets += view
            if (view is ViewGroup) for (i in 0 until view.childCount) collectTargets(view.getChildAt(i), targets)
        }
    }
}
