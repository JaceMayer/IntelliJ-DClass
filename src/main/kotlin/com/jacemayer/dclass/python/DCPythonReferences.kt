package com.jacemayer.dclass.python

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import com.jacemayer.dclass.psi.*
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import com.jetbrains.python.psi.types.TypeEvalContext

class DCPythonReferenceProvider : DCExternalReferenceProvider {
    override fun referencesFor(element: PsiElement): Array<PsiReference> =
        DCPythonLinks.referencesFor(element)
}

object DCPythonLinks {

    fun referencesFor(element: PsiElement): Array<PsiReference> = when (element) {
        is DCImportSymbol -> forImportSymbol(element)
        is DCClassDecl -> forClassDecl(element)
        is DCFieldDecl -> forFieldDecl(element)
        else -> PsiReference.EMPTY_ARRAY
    }

    fun forImportSymbol(symbol: DCImportSymbol): Array<PsiReference> {
        val names = symbol.pythonClassNames
        val ranges = symbol.partRanges
        if (names.size != ranges.size) return PsiReference.EMPTY_ARRAY
        return names.indices.map { i ->
            DCPythonClassReference(symbol, TextRange(ranges[i].first, ranges[i].second), listOf(names[i]))
        }.toTypedArray()
    }

    fun forClassDecl(cls: DCClassDecl): Array<PsiReference> {
        if (cls.isStruct) return PsiReference.EMPTY_ARRAY
        val name = cls.name ?: return PsiReference.EMPTY_ARRAY
        val range = nameRangeIn(cls, cls.nameIdentifier) ?: return PsiReference.EMPTY_ARRAY
        return arrayOf(DCPythonClassReference(cls, range, perspectivesOf(name)))
    }

    fun forFieldDecl(field: DCFieldDecl): Array<PsiReference> {
        val owner = field.parentOfType<DCClassDecl>() ?: return PsiReference.EMPTY_ARRAY
        if (owner.isStruct) return PsiReference.EMPTY_ARRAY
        val ownerName = owner.name ?: return PsiReference.EMPTY_ARRAY
        val fieldName = field.name ?: return PsiReference.EMPTY_ARRAY
        val range = nameRangeIn(field, field.nameIdentifier) ?: return PsiReference.EMPTY_ARRAY
        return arrayOf(DCPythonMethodReference(field, range, perspectivesOf(ownerName), fieldName))
    }

    private fun nameRangeIn(owner: PsiElement, name: PsiElement?): TextRange? {
        name ?: return null
        val start = name.textRange.startOffset - owner.textRange.startOffset
        if (start < 0) return null
        return TextRange(start, start + name.textLength)
    }
}

internal fun perspectivesOf(dclassName: String): List<String> =
    listOf(dclassName, "${dclassName}AI", "${dclassName}UD", "${dclassName}OV")

private fun findClasses(element: PsiElement, names: List<String>): List<PyClass> {
    val project = element.project
    val scope = GlobalSearchScope.allScope(project)
    return names.flatMap { PyClassNameIndex.find(it, project, scope) }
}

internal class DCPythonClassReference(
    element: PsiElement,
    range: TextRange,
    private val classNames: List<String>,
) : PsiPolyVariantReferenceBase<PsiElement>(element, range) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        findClasses(element, classNames)
            .map { PsiElementResolveResult(it) }
            .toTypedArray()

    override fun handleElementRename(newElementName: String): PsiElement = element
}

internal class DCPythonMethodReference(
    element: PsiElement,
    range: TextRange,
    private val classNames: List<String>,
    private val methodName: String,
) : PsiPolyVariantReferenceBase<PsiElement>(element, range) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val context = TypeEvalContext.codeInsightFallback(element.project)
        return findClasses(element, classNames)
            .mapNotNull { it.findMethodByName(methodName, true, context) }
            .distinct()
            .map { PsiElementResolveResult(it) }
            .toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement = element
}
