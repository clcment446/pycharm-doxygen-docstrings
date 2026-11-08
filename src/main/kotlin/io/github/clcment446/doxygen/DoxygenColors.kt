package io.github.clcment446.doxygen

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.jetbrains.python.PythonFileType
import javax.swing.Icon

object DoxygenColors {
    val TAG: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DOXYGEN_PY_TAG", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG,
    )
    val PARAM_NAME: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DOXYGEN_PY_TAG_VALUE", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG_VALUE,
    )
}

/** Settings → Editor → Color Scheme → Doxygen (Python). */
class DoxygenColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon? = PythonFileType.INSTANCE.icon
    override fun getHighlighter(): SyntaxHighlighter = PlainSyntaxHighlighter()
    override fun getDisplayName(): String = "Doxygen (Python)"
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = arrayOf(
        AttributesDescriptor("Tag (@brief, @param, ...)", DoxygenColors.TAG),
        AttributesDescriptor("Tag argument (parameter / exception name)", DoxygenColors.PARAM_NAME),
    )
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> =
        mapOf("tag" to DoxygenColors.TAG, "arg" to DoxygenColors.PARAM_NAME)

    override fun getDemoText(): String = """
        def solve(instance, time_limit):
            ""${'"'}<tag>@brief</tag> Solve the instance.

            <tag>@param</tag> <arg>instance</arg> problem to solve.
            <tag>@param</tag> <arg>time_limit</arg> limit in seconds.
            <tag>@return</tag> best solution found.
            <tag>@throws</tag> <arg>ValueError</arg> if the instance is empty.
            ""${'"'}
    """.trimIndent()
}
