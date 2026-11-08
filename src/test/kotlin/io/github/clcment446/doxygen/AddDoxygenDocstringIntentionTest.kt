package io.github.clcment446.doxygen

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AddDoxygenDocstringIntentionTest : BasePlatformTestCase() {

    private fun doTest(before: String, after: String) {
        myFixture.configureByText("a.py", before)
        val action = myFixture.findSingleIntention("Add Doxygen docstring")
        myFixture.launchAction(action)
        myFixture.checkResult(after)
    }

    fun testMethodWithSelfArgsAndNoneReturn() = doTest(
        """
        class C:
            def r<caret>un(self, x: int, *args, **kwargs) -> None:
                pass
        """.trimIndent(),
        "class C:\n" +
            "    def run(self, x: int, *args, **kwargs) -> None:\n" +
            "        \"\"\"@brief <caret>\n" +
            "\n" +
            "        @param x \n" +
            "        @param args \n" +
            "        @param kwargs \n" +
            "        \"\"\"\n" +
            "        pass",
    )

    fun testReturnKeptWhenNotNone() = doTest(
        "def f<caret>(a, b) -> int:\n    return a\n",
        "def f(a, b) -> int:\n    \"\"\"@brief <caret>\n\n    @param a \n    @param b \n    @return \n    \"\"\"\n    return a\n",
    )

    fun testClassmethodSkipsCls() = doTest(
        "class C:\n    @classmethod\n    def m<caret>k(cls):\n        return cls()\n",
        "class C:\n    @classmethod\n    def mk(cls):\n        \"\"\"@brief <caret>\n\n        @return \n        \"\"\"\n        return cls()\n",
    )

    fun testNotAvailableWhenDocstringExists() {
        myFixture.configureByText("a.py", "def f<caret>():\n    \"\"\"Doc.\"\"\"\n")
        assertEmpty(myFixture.filterAvailableIntentions("Add Doxygen docstring"))
    }

    fun testNotAvailableInBody() {
        myFixture.configureByText("a.py", "def f():\n    x = 1<caret>\n")
        assertEmpty(myFixture.filterAvailableIntentions("Add Doxygen docstring"))
    }
}
