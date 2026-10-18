package com.Atom2Universe.app.games.billiards.render

import com.Atom2Universe.app.games.billiards.core.Discipline
import com.Atom2Universe.app.games.billiards.core.TableFamily

/** Shared by the 3D balls and the player cards so their colours always agree. */
object BilliardBallStyle {
    private val pool=intArrayOf(0xF2EFDD,0xE2B72E,0x2B579F,0xBA302D,0x6A418C,0xDF732D,0x287C58,0x80352E,0x15181D)
    fun numbered(discipline: Discipline)=discipline.family==TableFamily.POOL || discipline.family==TableFamily.HEYBALL
    fun color(discipline: Discipline,id: Int): Int {
        if(discipline==Discipline.ENGLISH || discipline.family==TableFamily.CAROM) return when(id) { 0->0xF3EDD9; 1->0xE9BE42; else->0xB92620 }
        return when(discipline.family) {
            TableFamily.PYRAMID -> if(id==0) 0xD9AD46 else 0xECE9DB
            TableFamily.SNOOKER -> when(id) { 0->0xF4EFD9; 16->0xF2CB2E; 17->0x218956; 18->0x865239; 19->0x295FAB; 20->0xE894A7; 21->0x17181D; else->0xBC2625 }
            TableFamily.BLACKBALL -> when(id) { 0->0xF4EEDB; 8->0x16171C; in 1..7->0xC32E2E; else->0xEEC632 }
            else -> pool[(if(id>8) id-8 else id).coerceIn(0,8)]
        }
    }
}
