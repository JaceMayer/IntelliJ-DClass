package com.jacemayer.dclass.annotator

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.jacemayer.dclass.DCFileType
import com.jacemayer.dclass.psi.DCClassDecl
import com.jacemayer.dclass.psi.DCFieldDecl
import com.jacemayer.dclass.psi.DCFile
import com.jacemayer.dclass.psi.DCTypedefDecl


object DCIndex {

    fun declarationsIn(file: DCFile): Declarations =
        CachedValuesManager.getCachedValue(file) {
            val classes = LinkedHashMap<String, DCClassDecl>()
            for (c in file.classes) c.name?.let { classes.putIfAbsent(it, c) }

            val typedefs = LinkedHashMap<String, DCTypedefDecl>()
            for (t in file.typedefs) t.name?.let { typedefs.putIfAbsent(it, t) }

            CachedValueProvider.Result.create(Declarations(classes, typedefs), file)
        }

    fun projectDeclarations(project: Project): Declarations =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            val classes = LinkedHashMap<String, DCClassDecl>()
            val typedefs = LinkedHashMap<String, DCTypedefDecl>()
            val manager = PsiManager.getInstance(project)
            try {
                for (vf in FileTypeIndex.getFiles(DCFileType, GlobalSearchScope.allScope(project))) {
                    val file = manager.findFile(vf) as? DCFile ?: continue
                    val decls = declarationsIn(file)
                    for ((name, decl) in decls.classes) classes.putIfAbsent(name, decl)
                    for ((name, decl) in decls.typedefs) typedefs.putIfAbsent(name, decl)
                }
            } catch (_: IndexNotReadyException) {
            }
            CachedValueProvider.Result.create(
                Declarations(classes, typedefs),
                PsiModificationTracker.MODIFICATION_COUNT,
                DumbService.getInstance(project).modificationTracker,
            )
        }

    fun visibleDeclarations(file: DCFile): Declarations {
        val own = declarationsIn(file)
        val classes = LinkedHashMap(own.classes)
        val typedefs = LinkedHashMap(own.typedefs)

        val project = file.project
        val self = file.virtualFile
        val manager = PsiManager.getInstance(project)
        try {
            for (vf in FileTypeIndex.getFiles(DCFileType, GlobalSearchScope.allScope(project))) {
                if (vf == self) continue
                val other = manager.findFile(vf) as? DCFile ?: continue
                val decls = declarationsIn(other)
                for ((name, decl) in decls.classes) classes.putIfAbsent(name, decl)
                for ((name, decl) in decls.typedefs) typedefs.putIfAbsent(name, decl)
            }
        } catch (_: IndexNotReadyException) {
        }
        return Declarations(classes, typedefs)
    }

    data class Declarations(
        val classes: Map<String, DCClassDecl>,
        val typedefs: Map<String, DCTypedefDecl>,
    ) {
        fun isKnownType(name: String): Boolean = name in classes || name in typedefs

        fun hierarchyOf(start: DCClassDecl): List<DCClassDecl> {
            val seen = LinkedHashSet<DCClassDecl>()
            val queue = ArrayDeque<DCClassDecl>()
            queue += start
            while (queue.isNotEmpty()) {
                val c = queue.removeFirst()
                if (!seen.add(c)) continue
                for (p in c.parentRefs) classes[p.referencedName]?.let { queue += it }
            }
            return seen.toList()
        }

        fun findFieldInHierarchy(start: DCClassDecl, fieldName: String): DCFieldDecl? =
            hierarchyOf(start).firstNotNullOfOrNull { c ->
                c.fields.firstOrNull { it.name == fieldName }
            }
    }
}
