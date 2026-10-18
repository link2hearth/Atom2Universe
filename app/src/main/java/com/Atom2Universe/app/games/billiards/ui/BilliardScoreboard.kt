package com.Atom2Universe.app.games.billiards.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.billiards.core.*
import com.Atom2Universe.app.games.billiards.render.BilliardBallStyle
import kotlin.math.min

/** Native labels remain accessible and respect the system font size; icons are decorative. */
class BilliardScoreboard(context: Context,private val ballName: (Int)->String) : LinearLayout(context) {
    var playerNames: List<String>?=null
    private val density=resources.displayMetrics.density
    private fun dp(value: Int)=(value*density).toInt()
    private data class Badge(val id: Int,val potted: Boolean=false)
    private data class Indication(val label: String,val balls: List<Badge> = emptyList())

    private inner class Card : LinearLayout(context) {
        val title=TextView(context).apply {
            textSize=14f; typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            setTextColor(0xFFF1EBDD.toInt())
        }
        val detail=TextView(context).apply { textSize=12f; setTextColor(0xFFE4DFD2.toInt()) }
        val busy=ProgressBar(context,null,android.R.attr.progressBarStyleSmall).apply {
            isIndeterminate=true; indeterminateTintList=ColorStateList.valueOf(0xFFE4C488.toInt())
            visibility=GONE
        }
        val balls=BallRow()
        var indication: Indication?=null
        init {
            orientation=VERTICAL; setPadding(dp(10),dp(7),dp(10),dp(7))
            importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
            addView(LinearLayout(context).apply {
                gravity=Gravity.CENTER_VERTICAL
                addView(title,LayoutParams(0,LayoutParams.WRAP_CONTENT,1f))
                addView(busy,LayoutParams(dp(14),dp(14)).apply { marginStart=dp(4) })
            })
            addView(detail)
            addView(balls,LayoutParams(LayoutParams.MATCH_PARENT,dp(24)))
            title.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
            detail.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
            busy.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }
    private val cards=List(2) { player -> Card().also {
        addView(it,LayoutParams(0,LayoutParams.MATCH_PARENT,1f).apply { if(player==1) marginStart=dp(6) })
    } }

    fun update(s: BilliardSession,thinking: Boolean=false,plannedBall: Int?=null) {
        cards.forEachIndexed { player,card ->
            val active=s.match.player==player
            val busy=active && thinking
            val name=playerNames?.getOrNull(player) ?: context.getString(when {
                s.mode==PlayMode.COMPUTER -> if(player==0) R.string.billiard_you else R.string.billiard_mode_ai
                s.mode==PlayMode.PRACTICE -> if(player==0) R.string.billiard_mode_solo else R.string.billiard_practice_statistics
                else -> R.string.billiard_player_name
            },player+1)
            val title=if(s.mode==PlayMode.PRACTICE) name else context.getString(R.string.billiard_player_heading,name,s.match.score(player))
            // Retain the target of the shot in progress instead of changing it as balls fall.
            val indication=if(s.world.moving) card.indication ?: Indication(context.getString(R.string.billiard_shot_in_progress))
                else indication(s,player,plannedBall ?: s.nominated)
            card.indication=indication
            if(card.title.text.toString()!=title) card.title.text=title
            if(card.detail.text.toString()!=indication.label) card.detail.text=indication.label
            card.balls.set(s.discipline,indication.balls)
            card.busy.visibility=if(busy) VISIBLE else if(s.mode==PlayMode.COMPUTER && player==1) INVISIBLE else GONE
            var description=context.getString(R.string.billiard_player_description,title,indication.label)
            if(active) description=context.getString(R.string.billiard_active_player,description)
            if(busy) description=context.getString(R.string.billiard_player_description,description,
                context.getString(if(plannedBall!=null) R.string.billiard_computer_aiming else R.string.billiard_thinking))
            if(card.contentDescription!=description) card.contentDescription=description
            if(card.tag!=active) {
                card.tag=active
                card.background=GradientDrawable().apply {
                    cornerRadius=dp(14).toFloat(); setColor(if(active) 0xF02B4146.toInt() else 0xDB101B22.toInt())
                    setStroke(dp(1),if(active) 0xFFE4C488.toInt() else 0xFF34454C.toInt())
                }
            }
        }
    }

    private fun indication(s: BilliardSession,player: Int,nominated: Int): Indication {
        fun label(res: Int,vararg args: Any)=context.getString(res,*args)
        if(s.mode==PlayMode.PRACTICE) return Indication(if(player==0) label(R.string.billiard_practice_free_play)
            else label(R.string.billiard_shots_count,s.match.shots))
        val active=s.match.player==player
        if(s.discipline in listOf(Discipline.EIGHT,Discipline.BLACKBALL,Discipline.HEYBALL)) {
            val group=s.groupFor(player)
            if(group==0) return Indication(label(R.string.billiard_open_table),listOf(Badge(1),Badge(9)))
            val onTable=s.world.balls.filter { it.motion!=Motion.POCKETED }.map { it.id }.toSet()
            val ids=if(group==1) (1..7) else (9..15)
            val remaining=ids.count { it in onTable }
            val groupRes=if(s.discipline==Discipline.BLACKBALL) {
                if(group==1) R.string.billiard_reds_group else R.string.billiard_yellows_group
            } else if(group==1) R.string.billiard_solids else R.string.billiard_stripes
            var detail=if(remaining==0) label(R.string.billiard_black_to_play)
                else label(R.string.billiard_group_remaining,label(groupRes),remaining)
            if(active && s.match.freeShots>0) detail=label(R.string.billiard_selection,detail,label(R.string.billiard_free_contact))
            return Indication(detail,if(remaining==0) listOf(Badge(8,8 !in onTable)) else ids.map { Badge(it,it !in onTable) })
        }
        if(s.discipline==Discipline.ONE_POCKET) return Indication(label(R.string.billiard_pocket,s.pocketFor(player)+1))
        if(!active || s.match.winner>=0 || s.match.decision!=ShotDecision.NONE) return Indication(label(R.string.billiard_best_run,if(player==0) s.match.best0 else s.match.best1))
        if(s.pushOut) return Indication(label(R.string.billiard_free_contact))
        val targets=s.legalTargets().map { it.id }
        if(s.discipline==Discipline.SNOOKER || s.discipline==Discipline.SIX_RED) {
            if(targets.any { it in 1..15 }) return Indication(label(R.string.billiard_red_to_play),listOf(Badge(targets.first())))
            if(s.match.snookerColor && nominated !in targets) return Indication(label(R.string.billiard_choose_colour),targets.map { Badge(it) })
            val selected=if(nominated in targets) listOf(nominated) else targets
            return Indication(label(R.string.billiard_target,selected.joinToString(label(R.string.billiard_list_separator),transform=ballName)),selected.map { Badge(it) })
        }
        if(targets.size in 1..3) return Indication(label(R.string.billiard_target,targets.joinToString(label(R.string.billiard_list_separator),transform=ballName)),targets.map { Badge(it) })
        return Indication(label(R.string.billiard_any_object_ball))
    }

    private inner class BallRow : View(context) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=Typeface.DEFAULT_BOLD; textAlign=Paint.Align.CENTER }
        private val clip=Path()
        private var discipline=Discipline.EIGHT
        private var badges=emptyList<Badge>()
        private var numbers=emptyList<String>()
        init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
        fun set(game: Discipline,value: List<Badge>) {
            if(game==discipline && value==badges) return
            discipline=game; badges=value
            numbers=value.map { context.getString(R.string.billiard_number,it.id) }
            invalidate()
        }
        override fun onDraw(canvas: Canvas) {
            if(badges.isEmpty()) return
            val gap=dp(3).toFloat()
            val diameter=min(dp(19).toFloat(),(width-gap*(badges.size-1))/badges.size).coerceAtLeast(0f)
            val r=diameter/2; val y=height/2f
            val numbered=BilliardBallStyle.numbered(discipline)
            badges.forEachIndexed { i,badge ->
                val x=r+i*(diameter+gap)
                val centerX=if(layoutDirection==LAYOUT_DIRECTION_RTL) width-x else x
                canvas.save()
                canvas.translate(centerX,y)
                if(badge.potted) {
                    // Keep a quiet, empty slot: only balls still to play retain colour.
                    paint.color=0xFF718082.toInt(); paint.style=Paint.Style.STROKE; paint.strokeWidth=density
                    canvas.drawCircle(0f,0f,r*.92f,paint); paint.style=Paint.Style.FILL
                    paint.color=0xFF91A0A0.toInt()
                    if(numbered) {
                        paint.textSize=r*.95f
                        canvas.drawText(numbers[i],0f,-(paint.ascent()+paint.descent())/2,paint)
                    }
                } else {
                    paint.color=BilliardBallStyle.color(discipline,badge.id) or 0xFF000000.toInt()
                    canvas.drawCircle(0f,0f,r,paint)
                    if(numbered && badge.id in 9..15) {
                        clip.reset(); clip.addCircle(0f,0f,r,Path.Direction.CW)
                        canvas.save(); canvas.clipPath(clip)
                        paint.color=0xFFF4EEDB.toInt()
                        canvas.drawRect(-r,-r,r,-r*.48f,paint); canvas.drawRect(-r,r*.48f,r,r,paint)
                        canvas.restore()
                    }
                    paint.style=Paint.Style.STROKE; paint.strokeWidth=density*.7f; paint.color=0xAADED9CA.toInt()
                    canvas.drawCircle(0f,0f,r*.96f,paint); paint.style=Paint.Style.FILL
                    if(numbered && badge.id>0) {
                        paint.color=0xFFF9F4E6.toInt(); canvas.drawCircle(0f,0f,r*.53f,paint)
                        paint.color=0xFF172023.toInt(); paint.textSize=r*.86f
                        canvas.drawText(numbers[i],0f,-(paint.ascent()+paint.descent())/2,paint)
                    } else {
                        paint.color=0x55FFFFFF; canvas.drawCircle(-r*.3f,-r*.3f,r*.22f,paint)
                    }
                }
                canvas.restore()
            }
        }
    }
}
