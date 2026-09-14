package com.jacemayer.dclass.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.jacemayer.dclass.highlight.DCColors
import com.jacemayer.dclass.lexer.DCTokenTypes
import com.jacemayer.dclass.parser.DCElementTypes as E
import com.jacemayer.dclass.psi.*

class DCAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is DCParentRef -> checkParentRef(element, holder)
            is DCTypeRef -> checkTypeRef(element, holder)
            is DCClassDecl -> checkClass(element, holder)
            is DCTypedefDecl -> checkTypedef(element, holder)
            is DCFieldDecl -> checkField(element, holder)
            is DCParameter -> checkParameter(element, holder)
            is DCSwitchDecl -> checkSwitch(element, holder)
            else -> checkUnterminated(element, holder)
        }
    }


    private fun checkUnterminated(element: PsiElement, holder: AnnotationHolder) {
        val type = element.node?.elementType ?: return
        val text = element.text
        when {
            type == DCTokenTypes.BLOCK_COMMENT && !isClosedComment(text) ->
                error(element, "Unclosed block comment: missing '*/'", holder)

            type == DCTokenTypes.STRING && !isClosedQuote(text) ->
                error(element, "Unterminated string: missing a closing ${text.first()}", holder)

            type == DCTokenTypes.HEX_STRING && !text.endsWith(">") ->
                error(element, "Unterminated hex string: missing '>'", holder)
        }
    }


    private fun isClosedComment(text: String) = text.length >= 4 && text.endsWith("*/")

    private fun isClosedQuote(text: String) = text.length >= 2 && text.last() == text.first()


    private fun checkParameter(parameter: DCParameter, holder: AnnotationHolder) {
        checkScalings(parameter, holder)
        checkRange(parameter, holder)
    }

    private fun checkScalings(parameter: DCParameter, holder: AnnotationHolder) {
        val scalings = parameter.scalings
        if (scalings.isEmpty()) return

        val ops = scalings.map { use ->
            DCScaling.Op(
                if (use.kind == DCScalingKind.DIVISOR) DCScaling.Kind.DIVISOR else DCScaling.Kind.MODULUS,
                use.literal,
            )
        }

        for (finding in DCScaling.check(parameter.builtinTypeName, ops)) {
            val element = scalings[finding.index].element
            when (finding.severity) {
                DCScaling.Severity.ERROR -> error(element, finding.message, holder)
                DCScaling.Severity.WARNING -> warn(element, finding.message, holder)
            }
        }
    }


    private fun checkRange(parameter: DCParameter, holder: AnnotationHolder) {
        val range = parameter.range ?: return
        val bounds = range.bounds
        if (bounds.isEmpty()) return

        val finalDivisor = parameter.divisor?.let { DCTypeDomain.numberOf(it.literal) } ?: 1.0
        val divisorAtRange = parameter.scalings
            .takeWhile { it.element.textRange.startOffset < range.textRange.startOffset }
            .lastOrNull { it.kind == DCScalingKind.DIVISOR }
            ?.let { DCTypeDomain.numberOf(it.literal) } ?: 1.0

        val model = bounds.map {
            DCTypeDomain.Bound(it.min, it.max, it.text, it.isTextual)
        }

        for (finding in DCTypeDomain.checkRange(
            parameter.builtinTypeName, model, divisorAtRange, finalDivisor,
        )) {
            val element = bounds[finding.index]
            when (finding.severity) {
                DCTypeDomain.Severity.ERROR -> error(element, finding.message, holder)
                DCTypeDomain.Severity.WARNING -> warn(element, finding.message, holder)
            }
        }
    }


    private fun checkSwitch(switch: DCSwitchDecl, holder: AnnotationHolder) {
        checkDuplicateSwitchName(switch, holder)

        val key = switch.keyType
        val keyTypeName = key?.let { resolvedBuiltinName(it) }
        val keyRange = key?.range?.bounds.orEmpty().map {
            DCTypeDomain.Bound(it.min, it.max, it.text, it.isTextual)
        }

        val seen = HashMap<String, DCSwitchCase>()
        var sawDefault = false

        for (case in switch.cases) {
            if (case.isDefault) {
                if (sawDefault) error(case, "Duplicate default case", holder)
                sawDefault = true
                continue
            }

            val literal = case.valueText ?: continue
            DCTypeDomain.checkCaseValue(keyTypeName, keyRange, literal)?.let {
                error(case.valueNode?.psi ?: case, it, holder)
            }

            val key2 = DCTypeDomain.caseKey(keyTypeName, literal)
            val previous = seen.put(key2, case)
            if (previous != null) {
                val where = if (previous.valueText == literal) "" else " (same value as ${previous.valueText})"
                error(case.valueNode?.psi ?: case, "Duplicate case value$where", holder)
            }
        }
    }

    private fun checkDuplicateSwitchName(switch: DCSwitchDecl, holder: AnnotationHolder) {
        val file = switch.containingFile as? DCFile ?: return
        val name = switch.name ?: return
        val id = switch.nameIdentifier ?: return
        if (file.switches.any { it !== switch && it.name == name && it.textOffset < switch.textOffset }) {
            error(id, "Duplicate switch name: $name", holder)
        }
    }


    private fun resolvedBuiltinName(parameter: DCParameter): String? {
        parameter.builtinTypeName?.let { return it }
        val file = parameter.containingFile as? DCFile ?: return null
        val named = parameter.node.findChildByType(E.TYPE_REF)?.text ?: return null
        val typedef = DCIndex.visibleDeclarations(file).typedefs[named] ?: return null
        val inner = typedef.node.findChildByType(E.PARAMETER)?.psi as? DCParameter ?: return null
        return inner.builtinTypeName
    }


    private fun checkParentRef(ref: DCParentRef, holder: AnnotationHolder) {
        val file = ref.containingFile as? DCFile ?: return
        val cls = ref.parentOfType<DCClassDecl>() ?: return
        val decls = DCIndex.visibleDeclarations(file)

        val name = ref.referencedName
        val target = decls.classes[name]
        when {
            target == null -> {
                val sameKind = decls.classes.filterValues { it.isStruct == cls.isStruct }.keys
                val create = DCCreateDeclarationFix(
                    ref, name,
                    if (cls.isStruct) DCCreateDeclarationFix.Kind.STRUCT
                    else DCCreateDeclarationFix.Kind.DCLASS,
                )
                warn(
                    ref, "Undefined base class: $name", holder,
                    *renameFixes(ref, name, sameKind), create,
                )
            }

            target === cls ->
                error(ref, "'$name' cannot inherit from itself", holder)

            target.isStruct && !cls.isStruct ->
                error(ref, "'$name' is a struct and cannot be a dclass base", holder)

            !target.isStruct && cls.isStruct ->
                error(ref, "'$name' is a dclass; a struct can only derive from a struct", holder)
        }
    }

    private fun checkTypeRef(ref: DCTypeRef, holder: AnnotationHolder) {
        val file = ref.containingFile as? DCFile ?: return
        val decls = DCIndex.visibleDeclarations(file)
        val name = ref.referencedName
        if (!decls.isKnownType(name)) {
            val candidates = DCTokenTypes.BUILTIN_TYPES + decls.typedefs.keys + decls.classes.keys
            error(ref, "Unknown type: $name", holder, *renameFixes(ref, name, candidates))
        }
    }

    private fun checkClass(cls: DCClassDecl, holder: AnnotationHolder) {
        val file = cls.containingFile as? DCFile ?: return
        val id = cls.nameIdentifier ?: return
        val name = cls.name ?: return

        if (file.classes.any { it !== cls && it.name == name && it.textOffset < cls.textOffset }) {
            error(id, "Duplicate class name: $name", holder)
        }

        tint(id, DCColors.CLASS_NAME, holder)
        checkDuplicateFields(cls, holder)
    }

    private fun checkTypedef(decl: DCTypedefDecl, holder: AnnotationHolder) {
        val file = decl.containingFile as? DCFile ?: return
        val id = decl.nameIdentifier ?: return
        val name = decl.name ?: return

        if (file.typedefs.any { it !== decl && it.name == name && it.textOffset < decl.textOffset }) {
            error(id, "Duplicate typedef name: $name", holder)
        } else if (file.classes.any { it.name == name }) {
            error(id, "Typedef name collides with a class or struct: $name", holder)
        }
        tint(id, DCColors.CLASS_NAME, holder)
    }

    private fun checkDuplicateFields(cls: DCClassDecl, holder: AnnotationHolder) {
        val seen = HashSet<String>()
        for (field in cls.fields) {
            val name = field.name ?: continue
            val id = field.nameIdentifier ?: continue
            if (!seen.add(name)) {
                error(id, "Duplicate field name: $name", holder)
            }
        }
    }


    private fun checkField(field: DCFieldDecl, holder: AnnotationHolder) {
        val cls = field.parentOfType<DCClassDecl>() ?: return

        field.nameIdentifier?.let { tint(it, DCColors.FIELD_NAME, holder) }

        checkKeywords(cls, field, holder)

        if (field.kind == DCFieldDecl.FieldKind.MOLECULAR) {
            checkMolecular(cls, field, holder)
        }

        if (!cls.isStruct && field.name == null && field.kind == DCFieldDecl.FieldKind.PARAMETER &&
            PsiTreeUtil.findChildOfType(field, PsiErrorElement::class.java) == null
        ) {
            error(field, "Unnamed parameters are not allowed on a dclass", holder)
        }
    }

    private fun checkKeywords(cls: DCClassDecl, field: DCFieldDecl, holder: AnnotationHolder) {
        val file = field.containingFile as? DCFile ?: return
        val list = field.node.findChildByType(E.KEYWORD_LIST) ?: return
        val known = DCTokenTypes.BUILTIN_FIELD_KEYWORDS + file.keywordDecls.flatMap { it.names }

        for (child in list.psi.children) {
            val name = child.text
            when {
                name !in known ->
                    error(
                        child, "Unknown keyword: $name. Declare it with `keyword $name;` first", holder,
                        *renameFixes(child, name, known), DCDeclareKeywordFix(child, name),
                    )

                cls.isStruct ->
                    error(child, "Communication keywords are not allowed on a struct field", holder)
            }
        }
    }

    private fun checkMolecular(cls: DCClassDecl, field: DCFieldDecl, holder: AnnotationHolder) {
        val file = cls.containingFile as? DCFile ?: return
        val decls = DCIndex.visibleDeclarations(file)

        if (hasUnresolvedAncestor(cls, decls)) return
        val visible = visibleFieldNames(cls, decls)

        for (ref in field.node.getChildren(null)) {
            if (ref.elementType != E.MOLECULAR_REF) continue
            if (ref.text !in visible) {
                error(
                    ref.psi, "Unknown field: ${ref.text}", holder,
                    *renameFixes(ref.psi, ref.text, visible),
                )
            }
        }
    }


    private fun visibleFieldNames(cls: DCClassDecl, decls: DCIndex.Declarations): Set<String> {
        val names = LinkedHashSet<String>()
        walkHierarchy(cls, decls) { c -> c.fields.forEach { f -> f.name?.let(names::add) } }
        return names
    }

    private fun hasUnresolvedAncestor(cls: DCClassDecl, decls: DCIndex.Declarations): Boolean {
        var unresolved = false
        walkHierarchy(cls, decls) { c ->
            if (c.parentRefs.any { decls.classes[it.referencedName] == null }) unresolved = true
        }
        return unresolved
    }

    private fun walkHierarchy(
        start: DCClassDecl,
        decls: DCIndex.Declarations,
        action: (DCClassDecl) -> Unit,
    ) {
        val seen = HashSet<DCClassDecl>()
        val queue = ArrayDeque<DCClassDecl>()
        queue += start
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (!seen.add(c)) continue
            action(c)
            for (p in c.parentRefs) decls.classes[p.referencedName]?.let { queue += it }
        }
    }

    private fun error(
        element: PsiElement,
        message: String,
        holder: AnnotationHolder,
        vararg fixes: com.intellij.codeInsight.intention.IntentionAction,
    ) {
        val builder = holder.newAnnotation(HighlightSeverity.ERROR, message).range(element)
        for (fix in fixes) builder.withFix(fix)
        builder.create()
    }

    private fun warn(
        element: PsiElement,
        message: String,
        holder: AnnotationHolder,
        vararg fixes: com.intellij.codeInsight.intention.IntentionAction,
    ) {
        val builder = holder.newAnnotation(HighlightSeverity.WARNING, message).range(element)
        for (fix in fixes) builder.withFix(fix)
        builder.create()
    }

    private fun renameFixes(element: PsiElement, typed: String, candidates: Collection<String>) =
        DCSuggest.closeTo(typed, candidates)
            .mapIndexed { index, name ->
                DCReplaceWithFix(
                    element, name,
                    if (index == 0) com.intellij.codeInsight.intention.PriorityAction.Priority.HIGH
                    else com.intellij.codeInsight.intention.PriorityAction.Priority.NORMAL,
                )
            }
            .toTypedArray<com.intellij.codeInsight.intention.IntentionAction>()

    private fun tint(element: PsiElement, key: TextAttributesKey, holder: AnnotationHolder) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(key)
            .create()
    }
}
