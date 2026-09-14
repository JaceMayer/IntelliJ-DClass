package com.jacemayer.dclass.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.jacemayer.dclass.DCLanguage
import com.jacemayer.dclass.lexer.DCLexer
import com.jacemayer.dclass.lexer.DCTokenTypes
import com.jacemayer.dclass.psi.*
import com.jacemayer.dclass.parser.DCElementTypes as E

class DCParserDefinition : ParserDefinition {

    override fun createLexer(project: Project?): Lexer = DCLexer()
    override fun createParser(project: Project?): PsiParser = DCParser()
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getWhitespaceTokens(): TokenSet = WHITESPACE
    override fun getCommentTokens(): TokenSet = DCTokenTypes.COMMENTS
    override fun getStringLiteralElements(): TokenSet = DCTokenTypes.STRING_LITERALS

    override fun createFile(viewProvider: FileViewProvider): PsiFile = DCFile(viewProvider)

    override fun createElement(node: ASTNode): PsiElement = when (node.elementType) {
        E.CLASS_DECL, E.STRUCT_DECL -> DCClassDecl(node)
        E.PARAMETER_FIELD, E.ATOMIC_FIELD, E.MOLECULAR_FIELD -> DCFieldDecl(node)
        E.TYPEDEF_DECL -> DCTypedefDecl(node)
        E.KEYWORD_DECL -> DCKeywordDecl(node)
        E.IMPORT_DECL -> DCImportDecl(node)
        E.IMPORT_SYMBOL -> DCImportSymbol(node)
        E.SWITCH_DECL -> DCSwitchDecl(node)
        E.PARAMETER -> DCParameter(node)
        E.RANGE -> DCRangeSpec(node)
        E.RANGE_BOUND -> DCRangeBound(node)
        E.SWITCH_CASE -> DCSwitchCase(node)
        E.PARENT_REF -> DCParentRef(node)
        E.TYPE_REF -> DCTypeRef(node)
        E.MOLECULAR_REF -> DCMolecularRef(node)
        E.KEYWORD_REF -> DCKeywordRef(node)
        else -> com.intellij.extapi.psi.ASTWrapperPsiElement(node)
    }

    companion object {
        val FILE = IFileElementType(DCLanguage)
        private val WHITESPACE = TokenSet.create(TokenType.WHITE_SPACE)
    }
}
