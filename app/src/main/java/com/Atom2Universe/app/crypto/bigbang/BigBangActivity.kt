package com.Atom2Universe.app.crypto.bigbang

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.MainClickerActivity
import com.Atom2Universe.app.crypto.clicker.BigBangBonus
import com.Atom2Universe.app.crypto.clicker.BigBangRepository
import com.Atom2Universe.app.crypto.clicker.ElementTokenRepository
import com.Atom2Universe.app.crypto.clicker.FactoryRepository
import com.Atom2Universe.app.util.enableImmersiveMode

class BigBangActivity : ThemedActivity() {

    private lateinit var bigBangRepo: BigBangRepository
    private lateinit var elementTokenRepo: ElementTokenRepository
    private lateinit var factoryRepo: FactoryRepository

    private val pendingQty = mutableMapOf<BigBangBonus, Int>()
    private val itemViews  = mutableMapOf<BigBangBonus, View>()

    private lateinit var tokenBalanceView: TextView
    private lateinit var pendingSummaryView: TextView
    private lateinit var bigBangButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_big_bang)
        enableImmersiveMode()

        bigBangRepo      = BigBangRepository(this)
        elementTokenRepo = ElementTokenRepository(this)
        factoryRepo      = FactoryRepository(this)

        tokenBalanceView  = findViewById(R.id.big_bang_token_balance)
        pendingSummaryView = findViewById(R.id.big_bang_pending_summary)
        bigBangButton     = findViewById(R.id.big_bang_button)

        val count = bigBangRepo.getBigBangCount()
        val titleView = findViewById<TextView>(R.id.big_bang_title)
        titleView.text = if (count == 0) getString(R.string.big_bang_title)
                         else getString(R.string.big_bang_title_count, count)

        findViewById<ImageButton>(R.id.big_bang_back).setOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.big_bang_bonus_container)
        BigBangBonus.values().forEach { bonus ->
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_big_bang_bonus, container, false)
            setupBonusItem(itemView, bonus)
            container.addView(itemView)
            itemViews[bonus] = itemView
        }

        bigBangButton.setOnClickListener { triggerBigBang() }

        updateSummary()
    }

    private fun setupBonusItem(view: View, bonus: BigBangBonus) {
        view.findViewById<TextView>(R.id.bonus_name).text   = bonusName(bonus)
        view.findViewById<TextView>(R.id.bonus_effect).text =
            if (bonus == BigBangBonus.SPACETIME_COMPRESSION)
                getString(R.string.big_bang_bonus_effect_spacetime)
            else
                getString(R.string.big_bang_bonus_effect, bonus.effectPercent)

        view.findViewById<Button>(R.id.bonus_minus).setOnClickListener {
            val qty = pendingQty[bonus] ?: 0
            if (qty > 0) {
                pendingQty[bonus] = qty - 1
                updateItemView(view, bonus)
                updateSummary()
            }
        }
        view.findViewById<Button>(R.id.bonus_plus).setOnClickListener {
            val qty     = pendingQty[bonus] ?: 0
            val newCost = totalCost() + bonus.tokenCost
            if (newCost <= elementTokenRepo.getBalance()) {
                pendingQty[bonus] = qty + 1
                updateItemView(view, bonus)
                updateSummary()
            } else {
                Toast.makeText(this, R.string.big_bang_not_enough_tokens, Toast.LENGTH_SHORT).show()
            }
        }

        updateItemView(view, bonus)
    }

    private fun updateItemView(view: View, bonus: BigBangBonus) {
        val currentLevel = bigBangRepo.getLevel(bonus)
        val qty          = pendingQty[bonus] ?: 0

        view.findViewById<TextView>(R.id.bonus_level).text = if (qty > 0)
            getString(R.string.big_bang_bonus_level_arrow, currentLevel, currentLevel + qty)
        else
            getString(R.string.big_bang_bonus_level, currentLevel)

        view.findViewById<TextView>(R.id.bonus_qty).text  = qty.toString()
        view.findViewById<TextView>(R.id.bonus_cost).text = if (qty > 0)
            getString(R.string.big_bang_bonus_cost, qty * bonus.tokenCost)
        else
            ""
    }

    private fun updateSummary() {
        val balance = elementTokenRepo.getBalance()
        val cost    = totalCost()
        val count   = pendingQty.values.sum()

        tokenBalanceView.text   = getString(R.string.big_bang_tokens_balance, balance)
        pendingSummaryView.text = getString(R.string.big_bang_pending_summary, count, cost)
    }

    private fun totalCost(): Int = pendingQty.entries.sumOf { (bonus, qty) -> bonus.tokenCost * qty }

    private fun bonusName(bonus: BigBangBonus): String = when (bonus) {
        BigBangBonus.GOD_FINGER         -> "☝ " + getString(R.string.big_bang_bonus_god_finger)
        BigBangBonus.STAR_CORE          -> "⭐ " + getString(R.string.big_bang_bonus_star_core)
        BigBangBonus.PROTON_ACCELERATOR -> "⚡ " + getString(R.string.big_bang_bonus_proton_accel)
        BigBangBonus.FUSION_REACTOR     -> "🔥 " + getString(R.string.big_bang_bonus_fusion_reactor)
        BigBangBonus.HADRON_COLLIDER    -> "💠 " + getString(R.string.big_bang_bonus_hadron_collider)
        BigBangBonus.PROTON_INJECTOR    -> "⚗ " + getString(R.string.big_bang_bonus_proton_injector)
        BigBangBonus.PLASMA_CATALYST    -> "🌡 " + getString(R.string.big_bang_bonus_plasma_catalyst)
        BigBangBonus.SYNCHROTRON        -> "🔄 " + getString(R.string.big_bang_bonus_synchrotron)
        BigBangBonus.SPACETIME_COMPRESSION -> "🌀 " + getString(R.string.big_bang_bonus_spacetime)
    }

    private fun triggerBigBang() {
        val cost = totalCost()
        if (!elementTokenRepo.consumeTokens(cost)) {
            Toast.makeText(this, R.string.big_bang_not_enough_tokens, Toast.LENGTH_SHORT).show()
            return
        }

        pendingQty.forEach { (bonus, qty) ->
            if (qty > 0) bigBangRepo.addLevels(bonus, qty)
        }

        factoryRepo.reset()

        getSharedPreferences("pending_reset_flags", Context.MODE_PRIVATE).edit {
            putBoolean("reset_big_bang", true)
        }

        val intent = Intent(this, MainClickerActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}
