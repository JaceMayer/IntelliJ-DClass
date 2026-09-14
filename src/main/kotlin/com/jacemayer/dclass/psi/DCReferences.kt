package com.jacemayer.dclass.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.util.parentOfType
import com.jacemayer.dclass.annotator.DCIndex

class DCReference(
    element: PsiElement,
    private val resolver: (PsiElement) -> PsiElement?,
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength)) {

    override fun resolve(): PsiElement? = resolver(element)

    override fun handleElementRename(newElementName: String): PsiElement = element
}

internal fun resolveParentRef(element: PsiElement): PsiElement? {
    val file = element.containingFile as? DCFile ?: return null
    val name = (element as? DCParentRef)?.referencedName ?: return null
    return DCIndex.visibleDeclarations(file).classes[name]
}

internal fun resolveTypeRef(element: PsiElement): PsiElement? {
    val file = element.containingFile as? DCFile ?: return null
    val name = (element as? DCTypeRef)?.referencedName ?: return null
    val decls = DCIndex.visibleDeclarations(file)
    return decls.classes[name] ?: decls.typedefs[name]
}

internal fun resolveMolecularRef(element: PsiElement): PsiElement? {
    val file = element.containingFile as? DCFile ?: return null
    val cls = element.parentOfType<DCClassDecl>() ?: return null
    val name = (element as? DCMolecularRef)?.referencedName ?: return null
    return DCIndex.visibleDeclarations(file).findFieldInHierarchy(cls, name)
}
