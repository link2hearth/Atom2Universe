package com.Atom2Universe.app.games.billiards.render

import android.graphics.*
import android.opengl.GLES30 as GL
import android.opengl.GLUtils
import com.Atom2Universe.app.games.billiards.core.*

/** Textures are mathematical except the numbered badges, rasterized locally. */
internal class BilliardShader(numberLabel: (Int)->String,private val table: BilliardTable) {
    /** One compiled variant of the same source, with its own uniform locations. */
    private class Program(cutaway: Boolean) {
        val id: Int
        val mvp: Int; val model: Int; val kind: Int; val eye: Int; val ball: Int; val tint: Int
        val neon: Int; val alpha: Int; val outdoor: Int; val time: Int; val rotationStripes: Int
        val ballCount: Int; val occluders: Int; val badges: Int; val tableLength: Int; val toon: Int
        init {
            fun compile(type: Int, source: String): Int {
                val id=GL.glCreateShader(type); GL.glShaderSource(id,source); GL.glCompileShader(id)
                val status=IntArray(1); GL.glGetShaderiv(id,GL.GL_COMPILE_STATUS,status,0)
                check(status[0]!=0) { GL.glGetShaderInfoLog(id) }; return id
            }
            val source=if(cutaway) FRAGMENT.replaceFirst("#version 300 es","#version 300 es\n#define CUTAWAY 1") else FRAGMENT
            val v=compile(GL.GL_VERTEX_SHADER,VERTEX); val f=compile(GL.GL_FRAGMENT_SHADER,source)
            id=GL.glCreateProgram(); GL.glAttachShader(id,v); GL.glAttachShader(id,f); GL.glLinkProgram(id)
            val status=IntArray(1); GL.glGetProgramiv(id,GL.GL_LINK_STATUS,status,0)
            check(status[0]!=0) { GL.glGetProgramInfoLog(id) }
            GL.glDeleteShader(v); GL.glDeleteShader(f)
            fun loc(s: String)=GL.glGetUniformLocation(id,s)
            mvp=loc("uMvp"); model=loc("uModel"); kind=loc("uKind"); eye=loc("uEye")
            ball=loc("uBall"); tint=loc("uTint"); neon=loc("uNeon"); alpha=loc("uAlpha")
            outdoor=loc("uOutdoor"); time=loc("uTime"); rotationStripes=loc("uRotationStripes")
            ballCount=loc("uBallCount"); occluders=loc("uOccluders"); badges=loc("uBadges"); tableLength=loc("uTableLength")
            toon=loc("uToon")
        }
    }
    // A shader that may discard a pixel loses the GPU's early depth test for every draw, even
    // when it keeps them all. Only the room's walls and lights need the cut, so only they pay it.
    private val plain=Program(false)
    private val cut=Program(true)
    private var current=plain
    val mvp get()=current.mvp
    val model get()=current.model
    private val texture: Int
    private val occluders=FloatArray(32*4)
    init {
        val ids=IntArray(1); GL.glGenTextures(1,ids,0); texture=ids[0]
        GL.glBindTexture(GL.GL_TEXTURE_2D,texture)
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_LINEAR)
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_LINEAR)
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_S,GL.GL_CLAMP_TO_EDGE)
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_T,GL.GL_CLAMP_TO_EDGE)
        val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); canvas.drawColor(Color.WHITE)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK; textSize=78f; textAlign=Paint.Align.CENTER; typeface=Typeface.create("sans-serif",Typeface.BOLD) }
        for(i in 1..15) canvas.drawText(numberLabel(i),(i%4)*128f+64,(i/4)*128f+64-(paint.ascent()+paint.descent())/2,paint)
        GLUtils.texImage2D(GL.GL_TEXTURE_2D,0,bitmap,0); bitmap.recycle()
        for(p in listOf(plain,cut)) {
            GL.glUseProgram(p.id); GL.glUniform1i(p.badges,0)
            GL.glUniform1f(p.tableLength,table.length.toFloat())
        }
    }
    fun begin(x: Float,y: Float,z: Float,isNeon: Boolean,frame: BilliardFrame?,isOutdoor: Boolean=false,showRotationStripes: Boolean=false,
              isCartoon: Boolean=false) {
        var count=0
        frame?.balls?.forEach { b ->
            if(b.motion==Motion.POCKETED || count>=32) return@forEach
            occluders[count*4]=(b.p.x-table.length/2).toFloat(); occluders[count*4+1]=(.78+b.p.z).toFloat()
            occluders[count*4+2]=(b.p.y-table.width/2).toFloat(); occluders[count*4+3]=b.radius.toFloat()
            count++
        }
        val seconds=(android.os.SystemClock.uptimeMillis()%120_000L)/1000f
        for(p in listOf(cut,plain)) {
            GL.glUseProgram(p.id); GL.glUniform3f(p.eye,x,y,z); GL.glUniform1f(p.neon,if(isNeon) 1f else 0f)
            GL.glUniform1f(p.outdoor,if(isOutdoor) 1f else 0f)
            GL.glUniform1f(p.rotationStripes,if(showRotationStripes) 1f else 0f)
            GL.glUniform1f(p.toon,if(isCartoon) 1f else 0f)
            GL.glUniform1f(p.time,seconds)
            GL.glUniform1i(p.ballCount,count)
            GL.glUniform4fv(p.occluders,32,occluders,0)
        }
        current=plain
        GL.glActiveTexture(GL.GL_TEXTURE0); GL.glBindTexture(GL.GL_TEXTURE_2D,texture)
    }
    fun material(type: Int, color: Int = 0xFFFFFF, id: Int = -1,opacity: Float = 1f,clipRoom: Boolean=false) {
        val p=if(clipRoom) cut else plain
        if(p!==current) { GL.glUseProgram(p.id); current=p }
        GL.glUniform1i(p.kind,type); GL.glUniform1i(p.ball,id)
        GL.glUniform1f(p.alpha,opacity)
        GL.glUniform3f(p.tint,((color shr 16) and 255)/255f,((color shr 8) and 255)/255f,(color and 255)/255f)
    }
    fun destroy() { GL.glDeleteProgram(plain.id); GL.glDeleteProgram(cut.id); GL.glDeleteTextures(1,intArrayOf(texture),0) }
    companion object {
        private const val VERTEX="""#version 300 es
        layout(location=0) in vec3 aPosition;
        layout(location=1) in vec3 aNormal;
        layout(location=2) in vec4 aColor;
        uniform mat4 uMvp; uniform mat4 uModel;
        out vec3 vLocal; out vec3 vWorld; out vec3 vNormal; out vec4 vColor;
        void main() {
            gl_Position=uMvp*vec4(aPosition,1.0);
            vLocal=aPosition; vWorld=(uModel*vec4(aPosition,1.0)).xyz;
            vNormal=normalize(mat3(uModel)*aNormal); vColor=aColor;
        }
        """
        internal const val FRAGMENT="""#version 300 es
        precision highp float;
        in vec3 vLocal; in vec3 vWorld; in vec3 vNormal; in vec4 vColor;
        uniform mat4 uModel; uniform int uKind; uniform int uBall;
        uniform vec3 uTint; uniform vec3 uEye; uniform float uNeon; uniform float uAlpha;
        uniform float uOutdoor; uniform float uTime; uniform float uRotationStripes; uniform float uToon;
        uniform sampler2D uBadges;
        uniform float uTableLength;
        uniform int uBallCount; uniform vec4 uOccluders[32];
        out vec4 fragColor;
        float hash(vec2 p) { return fract(sin(dot(p,vec2(12.9898,78.233)))*43758.5453); }
        float noise(vec2 p) {
            vec2 i=floor(p),f=fract(p); f=f*f*(3.0-2.0*f);
            return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1)),f.x),f.y);
        }
        vec3 environment(vec3 r) {
            if(uOutdoor>.5) {
                vec3 e=mix(vec3(.16,.23,.12),vec3(.32,.56,.76),smoothstep(-.18,.65,r.y));
                e+=vec3(1.7,1.55,1.24)*pow(max(dot(r,normalize(vec3(-.25,1.0,.18))),0.0),110.0);
                return e;
            }
            vec3 e=mix(vec3(.025,.032,.04),vec3(.22,.27,.30),smoothstep(-.2,1.0,r.y));
            // Reflect two actual rectangular ceiling luminaires, rather than a painted white dot.
            if(r.y>.001) {
                vec3 hit=vWorld+r*((2.16-vWorld.y)/r.y);
                for(int i=0;i<2;i++) {
                    vec2 q=abs(hit.xz-vec2((float(i)*2.0-1.0)*uTableLength*.23,0.0));
                    float edge=max((q.x-.23)*4.0,(q.y-.10)*6.0);
                    vec3 light=normalize(vec3((float(i)*2.0-1.0)*uTableLength*.23,2.16,0.0)-vWorld);
                    e+=vec3(4.0,3.8,3.5)*pow(max(dot(r,light),0.0),64.0);
                    e+=vec3(1.0,.89,.66)*(1.0-smoothstep(-.06,.10,edge))*1.8;
                }
            }
            // Soft window reflection and coloured accents in the neon room.
            e+=vec3(.9,1.05,1.2)*pow(max(dot(r,normalize(vec3(-.7,.35,-1.0))),0.0),20.0);
            e+=uNeon*(vec3(.30,.025,.42)*pow(max(r.x,0.0),4.0)+vec3(.015,.22,.34)*pow(max(-r.x,0.0),4.0));
            return e;
        }
        // Cartoon: three flat inks instead of a gradient, and a hard white highlight on the balls.
        // Colours stay in their painted (sRGB) values: flat inks need no light measurement.
        vec3 cartoon(vec3 base,vec3 n,vec3 l,vec3 h,float lit,float ao) {
            float d=dot(n,l); float w=max(fwidth(d),.002);
            float tone=mix(.46,.72,smoothstep(-w,w,d-.05));
            tone=mix(tone,1.0,smoothstep(-w,w,d-.60));
            // The balls' shadows on the cloth and the depth of the pockets become hard patches too.
            tone*=mix(.60,1.0,smoothstep(.78,.82,lit));
            tone*=mix(.50,1.0,smoothstep(.55,.62,ao));
            vec3 colour=base*tone;
            colour+=uNeon*base*(vec3(.30,.04,.46)*step(.35,n.x)+vec3(.03,.24,.40)*step(.35,-n.x));
            // Flat faces would light up whole: only round or rippled things get the spot.
            float gloss=uKind==3 ? 1.0 : uKind==12 ? .5 : 0.0;
            float nh=dot(n,h); float s=max(fwidth(nh),.001);
            return mix(colour,vec3(1.0),gloss*smoothstep(.975-s,.975+s,nh)*step(0.0,d));
        }
        void main() {
            // Only the enclosing room is cut away: distant scenery must survive an orbit.
            #ifdef CUTAWAY
            if(vWorld.y>2.45 && uEye.y>2.45) discard;
            if(uKind==8 && vWorld.y>2.10 && uEye.y>2.10) discard;
            if((vWorld.z< -3.22 && uEye.z< -3.18) || (vWorld.x< -4.25 && uEye.x< -4.15)
                || (vWorld.z>3.22 && uEye.z>3.18) || (vWorld.x>4.25 && uEye.x>4.15)) discard;
            #endif
            vec3 base=vColor.rgb*uTint; vec3 n=normalize(vNormal);
            float alpha=vColor.a*uAlpha; float rough=.72; float metal=0.0;
            if(uKind==14) {
                vec3 r=normalize(vLocal);
                vec3 sky=mix(vec3(.77,.84,.80),vec3(.25,.53,.76),smoothstep(-.03,.80,r.y));
                vec2 cloudUv=r.xz/max(r.y,.07)*2.8;
                float clouds=smoothstep(mix(.63,.70,uToon),mix(.83,.72,uToon),noise(cloudUv)*.72+noise(cloudUv*2.1)*.28);
                sky=mix(sky,vec3(.92,.93,.87),clouds*smoothstep(.03,.22,r.y)*.7);
                sky+=vec3(.35,.29,.16)*pow(max(dot(r,normalize(vec3(-.25,1.0,.18))),0.0),350.0);
                fragColor=vec4(sky,1.0); return;
            }
            if(uKind==1) {
                if(uToon<.5) {
                    vec2 weave=vWorld.xz*1800.0;
                    float aa=1.0-clamp(length(fwidth(weave)),0.0,1.0);
                    base*=.96+.08*mix(.5,hash(floor(weave)),aa);
                    // Broad light pool on the cloth, soft peripheral falloff.
                    base*=.82+.18*exp(-dot(vWorld.xz*vec2(.38,.65),vWorld.xz*vec2(.38,.65)));
                }
                rough=.94;
            } else if(uKind==2) {
                if(uToon<.5) {
                    float g=noise(vec2(vWorld.x*3.0,vWorld.z*46.0));
                    float grain=sin(vWorld.z*240.0+g*8.0+sin(vWorld.x*6.0)*3.0);
                    base*=.82+.16*g+.08*grain;
                }
                rough=.29;
            } else if(uKind==3) {
                vec3 local=normalize(vLocal);
                n=normalize(mat3(uModel)*local); base=uTint; rough=.19;
                float edge=max(fwidth(local.y)*1.2,.005);
                if(uBall>8 && uBall<=15) base=mix(base,vec3(.96,.95,.91),smoothstep(.44-edge,.44+edge,abs(local.y)));
                if(uRotationStripes>.5 && uBall<=8) {
                    // Body-local, offset circles with different axes reveal both roll and spin.
                    // Unnumbered balls use -1; pool stripes (9-15) keep their existing markings.
                    float a=dot(local,normalize(vec3(.82,.31,.48)))-.20;
                    float b=dot(local,normalize(vec3(-.27,.91,.32)))+.34;
                    float aa=max(fwidth(a),.003), ab=max(fwidth(b),.003);
                    float marks=max(1.0-smoothstep(.024-aa,.024+aa,abs(a)),
                                    1.0-smoothstep(.020-ab,.020+ab,abs(b)));
                    float luminance=dot(base,vec3(.2126,.7152,.0722));
                    vec3 ink=mix(base*.52,mix(base,vec3(.72,.75,.73),.36),
                                 1.0-smoothstep(.12,.32,luminance));
                    base=mix(base,ink,marks*.65);
                }
                if(uBall>0 && uBall<=15 && abs(local.z)>.89) {
                    vec2 uv=local.xy/.86+.5; uv.y=1.0-uv.y;
                    vec2 cell=vec2(float(uBall%4),float(uBall/4));
                    vec3 badge=texture(uBadges,(cell+clamp(uv,0.0,1.0))*.25).rgb;
                    base=mix(base,badge,smoothstep(.89,.915,abs(local.z)));
                }
                if(uBall==0) base=mix(base,vec3(.6,.1,.08),smoothstep(.992,.995,max(max(abs(local.x),abs(local.y)),abs(local.z))));
            } else if(uKind==4) {
                // Staggered parquet, with individual plank tones and fine grain.
                float row=floor(vWorld.z/.19);
                vec2 plank=vec2(vWorld.x+mod(row,2.0)*.6,vWorld.z);
                vec2 grid=plank/vec2(1.2,.19); vec2 f=fract(grid);
                float seam=smoothstep(0.0,.006,min(min(f.x,1.0-f.x),min(f.y,1.0-f.y)));
                base*=mix(.30,.85+.20*hash(floor(grid)),seam);
                if(uToon<.5) base*=.94+.06*sin(vWorld.x*160.0+noise(vWorld.xz*8.0)*8.0);
                rough=.55;
                // Ambient shadow beneath the table.
                vec2 q=abs(vWorld.xz)-vec2(uTableLength*.52,uTableLength*.27);
                base*=mix(.45,1.0,smoothstep(-.2,.45,max(q.x,q.y)));
            } else if(uKind==5 || uKind==8) {
                fragColor=vec4(base,alpha); return;
            } else if(uKind==6) {
                float r=length(vLocal.xz);
                fragColor=vec4(.005,.012,.018,.40*(1.0-smoothstep(mix(.12,.80,uToon),mix(1.0,.86,uToon),r))); return;
            } else if(uKind==7) { rough=.23; metal=.65; }
            else if(uKind==9) {
                vec2 f=fract(vWorld.xz*8.0); base*=.80+.20*step(.18,abs(f.x-.5)+abs(f.y-.5)); rough=1.0;
            } else if(uKind==10) {
                vec2 uv=(abs(n.x)>.5 ? vWorld.zy : vWorld.xy)*vec2(4.0,8.0); uv.x+=mod(floor(uv.y),2.0)*.5;
                vec2 f=fract(uv); float joint=step(.055,f.x)*step(.10,f.y);
                base=mix(vec3(.22,.22,.20),base*(.8+.25*hash(floor(uv))),joint);
            } else if(uKind==11) {
                // Matte leather and rubber, without the brass material's reflections.
                if(uToon<.5) {
                    vec2 grain=vWorld.xz*1400.0;
                    float aa=1.0-clamp(length(fwidth(grain)),0.0,1.0);
                    base*=.97+.06*mix(.5,hash(floor(grain)),aa);
                }
                rough=.86;
            } else if(uKind==12) {
                // Shallow ripples and the tiled pool floor, without transparent geometry.
                float phase=uTime*6.2831853/120.0;
                vec2 p=vWorld.xz;
                n=normalize(vec3(.026*cos(p.x*8.0+p.y*3.0+phase*12.0),1.0,
                    .025*sin(p.y*9.0-p.x*2.0+phase*9.0)));
                vec2 tile=fract(p*3.5);
                float grout=1.0-smoothstep(.015,.040,min(min(tile.x,1.0-tile.x),min(tile.y,1.0-tile.y)));
                float caustic=pow(.5+.5*sin(p.x*13.0+sin(p.y*9.0+phase*5.0)+phase*4.0),12.0);
                base*=.92+.06*grout+.16*caustic; rough=.16;
            } else if(uKind==13) {
                if(uToon<.5) {
                    float blades=1.0-clamp(length(fwidth(vWorld.xz*90.0)),0.0,1.0);
                    base*=.88+.16*noise(vWorld.xz*.8)+.08*mix(.5,hash(floor(vWorld.xz*90.0)),blades);
                } else base*=.95+.10*step(.5,noise(vWorld.xz*.8));
                rough=1.0;
            }
            vec3 v=normalize(uEye-vWorld);
            vec3 l=normalize(vec3(-.25,1.0,.18)); vec3 h=normalize(l+v);
            float shadow=1.0;
            if(uKind==1) {
                for(int i=0;i<32;i++) {
                    if(i>=uBallCount) break;
                    vec4 b=uOccluders[i];
                    float altitude=max(0.0,b.y-.78-b.w);
                    float r=b.w+altitude*.25;
                    // Beyond 1.8 r a ball changes nothing here: skip it before the costly part.
                    vec2 offset=vWorld.xz-b.xz;
                    if(dot(offset,offset)>=r*r*3.24) continue;
                    float d=length(offset);
                    shadow*=1.0-.50*(1.0-smoothstep(r*.55,r*1.8,d))*exp(-altitude*5.0);
                }
            }
            float ao=uKind==3 ? .72+.28*smoothstep(-.9,.2,n.y) : 1.0;
            if(uKind==11) ao=.35+.65*smoothstep(.55,.82,vWorld.y);
            float lightVisibility=uKind==11 ? .03+.97*smoothstep(.55,.82,vWorld.y) : 1.0;
            vec3 result;
            if(uToon>.5) result=cartoon(base,n,l,h,shadow*lightVisibility,ao);
            else {
                float nv=max(dot(n,v),.001),nl=max(dot(n,l),0.0),nh=max(dot(n,h),0.0);
                float a=rough*rough,a2=a*a;
                float den=nh*nh*(a2-1.0)+1.0;
                float D=a2/(3.14159*den*den);
                float k=(rough+1.0)*(rough+1.0)/8.0;
                float G=nv/(nv*(1.0-k)+k)*nl/(nl*(1.0-k)+k);
                vec3 F0=mix(vec3(.045),pow(base,vec3(2.2)),metal);
                vec3 F=F0+(1.0-F0)*pow(1.0-max(dot(h,v),0.0),5.0);
                vec3 linear=pow(max(base,vec3(0.0)),vec3(2.2));
                result=linear*((.25+uOutdoor*.08)*ao+nl*.95*shadow*lightVisibility)*(1.0-metal*.6);
                result+=D*G*F/max(4.0*nv*max(nl,.001),.001)*nl*.85*lightVisibility;
                if(uKind==3 || uKind==2 || uKind==7 || uKind==12) {
                    vec3 fresnel=F0+(1.0-F0)*pow(1.0-nv,5.0);
                    result+=environment(reflect(-v,n))*fresnel*(1.0-rough*.7);
                }
                result+=uNeon*linear*(vec3(.15,.02,.23)*max(n.x,0.0)+vec3(.015,.12,.20)*max(-n.x,0.0));
                result=pow(max(result,vec3(0.0)),vec3(1.0/2.2));
            }
            // Distant room surfaces recede softly; the table stays crisp.
            float fog=1.0-exp(-max(0.0,length(uEye-vWorld)-3.0)*mix(.025,.012,uOutdoor));
            fragColor=vec4(mix(result,mix(vec3(.06,.08,.095),vec3(.77,.84,.80),uOutdoor),fog),alpha);
        }
        """
    }
}
