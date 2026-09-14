package com.jacemayer.dclass.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.jacemayer.dclass.annotator.DCIndex
import com.jacemayer.dclass.docs.DCBuiltinDocs
import com.jacemayer.dclass.lexer.DCTokenTypes
import com.jacemayer.dclass.psi.*
import javax.swing.Icon

class DCCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), Provider())
    }

    private class Provider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val position = parameters.position
            val file = position.containingFile as? DCFile ?: return

            when (val parent = position.parent) {
                is DCKeywordRef -> addFieldKeywords(file, parent, result)
                is DCMolecularRef -> addMolecularMembers(file, parent, result)
                is DCParentRef -> addBaseClasses(file, parent, result)
                is DCTypeRef -> addTypes(file, result)
                is PsiErrorElement -> if (parent.parent is DCFile) addDeclarationKeywords(result)
                is DCFile -> addDeclarationKeywords(result)
                else -> Unit
            }
        }

        private fun addDeclarationKeywords(result: CompletionResultSet) {
            for ((keyword, hint) in DECLARATION_KEYWORDS) {
                result.addElement(
                    LookupElementBuilder.create(keyword)
                        .withIcon(AllIcons.Nodes.Static)
                        .withTypeText(hint, true)
                        .withBoldness(true),
                )
            }
        }

        private fun addTypes(file: DCFile, result: CompletionResultSet) {
            for (name in DCBuiltinDocs.TYPES.keys) {
                val doc = DCBuiltinDocs.TYPES[name]
                val tail = doc?.fixedBytes?.let { "$it byte${if (it == 1) "" else "s"}" } ?: "variable length"
                result.addElement(
                    prioritised(
                        LookupElementBuilder.create(name)
                            .withIcon(AllIcons.Nodes.Type)
                            .withTypeText(tail, true)
                            .withBoldness(true),
                        PRIORITY_BUILTIN,
                    )
                )
            }

            val decls = DCIndex.visibleDeclarations(file)
            for ((name, decl) in decls.typedefs) {
                result.addElement(
                    prioritised(
                        LookupElementBuilder.create(name)
                            .withIcon(AllIcons.Nodes.Type)
                            .withTypeText("typedef", true)
                            .withTailText(locationOf(decl, file), true),
                        PRIORITY_TYPEDEF,
                    )
                )
            }
            for ((name, decl) in decls.classes) {
                result.addElement(
                    prioritised(
                        LookupElementBuilder.create(name)
                            .withIcon(iconFor(decl))
                            .withTypeText(if (decl.isStruct) "struct" else "dclass", true)
                            .withTailText(locationOf(decl, file), true),
                        if (decl.isStruct) PRIORITY_STRUCT else PRIORITY_DCLASS,
                    )
                )
            }
        }

        private fun addBaseClasses(file: DCFile, ref: DCParentRef, result: CompletionResultSet) {
            val owner = ref.parentOfType<DCClassDecl>()
            val wantStruct = owner?.isStruct == true
            val alreadyListed = owner?.parentRefs?.map { it.referencedName }?.toSet().orEmpty()

            for ((name, decl) in DCIndex.visibleDeclarations(file).classes) {
                if (decl.isStruct != wantStruct) continue
                if (decl === owner || name in alreadyListed) continue
                result.addElement(
                    LookupElementBuilder.create(name)
                        .withIcon(iconFor(decl))
                        .withTypeText("${decl.fields.size} fields", true)
                        .withTailText(locationOf(decl, file), true),
                )
            }
        }

        private fun addFieldKeywords(file: DCFile, ref: DCKeywordRef, result: CompletionResultSet) {
            val field = ref.parentOfType<DCFieldDecl>()
            val owner = field?.parentOfType<DCClassDecl>()
            if (owner?.isStruct == true) return

            val dummy = ref.text
            val used = field?.keywords?.filterNot { it == dummy }?.toSet().orEmpty()

            for (name in DCTokenTypes.BUILTIN_FIELD_KEYWORDS) {
                if (name in used) continue
                val doc = DCBuiltinDocs.KEYWORDS[name]
                result.addElement(
                    LookupElementBuilder.create(name)
                        .withIcon(AllIcons.Nodes.Parameter)
                        .withTypeText(doc?.implies?.takeIf { it.isNotEmpty() }?.let { "implies ${it.joinToString()}" }, true)
                        .withBoldness(true),
                )
            }
            for (name in file.keywordDecls.flatMap { it.names }) {
                if (name in used || name in DCTokenTypes.BUILTIN_FIELD_KEYWORDS) continue
                result.addElement(
                    LookupElementBuilder.create(name)
                        .withIcon(AllIcons.Nodes.Parameter)
                        .withTypeText("declared in this file", true),
                )
            }
        }

        private fun addMolecularMembers(file: DCFile, ref: DCMolecularRef, result: CompletionResultSet) {
            val molecular = ref.parentOfType<DCFieldDecl>() ?: return
            val owner = molecular.parentOfType<DCClassDecl>() ?: return
            val decls = DCIndex.visibleDeclarations(file)

            val dummy = ref.text
            val alreadyListed = molecular.node.getChildren(null)
                .filter { it.elementType == com.jacemayer.dclass.parser.DCElementTypes.MOLECULAR_REF }
                .map { it.text }
                .filterNot { it == dummy }
                .toSet()

            for (cls in decls.hierarchyOf(owner)) {
                for (f in cls.fields) {
                    val name = f.name ?: continue
                    if (f.kind != DCFieldDecl.FieldKind.ATOMIC) continue
                    if (name in alreadyListed) continue
                    result.addElement(
                        LookupElementBuilder.create(name)
                            .withIcon(AllIcons.Nodes.Method)
                            .withTypeText(f.keywords.joinToString(" ").ifEmpty { null }, true)
                            .withTailText(if (cls === owner) null else "  from ${cls.name}", true),
                    )
                }
            }
        }

        private fun locationOf(decl: PsiElement, from: DCFile): String? {
            val name = decl.containingFile?.name ?: return null
            return if (name == from.name) null else "  $name"
        }

        private fun iconFor(decl: DCClassDecl): Icon =
            if (decl.isStruct) AllIcons.Nodes.Record else AllIcons.Nodes.Class

        private fun prioritised(element: LookupElementBuilder, priority: Double): LookupElement =
            com.intellij.codeInsight.completion.PrioritizedLookupElement.withPriority(element, priority)

        private companion object {
            const val PRIORITY_BUILTIN = 40.0
            const val PRIORITY_TYPEDEF = 30.0
            const val PRIORITY_STRUCT = 20.0
            const val PRIORITY_DCLASS = 10.0

            val DECLARATION_KEYWORDS = listOf(
                "dclass" to "distributed object",
                "struct" to "message shape",
                "typedef" to "type alias",
                "keyword" to "declare a keyword",
                "switch" to "discriminated union",
                "from" to "python import",
                "import" to "python import",
            )
        }
    }
}
