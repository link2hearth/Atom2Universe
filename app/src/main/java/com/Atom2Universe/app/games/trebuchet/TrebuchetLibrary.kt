package com.Atom2Universe.app.games.trebuchet

/**
 * Une machine mise de côté : un nom, et les réglages qui vont avec.
 *
 * Les réglages sont copiés, jamais partagés : une machine enregistrée est un
 * souvenir, et un souvenir ne bouge pas quand on continue de bricoler.
 */
class MachinePreset(name: String, config: MachineConfig) {

    /** Le nom, nettoyé de ce qui casserait le format d'enregistrement. */
    val name: String = clean(name)

    val config: MachineConfig = MachineConfig().apply { copyFrom(config) }

    companion object {
        /** Longueur maximale d'un nom. Au-delà, personne ne le lit dans un menu. */
        const val MAX_NAME = 24

        fun clean(raw: String): String =
            raw.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
                .trim().take(MAX_NAME)
    }
}

/**
 * L'atelier : la liste des machines enregistrées, et la façon de la ranger.
 *
 * **Tout ce qui est ici est du Kotlin ordinaire, et c'est délibéré.** L'écriture et la
 * lecture d'une liste de machines n'ont besoin ni d'Android ni d'un fichier : elles ont
 * besoin d'une chaîne de caractères. Le reste — la ranger dans les préférences,
 * l'afficher dans un menu — est l'affaire de l'activité, et ne se teste pas. Ce qui se
 * teste est ici : un aller-retour qui rend exactement ce qu'on lui a donné, et une
 * lecture qui ne s'effondre pas devant une ligne abîmée.
 *
 * Le format est une ligne par machine, des valeurs séparées par des tabulations :
 *
 *     nom ⇥ poutre ⇥ pied ⇥ levier ⇥ masse ⇥ chape ⇥ crochet ⇥ fronde ⇥ projectile ⇥ charge
 *
 * Ni JSON ni sérialisation : dix champs plats se relisent à l'œil dans un fichier de
 * préférences, se réparent à la main, et ne demandent aucune bibliothèque qu'il faudrait
 * ensuite doubler dans les tests.
 *
 * **Le dixième champ est arrivé après les neuf premiers, et la relecture en tient
 * compte.** Une ligne écrite avant que la charge de la bombe n'existe s'arrête au
 * projectile ; elle reste parfaitement valable, et se relit avec la charge par défaut —
 * laquelle a justement été choisie pour redonner la bombe d'avant. C'est le seul
 * compromis à tenir quand on ajoute un réglage à un jeu déjà joué : le format grandit
 * par la droite, et le seuil de rejet ne bouge pas.
 */
object MachineLibrary {

    /**
     * Nombre maximal de machines gardées.
     *
     * Un menu déroulant de cent entrées n'est plus un menu. Au-delà, la plus ancienne
     * s'en va — et comme enregistrer sous un nom déjà pris remplace, on ne perd que ce
     * qu'on a vraiment cessé d'utiliser.
     */
    const val MAX_PRESETS = 30

    private const val SEP = '\t'

    fun encode(list: List<MachinePreset>): String = buildString {
        for (p in list) {
            val c = p.config
            append(p.name).append(SEP)
            append(c.beamLength).append(SEP)
            append(c.pivotHeight).append(SEP)
            append(c.leverRatio).append(SEP)
            append(c.counterweightMass).append(SEP)
            append(c.hangLength).append(SEP)
            append(c.pinAngleDeg).append(SEP)
            append(c.slingRatio).append(SEP)
            append(c.projectile.name).append(SEP)
            append(c.bombSticks)
            append('\n')
        }
    }

    /**
     * Relit une liste enregistrée.
     *
     * **Une ligne qu'on ne comprend pas est jetée, pas relevée en exception.** Ce texte
     * vient des préférences du téléphone : il a pu être écrit par une version plus
     * ancienne du jeu, tronqué par un arrêt brutal, ou modifié par curiosité. Perdre une
     * machine enregistrée est ennuyeux ; ne plus pouvoir ouvrir le jeu l'est beaucoup
     * plus.
     */
    fun decode(text: String): List<MachinePreset> {
        val out = ArrayList<MachinePreset>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val f = line.split(SEP)
            if (f.size < 9) continue
            val name = MachinePreset.clean(f[0])
            if (name.isEmpty()) continue
            val cfg = MachineConfig()
            cfg.beamLength = f[1].toFloatOrNull() ?: continue
            cfg.pivotHeight = f[2].toFloatOrNull() ?: continue
            cfg.leverRatio = f[3].toFloatOrNull() ?: continue
            cfg.counterweightMass = f[4].toFloatOrNull() ?: continue
            cfg.hangLength = f[5].toFloatOrNull() ?: continue
            cfg.pinAngleDeg = f[6].toFloatOrNull() ?: continue
            cfg.slingRatio = f[7].toFloatOrNull() ?: continue
            cfg.projectile = runCatching { Projectile.valueOf(f[8]) }.getOrDefault(Projectile.BOULET)
            // Le champ que les anciennes lignes n'ont pas : absent, il vaut la charge
            // par défaut, celle qui redonne la bombe telle qu'elle était.
            cfg.bombSticks = f.getOrNull(9)?.toIntOrNull() ?: Projectile.DEFAULT_STICKS
            // Une machine relue est bornée comme une machine réglée : rien ne garantit
            // que ce texte-là décrive quelque chose qui tienne debout.
            cfg.clamp()
            out.add(MachinePreset(name, cfg))
            if (out.size >= MAX_PRESETS) break
        }
        return out
    }

    /**
     * Range une machine dans la liste, la plus récente en tête.
     *
     * Un nom déjà pris **remplace** : c'est ce que veut dire enregistrer deux fois sous
     * le même nom, et c'est la seule façon de corriger une machine qu'on vient de
     * mettre de côté sans en accumuler trois versions.
     */
    fun put(list: List<MachinePreset>, preset: MachinePreset): List<MachinePreset> {
        val out = ArrayList<MachinePreset>(list.size + 1)
        out.add(preset)
        for (p in list) if (!p.name.equals(preset.name, ignoreCase = true)) out.add(p)
        while (out.size > MAX_PRESETS) out.removeAt(out.size - 1)
        return out
    }

    fun remove(list: List<MachinePreset>, name: String): List<MachinePreset> =
        list.filterNot { it.name.equals(name, ignoreCase = true) }
}
