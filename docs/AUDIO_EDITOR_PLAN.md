# Refonte de l'éditeur audio — plan complet

> Objectif : transformer le module « Éditeur audio » en un **Audacity de poche** : un écran de
> démarrage en **tuiles de projets** (comme Pixel art / Canvas / Notes) puis un vrai éditeur
> **multipiste non destructif**, avec le plus possible des fonctions d'Audacity qui ont un sens
> sur Android.
>
> Ce document est la mémoire du chantier : il se lit seul, sans la conversation qui l'a produit.
> Chaque étape a des cases à cocher ; **cocher en même temps que le commit** qui la livre.

## 0. Où en est-on

| Étape | Contenu | État |
|---|---|---|
| 1 | Cœur du modèle : clips, pistes, repères, opérations de ligne de temps, undo/redo | ✅ fait (`audioeditor/core/`, `TimelineTest`) |
| 2 | Fichiers PCM/WAV, mixeur, pics de forme d'onde, rééchantillonnage | ✅ fait (`core/Mixer|Peaks|Resampler`, `io/WavFile|PeakFiles`) |
| 3 | Effets DSP purs : socle, volume, filtres / EQ, dynamique, espace / modulation, temps / hauteur, bruit, génération | ✅ fait (`audioeditor/dsp/`, 171 tests au total) |
| 4 | Stockage des projets, import (décodage), export (FFmpeg), zip de projet, migration de l'ancien projet | ⬜ |
| 5 | Moteur de lecture temps réel (`AudioTrack`) + enregistrement par-dessus (overdub) | ⬜ |
| 6 | Galerie de projets en tuiles (écran de démarrage) + branchement dans le hub audio | ⬜ |
| 7 | Éditeur : vue chronologie multipiste, barre d'outils, feuilles d'effets avec aperçu | ⬜ |
| 8 | Export (feuille + formats), spectrogramme, analyse, repères, enveloppe de volume | ⬜ |
| 9 | Nettoyage de l'ancien code et des anciens textes, textes en/fr, passe finale | ⬜ |

Les étapes 1 → 5 sont du **code sans écran** (1, 2 et 3 sont faites) : elles se testent en JVM et se livrent une par une
sans rien changer pour l'utilisateur tant que l'étape 6 n'a pas rebranché le hub. L'ancien éditeur
continue donc de fonctionner pendant tout le chantier.

## 1. Ce que fait l'éditeur actuel (et pourquoi on le remplace)

Package `com.Atom2Universe.app.audioeditor` (≈ 7 000 lignes) :

* **Un seul projet implicite** (`audio_editor_project.json` dans `filesDir`) : pas de galerie, une
  boîte de dialogue « continuer / nouveau projet » au lancement.
* **Édition destructive via FFmpeg** : couper / copier / coller / supprimer / rogner réécrivent un
  WAV entier à chaque fois (plusieurs sessions `FFmpegKit.execute` + concaténation). Annuler =
  copier le fichier entier dans `history/`, limité à 100 Mo.
* **Une seule piste jouée à la fois** (`MediaPlayer` sur la piste active) : les « pistes » sont une
  liste de fichiers indépendants, il n'y a pas de mixage ni de ligne de temps commune.
* Pas d'effets, pas de fondus, pas de volume/panoramique par piste, pas de repères.
* Enregistrement micro (`AudioRecorder`, `MicRecordingService`) et spectrogramme (`SpectrumView`,
  `FFTProcessor`) — **à conserver** : le Dictaphone utilise `MicRecordingService`, et le Créateur
  SF2 utilise `SpectrumView`.
* Export MP3 / WAV / FLAC / OGG / AAC par FFmpeg avec métadonnées (`AudioMetadata`).

Entrée dans l'appli : `audio/AudioSubHubActivity.kt:84` (tuile → `AudioEditorActivity`),
`hub/HubTileArtworks.kt:118` (illustration de la tuile, clé = nom de classe), `AndroidManifest.xml:205`.

## 2. Décisions d'architecture

### 2.1 Modèle non destructif, immuable

* Un **projet** = pistes → clips → *sources*. Un clip ne contient pas d'audio : il lit
  `length` trames d'une source (fichier PCM) à partir de `srcStart` et les place à `start`.
* Tout est **immuable** (`data class`). Une édition renvoie un nouveau `Project` qui partage tout
  ce qu'elle n'a pas touché. Conséquences :
  * annuler / rétablir = une pile de `Project` (`EditSession`) : quelques pointeurs, aucun fichier ;
  * le fil audio lit « son » instantané sans verrou ;
  * couper / coller / déplacer / retourner / fondus sont **instantanés** quelle que soit la durée.
* Unité de temps : la **trame** (`Long`) à la fréquence du projet (44,1 kHz par défaut, 48 kHz au choix).
  Toutes les sources sont rééchantillonnées à la fréquence du projet à l'import.
* Les clips peuvent **se chevaucher** sur une piste (le mixeur additionne) : un fondu croisé =
  deux clips qui se recouvrent avec un fondu de sortie et un fondu d'entrée. Une queue d'écho ou de
  réverbération se superpose naturellement à la suite.
* **Fondus** : `Fade(len, from, to, shape)` où `from`/`to` sont les positions sur la courbe. Couper
  un clip au milieu d'un fondu donne à chaque morceau sa portion de courbe → pas de marche.
* **Retourner** (`reversed`) est un drapeau de clip, pas un rendu : lecture à l'envers, instantané.
* **Enveloppe de volume** par piste : liste de points `(trame, gain)` interpolés linéairement.
* **Effets qui doivent calculer des échantillons** (EQ, réverb, bruit…) : on *rend* la plage
  sélectionnée de la piste dans une **nouvelle source**, et `replaceRange` remplace la plage par un
  clip sur cette source. L'ancienne source reste sur disque tant que l'historique peut y revenir.
  Ramasse-miettes à la fermeture : supprimer les sources que `EditSession.sourcesInHistory()` ne cite plus.

### 2.2 Formats sur disque

* Un dossier par projet : `<filesDir>/audioeditor/projects/<id>/`
  * `manifest.json` — projet complet + méta (nom, dates, état de l'écran) ; écriture atomique
    (fichier `.tmp` renommé), comme `pixelart/io/ProjectStore.kt`
  * `sources/<idSource>.wav` — WAV RIFF : **16 bits** pour ce qui est importé / enregistré,
    **flottant 32 bits** pour les rendus d'effets (garde la dynamique : « Amplifier » puis
    « Normaliser » ne coupe rien)
  * `sources/<idSource>.peaks` — pyramide min/max pour dessiner la forme d'onde sans relire l'audio
  * `thumb.png` — vignette de la galerie (forme d'onde du mixage)
* Export de projet : `.a2uaudio.zip` (manifest + sources), à l'image de `.a2upix.zip`.
* Ne **pas** brancher le cloud Drive au début : les projets audio sont lourds. À reconsidérer
  (`cloud/projects/CloudProjectSync`) une fois le reste stable.

### 2.3 Moteur audio

* **Mixeur pur** (`core/Mixer`) : produit des blocs stéréo flottants à partir d'un `Project`
  immuable et d'un `SampleProvider`. Utilisé tel quel pour la lecture (temps réel), l'export
  (hors ligne), les aperçus d'effets et la vignette. Loi de panoramique **à la Audacity**
  (centre = plein niveau, le côté opposé s'atténue). Solo l'emporte sur sourdine.
* **Lecture** : un fil + `android.media.AudioTrack` en flottant, écriture bloquante, tampon ≈ 4× le
  minimum. Le curseur est dérivé de `playbackHeadPosition` (jamais d'un minuteur). Boucle, saut
  pendant la lecture, échange d'instantané du projet à chaud (éditer pendant que ça joue).
* **Enregistrement** : réutiliser `AudioRecorder` (mono 16 bits 44,1 kHz → WAV en cache) et
  `MicRecordingService` (service de premier plan, obligatoire depuis Android 14), puis passer le
  fichier dans le même chemin d'import que les fichiers ouverts. Overdub = lancer la lecture du
  projet en même temps (casque conseillé). La compensation de latence est hors périmètre.
* **Décodage** (import) : `MediaExtractor` + `MediaCodec` (déjà utilisés par `WaveformExtractor`),
  repli sur `FFmpegKit` pour les formats que le téléphone ne lit pas. Rééchantillonnage par sinc
  fenêtré (pas d'interpolation linéaire).
* **Export** : mixage hors ligne en WAV temporaire, puis `FFmpegKit.executeWithArguments` (tableau
  d'arguments, **pas** de chaîne passée à un shell : plus d'échappement à maintenir) vers
  MP3 / AAC / FLAC / OGG / WAV (16, 24, 32 flottant). Mêmes encodeurs que l'ancien code (ce sont
  ceux que la variante FFmpeg du projet embarque ; ne pas en supposer d'autres, p. ex. Opus).

### 2.4 Effets

* Pur Kotlin, sur des tampons flottants planaires, **en flux** par blocs (mémoire bornée même sur
  une heure d'audio). Interface commune : un lecteur de trames en entrée, un écrivain en sortie,
  une progression et un drapeau d'annulation. Les effets en deux passes (normaliser) relisent
  l'entrée deux fois.
* Catalogue visé (↔ Audacity) :
  * **Volume / fondus** : amplifier, normaliser (crête et RMS, retirer le décalage continu), fondu
    d'entrée / de sortie (5 courbes), inverser la polarité, silence, fondu enchaîné
  * **Filtres / égalisation** : passe-bas, passe-haut, passe-bande, coupe-bande, égaliseur
    graphique 10 bandes (+ préréglages), graves / aigus (étagères), wah
  * **Dynamique** : compresseur (seuil, ratio, attaque, relâchement, genou, gain), limiteur, porte
    de bruit, expandeur
  * **Temps / hauteur** : vitesse (type cassette), tempo (WSOLA, hauteur conservée), hauteur
    (tempo conservé), Paulstretch (étirement extrême), tronquer les silences
  * **Espace / modulation** : écho, réverbération (Freeverb), chorus, flanger, phaser, trémolo,
    vibrato, distorsion / saturation, bit-crusher
  * **Réparation** : réduction de bruit (profil + soustraction spectrale), retrait du décalage
    continu, trouver les saturations
  * **Canaux** : stéréo → mono, échanger G/D, panoramique
  * **Génération** : silence, tonalité (sinus, carré, scie, triangle), bruit (blanc, rose, brun), métronome
  * **Opérations de modèle (instantanées, sans rendu)** : retourner, répéter, dupliquer, fondus de clip, gain de clip
* **Aperçu** : rendre au plus ≈ 6 s de la sélection (ou de la position de lecture) en mémoire et
  les jouer avec un `AudioTrack` à part.
* L'interface des paramètres est **pilotée par des données** (un registre `EffectDef` avec ses
  `Param`) : une seule feuille générique à sliders pour tous les effets.

### 2.5 Interface

* **Galerie** (`AudioEditorLibraryActivity`) : copie conforme de l'esprit de
  `pixelart/PixelArtLibraryActivity.kt` — grille de cartes (vignette = forme d'onde du mixage,
  nom, durée · nombre de pistes · date), bouton « Nouveau » flottant, menu ⋮ (ouvrir un fichier
  audio comme nouveau projet, importer un projet), appui long ou ⋮ sur une carte = feuille d'actions
  (ouvrir, renommer, dupliquer, exporter, supprimer). Réutiliser les styles `PxIconButton`,
  `PxTitle`, `PxCaption`, `bg_px_card`, `item_px_project.xml` comme modèle.
* **Éditeur** (`AudioEditorActivity`, nouveau, reçoit l'id du projet) : un `ViewModel` garde la
  session (comme `pixelart/ui/EditorViewModel`) → la rotation ne perd rien ; sauvegarde
  automatique avec temporisation + à la sortie.
  * `TimelineView` : **une seule vue** custom qui dessine règle, pistes (en-têtes épinglés à
    gauche : nom, M / S, ⋮), clips avec forme d'onde, repères, sélection, curseur. Cache bitmap du
    contenu (invalidé par défilement / zoom / version du projet) ; le curseur se dessine par-dessus.
  * Gestes : toucher = placer le curseur + choisir la piste ; appui long puis glisser = sélection de
    plage (poignées aux deux bouts) ; glisser = défiler (inertie) ; pincer = zoom horizontal ;
    double toucher sur un clip = sélectionner le clip ; outil **Déplacer** : glisser un clip (aimant
    vers curseur / repères / bords), glisser ses bords = rogner, poignées de coin = fondus.
  * Barre de transport (début, lecture/pause, stop, enregistrer, boucle, temps, vumètre),
    barre d'édition (annuler, rétablir, couper, copier, coller, supprimer, scinder, rogner,
    silence, dupliquer, tout sélectionner, aimant), menus Effets / Pistes / Projet / Analyse en
    **feuilles du bas** (`actionSheet`, `bottomSheet` de `pixelart/ui/UiKit.kt`, à réutiliser tels quels).
* Textes : **français et anglais uniquement** (demande explicite du propriétaire, ne pas traduire
  dans les 12 autres langues) : `values/` + `values-fr/`, dans un nouveau `strings_audio_studio.xml`
  (c'est la convention des modules refondus, cf. `strings_pixel_studio.xml`) ; les autres langues
  retombent sur l'anglais. Tout nouveau texte visible va dans ces deux fichiers, jamais en dur dans le Kotlin.

## 3. Organisation du code

```
audioeditor/
  core/      Model.kt  Timeline.kt  EditSession.kt        ← étape 1 (fait)
             Mixer.kt  SampleProvider.kt  Peaks.kt  Resampler.kt   ← étape 2
  dsp/       Effect.kt (interfaces) + un fichier par famille d'effets  ← étape 3
  io/        WavFile.kt  PcmReader.kt  ProjectStore.kt  ManifestCodec.kt
             AudioImporter.kt  AudioExporter.kt  LegacyMigration.kt     ← étapes 2 et 4
  engine/    PlaybackEngine.kt  RecordController.kt  PreviewPlayer.kt    ← étape 5
  ui/        EditorViewModel.kt  TimelineView.kt  EffectCatalog.kt  Sheets.kt …  ← étapes 6-8
  AudioEditorLibraryActivity.kt   AudioEditorActivity.kt (réécrit)
  MicRecordingService.kt  AudioRecorder.kt  SpectrumView.kt  FFTProcessor.kt   ← conservés
```

`core/`, `dsp/` et la partie « disque » de `io/` n'ont **aucun import Android** (sauf
`org.json`, dont les tests chargent la vraie bibliothèque : `testImplementation("org.json:json")`
est déjà dans `app/build.gradle.kts`). Tout ce qui est logique se teste donc en JVM.

À supprimer en fin de chantier (étape 9) : `AudioEditorProjectManager`, `AudioEditorViewModel`,
`AudioTrack` (le modèle d'avant), `WaveformView`, `AudioVisualizationView`, `item_audio_track.xml`,
`activity_audio_editor.xml` (ancienne version), `dialog_export_audio.xml`, et les chaînes
`audio_editor_*` devenues inutiles (vérifier d'abord avec `grep` qu'aucun autre module — Dictaphone —
ne les utilise). Retirer les anciennes chaînes en `values/` et `values-fr/` ; dans les 12 autres
langues c'est du nettoyage facultatif (les clés orphelines ne gênent pas la compilation).

## 4. Conventions du dépôt à respecter

* Commentaires et noms de commit **en français**, ton explicatif (voir `git log`). Un commit =
  une étape livrée, message `Éditeur audio : …`.
* Chaque module refondu a ses **tests JVM** dans `app/src/test/java/com/Atom2Universe/app/<module>/`
  (JUnit 4). Les écrire en même temps que le code.
* Tout écran : `LocaleHelper.applyLocale` dans `attachBaseContext`, `AppThemeManager.applyAppStyle`
  (galerie) ou `AudioThemedActivity` (thème audio), `enableImmersiveMode()`.
* Les écrans de la suite audio utilisent `R.color.audio_*` et `AudioStyle` ; les messages passent par
  `audio.AudioFeedback` (bulle) plutôt que `Toast`.
* Ne pas casser : `MicRecordingService` (Dictaphone), `SpectrumView` + `FFTProcessor` (Créateur SF2).
* Le manifeste déclare déjà `AudioEditorActivity` (`configChanges` complet). Ajouter la galerie à côté.

## 5. Environnement de build (session cloud)

* `./gradlew` n'est pas exécutable : lancer `sh ./gradlew …`.
* Première exécution : Gradle 9.4.1 se télécharge, le NDK 28 s'installe tout seul (module
  `mididriver`) — compter ≈ 4 min. Maven Central peut répondre **429** de façon transitoire : relancer.
* Compiler : `sh ./gradlew --no-daemon :app:compileDebugKotlin`
* Tests de l'éditeur audio : `sh ./gradlew --no-daemon :app:testDebugUnitTest --tests "*audioeditor.*"`
  (≈ 45 s une fois le cache chaud). **Ne pas se fier au seul « BUILD SUCCESSFUL »** : compter les tests dans
  `app/build/test-results/testDebugUnitTest/TEST-*audioeditor*.xml` (`tests=… failures=0`).
* Vérifié le 2026-10-01 : compilation complète OK, 171 tests de l'éditeur audio verts
  (Timeline 35, Mixer 13, WavFile 11, Peaks 9, Resampler 9, Effects 19, FiltersDynamics 18, RenderRange 11,
  Space 18, TimePitch 15, NoiseGenerate 13).
* Sans `-PbancsMesure`, les bancs de mesure sont exclus (voir `app/build.gradle.kts`).

## 6. Étapes détaillées

### Étape 1 — Cœur du modèle ✅
- [x] `core/Model.kt` : `Fade`/`FadeShape`, `Source`, `Clip`, `EnvPoint`, `Track`, `Marker`, `Project`
- [x] `core/Timeline.kt` : `addSource/addTrack/removeTrack/moveTrack/addClip`, `splitAt`,
  `deleteRange` (ripple ou non), `silenceRange`, `insertSilence`, `copyRange` + `pasteAt`
  (`Clipboard`), `trimToRange`, `repeatRange`, `moveClip`, `trimClipLeft/Right`, `setClipGain`,
  `setClipFades`, `deleteClip`, `duplicateClip`, `reverseRange`, `replaceRange`, repères
- [x] `core/EditSession.kt` : historique par instantanés, `preview` + `commitGesture` pour les
  gestes continus (un curseur tiré = un seul pas d'annulation), presse-papiers, `sourcesInHistory`
- [x] `TimelineTest` (35 cas : coupes, ripple, fondus continus, retourné, bornes de rognage, historique)

### Étape 2 — Fichiers PCM, pics, rééchantillonnage, mixeur ✅
- [x] `io/WavFile.kt` : `WavFile.readInfo` (formats PCM 8/16/24/32 bits et flottant 32 bits,
  `WAVE_FORMAT_EXTENSIBLE`, chunks inconnus et octet de bourrage ignorés, taille de données absente
  = fichier non fermé relu quand même), `WavWriter` en flux (en-tête patché à la fermeture,
  `fmt` 18 + `fact` pour le flottant), `PcmReader` (lecture aléatoire planaire, zéros hors
  bornes, `@Synchronized`, tampon d'octets réutilisé), `FileSampleProvider` (un lecteur ouvert par source)
- [x] `core/Mixer.kt` : interface `SampleProvider` + `Mixer.render(project, start, frames, outL, outR, levels)` ;
  gain de clip × fondus × enveloppe × volume de piste ; panoramique à la Audacity ; mono → stéréo ;
  clips retournés ; chevauchements additionnés ; solo / sourdine ; volume général ; crêtes G/D
  (`Levels`) ; chemin rapide hors fondus ; tampons préalloués (pas d'allocation par bloc)
- [x] Tests : `MixerTest` (13 cas, dont « petits blocs = un seul bloc » avec fondus + coupe + retourné)
  et `WavFileTest` (11 cas : 16 bits / flottant, écrêtage, blocs, bornes, décalage, chunk LIST impair,
  fichier non fermé, fichier invalide)
- [x] `core/Peaks.kt` : `PeakBuilder` (un seul passage, bloc de base 256 trames), `PeakData` (octets
  signés ±127, min arrondi vers le bas et max vers le haut : la silhouette englobe toujours le signal),
  `columns()` = réduction à la volée en colonnes de dessin pour un nombre de trames par pixel donné
  (à utiliser tant que ≥ 256 trames/pixel), `columnsFromSamples()` pour le zoom serré (on lit alors
  l'audio brut), format de fichier `.peaks` versionné (`A2PB`). `io/PeakFiles.kt` : `build` (en flux),
  `save` atomique, `load` (null si abîmé), `loadOrBuild`
  * Pas de second niveau de résumé pour l'instant : une heure entièrement dézoomée parcourt
    ≈ 600 000 blocs par dessin. Acceptable derrière le cache bitmap de `TimelineView` ; à ajouter
    seulement si la mesure sur appareil le demande.
- [x] `core/Resampler.kt` : sinc fenêtré Kaiser (β 8,6), noyau tabulé sur 1024 phases interpolées,
  lignes normalisées (le continu passe à l'identique), coupure à 95 % de la nouvelle Nyquist en
  réduction, **en flux** (`process` par blocs de toute taille puis `finish`) ; `resampleAll` pour les
  petits signaux. Même sortie, à 1e-6 près, quelle que soit la taille des blocs
- [x] Tests : `PeaksTest` (9 : bloc englobant le vrai min/max, morceaux quelconques, colonnes vs calcul
  direct, hors bornes, flux tronqué, fichier de crêtes abîmé recalculé) et `ResamplerTest` (9 :
  44,1↔48 kHz à < 3e-3 d'un sinus idéal, ×2, repliement coupé (15 kHz → 22,05 kHz : RMS < 0,01),
  bande passante, continu, stéréo, blocs de 1 à 4096)

### Étape 3 — Effets DSP purs ✅

**3a — socle + volume + filtres + dynamique ✅**
- [x] `dsp/Effect.kt` : `FrameReader` / `FrameWriter` (planaire, par blocs), `RunContext` (progression +
  annulation), `Effect` (`outputChannels`, `changesLength`), `BlockEffect` (durée conservée, travail sur
  place, **`tailFrames`** = silence ajouté pour laisser mourir une queue, **`latencyFrames`** = retard
  interne compensé automatiquement, `analyze()` pour les passes de mesure), `MemoryReader` /
  `MemoryWriter` / `processInMemory` (aperçus, tests), `dbToLinear` / `linearToDb`
- [x] `dsp/RenderRange.kt` : `TrackRangeReader` (une piste, clips mélangés **sans** volume / pan /
  enveloppe / sourdine : un effet ne grave pas des réglages de mixage), `RenderRange.apply` (une
  nouvelle source **flottante 32 bits** + `.peaks` par piste, `replaceRange`, progression répartie
  sur les pistes, annulation = fichier effacé + projet intact, pistes verrouillées / vides ignorées)
- [x] `dsp/Volume.kt` : `Amplify`, `Invert`, `Normalize` (crête ou RMS, retrait du continu, canaux liés
  ou indépendants, garde-fou de crête), `RemoveDc`, `FadeEffect` (5 courbes), `StereoToMono`, `SwapChannels`
- [x] `dsp/Filters.kt` : `Biquad` (RBJ : passe-bas / haut / bande, coupe-bande, cloche, étagères),
  `FilterEffect` (Butterworth 12–48 dB/oct, passe-bande, coupe-bande), `GraphicEq` 10 bandes + 6
  préréglages (`GraphicEq.PRESETS`), `BassTreble`
- [x] `dsp/Dynamics.kt` : `Compressor` (détecteur lié, coude progressif, compensation), `Limiter` (mur de
  briques à anticipation 5 ms, retard compensé, gain lissé par moyenne glissante du minimum glissant),
  `NoiseGate` (attaque / maintien / relâchement)
- [x] Tests : `EffectsTest` (19), `FiltersDynamicsTest` (18), `RenderRangeTest` (11), `DspTestUtil`
  (mesure du gain en dB d'un effet à une fréquence donnée)

**3b — espace, modulation, temps / hauteur, bruit, génération ✅**
- [x] `dsp/Fft.kt` : FFT radix-2 en place, double précision, tables par taille (sert à la réduction de
  bruit, à Paulstretch et aux tests ; `FFTProcessor` existant reste réservé aux spectrogrammes)
- [x] `dsp/Space.kt` : `Echo` (retour, queue jusqu'à −60 dB), `Reverb` (Freeverb : 8 peignes + 4
  passe-tout par voie, voie droite décalée de 23, largeur stéréo, queue estimée d'après la taille de la
  pièce), `Chorus`, `Flanger`, `Vibrato` (ligne à retard fractionnaire `ModDelay`), `Tremolo`,
  `Phaser` (passe-tout 1er ordre balayés, coefficient recalculé tous les 16 échantillons),
  `Distortion` (douce `tanh`, nette, « lampe » asymétrique), `BitCrusher` (entier signé sur n bits + maintien)
- [x] `dsp/TimePitch.kt` : `ChangeSpeed` (rééchantillonnage), `ChangeTempo` (**WSOLA** : fenêtres de
  Hann ~22 ms recouvertes à 50 %, recherche de ±8 ms par corrélation normalisée grossière sur signal
  décimé ×4 puis affinée, décalage choisi sur le mono et appliqué à toutes les voies), `ChangePitch`
  (WSOLA puis rééchantillonnage, ±24 demi-tons), `PaulStretch` (fenêtre sinus, phases aléatoires
  hermitiennes, gain √2, graine reproductible), `TruncateSilence`
- [x] `dsp/NoiseReduction.kt` : `NoiseProfile` (puissance moyenne par case, sur un échantillon de bruit) +
  `NoiseReduction` (STFT 2 048 / saut 512, racine de Hann à l'analyse et à la synthèse, soustraction
  spectrale avec sur-soustraction `10^(sensibilité/10)`, plancher `reductionDb`, lissage fréquentiel
  qui **ne creuse jamais un pic utile**, gain qui remonte tout de suite et redescend lentement) ;
  latence = 2 048 trames, compensée par le socle
- [x] `dsp/Generate.kt` : `ToneReader` (sinus, carré et scie PolyBLEP, triangle, fondu de 2 ms),
  `NoiseReader` (blanc, rose de Kellet, brun), `ClickTrackReader` (métronome accentué),
  `GenerateSource.apply` (écrit une source 16 bits + crêtes et pose le clip, nouvelle piste ou existante)
- [x] Tests : `SpaceTest` (18), `TimePitchTest` (15 : durée et fréquence mesurées après vitesse /
  tempo / hauteur / Paulstretch, tempo = 1 redonne le signal), `NoiseGenerateTest` (13 : bruit seul
  atténué de > 12 dB, tonalité conservée à ±1 dB, pente des bruits colorés par FFT, métronome)

**Ce que l'étape a appris (à garder en tête pour l'interface)**
- Tout effet qui ne garde pas la durée **exactement** doit déclarer `changesLength = true`
  (tempo, hauteur, vitesse, Paulstretch, tronquer les silences) : `replaceRange(ripple = true)`
  recolle alors la suite sans trou ni chevauchement. `ChangePitch` en fait partie : WSOLA rend la durée
  à une fenêtre (≈ 10–20 ms) près.
- Les effets « longueur conservée + queue » (écho, réverb) passent par `BlockEffect.tailFrames` ;
  l'interface doit **prévenir** que la plage rendue dépasse la sélection (la queue recouvre la suite).
- Un effet ne grave jamais volume / pan / enveloppe / sourdine de la piste (`TrackRangeReader`).
- Les rendus sont en flottant 32 bits : l'écran peut proposer « Normaliser » juste après « Amplifier ».
- Plafonds testés : tempo ×0,25–×4, hauteur ±24 demi-tons, Paulstretch ×1–×500, vitesse ×0,1–×10.
  Les afficher dans les curseurs.
- Mesure des tests : `DspTest.gainDb` (gain d'un effet à une fréquence), `zeroCrossFreq`,
  `dominantFreq` (FFT) — réutilisables pour tout nouvel effet.
- Piège rencontré : un test qui synthétise une voix avec vibrato doit **intégrer la phase**
  (`phase += 2π·f(t)/fs`), pas calculer `sin(2π·f(t)·t)` — sinon la FM croît avec le temps.

### Étape 4 — Stockage, import, export
- [ ] `io/ManifestCodec.kt` : `Project` ⇄ JSON (versionné, tolérant aux champs inconnus)
- [ ] `io/ProjectStore.kt` : `list()` (résumés pour la galerie), `create`, `open`, `save` atomique,
  `rename`, `duplicate`, `delete`, `exportZip` / `importZip`, ramasse-miettes des sources
- [ ] `io/AudioImporter.kt` : décodage `MediaCodec` → rééchantillonnage → WAV 16 bits + pics ;
  repli FFmpeg ; import de plusieurs fichiers ; extraction de la piste audio d'une vidéo
- [ ] `io/AudioExporter.kt` : mixage hors ligne → WAV temporaire → `FFmpegKit.executeWithArguments`
  (+ métadonnées titre / artiste / album / année), export de la sélection, une piste par fichier
- [ ] `io/LegacyMigration.kt` : si `audio_editor_project.json` (ancien projet) existe, le convertir en
  projet « Ancien projet » une seule fois, puis le renommer pour ne pas le refaire
- [ ] Tests : aller-retour manifeste, zip, ramasse-miettes (via `PixelArtStoreTest` comme modèle)

### Étape 5 — Lecture et enregistrement
- [ ] `engine/PlaybackEngine.kt` : fil + `AudioTrack` flottant, `play(from, loop)`, `pause`, `stop`,
  `seek`, `setProject` à chaud, position fiable, vumètre, rappel de fin
- [ ] `engine/PreviewPlayer.kt` : joue un tampon en mémoire (aperçu d'effet), annulable
- [ ] `engine/RecordController.kt` : `AudioRecorder` + `MicRecordingService`, niveau en direct,
  overdub synchronisé avec la lecture, fichier → import → nouveau clip à la position du curseur
- [ ] Test sur appareil (non automatisable ici) : lecture sans craquement avec 8 pistes, seek en lecture, boucle

### Étape 6 — Galerie de projets
- [ ] `AudioEditorLibraryActivity` + `activity_audio_editor_library.xml` + `item_audio_project.xml`
  (calques sur `activity_pixel_art_library.xml` / `item_px_project.xml`)
- [ ] Vignette = forme d'onde du mixage (générée à l'enregistrement, comme `makeThumbnail`)
- [ ] Feuille « Nouveau projet » : nom, fréquence (44,1 / 48 kHz), départ « vide » / « enregistrer » /
  « importer des fichiers »
- [ ] Brancher `AudioSubHubActivity` sur la galerie (la tuile garde son illustration : si on change
  la classe cible, mettre à jour la clé dans `HubTileArtworks.kt:118`) ; déclarer l'activité dans le manifeste

### Étape 7 — Éditeur
- [ ] `EditorViewModel` (session, store, moteur, travaux de rendu avec progression et annulation)
- [ ] `TimelineView` (règle, pistes, clips, formes d'onde depuis les pics, sélection, curseur, repères, zoom, défilement, aimant)
- [ ] Outils Sélection / Déplacer (rogner, fondus par poignées) ; en-têtes de piste (M / S / ⋮ : volume, pan, couleur, renommer, verrouiller, dupliquer, supprimer, monter/descendre)
- [ ] Barres transport + édition ; `activity_audio_editor.xml` réécrit ; rotation sans perte
- [ ] `EffectCatalog` + feuille générique de paramètres avec **Aperçu** / **Appliquer** / **Annuler** pendant un rendu

### Étape 8 — Finitions
- [ ] Export : feuille de format (WAV 16/24/32f, MP3, AAC, FLAC, OGG), débit, métadonnées, plage, une piste par fichier
- [ ] Spectrogramme par piste (STFT mis en cache par source en bitmap), fenêtre d'analyse de fréquences, stats de sélection (crête, RMS, saturations)
- [ ] Repères et zones nommés, liste des repères, saut de repère en repère, export en texte
- [ ] Outil Enveloppe (points sur la piste, appui long = supprimer)
- [ ] Génération (silence, tonalité, bruit, métronome) ; tronquer les silences ; mixer et rendre

### Étape 9 — Nettoyage
- [ ] Supprimer l'ancien code et les anciens textes (liste §3), relancer `compileDebugKotlin` + tous les tests
- [ ] `strings_audio_studio.xml` en `values/` et `values-fr/`
- [ ] Passage sur appareil : rotation, ouverture d'un gros fichier (> 30 min), manque d'espace disque, permission micro refusée

## 7. Risques et points d'attention

* **Mémoire** : ne jamais charger une piste entière en `FloatArray`. Tout passe par des blocs
  (≤ 64 k trames) ; les pics se calculent en un seul passage à l'import.
* **Fil audio** : aucune allocation, aucun `synchronized` long, aucune E/S bloquante lente dans la
  boucle de rendu (lecture de fichier par blocs de 4 k trames avec un petit cache dans `PcmReader`).
* **Espace disque** : chaque rendu d'effet écrit une nouvelle source (flottant 32 bits = 2× un WAV
  16 bits). Prévenir avant un rendu long si l'espace libre est insuffisant ; ramasser les sources
  orphelines à la fermeture et au prochain lancement (plantage).
* **Android 14+** : l'enregistrement exige le service de premier plan de type microphone (déjà
  géré par `MicRecordingService`).
* **Clips qui se chevauchent** : décider une fois pour toutes quel clip reçoit un toucher (le
  dernier de la liste = le plus haut) et l'écrire dans `TimelineView`.
* **Aimant** : arrondir aux trames entières ; ne jamais produire de clip de longueur ≤ 0.
* **Ancien projet** : migrer au lieu de supprimer sans prévenir (voir `LegacyMigration`).

## 8. Idées hors périmètre (à ne pas oublier, pas à faire maintenant)

Synchronisation Drive des projets · compensation de latence d'enregistrement · plug-ins VST/LV2
(impossible sur Android) · lecture à vitesse variable en temps réel · édition au niveau de
l'échantillon (crayon) · multi-sélection de clips · automation du panoramique · export de piste
MIDI vers le Créateur SF2 · ouvrir un fichier audio depuis un autre appli via « Ouvrir avec ».
