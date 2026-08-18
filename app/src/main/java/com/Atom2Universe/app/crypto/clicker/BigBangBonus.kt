package com.Atom2Universe.app.crypto.clicker

// Bonus permanents du Big Bang. Désormais uniquement des réductions de prix :
// chaque niveau rabote la pente du prix du shop (mécanique dans ClickerShopEngine),
// l'une pour le Doigt Créateur, l'autre pour le Cœur d'Étoile.
enum class BigBangBonus(val id: String, val tokenCost: Int) {
    GOD_FINGER_DISCOUNT("god_finger_discount", 50),
    STAR_CORE_DISCOUNT("star_core_discount", 50);
}
