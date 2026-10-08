package com.Atom2Universe.app.science.parentes

import java.io.DataInputStream
import java.io.InputStream

data class SpeciesNote(val key: String, val qid: String, val revision: String, val aliases: List<String>)
data class SpeciesLabel(val qid: String, val revision: String, val aliases: List<String>)

/** Versioned, generated projection of the source JSON; all source edges survive. */
data class LifeArchive(
    val root: String, val version: String, val taxonomy: String, val retrieved: String,
    val nodes: Map<String, LifeNode>, val notes: Map<String, SpeciesNote>, val labels: Map<String, SpeciesLabel>
) {
    companion object {
        fun read(input: InputStream): LifeArchive = DataInputStream(input.buffered()).use { stream ->
            require(stream.readInt() == 0x50544154 && stream.readInt() == 1) { "Unknown atlas format" }
            fun count(maximum: Int = 200_000) = stream.readInt().also { require(it in 0..maximum) }
            val strings = Array(count()) {
                ByteArray(count(1_000_000)).also { stream.readFully(it) }.toString(Charsets.UTF_8)
            }
            fun text(): String = strings[stream.readInt()]
            fun texts(): List<String> = List(count()) { text() }
            val root = text(); val version = text(); val taxonomy = text(); val retrieved = text()
            val nodes = LinkedHashMap<String, LifeNode>()
            repeat(count()) {
                val id = text(); val parent = text().ifEmpty { null }; val scientific = text(); val key = text()
                val species = text().also { require(it == "0" || it == "1") } == "1"
                require(nodes.put(id, LifeNode(id, parent, scientific, species, key, texts(), texts(), texts())) == null)
            }
            val notes = LinkedHashMap<String, SpeciesNote>()
            repeat(count()) { val id = text(); notes[id] = SpeciesNote(text(), text(), text(), texts()) }
            val labels = LinkedHashMap<String, SpeciesLabel>()
            repeat(count()) { val id = text(); text(); labels[id] = SpeciesLabel(text(), text(), texts()) }
            require(stream.read() == -1) { "Trailing atlas data" }
            LifeArchive(root, version, taxonomy, retrieved, nodes, notes, labels)
        }
    }
}
