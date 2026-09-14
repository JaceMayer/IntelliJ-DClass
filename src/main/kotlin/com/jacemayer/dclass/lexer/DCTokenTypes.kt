package com.jacemayer.dclass.lexer

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.jacemayer.dclass.DCLanguage

class DCTokenType(debugName: String) : IElementType(debugName, DCLanguage) {
    override fun toString(): String = "DC:" + super.toString()
}

object DCTokenTypes {
    @JvmField val LINE_COMMENT = DCTokenType("LINE_COMMENT")
    @JvmField val BLOCK_COMMENT = DCTokenType("BLOCK_COMMENT")

    @JvmField val KW_DCLASS = DCTokenType("dclass")
    @JvmField val KW_STRUCT = DCTokenType("struct")
    @JvmField val KW_FROM = DCTokenType("from")
    @JvmField val KW_IMPORT = DCTokenType("import")
    @JvmField val KW_KEYWORD = DCTokenType("keyword")
    @JvmField val KW_TYPEDEF = DCTokenType("typedef")
    @JvmField val KW_SWITCH = DCTokenType("switch")
    @JvmField val KW_CASE = DCTokenType("case")
    @JvmField val KW_DEFAULT = DCTokenType("default")
    @JvmField val KW_BREAK = DCTokenType("break")

    @JvmField val TYPE_NAME = DCTokenType("TYPE_NAME")

    @JvmField val FIELD_KEYWORD = DCTokenType("FIELD_KEYWORD")

    @JvmField val IDENTIFIER = DCTokenType("IDENTIFIER")
    @JvmField val INTEGER = DCTokenType("INTEGER")
    @JvmField val REAL = DCTokenType("REAL")
    @JvmField val STRING = DCTokenType("STRING")
    @JvmField val HEX_STRING = DCTokenType("HEX_STRING")

    @JvmField val LBRACE = DCTokenType("{")
    @JvmField val RBRACE = DCTokenType("}")
    @JvmField val LPAREN = DCTokenType("(")
    @JvmField val RPAREN = DCTokenType(")")
    @JvmField val LBRACKET = DCTokenType("[")
    @JvmField val RBRACKET = DCTokenType("]")
    @JvmField val SEMICOLON = DCTokenType(";")
    @JvmField val COMMA = DCTokenType(",")
    @JvmField val COLON = DCTokenType(":")
    @JvmField val DOT = DCTokenType(".")
    @JvmField val SLASH = DCTokenType("/")
    @JvmField val PERCENT = DCTokenType("%")
    @JvmField val EQUALS = DCTokenType("=")
    @JvmField val STAR = DCTokenType("*")
    @JvmField val MINUS = DCTokenType("-")
    @JvmField val PLUS = DCTokenType("+")

    @JvmField val COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT)
    @JvmField val STRING_LITERALS = TokenSet.create(STRING, HEX_STRING)

    @JvmField val KEYWORDS = TokenSet.create(
        KW_DCLASS, KW_STRUCT, KW_FROM, KW_IMPORT, KW_KEYWORD,
        KW_TYPEDEF, KW_SWITCH, KW_CASE, KW_DEFAULT, KW_BREAK,
    )

    @JvmField val STRUCTURAL_KEYWORDS: Map<String, DCTokenType> = mapOf(
        "dclass" to KW_DCLASS,
        "struct" to KW_STRUCT,
        "from" to KW_FROM,
        "import" to KW_IMPORT,
        "keyword" to KW_KEYWORD,
        "typedef" to KW_TYPEDEF,
        "switch" to KW_SWITCH,
        "case" to KW_CASE,
        "default" to KW_DEFAULT,
        "break" to KW_BREAK,
    )

    @JvmField val BUILTIN_TYPES: Set<String> = setOf(
        "int8", "int16", "int32", "int64",
        "uint8", "uint16", "uint32", "uint64",
        "float64", "string", "blob", "blob32",
        "int8array", "int16array", "int32array",
        "uint8array", "uint16array", "uint32array",
        "uint32uint8array", "char",
    )

    @JvmField val BUILTIN_FIELD_KEYWORDS: Set<String> = setOf(
        "required", "broadcast", "ownrecv", "ram",
        "db", "clsend", "clrecv", "ownsend", "airecv",
    )
}
