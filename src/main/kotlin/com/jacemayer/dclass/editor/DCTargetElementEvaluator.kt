package com.jacemayer.dclass.editor

import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.psi.PsiElement
import com.intellij.util.ThreeState
import com.jacemayer.dclass.DCLanguage
import com.jacemayer.dclass.psi.DCNamedElement

class DCTargetElementEvaluator : TargetElementEvaluatorEx2() {

    override fun isAcceptableReferencedElement(element: PsiElement, referenceOrReferencedElement: PsiElement?): ThreeState {
        val owner = element.parent as? DCNamedElement ?: return ThreeState.UNSURE
        if (owner.nameIdentifier != element) return ThreeState.UNSURE
        if (referenceOrReferencedElement == null || referenceOrReferencedElement.language == DCLanguage) return ThreeState.UNSURE
        return ThreeState.NO
    }

    override fun getNamedElement(element: PsiElement): PsiElement? {
        val owner = element.parent as? DCNamedElement ?: return null
        return if (owner.nameIdentifier == element) owner else null
    }
}
