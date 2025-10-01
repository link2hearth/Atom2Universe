# 🧪 Atom → Univers

**Atom → Univers** est un idle/clicker cosmique. Chaque clic forge des atomes, chaque atome alimente vos laboratoires, et votre objectif ultime reste d’atteindre \(10^{80}\) atomes afin de reconstituer un univers entier.

Le jeu combine plusieurs boucles complémentaires :

* **Clic manuel (APC)** : appuyez sur l’atome central pour générer instantanément des ressources.
* **Production passive (APS)** : investissez dans des bâtiments scientifiques qui produisent automatiquement.
* **Moments de frénésie** : capturez les orbes de frénésie pour multiplier temporairement vos gains.
* **Collection** : utilisez des tickets pour déclencher le gacha et étendre votre tableau périodique, chaque élément offrant des bonus croissants.

---

## ⚙️ Ressources & progression

* Les atomes servent à acheter des améliorations, débloquer de nouveaux bâtiments et augmenter la puissance de vos clics.
* Une arithmétique à couches gère les très grands nombres : notation classique, scientifique, puis double exponentielle (`ee`). Il n’existe pas de plafond théorique.
* Les sauvegardes utilisent un export/import JSON qui retient mantisses et exposants pour les sessions hors ligne.

---

## 🏭 Bâtiments scientifiques

Les bâtiments sont regroupés par rôle (manuel, automatique, hybride) et se renforcent via des synergies spécifiques :

* Plusieurs bonus croisés existent, par exemple l’Accélérateur de particules qui renforce les Laboratoires de physique, ou les Supercalculateurs boostés par les Stations orbitales.
* Les descriptions détaillées se trouvent directement en jeu et dans `game-config.js`.

---

## 🕹️ Mini-jeux d’arcade

Deux expériences annexes viennent dynamiser la progression en apportant tickets, crédits Mach3 et bonus thématiques :

### Particules

* Accessible depuis l’onglet Arcade, ce casse-briques cosmique reprend les codes d’un brick breaker avec HUD dédié (niveau, vies, score) et zones d’interaction adaptées clavier/souris/tactile.【F:index.html†L158-L266】
* Terminer un niveau sans perdre de vie octroie des tickets de gacha, directement injectés dans l’inventaire et annoncés via un toast.【F:scripts/arcade/particules.js†L2532-L2555】【F:scripts/arcade/gacha.js†L1845-L1883】
* Les gravitons apparaissant au fil des manches peuvent être capturés pour gagner des tickets spéciaux convertis en crédits Mach3, utiles au second mini-jeu.【F:scripts/arcade/particules.js†L1980-L2056】【F:scripts/arcade/particules.js†L2345-L2350】【F:scripts/arcade/gacha.js†L1871-L1880】

### Mach3 (Métaux)

* Jeu de match-3 en temps limité basé sur une grille 9×16 et cinq types de gemmes métalliques ; chaque alignement ajoute du temps tandis que la pression monte avec un chrono à 6 secondes extensibles.【F:scripts/arcade/metaux-match3.js†L4-L118】
* Une partie consomme un crédit Mach3 ; le compteur de crédits est alimenté par Particules et affiché dans l’interface Arcade ainsi que sur l’écran de fin de partie pour planifier vos runs.【F:index.html†L424-L477】【F:scripts/app.js†L1608-L1705】

---

## 🎟️ Tickets de gacha

Le gacha ne consomme plus d’atomes : chaque tirage coûte **1 ticket**.

### Collecte des tickets

* Une **étoile de tickets** apparaît sur l’écran principal toutes les ~60 secondes (intervalle moyen). Cliquez dessus pour obtenir des tickets.
* Les éléments de rareté **Mythe quantique** réduisent cet intervalle d’1 s par élément unique, jusqu’à un minimum de 5 s.
* Certaines récompenses d’événements ou de DevKit peuvent également octroyer des tickets bonus.

### Tirages

* Un bouton dédié lance une animation cosmique et consomme automatiquement 1 ticket (sauf modes gratuits spéciaux).
* Les éléments tirés s’ajoutent à votre collection : les nouveaux éléments octroient des bonus “unique”, tandis que les doublons activent des effets “duplicate”.
* Chaque tirage affiche la rareté, le nom de l’élément et l’état de votre collection (nouveau/doublon/max).

### Raretés et probabilités

| Rareté | Poids | Description |
| --- | --- | --- |
| **Commun cosmique** | 55 % | Les éléments omniprésents dans les nébuleuses. |
| **Essentiel planétaire** | 20 % | Les fondations des mondes rocheux et océaniques. |
| **Forge stellaire** | 12 % | Alliages forgés au cœur des étoiles actives. |
| **Singularité minérale** | 7 % | Cristaux rarissimes difficiles à stabiliser. |
| **Mythe quantique** | 4 % | Éléments quasi légendaires, aux effets systémiques. |
| **Irréel** | 2 % | Créations synthétiques, jamais observées naturellement. |

---

### Pity journalier

Chaque journée met en avant une rareté précise : le système ajuste automatiquement les poids de tirage pour garantir une montée en probabilité des familles mises en vedette (Singularité minérale les lundis et jeudis, Mythe quantique les mardis et vendredis, Irréel les mercredis et samedis, mix équilibré le dimanche).【F:scripts/arcade/gacha.js†L107-L209】【F:config/config.js†L1528-L1612】
Le libellé de mise en avant est reflété dans l’interface gacha et se réinitialise à chaque changement de jour, offrant une forme de pity journalier : si vous ciblez une rareté spécifique, il suffit de jouer le jour associé pour profiter de chances renforcées, puis patienter jusqu’au prochain cycle si la session n’a pas produit le résultat attendu.【F:scripts/arcade/gacha.js†L109-L217】

---

## 💠 Bonus par rareté

Chaque groupe de rareté dispose d’une configuration propre. Les bonus sont cumulés par élément, puis complétés par des récompenses de collection :

### Commun cosmique

* **Par copie** : +1 atome par clic.
* **Collection complète** : +500 APC plats.
* **Accumulation** : toutes les 50 copies, +1 au multiplicateur global (APC & APS).

### Essentiel planétaire

* **Par élément unique** : +10 APC plats. Les doublons donnent également +10 APC.
* **Collection complète** : +1 000 APC plats.
* **Accumulation** : toutes les 30 copies, +1 au multiplicateur global (APC & APS).

### Forge stellaire

* **Par élément unique** : +50 APC plats.
* **Par doublon** : +25 APC plats.
* **Collection complète** : multiplie par 2 les bonus plats apportés par les Commun cosmique.
* **Accumulation** : toutes les 20 copies, +1 au multiplicateur global (APC & APS).

### Singularité minérale

* **Par élément unique** : +25 APC et +25 APS plats.
* **Par doublon** : +20 APC et +20 APS plats.
* **Accumulation** : toutes les 10 copies, +1 au multiplicateur global (APC & APS).

### Mythe quantique

* **Réduction des tickets** : chaque élément unique réduit de 1 s l’intervalle d’apparition de l’étoile à tickets (minimum 5 s).
* **Hors-ligne** : chaque doublon ajoute +1 % de gains hors-ligne (jusqu’à +100 %). Au-delà du plafond, chaque doublon offre +50 APC et +50 APS plats.
* **Collection complète** : +50 % de chances supplémentaires de déclencher une frénésie.

### Irréel

* **Par élément unique** : +1 % de chance de critique (cumulatif).
* **Par doublon** : +1 % au multiplicateur de critique.
* **Accumulation** : toutes les 5 copies, +1 au multiplicateur global (APC & APS).

---

## 📈 Progression de collection (recommandation indicative)

* **Début** : sécuriser les Commun cosmique et Essentiel planétaire pour accélérer les clics.
* **Milieu de partie** : les Forge stellaire et Singularité minérale installent de véritables moteurs APS/APC.
* **Fin de partie** : Mythe quantique et Irréel débloquent la gestion avancée des tickets, du hors-ligne, des critiques et des frénésies.

---

## 🧰 Encart spécial : bonus & modificateurs cumulés

Ce mémo récapitule l’ensemble des bonus actuellement en jeu. Il couvre les bâtiments de la boutique, les collections d’éléments, les succès et la fusion moléculaire, ainsi que leurs effets sur l’APC, l’APS, les frénésies, les critiques ou la génération de tickets.

### 🏪 Boutique scientifique

| Bâtiment | Rôle | Bonus principaux |
| --- | --- | --- |
| **Électrons libres** | Manuel | +1 APC plat/niveau, +5 % APC tous les 25 niveaux.【F:config/config.js†L30-L47】 |
| **Laboratoire de Physique** | Automatique | +1 APS plat/niveau, +5 % APC tous les 10 labos, +20 % APS si l’Accélérateur ≥200.【F:config/config.js†L50-L70】 |
| **Réacteur nucléaire** | Automatique | +10 APS plat/niveau, +1 % APS par 50 Électrons, +20 % APS si les Labos ≥200, palier 150 : APC global ×2.【F:config/config.js†L74-L100】 |
| **Accélérateur de particules** | Hybride | +50 APS plat/niveau (boosté par ≥100 Supercalculateurs), +2 % APC par niveau, palier 200 : +20 % APS pour les Labos.【F:config/config.js†L102-L121】 |
| **Supercalculateurs** | Automatique | +500 APS plat/niveau, doublés par les Stations ≥300, +1 % APS global tous les 25 niveaux.【F:config/config.js†L124-L145】 |
| **Sonde interstellaire** | Hybride | +5 000 APS plat/niveau (boosté par les Réacteurs), palier 150 : +10 APC plats par sonde.【F:config/config.js†L148-L172】 |
| **Station spatiale** | Hybride | +50 000 APS plat/niveau, +5 % APC par station, palier 300 : Supercalculateurs ×2.【F:config/config.js†L174-L189】 |
| **Forgeron d’étoiles** | Hybride | +500 000 APS plat/niveau (+2 % APS par Station), palier 150 : +25 % APC global.【F:config/config.js†L191-L212】 |
| **Galaxie artificielle** | Automatique | +5 000 000 APS plat/niveau (doublée par Bibliothèque ≥300), palier 100 : +50 % APC global.【F:config/config.js†L215-L242】 |
| **Simulateur de Multivers** | Automatique | +500 000 000 APS plat/niveau et +0,5 % APS global par bâtiment possédé, palier 200 : coûts −5 %.【F:config/config.js†L245-L262】 |
| **Tisseur de Réalité** | Hybride | +10 000 000 000 APS plat/niveau, bonus de clic plat = 0,1 × bâtiments × niveau, palier 300 : production totale ×2.【F:config/config.js†L265-L290】 |
| **Architecte Cosmique** | Hybride | +1 000 000 000 000 APS plat/niveau, −1 % coût futur par Architecte, palier 150 : +20 % APC global.【F:config/config.js†L293-L309】 |
| **Univers parallèle** | Automatique | +100 000 000 000 000 APS plat/niveau.【F:config/config.js†L312-L325】 |
| **Bibliothèque de l’Omnivers** | Hybride | +10 000 000 000 000 000 APS plat/niveau, +2 % boost global par Univers parallèle, palier 300 : Galaxies artificielles ×2.【F:config/config.js†L328-L349】 |
| **Grand Ordonnateur Quantique** | Hybride | +1 000 000 000 000 000 000 APS plat/niveau, palier 100 : double définitivement APC & APS.【F:config/config.js†L353-L368】 |

### 🧬 Collections d’éléments

* **Commun cosmique** : +1 APC plat par copie, set complet : +500 APC, multiplicateur global (APC & APS) +1 tous les 50 exemplaires (jusqu’à +100).【F:config/config.js†L910-L928】
* **Essentiel planétaire** : +10 APC plats par élément unique ou doublon, set complet : +1 000 APC, multiplicateur global +1 tous les 30 exemplaires (cap 100).【F:config/config.js†L929-L948】
* **Forge stellaire** : +50 APC plats par unique, +25 APC par doublon, set complet : double les bonus plats des Commun cosmique, multiplicateur global +1 tous les 20 exemplaires (cap 100).【F:config/config.js†L949-L968】
* **Singularité minérale** : +25 APC/APS plats par unique, +20 APC/APS par doublon, multiplicateur global +1 tous les 10 exemplaires (cap 100).【F:config/config.js†L969-L989】
* **Mythe quantique** : −1 s sur l’intervalle de l’étoile à tickets par élément unique (min 5 s), +1 % de gains hors-ligne par doublon (jusqu’à +100 %), puis +50 APC/APS plats au-delà, set complet : +50 % de chances de frénésie.【F:config/config.js†L990-L1014】
* **Irréel** : +1 % de chance de critique par unique, +1 % sur le multiplicateur de critique par doublon, multiplicateur global +1 tous les 5 exemplaires (cap 100).【F:config/config.js†L1015-L1034】

### 🏆 Succès & trophées

* **Échelles atomiques (21 paliers)** : de la cellule humaine (10^14) à l’univers observable (10^80), chaque trophée ajoute +2 au boost global de production (soit ×3 par palier obtenu).【F:config/config.js†L409-L608】
* **Ruée vers le million** : atteindre 1 000 000 d’atomes synthétisés ajoute +0,5 au boost global (×1,5 une fois débloqué).【F:config/config.js†L745-L776】
* **Convergence frénétique** : déclencher 100 frénésies augmente la réserve maximale de frénésies simultanées à 2.【F:config/config.js†L777-L793】
* **Tempête tri-phasée** : déclencher 1 000 frénésies porte la réserve à 3 et applique un multiplicateur global ×1,05.【F:config/config.js†L794-L809】
* **Collecteur d’étoiles** : compléter les raretés Commun cosmique & Essentiel planétaire active la collecte automatique des étoiles à tickets après 3 s.【F:config/config.js†L810-L827】

### ⚗️ Fusion moléculaire

* **Molécule d’eau (H₂O)** : consomme 2 Hydrogènes et 1 Oxygène avec 50 % de réussite pour octroyer +100 APC plats immédiats.【F:config/config.js†L713-L741】

Combinez ces leviers pour orchestrer vos pics de production, maximiser les frénésies et sécuriser les ressources critiques tout au long de la montée vers 10^80 atomes.

---

## 🌍 Internationalisation

L’interface repose sur des fichiers JSON (`scripts/i18n/<code>.json`) chargés dynamiquement. Pour ajouter une nouvelle langue :

1. **Dupliquez un fichier de référence** (`scripts/i18n/fr.json` par exemple) vers `scripts/i18n/<code>.json` en conservant la même structure de clés.
2. **Traduisez chaque entrée** : toutes les clés existantes doivent recevoir une valeur localisée afin d’éviter les retours de clés brutes dans l’interface.
3. **Enregistrez le code langue** dans `scripts/modules/i18n.js` au sein du tableau `AVAILABLE_LANGUAGES` pour que le sélecteur et le chargeur de ressources prennent en compte cette variante.

Une fois ces étapes terminées, rechargez la page : la langue apparaîtra automatiquement dans le sélecteur d’options et pourra être choisie sans redémarrer la session.

---

## 🛠️ Implémentation

* **Technologies** : HTML, CSS et JavaScript vanilla.
* **Configuration** : `game-config.js` centralise l’équilibrage (bâtiments, gacha, bonus) ; `periodic-elements.js` référence les 118 éléments.
* **Accessibilité** : navigation par onglets, compteurs `aria-live`, animations désactivables via classes CSS.
* **Sauvegarde** : export/import JSON ; le format stocke les tickets, la progression de collection, les multiplicateurs et les paramètres de l’étoile à tickets.

### 🚀 Lancer un serveur local

Le projet inclut un lanceur Node.js (`MyLocalServ`) afin de servir les fichiers statiques sans blocage des requêtes `fetch`. Installez au préalable [Node.js](https://nodejs.org/) (version LTS recommandée), puis choisissez la méthode adaptée à votre système :

#### Windows

* **Double-clic** : ouvrez `MyLocalServ.cmd`. La fenêtre affiche l’URL (`http://localhost:8080` par défaut) et reste ouverte pour vous permettre d’arrêter le serveur proprement.
* **Terminal** : exécutez la commande ci-dessous pour lancer le serveur depuis l’invite de commandes et, si besoin, préciser un port personnalisé.

```bat
cd Atom2Univers
MyLocalServ.cmd 3000
```

#### macOS / Linux (et terminaux en général)

```bash
cd Atom2Univers
node MyLocalServ.js
```

Le serveur démarre par défaut sur `http://localhost:8080`. Définissez la variable d’environnement `PORT` (ou passez un argument à `MyLocalServ.cmd`) pour changer le port si nécessaire. Appuyez sur `Ctrl+C` pour l’arrêter.

---

## 🎯 Objectif

Collectez, automatisez, déclenchez des frénésies et maîtrisez la synthèse élémentaire via les tickets pour franchir l’échelle des grands nombres… jusqu’à reconstituer l’univers tout entier.
