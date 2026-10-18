package com.Atom2Universe.app.games.golf.classic.render

import kotlin.math.*

/** Saved independently of the round; all combinations share the same articulated rig. */
data class GolferAppearance(val female: Boolean = false, val outfit: Int = 0, val skin: Int = 0)

internal data class GVec(val x: Float, val y: Float, val z: Float) {
    operator fun plus(b: GVec) = GVec(x+b.x,y+b.y,z+b.z)
    operator fun minus(b: GVec) = GVec(x-b.x,y-b.y,z-b.z)
    operator fun times(s: Float) = GVec(x*s,y*s,z*s)
    fun dot(b: GVec) = x*b.x+y*b.y+z*b.z
    fun cross(b: GVec) = GVec(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
    fun unit() = this * (1f/sqrt(dot(this)).coerceAtLeast(.00001f))
}

/** Rigid transforms, column-major like GLES, kept Android-free for geometry/pose validation. */
internal class GolferPose {
    val matrices = FloatArray(COUNT*16)
    private val up = GVec(0f,1f,0f)
    private val right = GVec(1f,0f,0f)

    private fun basis(bone: Int, origin: GVec, x: GVec, y: GVec, z: GVec) {
        val o=bone*16
        matrices[o]=x.x; matrices[o+1]=x.y; matrices[o+2]=x.z; matrices[o+3]=0f
        matrices[o+4]=y.x; matrices[o+5]=y.y; matrices[o+6]=y.z; matrices[o+7]=0f
        matrices[o+8]=z.x; matrices[o+9]=z.y; matrices[o+10]=z.z; matrices[o+11]=0f
        matrices[o+12]=origin.x; matrices[o+13]=origin.y; matrices[o+14]=origin.z; matrices[o+15]=1f
    }

    private fun rotation(bone: Int, p: GVec, yaw: Float=0f, lean: Float=0f, roll: Float=0f) {
        val cy=cos(yaw); val sy=sin(yaw); val cx=cos(lean); val sx=sin(lean)
        val x=GVec(cy,0f,-sy); val y=GVec(sy*sx,cx,cy*sx); val z=GVec(sy*cx,-sx,cy*cx)
        basis(bone,p,x*cos(roll)+y*sin(roll),y*cos(roll)-x*sin(roll),z)
    }

    fun point(bone: Int, x: Float, y: Float, z: Float): GVec {
        val o=bone*16
        return GVec(matrices[o]*x+matrices[o+4]*y+matrices[o+8]*z+matrices[o+12],
            matrices[o+1]*x+matrices[o+5]*y+matrices[o+9]*z+matrices[o+13],
            matrices[o+2]*x+matrices[o+6]*y+matrices[o+10]*z+matrices[o+14])
    }

    private fun segment(bone: Int, a: GVec, b: GVec, hint: GVec=right) {
        val y=(b-a).unit()
        var z=hint.cross(y)
        if(z.dot(z)<.001f) z=up.cross(y)
        z=z.unit()
        basis(bone,a,y.cross(z).unit(),y,z)
    }

    /** Two-bone IK: exact limb lengths, stable bend plane, no elbows glued to the grip. */
    private fun joint(a: GVec, b: GVec, upper: Float, lower: Float, pole: GVec): GVec {
        val delta=b-a
        val d=sqrt(delta.dot(delta)).coerceIn(.001f,upper+lower-.0001f)
        val direction=delta.unit()
        val along=(upper*upper-lower*lower+d*d)/(2f*d)
        val offset=pole-a
        val bend=(offset-direction*offset.dot(direction)).unit()
        return a+direction*along+bend*sqrt(max(0f,upper*upper-along*along))
    }

    /**
     * +z faces the ball, -x is the target (right-handed golfer).
     * Pull already encodes power before TOP; release starts at TOP at that SAME power.
     * Impact is shared with the shot clock, so short shots cannot snap to a full backswing.
     */
    fun update(progress: Float, power: Float, putting: Boolean, time: Float,
               female: Boolean, showcase: Boolean=false, leadGround: Float=0f, trailGround: Float=0f,
               ballGround: Float=0f) {
        val p=progress.coerceAtMost(1f)
        val amplitude=power.coerceIn(0f,1f)
        val back: Float
        val follow: Float
        when {
            p<0f -> { back=0f; follow=0f }
            p<=TOP -> { back=(p/TOP).coerceIn(0f,amplitude); follow=0f }
            p<IMPACT -> {
                val t=(p-TOP)/(IMPACT-TOP)
                back=amplitude*(1f-t*t); follow=0f
            }
            else -> {
                val t=((p-IMPACT)/(1f-IMPACT)).coerceIn(0f,1f)
                // Carry speed THROUGH contact, then ease into a balanced held finish.
                back=0f; follow=(1f-(1f-t).pow(8))*amplitude
            }
        }
        val full=if(putting) .16f else 1f
        val turn=(back*.82f-follow*1.25f)*full
        val shift=(back*.035f-follow*.095f)*full
        val breath=if(p<0f) sin(time*1.8f)*.004f else 0f
        val stand=if(showcase) 1f else 0f
        val pelvis=GVec(shift,.88f+follow*.035f*full+breath-min(abs(leadGround-trailGround)*.5f,.12f),-.025f)
        rotation(HIPS,pelvis,turn*.58f)
        rotation(CHEST,pelvis+GVec(0f,.095f,0f),turn,.19f*(1f-stand)-follow*.12f*full,
            (back*.06f+follow*.08f)*full)
        val neck=point(CHEST,0f,.43f,0f)
        // Keep looking at the ball until contact; then turn toward the flight.
        rotation(HEAD,neck,turn*.23f-follow*.45f*full,.18f*(1f-stand)-follow*.12f*full)
        val pony=point(HEAD,0f,.20f,-.15f)
        rotation(HAIR,pony,turn*.23f-follow*.45f*full,
            .10f+sin(time*2.2f)*.018f+back*.10f+follow*.08f)

        val stance=if(putting) .17f else .225f
        for(side in 0..1) {
            val sign=if(side==0) -1f else 1f
            val heel=if(side==1) follow*.105f*full else 0f
            val ground=if(side==0) leadGround else trailGround
            rotation(FOOT_L+side,GVec(sign*stance,ground+heel,.015f),sign*.15f,
                asin((heel/.20f).coerceIn(0f,.8f)))
            val ankle=point(FOOT_L+side,0f,.105f,0f)
            val hip=point(HIPS,sign*(if(female) .125f else .12f),-.01f,0f)
            val knee=joint(hip,ankle,.405f,.405f,GVec(sign*stance,.47f,.55f))
            segment(THIGH_L+side,hip,knee)
            segment(SHIN_L+side,knee,ankle)
        }

        // Hands and club move in one inclined swing plane. Club length stays constant.
        val swingAngle=back*(if(putting) .26f else 1.83f)-follow*(if(putting) .33f else 2.05f)
        val idle=if(p<0f&&!showcase) sin(time*1.4f)*.012f else 0f
        val a=swingAngle+idle
        var hand=GVec(sin(a)*.42f+shift*.3f,.94f+ballGround+(1f-cos(a))*.42f,
            .47f-abs(sin(a))*.12f)
        val wrist=(back*.65f-follow*.25f)*full
        val clubAngle=a+wrist
        val direction=GVec(sin(clubAngle)*.87f,-cos(clubAngle)*.87f,.493f).unit()
        val shoulderWidth=if(female) .158f else .190f
        // Keep both grips within reach even at maximum rotation or on a sloping lie.
        // Move the complete hand/club assembly, never stretch the forearms apart.
        repeat(4) {
            for(side in 0..1) {
                val sign=if(side==0) -1f else 1f
                val shoulder=point(CHEST,sign*shoulderWidth,.31f,.055f)
                val offset=direction*(if(side==0) .015f else .090f)+GVec(sign*.022f,0f,0f)
                val reach=hand+offset-shoulder
                val length=sqrt(reach.dot(reach))
                if(length>ARM_REACH) hand=shoulder+reach*(ARM_REACH/length)-offset
            }
        }
        val clubX=GVec(1f,0f,0f)
        segment(CLUB,hand,hand+direction,clubX)
        for(side in 0..1) {
            val sign=if(side==0) -1f else 1f
            val shoulder=point(CHEST,sign*shoulderWidth,.31f,.055f)
            val grip=hand+direction*(if(side==0) .015f else .090f)+GVec(sign*.022f,0f,0f)
            val elbow=joint(shoulder,grip,ARM_LENGTH,ARM_LENGTH,
                GVec(sign*.48f+shift,1.04f,.12f+if(side==1) -.18f else .10f))
            segment(UPPER_L+side,shoulder,elbow)
            segment(FORE_L+side,elbow,grip)
            segment(HAND_L+side,grip-direction*.035f,grip+direction*.06f)
        }
    }

    companion object {
        const val HIPS=0; const val CHEST=1; const val HEAD=2
        const val UPPER_L=3; const val FORE_L=5; const val HAND_L=7
        const val THIGH_L=9; const val SHIN_L=11; const val FOOT_L=13
        const val HAIR=15; const val CLUB=16; const val COUNT=17
        const val ARM_LENGTH=.32f
        private const val ARM_REACH=ARM_LENGTH*2f-.002f
        const val TOP=.35f
        const val STRIKE_SECONDS=.145f
        const val RELEASE_SECONDS=.80f
        // Preserve the contact instant while giving the follow-through time to settle.
        const val IMPACT=TOP+STRIKE_SECONDS/RELEASE_SECONDS*(1f-TOP)
        fun releaseProgress(seconds: Float) = (TOP+seconds/RELEASE_SECONDS*(1f-TOP)).coerceAtMost(1f)
    }
}
