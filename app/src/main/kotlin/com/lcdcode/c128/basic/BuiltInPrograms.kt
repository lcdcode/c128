package com.lcdcode.c128.basic

object BuiltInPrograms {
    private val HELLO = listOf(
        10 to "REM HELLO WORLD",
        20 to "PRINT \"HELLO, WORLD!\"",
    )

    fun lookup(name: String): List<Pair<Int, String>>? {
        val key = name.uppercase().trim()
        return when (key) {
            "*", "HELLO", "HELLO,WORLD", "HELLO WORLD", "HELLO.PRG" -> HELLO
            else -> null
        }
    }
}
