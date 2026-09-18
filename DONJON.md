# Donjon — document de conception

Refonte du jeu `games/roguelike`. Ce fichier rassemble **toutes les décisions prises**, le
plan par étapes et les questions encore ouvertes. On le met à jour à chaque étape.

## Pourquoi une refonte

Simulation de 1 200 parties de l'ancien jeu (septembre 2026) : tous les bots mouraient
entre les étages 2 et 4. La cause profonde : les monstres étaient recalculés à partir des
stats du joueur (`computeMobStats`), donc chaque gain d'équipement rendait les monstres
plus forts d'autant. Le combat n'offrait aucune décision (on fonce, on échange des PV).

## Le concept en une phrase

Un dungeon crawler de 100 étages : on explore une carte, les rencontres ouvrent un
**écran de combat au tour par tour façon FF / Pokémon**, et on progresse grâce à un
**butin à la Diablo** (stats aléatoires, raretés, uniques, sets de zone) et à des
**reliques qui donnent les sorts**.

## Décisions prises

### La carte (exploration)
- On voit les monstres sur la carte. Un monstre sur la carte = **un groupe** (1 à 3
  ennemis en combat).
- On peut essayer de les éviter. Un monstre qui nous voit **nous poursuit**.
- S'il nous rattrape (ou si on fonce dessus), le combat démarre.
- Après un combat, si un autre monstre nous poursuit et est tout proche, **on enchaîne**,
  pas le choix.
- **On ne peut se soigner que si aucun monstre ne nous poursuit.** Toute la gestion de la
  carte est là : attirer les monstres un par un, semer un poursuivant, souffler.

### Le terrain
- Plus labyrinthique : **petites salles** (3 à 7 cases de large, 3 à 5 de haut) et
  **beaucoup de couloirs**, parfois des étages presque entièrement en couloirs.
- Génération « salles et labyrinthe » (`DungeonGenerator.kt`) : salles posées, labyrinthe
  dans tout le reste, portes pour tout relier plus quelques boucles, culs-de-sac
  raccourcis mais pas supprimés. Chaque étage tire son style (nombre de salles,
  sinuosité, boucles, culs-de-sac).
- L'or est caché d'abord au bout des culs-de-sac : explorer paie.
- On ne se faufile pas en diagonale entre deux coins de mur.
- Contrôle : on glisse le doigt dans une des 8 directions (diagonales comprises) et on
  **reste appuyé pour continuer d'avancer**. Le pas part dès que le doigt a assez glissé,
  puis se répète. Une diagonale bloquée glisse le long du mur. Le déplacement s'arrête
  quand un nouveau monstre nous repère ou qu'un combat commence.
- **La taille de la carte suit le nombre de monstres** : on vient pour se battre, pas
  pour tourner en rond. ~100 cases de carte (~50 cases de sol) par monstre, format 3:2.
  Monstres : 3 + étage, plafonnés à 12. Étage 1 : 4 monstres sur 25 × 17 (escalier à ~40
  pas) ; étage 5 : 8 monstres sur 35 × 23 ; dès l'étage 9 : 12 monstres sur 41 × 27.

### Le combat
- Écran séparé, tour par tour classique : on attaque, on se fait attaquer.
- Actions : **Attaque** (l'épée de base du joueur, le bouton du début de partie),
  **sorts** (un bouton par relique équipée), **objets** (soins, en quantité limitée).
- **Au maximum 3 ennemis.** Quand ils sont plusieurs, ils « prennent leur temps » : chaque
  ennemi a une cadence (il frappe tous les N tours) et un groupe frappe moins souvent
  par ennemi. On ne contrôle **pas** d'équipe : un seul héros.
- Un peu d'action dans le tour par tour :
  - **Parer** : toucher l'écran au bon moment quand un ennemi frappe réduit les dégâts.
  - **Frapper juste** : pendant sa propre attaque, un swipe au bon moment augmente la
    chance de critique.
- Fin du combat : on ramasse le butin, puis retour sur la carte.

### Mort et checkpoints
- 100 étages. Checkpoints / boss aux paliers **20, 40, 60, 80, 100**.
- Esprit **die and retry**, **pas de mur de farming à la FF** : la première fois on meurt
  sans bien comprendre, on revient mieux préparé, après quelques essais ça passe.
- À la mort : **retour au dernier checkpoint avec tout l'équipement**, mais **perte
  d'une partie de l'argent**.

### Le butin (inspiration Diablo)
- Stats aléatoires, raretés, objets uniques, **sets de zone**.
- **Chaque objet a une base** qui dépend de son type, en plus de ses affixes :
  - une **arme donne toujours la caractéristique de son type** (bâton ou orbe → INT,
    épée → FOR, dague → DEX…) plus ses dégâts ;
  - une **armure donne son armure plus des caractéristiques de base** (INT, DEX…).
- **Matières par palier, avec des tiers qui se chevauchent** : une seule échelle
  (Cuir, Cuivre, Bronze, Fer, Acier, Mithril, Obsidienne…), 5 tiers par matière. Le
  **Cuir 5 se trouve aux mêmes étages que le Cuivre 1** : même puissance, noms variés.
  Les noms sont provisoires, « la limite c'est l'imagination ».
- Raretés pour l'instant : Normal (base seule), Magique (1–2 affixes), Rare (3–4).
  **Légendaires et sets : plus tard.**
- **Sac infini, aucune pression de gestion** : tout est disponible, on peut vendre (ça
  rapporte, donc on le fera naturellement), on ajoutera plein de confort plus tard.
- **L'écran de choix en fin de combat reste** (Équiper / Au sac) : ça doit être rapide
  en jeu. Rien n'est jamais perdu.
- **L'inventaire doit surtout être clair** : trié du meilleur au moins bon, ou du
  dernier ramassé au plus ancien.
- Bonus liés au gameplay (fenêtre de parade, parade parfaite qui soigne…) : **plus
  tard**, quand le gameplay tactile sera enrichi.
- Farmer une zone est voulu quand c'est pour compléter un set. Exemple : le **set du rat
  des égouts** (étages 1–20) empoisonne, et les petits monstres fuient « parce qu'on sent
  mauvais ». À affiner.
- Les monstres se règlent sur **l'étage**, jamais sur le joueur : l'équipement doit
  compter.

### Gameplay de combat : la suite (vision)
- Enrichir les gestes à la **Undertale** : de petits gameplays tactiles variés pour
  attaquer, parer et d'autres actions à définir. La touche au bon moment et le swipe
  actuels sont les premiers.
- **Idée à approfondir : la vitesse de jeu** (18/09/2026). Le combat repose sur des
  timings (frappe, parade) : une stat « vitesse » qui **accélère avec les étages** rendrait
  les gestes plus durs en descendant. Des équipements rares (pas des reliques) la
  **ralentiraient**. Bornes dans les deux sens : même à ×2, ça doit rester jouable ; et on
  ne doit pas pouvoir empiler assez de bonus pour que l'étage 100 soit, disons, 3 fois plus
  lent que prévu. Pas encore tranché ni codé.

### Les reliques = les sorts
- On équipe des reliques, chacune donne un sort (boule de feu, boule de glace…).
- Les dégâts d'un sort dépendent de la relique **et** des stats du joueur et de son
  équipement.
- Certains boss demandent telle ou telle relique (immunités : un boss insensible au feu
  mais pas au poison, etc.). **Clé souple** : un boss reste battable sans la bonne relique,
  mais bien plus dur. Jamais de verrou.
- **Quatre boutons de combat, façon Pokémon** : l'**attaque** à l'arme (fixe), **deux
  reliques**, et un **sort spécial** (obtention et fonctionnement à définir ; bouton
  verrouillé pour l'instant). La potion reste à part. On ne porte donc que deux reliques
  à la fois : le choix avant un boss, c'est le cœur du *die and retry*.
- **Les reliques se trouvent**, on n'en a aucune au départ — la Boule de feu aussi est à
  trouver, et ce n'est pas forcément la première.
- Il en faudra beaucoup. On commence par les **classiques des RPG**, un élément chacune :
  le feu brûle, la glace fige, la foudre paralyse (mais n'empêche pas la magie), le poison
  ronge. Un sort n'a pas l'obligation d'avoir un geste tactile à lui, même si c'est préféré.
- Pouvoirs sur la carte (semer un poursuivant, attirer un monstre…) : **oui, mais plus
  tard, et pas sur les reliques** — ça viendra avec le travail sur l'exploration.

### Les caractéristiques : copiées sur D&D
| Carac. | Rôle |
|---|---|
| FOR | dégâts de l'épée |
| DEX | chance de critique, fenêtre de parade plus large |
| CON | points de vie |
| INT | dégâts des sorts, DD des sorts de contrôle (jets de sauvegarde) |
| SAG | recharge des sorts (plus tard : résistances) |
| CHA | or gagné (plus tard : prix chez le marchand, qualité du butin) |

### Graphismes
- On garde les visuels actuels pour l'instant ; toute la partie graphique sera refaite
  plus tard. L'important est le concept.

## Zones prévues (à affiner)

| Étages | Zone | Set | Boss |
|---|---|---|---|
| 1–20 | Égouts | Rat des égouts | Roi des Rats |
| 21–40 | Cryptes | Fossoyeur | La Liche |
| 41–60 | Forge | Forgeron maudit | Golem de lave |
| 61–80 | Glacier | Chasseur du froid | Wyrm blanc |
| 81–100 | Abysses | Abyssal | Le Dévoreur |

## Questions ouvertes
- **À quoi sert l'argent ?** On le gagne aussi en vendant. Pistes : relancer un affixe
  d'un équipement (piste retenue en principe), booster une stat, changer un type /
  élément, acheter des consommables de combat (pansements, soins instantanés) — limités
  car très puissants.
- **Niveaux et expérience ?** Pour l'instant les caractéristiques viennent de la base
  (10 partout) et de l'équipement. Faut-il des niveaux façon D&D ?
- **Fuir un combat ?** Pas prévu pour l'instant.
- Quitter l'appli en plein combat régénère l'étage (petite triche possible).

## Plan par étapes

1. **La base** *(faite)* — carte avec monstres qui patrouillent et poursuivent, écran
   de combat (Attaque, une relique Boule de feu, potions, parade et swipe critique),
   repos uniquement sans poursuivant, les 6 caractéristiques D&D, monstres réglés sur
   l'étage, butin simple adapté aux nouvelles stats, mort = retour étage 1 avec le stuff
   et perte d'or. Étages 1 à 5, visuels actuels.
2. **Le squelette du butin** *(faite)* — bases d'objets (types d'armes et d'armures),
   matières et tiers qui se chevauchent, raretés Normal / Magique / Rare, sac infini,
   vente, inventaire clair et trié. Les **affixes** ont ensuite eu leur propre passe :
   budget mesuré, huit paliers, table complète et test de garde (voir « Les affixes »).
   Pas encore : légendaires, sets, relance.
3. **Sets, reliques et zones** — set du rat des égouts, plusieurs reliques, étages 1–20.
   *En cours* : les quatre reliques classiques, le combat à quatre boutons, les recharges
   gardées, les jets et la rage, les archétypes et leur Spécial sont faits (voir « Les
   reliques », « Les archétypes »). Le grimoire (états, réactions, résonances, lot 1) est
   fait le 18/09 ; son reste passe **avant** les sets.
4. **Boss et checkpoints** — Roi des Rats à l'étage 20, puis zones suivantes.

## Réglages de l'étape 1 (valeurs de départ, à ajuster en jouant)

- Héros : 10 dans chaque caractéristique. PV max = 40 + 4 × (CON − 10) + bonus.
  Épée 4–7 dégâts, +4 % par point de FOR au-dessus de 10. Critique : 5 % + 1 % par DEX,
  dégâts ×2. Armure : dégâts reçus × 50 / (50 + armure).
- Frapper juste : bien = +25 points de critique, parfait = +60.
- Parade : bien = dégâts ÷ 2, parfaite = dégâts × 0,2. Fenêtre élargie par la DEX.
- Boule de feu : 6–9 dégâts, +5 % par INT, brûlure 2 tours ; recharge 3 tours (SAG la
  réduit). *Remplacée depuis par « Les reliques » : ces 6–9 ne suivaient pas la
  profondeur.*
- Monstres à l'étage 1 (PV / dégâts / frappe tous les N tours / dès l'étage) :
  Rat 16/3/1/1, Gobelin 24/4/1/1, Squelette 34/6/2/2, Orc 50/10/2/3, Démon 70/14/3/5.
  Puis PV × (1 + 0,22 × (étage − 1)), dégâts × (1 + 0,15 × (étage − 1)).
- Taille des groupes : seul aux étages 1–2, jusqu'à 2 aux étages 3–4, jusqu'à 3 ensuite.
- Embuscade (les monstres frappent en premier) seulement si on ne voyait pas le monstre
  avant qu'il nous rejoigne, ou si on se reposait. Un combat enchaîné n'est pas une
  embuscade : devoir enchaîner est déjà la pénalité.
- Groupes : cadence d'un ennemi = sa cadence + (taille du groupe − 1), attaques décalées.
- Repos : +15 % des PV max par tour de repos, impossible si poursuivi.
- **Le repos attire les monstres** : à chaque tour de repos, 8 % de chance qu'un monstre
  errant surgisse hors de vue, à 5–10 pas, et vienne droit sur nous (on est alors
  poursuivi, donc plus de repos). S'il arrive sans qu'on l'ait vu : embuscade. Sans ça,
  le repos gratuit supprimait toute usure sur un étage.
- Mort : −30 % de l'or.
- Marchand sur l'escalier : potion à 15 or (5 maximum sur soi, soigne 40 % des PV).

## Réglages du butin (étape 2, squelette)

- **Puissance** d'un objet = rang de la matière × 4 + tier (Cuir 5 = Cuivre 1 = 5). Un
  étage tire une puissance autour de 1 + 0,4 × (étage − 1) : Cuir jusqu'à l'étage ~10,
  Cuivre vers 20, Fer vers 40, Mithril vers 60, Astralite vers 100.
- Matières (noms provisoires) : Cuir, Cuivre, Bronze, Fer, Acier, Mithril, Obsidienne,
  Adamantium, Orichalque, Astralite. Les armes et bijoux du premier palier sont « de bois ».
- **Bases** : Épée (FOR), Hache (FOR, +25 % dégâts), Dague (DEX, −20 %), Masse (CON),
  Bâton (INT, dégâts des sorts), Sceptre (SAG, dégâts des sorts), Bouclier (armure, CON),
  Orbe (INT, dégâts des sorts), Casque / Armure / Bottes (armure + une caractéristique au
  hasard), Amulette / Anneau (une caractéristique au hasard).
- Tout grimpe de +45 % par cran de puissance ; l'armure se mesure à l'étage (la même
  armure protège moins plus bas).
- Affixes : les 6 caractéristiques, armure, PV, dégâts d'arme, dégâts des sorts, chance
  et dégâts critiques, vol de vie. Jamais deux fois le même sur un objet. Le détail est
  dans **« Les affixes »** juste en dessous.
- **Note** d'un objet : affichée partout, elle sert à trier le sac et à comparer (▲ / ▼)
  avec l'objet porté. Voir **« La note »**.
- Vente : (3 + 2 × puissance) × 1 / 2 / 4 selon la rareté.
- Le héros commence avec une Épée de bois 1 normale.
- Les pièces défensives (casque, armure, bottes, bouclier) donnent des **PV** en plus de
  leur armure : 0,75 PV par point d'armure de base, multiplié par la puissance. Voir
  **« Ce que les PV devaient rattraper »**.

## Les affixes

Tout tient dans `AffixBudget` (`LootSystem.kt`), gardé par `AffixBudgetTest`.

### La règle, en une phrase

> Un affixe tiré au maximum de son palier vaut **12 % de son axe** — dégâts à l'arme, PV,
> PV effectifs, dégâts des sorts ou or — chez le **héros de référence**.

Le héros de référence, c'est celui qui porte sept objets **Normaux** (donc sans aucun
affixe) de la même puissance : épée, bouclier, casque, armure, bottes, amulette, anneau.
Il ne sert qu'à mesurer. On le modélise dans le code pour que les fourchettes soient
**calculées** et non inventées : si un jour on change les dégâts d'une épée ou les PV par
point de CON, les affixes suivent tout seuls, et le test dit lesquels ont dérivé.

Pourquoi il fallait un étalon : les stats ne se comparent pas à l'œil. À la puissance 1,
+1 point de CON vaut 7,8 % des PV, +1 % de dégâts critiques vaut 0,06 % des dégâts — 130
fois moins. Une table écrite à la main donne forcément des lignes mortes (qu'on jette) et
des lignes obligatoires (qu'on cherche toutes les parties).

### Les huit paliers

Un affixe n'existe qu'à partir d'une certaine **puissance d'objet**, comme l'*item level*
de Diablo ou Path of Exile. Les seuils tombent sur les zones du donjon :

| Palier | P1 | P2 | P3 | P4 | P5 | P6 | P7 | P8 |
|---|---|---|---|---|---|---|---|---|
| Puissance ≥ | 1 | 3 | 5 | 9 | 13 | 17 | 25 | 33 |
| soit l'étage | 1 | 6 | 11 | 21 | 31 | 41 | 61 | 81 |

Un objet tire **le meilleur palier que sa puissance autorise 6 fois sur 10**, un cran en
dessous 3 fois sur 10, deux crans 1 fois sur 10. C'est ce qui fait qu'on continue de
ramasser au même étage : deux objets de même puissance n'ont pas la même valeur. Le
tirage lui-même va de **55 % à 100 %** de la valeur du palier. Le palier est affiché au
joueur sur chaque ligne d'affixe.

### Deux familles

**Les affixes à budget** — quatre caractéristiques (FOR, CON, INT, SAG), armure, PV,
dégâts d'arme. Leur valeur est *déduite* de la règle des 12 %. Ils grandissent parce que
leur axe grandit : l'armure ×19 sur la partie, la FOR seulement ×2,3 (elle se dilue dans
un multiplicateur qui monte).

**Les affixes à plafond** — chance et dégâts critiques, dégâts des sorts, vol de vie, plus
la DEX et la CHA. Ce sont des taux, ou des stats dont l'axe ne grandit pas : la CHA donne
3 % d'or par point à l'étage 1 comme à l'étage 100. La règle des 12 % leur donnerait soit
+13 % de critique dès l'étage 1, soit la même valeur aux huit paliers — et un palier qui
n'apporte rien n'est pas un palier. On fixe donc à la main **ce qu'un équipement complet
peut en porter**, réparti sur les huit paliers.

**Certains affixes n'apparaissent pas tout de suite** : le vol de vie à partir du palier 4,
les dégâts critiques à partir du 3. Au palier 1, +1 % de vol de vie rend 0,1 % du sac de
PV par coup — une ligne qui ne fait rien occupe une place et déçoit.

### La table

Générée par `AffixBudgetTest` dans `build/affix-tiers.txt`. « vaut » = la part de son axe
qu'apporte le tirage maximum.

| Affixe | P1 | P2 | P3 | P4 | P5 | P6 | P7 | P8 |
|---|---|---|---|---|---|---|---|---|
| **FOR** | 2–3 | 2–4 | 3–4 | 3–4 | 3–5 | 3–5 | 4–6 | 4–7 |
| *vaut* | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % |
| **DEX** | 2–3 | 3–4 | 3–5 | 4–6 | 4–7 | 5–8 | 5–9 | 6–10 |
| *vaut* | 5 % | 7 % | 8 % | 10 % | 11 % | 13 % | 14 % | 15 % |
| **CON** | 1–2 | 2–2 | 2–3 | 3–4 | 3–5 | 4–6 | 5–8 | 6–10 |
| *vaut* | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % |
| **INT** | 2–2 | 2–3 | 2–3 | 2–3 | 2–3 | 2–3 | 2–3 | 3–4 |
| *vaut* | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % |
| **SAG** | 2–3 | 2–4 | 3–4 | 3–4 | 3–5 | 3–5 | 4–6 | 4–7 |
| *vaut* | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % |
| **CHA** | 2–2 | 2–3 | 3–4 | 3–5 | 4–6 | 5–8 | 6–10 | 7–12 |
| *vaut* | 6 % | 9 % | 12 % | 15 % | 18 % | 24 % | 30 % | 36 % |
| **Armure** | 5–8 | 9–15 | 13–22 | 21–36 | 28–51 | 36–65 | 52–93 | 68–122 |
| *vaut* | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % |
| **PV** | 4–6 | 5–8 | 6–11 | 9–15 | 11–19 | 14–24 | 18–32 | 23–41 |
| *vaut* | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % |
| **Dégâts d'arme** | 1 | 1 | 2 | 2–3 | 3–4 | 3–5 | 5–8 | 6–10 |
| *vaut* | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % | 12 % |
| **Dégâts des sorts** | 4,4–8 % | 6,1–11 % | 7,7–14 % | 9,9–18 % | 12,1–22 % | 14,3–26 % | 16,5–30 % | 19,3–35 % |
| *vaut* | 8 % | 11 % | 14 % | 18 % | 22 % | 26 % | 30 % | 35 % |
| **Chance critique** | 1,1–2 % | 1,4–2,6 % | 1,8–3,2 % | 2,2–4 % | 2,6–4,8 % | 3,1–5,6 % | 3,7–6,8 % | 4,4–8 % |
| *vaut* | 2 % | 2 % | 3 % | 4 % | 4 % | 5 % | 6 % | 7 % |
| **Dégâts critiques** | — | — | 11–20 % | 13,8–25 % | 16,5–30 % | 19,8–36 % | 23,7–43 % | 27,5–50 % |
| *vaut* | — | — | 1 % | 2 % | 3 % | 4 % | 5 % | 7 % |
| **Vol de vie** | — | — | — | 0,6–1 % | 0,7–1,3 % | 0,9–1,6 % | 1–1,9 % | 1,3–2,3 % |
| *vaut* | — | — | — | 1 % | 2 % | 3 % | 5 % | 7 % |

Comment lire les lignes qui ne font pas 12 % :

- **DEX** monte de 5 % à 15 % : elle ne donne pas que du critique, elle élargit aussi la
  fenêtre de parade (+4 ms par point sur 160). On compte ses points 1,8 fois pour ça — et
  la parade paie surtout pour qui vise juste, donc la DEX vaut plus cher qu'affiché ici
  pour un joueur adroit.
- **CHA** monte de 6 % à 36 % d'or gagné : c'est l'axe le plus faible du jeu tant que l'or
  ne sert qu'aux potions. À revoir quand l'or aura ses usages (question ouverte plus haut).
- **Chance critique** reste à 2–7 % : c'est voulu. Le critique du héros est plafonné à
  60 %, les affixes seuls ne doivent pas y suffire. Ils s'empilent sur sept emplacements,
  et chaque point rend les **dégâts critiques** meilleurs — d'où la ligne suivante.
- **Dégâts critiques** montent de 1 % à 7 % : ils ne valent rien sans chance de critique.
  C'est la mécanique voulue, celle de tous les ARPG : on monte le critique d'abord, et les
  dégâts critiques deviennent le multiplicateur de la fin de partie.
- **Vol de vie** : mesuré sur un combat entier (5 coups d'épée), pas sur un coup — c'est
  son cumul qui fait sa valeur.

### Fréquences

Tous les affixes ne sortent pas autant, comme les colonnes *frequency* de Diablo 2. Les
stats de confort sont communes, celles qui font les gros écarts sont rares : c'est ça qui
rend un objet mémorable, pas la taille du tirage.

| Fréquence | Affixes |
|---|---|
| 10 | FOR, DEX, CON, INT, armure, PV |
| 8 | SAG |
| 6 | CHA, dégâts d'arme, dégâts des sorts |
| 5 | chance critique, dégâts critiques |
| 3 | vol de vie |

Et chaque emplacement a sa liste : une arme peut porter des dégâts d'arme et du vol de
vie, une botte non ; un anneau porte tout sauf de l'armure et des dégâts d'arme.

### La note

La note se compte dans **la monnaie du budget** : chaque ligne vaut ce qu'elle apporte en
% de son axe, donc **un affixe plein vaut 12 points**, quel qu'il soit. Fini les
pondérations écrites à la main qui vieillissent mal, et fini la ligne qui paraît énorme
juste parce qu'elle s'affiche en pourcentage.

Le tout est multiplié par l'échelle de la puissance. Sans ça la note ne dirait que « bon
*pour sa puissance* » : une épée de Cuir 1 parfaite noterait autant qu'une épée
d'Astralite 5, et **on ne verrait jamais l'objet plus profond comme une amélioration** —
c'est exactement ce qui est arrivé au premier essai, et la progression s'est arrêtée net à
l'étage 11. `AffixBudgetTest.laNoteMonteAvecLaPuissance` monte la garde.

### Ce que les PV devaient rattraper

En calibrant les affixes, le héros de référence a montré un défaut de fond qui n'avait
rien à voir avec eux : **les PV ne suivaient pas les dégâts des monstres**.

| Étage | 1 | 10 | 40 | 100 |
|---|---|---|---|---|
| Coups encaissés avant de mourir (avant) | 6,8 | 3,7 | 2,1 | **1,7** |
| Coups encaissés (après) | 6,8 | 4,9 | 3,8 | **3,5** |

Les dégâts des monstres sont ×15,9 sur la partie et l'armure garde son taux de réduction
constant (c'était voulu) — mais les PV ne venaient que de `40 + 4 × CON`, qui ne monte que
×4. On finissait à deux coups de la mort à l'étage 100. Les gros affixes de PV de l'ancien
système masquaient le trou ; avec un budget honnête il est apparu tout de suite.

Le correctif tient en deux constantes : **PV de base 40 → 28**, et **chaque pièce
défensive donne 0,75 PV par point de son armure de base, à l'échelle de la puissance**
(soit 12 × puissance en PV pour casque + armure + bottes + bouclier). L'étage 1 est
inchangé au point près (51 PV), la fin de partie cesse de s'effondrer, et les affixes de
PV et de CON se calibrent enfin sur un sac de PV qui a du sens.

### Mesures après la refonte des affixes

Simulation jusqu'à l'étage 40 (30 profils par niveau d'adresse) :

| | Novice | Correct | Expert |
|---|---|---|---|
| Meilleur étage (médiane) | 14 | 19 | 27 |
| Meilleur étage (max) | 21 | 36 | 36 |
| Morts au total | 2268 | 1384 | 871 |
| Note d'équipement à l'étage 15 | 1435 | 1408 | 1169 |

La boucle voulue tient toujours : **au même étage, le novice porte un équipement mieux
noté que l'expert** (1435 contre 1169 à l'étage 15). L'équipement grimpe sans plateau, de
107 au départ à ~2900 à l'étage 35.

Mais le jeu est **plus dur qu'avant la refonte**, où les bots atteignaient l'étage 40.
C'est attendu : les anciens affixes étaient environ **5 fois au-dessus du budget** (une
caractéristique roulait 1–3 × (1 + 0,5 × (puissance − 1)), soit 9–27 points à la puissance
17 contre 3–5 aujourd'hui). Passer le budget de 12 % à 16 % a été mesuré : ça ne rend que
**2 étages** (novice 14 → 16, expert 27 → 29). Autrement dit le budget des affixes n'est
pas le bon levier pour la difficulté — **ce sont les courbes des monstres** (+22 % de PV et
+15 % de dégâts par étage) qu'il faudra regarder, ou le contenu qui manque encore : sets,
reliques multiples, consommables.

**Question ouverte** : viser à nouveau l'étage 40 pour un expert en adoucissant la courbe
des monstres ? Ou accepter un donjon plus serré, en comptant sur les sets et les reliques
de l'étape 3 pour rouvrir la progression ?

## Les reliques

Tout est dans `Relic` et `RelicBudget` (`Combat.kt`), gardé par `RelicTest`. L'outil de
mesure est `RoguelikeSimulationTest.relicsAtFixedGear` : chaque relique seule, à équipement
égal, contre « aucun sort ».

### Les dégâts suivent l'arme portée

Un sort se compte en **coups d'épée de référence** : un coefficient de 1 frappe comme l'épée
*normale* de la puissance de l'arme qu'on porte (quel que soit son type : un bâton compte
comme une épée de même puissance). Puis INT (+5 % par point) et les bonus « dégâts des
sorts » multiplient.

Pourquoi : l'ancienne Boule de feu faisait 6–9 à vie. Plus bas elle devenait plus faible
que l'épée et **la lancer faisait perdre un tour** — les bots allaient plus loin sans aucun
sort (médiane novice 25) qu'avec elle (16).

### Les quatre premières

| Relique | Recharge | Effet |
|---|---|---|
| Boule de feu | 3 | brûle 2 tours, un quart du coup par tour |
| Éclat de glace | 3 | jet de sauvegarde ; raté, la cible est **figée** 1 tour (délai) |
| Foudre | 5 | **paralyse** 3 tours : chaque attaque qui tombe demande un jet ; ratée, elle est perdue |
| Venin | 3 | une **dose** de poison (3 au plus) pendant 4 tours ; relancer renouvelle la durée |

Les dégâts ne sont écrits nulle part : `RelicBudget` les calcule (voir plus bas).

- Glace et foudre se distinguent : la glace **repousse** une attaque (le compteur est
  gelé, jamais une annulation), la foudre en **supprime** (le coup tombe à plat). La glace
  arrête tout, la foudre n'arrêtera pas les lanceurs de sorts quand il y en aura.
- Le venin frappe peu mais s'empile : c'est le sort des gros sacs de PV (boss).

### Les jets de dés, façon D&D

- **Affinités** : chaque monstre est *vulnérable* (dégâts ×2, −5 à ses jets), *normal*,
  *résistant* (dégâts ×0,5, +5 à ses jets) ou *immunisé* (ni dégâts, ni effet) à chaque
  élément. Rien ne les affiche : on les apprend en frappant (« Efficace ! », « Peu
  efficace… », « Immunisé ! »). C'est ce qui fait choisir ses deux reliques.

  | Monstre (provisoire) | Feu | Glace | Foudre | Poison |
  |---|---|---|---|---|
  | Rat | vulnérable | | | résistant |
  | Gobelin | | résistant | | vulnérable |
  | Squelette | résistant | | vulnérable | immunisé |
  | Orc | vulnérable | | résistant | |
  | Démon | immunisé | vulnérable | | résistant |

- **Jet de sauvegarde** contre le contrôle : le monstre lance **d20 + maîtrise + affinité**
  contre le **DD** du héros = 11 + modificateur d'INT ((INT − 10) / 2) + maîtrise. S'il
  n'atteint pas le DD, l'effet prend. **Le dé reste caché** (décision du 18/09) : on ne
  voit que « Résiste ! ». Le texte « 🎲 14 contre DD 13 » existe pour plus tard.
- **Les gestes pèsent sur les dés.** Jusque-là les deux couches étaient séparées : les
  gestes décidaient *combien* (critique, parade), les dés décidaient *si* (l'effet prend).
  Maintenant, le swipe au lancer d'un sort de contrôle compte aussi : « bien » ajoute +2
  au DD, « parfait » impose au monstre le **désavantage** de D&D (deux d20, il garde le
  pire) — le gel prend alors ~3 fois sur 4 au lieu d'une sur 2. Pour la foudre, le geste du
  lancer vaut pour tous les jets de la paralysie. Mesuré pour un joueur « correct » :
  glace 74 % → 76 %, foudre 72 % → 74 %. L'adresse compte, sans écraser l'équipement.
- **Maîtrise** : +2, puis +1 tous les 8 crans de puissance, comme les niveaux de D&D. Celle
  du héros suit son arme, celle du monstre l'étage. Avec l'équipement de l'étage et 10
  d'INT, le contrôle prend **une fois sur deux** ; ¾ contre un vulnérable, ¼ contre un
  résistant. L'INT devient la stat du contrôle, plus seulement des dégâts.
- **La rage** : un ennemi contrôlé **2 fois d'affilée** (sans avoir pu frapper entre-temps)
  enrage pendant 3 tours. Il réussit alors tous ses jets, son compteur descend de 2 par tour,
  mais il rate 35 % de ses coups. Elle remplace l'ancienne résistance de 2 tours après un
  contrôle.

Mesure (`relicsAtFixedGear`, étage 15, joueur correct, 3 combats enchaînés gagnés) :

| Aucun sort | Boule de feu | Éclat de glace | Foudre | Venin |
|---|---|---|---|---|
| 64 % | 71 % | 74 % | 72 % | 58 % |

Feu, glace et foudre se tiennent enfin. Il a fallu **un** réglage : au premier tableau
d'affinités, la glace était efficace contre l'orc et le démon (les deux qui frappent le plus
fort) et montait à 87 %. Leçon : **le tableau d'affinités pèse autant que les chiffres des
sorts**, il faut le répartir pour qu'aucun élément ne gagne partout. Le prix du contrôle a
été relevé d'après la mesure (gel 1 épée par tour, paralysie 0,8, fois la chance de prendre).

Le **venin** fait moins bien que rien : le squelette y est immunisé et le démon y résiste,
et les combats ordinaires sont trop courts pour que les doses s'empilent. C'est attendu
(c'est le sort des boss), mais ce sera à revoir quand les boss existeront.

Sur la partie entière (jusqu'à l'étage 100), les reliques profitent surtout aux joueurs
adroits : le novice fait comme sans sort (26 contre 25), le correct passe de 36 à 62,
l'expert de 57 à 100.

### Où on les trouve

Remplacé le 18/09/2026 : voir « Le grimoire », « Où les trouver ». (Avant : une relique
aux étages 2, 5, 9 et 14.) Une relique trouvée ne revient pas après une mort. Elle se porte
d'office s'il reste un emplacement libre, sinon elle attend dans l'inventaire (section
« Reliques » : toucher pour porter ou ranger).

Les anciennes sauvegardes gardent leur Boule de feu.

### Premier essai : aucun prix

Simulation jusqu'à l'étage 100 (`SIM_MAX_FLOOR=100`), 30 profils par niveau d'adresse,
250 000 tours de carte chacun. Les bots portent les deux premières reliques trouvées et en
lancent une dès qu'elle est prête.

| Meilleur étage (médiane) | Novice | Correct | Expert |
|---|---|---|---|
| Version d'avant (Boule de feu 6–9 à vie, étage 40 max) | 16 | 19 | 29 |
| Aucun sort | 25 | 36 | 57 |
| Reliques sans budget | 72 | 100 | 100 |
| Reliques, premier budget (glace à 0,36 épée) | 21 | 68 | 100 |
| Budget actuel + recharges gardées entre combats | 43 | 100 | 100 |
| **Jets de dés, affinités et rage** *(l'état actuel)* | **26** | **62** | **100** |

Sans budget, la glace frappait presque comme l'épée *et* figeait : un sort ne coûtait jamais
le coup d'épée qu'il remplaçait. L'expert arrivait à l'étage 100 en mourant une fois.

### Le budget : ce qui tient, ce qui ne tient pas

La règle posée (`RelicBudget`) : **un sort vaut le coup d'épée qu'il remplace plus une prime
de 0,12 épée par tour de recharge** — une relique vaut un affixe plein. L'effet se paie sur
les dégâts directs. Pour le feu et le poison, ça tient. **Pour le contrôle, non** :

| Étage 15, joueur correct | PV perdus par combat gagné | 3 combats enchaînés gagnés |
|---|---|---|
| Aucun sort | 21,3 % | 63,6 % |
| Boule de feu | 20,0 % | 69,9 % |
| Venin | 21,1 % | 66,3 % |
| Glace, 1 tour, coup 0,36 épée | 21,9 % | 58,3 % |
| Glace, 2 tours, coup 0,76 épée | 15,7 % | 83,8 % |
| Glace, 1 tour, coup 1,06 épée *(l'état actuel)* | 16,6 % | 82,4 % |
| Foudre, 1 tour sur ⚔ 1, coup 0,48 épée | 15,0 % | 88,4 % |

Deux raisons, qui tiennent au mécanisme et pas aux chiffres :

1. **Les recharges repartent à zéro à chaque combat.** Un combat dure ~4 tours, donc chaque
   relique s'y lance au moins une fois, quelle que soit sa recharge. Une « prime par tour
   de recharge » ne veut presque rien dire.
2. **Le contrôle ne vaut pas des tours, il vaut des attaques**, et pas de façon linéaire.
   Sur ~4 tours, retirer 1 ou 2 tours à un ennemi lui retire une grosse part de ses coups.
   La foudre sur ⚔ 1 supprime une attaque entière, qui vaut autant de coups d'épée que la
   cadence de l'ennemi (jusqu'à 5 dans un groupe de 3).

Deux réglages ratés d'affilée : on ne continue pas à tourner les boutons, on change le
mécanisme. Voir « Le contrôle : des jets, des résistances et la rage » juste en dessous.

Les boss n'existent pas encore : aucune de ces mesures ne les compte.

### Le contrôle : des jets, des résistances et la rage

Décidé après les mesures ci-dessus (18/09/2026). Le fil rouge : **plus il y a d'affixes, plus
c'est de l'optimisation d'équipement, et c'est voulu.** Les résistances des monstres doivent
obliger à jouer telle ou telle relique.

- **Les recharges se gardent d'un combat à l'autre** *(fait)*. Elles avancent d'un tour par
  tour de combat, par tour de repos, et tous les 8 pas sur la carte. Le repos recharge donc
  les sorts (on peut se reposer PV pleins si une relique se recharge), et il reste risqué.
  Les reliques portées s'affichent en haut à gauche de la carte, avec leurs tours restants.
  *Mesuré* : ça ne suffit pas à calmer le contrôle (glace 82 % → 81 %). Un combat dure assez
  longtemps pour que chaque relique ressorte. Ce sont les jets qui feront le travail.
- **Les monstres ont des résistances par élément** (feu, glace, foudre, poison), selon leur
  type et leur zone : un squelette ne craint pas le poison, un démon résiste au feu… La
  résistance réduit les dégâts de l'élément **et** la chance que son effet prenne.
- **Le contrôle devient un jet** : chance = chance de base de la relique × (1 − résistance)
  + bonus des affixes.
  - Glace : un jet au lancer. Réussi, la cible est figée — c'est un **délai** (son compteur
    s'arrête), jamais une annulation.
  - Foudre, façon Pokémon : paralysie de plusieurs tours, et **un jet à chaque attaque qui
    tombe** pour savoir si elle est perdue.
- **La rage remplace la résistance fixe de 2 tours** : un ennemi contrôlé 2 ou 3 fois de
  suite s'énerve. Pendant sa rage, on ne peut plus le contrôler, il frappe **plus vite**
  mais **moins précis**.
- **La précision** n'existe pas encore : aujourd'hui toute attaque ennemie touche (la parade
  ne fait que réduire). Il faut une chance de toucher aux monstres, que la rage abaisse.
- **Des affixes de sorts**, sur la même règle que les autres (budget + mesure) : chance
  d'effet (gel, paralysie…), pénétration des résistances, durée des effets (+1 tour de
  poison), critique des sorts, **exécution** (une chance d'achever un petit monstre déjà
  bien entamé).
- **Des affixes propres à une relique**, qui forcent des choix d'équipement : « Boule de feu :
  +X % de critique », « Venin : +1 dose au maximum », « Éclat de glace : fige tous les
  ennemis » (celui-là est assez fort pour être un légendaire).

L'ordre de travail :

1. Recharges gardées d'un combat à l'autre — **fait**.
2. Résistances des monstres, jets de contrôle (glace au lancer, foudre par attaque) et
   rage — **fait** (voir « Les jets de dés, façon D&D »).
3. Précision des monstres (la rage la fait baisser) — devenue l'étape A des archétypes,
   ci-dessous : la CA et le jet d'attaque.
4. Affixes de sorts généraux.
5. Affixes propres à une relique, probablement avec les légendaires.

## Le grimoire : les reliques *(tout le catalogue codé le 18/09/2026)*

Discuté le 18/09/2026. Les quatre reliques actuelles suivent toutes le même moule (des
dégâts + un statut) : seul l'élément les distingue. Le grimoire ajoute des **familles**
(renforcement, affaiblissement, arme enchantée, préparation / coup final, protection…) et
surtout des **combos**. Le principe posé par le propriétaire :

> **N'importe quel archétype peut jouer n'importe quelle relique.** La caractéristique
> d'une relique l'oriente vers un archétype, mais les meilleurs builds se trouvent en
> croisant, et on ne les découvre qu'en connaissant le jeu.

L'équilibrage vient après, en jouant et en mesurant (`RelicBudget`, `relicsAtFixedGear`).
Les recharges écrites ici sont des ordres de grandeur.

Tranché par le propriétaire le 18/09/2026 : **tout le catalogue est retenu, sauf la Pluie
d'or** (et donc la résonance Pot-de-vin). **Les soins en combat sont permis** : une relique
de soin prend un des deux emplacements, c'est un choix du joueur — forte, mais limitée par
là. Les reliques se trouvent **uniquement en explorant** (voir « Où les trouver »). Et il
faut que la base tienne debout : **on en ajoutera d'autres**.

Dans le code (`Combat.kt`) : une relique = un **élément** (affinités, réactions), une
**caractéristique**, une **cible** (une, toutes, en chaîne, soi-même) et **un effet**
(`RelicEffect`). Les réactions sont dans `Reaction`, les paires dans `Resonance`. Tous les
dégâts infligés à un ennemi passent par `Combat.wound` (la fracture et la marque y sont
réglées une fois pour toutes). Garde : `GrimoireTest`.

Inspirations : les réactions élémentaires de *Genshin Impact* et les surfaces de
*Divinity: Original Sin 2* (eau + foudre, feu + poison), les duos de dieux de *Hades*
(deux bénédictions qui s'associent), la Vulnérabilité et la Faiblesse de *Slay the Spire*,
les cris du barbare et les épines du paladin de *Diablo II*, la Marque du chasseur de D&D,
le Stop / Lenteur / Zeni-nage de *Final Fantasy*.

### Trois couches de combos

1. **Les états** : un sort pose un état sur la cible (ou sur le héros). Les quatre d'aujourd'hui
   (brûlé, figé, paralysé, empoisonné) plus quelques nouveaux, partagés par tout le grimoire.
2. **Les réactions** : un sort qui touche une cible **déjà** dans un certain état fait quelque
   chose en plus. Toujours actives, pour tout le monde : c'est le savoir du joueur, pas son
   équipement. Rien ne les annonce à l'avance, on les découvre (comme les affinités).
3. **Les résonances** : porter **deux reliques précises ensemble** donne un bonus nommé, un
   effet en plus **et** une caractéristique (+2). C'est le « set de reliques ». Cachées
   jusqu'à ce qu'on porte la paire une première fois ; ensuite, un carnet les liste.

Et par-dessus, le **Spécial de l'archétype** : le Coup mortel du voleur aime les cibles
figées ou empoisonnées, la Garde du guerrier aime les Épines, l'Image miroir du mage aime
tout ce qui fait durer le combat. Un guerrier qui porte Éclat de glace + Séisme, c'est un
build ; ce n'est écrit nulle part.

### Les nouveaux états

| État | Sur | Effet |
|---|---|---|
| **Trempé** | ennemi | la foudre et la glace prennent plus facilement (−3 au jet de sauvegarde) ; le feu l'efface (voir Vapeur) |
| **Fracturé** | ennemi | encaisse +25 % de **tous** les dégâts (la Vulnérabilité de *Slay the Spire*) |
| **Affaibli** | ennemi | ses coups font −30 % |
| **Saignement** | ennemi | perd des PV **chaque fois qu'il attaque** (pas à chaque tour) : le geler ou le paralyser le fait moins saigner, le laisser frapper le tue |
| **Marqué** | ennemi | compte comme **exposé** pour le Coup mortel ; les critiques contre lui font plus mal |
| **Aveuglé** | ennemi | attaque avec désavantage (deux d20, le pire gardé), comme la rage mais sans la vitesse |
| **Barrière** | héros | absorbe les prochains dégâts, jusqu'à un plafond |
| **Épines** | héros | renvoie une part de chaque coup reçu |
| **Arme enchantée** | héros | ses coups d'arme portent un effet pendant quelques tours |

Tous codés, plus le **charmé** (sa prochaine attaque frappe un allié) venu avec le Charme.
Le saignement ronge au moment où l'ennemi attaque : s'il en meurt, le coup ne part pas.
Les états qui durent perdent un tour **en fin de tour ennemi** : « affaibli 2 tours » couvre
les deux prochaines attaques ennemies. La marque, elle, dure jusqu'à la mort, et une seule
cible est marquée à la fois.

**Le gel a dû changer pour que les combos existent.** Un gel d'un tour se consumait pendant
le tour ennemi : le héros ne voyait jamais une cible figée, donc ni Bris, ni Fonte, ni Coup
mortel sur un figé. Désormais la glace **reste sur la cible jusqu'à son tour suivant**
(`Enemy.thawing`) : elle agit normalement, mais pendant le tour du héros elle compte comme
figée.

### Les réactions

| Réaction | Quand | Effet |
|---|---|---|
| **Explosion** | feu sur un **empoisonné** | consomme toutes les doses : leurs dégâts restants tombent d'un coup (le Catalyseur de *Slay the Spire*) |
| **Fonte** | feu sur un **figé** | le gel se brise, dégâts ×1,5 : on échange le contrôle contre un gros coup |
| **Vapeur** | feu sur un **trempé** | pas de brûlure, mais un brouillard : **tous** les ennemis aveuglés 1 tour (*Divinity*) |
| **Électrocution** | foudre sur un **trempé** | dégâts ×1,5, et le jet de paralysie se fait au désavantage |
| **Givre** | glace sur un **trempé** | le gel dure 2 tours au lieu d'un |
| **Bris** | **sort** physique (Séisme, Brise-armure…) sur un **figé** | dégâts ×2, le gel se brise |
| **Cautérisation** | feu sur un **saignant** | la plaie se ferme (plus de saignement), mais le coup fait ×1,5 |
| **Purification** | sacré sur un **empoisonné** | le poison devient lumière : ses doses restantes tombent d'un coup, en dégâts **sacrés** |

Toutes codées (les deux dernières ajoutées avec le lot 2). Une réaction se lit sur l'état **d'avant** le sort, et un monstre
immunisé à l'élément ne réagit pas. Précisions apparues en codant :
- **Le Bris ne vient pas de l'attaque de base.** Sinon « glace puis épée » doublait la valeur
  de l'Éclat de glace à chaque lancer, gratuitement.
- La **Pluie glacée** est de la glace (les gobelins y résistent) mais elle trempe au lieu de
  figer : l'élément décide des réactions et des affinités, l'effet est à part.
- L'**eau éteint le feu** : tremper une cible qui brûle éteint la brûlure.
- L'**électrocution** laisse l'eau (on peut électrocuter plusieurs fois) ; le **givre** la
  consomme (l'eau a gelé) ; la **vapeur** aussi (elle s'est évaporée).
- Les réactions et les résonances **ne sont pas au budget** : elles récompensent le savoir.

Garde-fou : la rage continue de s'appliquer. Figer, électrocuter, re-figer reste impossible
à l'infini.

### Le catalogue

Deux nouvelles « couleurs » de sorts, en plus des quatre éléments :
- **Sans élément** (physique, arcane) : jamais résisté, jamais efficace. La valeur sûre
  contre un monstre qu'on ne connaît pas encore.
- **Sacré** : un cinquième élément, pensé pour les **Cryptes** (étages 21–40). Le squelette
  **et le démon** y sont vulnérables (comme le radiant de D&D contre les fiélons ; d'abord
  écrit « démons résistants », changé pour la Purification, voir plus bas).

**Le poison, décidé le 18/09 par le propriétaire** : il empoisonne et ça suffit en début de
partie ; il devient surtout un **déclencheur de combos** plus tard (après l'étage 40). C'est
la Purification qui le rend précieux contre les démons : ils résistent au poison mais craignent
le sacré, donc empoisonner puis purifier fait quatre fois les dégâts restants. On ne refait
pas les Lames empoisonnées.

**FOR, la force (le guerrier en premier)**

| Relique | Rech. | Effet | Combos |
|---|---|---|---|
| **Brise-armure** ✅ | 3 | coup sans élément, la cible est **fracturée** 3 tours | profite à tout le monde : le démarreur universel |
| **Cri de guerre** ✅ | 5 | tous les ennemis **affaiblis** 2 tours ; les 2 prochains coups d'arme renforcés (+55 % au départ, le reste du budget) | Garde, Épines |
| **Séisme** ✅ | 4 | frappe **tous** les ennemis, dégâts modestes, sans élément | **Bris** sur chaque figé ; résonance Avalanche |
| **Saignée** ✅ | 3 | coup d'arme + **saignement** | punit les ennemis rapides et les enragés (ils frappent plus, ils saignent plus) |
| **Tourbillon** ✅ | 4 | un coup d'arme sur tous les ennemis (le budget de zone le met à ~55 % d'un coup) | les Lames empoisonnées touchent tout le monde, pour une seule charge (le Brise-armure aussi les porte) |

**DEX, la dextérité (le voleur en premier)**

| Relique | Rech. | Effet | Combos |
|---|---|---|---|
| **Lames empoisonnées** ✅ | 5 | les 3 prochains coups d'arme (riposte comprise) ajoutent chacun une dose ; se lance sans geste | Venin, Explosion, Coup mortel |
| **Marque du chasseur** ✅ | 3 | petit coup sans élément, cible **marquée** jusqu'à sa mort ; si elle meurt, la marque saute sur un autre | Coup mortel à chaque recharge |
| **Dagues en éventail** ✅ | 3 | petites dagues sur tous les ennemis ; chaque critique fait **saigner** | la DEX (critique) et Saignée |
| **Bombe fumigène** ✅ | 5 | tous les ennemis **aveuglés** 2 tours ; le prochain coup d'arme est une attaque sournoise (la cible compte comme exposée) | le voleur esquive déjà : il ne se fait plus toucher |
| **Fiole d'acide** ✅ | 3 | une dose de poison + **fracturé** 2 tours | le pont entre le poison et le guerrier |

**INT, l'intelligence (le mage en premier)**

| Relique | Rech. | Effet | Combos |
|---|---|---|---|
| **Pluie glacée** ✅ | 4 | tous les ennemis **trempés** 3 tours, dégâts faibles | pose Vapeur, Électrocution, Givre : le démarreur du mage |
| **Chaîne d'éclairs** ✅ | 4 | frappe la cible puis **rebondit** sur les autres (−30 % par rebond) ; sur un trempé, le rebond ne perd rien | Pluie glacée (résonance Orage) |
| **Cristallisation** ✅ | 3 | dégâts modestes ; contre un **figé**, énormes (et le gel se brise) | Éclat de glace, et le Coup mortel juste avant |
| **Météore** ✅ | 7 | tombe **2 tours plus tard** sur tous les ennemis, très fort | glace et foudre pour retenir les ennemis jusqu'à l'impact |
| **Projectile magique** ✅ | 1 | trois traits sans élément, répartis au hasard | le sort de secours : jamais résisté, recharge presque nulle |
| **Bouclier arcanique** ✅ | 4 | **barrière** ; si elle tient jusqu'à ton prochain tour, tes reliques gagnent un tour de recharge | le contresort du mage, Image miroir |

**SAG, la sagesse (aucun archétype : c'est la carac. des recharges)** — le Sablier a été retiré (voir plus bas)

| Relique | Rech. | Effet | Combos |
|---|---|---|---|
| **Lumière sacrée** ✅ | 3 | sacré, la cible est **aveuglée** 1 tour | les Cryptes ; **Purification** sur un empoisonné ; Aube |
| **Régénération** ✅ | 5 | soigne un peu à chacun de tes 3 prochains tours | permis (18/09) : elle prend un emplacement de relique, c'est le prix |

**CON et CHA (les caractéristiques qui n'ont pas encore de sort)** — le Pacte de sang a été retiré (voir plus bas)

| Relique | Carac. | Rech. | Effet | Combos |
|---|---|---|---|---|
| **Peau de pierre** ✅ | CON | 5 | armure ×2 pendant 2 tours + **épines** | Garde, Cri de guerre |
| **Charme** ✅ | CHA | 5 | jet ; raté, sa prochaine attaque frappe un autre ennemi (seul, il la perd) | les groupes de 3 |

### Les résonances (paires de reliques)

Toutes donnent **+2** à une caractéristique, en plus de leur effet. ✅ = codée.

| Résonance | Paire | Bonus | Effet |
|---|---|---|---|
| **Alchimie** ✅ | Boule de feu + Venin | +2 INT | l'Explosion éclabousse les autres ennemis (une dose chacun) |
| **Orage** ✅ | Pluie glacée + Chaîne d'éclairs | +2 INT | la Chaîne paralyse 1 tour chaque trempé qu'elle électrocute. *(L'idée d'abord écrite, « les rebonds continuent », ne faisait rien : avec 3 ennemis au plus, la chaîne les touche déjà tous.)* |
| **Avalanche** ✅ | Éclat de glace + Séisme | +2 FOR | le Séisme peut figer 1 tour (jet de sauvegarde) ceux qu'il ne brise pas. *(« Le Bris frappe les voisins » est tombé : le Séisme les frappe déjà.)* |
| **Zéro absolu** ✅ | Éclat de glace + Cristallisation | +2 INT | la Cristallisation ne brise plus le gel |
| **Corrosion** ✅ | Lames empoisonnées + Fiole d'acide | +2 DEX | 5 doses au plus au lieu de 3 |
| **Meute** ✅ | Marque du chasseur + Dagues en éventail | +2 DEX | chaque dague qui touche la cible marquée est un critique |
| **Charge du bélier** ✅ | Cri de guerre + Brise-armure | +2 FOR | le Brise-armure frappe aussi tous les ennemis affaiblis |
| **Hémorragie** ✅ | Saignée + Tourbillon | +2 CON | le Tourbillon fait saigner tout le monde |
| **Aube** ✅ | Lumière sacrée + Régénération | +2 SAG | chaque soin brûle aussi les morts-vivants |
| **Tempête de feu** ✅ | Boule de feu + Pluie glacée | +2 INT | la Vapeur aveugle 2 tours au lieu d'un |
| **Discorde** ✅ | Charme + Bombe fumigène | +2 CHA | un ennemi aveuglé ne résiste pas au Charme *(ajoutée : le Charme n'avait plus de paire sans la Pluie d'or)* |
| **Rempart** ✅ | Peau de pierre + Cri de guerre | +2 CON | les épines renvoient le double *(ajoutée)* |

On a volontairement des paires qui croisent les archétypes (Avalanche : une relique de
mage, une de guerrier). Les résonances pourront plus tard devenir des affixes de légendaire
(« compte comme portant Séisme »), ce qui ouvre des builds à trois reliques.

La découverte : la première fois qu'une paire est portée, le journal l'annonce et elle entre
au **carnet** (sauvegardé). L'inventaire affiche la résonance active (bonus + effet) et le
carnet ; celles qu'on n'a jamais portées ne montrent que leur nombre. Le bonus de +2 entre
dans la caractéristique comme l'équipement : il pèse sur les dégâts, les DD, la CA…

### Où les trouver

**Décidé le 18/09/2026 : par l'exploration uniquement**, jamais sur un monstre. C'est rare,
et c'est ce qui donne de l'intérêt à la carte ; comme on garde tout en mourant et que les
étages se refont, on finit par tout trouver.

Codé (`RoguelikeGame.RELIC_CHANCE`, `FIRST_RELIC_FLOOR`) : la première relique est garantie à
l'étage 2 (pour découvrir les sorts) ; ensuite, **15 %** des étages cachent une relique au
bout du cul-de-sac le plus éloigné du départ, tirée parmi celles qu'on n'a pas. Un tirage par
zone (le Sacré dans les Cryptes…) reste possible plus tard.

### Dans quel ordre les coder

1. **Les fondations** — **faites** : les nouveaux états, les réactions, le mécanisme des
   résonances, leur carnet dans l'inventaire.
2. **Le lot 1** — **fait** : Brise-armure, Cri de guerre, Séisme ; Marque du chasseur, Lames
   empoisonnées ; Pluie glacée, Chaîne d'éclairs ; et cinq résonances (Alchimie, Tempête de
   feu, Orage, Avalanche, Charge du bélier).
3. **Le lot 2** — **fait** le 18/09 : les 15 autres reliques, le saignement, la barrière,
   les épines, le charme, le Sacré et l'Arcane, deux réactions (Cautérisation, Purification)
   et neuf résonances. Puis le **Pacte de sang** et le **Sablier** ont été retirés (pièges
   mesurés, décision du propriétaire), avec leurs résonances Pacte et Fin des temps :
   **24 reliques, 12 résonances**. Ajouter une relique :
   une ligne dans `Relic`, ses textes, et si besoin un nouvel `RelicEffect` (son prix dans
   `RelicBudget.effectValue`, qu'un test oblige à écrire). Un nouvel état se pense **avec
   ses réactions**.
4. Puis l'étape D, les sets d'équipement : leurs bonus auront enfin des sorts à modifier.

### Lot 1 : la mesure

`relicsAtFixedGear` (chaque relique **seule**, à équipement égal, joueur « correct »,
victoires sur 3 combats enchaînés). Le bot lance une relique dès qu'elle est prête : il ne
joue **aucun combo** ni aucune résonance. Ces chiffres sont donc un plancher.

| Étage | Aucun sort | Feu | Glace | Foudre | Venin | Brise-armure | Cri | Séisme | Marque | Lames | Pluie | Chaîne |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 8 | 78 % | 81 % | 86 % | 85 % | 76 % | 78 % | 85 % | 84 % | 81 % | 80 % | 82 % | 82 % |
| 15 | 74 % | 80 % | 80 % | 81 % | 70 % | 72 % | 80 % | 81 % | 75 % | 73 % | 76 % | 79 % |
| 30 | 69 % | 75 % | 79 % | 78 % | 63 % | 68 % | 77 % | 77 % | 72 % | 67 % | 74 % | 75 % |
| 60 | 62 % | 72 % | 82 % | 79 % | 62 % | 68 % | 64 % | 77 % | 70 % | 54 % | 79 % | 79 % |

- Les sorts de zone (Séisme, Cri, Pluie, Chaîne) tiennent : partager la valeur sur le groupe
  moyen (1,65 ennemi) marche.
- **Brise-armure et Marque** sont au niveau d'« aucun sort » : leur valeur est dans les
  combos (préparer ses propres coups, le Coup mortel), que ce bot ne joue pas. Au premier
  essai ils faisaient moins bien : le bot fracturait le plus solide et frappait un autre. Il
  vise désormais sa propre cible avec eux.
- **Les Lames empoisonnées** restent en dessous en profondeur : squelettes immunisés, démons
  résistants, et le tour perdu à les lancer. Un réglage fait (on ne paie plus que les doses
  tombées **pendant** l'enchantement, 6 au lieu de 15) ; pas de second réglage des chiffres.
  Si ça ne suffit pas en jouant, **changer le mécanisme** : en faire une action gratuite (l'
  « action bonus » de D&D), avec un prix à la hauteur.
- Le Venin reste le sort des longs combats, comme avant.

### Lot 2 : la mesure, et deux règles corrigées

**Le banc a d'abord tourné 30 minutes sans finir.** Ce n'était pas la mesure, c'étaient des
combats **éternels** : avec l'équipement de l'étage 60, la SAG ramène les recharges à 1 tour ;
le bot relançait alors Bouclier, Régénération ou Charme **à chaque tour** et ne frappait plus
jamais, et le monstre ne le blessait plus non plus. Derrière le bot, deux vraies failles :
- **Le Charme ne déclenchait jamais la rage** : l'attaque détournée remettait la série de
  contrôles à zéro. Un monstre seul pouvait être bloqué sans fin. Corrigé : l'attaque charmée
  compte comme un contrôle, deux charmes d'affilée le font enrager.
- **Le Bouclier arcanique se rechargeait lui-même** avec son propre bonus. Corrigé : il ne
  recharge que les **autres** reliques.

Le banc (`relicsAtFixedGear`) est refait pour ne plus jamais attendre : un combat qui dépasse
300 tours est arrêté et **signalé** (« ⚠ combats bloqués » : c'est un bug à chercher), les
reliques tournent en parallèle, 1 000 séries par défaut (±1,5 point), une ligne par relique.
**3 secondes** au lieu de 30 minutes. `SIM_RELICS=METEOR,CHARM` ne mesure que celles-là,
`SIM_SERIES` et `SIM_FLOORS` règlent la précision et les étages. Le bot ne lance plus deux
tours de suite une action qui ne frappe pas (sort de soutien, Garde, doubles).

Victoires sur 3 combats enchaînés, joueur « correct », chaque relique seule, sans combo :

| Relique | Ét. 8 | 15 | 30 | 60 |
|---|---|---|---|---|
| *aucun sort* | *79* | *72* | *70* | *62* |
| Bouclier arcanique | 94 | 92 | 89 | 93 |
| Régénération | 92 | 89 | 86 | 79 |
| Lumière sacrée | 88 | 87 | 83 | 81 |
| Éclat de glace | 86 | 80 | 79 | 84 |
| Foudre | 84 | 81 | 79 | 79 |
| Cristallisation | 82 | 78 | 76 | 81 |
| Projectile magique | 82 | 76 | 75 | 83 |
| Pluie glacée | 82 | 77 | 75 | 78 |
| Chaîne d'éclairs | 83 | 79 | 75 | 77 |
| Séisme, Tourbillon | 85 | 81 | 77 | 76 |
| Cri de guerre | 84 | 79 | 79 | 75 |
| Dagues en éventail | 82 | 80 | 74 | 75 |
| Météore | 82 | 80 | 76 | 74 |
| Boule de feu | 82 | 80 | 76 | 73 |
| Marque du chasseur | 81 | 75 | 73 | 71 |
| Brise-armure | 78 | 71 | 68 | 67 |
| Saignée | 76 | 69 | 66 | 67 |
| Venin | 74 | 70 | 66 | 63 |
| Lames empoisonnées | 81 | 74 | 69 | 61 |
| Charme | 80 | 76 | 70 | 62 |
| Fiole d'acide | 74 | 72 | 64 | 59 |
| Bombe fumigène | 77 | 73 | 71 | 59 |
| Peau de pierre | 74 | 66 | 61 | 51 |

À lire, pas encore à régler (l'équilibrage vient en jouant) :
- **Trop forts seuls** : Bouclier arcanique et Régénération (la survie compte double dans
  cette mesure), Lumière sacrée (squelette et démon y sont vulnérables).
- **Pièges** (sous « aucun sort ») : le **Pacte de sang** (les PV payés restent perdus pour
  les combats suivants) et le **Sablier** (seul, son bonus de recharge ne sert à rien) sont
  **retirés du jeu** (décision du 18/09). La **Peau de pierre** est gardée : ses épines vont
  avec la Représaille du guerrier (Garde, blocage), à remesurer avec un guerrier.
- Les sorts de préparation (Brise-armure, Marque, Charme, Bombe, Fiole, poison) sont au niveau
  d'« aucun sort » : leur valeur est dans les combos, que ce bot ne joue pas.

### Les combos rendent-ils le jeu trop facile ?

Question du propriétaire (18/09). Le bot de base ne fait **aucun** combo : il lance la
première relique prête, sans regarder l'état des monstres. On lui a ajouté un **joueur de
combos** (`comboChoice`) qui connaît les règles, pas des scripts par paire : il **déclenche**
une réaction dès qu'une relique prête en a une à déclencher, sinon il **prépare** l'état
dont son autre relique a besoin si elle sera prête au tour suivant. Le banc
`combosAtFixedGear` joue chaque paire qui combine deux fois, au hasard puis en combo
(5 secondes). Extraits (victoires sur 3 combats, « aucun sort » : 74 / 69 / 63) :

| Paire | Ét. 15 | 30 | 60 |
|---|---|---|---|
| Éclat de glace + Brise-armure | 83 → 84 | 80 → 85 | 83 → **90** |
| Éclat de glace + Saignée | 80 → 86 | 79 → 84 | 83 → 88 |
| Éclat de glace + Cristallisation (Zéro absolu) | 91 → 93 | 88 → 93 | 90 → 93 |
| Éclat de glace + Séisme (Avalanche) | 88 → 90 | 86 → 89 | 89 → 92 |
| Venin + Lumière sacrée (Purification) | 81 → 79 | 73 → 77 | 71 → 76 |
| Pluie + Chaîne (Orage) | 92 → 91 | 90 → 90 | 89 → 92 |
| Lames empoisonnées + Boule de feu | 78 → 79 | 73 → 73 | 70 → **58** |
| Cri de guerre + Brise-armure (Charge du bélier) | 82 → 82 | 75 → 72 | 70 → **62** |

Réponse : **non, pas trop facile.** Jouer les combos rapporte de 0 à 7 points, jamais un
écart qui casse le jeu. Ce qui est fort, ce sont certaines **paires** en soi (la glace avec
presque tout, autour de 90 %), qu'on les joue bien ou pas. Deux enseignements :
- Préparer avec un sort **qui ne frappe pas** coûte un tour ; en profondeur, où les combats
  sont durs, ce tour se paie (Lames + feu, Cri + Brise-armure perdent en combo).
- Les résonances sans réaction (Meute, Hémorragie, Aube, Rempart, Corrosion) ne changent
  rien au jeu du bot : leur effet est passif, ou demande un ordre que ce bot ne cherche pas.

Partie entière jusqu'à l'étage 100 (`simulate`, 30 profils par niveau, les bots portent les
deux premières reliques trouvées, sans combo) : médianes novice / correct / expert
**31 / 71 / 89**, contre 31 / 57 / 100 après l'étape C. Dans le bruit de 30 profils ; l'expert
perd des profils qui plafonnent au nombre de tours de carte de la simulation (16 blocages),
pas des morts. Les reliques sont désormais plus rares (15 % des étages au lieu de 4 étages
fixes) : à surveiller en jouant.

Après le lot 2 (26 reliques, sans la Représaille du guerrier) : **37 / 70 / 100**, l'expert
retrouve l'étage 100 ; les blocages de l'expert passent de 16 à 9.


## La jauge : des tours calculés, pas des tours fixes *(décidé le 18/09/2026, à coder)*

Idée du propriétaire, née d'une question sur les sorts qui ne frappent pas (« pas
complètement gratuits ») : au lieu de tours fixes (le héros, puis tous les monstres),
**chacun a une jauge** qui se remplit à sa **vitesse** ; quand elle est pleine, c'est son
tour. C'est le **CTB de FFX**, pas l'ATB de FF7 :

> **Le temps ne s'écoule pas en continu.** Il est **figé pendant les actions** et pendant que
> le joueur choisit : rien ne presse. Entre deux actions, le moteur fait avancer toutes les
> jauges jusqu'à la prochaine pleine. (Le propriétaire déteste devoir choisir vite.)

- **Chaque action vide la jauge plus ou moins** : l'attaque la vide entièrement, un sort qui
  ne frappe pas seulement en partie (on rejoue plus vite), un sort lourd (Météore) peut
  coûter plus. Le coût dépend de la **classe** et du **stuff** (le mage lance moins cher,
  des affixes du type « incantation rapide »).
- **La Vitesse**, une nouvelle caractéristique d'équipement, donc **un affixe de plus**.
  L'armure lourde ralentit, la légère accélère. Les monstres ont leur vitesse (par type, et
  qui **monte avec les étages** : c'est l'idée de « vitesse de jeu » notée plus haut).
- **Une barre d'ordre des tours** à l'écran, comme FFX ; toucher une action montre où
  tomberait son prochain tour. Elle remplace les « ⚔ 2 » au-dessus des monstres.
- **Les durées** (poison, brûlure, gel, rage…) se comptent **aux tours de la victime**,
  comme FFX : le poison ronge quand c'est son tour. **Les recharges** des reliques se
  comptent **aux tours du héros** : un héros rapide recharge aussi plus vite.
- **Le contrôle joue sur les jauges** : le gel arrête la jauge, on peut la **repousser**,
  Hâte et Lenteur changent la vitesse, la rage la double. Le Sablier retrouverait un sens.
- Le « demi-tour » essayé juste avant (un sort qui ne frappe pas ne fait avancer les
  monstres que d'un demi-cran) est **abandonné** : c'est un cas particulier de la jauge.

Par étapes :
1. **Le moteur de jauges**, à vitesse normale et coûts pleins : les combats doivent se
   dérouler comme aujourd'hui (tests et simulation comme témoins — seules les durées « aux
   tours de la victime » changent un peu les chiffres).
2. **La barre d'ordre** à l'écran.
3. **Les coûts différents** (sorts de soutien moins chers, modulés par la classe).
4. **La Vitesse** et son affixe ; la vitesse des monstres par type et par étage.
5. **Les effets sur les jauges** : gel, repousser, Hâte, Lenteur.

## Les archétypes : le stuff fait la classe

Décidé le 18/09/2026. Trois archétypes pour commencer, **aucune classe choisie** : comme
dans Diablo, c'est ce qu'on porte qui fait la classe, et un hybride reste possible (le
multiclasse de D&D).

| | Guerrier | Voleur | Mage |
|---|---|---|---|
| Caractéristique | FOR | DEX | INT |
| Armure | lourde : il **encaisse** (beaucoup d'armure, la DEX ne compte plus dans la CA) | légère : il **évite** (CA + DEX) | tissu : peu de défense, compensée par les sorts |
| Défense (gestes) | parade + **blocage** au bouclier | **esquive**, et une esquive parfaite déclenche une **contre-attaque** | **illusions** (image miroir), sort *Bouclier* |
| Attaque | gros coups, charge | **coups mortels** sur une cible empoisonnée, figée ou entamée | contrôle et éléments |
| Reliques | cris, frappes (à inventer) | **Venin**, lames empoisonnées | feu, glace, foudre |

- **Deux défenses** (écart assumé avec D&D, où l'armure ne fait que rater) : la **CA**
  décide si un coup touche, l'**armure** réduit les dégâts du coup qui touche. C'est ce qui
  sépare le guerrier (il encaisse) du voleur (il évite) ; en pur D&D, les plaques donnent la
  meilleure CA et les deux se ressembleraient.
- **Chaque relique a sa caractéristique** : ses dégâts et son DD la suivent (le Venin
  suivra la DEX, pas l'INT) — comme le DD de D&D, 8 + maîtrise + la caractéristique de la
  classe.
- **Le bouton « Spécial » devient la capacité d'archétype**, donnée par le set porté
  (blocage, contre-attaque, image miroir…).
- **Les sets** : chaque zone a un set par archétype (rat-guerrier, rat-voleur, rat-mage…),
  bonus à 2 / 4 / 6 pièces. **Tous tombent partout, tout le temps.** La zone en recommande
  un par les faiblesses de ses monstres, sans jamais l'imposer — la clé souple.

### Étape A : la CA et le jet d'attaque *(faite)*

> **Pour le joueur, pas de jargon de D&D** (décidé le 18/09) : la CA s'affiche en **esquive**,
> un pourcentage (la chance que les monstres de l'étage ratent ; 1 point de CA = 5 %).
> Les descriptions des reliques ne parlent plus de DD ni de jet de sauvegarde, mais de
> cible qui « résiste parfois ». Le code et ce document gardent les noms de D&D.

- **Trois poids** sur le casque, l'armure et les bottes, avec chacun ses noms de pièces (pas
  d'adjectif à accorder) : tissu (Capuche, Robe, Sandales ; armure ×0,6), léger (Coiffe,
  Brigandine, Bottes ; armure ×0,9, +1 CA par pièce, la DEX compte), lourd (Heaume,
  Cuirasse, Solerets ; armure ×1,5, la DEX ne compte plus). La moyenne vaut 1 : le héros de
  référence des affixes ne bouge pas. Le poids ne change pas les PV de la pièce.
- **CA** = 10 + maîtrise (celle des pièces d'armure portées) + bonus des pièces (léger +1,
  bouclier +2) + modificateur de DEX (sauf avec une pièce lourde).
- **Jet d'attaque** : d20 + bonus d'attaque (maîtrise de l'étage + 6) ≥ CA → touché ; un 20
  touche toujours, un 1 rate toujours. Raté : aucun dégât (« Raté ! »). Touché : l'armure et
  la parade jouent comme avant.
- **Même moyenne de dégâts** : le héros de référence (bouclier, DEX 10) est touché 3 fois
  sur 4 ; les coups qui touchent sont relevés de ×4/3. L'équilibre PV / dégâts réglé avec
  les affixes tient, et c'est l'écart à la référence qui paie.
- **La rage** donne le **désavantage** à l'attaque (deux d20, le pire gardé) au lieu des
  35 % de ratés fixes.
- La **note** d'un objet compte sa CA : 1 CA = 1/15 des dégâts reçus chez le héros de
  référence, sur l'axe de la survie. Ce que la DEX apporte à la CA n'y est pas encore
  compté (la note de la DEX ne voit que le critique et la parade).
- Les pièces d'avant les poids n'en ont pas : ni bonus de CA, ni blocage de la DEX.
- *Mesuré* : même ordre de difficulté qu'avant la CA. Partie entière jusqu'à l'étage 100,
  médianes novice / correct / expert : 26 / 62 / 100 avant, **31 / 51 / 100** après — dans
  le bruit de 30 profils (le correct va de 15 à 100). À équipement égal, l'étage 15 sans
  sort passe de 64 % à 70 % de victoires sur 3 combats : le bot, qui prend la meilleure
  note, se trouve souvent une CA au-dessus de la référence.

### La suite

- **B.** Chaque relique suit sa caractéristique — **faite**. Chaque relique porte sa
  caractéristique (feu, glace, foudre : INT ; Venin : DEX). Elle fait ses dégâts (+5 % par
  point au-dessus de 10) et son DD (11 + son modificateur + maîtrise). Les bonus « dégâts des
  sorts » des objets comptent pour toutes. La description d'une relique affiche sa
  caractéristique. *Défaut connu* : la note de la DEX ne voit toujours que le critique et la
  parade, ni la CA ni le Venin — à revoir quand les archétypes auront leurs sets.
- **C.** Le bouton « Spécial » et la parade parfaite de chaque archétype — **faite**.
  - **L'archétype** vient de l'armure, en attendant les sets : 2 pièces du même poids sur
    3 (casque, armure, bottes). Lourd = guerrier, léger = voleur, tissu = mage. Sans
    majorité, le « Spécial » reste verrouillé. L'inventaire affiche l'archétype.
  - **Le renvoi du guerrier** (idée du propriétaire, 18/09) : le guerrier qui se protège
    beaucoup ne tuait rien. Il fait donc mal **en encaissant** : le blocage parfait au
    bouclier renvoie **30 %** du coup (le coup de bouclier), la Garde **50 %** ; la Peau de
    pierre s'y ajoute (30 %, doublée par Rempart). Tout se calcule sur le coup **brut**,
    avant parade et armure : plus le monstre frappe fort, plus il se fait mal, et la grosse
    armure du guerrier n'affaiblit pas ce qu'il renvoie (`Combat.retaliate`).
  - **Parade parfaite** : le guerrier **bloque** (aucun dégât) s'il porte un bouclier ; le
    voleur **esquive** (aucun dégât) et **riposte** d'un coup d'arme gratuit ; le mage
    fait un **contresort** : ses reliques gagnent un tour de recharge (pas le Spécial).
  - **Le bouton « Spécial »**, recharge 5 tours (la SAG la raccourcit), gardée d'un combat à
    l'autre comme les reliques, et que le repos recharge :
    - Guerrier, **Garde** : il passe son tour, ses fenêtres de parade doublent jusqu'à son
      prochain tour, et chaque coup reçu (bloqué ou encaissé) renvoie **50 %** du coup brut
      à l'attaquant — la **Représaille** ;
    - Voleur, **Coup mortel** : un coup d'arme (avec le swipe) ; sur une cible **exposée**
      (empoisonnée, figée, paralysée ou sous 30 % de ses PV), critique garanti et
      multiplicateur +1 — l'attaque sournoise de D&D, et de quoi achever un petit monstre ;
    - Mage, **Image miroir** : trois doubles. Avant chaque jet d'attaque, un d20 dit si le
      coup vise un double (6+ avec trois, 8+ avec deux, 11+ avec le dernier), comme dans
      D&D. Ils durent jusqu'à la fin du combat.
  - Les gestes restent le swipe et la touche ; des gestes propres à chaque archétype, à la
    Undertale, viendront plus tard.
  - *Mesuré* (partie entière jusqu'à l'étage 100, médianes novice / correct / expert) :
    31 / 51 / 100 avant, **31 / 57 / 100** après ; les morts baissent d'environ 10 à 15 %
    pour le novice et le correct. Un coup de pouce, pas un bouleversement.
- **D.** Les sets par zone et par archétype, qui tombent tous partout.
- **E.** Les affixes de sorts, puis ceux propres à une relique (voir plus haut).
- **Avant D** (décidé le 18/09) : le grimoire, voir « Le grimoire ».
  Les sets viendront quand leurs bonus auront des sorts à modifier.

## Mesures : la boucle « mieux équipé → plus profond » (**avant** la refonte des affixes)

> Ces chiffres datent d'avant la passe sur les affixes. Ils sont gardés parce qu'ils
> montrent d'où on vient : à l'époque, un affixe valait environ cinq fois son budget. Les
> mesures à jour sont dans « Mesures après la refonte des affixes ».

Simulation jusqu'à l'étage 40 (30 profils par niveau de skill, les bots équipent ce qui a
une meilleure note, la mort ramène à l'étage 1 avec tout l'équipement) :

| Arrivée à l'étage | Novice : morts / note d'équipement | Correct | Expert |
|---|---|---|---|
| 10 | 11 / 290 | 5 / 254 | 1 / 231 |
| 20 | 37 / 565 | 11 / 508 | 2 / 473 |
| 30 | 44 / 815 (3 profils sur 30) | 17 / 729 | 3 / 751 |
| 40 | — | 16 / 1023 (9 sur 30) | 4 / 974 |

- La boucle marche : un joueur moins adroit meurt plus, s'équipe mieux, et passe quand
  même. Au même étage, le novice porte un équipement mieux noté que l'expert.
- Les morts viennent pour un tiers de combats commencés en pleine forme, pour le reste
  d'embuscades et d'enchaînements.
- L'expert meurt très peu (4 fois avant l'étage 40) : les boss devront le tester.

## Mesures (simulation, étape 1)

Test `RoguelikeSimulationTest` (dossier `app/src/test/.../roguelike`). Les bots ne voient
que ce que voit le joueur ; ils jouent pareil sur la carte et ne diffèrent que par leur
précision au timing (novice : ~18 % de parades réussies, correct : ~55 %, expert : ~90 %).

- À équipement égal, étage 12 : un combat coûte 38 % des PV au novice, 21 % à l'expert ;
  trois combats enchaînés sans repos : 35 % de survie contre 84 %.
- Morts avant d'atteindre l'étage 15 (médiane) : novice 23, correct 10, expert 3.
- Les étages 1–2 servent d'apprentissage (2–3 % des PV par combat) ; le premier vrai pic
  est l'étage 5 (groupes de 3, démons).
