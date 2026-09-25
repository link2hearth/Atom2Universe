package com.Atom2Universe.app.games.caves

import android.content.Context
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.node.ExpeditionItems as E
import com.Atom2Universe.app.games.caves.node.GameEvent
import org.json.JSONObject
import kotlin.math.*

/** Exploration combat state. Input, inventory and damage are mutated on the GL thread. */
internal class ExpeditionCombat(private val r: CaveRenderer, private val context: Context, json: String) {
    var armor: Short? = null
        private set
    var shield = false
        private set
    var stamina = 100f
        private set
    var guard = 0f
        private set
    var charge = 0f
        private set
    private var rest = 0f
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
    init {
        val root=runCatching { JSONObject(json) }.getOrElse { JSONObject() }
        armor=root.optInt("armor").toShort().takeIf { E.armor(it)>0f }
        shield=root.optBoolean("shield")
        stamina=root.optDouble("stamina",100.0).toFloat().takeIf { it.isFinite() }?.coerceIn(0f,100f) ?: 100f
        recovery=root.optDouble("recovery",0.0).toFloat().takeIf { it.isFinite() }?.coerceIn(0f,2f) ?: 0f
        rest=root.optDouble("rest",0.0).toFloat().takeIf { it.isFinite() }?.coerceIn(0f,1f) ?: 0f
        guardRecovery=root.optDouble("guardRecovery",0.0).toFloat().takeIf { it.isFinite() }?.coerceIn(0f,1.1f) ?: 0f
    }
    fun snapshot()=JSONObject().put("armor",armor?.toInt() ?: 0).put("shield",shield)
        .put("stamina",stamina.toDouble()).put("recovery",recovery.toDouble())
        .put("rest",rest.toDouble()).put("guardRecovery",guardRecovery.toDouble()).toString()

    fun tick(dt: Float, held: Short?) {
        guard=(guard-dt).coerceAtLeast(0f); guardRecovery=(guardRecovery-dt).coerceAtLeast(0f)
        recovery=(recovery-dt).coerceAtLeast(0f); rest=(rest-dt).coerceAtLeast(0f)
        if(rest==0f && guard==0f && charge==0f) stamina=(stamina+dt*24f).coerceAtMost(100f)
        if(held!=previous || r.heldItemMode!=HotbarMode.COMBAT || r.playerNode.hp<=0) {
            charge=0f;pending=null;guard=0f;previous=held;queued=0f
        }
        if(pending!=null) {
            impactIn-=dt
            if(impactIn<=0f) { val attack=pending!!;pending=null; if(held==pendingId) strike(attack,heavy) }
        }
        if(queued>0f) {
            queued=(queued-dt).coerceAtLeast(0f)
            val profile=if(held!=null) E.melee[held] else null
            if(held!=null && profile!=null && guard==0f && recovery==0f && pending==null && charge==0f) { queued=0f; attack(profile,held,0f) }
        }
    }
    fun input(id: Short, down: Boolean) {
        val profile=E.melee[id] ?: return
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
        val cost=profile.stamina*(1f+strength*.75f)
        if(stamina<cost) { rest=.35f; r.farmMessageCallback?.invoke(context.getString(R.string.cave_melee_tired));return }
        stamina-=cost;rest=.65f;recovery=profile.recovery*(1f+strength*.45f)
        pending=profile;pendingId=id;heavy=strength;impactIn=if(profile.type=="hammer") .22f else .09f
        r.meleeVisual(strength)
    }
    fun hold(dt: Float) { charge=if(recovery==0f && pending==null && guard==0f) (charge+dt).coerceAtMost(.9f) else .001f }
    fun raiseGuard() {
        if(guardRecovery>0f || stamina<10f || pending!=null || r.playerNode.hp<=0) return
        stamina-=8f;guard=.7f;guardRecovery=1.05f;charge=0f;rest=.8f
    }
    /** Ennemis vivants dans le cône devant le regard, à portée et sans mur entre deux, du plus proche au plus loin. */
    fun targetsInArc(reach: Double, arc: Double, count: Int): List<Enemy> {
        val c=r.camera
        return r.enemyManager.enemies.asSequence().filter { it.hp>0 }.mapNotNull { e ->
            val dx=e.x-c.playerX; val dy=(c.eyeY).coerceIn(e.y+.15,e.y+e.def.eyeHeight.toDouble())-c.eyeY; val dz=e.z-c.playerZ
            val distance=sqrt(dx*dx+dy*dy+dz*dz)
            val dot=(dx*c.aimX+dy*c.aimY+dz*c.aimZ)/distance.coerceAtLeast(.001)
            if(distance-e.def.radius>reach || dot<arc || !r.clearCombatLine(c.playerX,c.eyeY,c.playerZ,e.x,c.eyeY+dy,e.z)) null else e to distance
        }.sortedBy { it.second }.take(count).map { it.first }.toList()
    }
    /** Coup d'outil (pioche, hache, houe…) : une seule cible, dégâts modestes, petit recul. */
    fun toolStrike(damage: Int) {
        val e=targetsInArc(TOOL_REACH,TOOL_ARC,1).firstOrNull() ?: return
        r.enemyManager.damageEnemy(e,damage)
        r.enemyManager.knockbackFromPlayer(e,2.0)
    }
    private fun strike(p: E.Melee, strength: Float) {
        val targets=targetsInArc(p.reach,p.arc,p.targets)
        for(e in targets) {
            r.enemyManager.damageEnemy(e,(p.damage*(1f+strength*.85f)).roundToInt())
            r.enemyManager.knockbackFromPlayer(e,if(p.type=="hammer") 7.0 else 2.5+strength*3.5)
            if(strength>.65f || p.type=="hammer") { e.staggerTimer=if(e.isBoss) .15f else .48f;e.attackWindup=0f }
        }
    }
    fun receive(damage: Int, dirX: Float, dirZ: Float, attacker: Enemy? = null) {
        if(r.playerNode.hp<=0) return
        var amount=damage.toFloat()
        val yaw=Math.toRadians(r.camera.yaw.toDouble())
        val frontal=-(sin(yaw)*dirX+cos(yaw)*dirZ)>.35
        if(guard>0f && frontal && stamina>=damage*1.2f) {
            stamina=(stamina-damage*1.2f).coerceAtLeast(0f);rest=.8f
            val parry=guard>.48f
            amount*=if(parry) 0f else if(shield) .18f else .5f
            if(parry) { message(R.string.cave_parried);attacker?.staggerTimer=.8f;attacker?.attackWindup=0f;attacker?.let { r.enemyManager.knockbackFromPlayer(it,5.0) };r.startSwing() }
        }
        amount*=1f-E.armor(armor)
        if(amount>0f) {
            val final=amount.roundToInt().coerceAtLeast(1)
            r.playerNode.applyDamage(final);r.eventBus.publish(GameEvent.PlayerHit(final,dirX,dirZ))
            val thorns=r.equippedWeaponStat("thorns")
            if(attacker!=null && thorns>0) r.enemyManager.damageEnemy(attacker,thorns)
        }
    }
    fun equip(id: Short) {
        if((r.inventory[id] ?: 0)<=0 || id==armor || id==E.SHIELD && shield) return
        val old=if(id==E.SHIELD) null else armor
        if(old!=null && (r.inventory[old] ?: 0)==Int.MAX_VALUE) { message(R.string.cave_equipment_full);return }
        if(E.armor(id)==0f && id!=E.SHIELD) return
        val left=r.inventory.getValue(id)-1
        if(left==0) r.inventory.remove(id) else r.inventory[id]=left
        if(old!=null) r.inventory[old]=(r.inventory[old] ?: 0)+1
        if(id==E.SHIELD) shield=true else armor=id
        r.changedFrontierInventory();announce()
    }
    fun removeEquipment() {
        val items=listOfNotNull(armor, if(shield) E.SHIELD else null)
        if(items.any { (r.inventory[it] ?: 0)==Int.MAX_VALUE }) { message(R.string.cave_equipment_full);return }
        for(id in items) r.inventory[id]=(r.inventory[id] ?: 0)+1
        armor=null;shield=false;r.changedFrontierInventory();announce()
    }
    companion object {
        const val TOOL_REACH = 2.6
        const val TOOL_ARC = .80
    }
    private fun message(id: Int) { r.farmMessageCallback?.invoke(context.getString(id)) }
    private fun announce() { r.farmMessageCallback?.invoke(context.getString(R.string.cave_equipment_changed,
        (E.armor(armor)*100).roundToInt(),context.getString(if(shield) R.string.cave_ui_ready else R.string.cave_ui_missing))) }
}
