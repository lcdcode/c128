package com.lcdcode.c128.basic

class BasicError(val code: String, message: String? = null) :
    RuntimeException(message ?: code) {
    val displayLine: String get() = "?$code ERROR"
}

object Err {
    fun syntax(): Nothing = throw BasicError("SYNTAX")
    fun undef(): Nothing = throw BasicError("UNDEF'D STATEMENT")
    fun divZero(): Nothing = throw BasicError("DIVISION BY ZERO")
    fun typeMismatch(): Nothing = throw BasicError("TYPE MISMATCH")
    fun nextWithoutFor(): Nothing = throw BasicError("NEXT WITHOUT FOR")
    fun illegalQty(): Nothing = throw BasicError("ILLEGAL QUANTITY")
    fun returnWithoutGosub(): Nothing = throw BasicError("RETURN WITHOUT GOSUB")
    fun fileNotFound(): Nothing = throw BasicError("FILE NOT FOUND")
    fun outOfMemory(): Nothing = throw BasicError("OUT OF MEMORY")
}
