package com.jacemayer.dclass.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jacemayer.dclass.lexer.DCTokenTypes
import com.jacemayer.dclass.parser.DCElementTypes as E
import com.jacemayer.dclass.psi.*


class DCFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(
        root: PsiElement,
        document: Document,
        quick: Boolean,
    ): Array<FoldingDescriptor> {
        if (root !is DCFile) return emptyArray()
        val descriptors = mutableListOf<FoldingDescriptor>()

        foldImportRun(root, document, descriptors)

        for (cls in PsiTreeUtil.findChildrenOfType(root, DCClassDecl::class.java)) {
            val body = cls.body ?: continue
            addIfMultiline(descriptors, body.node, body.textRange, document, placeholderFor(cls))
        }

        for (node in PsiTreeUtil.findChildrenOfAnyType(root, DCSwitchDecl::class.java)) {
            val lbrace = node.node.findChildByType(DCTokenTypes.LBRACE) ?: continue
            val rbrace = node.node.findChildByType(DCTokenTypes.RBRACE) ?: continue
            addIfMultiline(
                descriptors,
                node.node,
                TextRange(lbrace.startOffset, rbrace.textRange.endOffset),
                document,
                "{...}",
            )
        }

        for (comment in PsiTreeUtil.findChildrenOfType(root, PsiElement::class.java)) {
            if (comment.node?.elementType != DCTokenTypes.BLOCK_COMMENT) continue
            addIfMultiline(descriptors, comment.node, comment.textRange, document, "/*...*/")
        }

        return descriptors.toTypedArray()
    }

    private fun foldImportRun(
        file: DCFile,
        document: Document,
        out: MutableList<FoldingDescriptor>,
    ) {
        val runs = mutableListOf<MutableList<DCImportDecl>>()
        var current: MutableList<DCImportDecl>? = null
        for (child in file.children) {
            when {
                child is DCImportDecl -> {
                    if (current == null) {
                        current = mutableListOf()
                        runs.add(current)
                    }
                    current.add(child)
                }
                child.node.elementType in SKIPPABLE -> Unit
                else -> current = null
            }
        }

        for (run in runs) {
            if (run.size < 2) continue
            val range = TextRange(run.first().textOffset, run.last().textRange.endOffset)
            addIfMultiline(out, run.first().node, range, document, "import ...")
        }
    }

    private fun addIfMultiline(
        out: MutableList<FoldingDescriptor>,
        node: ASTNode,
        range: TextRange,
        document: Document,
        placeholder: String,
    ) {
        if (range.isEmpty || range.endOffset > document.textLength) return
        if (document.getLineNumber(range.startOffset) == document.getLineNumber(range.endOffset)) return
        out += FoldingDescriptor(node, range, null, placeholder)
    }

    private fun placeholderFor(cls: DCClassDecl): String {
        val count = cls.fields.size
        return when (count) {
            0 -> "{ }"
            1 -> "{ 1 field }"
            else -> "{ $count fields }"
        }
    }

    override fun getPlaceholderText(node: ASTNode): String = "..."

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false

    private companion object {
        val SKIPPABLE = setOf(
            com.intellij.psi.TokenType.WHITE_SPACE,
            DCTokenTypes.LINE_COMMENT,
            DCTokenTypes.BLOCK_COMMENT,
            DCTokenTypes.SEMICOLON,
        )
    }
}
