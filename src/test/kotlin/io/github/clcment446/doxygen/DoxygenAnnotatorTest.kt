package io.github.clcment446.doxygen

import com.intellij.openapi.options.colors.ColorSettingsPages
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DoxygenAnnotatorTest : BasePlatformTestCase() {

    fun testTagsAndParamNamesHighlightedOnlyInDocstrings() {
        myFixture.configureByText(
            "a.py",
            "def f(a):\n    \"\"\"@brief X.\n\n    @param a A.\n    @throws ValueError bad.\n    \"\"\"\n    s = \"@brief not a docstring\"\n",
        )
        val infos = myFixture.doHighlighting()
        val text = myFixture.editor.document.text
        fun highlighted(key: Any) = infos.filter { it.forcedTextAttributesKey == key }
            .map { text.substring(it.startOffset, it.endOffset) }
        assertEquals(listOf("@brief", "@param", "@throws"), highlighted(DoxygenColors.TAG))
        assertEquals(listOf("a", "ValueError"), highlighted(DoxygenColors.PARAM_NAME))
    }

    fun testColorSettingsPageRegistered() {
        assertTrue(ColorSettingsPages.getInstance().registeredPages.any { it is DoxygenColorSettingsPage })
    }
}
