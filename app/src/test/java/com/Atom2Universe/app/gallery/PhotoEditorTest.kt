package com.Atom2Universe.app.gallery

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoEditorTest {
    private fun sheet(w: Int, h: Int, color: Int) = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }
    private fun rectangle(left: Float,top: Float,right: Float,bottom: Float) =
        PhotoMask(listOf(MaskPart.Shape(Trace(listOf(PointF(left,top),PointF(right,bottom)),rectangle=true))))

    @Test fun localFilterPreservesUnselectedPixelsAndTransparencyAtBothResolutions() {
        val op = PhotoEdit.Tone(PhotoLook.MONO.matrix(),rectangle(0f,0f,0.5f,1f))
        for (edge in listOf(32,240)) {
            val source = sheet(edge,edge,0x802040a0.toInt())
            val output = PhotoRender.apply(source,op)
            val tinted = output.getPixel(edge/4,edge/2)
            assertEquals(Color.red(tinted),Color.green(tinted))
            assertEquals(Color.green(tinted),Color.blue(tinted))
            assertEquals(128,Color.alpha(tinted))
            assertEquals(source.getPixel(edge-3,5),output.getPixel(edge-3,5))
        }
    }

    @Test fun wandRemovesOnlyConnectedBackgroundAndBrushRefinesACutout() {
        val source = sheet(64,64,Color.WHITE)
        for (y in 8..55) for (x in 18..45) source.setPixel(x,y,Color.BLUE)
        source.setPixel(30,30,Color.WHITE)
        val bg = PhotoSelections.wand(source,PointF(.05f,.1f),20)
        val erased = PhotoRender.apply(source,PhotoEdit.Cut(bg,false))
        assertEquals(0,Color.alpha(erased.getPixel(2,2)))
        assertEquals(Color.BLUE,erased.getPixel(24,20))
        assertEquals(Color.WHITE,erased.getPixel(30,30))
        val estimate = PhotoSelections.subject(source,RectF(.2f,.05f,.8f,.95f))
        val refined = PhotoMask(estimate.parts + MaskPart.Shape(Trace(listOf(PointF(.4f,.4f)),.15f),subtract=true))
        val cut = PhotoRender.apply(source,PhotoEdit.Cut(refined,true))
        assertEquals(0,Color.alpha(cut.getPixel(1,1)))
        assertEquals(0,Color.alpha(cut.getPixel(25,25)))
        assertEquals(Color.BLUE,cut.getPixel(32,40))
        assertEquals(Color.WHITE,source.getPixel(1,1))
    }

    @Test fun cropMirrorAndRotationReplayInImageCoordinates() {
        val src = sheet(100,60,Color.RED)
        for (y in 0 until 60) for (x in 50 until 100) src.setPixel(x,y,Color.BLUE)
        val cropped = PhotoRender.apply(src,PhotoEdit.Frame(0f,RectF(.25f,0f,.75f,1f)))
        val mirrored = PhotoRender.apply(cropped,PhotoEdit.Mirror)
        val rotated = PhotoRender.apply(mirrored,PhotoEdit.Rotate)
        assertEquals(60,rotated.width)
        assertEquals(50,rotated.height)
        assertEquals(Color.BLUE,rotated.getPixel(5,5))
        assertEquals(Color.RED,rotated.getPixel(5,45))
    }

    private fun adjust(mask: PhotoMask? = null, vararg set: Pair<AdjustChannel,Int>) =
        PhotoEdit.Adjust(AdjustChannel.entries.map { c -> set.firstOrNull { it.first == c }?.second ?: 0 },mask)

    @Test fun adjustmentsStayLocalKeepTransparencyAndDoNothingAtZero() {
        val source = sheet(120,80,0x80406080.toInt())
        assertEquals(source.getPixel(60,40),PhotoRender.apply(source,adjust()).getPixel(60,40))
        val lit = PhotoRender.apply(source,adjust(rectangle(0f,0f,.5f,1f),AdjustChannel.LIGHT to 60,AdjustChannel.SHARPNESS to 50))
        assertTrue(Color.green(lit.getPixel(20,40)) > Color.green(source.getPixel(20,40)))
        assertEquals(128,Color.alpha(lit.getPixel(20,40)))
        assertEquals(source.getPixel(100,40),lit.getPixel(100,40))
        val grey = PhotoRender.apply(source,adjust(null,AdjustChannel.SATURATION to -100)).getPixel(60,40)
        assertEquals(Color.red(grey),Color.blue(grey))
    }

    @Test fun blurStraightenAndAutoLevelsWorkOnWholeImage() {
        val source = sheet(200,100,Color.BLACK)
        for (y in 0 until 100) for (x in 100 until 200) source.setPixel(x,y,Color.WHITE)
        val blurred = PhotoRender.apply(source,adjust(null,AdjustChannel.BLUR to 100))
        assertTrue(Color.red(blurred.getPixel(100,50)) in 60..200)
        assertEquals(Color.BLACK,blurred.getPixel(10,50))
        val level = PhotoRender.apply(source,PhotoEdit.Frame(10f,RectF(0f,0f,1f,1f)))
        assertEquals(200,level.width)
        assertEquals(100,level.height)
        for (corner in listOf(0 to 0,199 to 0,0 to 99,199 to 99)) assertEquals(255,Color.alpha(level.getPixel(corner.first,corner.second)))
        val dull = sheet(64,64,0xff404040.toInt())
        for (y in 0 until 64) for (x in 32 until 64) dull.setPixel(x,y,0xffa0a0a0.toInt())
        val levels = requireNotNull(PhotoRender.autoLevels(dull,null))
        val stretched = PhotoRender.apply(dull,levels)
        assertTrue(Color.red(stretched.getPixel(5,5)) < 8)
        assertTrue(Color.red(stretched.getPixel(60,5)) > 247)
        assertNull(PhotoRender.autoLevels(source,null))
    }

    @Test fun featherInvertFillAndCloneFollowTheSelection() {
        val source = sheet(100,100,Color.RED)
        for (y in 0 until 100) for (x in 50 until 100) source.setPixel(x,y,Color.BLUE)
        val left = rectangle(0f,0f,.5f,1f)
        val inverted = PhotoMask(left.parts + MaskPart.Invert)
        val filled = PhotoRender.apply(source,PhotoEdit.Fill(Color.GREEN,inverted))
        assertEquals(Color.RED,filled.getPixel(20,50))
        assertEquals(Color.GREEN,filled.getPixel(80,50))
        val soft = PhotoRender.apply(source,PhotoEdit.Fill(Color.GREEN,left.copy(feather = .08f)))
        assertEquals(Color.GREEN,soft.getPixel(10,50))
        assertEquals(Color.BLUE,soft.getPixel(90,50))
        val edge = soft.getPixel(50,50)
        assertTrue(Color.green(edge) in 40..215)
        // Copy the blue half onto the red one: the source lies 50 px to the right of the stroke.
        val stamp = Trace(listOf(PointF(.2f,.5f)),.2f)
        val cloned = PhotoRender.apply(source,PhotoEdit.Clone(stamp,PointF(.5f,0f),null))
        assertEquals(Color.BLUE,cloned.getPixel(20,50))
        assertEquals(Color.RED,cloned.getPixel(20,10))
        val faint = PhotoRender.apply(source,PhotoEdit.Ink(stamp.copy(soft = 1f),Color.WHITE,false,null,.5f))
        assertTrue(Color.green(faint.getPixel(20,50)) in 100..150)
        assertEquals(Color.RED,faint.getPixel(40,10))
    }

    @Test fun healContinuesTheSurroundingsOverADetail() {
        // A sky fading from light to darker blue, with a black bird in the middle.
        val sky = Bitmap.createBitmap(160,120,Bitmap.Config.ARGB_8888)
        for (y in 0 until 120) for (x in 0 until 160) sky.setPixel(x,y,Color.rgb(90+y/2,150+y/3,230))
        val clean = sky.copy(Bitmap.Config.ARGB_8888,true)
        for (y in 54..66) for (x in 70..90) sky.setPixel(x,y,Color.BLACK)
        val stroke = Trace(listOf(PointF(.42f,.5f),PointF(.58f,.5f)),.22f)
        val healed = PhotoRender.apply(sky,PhotoEdit.Heal(stroke,null))
        for (at in listOf(80 to 60,72 to 56,89 to 65)) {
            val got = healed.getPixel(at.first,at.second)
            val want = clean.getPixel(at.first,at.second)
            assertTrue(kotlin.math.abs(Color.red(got)-Color.red(want)) < 12)
            assertTrue(kotlin.math.abs(Color.green(got)-Color.green(want)) < 12)
            assertTrue(Color.blue(got) > 215)
        }
        assertEquals(clean.getPixel(10,10),healed.getPixel(10,10))
        assertEquals(clean.getPixel(80,110),healed.getPixel(80,110))
    }

    @Test fun undoRedoAndExportReplayOnSourceAfterSmallPreview() {
        val app = RuntimeEnvironment.getApplication()
        val model = PhotoEditorModel(app)
        fun settled() {
            val deadline = System.nanoTime()+10_000_000_000L
            while (model.busy && System.nanoTime()<deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
            assertFalse(model.busy)
            assertFalse(model.error)
        }
        val file = File(app.cacheDir,"editor-source.png")
        val source = sheet(2000,1000,Color.BLUE)
        file.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.PNG,100,it)) }
        model.open(Uri.fromFile(file),null)
        settled()
        assertTrue(requireNotNull(model.preview).width < source.width)
        val trace = Trace(listOf(PointF(.25f,.5f)),.25f)
        model.edit(PhotoEdit.Ink(trace,Color.WHITE,true,null))
        settled()
        model.history(-1)
        settled()
        assertEquals(Color.BLUE,requireNotNull(model.preview).let { it.getPixel(it.width/4,it.height/2) })
        model.history(1)
        settled()
        model.prepare()
        settled()
        val exported = requireNotNull(model.prepared)
        assertEquals(2000,exported.width)
        assertEquals(1000,exported.height)
        assertEquals(0,Color.alpha(exported.getPixel(500,500)))
        assertEquals(Color.BLUE,exported.getPixel(1500,500))
        model.cancelExport()
    }
}
