package com.Atom2Universe.app.games.golf

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import com.Atom2Universe.app.games.golf.classic.ClassicGolfActivity
import com.Atom2Universe.app.games.golf.classic.ClassicShotOverlay
import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE,application=Application::class)
class GolfCameraGestureTest {
    private class Listener:ClassicShotOverlay.Listener {
        var active=true
        var fastForward=false
        val speedChanges=mutableListOf<Boolean>()
        var rotations=0
        override fun manualFlightCamera()=active
        override fun cameraFastForward(held:Boolean){fastForward=held;speedChanges+=held}
        override fun cameraRotate(dx:Float,dy:Float){rotations++}
        override fun cameraMove(dx:Float,dy:Float,zoom:Float){}
        override fun canShoot()=false
        override fun aim(radians:Float){}
        override fun zoom(factor:Float){}
        override fun pull(fraction:Float){}
        override fun release(fraction:Float,needle:Float){}
        override fun cancel(){}
        override fun fieldOfView()=1f
        override fun overview()=false
        override fun aimOnMap(dx:Float,dy:Float){}
        override fun pan(dx:Float,dy:Float){}
        override fun rotate(radians:Float){}
    }
    private val listener=Listener()
    private val view by lazy { ClassicShotOverlay(RuntimeEnvironment.getApplication(),GolfSwing(),listener).apply{layout(0,0,1080,1920)} }
    private var down=0L
    private fun event(action:Int,delay:Long=0,x:Float=300f,y:Float=500f) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(delay))
        val now=SystemClock.uptimeMillis()
        if(action==MotionEvent.ACTION_DOWN)down=now
        val e=MotionEvent.obtain(down,now,action,x,y,0)
        view.onTouchEvent(e);e.recycle()
    }
    private fun tap(){event(MotionEvent.ACTION_DOWN);event(MotionEvent.ACTION_UP,50)}

    @Test fun doubleTapInOrbitAcceleratesOnlyWhileSecondPressIsHeld() {
        tap()
        assertFalse(listener.fastForward)
        event(MotionEvent.ACTION_DOWN,100,x=302f)
        assertTrue(listener.fastForward)
        event(MotionEvent.ACTION_MOVE,450,x=330f)
        assertTrue(listener.fastForward)
        assertEquals("Holding fast-forward must not turn the camera",0,listener.rotations)
        event(MotionEvent.ACTION_UP,450,x=302f)
        assertFalse(listener.fastForward)
        assertEquals(listOf(true,false),listener.speedChanges)
        assertFalse(view.holding)
    }

    @Test fun quickDoubleTapDoesNotLeaveFastForwardEnabled() {
        tap();event(MotionEvent.ACTION_DOWN,100);event(MotionEvent.ACTION_UP,50)
        assertFalse(listener.fastForward)
        assertEquals(listOf(true,false),listener.speedChanges)
        // A subsequent single press cannot reactivate fast-forward.
        event(MotionEvent.ACTION_DOWN,600);event(MotionEvent.ACTION_UP,50)
        assertFalse(listener.fastForward)
        assertEquals(listOf(true,false),listener.speedChanges)
    }

    @Test fun cancelledTouchAndShotResetBothStopFastForward() {
        tap();event(MotionEvent.ACTION_DOWN,100)
        assertTrue(listener.fastForward)
        event(MotionEvent.ACTION_CANCEL,50)
        assertFalse(listener.fastForward)
        event(MotionEvent.ACTION_DOWN,600);event(MotionEvent.ACTION_UP,50)
        event(MotionEvent.ACTION_DOWN,100)
        assertTrue(listener.fastForward)
        view.resetCameraTaps()
        assertFalse(listener.fastForward)
    }

    @Test fun draggingCameraThenTappingDoesNotAccelerate() {
        event(MotionEvent.ACTION_DOWN)
        event(MotionEvent.ACTION_MOVE,40,x=500f)
        event(MotionEvent.ACTION_UP,40,x=500f)
        event(MotionEvent.ACTION_DOWN,100,x=500f)
        event(MotionEvent.ACTION_UP,50,x=500f)
        assertTrue(listener.rotations>0)
        assertTrue(listener.speedChanges.isEmpty())
        assertFalse(view.holding)
    }

    @Test fun shotResetCancelsAnUnfinishedDoubleTap() {
        tap();view.resetCameraTaps()
        event(MotionEvent.ACTION_DOWN,100);event(MotionEvent.ACTION_UP,50)
        assertTrue(listener.speedChanges.isEmpty())
    }

    @Test fun orbitInputIsAvailableBeforeImpactAsWellAsDuringFlight() {
        // Construct the activity without opening its GL scene; exercise its actual input gate.
        val activity=Robolectric.buildActivity(ClassicGolfActivity::class.java).get()
        val gate=ReflectionHelpers.getField<ClassicShotOverlay.Listener>(activity,"shotListener")
        val game=ClassicGame(ClassicCourse.holes.first())
        ReflectionHelpers.setField(activity,"game",game)
        assertFalse(gate.manualFlightCamera())
        ReflectionHelpers.setField(activity,"pendingStrike",1f to 0f)
        ReflectionHelpers.setField(activity,"swingTime",.1f)
        assertTrue("The golfer is already animating before impact",gate.manualFlightCamera())
        game.hit(.5f)
        ReflectionHelpers.setField(activity,"pendingStrike",null)
        ReflectionHelpers.setField(activity,"swingTime",-1f)
        assertTrue("Flight does not depend on replay recording",gate.manualFlightCamera())
        ReflectionHelpers.setField(activity,"completed",true)
        assertFalse(gate.manualFlightCamera())
    }
}
