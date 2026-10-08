# Audio de Cave World

## Sons intégrés

- 25 extraits de banques publiées remplacent les anciens bruitages synthétiques :
  pas Kenney (herbe, béton/pierre, bois), pistolet 1911, mitraillette Carl Gustav M45,
  carabine Marlin 336, arc, arbalète, lancer, animaux et retours de combat.
- Les trois variantes du shotgun restent les sons originaux approuvés, inchangés.
- Rechargement silencieux, aucun son ajouté à chaque ennemi touché, aucune musique en Assaut.
- En Assaut, les soldats utilisent la même famille de sons d'arme que le joueur,
  transmise par le choix de manche, à volume réduit. Plus de tir ennemi générique.
  Leurs projectiles et réglages de cadence/chargeur/rechargement proviennent déjà
  du même RangedProfile ; les dégâts et la précision IA restent équilibrés pour le solo.
- Kill (soldat, monstre ou boss) : glockenspiel Sonivox, canal MIDI dédié, aucun WAV.
  Fonctionne en Assaut sans démarrer la musique ; coupure à la pause/destruction.
- Les pas sont une boucle PCM native de 460 ms, avec un seul appui de 120 ms maximum
  puis du silence. Le tempo ne dépend plus des images du jeu. Le moteur ajuste la
  vitesse de lecture selon la vitesse demandée (environ 290 ms en course normale).
  La hauteur varie légèrement avec cette vitesse de lecture. Le déplacement réel
  sert uniquement à autoriser/couper la boucle, avec une tolérance de 120 ms aux
  petites interruptions de collision. Aucun rattrapage de pas ni double déclenchement.
- Cadence et niveau des pas liés
  à la marche/course ; pas de pas à l'arrêt, accroupi, en l'air ou dans l'eau.
  Une seule boucle par sol pour les deux pieds, volume réduit
  et filtre passe-bas à 1 200 Hz pour un rendu étouffé. Les anciens WAV de pas sont supprimés.
  Le repérage par un ennemi est silencieux : le grincement de charnière est retiré.
- Animaux : vrais enregistrements de vache, mouton, cochon et poule, un appel toutes
  les 7 à 14 secondes, distance maximale 18 blocs, atténuation par les obstacles.
  Les deux fichiers de vache reprennent la même prise à des niveaux légèrement différents.
- Piano original Sonivox conservé dans le monde infini.

## Sources, crédits et licences

Voir `app/src/main/assets/caves/audio/CREDITS.md` et `sources.json`.
Les crédits sont également accessibles dans le menu du jeu (français/anglais).
Les adaptations des prises de Secretlondon restent sous CC BY-SA 3.0 ; les autres
extraits sont CC0 ou CC BY 3.0 selon leur source. Aucun script provenant des archives
de bruitages n'est exécuté.
Les fichiers bruts et les outils Python restent dans `.audio-work/`, ignoré par Git.

## Reproduire les enregistrements

Avec Python 3, numpy, soundfile et py7zr (outils de préparation seulement) :

```text
python tools/caves/fetch_audio_sources.py
python tools/caves/inspect_audio_sources.py
python tools/caves/import_recorded_audio.py
```

Le manifeste précise pour chaque fichier la source, les coupes, les traitements,
la licence et les empreintes SHA-256 avant/après. Les sons sont convertis en PCM
mono 16 bits à 22 050 Hz avec filtrage antirepliement, équilibrage de crête et petits
fondus aux bords. Aucun oscillateur ni couche synthétique n'est ajouté aux prises.
La durée maximale est inférieure à 3,5 secondes et les flux restent suivis 4 secondes
pour pouvoir les arrêter intégralement en pause.

`generate_audio.py` régénère uniquement les trois shotgun originaux.
L'ancien générateur de pas et animaux synthétiques est supprimé.

## Vérification

28 WAV utilisés : 12 tirs (dont les 3 shotgun originaux), 3 armes à projectiles,
2 retours de combat, 3 boucles de pas et 8 cris d'animaux. Aucun WAV de kill,
rechargement, impact de touche, repérage ou tir ennemi générique.
Le manifeste décrit les 25 fichiers importés ; les 3 shotgun sont générés localement.
L'aperçu temporaire et les anciennes variantes ne sont pas conservés.

Vérification autorisée : `./gradlew compileDebugKotlin`. Aucun APK n'est construit
ni installé par Codex. La compilation et les contrôles numériques ne remplacent
pas une écoute sur téléphone : vérifier notamment rafales, pas en course, cris proches
et pause pendant un meuglement.
