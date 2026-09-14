package com.jacemayer.dclass.editor

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.icons.AllIcons
import com.jacemayer.dclass.psi.*
import javax.swing.Icon

class DCStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (psiFile !is DCFile) return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                DCStructureViewModel(psiFile)
        }
    }
}

class DCStructureViewModel(psiFile: DCFile) :
    StructureViewModelBase(psiFile, DCStructureViewElement(psiFile)),
    StructureViewModel.ElementInfoProvider {

    init {
        withSorters(Sorter.ALPHA_SORTER)
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean {
        val value = element.value
        return value is DCFieldDecl && value.embeddedSwitch == null
    }

    override fun getSuitableClasses(): Array<Class<*>> =
        arrayOf(
            DCClassDecl::class.java,
            DCFieldDecl::class.java,
            DCTypedefDecl::class.java,
            DCSwitchDecl::class.java,
        )
}

class DCStructureViewElement(private val element: PsiElement) :
    StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = element

    override fun navigate(requestFocus: Boolean) {
        (element as? NavigatablePsiElement)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (element as? NavigatablePsiElement)?.canNavigate() ?: false
    override fun canNavigateToSource(): Boolean =
        (element as? NavigatablePsiElement)?.canNavigateToSource() ?: false

    override fun getAlphaSortKey(): String = presentableName() ?: ""

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String? = presentableName()
        override fun getLocationString(): String? = locationText()
        override fun getIcon(unused: Boolean): Icon? = iconFor()
    }

    override fun getChildren(): Array<TreeElement> = when (element) {
        is DCFile -> (element.classes + element.typedefs + element.switches)
            .sortedBy { it.textOffset }
            .map { DCStructureViewElement(it) }
            .toTypedArray()

        is DCClassDecl -> element.fields
            .map { DCStructureViewElement(it) }
            .toTypedArray()

        is DCFieldDecl -> element.embeddedSwitch
            ?.caseGroups
            ?.map { DCCaseGroupElement(it) }
            ?.toTypedArray()
            ?: emptyArray()

        is DCSwitchDecl -> element.caseGroups
            .map { DCCaseGroupElement(it) }
            .toTypedArray()

        else -> emptyArray()
    }

    private fun presentableName(): String? = when (element) {
        is DCFile -> element.name
        is DCClassDecl -> element.name ?: "<anonymous>"
        is DCSwitchDecl -> element.name ?: "switch"
        is DCFieldDecl ->
            if (element.embeddedSwitch != null) element.name ?: "switch"
            else element.name ?: "<unnamed>"
        is DCTypedefDecl -> element.name
        else -> element.text
    }

    private fun locationText(): String? = when (element) {
        is DCClassDecl -> element.parentRefs
            .joinToString(", ") { it.referencedName }
            .takeIf { it.isNotEmpty() }
            ?.let { ": $it" }

        is DCSwitchDecl -> element.keyParameter?.let { "on $it" }

        is DCFieldDecl -> element.embeddedSwitch?.keyParameter?.let { "on $it" }
            ?: element.keywords.joinToString(" ").takeIf { it.isNotEmpty() }

        else -> null
    }

    private fun iconFor(): Icon? = when (element) {
        is DCClassDecl -> if (element.isStruct) AllIcons.Nodes.Record else AllIcons.Nodes.Class
        is DCTypedefDecl -> AllIcons.Nodes.Type
        is DCSwitchDecl -> AllIcons.Nodes.Enum
        is DCFieldDecl -> if (element.embeddedSwitch != null) AllIcons.Nodes.Enum else when (element.kind) {
            DCFieldDecl.FieldKind.ATOMIC -> AllIcons.Nodes.Method
            DCFieldDecl.FieldKind.MOLECULAR -> AllIcons.Nodes.MethodReference
            DCFieldDecl.FieldKind.PARAMETER -> AllIcons.Nodes.Field
        }
        else -> null
    }
}

class DCCaseGroupElement(private val group: DCSwitchDecl.CaseGroup) :
    StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = group.label

    override fun navigate(requestFocus: Boolean) {
        (group.label as? NavigatablePsiElement)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (group.label as? NavigatablePsiElement)?.canNavigate() ?: false
    override fun canNavigateToSource(): Boolean =
        (group.label as? NavigatablePsiElement)?.canNavigateToSource() ?: false

    override fun getAlphaSortKey(): String = group.text

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = group.text
        override fun getLocationString(): String? =
            group.fields.size.takeIf { it > 0 }?.let { "$it field${if (it == 1) "" else "s"}" }
        override fun getIcon(unused: Boolean): Icon = AllIcons.Nodes.Variable
    }

    override fun getChildren(): Array<TreeElement> =
        group.fields.map { DCStructureViewElement(it) }.toTypedArray()
}
