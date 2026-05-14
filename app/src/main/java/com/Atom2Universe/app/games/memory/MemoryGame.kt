package com.Atom2Universe.app.games.memory

import org.json.JSONArray
import org.json.JSONObject

class MemoryGame {
    var difficulty: MemoryDifficulty = MemoryDifficulty.EASY
    var imageFolder: String = ""
    var imageFiles: List<String> = emptyList()
    var cards: List<MemoryCard> = emptyList()
    var flips: Int = 0
    var matchedPairs: Int = 0
    var elapsedSeconds: Long = 0L

    val isWon get() = matchedPairs == difficulty.pairCount

    fun newGame(diff: MemoryDifficulty, folder: String, files: List<String>) {
        difficulty = diff
        imageFolder = folder
        imageFiles = files.toList()
        flips = 0
        matchedPairs = 0
        elapsedSeconds = 0L
        val needed = diff.pairCount
        val indices = files.indices.toMutableList().shuffled().take(needed)
        cards = (indices + indices)
            .mapIndexed { i, imgIdx -> MemoryCard(id = i, imageIndex = imgIdx) }
            .shuffled()
    }

    fun serialize(): String {
        val obj = JSONObject()
        obj.put("difficulty", difficulty.name)
        obj.put("imageFolder", imageFolder)
        val filesArr = JSONArray().also { arr -> imageFiles.forEach { arr.put(it) } }
        obj.put("imageFiles", filesArr)
        val cardsArr = JSONArray().also { arr ->
            cards.forEach { card ->
                arr.put(JSONObject().apply {
                    put("id", card.id)
                    put("imgIdx", card.imageIndex)
                    put("state", card.state.name)
                })
            }
        }
        obj.put("cards", cardsArr)
        obj.put("flips", flips)
        obj.put("matched", matchedPairs)
        obj.put("elapsed", elapsedSeconds)
        return obj.toString()
    }

    fun deserialize(json: String): Boolean = runCatching {
        val obj = JSONObject(json)
        difficulty = MemoryDifficulty.valueOf(obj.getString("difficulty"))
        imageFolder = obj.getString("imageFolder")
        val filesArr = obj.getJSONArray("imageFiles")
        imageFiles = (0 until filesArr.length()).map { filesArr.getString(it) }
        val cardsArr = obj.getJSONArray("cards")
        cards = (0 until cardsArr.length()).map { i ->
            val c = cardsArr.getJSONObject(i)
            val state = CardState.valueOf(c.getString("state"))
                .let { if (it == CardState.FACE_UP) CardState.FACE_DOWN else it }
            MemoryCard(c.getInt("id"), c.getInt("imgIdx"), state)
        }
        flips = obj.getInt("flips")
        matchedPairs = obj.getInt("matched")
        elapsedSeconds = obj.getLong("elapsed")
        true
    }.getOrDefault(false)
}
