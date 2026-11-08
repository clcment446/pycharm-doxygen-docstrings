package io.github.clcment446.doxygen

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.psi.PyFunction

class DoxygenDocumentationProviderTest : BasePlatformTestCase() {

    private val sample = """
        def solve(instance: str, limit: float = 1.0) -> list[int]:
            ""${'"'}@brief Solve the <instance>.

            Uses a greedy start then local search.

            @param instance problem to solve.
            @param limit time limit
                in seconds.
            @return best tour found.
            @throws ValueError if the instance is empty.
            @note Deterministic.
            ""${'"'}
            return []
    """.trimIndent()

    private fun function(): PyFunction {
        val file = myFixture.configureByText("a.py", sample)
        return PsiTreeUtil.findChildOfType(file, PyFunction::class.java)!!
    }

    fun testRenderedHtml() {
        val html = DoxygenDocumentationProvider().generateDoc(function(), null)!!
        val expected = "<div class='definition'><pre>def <b>solve</b>(instance: str, limit: float = 1.0) -&gt; list[int]</pre></div>" +
            "<div class='content'><p>Solve the &lt;instance&gt;.</p><p>Uses a greedy start then local search.</p></div>" +
            "<table class='sections'>" +
            "<tr><td valign='top' class='section'><p>Params:</td><td valign='top'><table>" +
            "<tr><td><code>instance</code></td><td>problem to solve.</td></tr>" +
            "<tr><td><code>limit</code></td><td>time limit in seconds.</td></tr></table></td>" +
            "<tr><td valign='top' class='section'><p>Returns:</td><td valign='top'>best tour found.</td>" +
            "<tr><td valign='top' class='section'><p>Raises:</td><td valign='top'><table>" +
            "<tr><td><code>ValueError</code></td><td>if the instance is empty.</td></tr></table></td>" +
            "<tr><td valign='top' class='section'><p>Note:</td><td valign='top'>Deterministic.</td>" +
            "</table>"
        assertEquals(expected, html)
    }

    /** Guards the PyCharm wiring: our provider must win over the Python plugin's for Ctrl+Q. */
    fun testPlatformPicksOurProviderFirst() {
        val fn = function()
        val provider = DocumentationManager.getProviderFromElement(fn)
        val html = provider.generateDoc(fn, null)!!
        assertTrue(html, html.contains("<code>instance</code>"))
        assertTrue(html, html.contains("Raises:"))
    }

    fun testFallsBackWithoutTags() {
        val file = myFixture.configureByText("a.py", "def f(x):\n    \"\"\"Plain docstring.\"\"\"\n")
        val fn = PsiTreeUtil.findChildOfType(file, PyFunction::class.java)!!
        assertNull(DoxygenDocumentationProvider().generateDoc(fn, null))
        // The Python plugin's provider still renders it (its own markup, not ours).
        val html = DocumentationManager.getProviderFromElement(fn).generateDoc(fn, null)
        assertNotNull(html)
        assertTrue(html!!, html.contains("psi_element://#module#a"))
        assertFalse(html, html.contains("<div class='definition'><pre>def <b>f</b>"))
    }

    fun testRegistryKeyDisables() {
        val fn = function()
        Registry.get(DoxygenDocumentationProvider.REGISTRY_KEY).setValue(false, testRootDisposable)
        assertNull(DoxygenDocumentationProvider().generateDoc(fn, null))
    }
}
