package com.jacemayer.dclass.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.jacemayer.dclass.DCFileType
import com.jacemayer.dclass.DCLanguage
import com.jacemayer.dclass.lexer.DCTokenTypes as T
import com.jacemayer.dclass.parser.DCElementTypes as E

class DCFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, DCLanguage) {
    override fun getFileType() = DCFileType
    override fun toString() = "DClass file"

    val declarations: List<DCDeclaration>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, DCDeclaration::class.java)

    val classes: List<DCClassDecl>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, DCClassDecl::class.java)

    val typedefs: List<DCTypedefDecl>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, DCTypedefDecl::class.java)

    val keywordDecls: List<DCKeywordDecl>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, DCKeywordDecl::class.java)

    val switches: List<DCSwitchDecl>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, DCSwitchDecl::class.java)
}

abstract class DCDeclaration(node: ASTNode) : ASTWrapperPsiElement(node)

abstract class DCNamedElement(node: ASTNode) : DCDeclaration(node), PsiNameIdentifierOwner {

    override fun getNameIdentifier(): PsiElement? =
        node.findChildByType(T.IDENTIFIER)?.psi

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement {
        throw UnsupportedOperationException("Renaming DClass elements is not supported yet")
    }

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()
}

class DCClassDecl(node: ASTNode) : DCNamedElement(node) {
    val isStruct: Boolean get() = node.elementType == E.STRUCT_DECL

    val parentRefs: List<DCParentRef>
        get() {
            val list = node.findChildByType(E.PARENT_LIST) ?: return emptyList()
            return PsiTreeUtil.getChildrenOfTypeAsList(list.psi, DCParentRef::class.java)
        }

    val body: PsiElement? get() = node.findChildByType(E.CLASS_BODY)?.psi

    val fields: List<DCFieldDecl>
        get() = body?.let { PsiTreeUtil.getChildrenOfTypeAsList(it, DCFieldDecl::class.java) } ?: emptyList()

    override fun getReferences() = DCExternalReferenceProvider.collect(this)
}

open class DCFieldDecl(node: ASTNode) : DCNamedElement(node) {

    val kind: FieldKind
        get() = when (node.elementType) {
            E.ATOMIC_FIELD -> FieldKind.ATOMIC
            E.MOLECULAR_FIELD -> FieldKind.MOLECULAR
            else -> FieldKind.PARAMETER
        }

    override fun getNameIdentifier(): PsiElement? {
        node.findChildByType(T.IDENTIFIER)?.let { return it.psi }
        val param = node.findChildByType(E.PARAMETER) ?: return null
        return param.findChildByType(T.IDENTIFIER)?.psi
    }

    val keywords: List<String>
        get() {
            val list = node.findChildByType(E.KEYWORD_LIST) ?: return emptyList()
            return list.psi.children.map { it.text }
        }

    override fun getReferences() = DCExternalReferenceProvider.collect(this)

    val embeddedSwitch: DCSwitchDecl?
        get() = PsiTreeUtil.findChildOfType(this, DCSwitchDecl::class.java)

    enum class FieldKind { PARAMETER, ATOMIC, MOLECULAR }
}

class DCTypedefDecl(node: ASTNode) : DCNamedElement(node) {
    override fun getNameIdentifier(): PsiElement? {
        val param = node.findChildByType(E.PARAMETER) ?: return null
        return param.findChildByType(T.IDENTIFIER)?.psi
    }
}

class DCKeywordDecl(node: ASTNode) : DCDeclaration(node) {
    val names: List<String>
        get() = node.getChildren(null)
            .filter { it.elementType == T.IDENTIFIER || it.elementType == T.FIELD_KEYWORD }
            .map { it.text }
}

class DCImportDecl(node: ASTNode) : DCDeclaration(node) {
    val moduleText: String?
        get() = node.findChildByType(E.IMPORT_MODULE)?.text

    val symbols: List<String>
        get() = node.getChildren(null)
            .filter { it.elementType == E.IMPORT_SYMBOL }
            .map { it.text }
}

class DCSwitchDecl(node: ASTNode) : DCNamedElement(node) {
    val keyParameter: String?
        get() = node.findChildByType(E.PARAMETER)?.text?.trim()

    val keyType: DCParameter?
        get() = node.findChildByType(E.PARAMETER)?.psi as? DCParameter

    val cases: List<DCSwitchCase>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, DCSwitchCase::class.java)

    val caseGroups: List<CaseGroup>
        get() {
            val groups = mutableListOf<CaseGroup>()
            var current: CaseGroup? = null
            for (child in children) {
                when {
                    child.node.elementType == E.SWITCH_CASE -> {
                        current = CaseGroup(child, mutableListOf())
                        groups += current
                    }
                    child is DCFieldDecl -> current?.fields?.add(child)
                }
            }
            return groups
        }

    class CaseGroup(val label: PsiElement, val fields: MutableList<DCFieldDecl>) {
        val text: String get() = label.text.trim().removeSuffix(":").trim()
    }
}

class DCParameter(node: ASTNode) : ASTWrapperPsiElement(node) {
    val builtinTypeName: String?
        get() = node.findChildByType(E.BUILTIN_TYPE)
            ?.findChildByType(T.TYPE_NAME)
            ?.text

    val scalings: List<DCScalingUse>
        get() {
            val out = ArrayList<DCScalingUse>()
            node.findChildByType(E.BUILTIN_TYPE)?.let { collectScalings(it, out) }
            collectScalings(node, out)
            return out
        }

    val range: DCRangeSpec?
        get() = node.findChildByType(E.BUILTIN_TYPE)
            ?.findChildByType(E.RANGE)
            ?.psi as? DCRangeSpec

    val divisor: DCScalingUse?
        get() = scalings.lastOrNull { it.kind == DCScalingKind.DIVISOR }

    val modulus: DCScalingUse?
        get() = scalings.lastOrNull { it.kind == DCScalingKind.MODULUS }

    private fun collectScalings(from: ASTNode, into: MutableList<DCScalingUse>) {
        for (child in from.getChildren(null)) {
            when (child.elementType) {
                E.DIVISOR -> into += DCScalingUse(DCScalingKind.DIVISOR, child.psi)
                E.MODULUS -> into += DCScalingUse(DCScalingKind.MODULUS, child.psi)
            }
        }
    }
}

enum class DCScalingKind { DIVISOR, MODULUS }

class DCRangeSpec(node: ASTNode) : ASTWrapperPsiElement(node) {
    val bounds: List<DCRangeBound>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, DCRangeBound::class.java)
}

class DCRangeBound(node: ASTNode) : ASTWrapperPsiElement(node) {

    private val literals: List<ASTNode>
        get() = node.getChildren(null).filter {
            it.elementType == T.INTEGER || it.elementType == T.REAL || it.elementType == T.STRING
        }

    private val hasExplicitDash: Boolean
        get() = node.findChildByType(T.MINUS) != null

    val minText: String? get() = literals.getOrNull(0)?.text

    val maxText: String?
        get() {
            val second = literals.getOrNull(1) ?: return minText
            val text = second.text
            return if (!hasExplicitDash && text.startsWith("-")) text.substring(1) else text
        }

    val min: Double? get() = numberOf(minText)
    val max: Double? get() = numberOf(maxText)

    val isTextual: Boolean get() = literals.any { it.elementType == T.STRING }

    private fun numberOf(text: String?): Double? {
        val t = text ?: return null
        if (t.startsWith("0x") || t.startsWith("0X")) return t.substring(2).toLongOrNull(16)?.toDouble()
        if (t.startsWith("-0x") || t.startsWith("-0X")) return t.substring(3).toLongOrNull(16)?.toDouble()?.unaryMinus()
        return t.toDoubleOrNull()
    }
}

class DCSwitchCase(node: ASTNode) : ASTWrapperPsiElement(node) {

    val isDefault: Boolean get() = node.findChildByType(T.KW_DEFAULT) != null
    val valueNode: ASTNode?
        get() = node.getChildren(null).firstOrNull {
            it.elementType == T.INTEGER || it.elementType == T.REAL ||
                it.elementType == T.STRING || it.elementType == T.HEX_STRING
        }

    val valueText: String? get() = valueNode?.text
}

data class DCScalingUse(val kind: DCScalingKind, val element: PsiElement) {
    val literal: String get() = element.text.drop(1).trim()
}

class DCParentRef(node: ASTNode) : ASTWrapperPsiElement(node) {
    val referencedName: String get() = text
    override fun getReference() = DCReference(this, ::resolveParentRef)
}

class DCTypeRef(node: ASTNode) : ASTWrapperPsiElement(node) {
    val referencedName: String get() = text
    override fun getReference() = DCReference(this, ::resolveTypeRef)
}

class DCMolecularRef(node: ASTNode) : ASTWrapperPsiElement(node) {
    val referencedName: String get() = text
    override fun getReference() = DCReference(this, ::resolveMolecularRef)
}

class DCImportSymbol(node: ASTNode) : ASTWrapperPsiElement(node) {
    val pythonClassNames: List<String>
        get() {
            val parts = text.split('/')
            if (parts.isEmpty()) return emptyList()
            val base = parts.first()
            return listOf(base) + parts.drop(1).map { base + it }
        }

    val partRanges: List<Pair<Int, Int>>
        get() {
            val result = mutableListOf<Pair<Int, Int>>()
            var offset = 0
            for (part in text.split('/')) {
                result += offset to offset + part.length
                offset += part.length + 1
            }
            return result
        }

    override fun getReferences() = DCExternalReferenceProvider.collect(this)
}

class DCKeywordRef(node: ASTNode) : ASTWrapperPsiElement(node) {
    val referencedName: String get() = text
}
