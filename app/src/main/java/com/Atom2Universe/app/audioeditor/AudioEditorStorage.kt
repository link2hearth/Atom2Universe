package com.Atom2Universe.app.audioeditor

import android.content.Context
import com.Atom2Universe.app.audioeditor.io.ProjectStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import java.io.File

/** Le dossier des projets audio, partagé par la galerie et l'éditeur : un seul [ProjectStore] par processus. */
object AudioEditorStorage {
    @Volatile
    private var store: ProjectStore? = null

    fun store(context: Context): ProjectStore = store ?: synchronized(this) {
        store ?: ProjectStore(File(context.applicationContext.filesDir, "audioeditor/projects")).also { store = it }
    }

    /** Les écritures survivent à l'écran qui les a lancées (on quitte l'éditeur juste après une édition). */
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Une seule écriture de manifeste à la fois : deux sauvegardes qui se chevauchent s'écraseraient dans le désordre. */
    val saveMutex = Mutex()

    /** Écritures lancées et pas finies : la galerie attend qu'il retombe à 0 avant de relire les projets. */
    val writesInFlight = MutableStateFlow(0)
}
