package com.Atom2Universe.app.games.caves

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/** Moving starts a drag immediately; a stationary hold toggles a favorite once. */
internal object CaveInventoryGestures {
    fun clear(view: View) { (view.tag as? Binding)?.dispose();view.tag=null;view.setOnTouchListener(null) }
    fun bind(view: View, active: () -> Boolean = { true }, inactiveTap: () -> Unit = {},
             tap: () -> Unit, favorite: () -> Unit, drag: () -> Unit) {
        (view.tag as? Binding)?.dispose()
        view.tag=Binding(view,active,inactiveTap,tap,favorite,drag).also { it.install() }
    }

    private class Binding(val view: View,val active: () -> Boolean,val inactiveTap: () -> Unit,
                          val tap: () -> Unit,val favorite: () -> Unit,val drag: () -> Unit): View.OnAttachStateChangeListener {
        private val slop=ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
        private var down=false
        private var handled=false
        private var x=0f
        private var y=0f
        private val hold=Runnable {
            if(down && !handled && active()) {
                handled=true;view.isPressed=false;view.performLongClick()
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        fun install() {
            view.addOnAttachStateChangeListener(this)
            view.setOnClickListener { if(active()) tap() else inactiveTap() }
            view.setOnLongClickListener {
                if(!active()) false else {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    favorite();true
                }
            }
            view.setOnTouchListener { _,event ->
                if(!active()) { cancel();return@setOnTouchListener false }
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        down=true;handled=false;x=event.x;y=event.y;view.isPressed=true
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        view.postDelayed(hold,ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    MotionEvent.ACTION_MOVE -> if(down && !handled &&
                        (event.x-x)*(event.x-x)+(event.y-y)*(event.y-y)>slop*slop) {
                        handled=true;view.removeCallbacks(hold);view.isPressed=false;drag()
                    }
                    MotionEvent.ACTION_UP -> {
                        val click=down && !handled
                        cancel();if(click) view.performClick()
                    }
                    MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN -> cancel()
                }
                true
            }
        }

        private fun cancel() {
            down=false;handled=true;view.isPressed=false;view.removeCallbacks(hold)
            view.parent?.requestDisallowInterceptTouchEvent(false)
        }
        fun dispose() { cancel();view.removeOnAttachStateChangeListener(this) }
        override fun onViewDetachedFromWindow(v: View) { cancel() }
        override fun onViewAttachedToWindow(v: View) = Unit
    }
}
