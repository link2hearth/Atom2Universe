package com.Atom2Universe.app.audioeditor.io

import com.Atom2Universe.app.audioeditor.core.Clip
import com.Atom2Universe.app.audioeditor.core.EnvPoint
import com.Atom2Universe.app.audioeditor.core.Fade
import com.Atom2Universe.app.audioeditor.core.FadeShape
import com.Atom2Universe.app.audioeditor.core.Marker
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.Track
import org.json.JSONArray
import org.json.JSONObject

/** L'état de l'écran, gardé avec le projet pour rouvrir l'éditeur là où on l'avait laissé. */
data class ViewState(
    val cursor: Long = 0,
    val selStart: Long = 0,
    val selEnd: Long = 0,
    /** Zoom horizontal en trames par pixel ; 0 = laisser l'écran choisir. */
    val framesPerPixel: Double = 0.0,
    val scrollFrame: Long = 0,
    val activeTrack: Int = 0,
    val snap: Boolean = true,
    /** Les pistes montrées en spectrogramme plutôt qu'en forme d'onde. */
    val spectrogramTracks: List<Int> = emptyList(),
)

/** Ce qui décrit un projet sans être de l'audio : nom, dates, état de l'écran. */
data class ProjectMeta(
    val name: String,
    val created: Long,
    val modified: Long,
    val view: ViewState = ViewState(),
)

class LoadedProject(val id: String, val project: Project, val meta: ProjectMeta)

/** Une ligne de la galerie : lue dans le manifeste sans décoder tout le projet. */
data class ProjectSummary(
    val id: String,
    val name: String,
    val sampleRate: Int,
    val trackCount: Int,
    val frames: Long,
    val created: Long,
    val modified: Long,
) {
    val seconds: Double get() = if (sampleRate > 0) frames.toDouble() / sampleRate else 0.0
}

/**
 * Le manifeste JSON d'un projet : tout le modèle immuable ([Project]) plus les [ProjectMeta].
 *
 * Versionné (`v`) et tolérant : les champs inconnus sont ignorés, les champs absents prennent leur
 * valeur par défaut. En revanche un manifeste **dangereux** est refusé à la lecture (une source dont
 * le chemin sort de `sources/`), puisqu'un fichier de projet peut venir d'un partage.
 */
object ManifestCodec {

    const val VERSION = 1

    /** Les seuls chemins de source admis : `sources/<identifiant>.wav`, sans sous-dossier ni `..`. */
    private val SAFE_SOURCE_FILE = Regex("sources/[A-Za-z0-9_-]+\\.wav")
    private val SAFE_ID = Regex("[A-Za-z0-9_-]+")

    private fun d(v: Float, default: Double): Double = if (v.isFinite()) v.toDouble() else default

    private fun encodeFade(f: Fade) = JSONObject()
        .put("len", f.len).put("from", d(f.from, 0.0)).put("to", d(f.to, 1.0)).put("shape", f.shape.name)

    private fun decodeFade(j: JSONObject?): Fade {
        if (j == null) return Fade.NONE
        val len = j.optLong("len", 0)
        if (len <= 0) return Fade.NONE
        return Fade(
            len,
            j.optDouble("from", 0.0).toFloat(),
            j.optDouble("to", 1.0).toFloat(),
            runCatching { FadeShape.valueOf(j.optString("shape", "LINEAR")) }.getOrDefault(FadeShape.LINEAR),
        )
    }

    fun encode(p: Project, meta: ProjectMeta): JSONObject {
        val sources = JSONArray()
        for (s in p.sources.values.sortedBy { it.id }) {
            sources.put(
                JSONObject().put("id", s.id).put("file", s.file).put("ch", s.channels)
                    .put("frames", s.frames).put("rate", s.sampleRate).put("float", s.float),
            )
        }
        val tracks = JSONArray()
        for (t in p.tracks) {
            val clips = JSONArray()
            for (c in t.clips) {
                val cj = JSONObject().put("id", c.id).put("src", c.sourceId).put("srcStart", c.srcStart)
                    .put("len", c.length).put("start", c.start).put("gain", d(c.gain, 1.0))
                if (!c.fadeIn.isNone) cj.put("fadeIn", encodeFade(c.fadeIn))
                if (!c.fadeOut.isNone) cj.put("fadeOut", encodeFade(c.fadeOut))
                if (c.reversed) cj.put("rev", true)
                if (c.name.isNotEmpty()) cj.put("name", c.name)
                clips.put(cj)
            }
            val env = JSONArray()
            for (e in t.envelope) env.put(JSONArray().put(e.frame).put(d(e.gain, 1.0)))
            tracks.put(
                JSONObject().put("id", t.id).put("name", t.name).put("color", t.color)
                    .put("vol", d(t.volume, 1.0)).put("pan", d(t.pan, 0.0))
                    .put("mute", t.mute).put("solo", t.solo).put("locked", t.locked)
                    .put("clips", clips).put("env", env),
            )
        }
        val markers = JSONArray()
        for (m in p.markers) {
            markers.put(JSONObject().put("id", m.id).put("pos", m.pos).put("end", m.end).put("name", m.name))
        }
        val v = meta.view
        return JSONObject()
            .put("v", VERSION)
            .put("name", meta.name).put("created", meta.created).put("modified", meta.modified)
            // Résumé pour la galerie : évite de décoder tout le projet pour afficher une carte.
            .put("length", p.length).put("trackCount", p.tracks.size)
            .put("rate", p.sampleRate).put("master", d(p.master, 1.0)).put("nextId", p.nextId)
            .put(
                "view",
                JSONObject().put("cursor", v.cursor).put("selStart", v.selStart).put("selEnd", v.selEnd)
                    .put("fpp", v.framesPerPixel).put("scroll", v.scrollFrame)
                    .put("activeTrack", v.activeTrack).put("snap", v.snap)
                    .put("spectro", JSONArray(v.spectrogramTracks)),
            )
            .put("sources", sources).put("tracks", tracks).put("markers", markers)
    }

    /** @throws org.json.JSONException ou IllegalArgumentException si le manifeste est inutilisable ou dangereux */
    fun decode(j: JSONObject): Pair<Project, ProjectMeta> {
        val rate = j.optInt("rate", 44100)
        require(rate in 1000..384000) { "fréquence invalide : $rate" }

        val sources = LinkedHashMap<String, Source>()
        val sa = j.optJSONArray("sources") ?: JSONArray()
        for (i in 0 until sa.length()) {
            val s = sa.getJSONObject(i)
            val id = s.getString("id")
            val file = s.getString("file")
            require(SAFE_ID.matches(id)) { "identifiant de source invalide" }
            require(SAFE_SOURCE_FILE.matches(file)) { "chemin de source invalide : $file" }
            val ch = s.getInt("ch")
            require(ch in 1..2) { "nombre de canaux invalide : $ch" }
            sources[id] = Source(id, file, ch, s.getLong("frames"), s.optInt("rate", rate), s.optBoolean("float", false))
        }

        var maxId = 0
        val tracks = ArrayList<Track>()
        val ta = j.optJSONArray("tracks") ?: JSONArray()
        for (i in 0 until ta.length()) {
            val t = ta.getJSONObject(i)
            val clips = ArrayList<Clip>()
            val ca = t.optJSONArray("clips") ?: JSONArray()
            for (k in 0 until ca.length()) {
                val c = ca.getJSONObject(k)
                val len = c.getLong("len")
                val srcStart = c.optLong("srcStart", 0)
                // Un clip vide ou à l'envers ne jouerait rien : on ne le garde pas.
                if (len <= 0 || srcStart < 0) continue
                val id = c.getInt("id")
                maxId = maxOf(maxId, id)
                clips.add(
                    Clip(
                        id = id, sourceId = c.getString("src"), srcStart = srcStart, length = len,
                        start = c.optLong("start", 0).coerceAtLeast(0),
                        gain = c.optDouble("gain", 1.0).toFloat().takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 1f,
                        fadeIn = decodeFade(c.optJSONObject("fadeIn")),
                        fadeOut = decodeFade(c.optJSONObject("fadeOut")),
                        reversed = c.optBoolean("rev", false),
                        name = c.optString("name", ""),
                    ),
                )
            }
            val env = ArrayList<EnvPoint>()
            val ea = t.optJSONArray("env") ?: JSONArray()
            for (k in 0 until ea.length()) {
                val e = ea.getJSONArray(k)
                env.add(EnvPoint(e.getLong(0), e.getDouble(1).toFloat()))
            }
            val tid = t.getInt("id")
            maxId = maxOf(maxId, tid)
            tracks.add(
                Track(
                    id = tid, name = t.optString("name", ""), color = t.optInt("color", 0),
                    volume = t.optDouble("vol", 1.0).toFloat(), pan = t.optDouble("pan", 0.0).toFloat().coerceIn(-1f, 1f),
                    mute = t.optBoolean("mute", false), solo = t.optBoolean("solo", false),
                    locked = t.optBoolean("locked", false),
                    clips = emptyList(), envelope = env.sortedBy { it.frame },
                ).withClips(clips),
            )
        }

        val markers = ArrayList<Marker>()
        val ma = j.optJSONArray("markers") ?: JSONArray()
        for (i in 0 until ma.length()) {
            val m = ma.getJSONObject(i)
            val pos = m.getLong("pos")
            val id = m.getInt("id")
            maxId = maxOf(maxId, id)
            markers.add(Marker(id, pos, maxOf(pos, m.optLong("end", pos)), m.optString("name", "")))
        }

        val project = Project(
            sampleRate = rate, tracks = tracks, markers = markers, sources = sources,
            master = j.optDouble("master", 1.0).toFloat().takeIf { it.isFinite() } ?: 1f,
            nextId = maxOf(j.optInt("nextId", 1), maxId + 1),
        )
        val vj = j.optJSONObject("view")
        val view = if (vj == null) ViewState() else ViewState(
            cursor = vj.optLong("cursor", 0), selStart = vj.optLong("selStart", 0), selEnd = vj.optLong("selEnd", 0),
            framesPerPixel = vj.optDouble("fpp", 0.0).takeIf { it.isFinite() } ?: 0.0,
            scrollFrame = vj.optLong("scroll", 0), activeTrack = vj.optInt("activeTrack", 0),
            snap = vj.optBoolean("snap", true),
            spectrogramTracks = vj.optJSONArray("spectro")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList(),
        )
        val meta = ProjectMeta(j.optString("name", ""), j.optLong("created"), j.optLong("modified"), view)
        return project to meta
    }
}
