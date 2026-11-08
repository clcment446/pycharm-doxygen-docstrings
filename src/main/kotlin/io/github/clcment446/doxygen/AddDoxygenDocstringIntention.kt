package io.github.clcment446.doxygen

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.application.options.CodeStyle
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyFunction

/** Alt+Enter on a function without docstring: insert a Doxygen stub. */
class AddDoxygenDocstringIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = "Add Doxygen docstring"
    override fun getText(): String = familyName

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val function = targetFunction(element) ?: return false
        return function.docStringExpression == null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val function = targetFunction(element) ?: return
        val file = function.containingFile
        val document = file.viewProvider.document ?: return
        val colon = function.node.findChildByType(PyTokenTypes.COLON) ?: return

        val defLine = document.getLineNumber(function.textRange.startOffset)
        val defLineStart = document.getLineStartOffset(defLine)
        val baseIndent = document.charsSequence.subSequence(defLineStart, function.textRange.startOffset)
            .takeWhile { it == ' ' || it == '\t' }.toString()
        val statements = function.statementList
        val bodyIndent = run {
            val first = statements.statements.firstOrNull()
            val sameLine = first != null && document.getLineNumber(first.textRange.startOffset) == defLine
            if (first != null && !sameLine) {
                val ls = document.getLineStartOffset(document.getLineNumber(first.textRange.startOffset))
                document.charsSequence.subSequence(ls, first.textRange.startOffset).toString()
            } else {
                val opts = CodeStyle.getIndentOptions(file)
                baseIndent + if (opts.USE_TAB_CHARACTER) "\t" else " ".repeat(opts.INDENT_SIZE)
            }
        }
        val bodyOnSameLine = statements.textLength > 0 &&
            document.getLineNumber(statements.textRange.startOffset) == defLine

        val lines = buildStub(function)
        val sb = StringBuilder()
        var caretInStub = -1
        lines.forEachIndexed { i, line ->
            sb.append('\n')
            if (line.isNotEmpty()) sb.append(bodyIndent)
            if (i == 0) caretInStub = sb.length + line.indexOf("@brief ") + "@brief ".length
            sb.append(line)
        }
        if (bodyOnSameLine) sb.append('\n').append(bodyIndent)

        val insertAt = colon.textRange.endOffset
        // Drop whitespace between ':' and a same-line body so it moves cleanly to the next line.
        val wsEnd = if (bodyOnSameLine) statements.textRange.startOffset else insertAt
        document.replaceString(insertAt, wsEnd, sb)
        PsiDocumentManager.getInstance(project).commitDocument(document)
        editor?.caretModel?.moveToOffset(insertAt + caretInStub)
    }

    companion object {
        fun targetFunction(element: PsiElement): PyFunction? {
            val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, false) ?: return null
            // Only on the signature, not anywhere in the body.
            val bodyStart = function.statementList.textRange.startOffset
            return if (element.textRange.startOffset < bodyStart || element.textRange.isEmpty) function else null
        }

        /** Stub lines without indentation; first line starts with the opening quotes. */
        fun buildStub(function: PyFunction): List<String> {
            val lines = mutableListOf("\"\"\"@brief ")
            val params = DoxygenDocstring.documentableParameters(function)
            val returnsNone = function.annotation?.value?.text?.trim() == "None"
            if (params.isNotEmpty() || !returnsNone) lines += ""
            params.forEach { lines += "@param $it " }
            if (!returnsNone) lines += "@return "
            lines += "\"\"\""
            return lines
        }
    }
}
