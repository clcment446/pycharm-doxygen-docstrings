package io.github.clcment446.doxygen

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DoxygenParamInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(DoxygenParamInspection())
    }

    private fun messages(): List<String> =
        myFixture.doHighlighting().mapNotNull { it.description }.filter { "@param" in it }.sorted()

    fun testThreeWarnings() {
        myFixture.configureByText(
            "a.py",
            """
            def f(alpha, beta, gamma):
                ""${'"'}@brief Do it.

                @param alpha first.
                @param alpah typo.
                @param beta second.
                @param beta again.
                ""${'"'}
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                "@param 'alpah' does not match any parameter",
                "Duplicate @param 'beta'",
                "Missing @param for: gamma",
            ),
            messages(),
        )
    }

    fun testNoParamsIsNotFlagged() {
        myFixture.configureByText("a.py", "def f(a, b):\n    \"\"\"@brief Short.\"\"\"\n")
        assertEmpty(messages())
    }

    fun testSelfClsAndStarsIgnored() {
        myFixture.configureByText(
            "a.py",
            "class C:\n    def m(self, *args, **kw):\n        \"\"\"@param args a\n        @param kw b\n        \"\"\"\n",
        )
        assertEmpty(messages())
    }

    fun testAddMissingInSignatureOrder() {
        myFixture.configureByText(
            "a.py",
            "def <caret>f(a, b, c, d):\n    \"\"\"@brief X.\n\n    @param b B.\n    @param d D.\n    @return r\n    \"\"\"\n",
        )
        myFixture.launchAction(myFixture.findSingleIntention("Add missing @param tags"))
        myFixture.checkResult(
            "def f(a, b, c, d):\n    \"\"\"@brief X.\n\n    @param a \n    @param b B.\n    @param c \n    @param d D.\n    @return r\n    \"\"\"\n",
        )
    }

    fun testRenameUnknown() {
        myFixture.configureByText(
            "a.py",
            "def f(alpha):\n    \"\"\"@brief X.\n\n    @param al<caret>pah first.\n    \"\"\"\n",
        )
        myFixture.launchAction(myFixture.findSingleIntention("Rename to 'alpha'"))
        myFixture.checkResult("def f(alpha):\n    \"\"\"@brief X.\n\n    @param alpha first.\n    \"\"\"\n")
    }

    fun testRemoveUnknownWithContinuationLine() {
        myFixture.configureByText(
            "a.py",
            "def f(a):\n    \"\"\"@brief X.\n\n    @param a A.\n    @param zz<caret>z gone\n        continued.\n    @return r\n    \"\"\"\n",
        )
        myFixture.launchAction(myFixture.findSingleIntention("Remove @param tag"))
        myFixture.checkResult("def f(a):\n    \"\"\"@brief X.\n\n    @param a A.\n    @return r\n    \"\"\"\n")
    }

    fun testRemoveDuplicate() {
        myFixture.configureByText(
            "a.py",
            "def f(a):\n    \"\"\"@param a one.\n    @param <caret>a two.\n    \"\"\"\n",
        )
        myFixture.launchAction(myFixture.findSingleIntention("Remove @param tag"))
        myFixture.checkResult("def f(a):\n    \"\"\"@param a one.\n    \"\"\"\n")
    }
}
