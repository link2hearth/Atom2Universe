package com.Atom2Universe.app.games.caves.render

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import com.Atom2Universe.app.games.caves.entity.ProjectileKind

/** Géométrie facettée partagée FPS/TPS. +Y vers le haut, -Z vers la cible.
 * Le tampon est réutilisé : aucune allocation native à chaque image. */
internal class HeldEquipmentMesh {
    val vertices = FloatArray(100_000)
    val buffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    var count = 0
        private set
    fun clear() { count = 0 }

    private fun vertex(x: Float, y: Float, z: Float, color: Int, shade: Float) {
        vertices[count++] = x; vertices[count++] = y; vertices[count++] = z
        vertices[count++] = ((color shr 16) and 255) / 255f * shade
        vertices[count++] = ((color shr 8) and 255) / 255f * shade
        vertices[count++] = (color and 255) / 255f * shade
    }

    /** Cylindre effilé à huit faces, également utilisé pour cordes et articulations. */
    fun rod(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
            radius: Float, color: Int, endRadius: Float = radius) {
        val dx = bx-ax; val dy = by-ay; val dz = bz-az
        val len = sqrt(dx*dx+dy*dy+dz*dz)
        if (len < 0.00001f) return
        val nx = dx/len; val ny = dy/len; val nz = dz/len
        val ux: Float; val uy: Float; val uz: Float
        if (abs(ny) < 0.9f) {
            val l = sqrt(nx*nx+nz*nz); ux = -nz/l; uy = 0f; uz = nx/l
        } else {
            val l = sqrt(ny*ny+nz*nz); ux = 0f; uy = nz/l; uz = -ny/l
        }
        val vx = ny*uz-nz*uy; val vy = nz*ux-nx*uz; val vz = nx*uy-ny*ux
        for (i in 0 until 8) {
            val a = i * PI.toFloat()/4f; val b = (i+1)*PI.toFloat()/4f
            val rx = ux*cos(a)+vx*sin(a); val ry = uy*cos(a)+vy*sin(a); val rz = uz*cos(a)+vz*sin(a)
            val sx = ux*cos(b)+vx*sin(b); val sy = uy*cos(b)+vy*sin(b); val sz = uz*cos(b)+vz*sin(b)
            val shade = (0.74f + 0.20f*(ry+sy)*0.5f + 0.08f*(rx+sx)*0.5f).coerceIn(0.45f, 1f)
            fun p(end: Boolean, next: Boolean) {
                val r = if (end) endRadius else radius
                vertex((if(end) bx else ax)+(if(next) sx else rx)*r,
                    (if(end) by else ay)+(if(next) sy else ry)*r,
                    (if(end) bz else az)+(if(next) sz else rz)*r, color, shade)
            }
            p(false,false); p(false,true); p(true,true)
            p(false,false); p(true,true); p(true,false)
            vertex(ax,ay,az,color,0.65f); p(false,true); p(false,false)
            vertex(bx,by,bz,color,0.9f); p(true,false); p(true,true)
        }
    }

    fun box(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, color: Int) {
        val points = arrayOf(floatArrayOf(x-w,y-h,z-d),floatArrayOf(x+w,y-h,z-d),
            floatArrayOf(x+w,y+h,z-d),floatArrayOf(x-w,y+h,z-d),
            floatArrayOf(x-w,y-h,z+d),floatArrayOf(x+w,y-h,z+d),
            floatArrayOf(x+w,y+h,z+d),floatArrayOf(x-w,y+h,z+d))
        val faces = arrayOf(intArrayOf(0,3,2,1),intArrayOf(4,5,6,7),intArrayOf(0,4,7,3),
            intArrayOf(1,2,6,5),intArrayOf(3,7,6,2),intArrayOf(0,1,5,4))
        val shades = floatArrayOf(.72f,.88f,.68f,.82f,1f,.5f)
        for (i in faces.indices) for (j in intArrayOf(0,1,2,0,2,3)) {
            val p = points[faces[i][j]]; vertex(p[0],p[1],p[2],color,shades[i])
        }
    }

    fun stone(x: Float, y: Float, z: Float, size: Float = .065f) {
        rod(x,y-size*.65f,z,x+.012f,y,z-.008f,size*.55f,0x858C94,size)
        rod(x+.012f,y,z-.008f,x-.008f,y+size*.75f,z,size,0x858C94,size*.35f)
    }

    fun hand(x: Float, y: Float, z: Float, open: Float = 0f) {
        box(x,y,z,.044f,.054f,.035f,0xD5A17C)
        for (i in 0..3) {
            val fy = y-.033f+i*.022f
            rod(x-.018f,fy,z-.025f,x-.028f-open*.035f,fy,z-.056f-open*.045f,.011f,0xE3B28D)
        }
        rod(x+.033f,y+.029f,z,x+.050f,y-.010f,z-.030f,.019f,0xD5A17C)
    }

    /** Pose complète utilisée directement par les deux caméras, inspectable sans OpenGL. */
    fun pose(type: String?, rock: Boolean, fps: Boolean, charge: Float, rockCharge: Float,
             release: Float, loaded: Boolean, accent: Int, reload: Float = 0f, shotIndex: Int = 0) {
        clear()
        val m = this
        if(type=="dual_pistols") {
            for(side in listOf(-1f,1f)) {
                rod(side*.24f,if(fps) -.24f else -.03f,if(fps) .10f else .43f,side*.16f,-.06f,.02f,.06f,0x435B78,.038f)
                hand(side*.16f,-.025f,.02f)
            }
            weapon(type,charge,release,loaded,accent,reload=reload,shotIndex=shotIndex)
            return
        }
        val follow = if (release >= 0f) sin((release/.55f).coerceIn(0f,1f)*PI.toFloat()) else 0f
        // Les épaules restent fixes pendant le lancer : seuls coude et main se déplacent.
        val hx = if(rock) rockCharge*.065f-follow*.10f else 0f
        val hy = if(rock) rockCharge*.20f-follow*.10f else 0f
        val hz = if(rock) rockCharge*.26f-follow*.24f else 0f
        // En FPS on ne montre que le bout des manches et les avant-bras.
        // Les épaules TPS (+.43 en Z) grossissaient démesurément près de la caméra.
        val drawing = type == "sling" || type == "bow"
        val gripElbowX = if(fps && drawing) -.08f else .11f
        val shoulderY = if(fps) -.24f else -.03f
        val shoulderZ = if(fps) .10f else .43f
        val elbowZ = if(fps) .07f else .23f
        val elbowY = if(fps) -.14f else -.20f
        m.rod(if(fps) (if(drawing) -.13f else .13f) else 0f,shoulderY,shoulderZ,gripElbowX,elbowY+hy*.5f,elbowZ+hz*.5f,.069f,0x435B78,.055f)
        m.rod(gripElbowX,elbowY+hy*.5f,elbowZ+hz*.5f,hx,hy-.05f,hz+.035f,.053f,0xD5A17C,.038f)
        m.hand(hx,hy-.025f,hz+.02f,if(rock && release >= 0f) follow else 0f)
        if (rock) {
            if (!fps) {
                m.rod(-.54f,-.03f,.43f,-.57f,-.32f,.43f,.07f,0x435B78,.055f)
                m.rod(-.57f,-.32f,.43f,-.58f,-.56f,.40f,.052f,0xD5A17C,.037f)
                m.hand(-.58f,-.59f,.40f)
            }
            if (release < 0f && loaded) m.stone(hx,hy+.035f,hz-.035f)
        } else if (type != null) {
            val pulling = type == "sling" || type == "bow"
            val handY = if(type == "sling") .28f else if(pulling) 0f else -.035f
            val handZ = if(pulling) pullBack(type,charge,release)+(if(type == "sling") .030f else .025f) else -.19f
            val handX = if(type == "sling") .035f else if(pulling) .065f else -.025f
            val leftElbowX = if(fps) (if(pulling) .26f else -.24f) else -.38f
            if (!(fps && type == "sling")) {
                m.rod(if(fps) (if(pulling) .34f else -.30f) else -.52f,shoulderY,shoulderZ,leftElbowX,elbowY,elbowZ,.069f,0x435B78,.055f)
                m.rod(leftElbowX,elbowY,elbowZ,handX,handY,handZ,.052f,0xD5A17C,.034f)
                if (!pulling || charge <= .01f) m.hand(handX,handY,handZ)
            }
            m.weapon(type,charge,release,loaded,accent, showSlingHand = !fps,reload=reload,shotIndex=shotIndex)
        }
    }

    /** Passe translucide séparée : la poche et les élastiques restent opaques. */
    fun slingDrawHand(charge: Float, release: Float) {
        clear()
        hand(.035f,.28f,pullBack("sling",charge,release)+.030f)
    }

    // Main, poche et corde partagent exactement le même point de traction.
    // La course reste lisible sans rapprocher la main du plan de projection FPS.
    private fun pullBack(type: String, charge: Float, release: Float): Float {
        val snap = if (release >= 0f) sin(release*38f)*exp(-release*10f)*.045f else 0f
        return (if(type == "sling") .035f+charge*.20f else .09f+charge*.24f)+snap
    }

    /** Grip à l'origine. Les pièces mobiles utilisent le même repère dans les deux vues. */
    fun weapon(type: String, charge: Float, release: Float, loaded: Boolean, accent: Int, showSlingHand: Boolean = true, reload: Float = 0f, shotIndex: Int = 0) {
        val wood = 0x85502C; val leather = 0x392B28; val metal = 0xA9BBC7
        val snap = if (release >= 0f) sin(release*38f)*exp(-release*10f)*.085f else 0f
        when (type) {
            "shotgun", "smg", "lever_rifle" -> longGun(type,release,reload,accent)
            "dual_pistols" -> {
                for(side in 0..1) {
                    val start=count
                    weapon("gun",0f,if(side==shotIndex%2) release else -1f,loaded,accent,reload=reload)
                    for(i in start until count step 6) vertices[i]+=if(side==0) -.16f else .16f
                }
            }
            "sling" -> {
                rod(0f,-.12f,0f,0f,.09f,0f,.031f,wood,.043f)
                for (side in listOf(-1f,1f)) {
                    rod(0f,.07f,0f,side*.11f,.21f,-.018f,.039f,wood,.030f)
                    rod(side*.11f,.21f,-.018f,side*.14f,.34f,-.035f,.030f,wood,.023f)
                    rod(side*.14f,.305f,-.035f,side*.14f,.343f,-.035f,.028f,accent)
                }
                for (i in 0..5) rod(0f,-.11f+i*.026f,0f,0f,-.095f+i*.026f,0f,.033f,leather)
                val pull = pullBack(type,charge,release)
                for (side in listOf(-1f,1f)) {
                    // Deux bandes épaisses restent attachées aux bouts de la fourche.
                    for (offset in listOf(-.009f,.009f)) rod(side*.14f,.327f+offset,-.025f,
                        side*.027f,.29f+offset,pull,.007f,0xD6AA51,.005f)
                }
                box(0f,.29f,pull,.039f,.027f,.013f,leather)
                if (loaded) stone(0f,.29f,pull-.034f,.036f)
                if (showSlingHand && charge > .01f) hand(.035f,.28f,pull+.030f)
            }
            "bow" -> {
                rod(0f,-.10f,0f,0f,.10f,0f,.029f,leather)
                for (side in listOf(-1f,1f)) {
                    var y = side*.10f; var z = 0f
                    for (i in 1..7) {
                        val t = i/7f; val yy = side*(.10f+t*.48f)
                        val zz = -.14f*sin(t*PI.toFloat()) + charge*.065f*t
                        rod(0f,y,z,0f,yy,zz,.028f*(1f-t*.62f),wood,.023f*(1f-t*.62f))
                        y=yy; z=zz
                    }
                    rod(0f,side*.105f,0f,0f,side*.15f,-.04f,.032f,accent)
                    rod(0f,y,z,0f,0f,pullBack(type,charge,release),.004f,0xE4D8AE)
                }
                val pull = pullBack(type,charge,release)
                if (loaded) arrow(.035f,0f,pull)
                if (charge > .01f) hand(.065f,0f,pull+.025f)
            }
            "crossbow" -> {
                box(0f,.035f,-.13f,.038f,.045f,.27f,wood)
                rod(0f,-.13f,.075f,0f,.01f,.025f,.032f,leather)
                box(0f,.018f,.13f,.058f,.055f,.065f,wood)
                box(0f,.082f,-.13f,.015f,.008f,.265f,metal)
                for (side in listOf(-1f,1f)) {
                    rod(0f,.055f,-.34f,side*.19f,.055f,-.30f,.026f,metal,.019f)
                    rod(side*.19f,.055f,-.30f,side*.31f,.055f,-.22f+charge*.035f,.019f,0x465C6A,.011f)
                    rod(side*.31f,.055f,-.22f+charge*.035f,0f,.068f,
                        if(loaded) .07f+snap else -.24f+snap,.005f,0xDDCF9D)
                    box(side*.045f,.035f,-.25f,.011f,.047f,.030f,accent)
                }
                if (loaded) arrow(0f,.102f,.09f)
                rod(-.047f,-.03f,-.055f,-.047f,-.075f,.025f,.009f,metal)
                rod(-.047f,-.075f,.025f,0f,-.075f,.055f,.009f,metal)
            }
            "gun" -> {
                val kick = if(release >= 0f) sin((release/.16f).coerceIn(0f,1f)*PI.toFloat())*.055f else 0f
                rod(0f,-.15f,.055f,0f,.012f,.0f,.045f,leather,.038f)
                val magDrop=sin(reload*PI.toFloat())*.20f
                box(0f,-.105f-magDrop,.038f,.027f,.065f,.025f,0x202D36)
                box(0f,.045f,-.08f,.047f,.040f,.16f,0x354552)
                box(0f,.094f,-.08f+kick,.042f,.017f,.16f,metal)
                rod(0f,.049f,-.20f,0f,.049f,-.30f,.027f,0x647883)
                rod(0f,.049f,-.301f,0f,.049f,-.304f,.018f,0x101820)
                box(0f,.12f,-.205f,.008f,.012f,.013f,accent)
                for (side in listOf(-1f,1f)) box(side*.027f,.12f,.035f+kick,.008f,.01f,.015f,0x26343E)
                for (i in 0..4) box(.048f,.05f,.025f-i*.018f+kick,.003f,.027f,.004f,0x15232C)
                rod(0f,-.014f,-.13f,0f,-.064f,-.10f,.009f,metal)
                rod(0f,-.064f,-.10f,0f,-.064f,.008f,.009f,metal)
                rod(0f,-.007f,-.060f,0f,-.039f,-.040f,.009f,accent)
                if (release in 0f..0.085f) {
                    val flash = 1f-release/.085f
                    rod(0f,.049f,-.31f,0f,.049f,-.31f-.20f*flash,.045f*flash,0xFFD17A,0f)
                    rod(0f,.049f,-.31f,0f,.049f,-.43f*flash-.31f*(1f-flash),.021f*flash,0xFFFFDC,0f)
                }
            }
        }
    }

    private fun muzzle(x: Float,y: Float,z: Float,release: Float) {
        if(release !in 0f.. .075f) return
        val f=1f-release/.075f
        rod(x,y,z,x,y,z-.16f*f,.04f*f,0xFFD576,0f)
        rod(x,y,z,x,y,z-.09f*f,.017f*f,0xFFF5D7,0f)
    }

    private fun longGun(type: String, release: Float, reload: Float, accent: Int) {
        val pump=if(release>=0f) sin((release/.65f).coerceIn(0f,1f)*PI.toFloat()) else 0f
        val drop=sin(reload*PI.toFloat())
        val wood=if(type=="shotgun") 0x79472C else 0xA16A37
        val steel=0x687D8D
        val front=if(type=="smg") -.40f else -.68f
        // Carcasse, canon octogonal, bouche noire et guidon contrasté.
        box(0f,.065f,-.13f,.044f,.045f,.15f,0x30414E)
        rod(0f,.083f,-.23f,0f,.083f,front,.023f,steel,.018f)
        rod(0f,.083f,front-.001f,0f,.083f,front-.004f,.012f,0x10171D)
        box(0f,.114f,front+.03f,.007f,.014f,.012f,accent)
        rod(0f,-.13f,.055f,0f,.027f,0f,.034f,0x352C27)
        box(0f,.045f,.13f,.05f,.07f,.10f,if(type=="smg") 0x25333D else wood)
        box(0f,.025f,.235f,.055f,.08f,.016f,0x1C252B)
        for(side in listOf(-1f,1f)) {
            box(side*.034f,.120f,-.025f,.008f,.012f,.015f,0x202C34)
            rod(side*.046f,.064f,-.12f,side*.047f,.064f,-.045f+if(type=="lever_rifle") pump*.035f else 0f,.009f,accent)
        }
        if(type=="smg") {
            // Chargeur tombant, rail, ouïes, crosse métallique compacte.
            box(0f,-.13f-drop*.24f,-.14f,.026f,.13f,.033f,0x25343D)
            for(i in 0..4) box(.028f,-.035f-i*.043f-drop*.24f,-.14f,.003f,.005f,.034f,steel)
            for(i in 0..4) box(0f,.117f,-.10f-i*.028f,.040f,.007f,.008f,0x93A2AD)
            rod(0f,.018f,-.26f,0f,.018f,-.35f,.038f,0x1E2A32)
        } else if(type=="shotgun") {
            rod(0f,.025f,-.20f,0f,.025f,-.61f,.021f,steel)
            val z=-.35f+pump*.10f
            rod(0f,.023f,z-.07f,0f,.023f,z+.06f,.047f,wood)
            for(i in 0..5) rod(0f,.023f,z-.065f+i*.022f,0f,.023f,z-.057f+i*.022f,.050f,0x392F28)
            // Cartouchière sur la crosse et cartouche visible pendant la recharge.
            for(i in 0..3) rod(.056f,-.015f,.08f+i*.032f,.056f,.07f,.08f+i*.032f,.011f,0xA04431)
            if(reload>0f) rod(.085f,-.04f-drop*.05f,-.09f,.085f,.018f-drop*.05f,-.09f,.013f,0xD08C45)
        } else {
            rod(0f,.025f,-.20f,0f,.025f,-.47f,.034f,wood)
            // Levier sous le pontet : rotation pendant le réarmement.
            val y=-.07f-pump*.085f
            rod(0f,-.015f,-.09f,0f,y,-.11f,.009f,accent)
            rod(0f,y,-.11f,0f,y,.055f,.009f,accent)
            rod(0f,y,.055f,0f,-.02f,.035f,.009f,accent)
            box(0f,.126f,-.12f,.01f,.009f,.03f,accent)
        }
        muzzle(0f,.083f,front-.006f,release)
    }

    /** Projectiles en unités monde, orientés selon la vitesse réelle (chute comprise). */
    fun projectile(kind: ProjectileKind,x: Float,y: Float,z: Float,dx: Float,dy: Float,dz: Float) {
        val len=sqrt(dx*dx+dy*dy+dz*dz).coerceAtLeast(.0001f)
        val nx=dx/len;val ny=dy/len;val nz=dz/len
        when(kind) {
            ProjectileKind.ROCK -> stone(x,y,z,.048f)
            ProjectileKind.ARROW,ProjectileKind.BOLT -> {
                val length=if(kind==ProjectileKind.ARROW) .57f else .34f
                val bx=x-nx*length;val by=y-ny*length;val bz=z-nz*length
                rod(bx,by,bz,x-nx*.045f,y-ny*.045f,z-nz*.045f,.007f,if(kind==ProjectileKind.ARROW) 0xBC9256 else 0x829DA9)
                rod(x-nx*.045f,y-ny*.045f,z-nz*.045f,x,y,z,.020f,0xCAD8DE,0f)
                val sideLen=sqrt(nx*nx+nz*nz)
                val sx=if(sideLen>.001f) -nz/sideLen else 1f
                val sz=if(sideLen>.001f) nx/sideLen else 0f
                for(side in listOf(-1f,1f)) rod(bx,by,bz,bx+nx*.075f+side*sx*.035f,by+ny*.075f,bz+nz*.075f+side*sz*.035f,.012f,if(kind==ProjectileKind.ARROW) 0xD6EAD2 else 0x67BFD0,.003f)
            }
            ProjectileKind.BULLET,ProjectileKind.PELLET -> {
                val length=if(kind==ProjectileKind.BULLET) .20f else .075f
                rod(x-nx*length,y-ny*length,z-nz*length,x,y,z,if(kind==ProjectileKind.BULLET) .009f else .007f,0xFFE4A0,.003f)
            }
            else -> Unit
        }
    }

    private fun arrow(x: Float, y: Float, rear: Float) {
        rod(x,y,rear,x,y,rear-.59f,.006f,0xBE985D)
        rod(x,y,rear-.59f,x,y,rear-.65f,.019f,0xC7D5DB,0f)
        box(x,y,rear-.045f,.026f,.002f,.038f,0xD6E4DB)
        box(x,y,rear-.045f,.002f,.026f,.038f,0xD6E4DB)
    }
}
