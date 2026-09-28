package com.Atom2Universe.app.games.caves

import android.content.Context
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.entity.Harassment
import com.Atom2Universe.app.games.caves.render.HeldKind
import com.Atom2Universe.app.games.caves.render.HeldItemPoses
import com.Atom2Universe.app.games.caves.node.ExpeditionItems as E
import com.Atom2Universe.app.games.caves.node.ForgedEquipment as G
import com.Atom2Universe.app.games.caves.node.GameEvent
import com.Atom2Universe.app.games.caves.node.MineralItems as M
import com.Atom2Universe.app.games.caves.world.MineralProgression as P
import org.json.JSONObject
import kotlin.math.*

/** Exploration combat state. Input, inventory and damage are mutated on the GL thread. */
internal class ExpeditionCombat(private val r: CaveRenderer, private val context: Context, json: String) {
    var armor: Short? = null
        private set
    private val pieces = java.util.concurrent.ConcurrentHashMap<G.Slot, Short>()
    fun equipped(slot: G.Slot) = pieces[slot]
    val armorColor: Int? get() = (pieces[G.Slot.CHEST] ?: pieces.values.firstOrNull())?.let {
        G.template(it)?.let { t -> M.metals[P.metal(t.stage)].color }
    } ?: M.variant(armor)?.metal?.color
    var shield = false
        private set
    var guard = 0f
        private set
    var charge = 0f
        private set
    private var recovery = 0f
    private var guardRecovery = 0f
    private var pending: E.Melee? = null
    private var pendingId: Short? = null
    private var impactIn = 0f
    private var heavy = 0f
    private var previous: Short? = null
    // Coup demandé pendant la récupération du précédent : il part dès que le bras est libre,
    // au lieu d'être avalé sans rien dire (c'est ce qui donnait « l'épée ne marche pas »).
    private var queued = 0f
    private var healthFraction: Double? = null
    private var observedHp: Int? = null
    private var observedMaxHp: Int? = null
    init {
        val root=runCatching { JSONObject(json) }.getOrElse { JSONObject() }
        armor=root.optInt("armor").toShort().takeIf { E.armor(it)>0f }
        root.optJSONObject("pieces")?.let { saved -> G.Slot.entries.forEach { slot ->
            val id=saved.optInt(slot.name).toShort()
            if(G.get(id)!=null && G.template(id)?.slot==slot) pieces[slot]=id
        } }
        if(pieces.isNotEmpty()) armor=null
        shield=root.optBoolean("shield")
        healthFraction=root.optDouble("healthFraction",Double.NaN).takeIf { it.isFinite() }?.coerceIn(0.0,1.0)
        recovery=root.optDouble("recovery",0.0).toFloat().takeIf { it.isFinite() }?.coerceIn(0f,2f) ?: 0f
        guardRecovery=root.optDouble("guardRecovery",0.0).toFloat().takeIf { it.isFinite() }?.coerceIn(0f,1.1f) ?: 0f
    }
    fun snapshot()=JSONObject().put("armor",armor?.toInt() ?: 0).put("shield",shield)
        .put("pieces",JSONObject().also { saved -> pieces.forEach { (slot,id) -> saved.put(slot.name,id.toInt()) } })
        .put("recovery",recovery.toDouble()).put("guardRecovery",guardRecovery.toDouble())
        .put("healthFraction",currentHealthFraction()).toString()

    fun tick(dt: Float, held: Short?) {
        if(r.mode.allowsWorldEdits && r.mode.allowsCombat) syncArmorHealth()
        guard=(guard-dt).coerceAtLeast(0f); guardRecovery=(guardRecovery-dt).coerceAtLeast(0f)
        recovery=(recovery-dt).coerceAtLeast(0f)
        if(held!=previous || r.heldItemMode!=HotbarMode.COMBAT || r.playerNode.hp<=0) {
            charge=0f;pending=null;guard=0f;previous=held;queued=0f
        }
        if(pending!=null) {
            impactIn-=dt
            if(impactIn<=0f) { val attack=pending!!;pending=null; if(held==pendingId) strike(attack,heavy) }
        }
        if(queued>0f) {
            queued=(queued-dt).coerceAtLeast(0f)
            val profile=if(held!=null) E.melee(held) else null
            if(held!=null && profile!=null && guard==0f && recovery==0f && pending==null && charge==0f) { queued=0f; attack(profile,held,0f) }
        }
    }
    fun input(id: Short, down: Boolean) {
        val profile=E.melee(id) ?: return
        if(guard>0f) { charge=0f;return }
        if(recovery>0f || pending!=null) {
            if(!down) { if(charge>0f) queued=.45f; charge=0f }
            return
        }
        if(down) return // Charge advances explicitly in the renderer with simulation time.
        if(charge<=0f) return
        val strength=((charge-.25f)/.65f).coerceIn(0f,1f)
        charge=0f
        attack(profile,id,strength)
    }
    private fun attack(profile: E.Melee, id: Short, strength: Float) {
        val speed=1f+bonus(G.Bonus.ATTACK)/100f
        recovery=profile.recovery*(1f+strength*.45f)/speed
        val kind=HeldKind.forWeaponType(profile.type) ?: HeldKind.SWORD
        pending=profile;pendingId=id;heavy=strength
        impactIn=HeldItemPoses.attackDuration(kind)*HeldItemPoses.attackImpact(kind)/speed
        r.meleeVisual(strength,speed)
    }
    fun hold(dt: Float) { charge=if(recovery==0f && pending==null && guard==0f) (charge+dt).coerceAtMost(.9f) else .001f }
    fun raiseGuard() {
        if(guardRecovery>0f || pending!=null || r.playerNode.hp<=0) return
        guard=.7f;guardRecovery=1.05f;charge=0f
    }
    /** Ennemis vivants dans le cône devant le regard, à portée et sans mur entre deux, du plus proche au plus loin. */
    fun targetsInArc(reach: Double, arc: Double, count: Int): List<Enemy> {
        val c=r.camera
        return (r.enemyManager.enemies.asSequence()+r.huntableAnimals.asSequence()).filter { it.hp>0 }.mapNotNull { e ->
            val passive=e.def.behavior=="passive"
            val height=if(passive) r.passiveAnimals.height(e) else e.def.eyeHeight.toDouble()
            val radius=e.def.radius * if(passive && e.young) .72 else 1.0
            val dx=e.x-c.playerX; val dy=(c.eyeY).coerceIn(e.y+.15,e.y+height)-c.eyeY; val dz=e.z-c.playerZ
            val distance=sqrt(dx*dx+dy*dy+dz*dz)
            val dot=(dx*c.aimX+dy*c.aimY+dz*c.aimZ)/distance.coerceAtLeast(.001)
            if(distance-radius>reach || dot<arc || !r.clearCombatLine(c.playerX,c.eyeY,c.playerZ,e.x,c.eyeY+dy,e.z)) null else e to distance
        }.sortedBy { it.second }.take(count).map { it.first }.toList()
    }
    /** Coup d'outil (pioche, hache, houe…) : une seule cible, dégâts modestes, petit recul. */
    fun toolStrike(damage: Int) {
        val e=targetsInArc(TOOL_REACH,TOOL_ARC,1).firstOrNull() ?: return
        r.meleeContact(e,false)
        if(e.def.behavior=="passive") { r.damageAnimal(e,damage);return }
        Harassment.melee(e)
        r.enemyManager.damageEnemy(e,damage)
        r.enemyManager.knockbackFromPlayer(e,2.0)
    }
    private fun strike(p: E.Melee, strength: Float) {
        val targets=targetsInArc(p.reach,p.arc,p.targets)
        for(e in targets) {
            val critical=kotlin.random.Random.nextInt(100)<bonus(G.Bonus.CRIT)
            val multiplier=if(critical) 2f+bonus(G.Bonus.CRIT_DAMAGE)/100f else 1f
            val damage=(p.damage*(1f+strength*.85f)*multiplier).roundToInt()
            r.meleeContact(e,strength>.65f || p.type=="hammer" || critical)
            if(e.def.behavior=="passive") { r.damageAnimal(e,damage);continue }
            Harassment.melee(e)
            r.enemyManager.damageEnemy(e,damage)
            if(critical) message(R.string.cave_gear_critical)
            r.enemyManager.knockbackFromPlayer(e,if(p.type=="hammer") 7.0+strength*2.0 else 4.0+strength*4.0)
            if(strength>.65f || p.type=="hammer") { e.staggerTimer=if(e.isBoss) .15f else .48f;e.attackWindup=0f }
        }
    }
    fun receive(damage: Int, dirX: Float, dirZ: Float, attacker: Enemy? = null) {
        if(r.playerNode.hp<=0) return
        if(kotlin.random.Random.nextInt(100)<bonus(G.Bonus.DODGE)) { message(R.string.cave_gear_dodged); return }
        var amount=damage.toFloat()
        val yaw=Math.toRadians(r.camera.yaw.toDouble())
        val frontal=-(sin(yaw)*dirX+cos(yaw)*dirZ)>.35
        if(guard>0f && frontal) {
            val parry=guard>.48f
            amount*=if(parry) 0f else if(shield) .18f else .5f
            if(parry) { message(R.string.cave_parried);attacker?.staggerTimer=.8f;attacker?.attackWindup=0f;attacker?.let { r.enemyManager.knockbackFromPlayer(it,5.0) };r.startSwing() }
        }
        val stage=attacker?.let { it.level-1 } ?: P.stage(r.camera.playerY)
        amount*=1f-reduction(stage)
        if(amount>0f) {
            val final=amount.roundToInt().coerceAtLeast(1)
            r.playerNode.applyDamage(final);r.eventBus.publish(GameEvent.PlayerHit(final,dirX,dirZ))
        }
    }
    fun equip(id: Short) {
        if((r.inventory[id] ?: 0)<=0 || id==armor || id==E.SHIELD && shield) return
        val slot=G.template(id)?.slot
        if(slot==null && E.armor(id)==0f && id!=E.SHIELD) return
        val old=if(id==E.SHIELD) emptyList() else if(slot!=null) listOfNotNull(armor,pieces[slot])
            else pieces.values.toList()+listOfNotNull(armor)
        if(old.any { (r.inventory[it] ?: 0)==Int.MAX_VALUE }) { message(R.string.cave_equipment_full);return }
        // Creative templates and old unrolled stock materialize once, then retain their rolls.
        val equippedId=if(slot!=null && G.get(id)==null) runCatching { G.allocate(G.roll(id)) }.getOrElse {
            message(R.string.cave_ui_craft_failed); return
        } else id
        val left=r.inventory.getValue(id)-1
        if(left==0) r.inventory.remove(id) else r.inventory[id]=left
        for(previous in old) r.inventory[previous]=(r.inventory[previous] ?: 0)+1
        if(id==E.SHIELD) shield=true else if(slot!=null) { armor=null;pieces[slot]=equippedId }
        else { pieces.clear();armor=id }
        syncArmorHealth()
        r.changedFrontierInventory();announce()
    }
    fun removeEquipment() {
        val items=pieces.values.toList()+listOfNotNull(armor, if(shield) E.SHIELD else null)
        if(items.any { (r.inventory[it] ?: 0)==Int.MAX_VALUE }) { message(R.string.cave_equipment_full);return }
        for(id in items) r.inventory[id]=(r.inventory[id] ?: 0)+1
        pieces.clear();armor=null;shield=false;syncArmorHealth();r.changedFrontierInventory();announce()
    }
    /** Remove one slot without disturbing the other armor pieces or the shield. */
    fun removeEquipmentSlot(slot: G.Slot? = null, removeShield: Boolean = false) {
        val id = if (removeShield) { if (shield) E.SHIELD else null }
            else if (slot != null) pieces[slot] else armor
        id ?: return
        if ((r.inventory[id] ?: 0) == Int.MAX_VALUE) { message(R.string.cave_equipment_full); return }
        r.inventory[id] = (r.inventory[id] ?: 0) + 1
        if (removeShield) shield = false else if (slot != null) pieces.remove(slot) else armor = null
        syncArmorHealth(); r.changedFrontierInventory(); announce()
    }
    fun bonus(bonus: G.Bonus, includeWeapon: Boolean = true): Int {
        var total=pieces.values.sumOf { G.get(it)?.bonuses?.get(bonus) ?: 0 }
        if(includeWeapon && r.heldItemMode==HotbarMode.COMBAT) {
            val held=r.hotbar.getOrNull(r.selectedSlot)
            total+=G.get(held)?.takeIf { it.damage>0 }?.bonuses?.get(bonus) ?: 0
        }
        return total.coerceIn(0,bonus.cap)
    }
    fun reduction(stage: Int): Float {
        if(pieces.isEmpty()) return M.armorReduction(armor,stage) ?: E.armor(armor)
        val rating=pieces.values.sumOf { G.get(it)?.defense ?: 0 }.toFloat()
        return (rating/(rating+50f*P.scale(stage))).coerceAtMost(.65f)
    }
    fun summary(): String = buildList {
        add(context.getString(R.string.cave_gear_totals,r.playerNode.maxHp,(reduction(P.stage(r.camera.playerY))*100).roundToInt()))
        if(armor!=null) add(M.name(context,armor!!) ?: context.getString(R.string.cave_gear_legacy))
        G.Slot.entries.forEach { slot ->
            add(pieces[slot]?.let { G.name(context,it)+"\n"+G.describe(context,it,false) }
                ?: context.getString(R.string.cave_gear_empty,context.getString(slot.title)))
        }
        for(b in G.Bonus.entries) add(context.getString(R.string.cave_gear_total_bonus,context.getString(b.label),bonus(b),b.cap))
    }.joinToString("\n\n")
    fun playerSummary(): String = buildList {
        add(context.getString(R.string.cave_player_health, r.playerNode.hp, r.playerNode.maxHp))
        add(context.getString(R.string.cave_player_defense, pieces.values.sumOf { G.get(it)?.defense ?: 0 }))
        add(context.getString(R.string.cave_player_reduction, (reduction(P.stage(r.camera.playerY)) * 100).roundToInt()))
        add(context.getString(R.string.cave_player_shield, context.getString(if (shield) R.string.cave_ui_ready else R.string.cave_ui_missing)))
        for (b in G.Bonus.entries) add(context.getString(R.string.cave_gear_total_bonus, context.getString(b.label), bonus(b), b.cap))
        add(context.getString(R.string.cave_player_critical_total, 200 + bonus(G.Bonus.CRIT_DAMAGE)))
    }.joinToString("\n\n")
    companion object {
        const val TOOL_REACH = 2.6
        const val TOOL_ARC = .80
    }
    private fun currentHealthFraction(): Double {
        val player=r.playerNode
        var fraction=healthFraction ?: (player.hp.toDouble()/player.maxHp.coerceAtLeast(1))
        if(observedHp!=null) fraction+=(player.hp-observedHp!!).toDouble()/(observedMaxHp ?: player.maxHp).coerceAtLeast(1)
        if(player.hp<=0) fraction=0.0
        healthFraction=fraction.coerceIn(0.0,1.0)
        observedHp=player.hp;observedMaxHp=player.maxHp
        return healthFraction!!
    }
    /** Fractional health survives swaps; rounding the displayed HP cannot heal by repeated swaps. */
    private fun syncArmorHealth() {
        val player=r.playerNode
        val fraction=currentHealthFraction()
        val maximum=if(pieces.isEmpty()) M.armorHp(armor) else 50+pieces.values.sumOf { G.get(it)?.hp ?: 0 }
        r.playerStats.maxHp=maximum
        if(player.maxHp==maximum) return
        player.hp=ceil(fraction*maximum).toInt().coerceIn(0,maximum)
        player.setMaxHp(maximum)
        observedHp=player.hp;observedMaxHp=maximum
    }
    fun healing(amount: Int) = (amount.toLong()*r.playerNode.maxHp/50).coerceIn(1,Int.MAX_VALUE.toLong()).toInt()
    private fun message(id: Int) { r.farmMessageCallback?.invoke(context.getString(id)) }
    private fun announce() { r.farmMessageCallback?.invoke(context.getString(R.string.cave_equipment_changed,
        (reduction(P.stage(r.camera.playerY))*100).roundToInt(),context.getString(if(shield) R.string.cave_ui_ready else R.string.cave_ui_missing))) }
}
