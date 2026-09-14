package com.jacemayer.dclass.editor

import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import com.jacemayer.dclass.DCLanguage
import com.jacemayer.dclass.psi.DCClassDecl
import com.jacemayer.dclass.psi.DCSwitchDecl
import javax.swing.Icon

class DCBreadcrumbsProvider : BreadcrumbsProvider {

    override fun getLanguages(): Array<Language> = arrayOf(DCLanguage)

    override fun acceptElement(element: PsiElement): Boolean =
        element is DCClassDecl || element is DCSwitchDecl

    override fun getElementInfo(element: PsiElement): String = when (element) {
        is DCClassDecl -> "${if (element.isStruct) "struct" else "dclass"} ${element.name ?: ""}".trim()
        is DCSwitchDecl -> element.name?.let { "switch $it" } ?: "switch (${element.keyParameter ?: ""})"
        else -> ""
    }

    override fun getElementIcon(element: PsiElement): Icon? = when (element) {
        is DCClassDecl -> if (element.isStruct) AllIcons.Nodes.Static else AllIcons.Nodes.Class
        is DCSwitchDecl -> AllIcons.Nodes.Enum
        else -> null
    }
}
