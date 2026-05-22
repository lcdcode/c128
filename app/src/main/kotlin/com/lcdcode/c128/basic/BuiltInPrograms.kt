package com.lcdcode.c128.basic

object BuiltInPrograms {
    // A one-screen tour: a labelled bar chart from DATA, a Caesar cipher
    // demonstrating string functions, a sanity-checked POKE/PEEK round trip,
    // and a small countdown. Output fits within 25 rows so it doesn't scroll.
    // Skipped on purpose: INPUT (interactive), LOAD/GO (clobber the program),
    // HELP/LIST/NEW (interactive-only).
    private val DEMO = listOf(
        10 to "REM ----- C128 BASIC V7.0 SHOWCASE -----",
        20 to "PRINT CHR$(147)",
        30 to "PRINT \"+============================+\"",
        40 to "PRINT \"|  C128 BASIC V7.0 SHOWCASE  |\"",
        50 to "PRINT \"+============================+\"",
        100 to "REM --- BAR CHART FROM DATA ---",
        110 to "PRINT \"RAINFALL THIS WEEK (MM):\"",
        120 to "LET T=0 : LET M=0 : LET L=0",
        130 to "FOR I=1 TO 7",
        140 to "READ D$,V",
        150 to "GOSUB 800",
        160 to "T=T+V",
        170 to "IF V>M THEN M=V : H$=D$",
        180 to "IF V<10 THEN L=L+1",
        190 to "NEXT",
        200 to "PRINT \"TOTAL=\";T;\" AVG=\";INT(T*10/7)/10;\" MAX=\";M",
        210 to "PRINT \"WETTEST=\";H$;\" DRY(<10)=\";L",
        220 to "IF M>=20 AND L<=2 THEN PRINT \"RATING: STORMY (FEW DRY DAYS)\"",
        230 to "RESTORE : READ D$ : PRINT \"RESTORE-> 1ST=\";D$",
        300 to "REM --- STRINGS + FUNCTIONS ---",
        310 to "P$=\"HELLO\" : C$=\"\"",
        320 to "FOR I=1 TO LEN(P$)",
        330 to "C$=C$+CHR$(ASC(MID$(P$,I,1))+1)",
        340 to "NEXT",
        350 to "IF NOT (P$=C$) THEN PRINT \"CIPHER: \";P$;\" -> \";C$",
        360 to "PRINT \"ABS(-7)=\";ABS(-7);\" INT(3.7)=\";INT(3.7);\" 2^8=\";2^8",
        370 to "PRINT \"L=\";LEFT$(P$,3);\" R=\";RIGHT$(P$,2);\" M=\";MID$(P$,2,3)",
        380 to "PRINT \"STR=\"+STR$(M);\" VAL=\";VAL(\"3.14\");\" DICE=\";INT(RND(1)*6)+1",
        400 to "REM --- POKE/PEEK ---",
        410 to "POKE 0,ASC(\"H\") : POKE 1,ASC(\"I\")",
        420 to "IF PEEK(0)<>72 OR PEEK(1)<>73 THEN GOTO 440",
        430 to "PRINT \"POKE/PEEK: '\";CHR$(PEEK(0));CHR$(PEEK(1));\"'\" : GOTO 500",
        440 to "PRINT \"POKE/PEEK FAILED\"",
        500 to "REM --- COUNTDOWN + GREETING ---",
        510 to "PRINT TAB(5);",
        520 to "FOR I=3 TO 1 STEP -1 : PRINT I;\" \"; : NEXT",
        530 to "PRINT \"GO!\"",
        540 to "PRINT \"HELLO, WORLD!\"",
        550 to "GOTO 999",
        600 to "REM --- UNREACHED ---",
        610 to "PRINT \"(UNREACHABLE)\"",
        800 to "REM --- BAR-DRAW SUBROUTINE ---",
        810 to "PRINT D$;\" \";",
        820 to "FOR J=1 TO INT(V/2) : PRINT \"#\"; : NEXT",
        830 to "PRINT V",
        840 to "RETURN",
        900 to "DATA MON,12,TUE,18,WED,9,THU,22,FRI,15,SAT,7,SUN,11",
        999 to "END",
    )

    fun lookup(name: String): List<Pair<Int, String>>? {
        val key = name.uppercase().trim()
        return when (key) {
            "*", "HELLO", "HELLO,WORLD", "HELLO WORLD",
            "HELLO.PRG", "DEMO", "DEMO.PRG", "SHOWCASE" -> DEMO
            else -> null
        }
    }
}
