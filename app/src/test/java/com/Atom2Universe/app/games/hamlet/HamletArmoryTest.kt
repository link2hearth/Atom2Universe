package com.Atom2Universe.app.games.hamlet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HamletArmoryTest {
    private var time = 1_800_000_000_000L
    private fun game(sandbox: Boolean = false) = HamletGame({ time }, Random(9)).apply { startNew(sandbox) }.withStarters()
    private fun funded(): HamletGame = game().apply {
        coins = 1_000_000; xp = 1_000_000
        val origin = realmOrigin()
        val spot = (origin until origin + realmSize()).flatMap { y -> (origin until origin + realmSize()).map { x -> x to y } }
            .first { (x, y) -> canPlace("adventureForge", x, y, false) }
        val forge = buy("adventureForge", spot.first, spot.second, false)!!
        time += forge.def.build * 1000L + 1
        adventure.advanceProduction()
        AdventureResource.entries.forEach { adventure.add(it, 1_000_000) }
    }

    @Test fun `forge et progression ouvrent les recettes sans additionner les forges`() {
        val g = game(); val empty = g.adventure.bag.clone()
        assertNull(g.armory.craft(0, GearSet.HUNT, EquipmentRarity.RARE)); assertArrayEquals(empty, g.adventure.bag)
        val village = funded(); val forge = village.objs.values.first { it.t == "adventureForge" }
        village.adventure.frontier = 100
        village.objs[9999] = Obj(9999, "adventureForge", 0, 0)
        assertEquals(1, village.armory.forgeRank())
        assertNull(village.armory.craft(0, GearSet.HUNT, EquipmentRarity.LEGENDARY))
        forge.tier = 2
        assertNotNull(village.armory.craft(0, GearSet.HUNT, EquipmentRarity.LEGENDARY))
        forge.tier = 3; village.adventure.frontier = 44
        assertNull(village.armory.craft(0, GearSet.HUNT, EquipmentRarity.MYTHIC))
        village.adventure.frontier = 45
        assertNotNull(village.armory.craft(0, GearSet.HUNT, EquipmentRarity.MYTHIC))
    }

    @Test fun `fabrication paie exactement les ressources et aucun echec ne depense`() {
        val g = funded(); val a = g.armory
        val cost = a.craftCost(1, GearSet.HEARTHWARD, EquipmentRarity.RARE)
        val before = g.adventure.bag.clone()
        val piece = a.craft(1, GearSet.HEARTHWARD, EquipmentRarity.RARE)!!
        assertEquals(1, piece.slot); assertEquals(1, piece.level)
        for ((resource, spent) in cost.resources()) assertEquals(before[resource.ordinal] - spent, g.adventure[resource])
        assertNull(a.craft(-1, GearSet.HUNT, EquipmentRarity.RARE))
        g.adventure.bag[AdventureResource.ORE.ordinal] = 0
        val failed = g.adventure.bag.clone()
        assertNull(a.craft(0, GearSet.HUNT, EquipmentRarity.RARE))
        assertArrayEquals(failed, g.adventure.bag); assertEquals(1, a.inventory.size)
    }

    @Test fun `transfert et remplacement gardent chaque piece une seule fois`() {
        val g = game(true); val a = g.armory
        val first = a.craft(0, GearSet.HUNT, EquipmentRarity.RARE)!!
        val second = a.craft(0, GearSet.GALE, EquipmentRarity.EPIC)!!
        assertTrue(a.equip(0, first.id)); assertTrue(a.equip(7, second.id))
        assertTrue(a.equip(7, first.id)); assertNull(a.equipped(0, 0))
        assertEquals(first.id, a.equipped(7, 0)!!.id); assertNull(a.owner(second.id))
        assertEquals(2, a.inventory.size)
        assertTrue(a.unequip(7, 0)); assertNull(a.owner(first.id)); assertEquals(2, a.inventory.size)
        assertFalse(a.equip(-1, first.id)); assertFalse(a.equip(0, 9999))
    }

    @Test fun `deux paires et ensemble complet donnent des choix tactiques distincts`() {
        val g = game(true); val a = g.armory
        for (slot in 0..3) assertTrue(a.equip(0, a.craft(slot, if (slot < 2) GearSet.HEARTHWARD else GearSet.HUNT, EquipmentRarity.RARE)!!.id))
        val mixed = a.setBonus(0)
        assertEquals(0.08, mixed.hpPercent, 0.000001); assertEquals(0.04, mixed.crit, 0.000001)
        assertEquals(0.0, mixed.attackPercent, 0.0)
        for (slot in 0..1) assertTrue(a.equip(0, a.craft(slot, GearSet.HUNT, EquipmentRarity.EPIC)!!.id))
        val hunt = a.setBonus(0)
        assertEquals(0.06, hunt.crit, 0.000001); assertEquals(0.1, hunt.attackPercent, 0.000001)
        assertEquals(0.0, hunt.hpPercent, 0.0)
        assertEquals(mapOf(GearSet.HUNT to 4), a.setCounts(0))
    }

    @Test fun `tous les ensembles ajoutent leur specialite propre en combat`() {
        val g = game(true); val a = g.armory
        val specialties = ArrayList<GearBonuses>()
        for (set in GearSet.entries) {
            for (slot in 0..3) assertTrue(a.equip(0, a.craft(slot, set, EquipmentRarity.RARE)!!.id))
            val b = a.bonuses(0); specialties.add(b)
            when (set) {
                GearSet.HEARTHWARD -> assertTrue(b.openingShield > 0 && b.armor > 0)
                GearSet.HUNT -> assertTrue(b.crit > 0)
                GearSet.GALE -> assertTrue(b.haste > 0)
                GearSet.GROVE -> assertTrue(b.healingBonus > 0)
                GearSet.BLOOD -> assertTrue(b.lifesteal > 0)
                GearSet.STORM -> assertTrue(b.armorPierce > 0 && b.skillBoost > 0)
            }
        }
        assertEquals(6, specialties.toSet().size)
    }

    @Test fun `amelioration suit la piece et ne depasse pas le niveau dix`() {
        val g = funded(); val a = g.armory
        val p = a.craft(0, GearSet.HUNT, EquipmentRarity.RARE)!!
        assertTrue(a.equip(0, p.id))
        val attack = a.bonuses(0).attackPercent
        repeat(9) { assertTrue(a.enhance(p.id)) }
        assertEquals(10, a.equipped(0, 0)!!.level); assertTrue(a.bonuses(0).attackPercent > attack)
        assertTrue(a.equip(7, p.id)); assertEquals(10, a.equipped(7, 0)!!.level)
        val wallet = g.adventure.bag.clone()
        assertFalse(a.enhance(p.id)); assertArrayEquals(wallet, g.adventure.bag)
    }

    @Test fun `demontage consomme la piece et rembourse moins que les depenses`() {
        val g = funded(); val a = g.armory
        val before = g.adventure.bag.clone()
        val p = a.craft(1, GearSet.HEARTHWARD, EquipmentRarity.RARE)!!
        assertTrue(a.enhance(p.id)); assertTrue(a.enhance(p.id))
        val spent = before[AdventureResource.ORE.ordinal] - g.adventure[AdventureResource.ORE]
        val refund = a.salvageReturn(p.id)!!
        assertTrue(refund.ore in 1 until spent)
        assertTrue(a.equip(0, p.id)); assertFalse(a.salvage(p.id))
        assertTrue(a.unequip(0, 1)); val ore = g.adventure[AdventureResource.ORE]
        assertTrue(a.salvage(p.id)); assertEquals(ore + refund.ore, g.adventure[AdventureResource.ORE])
        assertTrue(a.inventory.isEmpty()); val wallet = g.adventure.bag.clone()
        assertFalse(a.salvage(p.id)); assertArrayEquals(wallet, g.adventure.bag)
        assertTrue(a.craft(1, GearSet.HUNT, EquipmentRarity.RARE)!!.id > p.id)
    }

    @Test fun `equipement gelee pendant combat et ne touche pas la formation`() {
        val g = game(true); val a = g.armory
        val p = a.craft(0, GearSet.BLOOD, EquipmentRarity.EPIC)!!
        assertTrue(a.equip(0, p.id)); val formation = g.adventure.teams[0].clone()
        assertNotNull(g.adventure.beginBattle(BattleMode.CAMPAIGN, 0, 3))
        assertFalse(a.equip(7, p.id)); assertFalse(a.unequip(0, 0)); assertFalse(a.enhance(p.id)); assertFalse(a.salvage(p.id))
        assertArrayEquals(formation, g.adventure.teams[0]); assertEquals(0, a.owner(p.id))
        g.adventure.abandonBattle(); assertTrue(a.equip(7, p.id))
    }

    @Test fun `sauvegarde garde pieces identites affectations et anciennes ameliorations`() {
        val g = game(true); val a = g.armory
        val p = a.craft(3, GearSet.GALE, EquipmentRarity.MYTHIC)!!
        assertTrue(a.enhance(p.id)); assertTrue(a.equip(11, p.id))
        val restored = game()
        assertTrue(HamletSave.fromJson(restored, HamletSave.toJson(g)))
        assertEquals(a.inventory, restored.armory.inventory)
        assertEquals(p.id, restored.armory.equipped(11, 3)!!.id)
        assertTrue(restored.armory.craft(0, GearSet.HUNT, EquipmentRarity.RARE)!!.id > p.id)
    }

    @Test fun `sauvegarde malformee ne duplique pas les pieces et borne les valeurs`() {
        val g = game(); val a = g.armory
        val piece = JSONObject().put("id", 5).put("slot", 0).put("set", "HUNT").put("rarity", "MYTHIC").put("level", Int.MAX_VALUE)
        val equipped = JSONArray().put(JSONObject().put("hero", 0).put("slot", 0).put("piece", 5))
            .put(JSONObject().put("hero", 7).put("slot", 0).put("piece", 5))
            .put(JSONObject().put("hero", 0).put("slot", 1).put("piece", 5))
        a.decode(JSONObject().put("pieces", JSONArray().put(piece).put(piece)).put("equipped", equipped).put("nextId", -1))
        assertEquals(1, a.inventory.size); assertEquals(10, a.inventory.single().level)
        assertEquals(5, a.equipped(0, 0)!!.id); assertNull(a.equipped(7, 0)); assertNull(a.equipped(0, 1))
        val sanitized = GearBonuses(hpPercent = Double.NaN, attackPercent = Double.POSITIVE_INFINITY,
            lifesteal = 99.0, armorPierce = -5.0, haste = 99.0).bounded()
        assertEquals(0.0, sanitized.hpPercent, 0.0); assertEquals(0.0, sanitized.attackPercent, 0.0)
        assertEquals(0.5, sanitized.lifesteal, 0.0); assertEquals(0.0, sanitized.armorPierce, 0.0); assertEquals(0.3, sanitized.haste, 0.0)
        a.decode(null); assertTrue(a.inventory.isEmpty()); assertNull(a.equipped(0, 0))
    }

    @Test fun `reforge conserve niveau ensemble identite et proprietaire`() {
        val g = funded(); val a = g.armory
        val p = a.craft(2, GearSet.GROVE, EquipmentRarity.RARE)!!
        repeat(4) { assertTrue(a.enhance(p.id)) }
        assertTrue(a.equip(3, p.id)); val before = a.bonuses(3).healingBonus
        assertFalse(a.canUpgradeQuality(p.id)); g.adventure.frontier = 10
        assertTrue(a.canUpgradeQuality(p.id)); assertTrue(a.upgradeQuality(p.id))
        val improved = a.equipped(3, 2)!!
        assertEquals(p.id, improved.id); assertEquals(5, improved.level); assertEquals(GearSet.GROVE, improved.set)
        assertEquals(EquipmentRarity.EPIC, improved.rarity); assertEquals(EquipmentRarity.RARE, improved.craftRarity)
        assertEquals(1, a.inventory.size); assertEquals(3, a.owner(p.id))
        assertTrue(a.bonuses(3).healingBonus > before)
        val restored = game()
        assertTrue(HamletSave.fromJson(restored, HamletSave.toJson(g)))
        assertEquals(improved, restored.armory.equipped(3, 2))
        assertEquals(a.salvageReturn(p.id), restored.armory.salvageReturn(p.id))
    }

    @Test fun `reforge facture les niveaux conserves et remboursement suit exactement investissement`() {
        val g = funded(); val a = g.armory
        g.objs.values.first { it.t == "adventureForge" }.tier = 3
        g.adventure.frontier = 45
        val before = g.adventure.bag.clone()
        val p = a.craft(1, GearSet.HEARTHWARD, EquipmentRarity.RARE)!!
        repeat(3) { assertTrue(a.enhance(p.id)) }
        repeat(3) { assertTrue(a.upgradeQuality(p.id)) }
        repeat(2) { assertTrue(a.enhance(p.id)) }
        val spent = AdventureResource.entries.associateWith { before[it.ordinal] - g.adventure[it] }
        val refund = a.salvageReturn(p.id)!!
        for ((resource, recovered) in refund.resources()) {
            assertEquals(spent.getValue(resource) / 2, recovered)
            if (spent.getValue(resource) > 0) assertTrue(recovered < spent.getValue(resource))
        }
        val directBefore = g.adventure.bag.clone()
        val direct = a.craft(1, GearSet.HEARTHWARD, EquipmentRarity.MYTHIC)!!
        repeat(5) { assertTrue(a.enhance(direct.id)) }
        for (resource in listOf(AdventureResource.ORE, AdventureResource.RUNES, AdventureResource.CRYSTAL, AdventureResource.STONE)) {
            val directSpent = directBefore[resource.ordinal] - g.adventure[resource]
            assertTrue("upgrading cheap gear must not bypass the final recipe cost", spent.getValue(resource) >= directSpent)
        }
        assertTrue(spent.getValue(AdventureResource.ORE) > directBefore[AdventureResource.ORE.ordinal] - g.adventure[AdventureResource.ORE])
        assertNull(a.qualityCost(p.id)); assertFalse(a.upgradeQuality(p.id))
        assertTrue(a.salvage(p.id)); val after = g.adventure.bag.clone()
        assertFalse(a.salvage(p.id)); assertArrayEquals(after, g.adventure.bag)
    }

    @Test fun `reforge respecte rang campagne ressources et combat sans depense en echec`() {
        val g = funded(); val a = g.armory
        val p = a.craft(0, GearSet.STORM, EquipmentRarity.RARE)!!
        val initial = g.adventure.bag.clone()
        assertFalse(a.upgradeQuality(p.id)); assertArrayEquals(initial, g.adventure.bag)
        g.adventure.frontier = 10; assertTrue(a.upgradeQuality(p.id))
        g.adventure.frontier = 20
        val epic = g.adventure.bag.clone()
        assertFalse(a.upgradeQuality(p.id)); assertArrayEquals(epic, g.adventure.bag)
        g.objs.values.first { it.t == "adventureForge" }.tier = 2
        g.adventure.bag[AdventureResource.RUNES.ordinal] = 0
        val empty = g.adventure.bag.clone()
        assertFalse(a.upgradeQuality(p.id)); assertArrayEquals(empty, g.adventure.bag)
        g.adventure.add(AdventureResource.RUNES, 100)
        assertNotNull(g.adventure.beginBattle(BattleMode.CAMPAIGN, 0, 3))
        val combat = g.adventure.bag.clone()
        assertFalse(a.canUpgradeQuality(p.id)); assertFalse(a.upgradeQuality(p.id)); assertArrayEquals(combat, g.adventure.bag)
        g.adventure.abandonBattle(); assertTrue(a.upgradeQuality(p.id))
        assertEquals(EquipmentRarity.LEGENDARY, a.inventory.single().rarity)
    }
}
