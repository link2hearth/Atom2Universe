# Son de Space Fight

## Direction musicale

Trois compositions originales sont écrites dans `StarsWarProceduralMusic.kt` :

- **Petites orbites** : célesta, 96 BPM, vagues 1–5.
- **Courrier des comètes** : marimba, 108 BPM, vagues 6–10.
- **Carrousel stellaire** : vibraphone, 116 BPM, vagues 11–15.

Le cycle recommence ensuite. Chaque morceau a huit mesures mélodiques et un
second passage avec des réponses instrumentales (environ 33 à 40 secondes par
boucle). Piano électrique et contrebasse accompagnent des accords majeurs 7 et
mineurs 7. Les changements se font à la mesure suivante, sur une harmonie commune.
Pas de mélodie aléatoire, de cymbales ni d'escalade vers des sons agressifs.

## Bruitages

`generate_sfx.py` produit les neuf WAV mono PCM 16 bits à 22 050 Hz dans
`app/src/main/assets/spacefight/audio`. Python 3 suffit, sans bibliothèque externe.
Les fichiers sont créés à l'avance, pas sur le téléphone : environ 205 Kio au total.

Trois variantes de tir arrondi, une bulle de destruction, un impact grave et
quatre petits carillons pour boss, nouvelle vague, météores et fin de partie.
Les attaques et fins sont adoucies ; le générateur vérifie l'absence d'écrêtage
et les extrémités proches de zéro. Aucun échantillon tiers n'est utilisé.

SoundPool gère huit voix au maximum. Les tirs sont discrets et espacés d'au
moins 115 ms ; les destructions sont limitées à une toutes les 85 ms. Les alertes
importantes ont priorité. La pause arrête les bruitages en cours et la musique.
Le retour depuis l'arrière-plan conserve le secteur musical et la pause du jeu.

## Recherche ayant guidé le choix

- Android SoundPool, sons courts préchargés :
  https://developer.android.com/reference/android/media/SoundPool
- Kenney Sci-fi Sounds, alternative disponible en CC0 :
  https://kenney.nl/assets/sci-fi-sounds
- Mutopia, Gymnopédie nº 1 de Satie, édition et MIDI indiqués Public Domain :
  https://www.ibiblio.org/mutopia/cgibin/piece-info.cgi?id=37

Cette version utilise uniquement les compositions et bruitages créés pour le
projet. Les six Gnossiennes déjà présentes dans Assets/Chiptune/Satie ne sont pas
utilisées ici : leur provenance n'a pas été établie pendant ce travail.

## Écoute sur téléphone à effectuer par le propriétaire

- Tir continu puis nombreux ennemis détruits : musique toujours intelligible.
- Dégât au joueur, boss, météores et fin de partie : signaux distincts.
- Vagues 6, 11 et 16 : changement d'instrument sans coupure au milieu d'une mesure.
- Pause, reprise, accueil Android et retour : aucun son bloqué ni reprise des
  anciens bruitages, et pas de retour systématique au premier thème.

Vérification autorisée : `./gradlew compileDebugKotlin`. Le timbre réel de Sonivox
et l'équilibre musique/bruitages restent à valider à l'écoute sur appareil.
