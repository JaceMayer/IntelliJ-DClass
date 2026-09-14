package com.jacemayer.dclass.python

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.jacemayer.dclass.DCIcons
import com.jacemayer.dclass.psi.DCClassDecl
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStarArgument
import com.jetbrains.python.psi.PyStarExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyTupleExpression

object DCSendUpdateCalls {

    private val FIELD_POSITION = mapOf(
        "sendUpdate" to 0,
        "sendUpdateToAvatarId" to 1,
        "sendUpdateToAccountId" to 1,
        "sendUpdateToChannel" to 1,
    )

    class Match(
        val owner: PyClass,
        val fieldLiteral: PyStringLiteralExpression,
        val fieldName: String,
        val argumentCount: Int?,
    )

    fun match(call: PyCallExpression): Match? {
        val callee = call.callee as? PyReferenceExpression ?: return null
        val position = FIELD_POSITION[callee.referencedName ?: return null] ?: return null
        val receiver = callee.qualifier as? PyReferenceExpression ?: return null
        if (receiver.qualifier != null || receiver.referencedName != "self") return null
        val owner = PsiTreeUtil.getParentOfType(call, PyClass::class.java) ?: return null

        val arguments = call.argumentList?.arguments ?: return null
        if (arguments.any { it is PyStarArgument }) return null
        val positional = arguments.filter { it !is PyKeywordArgument }
        val keywords = arguments.filterIsInstance<PyKeywordArgument>()

        val fieldExpression = keywords.firstOrNull { it.keyword == "fieldName" }?.valueExpression
            ?: positional.getOrNull(position)
        val literal = fieldExpression as? PyStringLiteralExpression ?: return null
        val name = literalValue(literal) ?: return null

        val argsKeyword = keywords.firstOrNull { it.keyword == "args" }
        val argsExpression = argsKeyword?.valueExpression ?: positional.getOrNull(position + 1)
        val count = when {
            argsKeyword == null && positional.size <= position + 1 && callee.referencedName == "sendUpdate" -> 0
            else -> literalLength(argsExpression)
        }
        return Match(owner, literal, name, count)
    }

    fun literalValue(literal: PyStringLiteralExpression): String? {
        if (literal.stringElements.size != 1) return null
        if (literal.stringElements.single().isFormatted) return null
        return literal.stringValue
    }

    private fun literalLength(expression: PyExpression?): Int? {
        var current = expression
        while (current is PyParenthesizedExpression) current = current.containedExpression
        val elements = when (current) {
            is PyListLiteralExpression -> current.elements
            is PyTupleExpression -> current.elements
            else -> return null
        }
        if (elements.any { it is PyStarExpression }) return null
        return elements.size
    }
}

class DCSendUpdateReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PyStringLiteralExpression::class.java),
            DCSendUpdateReferenceProvider(),
        )
    }
}

class DCSendUpdateReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val literal = element as? PyStringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
        val call = PsiTreeUtil.getParentOfType(literal, PyCallExpression::class.java, true)
            ?: return PsiReference.EMPTY_ARRAY
        val match = DCSendUpdateCalls.match(call) ?: return PsiReference.EMPTY_ARRAY
        if (match.fieldLiteral != literal) return PsiReference.EMPTY_ARRAY
        return arrayOf(DCSendUpdateReference(literal, match.owner))
    }
}

class DCSendUpdateReference(
    literal: PyStringLiteralExpression,
    private val owner: PyClass,
) : PsiPolyVariantReferenceBase<PyStringLiteralExpression>(
    literal,
    ElementManipulators.getValueTextRange(literal),
    true,
) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val name = DCSendUpdateCalls.literalValue(element) ?: return ResolveResult.EMPTY_ARRAY
        return DCDistributedModel.fieldsNamed(owner, name)
            .map { PsiElementResolveResult(it) }
            .toTypedArray()
    }

    override fun getVariants(): Array<Any> =
        DCDistributedModel.sendableFields(CompletionUtil.getOriginalOrSelf(owner)).mapNotNull { field ->
            val name = field.name ?: return@mapNotNull null
            LookupElementBuilder.create(field, name)
                .withIcon(DCIcons.FILE)
                .withTypeText(field.parentOfType<DCClassDecl>()?.name)
        }.toTypedArray()
}

class DCSendUpdateAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val call = element as? PyCallExpression ?: return
        val match = DCSendUpdateCalls.match(call) ?: return
        val possible = DCDistributedModel.possibleDClasses(match.owner)
        if (possible.isEmpty()) return

        val literal = match.fieldLiteral
        val range: TextRange = ElementManipulators.getValueTextRange(literal).shiftRight(literal.textRange.startOffset)

        val fields = DCDistributedModel.fieldsNamed(match.owner, match.fieldName)
        if (fields.isEmpty()) {
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "Unknown field '${match.fieldName}': not declared on ${describe(possible.mapNotNull { it.name })} or their base classes",
            ).range(range).create()
            return
        }

        val given = match.argumentCount ?: return
        val expected = fields.map { DCDistributedModel.argumentCount(it) }
        if (expected.any { it == null }) return
        val counts = expected.filterNotNull().toSortedSet()
        if (given in counts) return

        holder.newAnnotation(
            HighlightSeverity.ERROR,
            "'${match.fieldName}' takes ${counts.joinToString(" or ")} argument${if (counts.singleOrNull() == 1) "" else "s"}, but $given ${if (given == 1) "is" else "are"} given",
        ).range(range).create()
    }

    private fun describe(names: List<String>): String = when {
        names.size <= 3 -> names.joinToString(", ")
        else -> "${names.take(3).joinToString(", ")} and ${names.size - 3} more"
    }
}
