package io.github.clcment446.doxygen

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStringLiteralExpression

/** Checks that `@param` tags match the function signature. */
class DoxygenParamInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PyElementVisitor() {
            override fun visitPyFunction(node: PyFunction) {
                val doc = node.docStringExpression ?: return
                val tags = DoxygenDocstring.paramTags(doc.text)
                if (tags.isEmpty()) return
                val expected = DoxygenDocstring.documentableParameters(node)
                val seen = mutableSetOf<String>()
                for (tag in tags) {
                    when {
                        tag.name !in expected -> {
                            val undocumented = expected.filter { e -> tags.none { it.name == e } }
                            val fixes = undocumented.map<String, LocalQuickFix> { RenameParamFix(it) } + RemoveParamFix()
                            holder.registerProblem(
                                doc, tag.nameRange,
                                "@param '${tag.name}' does not match any parameter",
                                *fixes.toTypedArray(),
                            )
                        }
                        !seen.add(tag.name) -> holder.registerProblem(
                            doc, tag.nameRange, "Duplicate @param '${tag.name}'", RemoveParamFix(),
                        )
                    }
                }
                val missing = expected.filter { e -> tags.none { it.name == e } }
                if (missing.isNotEmpty()) {
                    val nameId = node.nameIdentifier ?: return
                    holder.registerProblem(
                        nameId,
                        "Missing @param for: ${missing.joinToString(", ")}",
                        ProblemHighlightType.WARNING,
                        AddMissingParamsFix(),
                    )
                }
            }
        }

    companion object {
        fun replaceLiteral(literal: PyStringLiteralExpression, newText: String) {
            val generator = PyElementGenerator.getInstance(literal.project)
            literal.replace(generator.createExpressionFromText(LanguageLevel.forElement(literal), newText))
        }

        /** The `@param` tag whose name range covers [range] (relative to the literal). */
        fun tagAt(text: String, range: TextRange): DoxygenDocstring.ParamTag? =
            DoxygenDocstring.paramTags(text).firstOrNull { it.nameRange.intersects(range) }
    }

    class RemoveParamFix : LocalQuickFix {
        override fun getFamilyName(): String = "Remove @param tag"
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val literal = descriptor.psiElement as? PyStringLiteralExpression ?: return
            val text = literal.text
            val tag = tagAt(text, descriptor.textRangeInElement) ?: return
            replaceLiteral(literal, text.removeRange(tag.blockRange.startOffset, tag.blockRange.endOffset))
        }
    }

    class RenameParamFix(private val newName: String) : LocalQuickFix {
        override fun getName(): String = "Rename to '$newName'"
        override fun getFamilyName(): String = "Rename @param tag"
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val literal = descriptor.psiElement as? PyStringLiteralExpression ?: return
            val text = literal.text
            val tag = tagAt(text, descriptor.textRangeInElement) ?: return
            replaceLiteral(literal, text.replaceRange(tag.nameRange.startOffset, tag.nameRange.endOffset, newName))
        }
    }

    class AddMissingParamsFix : LocalQuickFix {
        override fun getFamilyName(): String = "Add missing @param tags"
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val function = PsiTreeUtil.getParentOfType(descriptor.psiElement, PyFunction::class.java) ?: return
            val literal = function.docStringExpression ?: return
            val expected = DoxygenDocstring.documentableParameters(function)
            var text = literal.text
            for ((i, name) in expected.withIndex()) {
                val tags = DoxygenDocstring.paramTags(text)
                if (tags.any { it.name == name } || tags.isEmpty()) continue
                // After the closest preceding documented parameter, else before the first @param.
                val anchor = expected.subList(0, i).asReversed()
                    .firstNotNullOfOrNull { prev -> tags.lastOrNull { it.name == prev } }
                val ref = anchor ?: tags.first()
                val line = "${ref.indent}@param $name \n"
                text = if (anchor != null) {
                    val at = anchor.blockRange.endOffset
                    if (at > 0 && text[at - 1] == '\n') text.substring(0, at) + line + text.substring(at)
                    else text.substring(0, at) + "\n" + line.trimEnd('\n') + text.substring(at)
                } else {
                    text.substring(0, ref.blockRange.startOffset) + line + text.substring(ref.blockRange.startOffset)
                }
            }
            if (text != literal.text) replaceLiteral(literal, text)
        }
    }
}
