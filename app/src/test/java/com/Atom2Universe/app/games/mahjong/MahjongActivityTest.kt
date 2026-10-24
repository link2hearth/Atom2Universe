package com.Atom2Universe.app.games.mahjong

import android.app.Application
import android.content.Context
import android.content.DialogInterface
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.Atom2Universe.app.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class,qualifiers="w412dp-h820dp-port")
class MahjongActivityTest {
    private val prefs get()=RuntimeEnvironment.getApplication().getSharedPreferences("mahjong_garden",Context.MODE_PRIVATE)
    @Before fun reset() {
        prefs.edit().clear().putBoolean("sound",false).putBoolean("effects",false).putBoolean("haptics",false).commit()
    }

    @Test fun switchingModeAndCollectionPreservesEachBoardAndItsUndo() {
        val controller=Robolectric.buildActivity(MahjongActivity::class.java).setup()
        try {
            val activity=controller.get()
            mode(activity,"CLASSIC")
            var current=board(activity)
            current.game.solution.first().forEach(current::chooseTile)
            val classic=current.game.snapshot()
            activity.onBackPressedDispatcher.onBackPressed()
            mode(activity,"TRIPLE")
            current=board(activity)
            current.game.solution.first().take(2).forEach(current::chooseTile)
            val triple=current.game.snapshot()
            assertEquals(2,triple.tray.size)
            activity.onBackPressedDispatcher.onBackPressed()
            mode(activity,"CLASSIC")
            assertEquals(classic,board(activity).game.snapshot())
            action(activity,R.string.mj_collection)
            descendants(content(activity)).first { it.tag=="tab:porcelains" }.performClick()
            descendants(content(activity)).first { it.tag=="porcelain:1" }.performClick()
            activity.onBackPressedDispatcher.onBackPressed()
            assertEquals(classic,board(activity).game.snapshot())
            action(activity,R.string.mj_undo)
            assertEquals(0,board(activity).game.score)
            activity.onBackPressedDispatcher.onBackPressed()
            mode(activity,"TRIPLE")
            assertEquals(triple,board(activity).game.snapshot())
        } finally { controller.pause().stop().destroy() }
        val reopened=Robolectric.buildActivity(MahjongActivity::class.java).setup()
        try {
            mode(reopened.get(),"TRIPLE")
            assertEquals(2,board(reopened.get()).game.tray.size)
            assertEquals(1,MahjongStore(prefs).style)
        } finally { reopened.pause().stop().destroy() }
    }

    @Test fun rewardIsCreditedBeforeAnimationAndNeverTwice() {
        var earned=0
        val first=Robolectric.buildActivity(MahjongActivity::class.java).setup()
        try {
            val activity=first.get()
            mode(activity,"journey")
            val current=board(activity)
            val moves=current.game.solution.flatten()
            moves.forEach(current::chooseTile)
            assertEquals(1,prefs.getInt("wins",0)); assertEquals(2,prefs.getInt("stage",1))
            earned=MahjongStore(prefs).load("journey")!!.reward!!.pearls
            assertTrue(earned>=91)
            assertEquals(earned,prefs.getInt("pearls",0))
            assertEquals(3,MahjongStore(prefs).unlocked.size)
            current.onMove(MahjongGame.Result.MATCHED)
            assertEquals(earned,prefs.getInt("pearls",0))
            assertTrue(MahjongStore(prefs).load("journey")!!.reward!=null)
        } finally { first.pause().stop().destroy() }
        val second=Robolectric.buildActivity(MahjongActivity::class.java).setup()
        try {
            mode(second.get(),"journey")
            assertEquals(MahjongMode.TRIPLE,board(second.get()).game.mode)
            assertEquals(earned,prefs.getInt("pearls",0))
        } finally { second.pause().stop().destroy() }
    }

    @Test fun menuCardsAreSquareAndHelpIsSpecificToTheSelectedMode() {
        checkCardsAndHelp()
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun illustratedCollectionPurchasesAndRendersWithBothAppPalettes() {
        val app=RuntimeEnvironment.getApplication()
        val theme=app.getSharedPreferences("app_theme_prefs",Context.MODE_PRIVATE)
        val old=theme.getString("selected_brightness",null)
        try {
            for(palette in listOf("light","dark")) {
                reset()
                theme.edit().putString("selected_brightness",palette).commit()
                val controller=Robolectric.buildActivity(MahjongActivity::class.java).setup()
                try {
                    val activity=controller.get()
                    render(activity,"menu-$palette")
                    mode(activity,"CLASSIC")
                    render(activity,"board-$palette")
                    action(activity,R.string.mj_collection)
                    assertTrue(descendants(content(activity)).any { it.tag=="texture:23" })
                    descendants(content(activity)).first { it.tag=="tab:porcelains" }.performClick()
                    descendants(content(activity)).first { it.tag=="porcelain:23" }.performClick()
                    var dialog=ShadowDialog.getLatestDialog() as AlertDialog
                    assertFalse(dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled)
                    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                    prefs.edit().putInt("pearls",1000).commit()
                    descendants(content(activity)).first { it.tag=="porcelain:23" }.performClick()
                    dialog=ShadowDialog.getLatestDialog() as AlertDialog
                    assertTrue(dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled)
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                    shadowOf(Looper.getMainLooper()).idle()
                    assertEquals(650,MahjongStore(prefs).pearls)
                    assertEquals(23,MahjongStore(prefs).style)
                    render(activity,"porcelains-$palette")
                    descendants(content(activity)).first { it.tag=="tab:textures" }.performClick()
                    descendants(content(activity)).first { it.tag=="texture:4" }.performClick()
                    dialog=ShadowDialog.getLatestDialog() as AlertDialog
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                    shadowOf(Looper.getMainLooper()).idle()
                    assertEquals(300,MahjongStore(prefs).pearls)
                    assertEquals(4,MahjongStore(prefs).texture)
                    assertEquals(23,MahjongStore(prefs).style)
                    render(activity,"collection-$palette")
                    activity.onBackPressedDispatcher.onBackPressed()
                    val current=board(activity)
                    assertEquals(4,current.texture)
                    current.game.solution.toList().flatten().forEach(current::chooseTile)
                    render(activity,"reward-$palette")
                    for(asset in listOf("porcelain_atlas.webp","treasure_atlas.webp","texture_atlas.webp")) {
                        assertTrue(activity.assets.open("mahjong/$asset").use { it.readBytes().size } < 100_000)
                    }
                } finally { controller.pause().stop().destroy() }
            }
        } finally {
            if(old==null) theme.edit().remove("selected_brightness").commit()
            else theme.edit().putString("selected_brightness",old).commit()
        }
    }

    private fun render(activity:MahjongActivity,name:String) {
        val view=content(activity)
        val density=activity.resources.displayMetrics.density
        val w=(412*density).toInt(); val h=(820*density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
        view.layout(0,0,w,h)
        val bitmap=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val directory=java.io.File("build/mahjong-preview").apply { mkdirs() }
        java.io.File(directory,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }

    @Test
    @Config(qualifiers="w820dp-h412dp-land")
    fun landscapeMenuKeepsSquareCardsAndAccessibleHelp() {
        checkCardsAndHelp()
    }

    private fun checkCardsAndHelp() {
        val controller=Robolectric.buildActivity(MahjongActivity::class.java).setup()
        try {
            val activity=controller.get()
            val content=content(activity)
            val d=activity.resources.displayMetrics.density
            val config=activity.resources.configuration
            val w=(config.screenWidthDp*d).toInt(); val h=(config.screenHeightDp*d).toInt()
            content.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
            content.layout(0,0,w,h)
            val cards=descendants(content).filter { it.tag?.toString()?.startsWith("mode:")==true }.toList()
            assertEquals(8,cards.size)
            for(card in cards) { assertTrue(card.width>0); assertEquals(card.width,card.height) }
            assertFalse(descendants(content).filterIsInstance<TextView>().any { it.text.toString()==activity.getString(R.string.mj_classic_desc) })
            val triple=cards.first { it.tag=="mode:TRIPLE" }
            descendants(triple).first { it.contentDescription==activity.getString(R.string.kit_rules) }.performClick()
            val dialog=ShadowDialog.getLatestDialog() as AlertDialog
            val help=dialog.findViewById<TextView>(android.R.id.message)!!.text.toString()
            assertTrue(help.contains(activity.getString(R.string.mj_triple_desc)))
            assertFalse(help.contains(activity.getString(R.string.mj_connect_desc)))
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            mode(activity,"CLASSIC")
            val boardContent=content(activity)
            boardContent.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
            boardContent.layout(0,0,w,h)
            assertTrue(board(activity).height>h*.45f)
            for(title in listOf(R.string.mj_undo,R.string.mj_hint,R.string.mj_shuffle)) {
                val button=descendants(boardContent).first { it.contentDescription==activity.getString(title) }
                assertTrue(button.width>=48*d); assertTrue(button.height>=48*d)
            }
        } finally { controller.pause().stop().destroy() }
    }

    private fun content(activity:MahjongActivity):View=activity.findViewById(android.R.id.content)
    private fun descendants(view:View):Sequence<View> = sequence {
        yield(view)
        if(view is ViewGroup) for(i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    private fun board(activity:MahjongActivity)=descendants(content(activity)).filterIsInstance<MahjongBoardView>().single()
    private fun mode(activity:MahjongActivity,slot:String) {
        descendants(content(activity)).first { it.tag=="mode:$slot" }.performClick()
        awaitMahjongWork(activity)
    }
    private fun action(activity:MahjongActivity,title:Int) {
        descendants(content(activity)).first { it.contentDescription==activity.getString(title) }.performClick()
    }
}
