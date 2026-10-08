package com.Atom2Universe.app.science

/** Illustration de photosphère en lumière visible, partagée par les deux modules.
 * Les motifs et leur vitesse sont pédagogiques, jamais présentés comme une observation.
 */
internal object StellarSurfaceShader {
    val vertex = """
        attribute vec4 aPos; attribute vec3 aNorm;
        uniform mat4 uMVP; uniform mat4 uRot;
        varying vec3 vSurface; varying vec3 vNorm;
        void main() {
            gl_Position = uMVP * aPos;
            vSurface = aNorm;
            vNorm = normalize(mat3(uRot) * aNorm);
        }
    """.trimIndent()

    val fragment = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        uniform vec3 uTint;
        // Échelle des cellules, contraste, taches, graine propre à l'astre.
        uniform vec4 uPattern;
        uniform float uTime;
        varying vec3 vSurface; varying vec3 vNorm;
        float hash(vec3 p) {
            p = fract(p * 0.1031);
            p += dot(p, p.yzx + 19.19);
            return fract((p.x + p.y) * p.z);
        }
        float noise(vec3 p) {
            vec3 i = floor(p), f = fract(p);
            f = f*f*(3.0-2.0*f);
            return mix(
                mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),
                    mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
                mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),
                    mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
        }
        void main() {
            vec3 p = normalize(vSurface);
            vec3 drift = vec3(sin(uTime*0.19),cos(uTime*0.13),sin(uTime*0.17));
            vec3 q = p*uPattern.x + uPattern.w;
            float warp = noise(q*0.45+drift);
            float cells = noise(q+drift*0.35+warp*0.7);
            float fine = noise(q*3.1-drift*0.2);
            float textureLight = 1.04 + (cells-0.5)*uPattern.y*1.35 + (fine-0.5)*uPattern.y*0.35;
            float spots = smoothstep(0.72,0.86,noise(p*8.0+uPattern.w+drift*0.1));
            textureLight *= 1.0-spots*uPattern.z;
            float mu = max(normalize(vNorm).z,0.0);
            float limb = 0.78+0.22*pow(mu,0.45);
            // Bord moins sombre, teinte conservée dans les hautes lumières.
            vec3 vividTint = pow(uTint,vec3(1.18));
            float brightness = max(textureLight*limb*1.08,0.0);
            // Compression douce des pics : ne pas écrêter les motifs en une zone uniforme.
            if (brightness > 0.90) brightness = 0.90+0.10*(1.0-exp(-(brightness-0.90)*7.0));
            gl_FragColor = vec4(vividTint*brightness,1.0);
        }
    """.trimIndent()
}
