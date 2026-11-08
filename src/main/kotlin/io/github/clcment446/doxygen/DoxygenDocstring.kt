package io.github.clcment446.doxygen

import com.intellij.openapi.util.TextRange
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter

/** Pure-text helpers for Doxygen-style Python docstrings (no PSI mutation). */
object DoxygenDocstring {
    /** Tags recognised for highlighting and for deciding that a docstring is "Doxygen". */
    val KNOWN_TAGS: Set<String> = setOf(
        "file", "brief", "details", "param", "tparam", "return", "returns", "retval",
        "raise", "raises", "throw", "throws", "exception", "note", "warning", "see", "sa",
        "todo", "deprecated", "since", "author", "date", "version", "pre", "post",
        "invariant", "example", "code", "endcode", "attention", "bug", "remark", "remarks",
        "copyright", "class", "fn", "var", "namespace", "package",
    )

    /** Tags whose first word is an argument (parameter or exception name). */
    val ARG_TAGS: Set<String> = setOf("param", "tparam", "raise", "raises", "throw", "throws", "exception", "retval")

    /** `@tag` or `\tag`, optional `[in,out]` direction, optional argument word. */
    val TAG_REGEX: Regex = Regex("""(?<![\w@\\])[@\\]([A-Za-z]+)(\[[^\]\n]*])?(?:[ \t]+(\*{0,2}[A-Za-z_][\w.]*))?""")

    private val PARAM_REGEX = Regex("""(?<![\w@\\])[@\\]param(?:\[[^\]\n]*])?[ \t]+(\*{0,2}[A-Za-z_]\w*)""")

    data class TagMatch(val name: String, val tagRange: TextRange, val argRange: TextRange?, val arg: String?)

    /** A `@param` occurrence inside a string literal's text. Ranges are relative to that text. */
    data class ParamTag(val name: String, val nameRange: TextRange, val blockRange: TextRange, val indent: String)

    fun tags(text: String): List<TagMatch> = TAG_REGEX.findAll(text).mapNotNull { m ->
        val name = m.groupValues[1]
        if (name !in KNOWN_TAGS) return@mapNotNull null
        val tagEnd = m.groups[2]?.range?.last?.plus(1) ?: (m.groups[1]!!.range.last + 1)
        val argGroup = m.groups[3]?.takeIf { name in ARG_TAGS }
        TagMatch(
            name,
            TextRange(m.range.first, tagEnd),
            argGroup?.let { TextRange(it.range.first, it.range.last + 1) },
            argGroup?.value,
        )
    }.toList()

    fun hasDoxygenTags(text: String?): Boolean = text != null && tags(text).isNotEmpty()

    fun normalize(name: String): String = name.trimStart('*')

    /** Parameter names a docstring should document, in signature order. */
    fun documentableParameters(function: PyFunction): List<String> =
        function.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .mapNotNull { it.name }
            .filter { it != "self" && it != "cls" }

    fun paramTags(text: String): List<ParamTag> = PARAM_REGEX.findAll(text).map { m ->
        val tagStart = m.range.first
        val lineStart = text.lastIndexOf('\n', tagStart - 1) + 1
        val prefix = text.substring(lineStart, tagStart)
        val ownLine = prefix.isBlank()
        val start = if (ownLine) lineStart else tagStart
        val name = m.groups[1]!!
        ParamTag(
            normalize(name.value),
            TextRange(name.range.first, name.range.last + 1),
            TextRange(start, blockEnd(text, m.range.last + 1)),
            if (ownLine) prefix else "",
        )
    }.toList()

    /** End (exclusive) of a tag block: start of the next blank / tag / closing-quote line. */
    private fun blockEnd(text: String, from: Int): Int {
        var nl = text.indexOf('\n', from)
        if (nl < 0) return closingQuoteStart(text, from)
        while (true) {
            val lineStart = nl + 1
            val next = text.indexOf('\n', lineStart)
            val line = if (next < 0) text.substring(lineStart) else text.substring(lineStart, next)
            val t = line.trim()
            if (next < 0 || t.isEmpty() || t.startsWith("@") || t.startsWith("\\") ||
                t.startsWith("\"\"\"") || t.startsWith("'''")
            ) return lineStart
            nl = next
        }
    }

    private fun closingQuoteStart(text: String, from: Int): Int {
        val q = listOf("\"\"\"", "'''").map { text.lastIndexOf(it) }.filter { it >= from }.minOrNull()
        return q ?: text.length
    }

    // ---- structured parsing for rendering -------------------------------------------------

    data class Entry(val name: String, val description: String)

    data class Parsed(
        val brief: String,
        val paragraphs: List<String>,
        val params: List<Entry>,
        val returns: String?,
        val raises: List<Entry>,
        /** Other tags (note, see, warning, ...) in order: tag name to text. */
        val others: List<Entry>,
    )

    private val LINE_TAG = Regex("""^[@\\]([A-Za-z]+)(\[[^\]]*])?\s*(.*)$""")

    fun parse(docstring: String): Parsed {
        var brief: String? = null
        val paragraphs = mutableListOf<String>()
        val params = mutableListOf<Entry>()
        var returns: String? = null
        val raises = mutableListOf<Entry>()
        val others = mutableListOf<Entry>()

        var curTag: String? = null
        val buf = StringBuilder()

        fun flush() {
            val body = buf.toString().trim()
            buf.setLength(0)
            val tag = curTag
            curTag = null
            if (tag == null) {
                if (body.isEmpty()) return
                if (brief == null) brief = body else paragraphs += body
                return
            }
            when (tag) {
                "brief" -> if (brief == null) brief = body else paragraphs += body
                "details" -> if (body.isNotEmpty()) paragraphs += body
                "param", "tparam" -> splitArg(body)?.let { params += it }
                "return", "returns", "retval" -> returns = listOfNotNull(returns, body).joinToString("<br>")
                "raise", "raises", "throw", "throws", "exception" ->
                    raises += splitArg(body) ?: Entry(body, "")
                "file" -> Unit
                else -> others += Entry(tag, body)
            }
        }

        for (raw in docstring.lines()) {
            val line = raw.trim()
            val m = LINE_TAG.find(line)
            if (m != null && m.groupValues[1] in KNOWN_TAGS) {
                flush()
                curTag = m.groupValues[1]
                buf.append(m.groupValues[3])
            } else if (line.isEmpty()) {
                flush()
            } else {
                if (buf.isNotEmpty()) buf.append(' ')
                buf.append(line)
            }
        }
        flush()
        return Parsed(brief ?: "", paragraphs, params, returns, raises, others)
    }

    private fun splitArg(body: String): Entry? {
        if (body.isBlank()) return null
        val parts = body.split(Regex("""\s+"""), limit = 2)
        return Entry(parts[0], parts.getOrElse(1) { "" })
    }
}
