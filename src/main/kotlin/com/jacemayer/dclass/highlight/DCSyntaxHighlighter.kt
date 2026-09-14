package com.jacemayer.dclass.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as D
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.jacemayer.dclass.lexer.DCLexer
import com.jacemayer.dclass.lexer.DCTokenTypes as T

object DCColors {
    val KEYWORD = createTextAttributesKey("DC_KEYWORD", D.KEYWORD)
    val TYPE = createTextAttributesKey("DC_TYPE", D.KEYWORD)
    val FIELD_KEYWORD = createTextAttributesKey("DC_FIELD_KEYWORD", D.METADATA)
    val IDENTIFIER = createTextAttributesKey("DC_IDENTIFIER", D.IDENTIFIER)
    val CLASS_NAME = createTextAttributesKey("DC_CLASS_NAME", D.CLASS_NAME)
    val FIELD_NAME = createTextAttributesKey("DC_FIELD_NAME", D.INSTANCE_FIELD)
    val NUMBER = createTextAttributesKey("DC_NUMBER", D.NUMBER)
    val STRING = createTextAttributesKey("DC_STRING", D.STRING)
    val LINE_COMMENT = createTextAttributesKey("DC_LINE_COMMENT", D.LINE_COMMENT)
    val BLOCK_COMMENT = createTextAttributesKey("DC_BLOCK_COMMENT", D.BLOCK_COMMENT)
    val BRACES = createTextAttributesKey("DC_BRACES", D.BRACES)
    val PARENTHESES = createTextAttributesKey("DC_PARENTHESES", D.PARENTHESES)
    val BRACKETS = createTextAttributesKey("DC_BRACKETS", D.BRACKETS)
    val SEMICOLON = createTextAttributesKey("DC_SEMICOLON", D.SEMICOLON)
    val COMMA = createTextAttributesKey("DC_COMMA", D.COMMA)
    val OPERATOR = createTextAttributesKey("DC_OPERATOR", D.OPERATION_SIGN)
    val BAD_CHARACTER = createTextAttributesKey("DC_BAD_CHARACTER", com.intellij.openapi.editor.HighlighterColors.BAD_CHARACTER)
}

class DCSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = DCLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        when (tokenType) {
            in KEYWORD_TOKENS -> pack(DCColors.KEYWORD)
            T.TYPE_NAME -> pack(DCColors.TYPE)
            T.FIELD_KEYWORD -> pack(DCColors.FIELD_KEYWORD)
            T.IDENTIFIER -> pack(DCColors.IDENTIFIER)
            T.INTEGER, T.REAL -> pack(DCColors.NUMBER)
            T.STRING, T.HEX_STRING -> pack(DCColors.STRING)
            T.LINE_COMMENT -> pack(DCColors.LINE_COMMENT)
            T.BLOCK_COMMENT -> pack(DCColors.BLOCK_COMMENT)
            T.LBRACE, T.RBRACE -> pack(DCColors.BRACES)
            T.LPAREN, T.RPAREN -> pack(DCColors.PARENTHESES)
            T.LBRACKET, T.RBRACKET -> pack(DCColors.BRACKETS)
            T.SEMICOLON -> pack(DCColors.SEMICOLON)
            T.COMMA -> pack(DCColors.COMMA)
            T.SLASH, T.PERCENT, T.EQUALS, T.STAR, T.MINUS, T.PLUS, T.COLON -> pack(DCColors.OPERATOR)
            TokenType.BAD_CHARACTER -> pack(DCColors.BAD_CHARACTER)
            else -> EMPTY
        }

    private companion object {
        val EMPTY = emptyArray<TextAttributesKey>()
        val KEYWORD_TOKENS = setOf(
            T.KW_DCLASS, T.KW_STRUCT, T.KW_FROM, T.KW_IMPORT, T.KW_KEYWORD,
            T.KW_TYPEDEF, T.KW_SWITCH, T.KW_CASE, T.KW_DEFAULT, T.KW_BREAK,
        )
    }
}

class DCSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = DCSyntaxHighlighter()
}
