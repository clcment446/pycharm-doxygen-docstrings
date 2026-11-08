package io.github.clcment446.doxygen

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyDocStringOwner
import com.jetbrains.python.psi.PyStringLiteralExpression

/** Colors `@tag` keywords and their argument inside Python docstrings. */
class DoxygenAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PyStringLiteralExpression || !isDocstring(element)) return
        val base = element.textRange.startOffset
        for (tag in DoxygenDocstring.tags(element.text)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(tag.tagRange.shiftRight(base))
                .textAttributes(DoxygenColors.TAG)
                .create()
            tag.argRange?.let {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(it.shiftRight(base))
                    .textAttributes(DoxygenColors.PARAM_NAME)
                    .create()
            }
        }
    }

    companion object {
        fun isDocstring(literal: PyStringLiteralExpression): Boolean {
            val owner = PsiTreeUtil.getParentOfType(literal, PyDocStringOwner::class.java) ?: return false
            return owner.docStringExpression == literal
        }
    }
}
