package com.Atom2Universe.app.science.botany

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Fast raster guard: no empty/clipped organ, reproducible plates and distinct diagnostic features. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BotanyRenderingTest {
    private fun render(id:String,view:PlantView):Bitmap = Bitmap.createBitmap(340,370,Bitmap.Config.ARGB_8888).also { bmp->
        val canvas=Canvas(bmp);canvas.translate(20f,20f)
        PlantPainter(canvas,BotanyCatalog.get(id)!!).draw(view)
    }
    private fun pixels(b:Bitmap)=IntArray(b.width*b.height).also{b.getPixels(it,0,b.width,0,0,b.width,b.height)}

    @Test fun chaqueOrganeEstVisibleEtResteDansLeCadre() {
        val failures=mutableListOf<String>()
        val directory=File("build/reports/botany-rendering").apply{mkdirs()}
        for(view in PlantView.entries) {
            // Diagnostic contact sheets accompany the bounds assertion, useful when adding catalogue entries.
            val sheet=Bitmap.createBitmap(340*6,390*((BotanyCatalog.species.size+5)/6),Bitmap.Config.ARGB_8888)
            val c=Canvas(sheet);c.drawColor(0xfff4f0e3.toInt())
            val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.DKGRAY;textSize=15f}
            for((i,p) in BotanyCatalog.species.withIndex()) {
                val b=render(p.id,view);val data=pixels(b)
                val nonEmpty=data.count{Color.alpha(it)>0}
                if(nonEmpty<150)failures+="${p.id}/$view empty ($nonEmpty)"
                var outside=0
                for(y in 0 until b.height)for(x in 0 until b.width) {
                    if((x<20 || x>=320 || y<20 || y>=320) && Color.alpha(data[y*b.width+x])>0)outside++
                }
                if(outside>0)failures+="${p.id}/$view outside plate ($outside pixels)"
                c.drawBitmap(b,(i%6)*340f,(i/6)*390f,null)
                c.drawText("${p.id} / ${view.name}",(i%6)*340f+15,(i/6)*390f+365,text)
                b.recycle()
            }
            File(directory,"${view.name.lowercase()}.png").outputStream().use{sheet.compress(Bitmap.CompressFormat.PNG,100,it)}
            sheet.recycle()
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }

    @Test fun caracteresDesComparaisonsNeSontPasDesImagesIdentiques() {
        for((a,b,view) in listOf(
            Triple("glycine_max","arachis",PlantView.HABIT),Triple("glycine_max","arachis",PlantView.LEAF),
            Triple("glycine_max","arachis",PlantView.FRUIT),Triple("allium_cepa","brassica_oleracea",PlantView.FLOWER),
            Triple("olea","coffea",PlantView.HABIT),Triple("persica","armeniaca",PlantView.LEAF),
            Triple("olea","coffea",PlantView.FLOWER),Triple("persica","armeniaca",PlantView.FRUIT),
            Triple("coffea","citrus_maxima",PlantView.FRUIT),
            Triple("lycopersicum","tuberosum",PlantView.LEAF),Triple("lycopersicum","capsicum",PlantView.FLOWER),
            Triple("tuberosum","melongena",PlantView.FRUIT),Triple("nigrum","lycopersicum",PlantView.HABIT),
            Triple("sequoiadendron","sequoia",PlantView.HABIT), Triple("sequoiadendron","sequoia",PlantView.LEAF),
            Triple("sequoiadendron","pseudotsuga",PlantView.FRUIT), Triple("echinacea","rudbeckia",PlantView.FLOWER),
            Triple("robur","petraea",PlantView.FRUIT),Triple("fagus","carpinus",PlantView.LEAF),
            Triple("abies","picea",PlantView.FRUIT),Triple("calluna","erica",PlantView.LEAF),
            Triple("dactylis","lolium",PlantView.FLOWER),Triple("robur","petraea",PlantView.LEAF),
            Triple("campestre","platanoides",PlantView.LEAF),Triple("abies","picea",PlantView.HABIT),
            Triple("picea","larix",PlantView.HABIT),Triple("betula","pinus",PlantView.HABIT),
            Triple("calluna","erica",PlantView.FLOWER),Triple("viola","papaver",PlantView.FLOWER),
            Triple("digitalis","campanula",PlantView.FLOWER),Triple("castanea","aesculus",PlantView.FRUIT),
            Triple("corylus","fagus",PlantView.FRUIT),Triple("daucus","anthriscus",PlantView.FRUIT),
            Triple("plantago_lanceolata","papaver",PlantView.FRUIT),
            Triple("cardamine","capsella",PlantView.FRUIT),Triple("ulmus_minor","fraxinus",PlantView.FRUIT),
            Triple("malva","geranium",PlantView.FRUIT),Triple("matricaria","cichorium",PlantView.FLOWER),
            Triple("salix_alba","salix_caprea",PlantView.LEAF),Triple("malva","geranium",PlantView.LEAF),
            Triple("adansonia","vachellia_tortilis",PlantView.HABIT),Triple("vachellia_tortilis","faidherbia",PlantView.FRUIT),
            Triple("calotropis","hibiscus_trionum",PlantView.FLOWER),Triple("cenchrus_ciliaris","hyparrhenia",PlantView.FLOWER))) {
            val aa=render(a,view);val bb=render(b,view)
            val ap=pixels(aa);val bp=pixels(bb)
            assertTrue("$a/$b",ap.indices.count{ap[it]!=bp[it]}>1000)
            aa.recycle();bb.recycle()
        }
        val a=render("robur",PlantView.HABIT);val b=render("robur",PlantView.HABIT)
        assertArrayEquals(pixels(a),pixels(b));a.recycle();b.recycle()
    }

    @Test fun lesProfilsDOrganesSontReutilisablesSansIdentifiantConnu() {
        for(id in listOf("papaver","viola","digitalis","corylus","pinus","plantago_major",
            "ulmus_minor","cardamine","capsella","cichorium","silene_dioica","malva","veronica","vaccinium","geranium","genista") + (AfricanDrylandsCatalog.species + NorthAmericanCatalog.species + ModelPlantsCatalog.species + NightshadesCatalog.species + OrchardCatalog.species + FoodCropsCatalog.species).map{it.id}) {
            val species=BotanyCatalog.get(id)!!
            val views=if((FieldBotanyCatalog.species+AfricanDrylandsCatalog.species+NorthAmericanCatalog.species+ModelPlantsCatalog.species+NightshadesCatalog.species+OrchardCatalog.species+FoodCropsCatalog.species).any{it.id==id})PlantView.entries else listOf(PlantView.FLOWER,PlantView.FRUIT)
            for(view in views) {
                val original=render(id,view)
                val future=Bitmap.createBitmap(340,370,Bitmap.Config.ARGB_8888)
                val canvas=Canvas(future);canvas.translate(20f,20f)
                PlantPainter(canvas,species.copy(id="future_taxon")).draw(view)
                assertArrayEquals("$id/$view depends on drawing traits, not an ID",pixels(original),pixels(future))
                original.recycle();future.recycle()
            }
        }
    }

    @Test fun premierLotDeReferencesIllustrees() {
        referenceSheet(ModelPlantsCatalog.species,"references-lot-1.png")
    }

    @Test fun deuxiemeLotDeReferencesIllustrees() {
        referenceSheet(NightshadesCatalog.species,"references-lot-2.png")
    }

    @Test fun troisiemeLotDeReferencesIllustrees() {
        referenceSheet(OrchardCatalog.species,"references-lot-3.png")
    }

    @Test fun quatriemeLotDeReferencesIllustrees() {
        referenceSheet(FoodCropsCatalog.species,"references-lot-4.png")
    }

    private fun referenceSheet(species:List<PlantSpecies>,filename:String) {
        val sheet=Bitmap.createBitmap(340*4,370*species.size,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(sheet);canvas.drawColor(0xfff4f0e3.toInt())
        val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.DKGRAY;textSize=15f}
        for((row,p) in species.withIndex())for((col,view) in PlantView.entries.withIndex()) {
            val first=render(p.id,view);val second=render(p.id,view)
            assertArrayEquals("${p.id}/$view",pixels(first),pixels(second))
            canvas.drawBitmap(first,col*340f,row*370f,null)
            canvas.drawText("${p.id} / $view",col*340f+20,row*370f+355,text)
            first.recycle();second.recycle()
        }
        val file=File("build/reports/botany-rendering/$filename")
        file.parentFile!!.mkdirs();file.outputStream().use{sheet.compress(Bitmap.CompressFormat.PNG,100,it)}
        sheet.recycle()
    }
}
