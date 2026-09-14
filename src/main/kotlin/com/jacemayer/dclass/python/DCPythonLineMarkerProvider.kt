package com.jacemayer.dclass.python

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.jacemayer.dclass.DCIcons
import com.jacemayer.dclass.psi.DCClassDecl
import com.jacemayer.dclass.psi.DCFieldDecl
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import javax.swing.Icon

class DCPythonLineMarkerProvider : LineMarkerProviderDescriptor() {

    override fun getName(): String = "DClass declaration"

    override fun getIcon(): Icon = DCIcons.FILE

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        for (element in elements) {
            ProgressManager.checkCanceled()
            if (element.node?.elementType != PyTokenTypes.IDENTIFIER) continue
            when (val parent = element.parent) {
                is PyClass -> if (parent.nameIdentifier == element) markClass(parent, element, result)
                is PyFunction -> if (parent.nameIdentifier == element) markFunction(parent, element, result)
            }
        }
    }

    private fun markClass(cls: PyClass, anchor: PsiElement, result: MutableCollection<in LineMarkerInfo<*>>) {
        val dclass = DCDistributedModel.dclassFor(cls) ?: return
        result += NavigationGutterIconBuilder.create(DCIcons.FILE)
            .setTarget(dclass)
            .setTooltipText("Declared as dclass ${dclass.name}")
            .createLineMarkerInfo(anchor)
    }

    private fun markFunction(function: PyFunction, anchor: PsiElement, result: MutableCollection<in LineMarkerInfo<*>>) {
        val fields = DCDistributedModel.implementedFields(function)
        if (fields.isEmpty()) return
        result += NavigationGutterIconBuilder.create(DCIcons.FILE)
            .setTargets(fields)
            .setPopupTitle("DClass Fields Implemented by ${function.name}")
            .setTooltipText(tooltip(fields))
            .setCellRenderer { DCFieldCellRenderer() }
            .createLineMarkerInfo(anchor)
    }

    private fun tooltip(fields: List<DCFieldDecl>): String {
        val names = fields.map { "${it.parentOfType<DCClassDecl>()?.name}.${it.name}" }
        return if (names.size <= 5) {
            "Implements " + names.joinToString(", ")
        } else {
            "Implements ${names.take(5).joinToString(", ")} and ${names.size - 5} more"
        }
    }
}

class DCFieldCellRenderer : com.intellij.ide.util.DefaultPsiElementCellRenderer() {
    override fun getElementText(element: PsiElement): String {
        val field = element as? DCFieldDecl ?: return super.getElementText(element)
        return "${field.parentOfType<DCClassDecl>()?.name}.${field.name}"
    }

    override fun getContainerText(element: PsiElement, name: String): String? =
        element.containingFile?.name
}
