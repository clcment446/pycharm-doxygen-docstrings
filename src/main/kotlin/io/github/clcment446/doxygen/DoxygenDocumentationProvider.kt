package io.github.clcment446.doxygen

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDocStringOwner
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction

/**
 * Quick Documentation (Ctrl+Q) for Doxygen docstrings. Registered with order="first" before the
 * Python provider; returns null (falls back to the default rendering) when the docstring has no
 * Doxygen tags or when the registry key `doxygen.docstrings.quickDoc` is off.
 */
class DoxygenDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (!enabled()) return null
        val owner = element as? PyDocStringOwner ?: return null
        val doc = owner.docStringValue ?: return null
        if (!DoxygenDocstring.hasDoxygenTags(doc)) return null
        return render(signature(owner), DoxygenDocstring.parse(doc))
    }

    companion object {
        const val REGISTRY_KEY: String = "doxygen.docstrings.quickDoc"

        fun enabled(): Boolean = try {
            Registry.`is`(REGISTRY_KEY, true)
        } catch (_: Throwable) {
            true
        }

        private fun esc(s: String): String = StringUtil.escapeXmlEntities(s)
        private fun oneLine(s: String): String = s.replace(Regex("""\s+"""), " ").trim()

        fun signature(owner: PyDocStringOwner): String = when (owner) {
            is PyFunction -> buildString {
                owner.containingClass?.name?.let { append(esc(it)).append('.') }
                if (owner.isAsync) append("async ")
                append("def <b>").append(esc(owner.name ?: "")).append("</b>")
                append(esc(oneLine(owner.parameterList.text)))
                owner.annotation?.value?.text?.let { append(" -&gt; ").append(esc(oneLine(it))) }
            }
            is PyClass -> buildString {
                append("class <b>").append(esc(owner.name ?: "")).append("</b>")
                owner.superClassExpressionList?.text?.let { append(esc(oneLine(it))) }
            }
            is PyFile -> "module <b>${esc(owner.name)}</b>"
            else -> esc((owner as? PsiElement)?.text?.lineSequence()?.firstOrNull() ?: "")
        }

        fun render(signature: String, d: DoxygenDocstring.Parsed): String = buildString {
            append(DocumentationMarkup.DEFINITION_START).append(signature).append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.CONTENT_START)
            if (d.brief.isNotEmpty()) append("<p>").append(esc(d.brief)).append("</p>")
            d.paragraphs.forEach { append("<p>").append(esc(it)).append("</p>") }
            append(DocumentationMarkup.CONTENT_END)

            val sections = StringBuilder()
            fun section(title: String, body: String) {
                sections.append(DocumentationMarkup.SECTION_HEADER_START).append(title)
                    .append(DocumentationMarkup.SECTION_SEPARATOR).append(body)
                    .append(DocumentationMarkup.SECTION_END)
            }
            fun table(entries: List<DoxygenDocstring.Entry>): String = entries.joinToString("", "<table>", "</table>") {
                "<tr><td><code>${esc(it.name)}</code></td><td>${esc(it.description)}</td></tr>"
            }
            if (d.params.isNotEmpty()) section("Params:", table(d.params))
            d.returns?.let { section("Returns:", esc(it).replace("&lt;br&gt;", "<br>")) }
            if (d.raises.isNotEmpty()) section("Raises:", table(d.raises))
            d.others.groupBy { it.name }.forEach { (tag, items) ->
                section(tag.replaceFirstChar { it.uppercase() } + ":", items.joinToString("<br>") { esc(it.description) })
            }
            if (sections.isNotEmpty()) {
                append(DocumentationMarkup.SECTIONS_START).append(sections).append(DocumentationMarkup.SECTIONS_END)
            }
        }
    }
}
