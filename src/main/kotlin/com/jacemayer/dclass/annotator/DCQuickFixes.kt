package com.jacemayer.dclass.annotator

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jacemayer.dclass.psi.DCFile
import com.jacemayer.dclass.psi.DCImportDecl
import kotlin.math.max
import kotlin.math.min


class DCReplaceWithFix(
    element: PsiElement,
    private val replacement: String,
    private val rank: com.intellij.codeInsight.intention.PriorityAction.Priority =
        com.intellij.codeInsight.intention.PriorityAction.Priority.NORMAL,
) : LocalQuickFixAndIntentionActionOnPsiElement(element),
    com.intellij.codeInsight.intention.PriorityAction {

    override fun getPriority(): com.intellij.codeInsight.intention.PriorityAction.Priority = rank

    override fun getText(): String = "Change to '$replacement'"
    override fun getFamilyName(): String = "Change to the suggested name"

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement,
    ) {
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val range = startElement.textRange
        document.replaceString(range.startOffset, range.endOffset, replacement)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}

class DCDeclareKeywordFix(
    element: PsiElement,
    private val keyword: String,
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

    override fun getText(): String = "Declare 'keyword $keyword;'"
    override fun getFamilyName(): String = "Declare the keyword"

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement,
    ) {
        val dcFile = file as? DCFile ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

        val afterLastKeyword = dcFile.keywordDecls.maxByOrNull { it.textRange.endOffset }
        val afterLastImport = dcFile.children.filterIsInstance<DCImportDecl>()
            .maxByOrNull { it.textRange.endOffset }

        val text: String
        val offset: Int
        when {
            afterLastKeyword != null -> {
                offset = afterLastKeyword.textRange.endOffset
                text = "\nkeyword $keyword;"
            }
            afterLastImport != null -> {
                offset = afterLastImport.textRange.endOffset
                text = "\n\nkeyword $keyword;"
            }
            else -> {
                offset = 0
                text = "keyword $keyword;\n\n"
            }
        }
        document.insertString(offset, text)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}


class DCCreateDeclarationFix(
    element: PsiElement,
    private val name: String,
    private val kind: Kind,
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

    enum class Kind(val keyword: String) { DCLASS("dclass"), STRUCT("struct") }

    override fun getText(): String = "Create ${kind.keyword} '$name'"
    override fun getFamilyName(): String = "Create the missing declaration"

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement,
    ) {
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val existing = document.text
        val separator = if (existing.endsWith("\n")) "\n" else "\n\n"
        document.insertString(document.textLength, "$separator${kind.keyword} $name {\n};\n")
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}


object DCSuggest {

    fun closeTo(name: String, candidates: Collection<String>, limit: Int = 2): List<String> {
        if (name.isEmpty()) return emptyList()
        val threshold = min(2, max(1, name.length / 3))
        return candidates
            .asSequence()
            .filter { it != name }
            .map { it to distance(name, it) }
            .filter { (_, d) -> d <= threshold }
            .sortedWith(compareBy({ it.second }, { it.first }))
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun distance(a: String, b: String): Int {
        if (a == b) return 0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
