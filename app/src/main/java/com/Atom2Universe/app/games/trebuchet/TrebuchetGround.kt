package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld

/**
 * **Le sol du monde : une seule façon de le poser, pour toutes les machines.**
 *
 * Le dossier `trebuchet` contient deux jeux — le trébuchet et l'atelier d'engrenages —
 * et le sol n'appartient ni à l'un ni à l'autre : c'est une propriété du *site*, pas de
 * la machine qui tire dessus. Il était pourtant bâti deux fois, de deux façons
 * différentes, et c'est cette divergence-là qui a produit les bugs de sol du jeu.
 *
 * La règle tient en une phrase : **le sol physique, c'est le profil du relief prolongé à
 * plat jusqu'aux bornes du monde.** Rien d'autre. Ce qui vaut la peine d'être dit, c'est
 * pourquoi ce n'était pas ça.
 *
 * ### La fausse piste : la dalle de quarante kilomètres
 *
 * L'atelier posait, *en plus* du relief, une dalle plate de quarante kilomètres qui
 * portait la machine et fermait le monde de part et d'autre du site — parce que le
 * relief, lui, ne s'étend que de [TrebuchetRules.GROUND_LEFT] à
 * [TrebuchetRules.GROUND_RIGHT], alors que le monde de l'atelier fait ±20 km.
 *
 * Cette dalle a d'abord eu sa face supérieure à l'altitude zéro. Elle passait donc
 * **au-dessus** d'un site de vallon posé huit mètres plus bas : c'était la dalle qui
 * portait le village pendant que le décor se dessinait sur le vrai profil, et le joueur
 * voyait ses bâtiments flotter huit mètres en l'air. On a corrigé en calant la dalle sur
 * `min(0, terrain.lowest)`. Le symptôme visible a disparu, et le défaut de fond est
 * resté : **une dalle plate ne peut pas être d'accord avec un relief qui ne l'est pas.**
 *
 * Mesuré en lâchant une bille à six postes fixes du monde, sur les quarante premières
 * graines en style JEU, et en comparant **où elle se pose** à ce que `terrain.heightAt`
 * annonce au même endroit :
 *
 * ```
 * avant : 28,78 m d'écart (graine 27 en x=1200 : annoncé 27,23 m, posée à -1,55 m)
 * après :  0,01 m         (l'enfoncement au repos d'un contact, rien d'autre)
 * ```
 *
 * Vingt-huit mètres, et ce n'est pas cosmétique : `GearMachineGame.trackShot` décide
 * qu'un boulet a atterri en comparant sa hauteur à `terrain.heightAt(body.x)`. Un tir
 * qui dépassait le relief était donc déclaré posé **avant de toucher**, et sa portée —
 * la seule note que rend l'atelier — annoncée d'autant plus courte, sans que rien ne le
 * signale.
 *
 * ### Ce qu'on fait à la place
 *
 * On prolonge le profil ([Terrain.extended]) jusqu'aux bornes du monde et on en fait des
 * corps. Le sol physique **est** `heightAt`, partout, par construction : il n'y a plus
 * deux sources à tenir d'accord.
 *
 * Et ça ne coûte rien. Prolonger n'ajoute que deux nœuds aux extrémités, dont la
 * simplification du profil efface souvent l'ancien bord devenu inutile ; la dalle
 * disparaît en échange. Mieux : l'ancienne dalle allait de -20 km à +20 km, donc son
 * bord gauche la plaçait en tête du balayage par bord gauche du moteur et son bord
 * droit ne laissait jamais la boucle s'arrêter — elle était testée contre *tous* les
 * corps du monde. Les deux prolongements, eux, ne recouvrent pas le site.
 *
 * Gardé par `GearMachineSiteTest` : « le sol suit le relief jusqu au bout du monde »
 * refait exactement la mesure ci-dessus, et « un site en contrebas ne remonte pas a l
 * altitude zero » garde le premier bug, celui de la dalle calée à zéro.
 */
object TrebuchetGround {

    /**
     * Pose le relief dans [world] et rend les corps créés, le plus à gauche en premier.
     *
     * @param leftX bord gauche du monde, où le profil est prolongé à plat.
     * @param rightX bord droit, idem.
     * @param friction la même pour tout le sol : une seule terre, un seul frottement.
     * @param stamp de quoi tamponner chaque corps avant qu'il n'entre dans le monde.
     *   L'atelier s'en sert pour ses couches de collision — ses engrenages vivent sur
     *   des étages numérotés, et le sol doit les traverser tous. Le trébuchet, qui n'a
     *   pas de couches, ne passe rien.
     */
    fun lay(
        world: PhysWorld,
        terrain: Terrain,
        leftX: Float,
        rightX: Float,
        friction: Float = 0.55f,
        stamp: (PhysBody) -> Unit = {}
    ): List<PhysBody> {
        val bodies = terrain.extended(leftX, rightX).bodies(friction)
        for (b in bodies) {
            stamp(b)
            world.add(b)
        }
        return bodies
    }
}
