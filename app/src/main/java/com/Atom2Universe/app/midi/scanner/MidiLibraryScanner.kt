package com.Atom2Universe.app.midi.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.data.MidiTrack
import com.Atom2Universe.app.midi.repository.MidiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Scanner pour bibliothèque MIDI
 *
 * Scan incrémental d'un dossier via ContentResolver direct (rapide),
 * compare avec la base existante pour n'insérer que les nouveaux fichiers
 * et supprimer ceux qui n'existent plus.
 */
class MidiLibraryScanner(
    private val context: Context,
    private val repository: MidiRepository
) {

    /**
     * État du scan en cours
     */
    data class ScanProgress(
        val totalFiles: Int = 0,
        val scannedFiles: Int = 0,
        val currentFile: String = "",
        val isScanning: Boolean = false,
        val errors: List<String> = emptyList()
    )

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress

    /**
     * Info d'un document enfant, récupérée en une seule requête ContentResolver
     * par dossier (au lieu d'une requête par fichier avec DocumentFile)
     */
    private data class DocumentInfo(
        val documentId: String,
        val displayName: String,
        val mimeType: String?,
        val size: Long
    ) {
        val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
        val isMidiFile: Boolean get() {
            val lower = displayName.lowercase()
            return lower.endsWith(".mid") || lower.endsWith(".midi")
        }
    }

    /**
     * Contexte de dossier pour déterminer artiste/album depuis la hiérarchie
     * null = niveau racine, artistName rempli = dans un dossier artiste
     */
    private data class FolderContext(
        val artistName: String?,
        val albumPath: List<String>
    )

    /** Fichier MIDI trouvé pendant le parcours, avec le dossier qui le contient */
    private data class FoundMidiFile(
        val info: DocumentInfo,
        val folderContext: FolderContext?
    )

    /**
     * Lance le scan d'un dossier MIDI
     *
     * Mode incrémental: compare les fichiers sur disque avec la base de données,
     * n'insère que les nouveaux fichiers et supprime ceux qui n'existent plus.
     *
     * @param folderUri URI du dossier racine (content:// tree URI)
     * @param refresh Si true, supprime aussi les fichiers absents du disque
     * @return Nombre total de fichiers MIDI dans la bibliothèque
     */
    suspend fun scanMidiFolder(folderUri: Uri, refresh: Boolean = false): Int = withContext(Dispatchers.IO) {
        _scanProgress.value = ScanProgress(isScanning = true)

        try {
            // Vérifier que le dossier est valide via DocumentFile (vérification d'accès)
            val folder = DocumentFile.fromTreeUri(context, folderUri)
            if (folder == null || !folder.exists() || !folder.isDirectory) {
                _scanProgress.value = _scanProgress.value.copy(
                    isScanning = false,
                    errors = listOf(context.getString(R.string.midi_engine_scan_invalid_folder))
                )
                return@withContext 0
            }

            val treeUri = folderUri
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)

            // Charger les chemins déjà en base pour comparaison incrémentale
            val existingPaths: Set<String> = repository.getAllFilePaths()

            // Un seul parcours de l'arborescence (le plus coûteux : une requête par dossier)
            val foundFiles = mutableListOf<FoundMidiFile>()
            collectMidiFilesFast(
                treeUri = treeUri,
                parentDocId = rootDocId,
                folderContext = null,
                foundFiles = foundFiles
            )
            _scanProgress.value = _scanProgress.value.copy(totalFiles = foundFiles.size)

            // Comparaison avec la base, sans nouvelle requête
            val allFoundPaths = mutableSetOf<String>()
            val newTracks = mutableListOf<MidiTrack>()
            for (found in foundFiles) {
                val fileUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri, found.info.documentId
                ).toString()
                allFoundPaths.add(fileUri)

                // Skip si déjà en base
                if (fileUri in existingPaths) continue

                newTracks.add(createTrack(found, fileUri))
            }

            // Insérer uniquement les nouveaux tracks
            if (newTracks.isNotEmpty()) {
                repository.addTracks(newTracks)
            }

            // Supprimer les tracks dont le fichier n'existe plus (refresh uniquement)
            if (refresh && existingPaths.isNotEmpty()) {
                val removedPaths = existingPaths - allFoundPaths
                if (removedPaths.isNotEmpty()) {
                    repository.deleteTracksByFilePaths(removedPaths.toList())
                }
            }

            _scanProgress.value = _scanProgress.value.copy(
                isScanning = false,
                scannedFiles = allFoundPaths.size
            )

            return@withContext allFoundPaths.size

        } catch (e: Exception) {
            _scanProgress.value = _scanProgress.value.copy(
                isScanning = false,
                errors = listOf(e.message ?: context.getString(R.string.midi_sf2_err_unknown))
            )
            return@withContext 0
        }
    }

    /**
     * Liste les documents enfants d'un dossier via ContentResolver direct.
     *
     * UNE seule requête renvoie tous les enfants avec toutes leurs métadonnées,
     * au lieu d'une requête par fichier comme DocumentFile.listFiles().
     */
    private fun listChildDocuments(treeUri: Uri, parentDocId: String): List<DocumentInfo> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val results = mutableListOf<DocumentInfo>()

        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    results.add(
                        DocumentInfo(
                            documentId = docId,
                            displayName = name,
                            mimeType = cursor.getString(2),
                            size = cursor.getLong(3)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Accès refusé ou dossier invalide - ignorer silencieusement
        }

        return results
    }

    /**
     * Collecte récursive rapide des fichiers MIDI via ContentResolver (une requête par dossier).
     *
     * Hiérarchie de dossiers:
     * - Racine → dossiers = Artistes, fichiers = artiste inconnu (vide en base)
     * - Dossier artiste → dossiers = Albums, fichiers = album = nom artiste
     * - Sous-dossiers → chemin album joint par " / "
     */
    private fun collectMidiFilesFast(
        treeUri: Uri,
        parentDocId: String,
        folderContext: FolderContext?,
        foundFiles: MutableList<FoundMidiFile>
    ) {
        val children = listChildDocuments(treeUri, parentDocId)

        for (child in children) {
            if (child.isDirectory) {
                // Déterminer le contexte hiérarchique pour le sous-dossier
                val childContext = if (folderContext == null) {
                    // Premier niveau sous la racine = Artiste
                    FolderContext(artistName = child.displayName, albumPath = emptyList())
                } else {
                    // Sous-dossier d'un artiste = partie du chemin d'album
                    FolderContext(
                        artistName = folderContext.artistName,
                        albumPath = folderContext.albumPath + child.displayName
                    )
                }

                collectMidiFilesFast(treeUri, child.documentId, childContext, foundFiles)
            } else if (child.isMidiFile) {
                foundFiles.add(FoundMidiFile(child, folderContext))

                // Mettre à jour la progression (le total n'est connu qu'à la fin du parcours)
                _scanProgress.value = _scanProgress.value.copy(
                    scannedFiles = foundFiles.size,
                    currentFile = child.displayName
                )
            }
        }
    }

    /**
     * Crée la piste d'un nouveau fichier : artiste et album d'après la hiérarchie des dossiers.
     * Artiste ou album inconnu = chaîne vide en base ; l'écran affiche le texte traduit.
     */
    private fun createTrack(found: FoundMidiFile, fileUri: String): MidiTrack {
        val folderContext = found.folderContext
        val artist: String
        val album: String

        if (folderContext == null) {
            // Fichier à la racine
            artist = ""
            album = ""
        } else {
            artist = folderContext.artistName ?: ""
            album = if (folderContext.albumPath.isNotEmpty()) {
                folderContext.albumPath.joinToString(" / ")
            } else {
                // Directement dans le dossier artiste (pas de sous-dossier album)
                artist
            }
        }

        val title = found.info.displayName.replace(
            Regex("\\.midi?$", RegexOption.IGNORE_CASE), ""
        )

        return MidiTrack(
            id = 0,
            title = title,
            artist = artist,
            album = album,
            filePath = fileUri,
            duration = 0L,
            dateAdded = System.currentTimeMillis(),
            fileSize = found.info.size
        )
    }

    /**
     * Réinitialise le state du scanner
     */
    fun reset() {
        _scanProgress.value = ScanProgress()
    }

    companion object {
        private const val TAG = "MidiLibraryScanner"
    }
}
