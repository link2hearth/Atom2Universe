package com.Atom2Universe.app.science.parentes

import android.app.Application
import android.content.Context
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],manifest=Config.NONE,application=Application::class,shadows=[ParentesGestureTest.TestAppearance::class])
class ParentesGestureTest {
    /** No packaged app theme in this JVM suite; keep gesture tests independent of theme loading. */
    @Implements(value=AppearanceStyle::class,isInAndroidSdk=false)
    class TestAppearance {
        @Implementation fun color(context:Context,attr:Int):Int=when(attr) {
            R.attr.a2uBackgroundColor -> 0xff17202a.toInt()
            R.attr.a2uTextColor -> 0xffeeeeee.toInt()
            else -> 0xff829ca5.toInt()
        }
    }
    private val view by lazy { ParentesTreeView(RuntimeEnvironment.getApplication()).apply{layout(0,0,600,800)} }
    private var down=0L
    private fun value(name:String)=ReflectionHelpers.getField<Float>(view,name)
    private fun event(action:Int,delay:Long=0,x:Float=100f,y:Float=200f) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(delay))
        val now=SystemClock.uptimeMillis()
        if(action==MotionEvent.ACTION_DOWN)down=now
        val e=MotionEvent.obtain(down,now,action,x,y,0)
        view.onTouchEvent(e);e.recycle()
    }
    private fun finish()=shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
    @Test fun doubleTouchZoomEtRecentrageSansOuvrirDeFiche() {
        var selections=0;view.onSelect={selections++}
        event(MotionEvent.ACTION_DOWN);event(MotionEvent.ACTION_UP,40)
        event(MotionEvent.ACTION_DOWN,80);event(MotionEvent.ACTION_UP,40)
        finish()
        assertEquals(2f,value("zoom"),.001f)
        assertEquals(300f,100f*value("zoom")+value("tx"),.01f)
        assertEquals(400f,200f*value("zoom")+value("ty"),.01f)
        assertEquals(0,selections)
    }
    @Test fun secondTouchMaintenuDeplaceSansSautDeZoomALaFin() {
        event(MotionEvent.ACTION_DOWN);event(MotionEvent.ACTION_UP,40)
        event(MotionEvent.ACTION_DOWN,80)
        event(MotionEvent.ACTION_MOVE,40,160f,260f)
        event(MotionEvent.ACTION_MOVE,40,200f,280f)
        event(MotionEvent.ACTION_UP,40,200f,280f)
        finish()
        assertEquals(1f,value("zoom"),.001f)
        assertEquals(100f,value("tx"),.01f)
        assertEquals(80f,value("ty"),.01f)
    }
    @Test fun gesteAnnuleNeRecentrePasLaCamera() {
        event(MotionEvent.ACTION_DOWN);event(MotionEvent.ACTION_UP,40)
        event(MotionEvent.ACTION_DOWN,80);event(MotionEvent.ACTION_CANCEL,40)
        finish()
        assertEquals(1f,value("zoom"),.001f)
        assertEquals(0f,value("tx"),.001f)
    }
    @Test fun rechercherUneEspeceSepareEtAfficheLesDeuxNomsVoisins() {
        fun node(id:String,parent:String?,species:Boolean)=LifeNode(id,parent,id,species,"",emptyList(),emptyList(),emptyList())
        val tree=LifeTree(listOf(node("root",null,false),node("a","root",true),node("b","root",true)).associateBy{it.id},"root",emptySet())
        val scene=TreeScene(linkedMapOf("root" to null,"a" to "root","b" to "root"))
        val positions=mapOf("root" to TreeLayout.RadialPoint(0f,0f,0f,0f),
            "a" to TreeLayout.RadialPoint(640f,0f,0f,640f),
            "b" to TreeLayout.RadialPoint(640f,1f,.00156f,640f))
        view.show(scene,tree,emptySet(),positions){if(it=="root")"" else "Species $it"}
        view.fit(false)
        view.reveal("a");finish()
        assertTrue(value("zoom")>4f)
        val bitmap=Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val marks=ReflectionHelpers.getField<List<Any>>(view,"marks")
        val labels=marks.filter{ReflectionHelpers.getField<String>(it,"id")!="root"}
            .map{ReflectionHelpers.getField<RectF>(it,"bounds")}
        assertEquals(2,labels.size)
        assertTrue(labels.all{!it.isEmpty})
        assertFalse(RectF.intersects(labels[0],labels[1]))
        bitmap.recycle()
    }
}
