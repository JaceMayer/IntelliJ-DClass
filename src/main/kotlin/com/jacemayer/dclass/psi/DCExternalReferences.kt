package com.jacemayer.dclass.psi

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

interface DCExternalReferenceProvider {

    fun referencesFor(element: PsiElement): Array<PsiReference>

    companion object {
        val EP: ExtensionPointName<DCExternalReferenceProvider> =
            ExtensionPointName.create("com.jacemayer.dclass.externalReferenceProvider")

        fun collect(element: PsiElement): Array<PsiReference> {
            val providers = EP.extensionList
            if (providers.isEmpty()) return PsiReference.EMPTY_ARRAY
            val all = mutableListOf<PsiReference>()
            for (p in providers) all += p.referencesFor(element)
            return if (all.isEmpty()) PsiReference.EMPTY_ARRAY else all.toTypedArray()
        }
    }
}
