package com.jacemayer.dclass.python

import com.intellij.openapi.project.DumbService
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.parentOfType
import com.jacemayer.dclass.annotator.DCIndex
import com.jacemayer.dclass.parser.DCElementTypes
import com.jacemayer.dclass.psi.DCClassDecl
import com.jacemayer.dclass.psi.DCFieldDecl
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.search.PyClassInheritorsSearch
import com.jetbrains.python.psi.types.TypeEvalContext

object DCDistributedModel {

    private val SUFFIXES = listOf("AI", "UD", "OV")

    fun dclassFor(pyClass: PyClass): DCClassDecl? {
        val name = pyClass.name ?: return null
        val classes = DCIndex.projectDeclarations(pyClass.project).classes
        classes[name]?.takeIf { !it.isStruct }?.let { return it }
        for (suffix in SUFFIXES) {
            if (name.length > suffix.length && name.endsWith(suffix)) {
                classes[name.removeSuffix(suffix)]?.takeIf { !it.isStruct }?.let { return it }
            }
        }
        return null
    }

    fun inheritors(pyClass: PyClass): List<PyClass> {
        if (DumbService.isDumb(pyClass.project)) return emptyList()
        return CachedValuesManager.getCachedValue(pyClass) {
            CachedValueProvider.Result.create(
                PyClassInheritorsSearch.search(pyClass, true).findAll().toList(),
                PsiModificationTracker.MODIFICATION_COUNT,
            )
        }
    }

    fun possibleDClasses(pyClass: PyClass): List<DCClassDecl> {
        if (DumbService.isDumb(pyClass.project)) return emptyList()
        return CachedValuesManager.getCachedValue(pyClass) {
            val found = LinkedHashSet<DCClassDecl>()
            dclassFor(pyClass)?.let { found += it }
            for (inheritor in inheritors(pyClass)) dclassFor(inheritor)?.let { found += it }
            CachedValueProvider.Result.create(found.toList(), PsiModificationTracker.MODIFICATION_COUNT)
        }
    }

    fun fieldsNamed(pyClass: PyClass, name: String): List<DCFieldDecl> {
        val decls = DCIndex.projectDeclarations(pyClass.project)
        return possibleDClasses(pyClass)
            .mapNotNull { decls.findFieldInHierarchy(it, name) }
            .distinct()
    }

    fun sendableFields(pyClass: PyClass): List<DCFieldDecl> {
        val decls = DCIndex.projectDeclarations(pyClass.project)
        val byName = LinkedHashMap<String, DCFieldDecl>()
        for (dclass in possibleDClasses(pyClass)) {
            for (cls in decls.hierarchyOf(dclass)) {
                for (field in cls.fields) {
                    val name = field.name ?: continue
                    byName.putIfAbsent(name, field)
                }
            }
        }
        return byName.values.toList()
    }

    fun fieldNamesInHierarchy(dclass: DCClassDecl): Set<String> =
        CachedValuesManager.getCachedValue(dclass) {
            val decls = DCIndex.projectDeclarations(dclass.project)
            val names = HashSet<String>()
            for (cls in decls.hierarchyOf(dclass)) for (field in cls.fields) field.name?.let { names += it }
            CachedValueProvider.Result.create(names, PsiModificationTracker.MODIFICATION_COUNT)
        }

    fun argumentCount(field: DCFieldDecl): Int? {
        val decls = DCIndex.projectDeclarations(field.project)
        return argumentCount(field, decls, HashSet())
    }

    private fun argumentCount(
        field: DCFieldDecl,
        decls: DCIndex.Declarations,
        visiting: MutableSet<DCFieldDecl>,
    ): Int? {
        if (!visiting.add(field)) return null
        return when (field.kind) {
            DCFieldDecl.FieldKind.ATOMIC ->
                field.node.getChildren(null).count { it.elementType == DCElementTypes.PARAMETER }

            DCFieldDecl.FieldKind.MOLECULAR -> {
                val owner = field.parentOfType<DCClassDecl>() ?: return null
                var total = 0
                for (ref in field.node.getChildren(null)) {
                    if (ref.elementType != DCElementTypes.MOLECULAR_REF) continue
                    val member = decls.findFieldInHierarchy(owner, ref.text) ?: return null
                    if (member.kind != DCFieldDecl.FieldKind.ATOMIC) return null
                    total += argumentCount(member, decls, visiting) ?: return null
                }
                total
            }

            DCFieldDecl.FieldKind.PARAMETER -> null
        }
    }

    fun implementedFields(function: PyFunction): List<DCFieldDecl> {
        val name = function.name ?: return emptyList()
        val owner = function.containingClass ?: return emptyList()
        if (DumbService.isDumb(function.project)) return emptyList()
        val decls = DCIndex.projectDeclarations(function.project)
        val context = TypeEvalContext.codeInsightFallback(function.project)
        val found = LinkedHashSet<DCFieldDecl>()
        for (candidate in listOf(owner) + inheritors(owner)) {
            val dclass = dclassFor(candidate) ?: continue
            if (name !in fieldNamesInHierarchy(dclass)) continue
            val field = decls.findFieldInHierarchy(dclass, name) ?: continue
            if (field.kind == DCFieldDecl.FieldKind.PARAMETER) continue
            val resolved = if (candidate == owner) function else candidate.findMethodByName(name, true, context)
            if (resolved == function) found += field
        }
        return found.toList()
    }
}
