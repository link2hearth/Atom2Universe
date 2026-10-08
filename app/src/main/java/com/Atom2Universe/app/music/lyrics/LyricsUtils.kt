package com.Atom2Universe.app.music.lyrics

import com.Atom2Universe.app.music.MusicTagEditor
import com.Atom2Universe.app.music.model.MusicTrack

/**
 * Utilitaires pour les opérations sur les paroles.
 */
object LyricsUtils {

    /**
     * Nettoie les métadonnées (titre, artiste) pour améliorer les correspondances.
     *
     * Supprime :
     * - "(feat. ...)", "[Featuring]", "(ft. ...)"
     * - "(Remastered)", "[Live]", "(Remix)", "(Radio Edit)"
     * - Années entre parenthèses "(2020)"
     * - Pseudos courts "(user123)", "[DJ]"
     * - Numéros de piste " - 01", "_01"
     * - Extensions de fichiers ".mp3", ".flac"
     * - Espaces multiples
     */
    fun cleanMetadata(text: String): String {
        return text
            // Supprimer le numéro de piste en tête (1 ou 2 chiffres + séparateur/espace)
            // seulement si le reste contient au moins une lettre — pour éviter de vider
            // un titre purement numérique comme "05". Les titres à 3+ chiffres sont
            // préservés (ex: "365 Days", "100 Years").
            .replace(Regex("""^\d{1,2}[\s._-]+(?=.*[a-zA-Z])"""), "")
            // Remove feat., featuring, ft., etc.
            .replace(Regex("""\(?\s*[Ff]eat\.?\s+[^)]*\)?"""), "")
            .replace(Regex("""\(?\s*[Ff]eaturing\s+[^)]*\)?"""), "")
            .replace(Regex("""\(?\s*[Ff]t\.?\s+[^)]*\)?"""), "")
            .replace(Regex("""\(?\s*[Ww]ith\s+[^)]*\)?"""), "")
            // Remove (Remastered), (Live), (Remix), (Radio Edit), etc.
            .replace(Regex("""\([^)]*(?:[Rr]emaster|[Ll]ive|[Rr]emix|[Ee]dit|[Vv]ersion|[Ee]dition|[Rr]adio|[Dd]eluxe|[Bb]onus)[^)]*\)"""), "")
            // Remove [Remastered], [Live], [Remix], etc.
            .replace(Regex("""\[[^\]]*(?:[Rr]emaster|[Ll]ive|[Rr]emix|[Ee]dit|[Vv]ersion|[Ee]dition|[Rr]adio|[Dd]eluxe|[Bb]onus)[^\]]*\]"""), "")
            // Remove year in parentheses like (2020)
            .replace(Regex("""\(\d{4}\)"""), "")
            // Remove single lowercase/uppercase words in parentheses like (pseudo), (DJ), (ABC)
            // This catches things like "Song Title(user123)" or "Title (xyz)"
            .replace(Regex("""\s*\([a-zA-Z0-9_-]{1,15}\)"""), "")
            // Remove single lowercase/uppercase words in brackets like [pseudo], [DJ], [ABC]
            // This catches things like "Song Title[user123]" or "Title [xyz]"
            .replace(Regex("""\s*\[[a-zA-Z0-9_-]{1,15}\]"""), "")
            // Remove trailing numbers/tags like " - 01", "_01", etc.
            .replace(Regex("""[\s_-]+\d+$"""), "")
            // Remove common file suffixes
            .replace(Regex("""(?i)\.(mp3|flac|ogg|m4a|wav)$"""), "")
            // Remove extra whitespace and trim
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Normalise agressivement un texte pour les tentatives de recherche avancées.
     * Utilisée en dernier recours quand cleanMetadata() n'a pas permis de trouver des paroles.
     *
     * En plus de cleanMetadata() (qui gère déjà feat., live, remastered, etc.) :
     * - Supprime TOUT le contenu restant entre parenthèses et crochets
     * - Supprime les symboles (&, !, ?, :, /, ...) et les chiffres
     * - Ne conserve que les lettres (Unicode, accents inclus) et les espaces
     *
     * Exemple : "Angus & Julia Stone" → "Angus  Julia Stone" → "Angus Julia Stone"
     *           "My Artist! (Vol. 2)" → "My Artist"
     *           "Band / Solo" → "Band  Solo" → "Band Solo"
     *           "The Beatles" → "Beatles"
     */
    fun normalizeForSearch(text: String): String {
        return cleanMetadata(text)
            // Supprimer tout contenu restant entre parenthèses
            .replace(Regex("""\([^)]*\)"""), "")
            // Supprimer tout contenu restant entre crochets
            .replace(Regex("""\[[^\]]*\]"""), "")
            // Supprimer "The" en début de chaîne (ex: "The Beatles" → "Beatles")
            .replace(Regex("""^[Tt]he\s+"""), "")
            // Supprimer les symboles et chiffres — ne garder que les lettres unicode et espaces
            .replace(Regex("""[^\p{L}\s]"""), " ")
            // Nettoyer les espaces multiples
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Construit une requête de recherche optimisée pour trouver des paroles.
     * Format : "artiste titre lyrics" avec métadonnées nettoyées.
     */
    fun buildLyricsSearchQuery(title: String, artist: String): String {
        val cleanTitle = cleanMetadata(title)
        val cleanArtist = cleanMetadata(artist)
        return "$cleanArtist $cleanTitle lyrics"
    }

    /**
     * Lit les métadonnées directement depuis les tags ID3 du fichier MP3.
     * Retourne un objet avec titre et artiste lus depuis le fichier, ou null en cas d'erreur.
     * Priorité : Tags ID3 du fichier > MusicTrack du MediaStore
     */
    suspend fun getMetadataFromFile(track: MusicTrack): MetadataInfo? {
        // Lire les tags directement depuis le fichier MP3
        val tagInfo = MusicTagEditor.readTags(track)

        return if (tagInfo != null && tagInfo.title.isNotBlank() && tagInfo.artist.isNotBlank()) {
            // Utiliser les tags lus depuis le fichier (plus à jour)
            MetadataInfo(
                title = tagInfo.title,
                artist = tagInfo.artist
            )
        } else {
            // Fallback sur les données du MediaStore
            MetadataInfo(
                title = track.title,
                artist = track.artist
            )
        }
    }

    /**
     * Métadonnées d'un track (titre et artiste).
     */
    data class MetadataInfo(
        val title: String,
        val artist: String
    )
}
