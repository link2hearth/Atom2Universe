package com.Atom2Universe.app.sf2creator.data

import android.content.Context

/**
 * What was copied in the SF2 creator: programs, instruments or samples of a project. Only
 * their ids are kept (the copy is made when pasting), so the clipboard costs nothing, works
 * from one project to another and survives the app being closed.
 */
object Sf2ProjectClipboard {

    enum class Kind { PROGRAMS, INSTRUMENTS, SAMPLES }

    /**
     * @param ids programs, preset zones (instruments as they appear in a program) or samples
     * @param label what to show on the paste button
     */
    class Content(val kind: Kind, val projectId: Long, val ids: List<Long>, val label: String)

    private const val PREFS = "sf2_clipboard"

    fun get(context: Context): Content? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val kind = prefs.getString("kind", null)?.let { name -> Kind.entries.firstOrNull { it.name == name } } ?: return null
        val ids = prefs.getString("ids", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return null
        return Content(kind, prefs.getLong("project", -1), ids, prefs.getString("label", "").orEmpty())
    }

    fun set(context: Context, content: Content) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("kind", content.kind.name)
            .putLong("project", content.projectId)
            .putString("ids", content.ids.joinToString(","))
            .putString("label", content.label)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
