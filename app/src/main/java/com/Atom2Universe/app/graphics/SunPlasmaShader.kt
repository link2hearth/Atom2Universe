package com.Atom2Universe.app.graphics

/** Un même programme de plasma pour le Drawable Android et le repère solaire OpenGL. */
internal object SunPlasmaShader {
    const val agsl = """
            uniform float2 size;
            uniform float phase;
            uniform float eruptionAge;
            uniform float4 eruptionShape;
            uniform float4 eruptionDetail;

            float hash(float3 p) {
                p = fract(p * 0.1031);
                p += dot(p, p.yzx + 33.33);
                return fract((p.x + p.y) * p.z);
            }
            float noise(float3 p) {
                float3 i = floor(p);
                float3 f = fract(p);
                f = f*f*(3.0-2.0*f);
                return mix(
                    mix(mix(hash(i), hash(i+float3(1,0,0)), f.x),
                        mix(hash(i+float3(0,1,0)), hash(i+float3(1,1,0)), f.x), f.y),
                    mix(mix(hash(i+float3(0,0,1)), hash(i+float3(1,0,1)), f.x),
                        mix(hash(i+float3(0,1,1)), hash(i+float3(1,1,1)), f.x), f.y), f.z);
            }
            float fbm(float3 p) {
                float result = 0.0;
                float amplitude = 0.53;
                for (int i=0; i<4; i++) {
                    result += amplitude * noise(p);
                    p = p * 2.03 + float3(17.1, 9.2, 13.7);
                    amplitude *= 0.48;
                }
                return result;
            }
            half4 main(float2 coord) {
                float2 uv = (coord - size*0.5) / (min(size.x,size.y)*0.309);
                float radius = length(uv);
                if (radius > 1.60) return half4(0);
                float aa = 2.0 / (min(size.x,size.y)*0.309);
                float3 orbit = float3(cos(phase*0.43), sin(phase*0.37), sin(phase*0.61)) * 1.2;
                float angle = atan(uv.y, uv.x);
                float2 direction = uv / max(radius, 0.0001);
                float3 edgePoint = float3(direction*7.0, 1.2) + orbit;
                float edgeNoise = fbm(edgePoint);
                float edge = 1.0 + (edgeNoise-0.5)*0.018;
                float disk = 1.0-smoothstep(edge-aa, edge+aa, radius);

                // Couronne irrégulière et langues de feu qui s'étirent depuis le limbe.
                float outside = max(radius-edge, 0.0);
                float tongues = pow(max(0.0, noise(edgePoint*2.6)), 3.0);
                float flameHeight = 0.025 + 0.17*tongues;
                float wisps = fbm(float3(direction*22.0, outside*24.0) + orbit*2.0);
                float flame = exp(-outside/flameHeight) * smoothstep(0.29,0.72,wisps);
                float halo = exp(-outside*22.0)*0.12;

                // Vagues de plasma : croissance, détachement, puis dispersion.
                // Le planificateur Kotlin garantit une seule émission à la fois.
                float arcs = 0.0;
                if (radius > 0.98 && eruptionAge > 0.0 && eruptionAge < 1.0) {
                    float fi = eruptionDetail.w;
                    float age = eruptionAge;
                    float envelope = smoothstep(0.0,0.16,age)*(1.0-smoothstep(0.65,1.0,age));
                    float grow = smoothstep(0.0,0.55,age);
                    float detach = smoothstep(0.35,0.95,age);
                    float anchor = eruptionShape.x;
                    float delta = atan(sin(angle-anchor), cos(angle-anchor));
                    float width = eruptionShape.z*(0.6+0.7*grow+0.4*detach);
                    float bend = outside*eruptionShape.w*(0.5+grow) + 0.035*sin(age*5.0+fi)*grow;
                    float x = (delta-bend)/width;
                    float crest = pow(max(0.0,1.0-x*x),eruptionDetail.x);
                    float asymmetry = 1.0+0.3*sin(fi)*x;
                    float arch = 1.0 + 0.20*detach + (0.025+eruptionShape.y*grow)*crest*asymmetry;
                    float ripple = (0.008+0.015*grow)*sin(x*eruptionDetail.y-age*5.0+fi);
                    float distance = abs(radius-arch-ripple);
                    float ribbon = exp(-distance/(eruptionDetail.z*(0.6+grow)));
                    float veil = exp(-distance/(0.035+0.04*grow))*0.25;
                    float fibers = 0.45+0.55*noise(float3(direction*45.0,radius*35.0)+orbit+fi);
                    float shape = 1.0-smoothstep(0.78,1.0,abs(x));
                    arcs += (ribbon*fibers+veil)*shape*envelope;
                    // Fragments qui s'écartent de l'arche et s'éteignent dans l'espace.
                    for (int j=0; j<3; j++) {
                        float fj = float(j)-1.0;
                        float scatter = hash(float3(fi,fj,7.3));
                        float fragmentAngle = anchor + (fj+scatter-0.5)*width*(0.4+detach) + bend;
                        float fragmentRadius = 1.04+(0.28+0.23*scatter)*grow+0.05*detach;
                        float2 center = float2(cos(fragmentAngle),sin(fragmentAngle))*fragmentRadius;
                        float2 offset = uv-center;
                        float fragment = exp(-dot(offset,offset)/(0.0003+0.0012*detach));
                        arcs += fragment*detach*envelope*0.65;
                    }
                }
                float coronaAlpha = clamp(halo+flame*0.72+arcs*0.7,0.0,0.92);
                coronaAlpha *= 1.0-smoothstep(1.47,1.60,radius);
                float3 corona = mix(float3(1.0,0.13,0.008), float3(1.0,0.62,0.08),
                    clamp(flame+arcs,0.0,1.0));
                float3 surface = float3(0);
                if (radius < edge+aa) {
                    float z = sqrt(max(0.0, 1.0-min(dot(uv,uv),1.0)));
                    float3 sphere = float3(uv,z);
                    // Rotation réelle de la texture sur la sphère, pas du disque à plat.
                    float rotation = phase*0.055;
                    sphere = float3(cos(rotation)*sphere.x+sin(rotation)*sphere.z,
                        sphere.y, -sin(rotation)*sphere.x+cos(rotation)*sphere.z);
                    float3 p = sphere*5.5;
                    float3 current = float3(sin(phase*0.31), cos(phase*0.53), sin(phase*0.47))*1.6;
                    float3 warp = float3(fbm(p+orbit), fbm(p-current+11.7), fbm(p+orbit.zxy+27.3));
                    float3 flowing = p + (warp-0.5)*2.8 + current*0.65;
                    float broad = fbm(flowing*1.8);
                    float grain = noise(flowing*23.0+orbit*3.0);
                    float filament = 1.0-abs(2.0*noise(flowing*7.0-current*2.0)-1.0);
                    filament = pow(filament, 9.0);
                    float heat = clamp(broad*0.9+grain*0.13+filament*0.23,0.0,1.0);
                    surface = mix(float3(0.67,0.055,0.002),float3(1.0,0.37,0.008),
                        smoothstep(0.24,0.52,heat));
                    surface = mix(surface,float3(1.0,0.86,0.23),smoothstep(0.48,0.73,heat));
                    surface = mix(surface,float3(1.0,0.97,0.68),smoothstep(0.72,0.9,heat)*0.7);
                    surface *= 0.68+0.32*pow(z,0.4);
                    surface += float3(0.22,0.07,0.002)*exp(-abs(radius-1.0)*65.0);
                }
                float alpha = disk + coronaAlpha*(1.0-disk);
                float3 rgb = surface*disk + corona*coronaAlpha*(1.0-disk);
                return half4(half3(clamp(rgb,0.0,1.0)), half(alpha));
            }
            """

    val glsl: String by lazy {
        var body = agsl
        for ((from, to) in listOf("float2" to "vec2", "float3" to "vec3", "float4" to "vec4",
            "half3" to "vec3", "half4" to "vec4", "half" to "float")) {
            body = body.replace(Regex("\\b$from\\b"), to)
        }
        body = body.replace("vec4 main(vec2 coord)", "vec4 sunColor(vec2 coord)")
        """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec2 vUV;
        """.trimIndent() + "\n" + body + "\nvoid main() { gl_FragColor = sunColor(vUV * size); }"
    }
}