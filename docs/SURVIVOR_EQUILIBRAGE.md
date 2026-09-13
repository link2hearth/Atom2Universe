# Survivor — audit et proposition d’équilibrage

Analyse initiale du code présent le 13 septembre 2026, suivie de son implémentation le même jour. Les sections 1 à 5 et le script de calcul conservent les **anciennes valeurs** pour expliquer le diagnostic. La section 8 décrit la première implémentation ; **la section 9 précise les ajustements actuels après le premier retour en jeu**.

## 1. Diagnostic

Le ressenti de manque de pression est cohérent avec les formules : les ennemis arrivent lentement, restent fragiles, ne gagnent ni vitesse ni dégâts et courent moins vite que le joueur. Plusieurs armes multiplient dégâts × cadence × nombre de projectiles ; les PV ennemis progressent seulement de façon linéaire. Augmenter le plafond de population ne suffit pas lorsque les ennemis meurent avant de le remplir.

Il faut traiter ensemble le débit d’apparition, la résistance, les multiplicateurs offensifs et la survie du joueur. Une simple multiplication des PV laisserait les mêmes écarts entre armes.

## 2. Méthode et limites

Source principale : `app/src/main/java/com/Atom2Universe/app/games/survivor/SurvivorGame.kt` ; dimensions du terrain dans `SurvivorView.kt:177`. Calculs reproductibles avec `tools/survivor-balance-analysis.ps1` depuis la racine du dépôt.

Ce sont des calculs des formules, **pas une simulation spatiale ni des mesures sur téléphone**. Les dégâts/s théoriques supposent une cible disponible, des impacts réussis et aucun temps perdu. Les tirs peuvent manquer, plusieurs tirs peuvent viser une cible déjà condamnée et les dégâts dépassant ses PV sont perdus. Les cadences réelles sont légèrement réduites par la remise à zéro des compteurs à chaque tir plutôt que la conservation du dépassement d’une frame.

Les configurations sont comparées avec le même nombre de choix offensifs, sans coût de déblocage de seconde arme, critique ou passif. Elles illustrent les différences, sans prétendre être optimales ou garanties par les trois cartes aléatoires. Le joueur peut équiper **deux armes au maximum**.

Les vagues dépendent du temps : 30 secondes par vague. Le niveau du joueur dépend de l’XP, pas du temps. Six, douze et vingt-quatre améliorations correspondent aux niveaux 7, 13 et 25 et nécessitent environ 250, 770 et 4 105 XP cumulés, sans bonus XP. On ne peut donc pas attribuer honnêtement un build précis à une minute sans mesurer les éliminations et les choix.

## 3. Ennemis actuels

À leur apparition : `PV = PV de base × (1 + 0,06 × (vague − 1))`. Les ennemis déjà présents ne sont pas renforcés au passage de vague. À m minutes, le multiplicateur est approximativement `1 + 0,12m`.

| Temps / vague | Zombie | Rapide | Erratique | Orbiteur | Tireur |
|---|---:|---:|---:|---:|---:|
| Départ / 1 | 8 | 4* | 6* | 16* | 5* |
| 2 min / 5 | 9,92 | 4,96 | 7,44 | 19,84* | 6,20 |
| 5 min / 11 | 12,80 | 6,40 | 9,60 | 25,60 | 8 |
| 10 min / 21 | 17,60 | 8,80 | 13,20 | 35,20 | 11 |
| 15 min / 31 | 22,40 | 11,20 | 16,80 | 44,80 | 14 |
| 20 min / 41 | 27,20 | 13,60 | 20,40 | 54,40 | 17 |

\* Valeur de formule : ce type n’apparaît pas encore naturellement à cet instant. Rapides à 60 s, erratiques à 90 s, tireurs à 120 s, orbiteurs à 150 s.

Le boss apparaît toutes les 3 minutes : environ **256 PV à 3 min, 323 à 6 min, 391 à 9 min, 459 à 12 min, 526 à 15 min**. Avant 15 min, un boss vivant empêche le suivant d’apparaître. Les formations utilisent la même courbe de PV.

| Type | Vitesse constante | Dégâts au contact constants | XP normale |
|---|---:|---:|---:|
| Zombie | 80 | 10 | 5 |
| Rapide | 140 | 5 | 3 |
| Erratique | 105, avec détours | 8 | 4 |
| Orbiteur | 95, trajectoire indirecte | 15 | 10 |
| Tireur | 45 | 5 | 8 |
| Boss | 60 | 25 | 500 |

Les tirs ennemis font toujours 8 dégâts, vitesse 210, environ un tir toutes les 2,2 secondes lorsque la cible est à portée. Le joueur démarre à **180 de vitesse**, gagne 25 par rang et atteint 430. Même le rapide de base ne le rattrape pas en poursuite rectiligne. Une apparition devant lui peut toutefois l’intercepter.

### Arrivées et occupation réelle

| Temps | Arrivées principales/s, moyenne théorique sur les vagues | Plafond hors boss |
|---|---:|---:|
| Départ | 1,95 | 32 |
| 2 min | 1,40 | 40 |
| 5 min | 1,83 | 70 |
| 10 min | 3,52 | 120 |
| 15–20 min | 3,52 | 120 |

Ces valeurs supposent une faible occupation, ignorent formations et renforts occasionnels, les refus de tireurs au plafond et l’arrondi aux frames. À partir de la deuxième vague, six secondes sur trente n’ont aucune apparition individuelle : **20 % de pause**. La cadence initiale baisse jusqu’à 2 min, avant de remonter. Les renforts ajoutent au plus environ 12 % sur les types admissibles, et moins en pratique.

Dès 65 % d’occupation, l’intervalle hors variation aléatoire est multiplié par 1,8. Le jeu ralentit donc les renforts dès que la foule commence à se former. Les ennemis hors écran comptent dans ce plafond. Les formations n’arrivent pour la première fois que vers **4 minutes** : leur compteur initial de 180 s ne descend qu’à partir de la vague 3, donc après la première minute. Ensuite, elles sont espacées de 85–110 s, avec au plus une formation simultanée avant 15 min.

La taille du monde visible utilise les pixels de la surface, sans normalisation de ces distances de gameplay. À titre d’exemple, sur une surface 1080 × 1920, un zombie né au milieu d’un bord met approximativement **7 à 12,5 s** à atteindre un joueur immobile, en tenant compte des rayons de contact. Dans un coin c’est plus long. Le ressenti dépend donc aussi de la résolution et de la taille de surface.

La sélection des types réutilise le même nombre aléatoire avec des seuils successifs. Entre 90 et 150 s, la branche erratique absorbe tout l’intervalle qui aurait permis un rapide : **les apparitions individuelles de rapides disparaissent temporairement**. Employer des probabilités explicites évitera cette rupture involontaire de composition.

## 4. Comparaison des armes

Les rangs de dégâts donnent généralement `+30 % de la base`, pas ×1,30 à chaque rang. Malgré cela, les multiplicateurs se combinent très vite.

### Sans amélioration

| Arme | Dégâts/s sur une cible | Potentiel de groupe / particularité |
|---|---:|---|
| Projectile | 13,2 | Un tir, portée nominale 600 |
| Laser | 9,1 | Chaque ennemi traversé reçoit ces dégâts ; portée 300 |
| Orbitales | 9 en tirs | Deux orbes ; plafond supplémentaire de 20/s au contact, rarement entièrement exploité |
| Aura | 7,5 | Par ennemi dans le rayon 120 ; ralentissement de 30 %, sans critique |
| Foudre | 6 | Trois cibles distinctes, soit 18/s au total ; portée 300 par saut |
| Rebond | 7,7 | Deux cibles distinctes, soit 15,4/s au total ; temps de trajet |
| Bombe | 10 | Par ennemi touché dans l’explosion de rayon 90 ; cadence 0,5/s |

`AURA_TICK = 2,5` représente **2,5 frappes par seconde**, pas une frappe toutes les 2,5 secondes. La durée visuelle du laser ne multiplie pas ses dégâts : ceux-ci sont appliqués une seule fois à l’émission.

### À budget offensif égal

Ordre des allocations : dégâts/cadence/nombre pour projectile, laser et bombe ; dégâts/cadence/sauts pour foudre et rebond ; dégâts/fréquence/rayon pour aura ; dégâts/cadence/orbes/multitir pour orbitales.

| Famille | 6 choix | 12 choix | 24 choix |
|---|---|---|---|
| Projectile, aura, foudre, rebond | 2/2/2 | 4/4/4 | 8/8/8 |
| Laser | 2/2/2 | 5/4/3 | 11/10/3 |
| Orbitales | 2/1/2/1 | 4/2/4/2 | 8/5/6/5 |
| Bombe | 2/2/2 | 5/3/4 | 10/10/4 |

| Arme / mesure théorique | 0 choix | 6 choix | 12 choix | 24 choix |
|---|---:|---:|---:|---:|
| Projectile : somme des tirs | 13,2 | 76,0 | 203,3 | 727,1 |
| Laser : par cible et par faisceau | 9,1 | 18,9 | 36,4 | 97,8 |
| Laser : faisceaux sur cibles distinctes, sans traversées supplémentaires | 9,1 | 56,8 | 145,6 | 391,3 |
| Orbitales : somme des tirs, hors contact | 9,0 | 66,2 | 231,7 | 1 285,2 |
| Aura : par ennemi dans la zone | 7,5 | 15,6 | 26,4 | 56,1 |
| Foudre : boss seul / total si tous les sauts trouvent une cible | 6 / 18 | 11,5 / 57,6 | 18,5 / 129,4 | 36,7 / 403,9 |
| Rebond : boss seul / total si tous les rebonds réussissent | 7,7 / 15,4 | 14,8 / 59,1 | 23,7 / 142,3 | 47,1 / 471,2 |
| Bombe : par cible recevant toutes les bombes, sans résidu | 10 | 57,6 | 162,5 | 400 |

**Les lignes ne représentent pas toutes le même nombre de victimes.** Projectile et orbitales sont des budgets de dégâts émis : des tirs parallèles peuvent manquer un petit ennemi. Foudre et rebond ne peuvent pas toucher plusieurs fois la même cible pendant la même attaque. Laser, aura et bombe gagnent avec le nombre de cibles ; il n’existe pas de plafond global simple à leurs dégâts de groupe.

### Ce que cela donne après plusieurs vagues

- Vague 1 : un zombie de 8 PV demande deux projectiles de 6. Un seul rang de dégâts donne 7,8 et ne franchit même pas ce seuil ; deux rangs donnent 9,6 et permettent de tuer en un impact jusqu’à la vague 4.
- Vague 5, vers 2 min : avec le build projectile à 6 choix, les tirs font 9,6 dégâts pour un zombie de 9,92 PV. Le débit de 76/s paraît énorme, mais deux impacts restent nécessaires. Cela montre pourquoi les seuils de PV comptent autant que le DPS.
- Vague 11, vers 5 min : le build projectile à 12 choix fait 13,2 par tir et tue un zombie de 12,8 PV en un impact. Il émet 15,4 projectiles/s, face à environ 1,83 apparition principale/s. Même avec beaucoup de tirs perdus, le débit d’ennemis est insuffisant pour exploiter cette puissance.
- Vague 21, vers 10 min : le build aura à 12 choix inflige 6,6 par frappe, toutes les 0,25 s, rayon 192. Un zombie de 17,6 PV meurt en trois frappes, soit environ 0,50–0,75 s après son entrée. Ralenti à 56 de vitesse, il met environ `(192 − 16) / 56 = 3,14 s` à atteindre le joueur immobile. L’aura seule peut donc empêcher le zombie ordinaire de toucher le joueur, sans même investir dans le ralentissement.
- Boss de 9 min, environ 391 PV : build à 12 choix, temps idéal ≈ 1,7 s pour les tirs orbitaux, contre ≈ 21,2 s pour la foudre sans autres bonus. Ce sont des planchers continus, hors portée, déplacements et discrétisation des attaques ; le résultat réel dépend fortement des impacts.

### Armes fortes et faibles

**Orbitales : croissance la plus préoccupante.** Nombre d’orbes × multitir × dégâts × cadence, puis contact gratuit et effets secondaires. Le multitir double immédiatement les tirs de toutes les orbes au rang 1. Les 20 orbes et 6 tirs/orbe autorisés sont excessifs pour la résistance actuelle.

**Projectile : très fort après investissement**, surtout nombre + pénétration + fragmentation. Les tirs parallèles limitent néanmoins la conversion en dégâts sur une petite cible.

**Aura : très forte défensivement**, malgré son chiffre individuel modeste. Elle endommage et ralentit toutes les cibles sans viser. Le rayon maximal atteint 480 : sa surface nominale devient 16 fois celle de départ. Les tireurs sont un contre naturel, à condition de survivre assez longtemps et d’être présents.

**Bombe : forte sur les groupes et avec multitir/résidus**, moins fiable contre une cible mobile et espacée. Augmenter la foule va naturellement la renforcer : ne pas lui donner un gros bonus de dégâts en même temps.

**Laser : situationnel**, dépend des alignements et de la portée, mais peut endommager plusieurs fois une même cible si plusieurs faisceaux la traversent. Pas assez d’éléments pour le qualifier de globalement faible.

**Foudre : faible sur boss isolé, très forte avec division dans une foule.** À huit rangs de sauts et cinq rangs de division, l’arbre a une espérance théorique d’environ **171 cibles**, si toutes les branches trouvent une cible distincte à portée. Le plafond de population et la géométrie la limitent en jeu, mais cette croissance reste dangereuse.

**Rebond : candidat le plus clair à un léger renforcement**, notamment pour sa fiabilité et son début de partie. Il cumule temps de trajet, interdiction de retoucher un boss et absence des multiplicateurs du projectile/orbital. Dans une foule dense, tous ses rebonds réussis peuvent toutefois bien le valoriser.

## 5. Bonus et anomalies à corriger avant de régler les chiffres

1. **Fourche projectile rangs 2–5 et fragmentation orbitale rangs 2–3 : aucun effet supplémentaire.** Le code teste seulement `> 0` et crée toujours deux fragments à 50 %. Passer ces bonus à un rang, ou implémenter une vraie progression.
2. **Ralentissement aura rangs 9–10 : aucun effet**, le plancher de vitesse 0,3 est atteint au rang 8. Plafonner à 8 au minimum ; la proposition ci-dessous va plus loin.
3. **Pénétration : pas de mémoire des ennemis déjà touchés.** Le même projectile peut consommer plusieurs impacts sur un ennemi resté en contact sur plusieurs frames. Ajouter un ensemble des cibles touchées, comme pour le rebond.
4. **Fragments orbitaux perdent leur provenance.** Ils sont créés avec `isOrbital = false`, donc peuvent appliquer le poison du projectile plutôt que les effets orbitaux. Conserver l’origine explicitement, tout en interdisant les fragmentations récursives.
5. **Poison/brûlure partagés entre armes.** Une application faible peut remplacer une brûlure forte ; le plafond de poison orbital peut réduire un cumul de poison projectile. Choisir explicitement une règle : conserver la brûlure la plus forte et gérer les piles de poison par source.
6. **DPS affiché et vol de vie utilisent les dégâts bruts**, même au-delà des PV restants. Une bombe à 100 sur un ennemi à 2 PV compte 100 et soigne sur cette base. Mesurer `min(dégâts, PV restants positifs)` pour le DPS utile et le soin. Garder éventuellement un compteur brut séparé.
7. **Division de foudre : croissance en arbre.** Les branches récupèrent tout le nombre de sauts restant. Remplacer par des branches courtes ou un budget global de cibles.
8. **Résidu/durée et épines peuvent être proposés sans prérequis utile** : durée sans résidu, épines sans bouclier. Filtrer les choix pour éviter les améliorations immédiatement inactives.

Autres bonus à prendre en compte :

- Poison projectile rang 1 : 3,12 dégâts par tick de 0,5 s et par pile, jusqu’à trois piles, soit **18,72/s** à plein cumul, pendant 3 s renouvelables. Au rang 10 : 57,6/s. Le premier rang apporte beaucoup, les suivants ajoutent bien moins.
- Brûlure laser : **4,55/s au rang 1**, 14/s au rang 10, pendant 2 s renouvelables. Elle ne gagne rien avec `laser_dmg` ni les critiques.
- Poison orbital : 9,36/s au rang 1 à trois piles, 28,8/s au rang 10. Brûlure orbitale : 1,95/s puis 6/s. Le débit élevé des orbitales permet d’atteindre rapidement les piles, si l’ennemi survit.
- Résidu bombe : 20 % des dégâts de la bombe toutes les 0,5 s ; 2 s de base, jusqu’à 7 s. Environ **+80 % à +280 % des dégâts initiaux par zone** si la victime y reste toute la durée. Plusieurs zones peuvent se cumuler. Le premier tick est immédiat ; le nombre exact dépend de la discrétisation temporelle.
- Critique : chance `5 % × rang`, multiplicateur `1,5 + 0,5 × rang multiplicateur`. Gain moyen sur les dégâts admissibles : `1 + chance × (multiplicateur − 1)`. Avec 5 rangs de chance et 5 de multiplicateur, facteur **1,75** pour 10 choix. Sans plafond du multiplicateur, ce bonus continue à progresser indéfiniment. Aura, contact orbital et DoT ne critiquent pas ; la bombe conserve les dégâts critiques calculés au lancement, y compris dans son résidu.
- Explosion à la mort : 12 % de chance par rang, jusqu’à 96 %, dégâts de 40 % des PV max de la victime dans un rayon 90. Pas de cascade récursive immédiate, mais augmenter les PV des boss augmente aussi leur explosion à la mort.
- Armure : maximum réellement accessible **50 %** avec 10 rangs, malgré un clamp à 75 %. Invulnérabilité globale 0,5 s : environ deux impacts/s au maximum. Avec armure au maximum, des zombies infligent au plus environ 10 PV/s, compensés par 10 rangs de régénération. Les tireurs font encore moins.
- Régénération : jusqu’à 15 PV/s ; vol de vie : jusqu’à 100 % du dégât brut, plafonné à 5 % des PV max par activation toutes les 0,2 s, soit un plafond théorique de 25 % des PV max/s. Monter les PV max augmente ce plafond.
- PV max : +20 **et soin intégral** à chaque choix, sans limite de rang. Bouclier : +25 et recharge intégrale, puis 5/s après 3 s sans impact. Ces outils diminuent fortement l’attrition.

## 6. Proposition chiffrée pour une première version à tester

Ces valeurs constituent un point de départ cohérent, **pas un équilibre validé en partie**. Priorité : pression dès le début, conservation du plaisir de devenir puissant, suppression des multiplicateurs excessifs. Ne pas faire dépendre les PV du DPS affiché actuel, qui compte les dégâts perdus.

### Ennemis : résistance et menace

Utiliser une courbe commune aux apparitions normales et aux formations, avec `m = (vague − 1) / 2` :

`multiplicateur PV = 1 + 0,16m + 0,012m²`

| Type | PV de base actuels → proposés | Vitesse actuelle → proposée | Contact de base |
|---|---|---|---:|
| Zombie | 8 → 10 | 80 → 90 | 10 |
| Rapide | 4 → 6 | 140 → 170 | 5 |
| Erratique | 6 → 8 | 105 → 130 | 8 |
| Orbiteur | 16 → 20 | 95 → 120 | 15 |
| Tireur | 5 → 12 | 45 → 60 | 5 |
| Boss | 188 → 260 | 60 → 85 | 25 |

Vitesse multipliée ensuite par `1 + min(0,015m ; 0,30)`. Un rapide atteint 195,5 à 10 min : il devient une vraie menace pour le joueur sans vitesse. Dégâts de contact **et projectiles ennemis** multipliés par `1 + min(0,04m ; 1)`. Garder la cadence des tireurs et l’invulnérabilité de 0,5 s pour le premier essai, afin de ne pas tout durcir simultanément.

| Temps | PV zombie actuels → proposés | PV tireur proposés | Débit principal proposé/s |
|---|---|---:|---:|
| Départ | 8 → 10 | 12 | 2,6 |
| 2 min | 9,92 → 13,68 | 16,42 | 3,4 |
| 5 min | 12,8 → 21 | 25,2 | 5 |
| 10 min | 17,6 → 38 | 45,6 | 7 |
| 15 min | 22,4 → 61 | 73,2 | 9 |
| 20 min | 27,2 → 90 | 108 | 11 |

Boss proposés à leurs apparitions : **413 PV à 3 min, 622 à 6 min, 887 à 9 min, 1 208 à 12 min, 1 586 à 15 min**. Garder l’intervalle de 3 min et la protection contre plusieurs boss avant 15 min au premier essai.

### Apparitions : supprimer les périodes creuses

- Interpoler linéairement le débit entre les points du tableau ; après 20 min, rester à 11/s pour cette première proposition.
- Supprimer l’arrêt de six secondes et le multiplicateur d’intervalle ×1,8 à 65 % d’occupation. Garder un plafond de sécurité.
- Plafonds proposés aux mêmes jalons : **48, 64, 100, 150, 180, 200**, interpolés. Ce sont des limites techniques, pas une population forcée.
- Remplacer le renfort aléatoire de 12 % par une distribution en petits groupes de 2–4, comprise dans le débit annoncé. Conserver un compteur fractionnaire pour éviter une dépendance forte au nombre de frames.
- Démarrer avec six zombies déjà visibles à 220–300 unités du joueur, répartis avec une direction de sortie libre. Ces six ennemis comptent dans le budget d’apparition initial ; pas de contact immédiat.
- Première formation à 90 s de temps de partie, puis toutes les 45–60 s, 8–14 membres, en plus du débit principal. Maintenir une seule formation avant 15 min au premier essai.
- Normaliser les unités du monde sur une largeur visible de référence de 720, hauteur adaptée au ratio d’écran. Apparitions ordinaires juste hors écran ; recycler seulement les ennemis non-boss très loin derrière le joueur (distance > 1,5 diagonale visible), en conservant leurs PV et sans attribuer d’XP. Vérifier la conversion du rendu et des entrées ensemble.
- Composer les groupes explicitement : départ 100 % zombies ; à 30 s, 85 % zombies/15 % rapides ; à 90 s, 65/20/10/0/5 ; à partir de 150 s, **55/20/12/7/6 %** zombie/rapide/erratique/orbiteur/tireur. Plafond tireurs : `min(10 ; 2 + floor(m/2))`. Si plein, remplacer le tireur prévu par un zombie, sans perdre l’apparition.

L’augmentation proposée du débit et des PV représente déjà beaucoup : à 10 min, le budget d’arrivées d’une population fictive 100 % zombie passe d’environ **62 à 266 PV/s**, hors formations et limites de population. À 20 min, 96 → 990 PV/s. Il faudra regarder attentivement les parties tardives : si la foule sature en permanence après 15 min, réduire d’abord le coefficient quadratique 0,012 vers 0,008, plutôt qu’augmenter encore la puissance des armes dominantes.

### Armes et bonus : limiter les explosions de puissance

Règle commune proposée : **+20 % de dégâts de base par rang au lieu de +30 %**, plafond de 20 rangs conservé. Cadence : **+10 % par rang pour toutes les armes**, plafonds de rang actuels conservés. Autres paramètres inchangés sauf ci-dessous.

| Arme | Base proposée | Réglage spécifique |
|---|---|---|
| Projectile | 6 dégâts, 2,2 tirs/s | +1 projectile/rang, plafond 6 rangs ; tirs additionnels à 70 % des dégâts du principal ; pénétration max 3 rangs et une touche par cible |
| Laser | 8 dégâts, 1,4 tirs/s, portée 330 | 3 rangs multirayons conservés ; au maximum un impact direct par ennemi et par salve, même si les faisceaux se croisent |
| Orbitales | 3 dégâts, 1,4 tirs/s/orbe | 2 orbes + au maximum 8 rangs ; multitir max 2 rangs, tirs additionnels à 65 % ; contact toutes les 0,4 s |
| Aura | 3 dégâts, 2,5 frappes/s | Rayon 120 +10 %/rang, max 10 rangs ; ralentissement initial 20 %, +4 points/rang, max 5 rangs, donc 40 % maximum |
| Foudre | 6 dégâts, 1,3 attaques/s | Trois cibles de base, sauts max 8 rangs ; division +8 %/rang, max 5 ; chaque division ajoute une seule touche sans descendants ; plafond global 16 cibles/salve |
| Rebond | 8 dégâts, 1,3 tirs/s, vitesse 320 | Deux cibles de base ; conserver les rangs de rebond ; réorienter vers une cible vivante au rebond et supprimer le tir si aucune cible valide |
| Bombe | 18 dégâts, 0,55 tirs/s | Multitir max 2 rangs ; bombes additionnelles à 65 % ; rayon +8 %/rang au lieu de +10 % |

Fourche/fragmentation : un seul rang, deux fragments à **35 %** au lieu de 50 %, sans nouvelle fragmentation. Préserver correctement l’origine des tirs.

Poison proposé : trois piles max **par famille d’arme**, chaque pile fait 20 % du dégât de base non amélioré de cette famille par tick de 0,5 s, multiplié par `1 + 0,20 × rang poison`, durée 3 s. Aucun effet au rang 0. Ainsi le projectile rang 1 donne 8,64/s à plein cumul, contre 18,72 actuellement ; l’orbital donne 4,32/s. Les fragments appliquent l’effet de leur famille, sans créer une troisième famille.

Brûlure proposée : conserver les valeurs actuelles, mais garder la source la plus forte lors d’un recouvrement, sans additionner les brûlures. Les dégâts de brûlure restent indépendants des rangs de dégâts directs.

Résidu proposé : 12 % des dégâts de la bombe par tick de 0,5 s, 2 s +0,4 s/rang de durée, cinq rangs maximum. Au plus deux résidus endommagent un même ennemi par tick, en retenant les deux plus forts. Cela conserve la zone persistante sans un empilement sans limite.

Avec les configurations à **six choix** de l’audit, les nouveaux budgets seraient approximativement : projectile **53,2/s** (ancien 76), tirs orbitaux **42,7/s** (66,2), bombe **38,3/s par cible recevant toutes les bombes** (57,6), aura **12,6/s par cible** (15,6), foudre **65,5/s sur cinq cibles** (57,6), rebond **69,9/s sur quatre cibles** (59,1), laser **56,4/s sur trois cibles distinctes** (56,8). Ces allocations restent toutes légales ; les anciens builds à 12/24 choix devront être redistribués après réduction des plafonds.

### Défense et progression

- Vitesse joueur : +12/rang au lieu de +25, maximum 8 rangs ; 180 → 276 au maximum.
- Régénération : +0,35 PV/s par rang, maximum 10 rangs, soit 3,5 PV/s.
- Vol de vie : +2 % par rang, maximum 5 rangs ; basé sur les PV effectivement retirés, plafond par activation à 2 % des PV max, compteur de 0,3 s. Ne soigne que sur dégâts directs, pour ne pas dépendre de la fréquence des ticks de brûlure.
- Armure : conserver +5 points/rang, plafond 10 rangs. Elle reste utile sans annuler à elle seule les dégâts.
- PV max : conserver +20 mais soigner seulement de 20, sans soin intégral automatique.
- Bouclier : +20/rang au lieu de +25 ; gain immédiat de 20, pas recharge intégrale à chaque rang. Conserver sa régénération actuelle et ses épines.
- Critique : conserver la chance ; multiplicateur +0,25/rang, maximum 6 rangs, donc critique ×3 maximum et gain moyen maximal ×2 à 50 % de chance.
- Explosion à la mort : 8 % de chance/rang, maximum 6 ; dégâts `min(25 % des PV max de la victime ; 50 % des PV du zombie de la vague)`. Rayon 90 et interdiction de cascade conservés. Ce plafond empêche un boss renforcé de devenir une bombe démesurée.
- XP des ennemis ordinaires/formations : multiplier les valeurs actuelles par **0,65** pour le premier essai, en gardant les fractions. Boss : **200 XP au lieu de 500**. Conserver le seuil initial 30 et la croissance ×1,13, ainsi que +10 %/rang du bonus XP. Le débit accru donnera encore plus d’XP plus tard ; ce facteur ne garantit pas à lui seul la bonne progression.
- Garder les résurrections actuelles. Ne pas proposer durée de résidu sans résidu, épines sans bouclier, ni de rang devenu sans effet.

Si ces plafonds sont implémentés, prévoir le traitement des sauvegardes dont les rangs dépassent les nouveaux maximums : conversion/remboursement en choix, ou version d’équilibrage conservée pour la partie en cours. Ne pas simplement masquer les cartes tout en laissant les anciens bonus actifs.

## 7. Validation en jeu à prévoir

Avant toute conclusion définitive, mesurer sur le téléphone : dégâts utiles **par arme**, dégâts perdus, ennemis visibles/proches/hors écran, PV ennemis ajoutés par seconde, temps d’élimination des boss, dégâts reçus et niveau du joueur toutes les 30 secondes. L’indicateur DPS global actuel ne permet pas cette comparaison.

Objectifs de départ à tester : première menace visible en 1–2 s, mouvement nécessaire dans les 10 premières secondes, environ 8–15 ennemis visibles après 30 s, 15–30 vers 2 min et 30–60 vers 5 min pour un build médian. Ce sont des cibles de ressenti, pas des populations à imposer artificiellement. Boss idéalement 15–30 s pour un build polyvalent, 8–15 s pour un build spécialisé.

Comparer séparément les sept armes de départ, puis les couples orbitales+aura, projectile+aura, foudre+bombe et laser+rebond ; inclure un build offensif choisi, un build défensif et des choix aléatoires. Vérifier plusieurs résolutions puisque le gameplay actuel utilise leurs pixels. Observer aussi les ralentissements : séparation des ennemis jusqu’à six passes et collisions tirs × ennemis rendent le passage à 200 ennemis potentiellement coûteux.

Ordre conseillé : corriger les bonus et la mesure des dégâts, appliquer le premier ensemble cohérent proposé, puis tester des parties de 5/10/20 min. Ajuster le débit si le terrain est vide, les PV si les ennemis présents disparaissent instantanément, les trajectoires si une course en ligne droite suffit, et les défenses si les contacts n’ont plus de conséquence. Aucun APK n’a été construit ou installé dans cet audit.

## 8. Implémentation du 13 septembre 2026

- Courbes de PV, vitesse, dégâts, population et cadence centralisées dans `SurvivorBalance.kt`. Même XP par type dans les formations et les apparitions ordinaires, puis facteur 0,65 hors boss.
- Six ennemis au départ ; leur distance est réduite si nécessaire pour rester visibles sur une surface courte. Leur budget d’apparition est déduit des premières secondes. Petits groupes de 2–4, sans pause de fin de vague et sans ralentissement de cadence à forte occupation.
- Formations dès 90 s ; leur trajectoire s’adapte aux dimensions du terrain normalisé. Rendu et collisions utilisent la même échelle, tandis que le HUD et le joystick conservent leurs coordonnées d’écran.
- Plafonds et statistiques d’armes/défenses de la section 6 appliqués. Les fragments ne retouchent pas les victimes du projectile parent ; les explosions imbriquées ne réutilisent plus le même tampon de suppression. Les ennemis déjà morts ne peuvent ni être ciblés ni infliger de dégâts au contact.
- Poison séparé par famille et sauvegardé ; brûlure la plus forte conservée ; résidus limités aux deux plus puissants par cible sur une cadence commune de 0,5 s. Une nouvelle zone peut donc attendre jusqu’à 0,5 s si d’autres zones sont déjà actives.
- DPS global fondé sur les PV effectivement retirés ; vol de vie sur dégâts directs uniquement. Aucun nouvel écran de statistiques ni instrumentation complète par arme ajouté dans cette version.
- Textes anglais/français mis à jour, descriptions des cartes contenues dans leur largeur.
- Anciennes sauvegardes conservées : niveau, XP, éliminations, temps, armes et PV du joueur restent présents. Les rangs dépassant les nouveaux plafonds sont remboursés en choix d’amélioration. Le bouclier conserve son pourcentage de charge ; les ennemis sont convertis aux nouvelles statistiques en conservant leur pourcentage de PV. Les anciens effets temporaires de poison/brûlure/ralentissement sont retirés. Un numéro d’équilibrage empêche de refaire cette conversion après sauvegarde.

Validation : compilation `compileDebugKotlin`, contrôle des ressources XML et relecture des interactions. L’équilibre ressenti, la fluidité à forte population et la reprise d’une partie sur téléphone restent à vérifier en jeu par le propriétaire ; aucun APK construit ou installé.

## 9. Ajustements après le premier essai — révision 3

Retour du joueur : première minute difficile mais intéressante, ennemis trop rapides à haut niveau, terrain trop zoomé et manque de cibles vers 2 minutes avec le projectile amélioré.

- Largeur logique 720 → **960 unités** : les éléments font 25 % de moins à écran identique, avec 33 % de largeur de terrain visible en plus. Le rendu reste indépendant de la résolution.
- Vitesses de base zombie/rapide/erratique/orbiteur/tireur/boss : **85/150/115/105/50/70**, entre les valeurs originales et la première révision. Croissance réduite à +0,25 %/minute, plafonnée à +5 % au lieu de +30 %. À 20 minutes, un rapide passe ainsi de 221 à **157,5**, sous les 180 du joueur sans bonus. Vitesses des formations et projectiles ennemis conservées à leurs valeurs d’origine.
- Cadences de base à 0/1/2/5/10/15/20 minutes : **2,6/3/4,6/5,8/7/9/11 ennemis par seconde**. La première minute conserve sa cadence précédente.
- Renfort adaptatif **sur la cadence, pas sur les PV ou la vitesse**. Le jeu observe les ennemis vivants à moins de 520 unités. La cible indicative passe de 12 à 24 ennemis proches avec le temps ; elle ne force aucune apparition au contact.
- Aucun supplément adaptatif avant 60 s, activation progressive jusqu’à 120 s. Moins il reste de cibles proches, plus la cadence augmente, jusqu’à **+65 % maximum**. Réponse lissée sur 8 s à la hausse et 3 s à la baisse. Le supplément se résorbe lorsque la foule revient ou si le joueur a moins de 35 % de ses PV.
- Vers 2 minutes, la cadence peut ainsi tendre de **4,6 à 7,59/s** sur un terrain durablement vide, contre 3,4/s dans la première révision. Le plafond reste **64 ennemis vivants hors boss**, partagé entre formations et apparitions ordinaires, y compris hors écran. Il monte toujours jusqu’à 200.
- Le plafond reste une limite technique : le DPS utile chute quand il manque des cibles et ne permet donc pas de déterminer seul une population souhaitable. Un gain de puissance doit rester perceptible ; la réponse adaptative est volontairement bornée.
- Sauvegardes : la révision 2 reçoit les vitesses réduites sans recalculer les PV, l’XP ou les effets des ennemis déjà présents. La migration complète reste réservée aux parties antérieures au premier équilibrage. Le supplément de cadence est sauvegardé pour préserver la continuité à la reprise.

Ces réglages restent à confirmer en partie, notamment le temps d’arrivée accru par le recul de caméra et la réduction des vitesses. Les PV, dégâts d’armes et récompenses ne sont pas changés dans cette révision.
