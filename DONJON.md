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
- Carte de 41 × 27 cases ; escalier dans la salle la plus lointaine (~80 pas).

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
- Farmer une zone est voulu quand c'est pour compléter un set. Exemple : le **set du rat
  des égouts** (étages 1–20) empoisonne, et les petits monstres fuient « parce qu'on sent
  mauvais ». À affiner.
- Les monstres se règlent sur **l'étage**, jamais sur le joueur : l'équipement doit
  compter.

### Les reliques = les sorts
- On équipe des reliques, chacune donne un sort (boule de feu, boule de glace…).
- Les dégâts d'un sort dépendent de la relique **et** des stats du joueur et de son
  équipement.
- Certains boss demandent telle ou telle relique (immunités : un boss insensible au feu
  mais pas au poison, etc.).

### Les caractéristiques : copiées sur D&D
| Carac. | Rôle |
|---|---|
| FOR | dégâts de l'épée |
| DEX | chance de critique, fenêtre de parade plus large |
| CON | points de vie |
| INT | dégâts des sorts |
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
- **À quoi sert l'argent ?** Pistes : relancer une stat d'un équipement, booster une
  stat, changer un type / élément, acheter des consommables de combat (pansements, soins
  instantanés) — limités car très puissants.
- **Niveaux et expérience ?** Pour l'instant les caractéristiques viennent de la base
  (10 partout) et de l'équipement. Faut-il des niveaux façon D&D ?
- **Fuir un combat ?** Pas prévu pour l'instant.
- Quitter l'appli en plein combat régénère l'étage (petite triche possible).

## Plan par étapes

1. **La base** *(en cours)* — carte avec monstres qui patrouillent et poursuivent, écran
   de combat (Attaque, une relique Boule de feu, potions, parade et swipe critique),
   repos uniquement sans poursuivant, les 6 caractéristiques D&D, monstres réglés sur
   l'étage, butin simple adapté aux nouvelles stats, mort = retour étage 1 avec le stuff
   et perte d'or. Étages 1 à 5, visuels actuels.
2. **Le butin à la Diablo** — affixes riches, raretés revues, uniques, usages de l'argent.
3. **Sets, reliques et zones** — set du rat des égouts, plusieurs reliques, étages 1–20.
4. **Boss et checkpoints** — Roi des Rats à l'étage 20, puis zones suivantes.

## Réglages de l'étape 1 (valeurs de départ, à ajuster en jouant)

- Héros : 10 dans chaque caractéristique. PV max = 40 + 4 × (CON − 10) + bonus.
  Épée 4–7 dégâts, +4 % par point de FOR au-dessus de 10. Critique : 5 % + 1 % par DEX,
  dégâts ×2. Armure : dégâts reçus × 50 / (50 + armure).
- Frapper juste : bien = +25 points de critique, parfait = +60.
- Parade : bien = dégâts ÷ 2, parfaite = dégâts × 0,2. Fenêtre élargie par la DEX.
- Boule de feu : 6–9 dégâts, +5 % par INT, brûlure 2 tours ; recharge 3 tours (SAG la
  réduit).
- Monstres à l'étage 1 (PV / dégâts / frappe tous les N tours / dès l'étage) :
  Rat 16/3/1/1, Gobelin 24/4/1/1, Squelette 34/6/2/2, Orc 50/10/2/3, Démon 70/14/3/5.
  Puis PV × (1 + 0,22 × (étage − 1)), dégâts × (1 + 0,15 × (étage − 1)).
- Taille des groupes : seul aux étages 1–2, jusqu'à 2 aux étages 3–4, jusqu'à 3 ensuite.
- Embuscade (les monstres frappent en premier) seulement si on ne voyait pas le monstre
  avant qu'il nous rejoigne, ou si on se reposait. Un combat enchaîné n'est pas une
  embuscade : devoir enchaîner est déjà la pénalité.
- Groupes : cadence d'un ennemi = sa cadence + (taille du groupe − 1), attaques décalées.
- Repos : +15 % des PV max par tour de repos, impossible si poursuivi.
- Mort : −30 % de l'or.
- Marchand sur l'escalier : potion à 15 or (5 maximum sur soi, soigne 40 % des PV).

## Mesures (simulation, étape 1)

Test `RoguelikeSimulationTest` (dossier `app/src/test/.../roguelike`). Les bots ne voient
que ce que voit le joueur ; ils jouent pareil sur la carte et ne diffèrent que par leur
précision au timing (novice : ~18 % de parades réussies, correct : ~55 %, expert : ~90 %).

- À équipement égal, étage 12 : un combat coûte 38 % des PV au novice, 21 % à l'expert ;
  trois combats enchaînés sans repos : 35 % de survie contre 84 %.
- Morts avant d'atteindre l'étage 15 (médiane) : novice 23, correct 10, expert 3.
- Les étages 1–2 servent d'apprentissage (2–3 % des PV par combat) ; le premier vrai pic
  est l'étage 5 (groupes de 3, démons).
