package com.jacemayer.dclass.editor

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.parentOfType
import com.jacemayer.dclass.lexer.DCLexer
import com.jacemayer.dclass.lexer.DCTokenTypes
import com.jacemayer.dclass.psi.DCClassDecl
import com.jacemayer.dclass.psi.DCFieldDecl
import com.jacemayer.dclass.psi.DCTypedefDecl

class DCFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner(): WordsScanner =
        DefaultWordsScanner(
            DCLexer(),
            TokenSet.create(DCTokenTypes.IDENTIFIER),
            DCTokenTypes.COMMENTS,
            DCTokenTypes.STRING_LITERALS,
        )

    override fun canFindUsagesFor(element: PsiElement): Boolean = when (element) {
        is DCClassDecl -> element.name != null
        is DCTypedefDecl -> element.name != null
        is DCFieldDecl -> element.name != null
        else -> false
    }

    override fun getHelpId(element: PsiElement): String? = null

    override fun getType(element: PsiElement): String = when (element) {
        is DCClassDecl -> if (element.isStruct) "struct" else "dclass"
        is DCTypedefDecl -> "typedef"
        is DCFieldDecl -> "field"
        else -> ""
    }

    override fun getDescriptiveName(element: PsiElement): String = when (element) {
        is DCFieldDecl -> listOfNotNull(element.parentOfType<DCClassDecl>()?.name, element.name).joinToString(".")
        is DCClassDecl -> element.name ?: ""
        is DCTypedefDecl -> element.name ?: ""
        else -> ""
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String =
        if (useFullName) getDescriptiveName(element) else (element as? com.intellij.psi.PsiNamedElement)?.name ?: ""
}
