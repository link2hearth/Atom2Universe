package com.example.atom2univers

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView

class GameWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    private var activeTouchCount = 0

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!hasFocus()) {
            requestFocusFromTouch()
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeTouchCount = 1
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                activeTouchCount += 1
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                activeTouchCount = (activeTouchCount - 1).coerceAtLeast(0)
                if (activeTouchCount == 0) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_UP -> {
                activeTouchCount = 0
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                activeTouchCount = 0
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
