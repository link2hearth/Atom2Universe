package com.Atom2Universe.app.games.physics

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Le monde physique : la liste des corps, celle des liaisons, et la boucle de
 * simulation.
 *
 * Le fonctionnement reprend celui de Box2D-Lite, en trois temps à chaque pas :
 *  1. **détection** des contacts (théorème de l'axe séparateur pour les boîtes,
 *     point le plus proche pour les disques) ;
 *  2. **résolution** des contacts et des liaisons par impulsions séquentielles
 *     (plusieurs passes qui corrigent tour à tour chaque point) ;
 *  3. **intégration** des positions.
 *
 * Les impulsions sont conservées d'une image à l'autre (« warm starting ») :
 * c'est ce qui rend une pile de briques stable au lieu de trembler.
 */
class PhysWorld {

    val bodies = ArrayList<PhysBody>()
    val joints = ArrayList<Joint>()
    /**
     * Les contacts suivis d'une image à l'autre.
     *
     * **Le rangement compte, et il doit être celui de l'insertion.** Les impulsions
     * séquentielles corrigent les contacts l'un après l'autre : changer leur ordre
     * change le résultat. Or la clé d'un contact est faite des identifiants des corps,
     * qui viennent d'un compteur global jamais remis à zéro — une table de hachage
     * ordinaire les range donc dans un ordre qui dépend du **nombre de corps créés
     * depuis le lancement de l'application**. Deux fois le même tir, dans la même
     * partie, ne donnaient pas le même effondrement : mesuré, deux mètres d'écart sur
     * une pierre.
     *
     * Rien de tout ça n'est visible dans un bac à sable, et tout le devient dès qu'un
     * niveau doit être reproductible à partir de sa graine. Une table à ordre d'insertion
     * range les contacts dans l'ordre où la détection les a trouvés, qui ne dépend que
     * de l'ordre des corps dans le monde.
     */
    private val arbiters = LinkedHashMap<Long, Arbiter>()
    private val fresh = Array(2) { Contact() }
    private val doomed = ArrayList<Long>()

    /**
     * Les contacts à résoudre, remis à plat dans l'ordre de la table.
     *
     * Le solveur parcourt la liste des contacts vingt-deux fois par pas — seize
     * passes de vitesses, six de positions, plus les mesures. Le faire sur la table
     * elle-même fabriquait vingt-deux itérateurs par pas, donc sept cents par image
     * de vol : rien de dramatique, mais c'est du travail pour le ramasse-miettes au
     * pire moment. Un tableau parcouru par indice n'alloue rien, et l'ordre reste
     * exactement celui de la table.
     */
    private val active = ArrayList<Arbiter>()

    /** Les liaisons à résoudre : celles dont au moins un corps est éveillé. */
    private val activeJoints = ArrayList<Joint>()

    // Tampons de la recherche de paires. Ils vivent avec le monde plutôt que dans la
    // méthode : un pas ne doit rien allouer du tout.
    private var aabbMinX = FloatArray(0)
    private var aabbMaxX = FloatArray(0)
    private var aabbMinY = FloatArray(0)
    private var aabbMaxY = FloatArray(0)
    private var sweepKeys = LongArray(0)
    private var pairBuf = LongArray(64)

    var gravity = 9.81f

    /** Nombre de passes du solveur de vitesses : plus il y en a, plus les piles sont stables. */
    var iterations = 14

    /**
     * Nombre de passes du solveur de **positions**.
     *
     * Le moteur résout séparément les vitesses et les positions. Les vitesses
     * empêchent les corps de s'enfoncer davantage ; les positions rattrapent
     * l'enfoncement déjà là, en poussant sur des vitesses fantômes qui ne servent
     * qu'au déplacement. C'est cette séparation qui empêche le solveur de créer de
     * l'énergie, et c'est ce qui permet à une chaîne de liaisons de tenir.
     */
    var positionIterations = 6

    /**
     * Vitesse d'approche à partir de laquelle un contact compte comme un choc :
     * en dessous, pas de rebond et pas de dégât. Sans ce seuil, une caisse posée
     * par terre s'abîmerait toute seule sous son propre poids et tremblerait.
     */
    var impactSpeedThreshold = 0.5f

    /**
     * Enfoncement que la passe de position laisse subsister, en mètres.
     *
     * Un solveur d'impulsions a besoin d'un peu de recouvrement pour savoir qu'il y a
     * contact : s'il séparait les corps jusqu'au contact exact, il passerait son temps
     * à osciller entre « touche » et « touche pas ». Cinq millimètres est la valeur
     * classique, et elle ne se voit pas sur une caisse posée par terre.
     *
     * Elle se voit en revanche sur une **pile**, où elle s'additionne : un mur de vingt
     * assises s'enfonce de dix centimètres dans lui-même, s'affaisse de travers, et
     * finit par s'écrouler tout seul. C'est la raison pour laquelle un jeu de cibles
     * empilées peut vouloir la resserrer, au prix de quelques passes de plus.
     */
    var allowedPenetration = 0.005f

    /** Nombre maximal de sous-pas consentis par image (voir [stepFrame]). */
    var maxSubSteps = 32

    /**
     * Mise en sommeil des corps immobiles.
     *
     * Un corps qui n'a plus bougé depuis [sleepDelay] s'endort : il n'est plus
     * intégré, ses contacts ne sont plus ni cherchés ni résolus, et il ne coûte plus
     * que la mise à jour de sa boîte englobante. Il se réveille dès qu'un corps
     * éveillé le touche pour de bon, ou qu'on le lui demande.
     *
     * C'est la seule façon de rendre une cible empilée abordable. Le coût d'une image
     * vaut le nombre de corps **actifs** multiplié par le nombre de sous-pas, et le
     * nombre de sous-pas est dicté par le corps le plus rapide du monde entier : un
     * boulet à cent trente mètres par seconde force trente-deux sous-pas, et les
     * soixante-dix pierres d'un château étaient résolues à chacun **alors qu'aucune
     * ne bougeait**. Mesuré : 7,3 ms par image de simulation, contre 0,2 une fois les
     * pierres endormies.
     *
     * Le réglage est volontairement à l'arrêt par défaut : un jeu où le joueur pose
     * des pièces à la main n'y gagnerait rien et pourrait s'y perdre.
     */
    var sleepEnabled = false

    /** Vitesse en dessous de laquelle un corps est candidat au sommeil, en m/s. */
    var sleepLinearTol = 0.04f

    /** Rotation en dessous de laquelle un corps est candidat au sommeil, en rad/s. */
    var sleepAngularTol = 0.06f

    /**
     * Durée d'immobilité exigée avant l'endormissement, en secondes.
     *
     * Elle ne se compte pas en pas mais en temps : un pas peut valoir huit
     * millisecondes ou deux cent cinquante microsecondes selon ce qui vole.
     */
    var sleepDelay = 0.4f

    /**
     * Amortissement ambiant, en fraction de vitesse perdue **par seconde**.
     *
     * Il ne représente rien de physique : c'est une petite friction numérique qui
     * aide un tas de caisses à finir par se taire, pour que [isAtRest] puisse
     * déclarer la fin d'un coup. L'air, lui, se modélise pour de bon avec
     * [PhysBody.dragFactor].
     *
     * L'unité compte. C'était autrefois un facteur appliqué **par pas** (0,999),
     * réglé du temps où une image valait un pas. Depuis les sous-pas adaptatifs, une
     * image de jeu peut valoir trente-deux pas : le même facteur freinait alors
     * trente-deux fois plus. Un boulet de trébuchet en vol perdait 38 % de sa vitesse
     * par seconde et retombait à la moitié de sa portée — un frottement fantôme, dont
     * l'intensité dépendait de la vitesse de la simulation elle-même.
     */
    var linearDamping = 0.05f
    var angularDamping = 0.3f

    private var linearKeep = 1f
    private var angularKeep = 1f

    private var stamp = 0

    fun add(body: PhysBody) {
        bodies.add(body)
    }

    fun remove(body: PhysBody) {
        bodies.remove(body)
        wakeNeighbours(body)
        forgetContacts(body)
        joints.removeAll { it.a === body || it.b === body }
    }

    /**
     * Réveille ce qui touchait un corps qu'on retire du monde.
     *
     * Sans ça, une pierre reste **suspendue en l'air** à la place de celle qui vient
     * d'éclater sous elle : un corps endormi n'est plus intégré du tout, donc il ne
     * retombe pas.
     *
     * Deux chemins, et il en faut deux. Les contacts mémorisés donnent les voisins
     * d'un corps qui bougeait encore — c'est le cas ordinaire, une pierre qui casse
     * sous un coup. Mais deux dormeurs n'ont plus de contact mémorisé du tout, par
     * construction : le moteur ne cherche pas les contacts entre deux corps qui
     * dorment. D'où le second chemin, le voisinage géométrique, qui coûte un
     * parcours de la liste des corps — une misère, puisqu'on ne retire un corps que
     * lorsqu'il casse.
     */
    private fun wakeNeighbours(body: PhysBody) {
        for (arb in arbiters.values) {
            if (arb.a === body) arb.b.wake() else if (arb.b === body) arb.a.wake()
        }
        body.updateAabb()
        val marge = 0.05f
        for (bd in bodies) {
            if (!bd.sleeping) continue
            if (bd.aabbMinX > body.aabbMaxX + marge || bd.aabbMaxX < body.aabbMinX - marge) continue
            if (bd.aabbMinY > body.aabbMaxY + marge || bd.aabbMaxY < body.aabbMinY - marge) continue
            bd.wake()
        }
    }

    /** Réveille tout le monde : à faire quand le jeu rebâtit sa scène. */
    fun wakeAll() {
        for (bd in bodies) bd.wake()
    }

    fun addJoint(joint: Joint) {
        joints.add(joint)
    }

    fun removeJoint(joint: Joint) {
        joints.remove(joint)
    }

    fun clear() {
        bodies.clear()
        joints.clear()
        arbiters.clear()
        active.clear()
        activeJoints.clear()
    }

    /** Oublie les contacts mémorisés d'un corps (à faire quand on le téléporte). */
    fun forgetContacts(body: PhysBody) {
        doomed.clear()
        for ((k, arb) in arbiters) if (arb.a === body || arb.b === body) doomed.add(k)
        for (k in doomed) arbiters.remove(k)
    }

    /** Remet à zéro les chocs encaissés par tous les corps. */
    fun clearImpacts() {
        for (bd in bodies) bd.impactAccum = 0f
    }

    /**
     * Simule une image entière, en la découpant en autant de sous-pas qu'il faut
     * pour que rien ne traverse rien.
     *
     * Le moteur teste les collisions à des positions figées : un boulet à 30 m/s
     * avance de 50 cm par image à 60 Hz, et passerait **au travers** d'une planche
     * de 10 cm sans jamais la toucher. On mesure donc le corps le plus rapide et
     * le corps le plus mince, et on subdivise le pas jusqu'à ce que le premier ne
     * puisse plus franchir le second d'un seul bond.
     *
     * Au repos, [maxSubSteps] n'est jamais atteint : un monde tranquille coûte un
     * seul sous-pas, exactement comme avant.
     */
    fun stepFrame(dt: Float) {
        if (dt <= 0f) return
        // On consomme l'image par tranches, en **recalculant la taille de la
        // tranche après chacune**. C'est indispensable dès qu'un choc entre en jeu :
        // le contrepoids d'une machine de jet frappe le bras au milieu de l'image et
        // le fait passer de zéro à trois tours par seconde. Un découpage décidé une
        // fois pour toutes au début de l'image aurait taillé les pas pour un bras
        // immobile, et le reste de l'image se serait joué à pleine vitesse avec des
        // pas énormes — le boulet traversait sa butée.
        var remaining = dt
        var guard = 0
        while (remaining > 1e-6f && guard < 4 * maxSubSteps) {
            val h = minOf(remaining, safeStep(dt))
            step(h)
            remaining -= h
            guard++
        }
    }

    /**
     * Durée pendant laquelle, à l'état actuel, rien ne peut franchir la plus petite
     * épaisseur du monde.
     *
     * La vitesse retenue est celle du **point le plus rapide** de chaque corps,
     * rotation comprise : le centre d'un bras de douze mètres avance lentement
     * pendant que son extrémité file à vingt mètres par seconde.
     */
    private fun safeStep(frameDt: Float): Float {
        var fastest = 0f
        var thinnest = Float.MAX_VALUE
        for (bd in bodies) {
            if (!bd.inWorld) continue
            // Un corps que personne ne peut toucher n'a pas à imposer son épaisseur :
            // l'axe d'une machine de jet fait huit centimètres et ne sert qu'à porter
            // une liaison, mais il faisait découper l'image en douze sous-pas pour
            // que rien ne le traverse — alors que rien ne peut le traverser.
            if (bd.collidesWith != 0 && bd.smallestHalfExtent < thinnest) {
                thinnest = bd.smallestHalfExtent
            }
            if (!bd.sleeping && (bd.invMass > 0f || bd.invI > 0f)) {
                val s = sqrt(bd.speedSq) + abs(bd.omega) * bd.boundingRadius
                if (s > fastest) fastest = s
            }
        }
        if (thinnest == Float.MAX_VALUE || fastest <= 0f) return frameDt
        // Marge de deux : deux corps peuvent se croiser en sens contraire, et leur
        // rapprochement vaut alors la somme de leurs vitesses.
        val safe = thinnest * 0.5f / fastest
        // Le plancher garantit que l'image finit toujours par être consommée, même
        // face à une vitesse aberrante.
        return safe.coerceIn(frameDt / maxSubSteps, frameDt)
    }

    /** Nombre de sous-pas que [stepFrame] emploierait pour une image de [dt]. */
    fun subStepsFor(dt: Float): Int =
        ceil(dt / safeStep(dt)).toInt().coerceIn(1, maxSubSteps)

    fun step(dt: Float) {
        if (dt <= 0f) return
        val invDt = 1f / dt
        stamp++

        // L'amortissement est donné par seconde : c'est ici qu'il devient un facteur
        // pour ce pas-ci, quelle que soit sa durée.
        linearKeep = (1f - linearDamping * dt).coerceIn(0f, 1f)
        angularKeep = (1f - angularDamping * dt).coerceIn(0f, 1f)

        broadPhase()
        selectJoints()

        // 1. Intégration des forces
        for (bd in bodies) {
            if (!bd.inWorld) continue
            // Un couple posé de l'extérieur — le ressort d'une planche — est un ordre
            // de bouger : il réveille son corps, sans quoi il serait avalé sans effet.
            if (bd.torque != 0f) bd.wake()
            if (bd.sleeping) { bd.torque = 0f; continue }
            if (bd.invMass > 0f) {
                bd.vy -= gravity * dt
                // La traînée de l'air : elle s'oppose au mouvement et croît comme
                // le carré de la vitesse. On la borne à ce qui annule exactement la
                // vitesse dans le pas : sinon un pas un peu long la renverserait et
                // l'air pousserait le corps en arrière, ce qui créerait de l'énergie.
                if (bd.dragFactor > 0f) {
                    val v = sqrt(bd.speedSq)
                    if (v > 1e-4f) {
                        val dv = minOf(bd.dragFactor * v * v * bd.invMass * dt, v)
                        bd.vx -= dv * bd.vx / v
                        bd.vy -= dv * bd.vy / v
                    }
                }
            }
            if (bd.invI > 0f && bd.torque != 0f) bd.omega += bd.invI * bd.torque * dt
            bd.torque = 0f
        }

        // 2. Vitesses : on empêche les corps de s'enfoncer davantage.
        // On **mesure d'abord tous les contacts, puis on les prépare** : la reprise des
        // impulsions de l'image précédente bouscule les vitesses le temps d'un pas, et
        // un contact mesuré après elle croirait à un choc violent là où rien ne bouge.
        // Voir [Arbiter.measure].
        for (i in active.indices) active[i].measure(impactSpeedThreshold)
        for (i in active.indices) active[i].preStep(invDt)
        for (i in activeJoints.indices) activeJoints[i].preStep(invDt)
        repeat(iterations) {
            for (i in active.indices) active[i].applyImpulse()
            // Les liaisons sont résolues deux fois par passe : une chaîne de corps
            // reliés fait circuler l'effort de proche en proche, et c'est le maillon
            // le plus lent qui décide de la stabilité de l'ensemble.
            for (i in activeJoints.indices) activeJoints[i].applyImpulse()
            for (i in activeJoints.indices) activeJoints[i].applyImpulse()
        }

        // 2 bis. Comptabilisation des chocs, pour les cibles qui doivent casser.
        for (i in active.indices) {
            val arb = active[i]
            if (!arb.impacting) continue
            // L'énergie du **choc seul**, sans le poids que le contact porte par
            // ailleurs : voir [Arbiter.impactEnergy].
            val p = arb.impactEnergy
            arb.a.impactAccum += p
            arb.b.impactAccum += p
        }

        // 2 ter. Positions : on rattrape ce qui est déjà enfoncé ou décroché, sur
        // les vitesses fantômes, qui ne donnent d'élan à personne.
        for (bd in bodies) {
            if (bd.frozen) continue
            bd.pvx = 0f; bd.pvy = 0f; bd.pomega = 0f
        }
        repeat(positionIterations) {
            for (i in active.indices) active[i].applyPositionImpulse(allowedPenetration)
            for (i in activeJoints.indices) activeJoints[i].applyPositionImpulse()
        }

        // 3. Intégration des positions + amortissement léger (aide la mise au repos)
        for (bd in bodies) {
            if (!bd.inWorld || bd.sleeping) continue
            // La vitesse fantôme s'ajoute au déplacement, jamais à la vitesse.
            if (bd.invMass > 0f) {
                bd.x += (bd.vx + bd.pvx) * dt
                bd.y += (bd.vy + bd.pvy) * dt
                bd.vx *= linearKeep
                bd.vy *= linearKeep
            }
            if (bd.invI > 0f) {
                bd.angle += (bd.omega + bd.pomega) * dt
                bd.omega *= angularKeep
            }
            bd.pvx = 0f; bd.pvy = 0f; bd.pomega = 0f
        }

        // 4. Qui peut s'endormir ?
        if (sleepEnabled) settleToSleep(dt)
    }

    /**
     * Retient les liaisons à résoudre, et réveille ce qu'elles tirent.
     *
     * Une liaison n'est pas un contact : elle ne se contente pas d'empêcher deux corps
     * de se traverser, elle les **tient**, et elle pousse sur eux à chaque passe. Une
     * liaison résolue contre un corps endormi lui verse donc de la vitesse qu'il
     * n'intègre jamais — elle s'accumule, image après image, et le jour où quelque
     * chose le réveille il part comme un ressort qu'on relâche. Mesuré : un boulet
     * sortait à deux fois et demie l'énergie que la machine contient.
     *
     * D'où la règle : une liaison dont les deux corps dorment ne se résout pas du
     * tout, et une liaison qui travaille réveille ses deux corps.
     */
    private fun selectJoints() {
        activeJoints.clear()
        for (i in joints.indices) {
            val j = joints[i]
            if (j.a.frozen && j.b.frozen) continue
            if (j.a.sleeping) j.a.wake()
            if (j.b.sleeping) j.b.wake()
            activeJoints.add(j)
        }
    }

    /** Aligne deux corps sur la plus petite de leurs deux horloges de repos. */
    private fun shareRest(a: PhysBody, b: PhysBody) {
        // Un corps immobile par nature n'a pas d'horloge : le sol garderait tout le
        // monde éveillé.
        if (a.immovable || b.immovable) return
        val t = minOf(a.restTime, b.restTime)
        a.restTime = t
        b.restTime = t
    }

    /**
     * Endort les corps qui n'ont plus bougé depuis assez longtemps.
     *
     * L'immobilité d'un instant ne prouve rien : une pierre lancée en l'air passe par
     * une vitesse nulle au sommet de sa course, et une pile qui s'effondre marque des
     * temps d'arrêt. C'est la **durée** qui décide, et elle se compte en secondes, pas
     * en pas.
     */
    private fun settleToSleep(dt: Float) {
        for (bd in bodies) {
            if (!bd.inWorld || bd.sleeping || bd.immovable) continue
            if (!bd.allowSleep) { bd.restTime = 0f; continue }
            if (bd.speedSq < sleepLinearTol * sleepLinearTol && abs(bd.omega) < sleepAngularTol) {
                bd.restTime += dt
            } else {
                bd.restTime = 0f
            }
        }

        // Deux corps qui se touchent, ou que relie une liaison, s'endorment
        // **ensemble**, en partageant le plus petit de leurs temps de repos.
        //
        // Sans ça, ils ne s'endorment jamais. Le premier à franchir le délai s'endort
        // seul ; son voisin encore éveillé le touche donc au pas suivant, ce qui le
        // réveille — c'est la règle, et c'est une bonne règle — et son compteur repart
        // de zéro. Puis c'est au voisin de s'endormir, et le premier le réveille à son
        // tour. Une pile de cinq caisses parfaitement immobiles se relançait ainsi
        // indéfiniment.
        //
        // Le partage du plus petit temps revient à faire dormir des **îlots** : au
        // bout de quelques pas, tout ce qui se touche partage la même horloge, franchit
        // le délai au même pas, et s'endort d'un bloc. Un réveil, où qu'il arrive dans
        // l'îlot, ramène tout le monde à zéro par le même chemin.
        for (i in active.indices) shareRest(active[i].a, active[i].b)
        for (i in joints.indices) shareRest(joints[i].a, joints[i].b)

        for (bd in bodies) {
            if (!bd.inWorld || bd.sleeping || bd.immovable) continue
            if (bd.restTime >= sleepDelay) bd.sleep()
        }
    }

    /**
     * Vrai quand plus rien ne bouge dans le monde. C'est ce qui dit à un jeu de
     * tir que le coup est terminé : le boulet s'est arrêté, les débris aussi.
     */
    fun isAtRest(linearTol: Float = 0.05f, angularTol: Float = 0.08f): Boolean {
        for (bd in bodies) {
            if (!bd.inWorld) continue
            if (!bd.atRest(linearTol, angularTol)) return false
        }
        return true
    }

    /** Vitesse du corps le plus rapide, en m/s (0 si tout dort). */
    fun fastestSpeed(): Float {
        var best = 0f
        for (bd in bodies) {
            if (!bd.inWorld || bd.invMass == 0f) continue
            val s = bd.speedSq
            if (s > best) best = s
        }
        return sqrt(best)
    }

    /**
     * Recherche des paires en contact, par **balayage** de l'axe des X.
     *
     * C'était autrefois la boucle la plus simple qui soit : tous les corps contre tous
     * les autres. Correcte, et parfaitement tenable tant que le monde comptait dix
     * pièces — mais son coût croît comme le carré du nombre de corps, et il se paie à
     * chacun des trente-deux sous-pas qu'un boulet rapide impose. Avec un château de
     * soixante-dix pierres, la recherche des paires mangeait à elle seule **60 % du
     * temps de simulation**, l'essentiel en pure perte : pour chaque paire éloignée,
     * elle interrogeait quand même la table des contacts, une fois par couple de
     * formes, avec une clé qu'il faut emballer dans un objet à chaque appel.
     *
     * Le balayage range les corps par bord gauche, puis n'examine, pour chacun, que
     * ceux dont le bord gauche tombe avant son bord droit. Deux constructions à trois
     * cents mètres l'une de l'autre ne se rencontrent plus jamais, et l'ordre des
     * paires retenues est rétabli à l'identique par un tri : **la simulation donne
     * exactement les mêmes nombres qu'avant**, ce qui compte pour un jeu dont les
     * niveaux doivent être reproductibles à partir de leur graine.
     */
    private fun broadPhase() {
        val n = bodies.size
        if (aabbMinX.size < n) {
            aabbMinX = FloatArray(n); aabbMaxX = FloatArray(n)
            aabbMinY = FloatArray(n); aabbMaxY = FloatArray(n)
            sweepKeys = LongArray(n)
        }

        // 1. Boîte englobante de chaque corps, et clé de tri (bord gauche, indice).
        // Un dormeur garde la sienne : il n'a pas bougé depuis qu'on l'a calculée.
        var m = 0
        for (i in 0 until n) {
            val bd = bodies[i]
            if (!bd.inWorld) continue
            if (!bd.sleeping) bd.updateAabb()
            aabbMinX[i] = bd.aabbMinX; aabbMaxX[i] = bd.aabbMaxX
            aabbMinY[i] = bd.aabbMinY; aabbMaxY[i] = bd.aabbMaxY
            // Le OU exclusif du bit de signe : `Arrays.sort` compare des entiers
            // signés, alors que la clé, elle, doit se comparer comme un entier non
            // signé — sans lui, tous les corps situés à droite de l'origine passent
            // avant ceux de gauche, le balayage s'arrête au mauvais endroit et les
            // contacts avec le sol se perdent. Symptôme : la construction traverse
            // le sol et tombe indéfiniment.
            sweepKeys[m++] =
                (((sortableBits(aabbMinX[i]) shl 32) or (i.toLong() and 0xFFFFFFFFL)) xor Long.MIN_VALUE)
        }
        java.util.Arrays.sort(sweepKeys, 0, m)

        // 2. Balayage : les paires dont les boîtes se chevauchent.
        var np = 0
        for (p in 0 until m) {
            val i = (sweepKeys[p] and 0xFFFFFFFFL).toInt()
            val a = bodies[i]
            val aMaxX = aabbMaxX[i]
            val aMinY = aabbMinY[i]
            val aMaxY = aabbMaxY[i]
            for (q in p + 1 until m) {
                val j = (sweepKeys[q] and 0xFFFFFFFFL).toInt()
                // Rangés par bord gauche : dès que le suivant commence après la fin
                // de celui-ci, tous ceux d'après aussi. C'est tout le principe.
                if (aabbMinX[j] > aMaxX) break
                if (aabbMinY[j] > aMaxY || aabbMaxY[j] < aMinY) continue
                val b = bodies[j]
                if (a.immovable && b.immovable) continue
                // Deux corps qui dorment ne peuvent rien se faire. C'est ici que se
                // gagne le prix d'un château au repos : la paire est reconnue et
                // abandonnée en trois comparaisons.
                if (a.frozen && b.frozen) continue
                if (!a.collidesWith(b)) continue
                if (np == pairBuf.size) pairBuf = pairBuf.copyOf(np * 2)
                val lo = if (i < j) i else j
                val hi = if (i < j) j else i
                pairBuf[np++] = (lo.toLong() shl 32) or hi.toLong()
            }
        }

        // 3. Remise en ordre : le solveur corrige les contacts l'un après l'autre,
        // donc l'ordre dans lequel on les découvre change le résultat. Triées par
        // indices de corps, les paires retrouvent l'ordre de la boucle « tous contre
        // tous », qui ne dépend que de l'ordre des corps dans le monde.
        java.util.Arrays.sort(pairBuf, 0, np)

        for (k in 0 until np) {
            val key = pairBuf[k]
            val a = bodies[(key ushr 32).toInt()]
            val b = bodies[(key and 0xFFFFFFFFL).toInt()]
            if (connectedByJoint(a, b)) continue
            narrowPhase(a, b)
        }

        // Nettoyage des contacts qui n'ont pas été revus (corps écartés ou retirés)
        doomed.clear()
        for ((k, arb) in arbiters) if (arb.stamp != stamp) doomed.add(k)
        for (k in doomed) arbiters.remove(k)

        // Le solveur travaillera sur cette liste, dans l'ordre de la table.
        active.clear()
        for (arb in arbiters.values) active.add(arb)
    }

    /** Contacts entre deux corps proches, forme par forme. */
    private fun narrowPhase(a: PhysBody, b: PhysBody) {
        for (pa in a.parts.indices) {
            for (pb in b.parts.indices) {
                val n = Collider.collide(a, pa, b, pb, fresh)
                if (n <= 0) continue
                val arb = arbiters.getOrPut(pairKey(a, pa, b, pb)) { Arbiter(a, b, pa, pb) }
                arb.update(fresh, n, Collider.normalX, Collider.normalY)
                arb.stamp = stamp
                // Un contact avéré avec un corps éveillé réveille le dormeur : c'est
                // ainsi qu'un effondrement se propage de proche en proche dans une
                // construction endormie, sans qu'on ait à tenir la liste de qui
                // s'appuie sur qui.
                //
                // Et **seulement le dormeur** : réveiller un corps déjà éveillé
                // remettrait son horloge de repos à zéro à chaque image, et plus
                // rien ne s'endormirait jamais — une pile de caisses posées se
                // réveillant elle-même indéfiniment par ses propres contacts.
                if (b.sleeping && !a.frozen) b.wake()
                if (a.sleeping && !b.frozen) a.wake()
            }
        }
    }

    /**
     * Les bits d'un flottant, réarrangés pour que l'ordre entier soit l'ordre réel.
     *
     * Trier des couples (position, indice) sans rien allouer demande de les loger dans
     * un entier long. Les flottants positifs se comparent déjà correctement bit à bit ;
     * les négatifs se comparent à l'envers, d'où le retournement.
     */
    private fun sortableBits(f: Float): Long {
        val b = f.toRawBits()
        val k = if (b < 0) b.inv() else b xor Int.MIN_VALUE
        return k.toLong() and 0xFFFFFFFFL
    }

    /** Vrai si une liaison relie déjà ces deux corps et interdit leur collision. */
    private fun connectedByJoint(a: PhysBody, b: PhysBody): Boolean {
        for (j in joints) {
            if (j.collideConnected) continue
            if ((j.a === a && j.b === b) || (j.a === b && j.b === a)) return true
        }
        return false
    }

    /**
     * Clé d'un contact : le couple de corps **et** le couple de formes touchées.
     * Deux formes d'un même corps qui touchent le même voisin doivent avoir chacune
     * leur propre suivi, sinon leurs impulsions mémorisées se mélangeraient.
     */
    private fun pairKey(a: PhysBody, pa: Int, b: PhysBody, pb: Int): Long {
        val ida = a.id.toLong() and 0xFFFFFF
        val idb = b.id.toLong() and 0xFFFFFF
        return (ida shl 40) or (idb shl 16) or ((pa.toLong() and 0xFF) shl 8) or (pb.toLong() and 0xFF)
    }
}