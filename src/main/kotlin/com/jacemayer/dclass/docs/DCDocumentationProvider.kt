package com.jacemayer.dclass.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.jacemayer.dclass.annotator.DCIndex
import com.jacemayer.dclass.lexer.DCTokenTypes as T
import com.jacemayer.dclass.parser.DCElementTypes as E
import com.jacemayer.dclass.psi.*

class DCDocumentationProvider : AbstractDocumentationProvider() {

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? {
        val element = contextElement ?: return null
        if (element.containingFile !is DCFile) return null

        element.parentOfType<DCKeywordRef>(withSelf = true)?.let { return it }
        element.parentOfType<DCParentRef>(withSelf = true)?.let { return it }
        element.parentOfType<DCTypeRef>(withSelf = true)?.let { return it }
        element.parentOfType<DCMolecularRef>(withSelf = true)?.let { return it }

        if (element.node?.elementType == T.TYPE_NAME) return element
        if (element.node?.elementType == T.FIELD_KEYWORD) return element

        element.parentOfType<DCFieldDecl>(withSelf = true)?.let { return it }
        element.parentOfType<DCClassDecl>(withSelf = true)?.let { return it }
        element.parentOfType<DCTypedefDecl>(withSelf = true)?.let { return it }
        return null
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        element ?: return null
        return when {
            element is DCKeywordRef -> keywordDoc(element.text, element)
            element.node?.elementType == T.FIELD_KEYWORD -> keywordDoc(element.text, element)
            element.node?.elementType == T.TYPE_NAME -> builtinTypeDoc(element)
            element is DCParentRef -> namedTargetDoc(element, element.referencedName)
            element is DCTypeRef -> namedTargetDoc(element, element.referencedName)
            element is DCMolecularRef -> molecularRefDoc(element)
            element is DCFieldDecl -> fieldDoc(element)
            element is DCClassDecl -> classDoc(element)
            element is DCTypedefDecl -> typedefDoc(element)
            else -> null
        }
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? =
        when (element) {
            is DCClassDecl -> classSignature(element)
            is DCFieldDecl -> fieldSignature(element)
            is DCTypedefDecl -> "typedef ${element.name}"
            else -> null
        }



    private fun keywordDoc(name: String, element: PsiElement): String {
        val file = element.containingFile as? DCFile
        val declared = file?.keywordDecls?.any { name in it.names } == true
        val builtin = DCBuiltinDocs.KEYWORDS[name]
        val doc = builtin ?: DCBuiltinDocs.CONVENTIONAL_KEYWORDS[name]?.takeIf { declared }

        val sb = StringBuilder()
        sb.append(DocumentationMarkup.DEFINITION_START)
        sb.append("keyword <b>").append(escape(name)).append("</b>")
        sb.append(DocumentationMarkup.DEFINITION_END)

        sb.append(DocumentationMarkup.CONTENT_START)
        sb.append(
            when {
                doc != null -> escape(doc.summary)
                declared -> "Declared in this file with <code>keyword ${escape(name)};</code>. " +
                    "A custom keyword carries no server-side meaning: it marks fields for your own code to act on."
                else -> "Not one of the nine built-in keywords, and not declared in this file. " +
                    "Add <code>keyword ${escape(name)};</code> before using it."
            }
        )
        sb.append(DocumentationMarkup.CONTENT_END)

        sb.append(DocumentationMarkup.SECTIONS_START)
        if (doc != null && doc.implies.isNotEmpty()) {
            section(
                sb, "Implies",
                doc.implies.joinToString(", ") { imp ->
                    "<code>$imp</code>" +
                        (DCBuiltinDocs.KEYWORDS[imp]?.let { " &mdash; " + escape(it.summary.substringBefore('.')) + "." } ?: "")
                },
            )
        }
        doc?.note?.let { section(sb, "Note", escape(it)) }
        sb.append(DocumentationMarkup.SECTIONS_END)
        return sb.toString()
    }


    private fun builtinTypeDoc(element: PsiElement): String? {
        val name = element.text
        val doc = DCBuiltinDocs.TYPES[name] ?: return null
        val typeNode = element.parent?.node?.takeIf { it.elementType == E.BUILTIN_TYPE }

        val sb = StringBuilder()
        sb.append(DocumentationMarkup.DEFINITION_START)
        sb.append("<b>").append(name).append("</b>")
        sb.append(DocumentationMarkup.DEFINITION_END)

        sb.append(DocumentationMarkup.CONTENT_START).append(escape(doc.summary))
        sb.append(DocumentationMarkup.CONTENT_END)

        sb.append(DocumentationMarkup.SECTIONS_START)
        doc.fixedBytes?.let {
            section(sb, "Wire size", "$it byte${if (it == 1) "" else "s"}, fixed")
        }
        if (doc.min != null && doc.max != null) {
            section(sb, "Values", "%,d to %,d".format(doc.min, doc.max))
        }
        doc.encoding?.let { section(sb, "Encoding", escape(it)) }

        if (typeNode != null) {
            val parameter = element.parentOfType<DCParameter>()
            parameter?.divisor?.let {
                section(sb, "Divisor ${it.literal}", escape(DCBuiltinDocs.divisorNote(doc, it.literal)))
            }
            parameter?.modulus?.let {
                section(sb, "Modulus ${it.literal}", escape(DCBuiltinDocs.modulusNote(it.literal)))
            }
            typeNode.findChildByType(E.RANGE)?.let {
                section(sb, "Range ${escape(it.text)}", escape(DCBuiltinDocs.rangeNote(it.text)))
            }
            typeNode.treeParent?.findChildByType(E.ARRAY_SPEC)?.let {
                section(sb, "Array ${escape(it.text)}", escape(DCBuiltinDocs.arraySpecNote(it.text)))
            }
        }
        sb.append(DocumentationMarkup.SECTIONS_END)
        return sb.toString()
    }

    private fun namedTargetDoc(element: PsiElement, name: String): String {
        val file = element.containingFile as? DCFile ?: return plain(name)
        val decls = DCIndex.visibleDeclarations(file)

        decls.classes[name]?.let { return classDoc(it) }
        decls.typedefs[name]?.let { return typedefDoc(it) }

        return definition(name) + content(
            "No <code>dclass</code>, <code>struct</code> or <code>typedef</code> named <b>$name</b> " +
                "is visible from this file."
        )
    }

    private fun molecularRefDoc(element: DCMolecularRef): String {
        val cls = element.parentOfType<DCClassDecl>() ?: return plain(element.referencedName)
        val file = element.containingFile as? DCFile ?: return plain(element.referencedName)
        val decls = DCIndex.visibleDeclarations(file)

        var found: DCFieldDecl? = null
        val seen = HashSet<DCClassDecl>()
        val queue = ArrayDeque<DCClassDecl>().apply { add(cls) }
        while (queue.isNotEmpty() && found == null) {
            val c = queue.removeFirst()
            if (!seen.add(c)) continue
            found = c.fields.firstOrNull { it.name == element.referencedName }
            for (p in c.parentRefs) decls.classes[p.referencedName]?.let { queue += it }
        }
        return found?.let { fieldDoc(it) } ?: plain(element.referencedName)
    }

    private fun classDoc(cls: DCClassDecl): String {
        val sb = StringBuilder()
        sb.append(definition(classSignature(cls)))
        sb.append(DocumentationMarkup.SECTIONS_START)

        section(
            sb, "Kind",
            if (cls.isStruct)
                "A struct: the same shape as a dclass, but embedded inside a message rather than " +
                    "existing as an object in its own right. If a Python class of this name exists, " +
                    "an instance of it is passed in; otherwise the fields arrive as a tuple."
            else
                "A dclass: an object type clients may create and hold. Its field order fixes the " +
                    "16-bit field IDs sent on the wire, so inserting a field renumbers everything after it.",
        )

        val fields = cls.fields
        section(sb, "Fields", fields.size.toString())
        if (cls.parentRefs.isNotEmpty()) {
            section(sb, "Inherits", escape(cls.parentRefs.joinToString(", ") { it.referencedName }))
        }
        if (fields.isNotEmpty()) {
            section(
                sb, "Members",
                fields.take(12).joinToString("<br>") { f ->
                    "<code>" + escape(fieldSignature(f)) + "</code>"
                } + if (fields.size > 12) "<br>and ${fields.size - 12} more" else ""
            )
        }
        sb.append(DocumentationMarkup.SECTIONS_END)
        return sb.toString()
    }

    private fun typedefDoc(decl: DCTypedefDecl): String =
        definition("typedef <b>${escape(decl.name ?: "?")}</b>") +
            content("Alias declared as <code>" + escape(decl.text.trimEnd(';', ' ', '\n')) + "</code>")

    private fun fieldDoc(field: DCFieldDecl): String {
        val sb = StringBuilder()
        sb.append(definition(escape(fieldSignature(field))))
        sb.append(DocumentationMarkup.SECTIONS_START)

        section(
            sb, "Kind",
            when (field.kind) {
                DCFieldDecl.FieldKind.ATOMIC -> "Atomic field (a method call with arguments)"
                DCFieldDecl.FieldKind.MOLECULAR -> "Molecular field (sends several atomic fields as one update)"
                DCFieldDecl.FieldKind.PARAMETER -> "Parameter field (a single stored value)"
            },
        )

        val keywords = field.keywords
        if (keywords.isNotEmpty()) {
            section(
                sb, "Keywords",
                keywords.joinToString("<br>") { kw ->
                    val doc = DCBuiltinDocs.KEYWORDS[kw]
                    "<code>$kw</code>" + (doc?.let { " &mdash; " + escape(it.summary) } ?: "")
                },
            )
        }
        field.parentOfType<DCClassDecl>()?.name?.let { section(sb, "Declared in", escape(it)) }
        sb.append(DocumentationMarkup.SECTIONS_END)
        return sb.toString()
    }

    private fun classSignature(cls: DCClassDecl): String {
        val kind = if (cls.isStruct) "struct" else "dclass"
        val parents = cls.parentRefs.joinToString(", ") { it.referencedName }
        return "$kind <b>${escape(cls.name ?: "<anonymous>")}</b>" +
            if (parents.isNotEmpty()) " : ${escape(parents)}" else ""
    }

    private fun fieldSignature(field: DCFieldDecl): String =
        field.text.lineSequence().joinToString(" ") { it.trim() }.trimEnd(';').trim()

    private fun definition(html: String) =
        DocumentationMarkup.DEFINITION_START + html + DocumentationMarkup.DEFINITION_END

    private fun content(html: String) =
        DocumentationMarkup.CONTENT_START + html + DocumentationMarkup.CONTENT_END

    private fun plain(name: String) = definition(escape(name))

    private fun section(sb: StringBuilder, label: String, value: String) {
        sb.append(DocumentationMarkup.SECTION_HEADER_START).append(label)
        sb.append(DocumentationMarkup.SECTION_SEPARATOR).append("<p>").append(value)
        sb.append(DocumentationMarkup.SECTION_END)
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
