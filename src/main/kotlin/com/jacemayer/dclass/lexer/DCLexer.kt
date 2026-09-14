package com.jacemayer.dclass.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class DCLexer : LexerBase() {

    private var buffer: CharSequence = ""
    private var bufferEnd = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var currentToken: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.bufferEnd = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        advance()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = currentToken
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = bufferEnd

    private fun charAt(i: Int): Char = if (i < bufferEnd) buffer[i] else ' '

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= bufferEnd) {
            currentToken = null
            return
        }

        val c = charAt(tokenStart)

        if (c.isWhitespace()) {
            var i = tokenStart
            while (i < bufferEnd && charAt(i).isWhitespace()) i++
            tokenEnd = i
            currentToken = TokenType.WHITE_SPACE
            return
        }

        if (c == '/' && charAt(tokenStart + 1) == '/') {
            var i = tokenStart + 2
            while (i < bufferEnd && charAt(i) != '\n') i++
            tokenEnd = i
            currentToken = DCTokenTypes.LINE_COMMENT
            return
        }

        if (c == '/' && charAt(tokenStart + 1) == '*') {
            var i = tokenStart + 2
            while (i < bufferEnd) {
                if (charAt(i) == '*' && charAt(i + 1) == '/') {
                    i += 2
                    break
                }
                i++
            }
            tokenEnd = minOf(i, bufferEnd)
            currentToken = DCTokenTypes.BLOCK_COMMENT
            return
        }

        if (isAlpha(c)) {
            var i = tokenStart
            while (i < bufferEnd && isAlnum(charAt(i))) i++
            tokenEnd = i
            val text = buffer.subSequence(tokenStart, i).toString()
            currentToken = DCTokenTypes.STRUCTURAL_KEYWORDS[text]
                ?: when {
                    text in DCTokenTypes.BUILTIN_TYPES -> DCTokenTypes.TYPE_NAME
                    text in DCTokenTypes.BUILTIN_FIELD_KEYWORDS -> DCTokenTypes.FIELD_KEYWORD
                    else -> DCTokenTypes.IDENTIFIER
                }
            return
        }
        if (c.isDigit() ||
            (c == '.' && charAt(tokenStart + 1).isDigit()) ||
            ((c == '+' || c == '-') && charAt(tokenStart + 1).isDigit()) ||
            ((c == '+' || c == '-') && charAt(tokenStart + 1) == '.' && charAt(tokenStart + 2).isDigit())
        ) {
            lexNumber()
            return
        }

        if (c == '"' || c == '\'') {
            var i = tokenStart + 1
            while (i < bufferEnd && charAt(i) != c) {
                if (charAt(i) == '\n') break
                i++
            }
            if (i < bufferEnd && charAt(i) == c) i++
            tokenEnd = i
            currentToken = DCTokenTypes.STRING
            return
        }

        if (c == '<') {
            var i = tokenStart + 1
            while (i < bufferEnd && charAt(i) != '>' && charAt(i) != '\n') i++
            if (i < bufferEnd && charAt(i) == '>') i++
            tokenEnd = i
            currentToken = DCTokenTypes.HEX_STRING
            return
        }

        tokenEnd = tokenStart + 1
        currentToken = when (c) {
            '{' -> DCTokenTypes.LBRACE
            '}' -> DCTokenTypes.RBRACE
            '(' -> DCTokenTypes.LPAREN
            ')' -> DCTokenTypes.RPAREN
            '[' -> DCTokenTypes.LBRACKET
            ']' -> DCTokenTypes.RBRACKET
            ';' -> DCTokenTypes.SEMICOLON
            ',' -> DCTokenTypes.COMMA
            ':' -> DCTokenTypes.COLON
            '.' -> DCTokenTypes.DOT
            '/' -> DCTokenTypes.SLASH
            '%' -> DCTokenTypes.PERCENT
            '=' -> DCTokenTypes.EQUALS
            '*' -> DCTokenTypes.STAR
            '-' -> DCTokenTypes.MINUS
            '+' -> DCTokenTypes.PLUS
            else -> TokenType.BAD_CHARACTER
        }
    }

    private fun lexNumber() {
        var i = tokenStart
        if (charAt(i) == '+' || charAt(i) == '-') i++

        if (charAt(i) == '0' && (charAt(i + 1) == 'x' || charAt(i + 1) == 'X') && isHexDigit(charAt(i + 2))) {
            i += 2
            while (i < bufferEnd && isHexDigit(charAt(i))) i++
            tokenEnd = i
            currentToken = DCTokenTypes.INTEGER
            return
        }

        var isReal = false
        while (i < bufferEnd && charAt(i).isDigit()) i++
        if (i < bufferEnd && charAt(i) == '.') {
            isReal = true
            i++
            while (i < bufferEnd && charAt(i).isDigit()) i++
        }
        if (i < bufferEnd && (charAt(i) == 'e' || charAt(i) == 'E')) {
            val save = i
            i++
            if (charAt(i) == '+' || charAt(i) == '-') i++
            if (charAt(i).isDigit()) {
                isReal = true
                while (i < bufferEnd && charAt(i).isDigit()) i++
            } else {
                i = save
            }
        }
        tokenEnd = i
        currentToken = if (isReal) DCTokenTypes.REAL else DCTokenTypes.INTEGER
    }

    private fun isAlpha(c: Char) = (c in 'a'..'z') || (c in 'A'..'Z') || c == '_'
    private fun isAlnum(c: Char) = isAlpha(c) || c.isDigit()
    private fun isHexDigit(c: Char) = c.isDigit() || (c in 'a'..'f') || (c in 'A'..'F')
}
