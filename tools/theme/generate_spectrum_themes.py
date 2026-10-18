"""
Génère les thèmes « arc-en-ciel » : 36 teintes × 3 intensités.

Android ne sait pas créer un thème pendant que l'appli tourne : chaque couleur que le
joueur peut choisir doit exister d'avance comme style compilé. Ce script les écrit tous,
avec la même formule, dans deux fichiers :

- app/src/main/res/values/themes_spectrum.xml  (les 108 styles Theme.A2U.Spectrum.*)
- app/src/main/java/com/Atom2Universe/app/SpectrumThemes.kt  (la table styles + couleurs)

Lancer depuis la racine du dépôt :  python tools/theme/generate_spectrum_themes.py

La formule : pour chaque teinte, on garde la saturation de l'intensité et on cherche la
clarté qui donne une **luminance fixe**. Sans cela, un bleu saturé sortirait trop sombre
(texte foncé illisible dessus) et un jaune trop clair : à luminance égale, toutes les
teintes se lisent aussi bien.
"""
import colorsys
import os

HUES = 36
# nom, saturation (HSL), luminance visée de la couleur d'accent
LEVELS = [
    ("vivid", 0.90, 0.30),
    ("soft", 0.45, 0.32),
    ("pastel", 0.70, 0.56),
]
BACKGROUND = (0x10, 0x14, 0x1C)  # audio_background : le fond de toute l'appli
DIM_MIX = 0.24                   # part d'accent dans la couleur « dim » (fonds teintés)
MIDI_DARKEN = 0.14               # l'accent MIDI est un cran plus sombre, comme les thèmes fixes


def luminance(rgb):
    def lin(c):
        c /= 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (lin(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a, b):
    la, lb = sorted((luminance(a), luminance(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def hsl(h, s, l):
    r, g, b = colorsys.hls_to_rgb(h, l, s)
    return tuple(round(c * 255) for c in (r, g, b))


def accent_for(hue, sat, target):
    lo, hi = 0.0, 1.0
    for _ in range(40):
        mid = (lo + hi) / 2
        if luminance(hsl(hue, sat, mid)) < target:
            lo = mid
        else:
            hi = mid
    return hsl(hue, sat, hi)


def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def hexa(rgb):
    return "#%02X%02X%02X" % rgb


def main():
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    xml = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- GÉNÉRÉ par tools/theme/generate_spectrum_themes.py : ne pas modifier à la main. -->",
        "<!-- Les thèmes de couleur libre : 36 teintes (une tous les 10°) × 3 intensités. -->",
        "<resources>",
    ]
    styles, accents = [], []
    for li, (name, sat, target) in enumerate(LEVELS):
        for h in range(HUES):
            accent = accent_for(h / HUES, sat, target)
            dim = mix(BACKGROUND, accent, DIM_MIX)
            midi = mix(accent, (0, 0, 0), MIDI_DARKEN)
            on_midi = (255, 255, 255) if contrast(midi, (255, 255, 255)) >= contrast(midi, (0, 0, 0)) else (0, 0, 0)
            style = "Theme.A2U.Spectrum.%s.%02d" % (name.capitalize(), h)
            xml += [
                '    <style name="%s" parent="Theme.A2U">' % style,
                '        <item name="a2uMidiAccent">%s</item>' % hexa(midi),
                '        <item name="a2uMidiAccentOnText">%s</item>' % hexa(on_midi),
                '        <item name="a2uMusicAccent">%s</item>' % hexa(accent),
                '        <item name="a2uMusicAccentDim">%s</item>' % hexa(dim),
                "    </style>",
            ]
            styles.append("R.style." + style.replace(".", "_"))
            accents.append("0xFF%02X%02X%02X.toInt()" % accent)
    xml.append("</resources>")
    with open(os.path.join(root, "app/src/main/res/values/themes_spectrum.xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(xml) + "\n")

    def rows(items):
        return "\n".join("        " + ", ".join(items[i:i + 4]) + "," for i in range(0, len(items), 4))

    kt = """package com.Atom2Universe.app

// GÉNÉRÉ par tools/theme/generate_spectrum_themes.py : ne pas modifier à la main.

/**
 * Les thèmes de couleur libre : [HUES] teintes × [LEVELS] intensités, rangés intensité par
 * intensité. Les couleurs d'accent sont celles des styles, pour dessiner la barre de teintes et
 * l'aperçu sans avoir à ouvrir chaque thème.
 */
object SpectrumThemes {
    const val HUES = %d
    const val LEVELS = %d

    private val styles = intArrayOf(
%s
    )

    private val accents = intArrayOf(
%s
    )

    private fun index(hue: Int, level: Int) =
        level.coerceIn(0, LEVELS - 1) * HUES + Math.floorMod(hue, HUES)

    fun style(hue: Int, level: Int): Int = styles[index(hue, level)]
    fun accent(hue: Int, level: Int): Int = accents[index(hue, level)]
}
""" % (HUES, len(LEVELS), rows(styles), rows(accents))
    with open(os.path.join(root, "app/src/main/java/com/Atom2Universe/app/SpectrumThemes.kt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(kt)
    print("%d styles written" % len(styles))


if __name__ == "__main__":
    main()
