package com.Atom2Universe.app.pixelart.io

import android.content.Context
import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.core.PixelColor
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Une palette : une liste de couleurs nommée. Les palettes « intégrées » ne se modifient pas. */
class NamedPalette(
    val id: String,
    val name: String,
    val colors: List<Int>,
    val builtIn: Boolean = false,
    @StringRes val nameRes: Int = 0,
) {
    fun title(context: Context): String = if (nameRes != 0) context.getString(nameRes) else name
}

/** Lecture des formats de palette courants — sans Android, donc testable. */
object PaletteFormats {

    /** Une couleur par ligne (`RRGGBB`, `#RRGGBB`, `AARRGGBB`), les lignes illisibles sont ignorées. */
    fun parseHexLines(text: String): List<Int> =
        text.lineSequence().mapNotNull { PixelColor.parseHex(it.substringBefore(';').trim()) }.distinct().toList()

    /** Palette GIMP (`.gpl`) : lignes `R G B nom` après l'en-tête. */
    fun parseGpl(text: String): List<Int> {
        val out = ArrayList<Int>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("GIMP") || line.startsWith("Name") ||
                line.startsWith("Columns")
            ) continue
            val p = line.split(Regex("\\s+"))
            if (p.size < 3) continue
            val r = p[0].toIntOrNull() ?: continue
            val g = p[1].toIntOrNull() ?: continue
            val b = p[2].toIntOrNull() ?: continue
            out.add(PixelColor.argb(255, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255)))
        }
        return out.distinct()
    }

    /** Détecte le format d'après le contenu : GPL s'il commence par l'en-tête GIMP, sinon une couleur par ligne. */
    fun parse(text: String): List<Int> =
        if (text.trimStart().startsWith("GIMP Palette")) parseGpl(text) else parseHexLines(text)

    fun toHexLines(colors: List<Int>): String =
        colors.joinToString("\n") { PixelColor.toHex(it).removePrefix("#") }

    /** Couleurs distinctes d'une image, opaques d'abord, par ordre d'apparition (au plus [max]). */
    fun uniqueColors(pixels: IntArray, max: Int = 256): List<Int> {
        val seen = LinkedHashSet<Int>()
        for (c in pixels) {
            if (c ushr 24 == 0) continue
            seen.add(c)
            if (seen.size >= max) break
        }
        return seen.toList()
    }
}

/** Les palettes (intégrées + celles de l'utilisateur), la courante, et les couleurs récentes. */
class PaletteStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("pixel_studio_prefs", Context.MODE_PRIVATE)

    val builtIns: List<NamedPalette> = listOf(
        NamedPalette("builtin_default", "", BASIC_16, true, R.string.px_palette_default),
        NamedPalette("builtin_endesga32", "Endesga 32", ENDESGA_32, true),
        NamedPalette("builtin_pico8", "PICO-8", PICO_8, true),
        NamedPalette("builtin_sweetie16", "Sweetie 16", SWEETIE_16, true),
        NamedPalette("builtin_gameboy", "Game Boy", GAME_BOY, true),
        NamedPalette("builtin_grays", "", GRAYS, true, R.string.px_palette_grays),
    )

    private var users: MutableList<NamedPalette> = readUsers()

    fun all(): List<NamedPalette> = builtIns + users

    var currentId: String
        get() = prefs.getString(KEY_CURRENT, null)?.takeIf { id -> all().any { it.id == id } } ?: builtIns[0].id
        set(v) { prefs.edit().putString(KEY_CURRENT, v).apply() }

    fun current(): NamedPalette = all().first { it.id == currentId }

    fun create(name: String, colors: List<Int>): NamedPalette {
        val p = NamedPalette(UUID.randomUUID().toString().take(12), name, colors)
        users.add(p)
        writeUsers()
        return p
    }

    fun update(id: String, colors: List<Int>) {
        val i = users.indexOfFirst { it.id == id }
        if (i < 0) return
        users[i] = NamedPalette(id, users[i].name, colors)
        writeUsers()
    }

    fun rename(id: String, name: String) {
        val i = users.indexOfFirst { it.id == id }
        if (i < 0) return
        users[i] = NamedPalette(id, name, users[i].colors)
        writeUsers()
    }

    fun delete(id: String) {
        if (users.removeAll { it.id == id }) {
            if (currentId == id) currentId = builtIns[0].id
            writeUsers()
        }
    }

    // ---- Couleurs récentes ---------------------------------------------------------------------

    fun recent(): List<Int> {
        val s = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            List(a.length()) { a.getInt(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addRecent(color: Int) {
        val list = recent().toMutableList()
        list.remove(color)
        list.add(0, color)
        while (list.size > MAX_RECENT) list.removeAt(list.size - 1)
        prefs.edit().putString(KEY_RECENT, JSONArray(list).toString()).apply()
    }

    // ---- Stockage ----------------------------------------------------------------------------------

    private fun readUsers(): MutableList<NamedPalette> {
        val out = ArrayList<NamedPalette>()
        val s = prefs.getString(KEY_USERS, null) ?: return out
        try {
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val cols = o.getJSONArray("colors")
                out.add(NamedPalette(o.getString("id"), o.getString("name"), List(cols.length()) { cols.getInt(it) }))
            }
        } catch (e: Exception) {
            // Des palettes illisibles ne doivent pas empêcher d'ouvrir l'éditeur.
        }
        return out
    }

    private fun writeUsers() {
        val a = JSONArray()
        for (p in users) a.put(JSONObject().put("id", p.id).put("name", p.name).put("colors", JSONArray(p.colors)))
        prefs.edit().putString(KEY_USERS, a.toString()).apply()
    }

    companion object {
        private const val KEY_USERS = "user_palettes"
        private const val KEY_CURRENT = "current_palette"
        private const val KEY_RECENT = "recent_colors"
        const val MAX_RECENT = 14

        private fun hex(vararg v: Int): List<Int> = v.map { it or (0xFF shl 24) }

        val BASIC_16 = hex(
            0x000000, 0xFFFFFF, 0x7F7F7F, 0xC8C8C8, 0xC80000, 0x00C800, 0x0000C8, 0xC8C800,
            0xC800C8, 0x00C8C8, 0xC86400, 0x8B4513, 0xFFC0CB, 0x800080, 0x008000, 0xFFD700,
        )
        val ENDESGA_32 = hex(
            0xBE4A2F, 0xD77643, 0xEAD4AA, 0xE4A672, 0xB86F50, 0x733E39, 0x3E2731, 0xA22633,
            0xE43B44, 0xF77622, 0xFEAE34, 0xFEE761, 0x63C74D, 0x3E8948, 0x265C42, 0x193C3E,
            0x124E89, 0x0099DB, 0x2CE8F5, 0xFFFFFF, 0xC0CBDC, 0x8B9BB4, 0x5A6988, 0x3A4466,
            0x262B44, 0x181425, 0xFF0044, 0x68386C, 0xB55088, 0xF6757A, 0xE8B796, 0xC28569,
        )
        val PICO_8 = hex(
            0x000000, 0x1D2B53, 0x7E2553, 0x008751, 0xAB5236, 0x5F574F, 0xC2C3C7, 0xFFF1E8,
            0xFF004D, 0xFFA300, 0xFFEC27, 0x00E436, 0x29ADFF, 0x83769C, 0xFF77A8, 0xFFCCAA,
        )
        val SWEETIE_16 = hex(
            0x1A1C2C, 0x5D275D, 0xB13E53, 0xEF7D57, 0xFFCD75, 0xA7F070, 0x38B764, 0x257179,
            0x29366F, 0x3B5DC9, 0x41A6F6, 0x73EFF7, 0xF4F4F4, 0x94B0C2, 0x566C86, 0x333C57,
        )
        val GAME_BOY = hex(0x0F380F, 0x306230, 0x8BAC0F, 0x9BBC0F)
        val GRAYS = hex(
            0x000000, 0x111111, 0x222222, 0x333333, 0x444444, 0x555555, 0x666666, 0x777777,
            0x888888, 0x999999, 0xAAAAAA, 0xBBBBBB, 0xCCCCCC, 0xDDDDDD, 0xEEEEEE, 0xFFFFFF,
        )
    }
}
