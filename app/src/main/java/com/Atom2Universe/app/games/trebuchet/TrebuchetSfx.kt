package com.Atom2Universe.app.games.trebuchet

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.billthefarmer.mididriver.MidiDriver
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * L'ambiance sonore du trébuchet — et de l'atelier, qui partage son décor.
 *
 * Deux sources, chacune pour ce qu'elle fait bien :
 * - **de vrais enregistrements** (`assets/trebuchet/audio/`) pour tout ce qui doit
 *   sonner comme de la matière : l'atelier (bois, cailloux, cordes, crochet), le tir,
 *   les villageois qui fuient, et le vent de fond en boucle ;
 * - **Sonivox EAS** (General MIDI) pour les chocs, les explosions et les feux
 *   d'artifice, qui sonnaient déjà juste — même patron que
 *   [com.Atom2Universe.app.games.caves.CaveSoundEngine] : le moteur natif est possédé
 *   en propre, démarré et arrêté avec la vue qui le porte.
 */
class TrebuchetSfx(private val context: Context) {

    private companion object {
        const val CH_PERC = 9        // Percussion GM : explosion, étincelles.
        const val CH_CRACK = 1       // Program 127 — Gunshot.
        const val CH_BREATH = 2      // Program 121 — Breath Noise.

        const val PITCH_CENTER = 8192

        const val AUDIO_DIR = "trebuchet/audio"
        const val AMBIENCE = "ambience_wind_birds"

        /** Le vent reste **derrière** : il meuble le silence, il ne couvre rien. */
        const val AMBIENCE_VOLUME = 0.35f

        /** Un seul cri de groupe à la fois : un village qui hurle en rafale lasse vite. */
        const val VILLAGERS_COOLDOWN_MS = 1500L
    }

    /**
     * Ce que le joueur règle dans l'atelier : chaque pièce a son bruit.
     *
     * Le [cooldownMs] est l'écart minimal entre deux sons du même outil. Un glissé
     * fluide ou une roulette qu'on fait défiler en appelleraient des dizaines par
     * seconde ; un coup de marteau supporte d'être répété vite, des cailloux qu'on
     * déverse non — on laisse le son finir avant d'en verser d'autres.
     */
    enum class Tool(val samples: Array<String>, val cooldownMs: Long) {
        /** Longueur de la poutre, hauteur du poteau, projectile, charge, poids. */
        HAMMER(arrayOf("hammer_0", "hammer_1", "hammer_2"), 180L),
        /** Le bras court : la poutre coulisse dans son logement. */
        SLIDE(arrayOf("beam_slide"), 900L),
        /** La masse du contrepoids : on y verse des pierres. */
        STONES(arrayOf("stones_pour"), 1000L),
        /** La suspension du contrepoids : la corde se tend sous la caisse. */
        ROPE_HANG(arrayOf("rope_hang"), 900L),
        /** L'angle du crochet de largage. */
        HOOK(arrayOf("hook_click"), 250L),
        /** La longueur de la fronde : on refait le nœud. */
        ROPE_TIE(arrayOf("rope_tie"), 900L)
    }

    /** Coupe tous les bruitages, vent compris, sans arrêter les moteurs — le réglage du menu. */
    var enabled = true
        set(value) {
            field = value
            updateAmbience()
        }

    private var driver: MidiDriver? = null
    private var ready = false
    private var scope: CoroutineScope? = null
    private val rng = Random(System.nanoTime())

    /** Les toms graves de la table de percussion : le registre d'un éboulement. */
    private val TOMS = intArrayOf(41, 43, 45, 47)

    // Les enregistrements. Le fil de physique (cris) et celui de l'interface (atelier)
    // jouent tous les deux : d'où les tables concurrentes.
    @Volatile private var pool: SoundPool? = null
    private val pending = ConcurrentHashMap<Int, String>()
    private val samples = ConcurrentHashMap<String, Int>()
    private val lastPlayed = ConcurrentHashMap<String, Long>()
    private var ambience: MediaPlayer? = null

    fun start() {
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val d = MidiDriver.getInstance {
            ready = true
            programChange(CH_CRACK, 127)
            programChange(CH_BREATH, 121)
        }
        driver = d
        d.start()
        loadSamples()
        startAmbience()
    }

    fun stop() {
        ready = false
        scope?.cancel()
        scope = null
        driver?.stop()
        driver = null
        pool?.release()
        pool = null
        pending.clear()
        samples.clear()
        lastPlayed.clear()
        ambience?.release()
        ambience = null
    }

    private fun loadSamples() {
        val sp = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        // Un son n'est jouable qu'une fois décodé : jusque-là, on se tait plutôt que
        // de demander à SoundPool un identifiant qu'il ne connaît pas encore.
        sp.setOnLoadCompleteListener { source, id, status ->
            val name = pending.remove(id)
            if (source === pool && status == 0 && name != null) samples[name] = id
        }
        pool = sp
        val names = Tool.entries.flatMap { it.samples.asList() } + listOf("launch", "villagers_flee")
        for (name in names) {
            try {
                context.assets.openFd("$AUDIO_DIR/$name.ogg").use { pending[sp.load(it, 1)] = name }
            } catch (e: Exception) {
                Log.w("TrebuchetSfx", "Cannot load $name", e)
            }
        }
    }

    /** Le vent et les oiseaux, en boucle : un enregistrement fait pour se répéter sans couture. */
    private fun startAmbience() {
        try {
            ambience = MediaPlayer().apply {
                context.assets.openFd("$AUDIO_DIR/$AMBIENCE.ogg").use {
                    setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                isLooping = true
                setVolume(AMBIENCE_VOLUME, AMBIENCE_VOLUME)
                prepare()
            }
        } catch (e: Exception) {
            Log.w("TrebuchetSfx", "Cannot start ambience", e)
            ambience?.release()
            ambience = null
        }
        updateAmbience()
    }

    private fun updateAmbience() {
        val player = ambience ?: return
        if (enabled && !player.isPlaying) player.start()
        else if (!enabled && player.isPlaying) player.pause()
    }

    // ── Bruitages ────────────────────────────────────────────────────────────

    /**
     * Le bruit d'atelier de la pièce qu'on règle. Appelable à chaque image d'un
     * glissé : c'est ici, et non chez l'appelant, que la cadence est tenue.
     */
    fun edit(tool: Tool) {
        val name = tool.samples[rng.nextInt(tool.samples.size)]
        // La cadence est celle de l'outil, pas du fichier : les trois marteaux se
        // partagent la leur, sinon un glissé les ferait sonner ensemble.
        play(name, 0.8f, 0.94f + rng.nextFloat() * 0.12f, tool.name, tool.cooldownMs)
    }

    /** Le départ du tir : le bois qui grince, la corde qui claque, le bras qui fouette. */
    fun launch() = play("launch", 0.9f)

    /** Les villageois qui détalent en criant. */
    fun villagerCry() =
        play("villagers_flee", 0.75f, 0.92f + rng.nextFloat() * 0.16f, cooldownMs = VILLAGERS_COOLDOWN_MS)

    /**
     * Une pierre qui cède : **un choc mat et grave, jamais une explosion**.
     *
     * La différence n'est pas cosmétique. Le premier essai employait [explosion] — grosse
     * caisse, cymbale crash et craquement — pour sonner une assise qui se brise, et le
     * joueur a cru à un bug de simulation : « le boulet est explosif on dirait, ça cogne
     * et ça explose contre le bois ». La physique était juste ; c'est le son qui
     * racontait autre chose.
     *
     * Ici, un tom grave tiré au sort et un craquement court. De la matière qui cède, pas
     * de la poudre.
     */
    fun rubble() {
        if (!enabled) return
        val tom = TOMS[rng.nextInt(TOMS.size)]
        note(CH_PERC, tom, 52 + rng.nextInt(34))
        noteOnOffDelayed(CH_CRACK, 46 + rng.nextInt(6), 40 + rng.nextInt(26), 130L)
    }

    /** Le boulet — ou la bombe — qui rend tout d'un coup. */
    fun explosion() {
        if (!enabled) return
        note(CH_PERC, 36, 127)   // Grosse caisse.
        note(CH_PERC, 49, 110)   // Cymbale crash.
        noteOnOffDelayed(CH_CRACK, 60, 120, 320L)
        scope?.launch { delay(60L); note(CH_PERC, 45, 90) }   // Traîne grave.
    }

    /** Une fusée qui part : un souffle qui monte. */
    fun fireworkLaunch() {
        if (!enabled) return
        val note = 58 + rng.nextInt(6)
        note(CH_BREATH, note, 55 + rng.nextInt(20))
        scope?.launch {
            var bend = PITCH_CENTER - 3000
            pitchBend(CH_BREATH, bend)
            repeat(5) {
                delay(30L)
                bend += 600
                pitchBend(CH_BREATH, bend.coerceAtMost(16383))
            }
            noteOff(CH_BREATH, note)
            pitchBend(CH_BREATH, PITCH_CENTER)
        }
    }

    /** Un bouquet qui éclate : un crac, et une étincelle métallique. */
    fun fireworkBurst() {
        if (!enabled) return
        noteOnOffDelayed(CH_CRACK, 60, 65 + rng.nextInt(30), 260L)
        val accent = if (rng.nextBoolean()) 81 else 57
        note(CH_PERC, accent, 55 + rng.nextInt(45))
    }

    // ── Bas niveau ───────────────────────────────────────────────────────────

    /**
     * Joue un enregistrement, légèrement désaccordé par [rate] pour qu'un son répété
     * ne sonne pas comme une boucle. Muet tant qu'il n'est pas décodé.
     */
    private fun play(
        name: String, volume: Float, rate: Float = 1f,
        group: String = name, cooldownMs: Long = 0L
    ) {
        if (!enabled) return
        val sp = pool ?: return
        val id = samples[name] ?: return
        val now = SystemClock.uptimeMillis()
        val previous = lastPlayed[group]
        if (previous != null && now - previous < cooldownMs) return
        lastPlayed[group] = now
        sp.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    private fun noteOnOffDelayed(channel: Int, pitch: Int, velocity: Int, durationMs: Long) {
        note(channel, pitch, velocity)
        scope?.launch {
            delay(durationMs)
            noteOff(channel, pitch)
        }
    }

    private fun note(channel: Int, pitch: Int, velocity: Int) {
        if (!ready) return
        driver?.queueEvent(
            byteArrayOf(
                (0x90 or (channel and 0x0F)).toByte(),
                pitch.coerceIn(0, 127).toByte(),
                velocity.coerceIn(0, 127).toByte()
            )
        )
    }

    private fun noteOff(channel: Int, pitch: Int) {
        driver?.queueEvent(
            byteArrayOf(
                (0x80 or (channel and 0x0F)).toByte(),
                pitch.coerceIn(0, 127).toByte(),
                0
            )
        )
    }

    private fun programChange(channel: Int, program: Int) {
        driver?.queueEvent(
            byteArrayOf((0xC0 or (channel and 0x0F)).toByte(), program.coerceIn(0, 127).toByte())
        )
    }

    private fun pitchBend(channel: Int, value: Int) {
        val v = value.coerceIn(0, 16383)
        driver?.queueEvent(
            byteArrayOf(
                (0xE0 or (channel and 0x0F)).toByte(),
                (v and 0x7F).toByte(),
                ((v shr 7) and 0x7F).toByte()
            )
        )
    }
}
