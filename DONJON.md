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
   *En cours* : les quatre reliques classiques (feu, glace, foudre, poison), le combat à
   quatre boutons, les reliques à trouver et les recharges gardées d'un combat à l'autre
   sont faits (voir « Les reliques »). Ensuite : résistances, jets de contrôle et rage ;
   puis le sort spécial, le set et la zone.
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

Provisoire : une relique attend aux étages **2, 5, 9 et 14**, au bout du cul-de-sac le plus
éloigné du départ. Elle est tirée au hasard parmi celles qu'on n'a pas. Une relique trouvée
ne revient pas après une mort. Elle se porte d'office s'il reste un emplacement libre, sinon
elle attend dans l'inventaire (section « Reliques » : toucher pour porter ou ranger).

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
  - **Parade parfaite** : le guerrier **bloque** (aucun dégât) s'il porte un bouclier ; le
    voleur **esquive** (aucun dégât) et **riposte** d'un coup d'arme gratuit ; le mage
    fait un **contresort** : ses reliques gagnent un tour de recharge (pas le Spécial).
  - **Le bouton « Spécial »**, recharge 5 tours (la SAG la raccourcit), gardée d'un combat à
    l'autre comme les reliques, et que le repos recharge :
    - Guerrier, **Garde** : il passe son tour, ses fenêtres de parade doublent jusqu'à son
      prochain tour ;
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
