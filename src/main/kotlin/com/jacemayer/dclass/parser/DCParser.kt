package com.jacemayer.dclass.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.jacemayer.dclass.lexer.DCTokenTypes as T
import com.jacemayer.dclass.parser.DCElementTypes as E

class DCParser : PsiParser {

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val fileMarker = builder.mark()
        while (!builder.eof()) {
            val before = builder.currentOffset
            parseDeclaration(builder)
            if (builder.currentOffset == before && !builder.eof()) {
                builder.advanceLexer()
            }
        }
        fileMarker.done(root)
        return builder.treeBuilt
    }


    private fun parseDeclaration(b: PsiBuilder) {
        when (b.tokenType) {
            T.KW_FROM -> parseFromImport(b)
            T.KW_IMPORT -> parseImport(b)
            T.KW_KEYWORD -> parseKeywordDecl(b)
            T.KW_TYPEDEF -> parseTypedefDecl(b)
            T.KW_DCLASS, T.KW_STRUCT -> parseClassOrStruct(b)
            T.KW_SWITCH -> {
                val m = b.mark()
                parseSwitchBody(b)
                consumeIf(b, T.SEMICOLON)
                m.done(E.SWITCH_DECL)
            }
            T.SEMICOLON -> b.advanceLexer()
            else -> {
                val m = b.mark()
                b.advanceLexer()
                m.error("Unexpected token at top level")
                recoverToDeclaration(b)
            }
        }
    }

    private fun recoverToDeclaration(b: PsiBuilder) {
        while (!b.eof() && !isDeclStarter(b.tokenType)) {
            if (b.tokenType == T.SEMICOLON) {
                b.advanceLexer()
                return
            }
            b.advanceLexer()
        }
    }


    private fun parseImport(b: PsiBuilder) {
        val m = b.mark()
        b.advanceLexer()
        parseDottedSlashPath(b)
        consumeIf(b, T.SEMICOLON)
        m.done(E.IMPORT_DECL)
    }

    private fun parseFromImport(b: PsiBuilder) {
        val m = b.mark()
        b.advanceLexer()
        parseDottedSlashPath(b)
        if (b.tokenType == T.KW_IMPORT) {
            b.advanceLexer()
            if (b.tokenType == T.STAR) {
                b.advanceLexer()
            } else {
                do {
                    val sym = b.mark()
                    if (!parseSlashIdent(b)) {
                        sym.drop()
                        break
                    }
                    sym.done(E.IMPORT_SYMBOL)
                } while (consumeIf(b, T.COMMA))
            }
        } else {
            b.error("Expected 'import'")
        }
        consumeIf(b, T.SEMICOLON)
        m.done(E.IMPORT_DECL)
    }

    private fun parseDottedSlashPath(b: PsiBuilder) {
        val m = b.mark()
        if (!parseSlashIdent(b)) {
            m.done(E.IMPORT_MODULE)
            return
        }
        while (b.tokenType == T.DOT) {
            b.advanceLexer()
            if (!parseSlashIdent(b)) break
        }
        m.done(E.IMPORT_MODULE)
    }

    private fun parseSlashIdent(b: PsiBuilder): Boolean {
        if (b.tokenType != T.IDENTIFIER) {
            b.error("Expected identifier")
            return false
        }
        b.advanceLexer()
        while (b.tokenType == T.SLASH) {
            b.advanceLexer()
            if (b.tokenType != T.IDENTIFIER) {
                b.error("Expected identifier")
                break
            }
            b.advanceLexer()
        }
        return true
    }


    private fun parseKeywordDecl(b: PsiBuilder) {
        val m = b.mark()
        b.advanceLexer()
        while (b.tokenType == T.IDENTIFIER || b.tokenType == T.FIELD_KEYWORD) {
            b.advanceLexer()
        }
        expect(b, T.SEMICOLON, "Expected ';'")
        m.done(E.KEYWORD_DECL)
    }

    private fun parseTypedefDecl(b: PsiBuilder) {
        val m = b.mark()
        b.advanceLexer()
        parseParameter(b)
        if (!consumeIf(b, T.SEMICOLON)) {
            b.error("Expected ';'")
            skipToSemicolon(b)
        }
        m.done(E.TYPEDEF_DECL)
    }


    private fun parseClassOrStruct(b: PsiBuilder) {
        val isStruct = b.tokenType == T.KW_STRUCT
        val m = b.mark()
        b.advanceLexer()

        if (b.tokenType == T.IDENTIFIER) b.advanceLexer()

        if (b.tokenType == T.COLON) {
            val parents = b.mark()
            b.advanceLexer()
            do {
                if (b.tokenType != T.IDENTIFIER) {
                    b.error("Expected base class name")
                    break
                }
                val p = b.mark()
                b.advanceLexer()
                p.done(E.PARENT_REF)
            } while (consumeIf(b, T.COMMA))
            parents.done(E.PARENT_LIST)
        }

        if (b.tokenType == T.LBRACE) {
            parseClassBody(b)
        } else {
            b.error("Expected '{'")
        }

        consumeIf(b, T.SEMICOLON)
        m.done(if (isStruct) E.STRUCT_DECL else E.CLASS_DECL)
    }

    private fun parseClassBody(b: PsiBuilder) {
        val body = b.mark()
        b.advanceLexer()
        while (!b.eof() && b.tokenType != T.RBRACE) {
            if (b.tokenType == T.SEMICOLON) {
                b.advanceLexer()
                continue
            }
            val before = b.currentOffset
            parseClassField(b)

            if (b.tokenType == T.SEMICOLON) {
                b.advanceLexer()
            } else if (b.tokenType != T.RBRACE) {
                b.error("Expected ';'")
                skipToFieldBoundary(b)
            }
            if (b.currentOffset == before && !b.eof() && b.tokenType != T.RBRACE) {
                b.advanceLexer()
            }
        }
        expect(b, T.RBRACE, "Expected '}'")
        body.done(E.CLASS_BODY)
    }

    private fun skipToFieldBoundary(b: PsiBuilder) {
        var depth = 0
        while (!b.eof()) {
            when (b.tokenType) {
                T.LBRACE, T.LPAREN, T.LBRACKET -> depth++
                T.RBRACE -> {
                    if (depth == 0) return
                    depth--
                }
                T.RPAREN, T.RBRACKET -> if (depth > 0) depth--
                T.SEMICOLON -> if (depth == 0) { b.advanceLexer(); return }
                else -> {
                    if (depth == 0 && isDeclStarter(b.tokenType)) return
                }
            }
            b.advanceLexer()
        }
    }


    private fun parseClassField(b: PsiBuilder) {
        val t = b.tokenType
        val next = b.lookAhead(1)

        if (t == T.LPAREN || (t == T.IDENTIFIER && next == T.LPAREN)) {
            parseAtomicField(b)
            return
        }
        if (t == T.IDENTIFIER && next == T.COLON) {
            parseMolecularField(b)
            return
        }
        parseParameterField(b)
    }

    private fun parseAtomicField(b: PsiBuilder) {
        val m = b.mark()
        if (b.tokenType == T.IDENTIFIER) b.advanceLexer()
        expect(b, T.LPAREN, "Expected '('")
        if (b.tokenType != T.RPAREN) {
            do {
                val before = b.currentOffset
                parseParameterWithDefault(b)
                if (b.currentOffset == before) break
            } while (consumeIf(b, T.COMMA))
        }
        expect(b, T.RPAREN, "Expected ')'")
        parseKeywordList(b)
        m.done(E.ATOMIC_FIELD)
    }

    private fun parseMolecularField(b: PsiBuilder) {
        val m = b.mark()
        b.advanceLexer()
        b.advanceLexer()
        do {
            if (b.tokenType != T.IDENTIFIER) {
                b.error("Expected atomic field name")
                break
            }
            val ref = b.mark()
            b.advanceLexer()
            ref.done(E.MOLECULAR_REF)
        } while (consumeIf(b, T.COMMA))
        parseKeywordList(b)
        m.done(E.MOLECULAR_FIELD)
    }

    private fun parseParameterField(b: PsiBuilder) {
        val m = b.mark()
        val before = b.currentOffset
        parseParameterWithDefault(b)
        if (b.currentOffset == before) {
            m.drop()
            return
        }
        parseKeywordList(b)
        m.done(E.PARAMETER_FIELD)
    }

    private fun parseKeywordList(b: PsiBuilder) {
        if (b.tokenType != T.FIELD_KEYWORD && b.tokenType != T.IDENTIFIER) return
        val m = b.mark()
        var consumed = false
        while (b.tokenType == T.FIELD_KEYWORD || b.tokenType == T.IDENTIFIER) {
            val kw = b.mark()
            b.advanceLexer()
            kw.done(E.KEYWORD_REF)
            consumed = true
        }
        if (consumed) m.done(E.KEYWORD_LIST) else m.drop()
    }


    private fun parseParameterWithDefault(b: PsiBuilder) {
        parseParameter(b)
        if (b.tokenType == T.EQUALS) {
            val m = b.mark()
            b.advanceLexer()
            parseValue(b)
            m.done(E.DEFAULT_VALUE)
        }
    }

    private fun parseParameter(b: PsiBuilder) {
        val m = b.mark()
        if (!parseTypeDefinition(b)) {
            m.drop()
            return
        }
        if (b.tokenType == T.IDENTIFIER) {
            b.advanceLexer()
            parseScalingSuffix(b)
        } else if (isReservedWord(b.tokenType)) {
            val bad = b.mark()
            val text = b.tokenText
            b.advanceLexer()
            bad.error("'$text' is a reserved word and cannot be used as a name")
            parseScalingSuffix(b)
        }
        m.done(E.PARAMETER)
    }

    private fun parseScalingSuffix(b: PsiBuilder) {
        while (true) {
            when (b.tokenType) {
                T.SLASH -> parseScaling(b, E.DIVISOR)
                T.PERCENT -> parseScaling(b, E.MODULUS)
                T.LBRACKET -> parseArraySpec(b)
                else -> return
            }
        }
    }

    private fun parseScaling(b: PsiBuilder, type: IElementType) {
        val m = b.mark()
        b.advanceLexer()
        expectNumber(b)
        m.done(type)
    }

    private fun isReservedWord(type: IElementType?): Boolean =
        type == T.TYPE_NAME || (type != null && T.KEYWORDS.contains(type))

    private fun parseTypeDefinition(b: PsiBuilder): Boolean {
        if (!parseTypeName(b)) return false
        while (b.tokenType == T.LBRACKET) parseArraySpec(b)
        return true
    }

    private fun parseTypeName(b: PsiBuilder): Boolean {
        when (b.tokenType) {
            T.TYPE_NAME -> {
                val m = b.mark()
                b.advanceLexer()
                while (true) {
                    when (b.tokenType) {
                        T.LPAREN -> parseRange(b, T.LPAREN, T.RPAREN)
                        T.SLASH -> parseScaling(b, E.DIVISOR)
                        T.PERCENT -> parseScaling(b, E.MODULUS)
                        else -> {
                            m.done(E.BUILTIN_TYPE)
                            return true
                        }
                    }
                }
            }
            T.KW_STRUCT -> {
                val m = b.mark()
                b.advanceLexer()
                if (b.tokenType == T.IDENTIFIER) b.advanceLexer()
                if (b.tokenType == T.LBRACE) parseClassBody(b) else b.error("Expected '{'")
                m.done(E.INLINE_STRUCT_TYPE)
                return true
            }
            T.KW_SWITCH -> {
                val m = b.mark()
                parseSwitchBody(b)
                m.done(E.SWITCH_DECL)
                return true
            }
            T.IDENTIFIER -> {
                val m = b.mark()
                b.advanceLexer()
                m.done(E.TYPE_REF)
                return true
            }
            else -> {
                b.error("Expected a type")
                return false
            }
        }
    }

    private fun parseArraySpec(b: PsiBuilder) {
        parseRange(b, T.LBRACKET, T.RBRACKET)
    }

    private fun parseRange(b: PsiBuilder, open: IElementType, close: IElementType) {
        val m = b.mark()
        b.advanceLexer()
        if (b.tokenType != close) {
            do {
                val before = b.currentOffset
                parseRangeBound(b)
                if (b.currentOffset == before) break
            } while (consumeIf(b, T.COMMA))
        }
        expect(b, close, "Expected '${if (close == T.RPAREN) ")" else "]"}'")
        m.done(if (open == T.LBRACKET) E.ARRAY_SPEC else E.RANGE)
    }

    private fun parseRangeBound(b: PsiBuilder) {
        val m = b.mark()
        if (!consumeNumberOrChar(b)) {
            m.drop()
            b.error("Expected a numeric bound")
            return
        }
        if (b.tokenType == T.MINUS) {
            b.advanceLexer()
            if (!consumeNumberOrChar(b)) b.error("Expected upper bound")
        } else if (b.tokenType == T.INTEGER && b.tokenText?.startsWith("-") == true) {
            b.advanceLexer()
        }
        m.done(E.RANGE_BOUND)
    }

    private fun consumeNumberOrChar(b: PsiBuilder): Boolean =
        when (b.tokenType) {
            T.INTEGER, T.REAL, T.STRING -> { b.advanceLexer(); true }
            else -> false
        }

    private fun expectNumber(b: PsiBuilder) {
        if (b.tokenType == T.INTEGER || b.tokenType == T.REAL) b.advanceLexer()
        else b.error("Expected a number")
    }


    private fun parseValue(b: PsiBuilder) {
        when (b.tokenType) {
            T.INTEGER, T.REAL, T.HEX_STRING -> {
                b.advanceLexer()
                if (b.tokenType == T.STAR) {
                    b.advanceLexer()
                    expectNumber(b)
                }
            }
            T.STRING -> b.advanceLexer()
            T.LBRACKET -> parseValueList(b, T.LBRACKET, T.RBRACKET)
            T.LBRACE -> parseValueList(b, T.LBRACE, T.RBRACE)
            T.LPAREN -> parseValueList(b, T.LPAREN, T.RPAREN)
            else -> b.error("Expected a default value")
        }
    }

    private fun parseValueList(b: PsiBuilder, open: IElementType, close: IElementType) {
        val m = b.mark()
        b.advanceLexer()
        while (!b.eof() && b.tokenType != close) {
            val before = b.currentOffset
            parseValue(b)
            if (b.currentOffset == before) break
            if (!consumeIf(b, T.COMMA)) break
        }
        expect(b, close, "Expected closing bracket")
        m.done(E.VALUE_LIST)
    }


    private fun parseSwitchBody(b: PsiBuilder) {
        b.advanceLexer()
        if (b.tokenType == T.IDENTIFIER) b.advanceLexer()
        expect(b, T.LPAREN, "Expected '('")
        parseParameter(b)
        expect(b, T.RPAREN, "Expected ')'")
        expect(b, T.LBRACE, "Expected '{'")
        while (!b.eof() && b.tokenType != T.RBRACE) {
            val before = b.currentOffset
            when (b.tokenType) {
                T.KW_CASE -> {
                    val c = b.mark()
                    b.advanceLexer()
                    parseValue(b)
                    expect(b, T.COLON, "Expected ':'")
                    c.done(E.SWITCH_CASE)
                }
                T.KW_DEFAULT -> {
                    val c = b.mark()
                    b.advanceLexer()
                    expect(b, T.COLON, "Expected ':'")
                    c.done(E.SWITCH_CASE)
                }
                T.KW_BREAK -> {
                    b.advanceLexer()
                    consumeIf(b, T.SEMICOLON)
                }
                T.SEMICOLON -> b.advanceLexer()
                else -> {
                    parseClassField(b)
                    if (b.tokenType == T.SEMICOLON) b.advanceLexer()
                }
            }
            if (b.currentOffset == before && !b.eof() && b.tokenType != T.RBRACE) {
                b.advanceLexer()
            }
        }
        expect(b, T.RBRACE, "Expected '}'")
    }


    private fun consumeIf(b: PsiBuilder, type: IElementType): Boolean {
        if (b.tokenType == type) {
            b.advanceLexer()
            return true
        }
        return false
    }

    private fun expect(b: PsiBuilder, type: IElementType, message: String) {
        if (!consumeIf(b, type)) b.error(message)
    }

    private fun skipToSemicolon(b: PsiBuilder) {
        while (!b.eof() && b.tokenType != T.SEMICOLON && !isDeclStarter(b.tokenType)) {
            b.advanceLexer()
        }
        consumeIf(b, T.SEMICOLON)
    }

    private fun isDeclStarter(t: IElementType?): Boolean = t != null && DECL_STARTERS.contains(t)

    private companion object {
        val DECL_STARTERS: TokenSet = TokenSet.create(
            T.KW_DCLASS, T.KW_STRUCT, T.KW_TYPEDEF,
            T.KW_KEYWORD, T.KW_IMPORT, T.KW_FROM,
        )
    }
}
