package com.Atom2Universe.app.studio.data

/** Une touche garde sa piste jusqu'au relâchement, y compris lorsqu'une pédale la prolonge. */
internal class MidiPerformance(
    private val onPress: (Int, Int, Float) -> Unit,
    private val onRelease: (Int, Int) -> Unit,
) {
    private class Key(val track: Int, var lifted: Boolean = false)
    private val keys = HashMap<Int, Key>()
    private val sustain = BooleanArray(16)
    private var closed = false

    @Synchronized fun receive(bytes: ByteArray, track: Int) {
        if (closed || bytes.size < 3) return
        val channel = bytes[0].toInt() and 15
        if (channel == 9) return
        if (bytes[0].toInt() and 0xF0 == 0xB0) {
            when (bytes[1].toInt()) {
                64 -> {
                    sustain[channel] = bytes[2].toInt() >= 64
                    if (!sustain[channel]) releaseChannel(channel, liftedOnly = true)
                }
                120 -> releaseChannel(channel, liftedOnly = false)
                121 -> {
                    sustain[channel] = false
                    releaseChannel(channel, liftedOnly = true)
                }
                123 -> if (sustain[channel]) {
                    for ((id, key) in keys) if (id / 128 == channel) key.lifted = true
                } else releaseChannel(channel, liftedOnly = false)
            }
            return
        }
        MidiNotes.handle(bytes, { pitch, velocity ->
            val id = channel * 128 + pitch
            keys.remove(id)?.let { onRelease(it.track, pitch) }
            if (track >= 0) {
                keys[id] = Key(track)
                onPress(track, pitch, velocity)
            }
        }, { pitch ->
            val id = channel * 128 + pitch
            val key = keys[id]
            if (key != null) {
                if (sustain[channel]) key.lifted = true else {
                    keys.remove(id)
                    onRelease(key.track, pitch)
                }
            }
        })
    }

    private fun releaseChannel(channel: Int, liftedOnly: Boolean) {
        val iterator = keys.iterator()
        while (iterator.hasNext()) {
            val (id, key) = iterator.next()
            if (id / 128 == channel && (!liftedOnly || key.lifted)) {
                iterator.remove()
                onRelease(key.track, id % 128)
            }
        }
    }

    @Synchronized fun reset() {
        for ((id, key) in keys) onRelease(key.track, id % 128)
        keys.clear()
        sustain.fill(false)
    }

    @Synchronized fun close() {
        closed = true
        reset()
    }
}
