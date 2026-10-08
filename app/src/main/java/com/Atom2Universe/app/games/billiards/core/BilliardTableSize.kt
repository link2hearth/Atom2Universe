package com.Atom2Universe.app.games.billiards.core

/** Cushion-nose dimensions in metres. Commercial names are deliberately separate. */
enum class BilliardTableSize(val family: TableFamily, val length: Double, val width: Double, val feet: Int = 0) {
    // FFB 2026-27, art. 1.1.01: nominal 2.60 / 2.80 / 3.10 m tables.
    // https://www.ffbillard.com/ext/telechargement.php?id=34494
    CAROM_210(TableFamily.CAROM,2.10,1.05),
    CAROM_230(TableFamily.CAROM,2.30,1.15),
    CAROM_252(TableFamily.CAROM,2.52,1.26),
    CAROM_284(TableFamily.CAROM,2.84,1.42),
    // Diamond 7 ft (80 x 40 in), domestic 8 ft (88 x 44), WPA oversize 8 / 9 ft.
    // https://diamondbilliards.com/pages/pro-am
    // https://www.brunswickbilliards.com/products/ann-arbor-pool-table
    // https://wpapool.com/wp-content/uploads/2024/01/RECOMMENDED-EQUIPMENT-SPECIFICATIONS.pdf
    POOL_7(TableFamily.POOL,2.032,1.016,7),
    POOL_8(TableFamily.POOL,2.2352,1.1176,8),
    POOL_8_PRO(TableFamily.POOL,2.3368,1.1684,8),
    POOL_9(TableFamily.POOL,2.54,1.27,9),
    // Small UK tables are not necessarily exactly 2:1. Keep the legacy 7 ft dimensions.
    // https://www.pooltablesonline.co.uk/supreme-winner-pool-table/
    BLACKBALL_6(TableFamily.BLACKBALL,1.60,.826,6),
    BLACKBALL_7(TableFamily.BLACKBALL,1.83,.915,7),
    // Smaller home formats use adapted markings; only the 12 ft is the WPBSA reference.
    // https://www.vismara.tw/data/goods/1723800473UVL8X.pdf
    // https://wpbsa.com/wp-content/uploads/2198_WPBSA-Rulebook-2024-25.pdf
    SNOOKER_9(TableFamily.SNOOKER,2.54,1.27,9),
    SNOOKER_10(TableFamily.SNOOKER,2.84,1.42,10),
    SNOOKER_12(TableFamily.SNOOKER,3.569,1.778,12),
    // Russian federation equipment / training specifications, appendix 1.
    // https://www.fbsrf.ru/sites/default/files/tehnicheskie_normativy_fbsr_1.pdf
    PYRAMID_8(TableFamily.PYRAMID,2.24,1.12,8),
    PYRAMID_9(TableFamily.PYRAMID,2.54,1.27,9),
    PYRAMID_10(TableFamily.PYRAMID,2.95,1.47,10),
    PYRAMID_12(TableFamily.PYRAMID,3.55,1.775,12),
    // Compact heyball is an explicit leisure adaptation, not a tournament specification.
    // https://joybilliards.co.za/q5-table/
    HEYBALL_8(TableFamily.HEYBALL,2.2352,1.1176,8),
    HEYBALL_9(TableFamily.HEYBALL,2.54,1.27,9);

    val isReference get() = this==defaultFor(family)
    val adaptedMarkings get() = !isReference && family in listOf(TableFamily.SNOOKER,TableFamily.HEYBALL)

    companion object {
        fun defaultFor(family: TableFamily): BilliardTableSize = when(family) {
            TableFamily.CAROM -> CAROM_284
            TableFamily.POOL -> POOL_9
            TableFamily.BLACKBALL -> BLACKBALL_7
            TableFamily.SNOOKER -> SNOOKER_12
            TableFamily.PYRAMID -> PYRAMID_12
            TableFamily.HEYBALL -> HEYBALL_9
        }
        fun forFamily(family: TableFamily) = entries.filter { it.family==family }
        /** Missing, obsolete or cross-family preferences must never resize a saved match. */
        fun restore(name: String?,family: TableFamily) = entries.firstOrNull { it.name==name && it.family==family } ?: defaultFor(family)
    }
}
