package com.Atom2Universe.app.games.memory

enum class CardState { FACE_DOWN, FACE_UP, MATCHED }

data class MemoryCard(
    val id: Int,
    val imageIndex: Int,
    var state: CardState = CardState.FACE_DOWN
)
