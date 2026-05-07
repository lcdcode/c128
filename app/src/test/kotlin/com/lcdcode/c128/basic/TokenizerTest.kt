package com.lcdcode.c128.basic

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenizerTest {
    @Test fun keywords() {
        val ts = Tokenizer.tokenize("PRINT 42")
        assertEquals(TokenType.PRINT, ts[0].type)
        assertEquals(TokenType.NUMBER, ts[1].type)
        assertEquals(42.0, ts[1].number, 0.0)
    }

    @Test fun stringPreservesCase() {
        val ts = Tokenizer.tokenize("PRINT \"Hello\"")
        assertEquals("Hello", ts[1].text)
    }

    @Test fun comparisonOps() {
        val ts = Tokenizer.tokenize("A<>B <= C >= D")
        val types = ts.map { it.type }
        assertEquals(
            listOf(
                TokenType.IDENT, TokenType.NEQ, TokenType.IDENT,
                TokenType.LTE, TokenType.IDENT,
                TokenType.GTE, TokenType.IDENT, TokenType.EOL
            ),
            types
        )
    }

    @Test fun stringIdentifier() {
        val ts = Tokenizer.tokenize("CHR$(65)")
        assertEquals(TokenType.IDENT, ts[0].type)
        assertEquals("CHR$", ts[0].text)
    }
}
