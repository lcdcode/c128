package com.lcdcode.c128.basic

enum class TokenType {
    NUMBER, STRING, IDENT,
    PRINT, LIST, NEW, RUN, END, REM, HELP,
    GOTO, IF, THEN, FOR, TO, STEP, NEXT,
    LET, INPUT, GOSUB, RETURN, LOAD, GO,
    DATA, READ, RESTORE, POKE,
    AND, OR, NOT,
    PLUS, MINUS, STAR, SLASH, CARET,
    EQ, NEQ, LT, GT, LTE, GTE,
    LPAREN, RPAREN, COMMA, SEMI, COLON,
    EOL,
}

data class Token(val type: TokenType, val text: String, val number: Double = 0.0)

private val KEYWORDS = mapOf(
    "PRINT" to TokenType.PRINT,
    "LIST" to TokenType.LIST,
    "NEW" to TokenType.NEW,
    "RUN" to TokenType.RUN,
    "END" to TokenType.END,
    "REM" to TokenType.REM,
    "HELP" to TokenType.HELP,
    "GOTO" to TokenType.GOTO,
    "IF" to TokenType.IF,
    "THEN" to TokenType.THEN,
    "FOR" to TokenType.FOR,
    "TO" to TokenType.TO,
    "STEP" to TokenType.STEP,
    "NEXT" to TokenType.NEXT,
    "LET" to TokenType.LET,
    "INPUT" to TokenType.INPUT,
    "GOSUB" to TokenType.GOSUB,
    "RETURN" to TokenType.RETURN,
    "LOAD" to TokenType.LOAD,
    "GO" to TokenType.GO,
    "DATA" to TokenType.DATA,
    "READ" to TokenType.READ,
    "RESTORE" to TokenType.RESTORE,
    "POKE" to TokenType.POKE,
    "AND" to TokenType.AND,
    "OR" to TokenType.OR,
    "NOT" to TokenType.NOT,
)

object Tokenizer {
    fun tokenize(source: String): List<Token> {
        val src = source.uppercase()
        val out = mutableListOf<Token>()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c == ' ' || c == '\t' -> i++
                c == '"' -> {
                    val sb = StringBuilder()
                    i++
                    while (i < src.length && src[i] != '"') {
                        sb.append(source[i]); i++
                    }
                    if (i < src.length) i++ // closing quote (or EOL)
                    out += Token(TokenType.STRING, sb.toString())
                }
                c.isDigit() || (c == '.' && i + 1 < src.length && src[i + 1].isDigit()) -> {
                    val start = i
                    while (i < src.length && src[i].isDigit()) i++
                    if (i < src.length && src[i] == '.') {
                        i++
                        while (i < src.length && src[i].isDigit()) i++
                    }
                    if (i < src.length && src[i] == 'E') {
                        i++
                        if (i < src.length && (src[i] == '+' || src[i] == '-')) i++
                        val expDigitsStart = i
                        while (i < src.length && src[i].isDigit()) i++
                        // Require at least one digit after E / E+ / E-. Without this,
                        // strings like "1E" or "1E-" would parse to NumberFormatException
                        // and escape the BasicError channel.
                        if (i == expDigitsStart) Err.syntax()
                    }
                    val text = src.substring(start, i)
                    val parsed = try { text.toDouble() } catch (_: NumberFormatException) { Err.syntax() }
                    if (!parsed.isFinite()) Err.overflow()
                    out += Token(TokenType.NUMBER, text, parsed)
                }
                c.isLetter() -> {
                    val start = i
                    while (i < src.length && (src[i].isLetterOrDigit())) i++
                    // Only $-suffix is supported (string vars). %-suffix (integer vars in real
                    // C128 BASIC) is not implemented; let the punctuation branch reject it as
                    // a syntax error rather than silently treating "A%" as numeric.
                    if (i < src.length && src[i] == '$') i++
                    val word = src.substring(start, i)
                    // REM consumes the rest of the line; LIST reads from program source, not
                    // tokens, so we don't need to keep the comment text in the token stream.
                    if (word == "REM") {
                        out += Token(TokenType.REM, word)
                        i = src.length
                    } else {
                        val kw = KEYWORDS[word]
                        if (kw != null) {
                            out += Token(kw, word)
                        } else {
                            // C64-style keyword-prefix matching: in real BASIC `IFP>194` lexes
                            // as IF + P, not as an identifier "IFP". If no whole-word match,
                            // emit the longest keyword prefix and rewind to re-scan the rest.
                            // String-suffixed names ("AB$") are left alone.
                            var matched = false
                            if (!word.endsWith("$")) {
                                for (len in word.length - 1 downTo 2) {
                                    val prefix = word.substring(0, len)
                                    val pkw = KEYWORDS[prefix]
                                    if (pkw != null) {
                                        out += Token(pkw, prefix)
                                        i = start + len
                                        matched = true
                                        break
                                    }
                                }
                            }
                            if (!matched) out += Token(TokenType.IDENT, word)
                        }
                    }
                }
                else -> {
                    val two = if (i + 1 < src.length) src.substring(i, i + 2) else ""
                    when (two) {
                        "<>" -> { out += Token(TokenType.NEQ, two); i += 2; continue }
                        "<=" -> { out += Token(TokenType.LTE, two); i += 2; continue }
                        ">=" -> { out += Token(TokenType.GTE, two); i += 2; continue }
                    }
                    val tt = when (c) {
                        '+' -> TokenType.PLUS
                        '-' -> TokenType.MINUS
                        '*' -> TokenType.STAR
                        '/' -> TokenType.SLASH
                        '^' -> TokenType.CARET
                        '=' -> TokenType.EQ
                        '<' -> TokenType.LT
                        '>' -> TokenType.GT
                        '(' -> TokenType.LPAREN
                        ')' -> TokenType.RPAREN
                        ',' -> TokenType.COMMA
                        ';' -> TokenType.SEMI
                        ':' -> TokenType.COLON
                        else -> Err.syntax()
                    }
                    out += Token(tt, c.toString())
                    i++
                }
            }
        }
        out += Token(TokenType.EOL, "")
        return out
    }
}
