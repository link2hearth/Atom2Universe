package com.Atom2Universe.app.notes.editor

/**
 * Le Markdown des notes, lu ligne par ligne, sans rien d'Android : testé en JVM.
 *
 * Le principe de l'éditeur : **le texte affiché EST le Markdown enregistré**. On ne traduit jamais
 * entre un texte mis en forme et du Markdown (c'est ce va-et-vient qui corrompait les notes) : on
 * colore simplement les marques (`**`, `# `, `- [ ] `…) et on met en forme ce qu'elles entourent.
 * Ouvrir puis enregistrer une note ne peut donc rien changer.
 *
 * Syntaxe reconnue :
 * - blocs (début de ligne) : `# `, `## `, `### ` titres ; `> ` citation ; `- ` ou `* ` puce ;
 *   `- [ ] ` / `- [x] ` case à cocher ; `1. ` liste numérotée. Les listes acceptent un retrait d'espaces.
 * - en ligne : `**gras**`, `*italique*`, `***gras italique***`, `__souligné__`, `~~barré~~`,
 *   `` `code` ``, `[texte](https://…)`, `[[Titre d'une note]]`.
 */
object MarkdownSyntax {

    enum class Kind {
        /** Une marque de syntaxe : affichée atténuée, retirée du texte rendu. */
        MARKER,
        HEADING, BOLD, ITALIC, UNDERLINE, STRIKE, CODE,
        /** Le texte d'un lien web ; [Token.data] porte l'adresse. */
        LINK,
        /** Le titre d'un lien vers une autre note ; [Token.data] porte ce titre. */
        WIKI,
        /** Le préfixe `- ` d'une puce, dessiné comme un point. */
        BULLET,
        /** Le préfixe `- [ ] ` d'une case ; [Token.level] vaut 1 si elle est cochée. */
        CHECK,
        /** Le texte d'une case cochée (barré, atténué). */
        DONE,
        /** Le préfixe `12. ` d'une liste numérotée. */
        NUMBER,
        /** Le préfixe `> ` d'une citation, dessiné comme une barre. */
        QUOTE_BAR,
        QUOTE,
    }

    /** Une plage [start, end[ de la ligne (ou du texte) et ce qu'elle porte. */
    data class Token(val kind: Kind, val start: Int, val end: Int, val level: Int = 0, val data: String? = null)

    enum class Block { PARAGRAPH, HEADING, QUOTE, BULLET, CHECK, NUMBER }

    /**
     * Une ligne lue : son type de bloc, la fin de son préfixe (le texte commence là), son retrait
     * (pour les listes), et toutes les plages à mettre en forme.
     */
    data class Line(
        val block: Block,
        val prefixEnd: Int,
        val indent: Int,
        val tokens: List<Token>,
        /** Niveau de titre (1 à 3), numéro de liste, ou 1 pour une case cochée. */
        val level: Int = 0,
    )

    private val HEADING = Regex("^(#{1,3}) ")
    private val CHECK = Regex("^( *)([-*]) \\[([ xX])\\] ")
    private val BULLET = Regex("^( *)([-*]) ")
    private val NUMBER = Regex("^( *)(\\d{1,9})([.)]) ")

    fun parseLine(line: String): Line {
        val tokens = ArrayList<Token>()
        var block = Block.PARAGRAPH
        var prefixEnd = 0
        var indent = 0
        var level = 0

        val heading = HEADING.find(line)
        val check = if (heading == null) CHECK.find(line) else null
        val bullet = if (heading == null && check == null) BULLET.find(line) else null
        val number = if (heading == null && check == null && bullet == null) NUMBER.find(line) else null
        when {
            heading != null -> {
                block = Block.HEADING
                level = heading.groupValues[1].length
                prefixEnd = heading.range.last + 1
                tokens += Token(Kind.MARKER, 0, prefixEnd)
                tokens += Token(Kind.HEADING, 0, line.length, level)
            }
            line.startsWith("> ") || line == ">" -> {
                block = Block.QUOTE
                prefixEnd = minOf(2, line.length)
                tokens += Token(Kind.QUOTE_BAR, 0, prefixEnd)
                if (line.length > prefixEnd) tokens += Token(Kind.QUOTE, prefixEnd, line.length)
            }
            check != null -> {
                block = Block.CHECK
                indent = check.groupValues[1].length
                prefixEnd = check.range.last + 1
                level = if (check.groupValues[3] == " ") 0 else 1
                tokens += Token(Kind.CHECK, indent, prefixEnd, level)
                if (level == 1 && line.length > prefixEnd) tokens += Token(Kind.DONE, prefixEnd, line.length)
            }
            bullet != null -> {
                block = Block.BULLET
                indent = bullet.groupValues[1].length
                prefixEnd = bullet.range.last + 1
                tokens += Token(Kind.BULLET, indent, prefixEnd)
            }
            number != null -> {
                block = Block.NUMBER
                indent = number.groupValues[1].length
                prefixEnd = number.range.last + 1
                level = number.groupValues[2].toIntOrNull() ?: 1
                tokens += Token(Kind.NUMBER, indent, prefixEnd, level)
            }
        }

        parseInline(line, prefixEnd, tokens)
        return Line(block, prefixEnd, indent, tokens, level)
    }

    /**
     * Les styles en ligne à partir de [from]. Chaque règle « consomme » ses marques dans une copie
     * de travail (remplacées par un caractère nul) pour que les règles suivantes ne les relisent pas :
     * les `**` d'un gras ne deviennent pas deux italiques, et rien n'est lu dans un `code`.
     */
    private fun parseInline(line: String, from: Int, out: MutableList<Token>) {
        if (from >= line.length) return
        val work = line.toCharArray()
        for (i in 0 until from) work[i] = NUL

        fun consumed(s: Int, e: Int) { for (i in s until e) work[i] = NUL }
        fun text() = String(work)

        // `code` : tout son contenu est retiré des autres règles.
        CODE.findAll(text()).forEach { m ->
            val s = m.range.first
            val e = m.range.last + 1
            out += Token(Kind.MARKER, s, s + 1)
            out += Token(Kind.CODE, s + 1, e - 1)
            out += Token(Kind.MARKER, e - 1, e)
            consumed(s, e)
        }
        // [[Titre]]
        WIKI.findAll(text()).forEach { m ->
            val s = m.range.first
            val e = m.range.last + 1
            out += Token(Kind.MARKER, s, s + 2)
            out += Token(Kind.WIKI, s + 2, e - 2, data = m.groupValues[1].trim())
            out += Token(Kind.MARKER, e - 2, e)
            consumed(s, s + 2); consumed(e - 2, e)
        }
        // [texte](adresse) : l'adresse entière est une marque.
        LINK.findAll(text()).forEach { m ->
            val s = m.range.first
            val e = m.range.last + 1
            val textEnd = s + 1 + m.groupValues[1].length
            out += Token(Kind.MARKER, s, s + 1)
            out += Token(Kind.LINK, s + 1, textEnd, data = m.groupValues[2])
            out += Token(Kind.MARKER, textEnd, e)
            consumed(s, s + 1); consumed(textEnd, e)
        }
        wrapped(text(), BOLD_ITALIC, 3, out, ::consumed, Kind.BOLD, Kind.ITALIC)
        wrapped(text(), BOLD, 2, out, ::consumed, Kind.BOLD)
        wrapped(text(), UNDERLINE, 2, out, ::consumed, Kind.UNDERLINE)
        wrapped(text(), STRIKE, 2, out, ::consumed, Kind.STRIKE)
        wrapped(text(), ITALIC, 1, out, ::consumed, Kind.ITALIC)
    }

    private inline fun wrapped(
        text: String, regex: Regex, m: Int, out: MutableList<Token>,
        consumed: (Int, Int) -> Unit, vararg kinds: Kind,
    ) {
        regex.findAll(text).forEach { match ->
            val s = match.range.first
            val e = match.range.last + 1
            out += Token(Kind.MARKER, s, s + m)
            for (k in kinds) out += Token(k, s + m, e - m)
            out += Token(Kind.MARKER, e - m, e)
            consumed(s, s + m); consumed(e - m, e)
        }
    }

    private const val NUL = '\u0000'
    private val CODE = Regex("`[^`\\u0000]+`")
    private val WIKI = Regex("\\[\\[([^\\[\\]\\u0000]+)]]")
    private val LINK = Regex("\\[([^\\[\\]\\u0000]+)]\\(([^()\\s\\u0000]+)\\)")
    private val BOLD_ITALIC = Regex("\\*\\*\\*(?=[^\\s*\\u0000])(.+?)(?<=[^\\s*\\u0000])\\*\\*\\*")
    private val BOLD = Regex("\\*\\*(?=[^\\s*\\u0000])(.+?)(?<=[^\\s*\\u0000])\\*\\*")
    private val UNDERLINE = Regex("__(?=[^\\s_\\u0000])(.+?)(?<=[^\\s_\\u0000])__")
    private val STRIKE = Regex("~~(?=[^\\s~\\u0000])(.+?)(?<=[^\\s~\\u0000])~~")
    private val ITALIC = Regex("(?<!\\*)\\*(?=[^\\s*\\u0000])(.+?)(?<=[^\\s*\\u0000])\\*(?!\\*)")

    // ---- Rendu sans les marques (aperçus, recherche, lecture à voix haute) -------------------------

    /** Un texte débarrassé de ses marques, et les plages où le mettre en forme. */
    data class Rendered(val text: String, val tokens: List<Token>)

    /**
     * Le texte tel qu'on le lit : marques retirées, cases devenues ☐ / ☑, puces devenues •,
     * numéros gardés. Les plages ([Kind.HEADING], [Kind.BOLD]…) sont exprimées dans ce texte rendu.
     * [maxLines] coupe tôt pour les aperçus de la bibliothèque.
     */
    fun render(markdown: String, maxLines: Int = Int.MAX_VALUE): Rendered {
        val sb = StringBuilder()
        val tokens = ArrayList<Token>()
        val lines = markdown.split('\n')
        for ((n, line) in lines.withIndex()) {
            if (n >= maxLines) break
            if (n > 0) sb.append('\n')
            val parsed = parseLine(line)
            val base = sb.length
            // Le préfixe rendu
            when (parsed.block) {
                Block.CHECK -> sb.append(" ".repeat(parsed.indent)).append(if (parsed.level == 1) "☑ " else "☐ ")
                Block.BULLET -> sb.append(" ".repeat(parsed.indent)).append("• ")
                Block.NUMBER -> sb.append(line, 0, parsed.prefixEnd)
                else -> Unit
            }
            // Les caractères gardés, et la position rendue de chacun.
            val hidden = BooleanArray(line.length)
            for (t in parsed.tokens) if (t.kind == Kind.MARKER || t.kind == Kind.QUOTE_BAR) for (i in t.start until t.end) hidden[i] = true
            for (i in 0 until parsed.prefixEnd) hidden[i] = true
            val at = IntArray(line.length + 1)
            for (i in line.indices) {
                at[i] = sb.length
                if (!hidden[i]) sb.append(line[i])
            }
            at[line.length] = sb.length
            for (t in parsed.tokens) {
                when (t.kind) {
                    Kind.MARKER, Kind.QUOTE_BAR, Kind.BULLET, Kind.NUMBER -> Unit
                    Kind.CHECK -> tokens += t.copy(start = base + parsed.indent, end = base + parsed.indent + 1)
                    Kind.HEADING -> tokens += t.copy(start = base, end = sb.length)
                    else -> if (at[t.end] > at[t.start]) tokens += t.copy(start = at[t.start], end = at[t.end])
                }
            }
        }
        return Rendered(sb.toString(), tokens)
    }

    /** Le texte brut d'une note : pour la recherche et la lecture à voix haute. */
    fun toPlainText(markdown: String): String = render(markdown).text.trim()

    /** Les titres des notes citées par `[[…]]`. */
    fun wikiLinks(markdown: String): List<String> =
        WIKI.findAll(markdown).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinct().toList()

    /** Cases cochées et total : pour l'avancement affiché sur la carte d'une liste. */
    fun checklistProgress(markdown: String): Pair<Int, Int> {
        var done = 0
        var total = 0
        for (line in markdown.split('\n')) {
            val m = CHECK.find(line) ?: continue
            total++
            if (m.groupValues[3] != " ") done++
        }
        return done to total
    }

    /** Le nombre de mots du texte rendu. */
    fun wordCount(markdown: String): Int =
        toPlainText(markdown).split(Regex("[\\s☐☑•]+")).count { it.any(Char::isLetterOrDigit) }
}
