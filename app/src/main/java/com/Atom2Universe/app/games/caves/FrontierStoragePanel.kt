package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.world.FrontierWorkshops

/** Shared entry point for containers and workshop inventories. */
internal class FrontierStoragePanel(activity: CaveActivity) {
    private val browser=CaveStorageBrowser(activity)
    fun show(snapshot: FrontierWorkshops.View,items: Map<Short,Int>) = browser.show(snapshot,items)
}
