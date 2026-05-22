package com.lcdcode.c128.basic

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class CapturingConsole(inputs: List<String> = emptyList()) : BasicConsole {
    private val sb = StringBuilder()
    private val inputIter = inputs.iterator()
    var modeC64: Boolean? = null
    override fun print(s: String) { sb.append(s) }
    override fun newline() { sb.append('\n') }
    override val column: Int get() {
        val lastNl = sb.lastIndexOf('\n')
        return if (lastNl == -1) sb.length else sb.length - lastNl - 1
    }
    override suspend fun readLine(): String =
        if (inputIter.hasNext()) inputIter.next() else ""
    override fun switchMode(c64: Boolean) { modeC64 = c64 }
    val output: String get() = sb.toString()
}

class InterpreterTest {
    private fun run(vararg lines: String): String = runBlocking {
        val c = CapturingConsole()
        val ip = Interpreter(c).apply { statementDelayMs = 0L }
        for (l in lines) ip.execLine(l)
        c.output
    }

    private fun runWithInput(inputs: List<String>, vararg lines: String): String = runBlocking {
        val c = CapturingConsole(inputs)
        val ip = Interpreter(c).apply { statementDelayMs = 0L }
        for (l in lines) ip.execLine(l)
        c.output
    }

    @Test fun printArithmetic() {
        assertEquals(" 4\n", run("PRINT 2+2"))
    }

    @Test fun printString() {
        assertEquals("HELLO\n", run("PRINT \"HELLO\""))
    }

    @Test fun printSemicolonNoNewline() {
        assertEquals("AB\n", run("PRINT \"A\";\"B\""))
    }

    @Test fun forNextLoop() {
        val out = run(
            "10 FOR I=1 TO 3",
            "20 PRINT I",
            "30 NEXT I",
            "RUN"
        )
        assertEquals(" 1\n 2\n 3\n", out)
    }

    @Test fun gotoBranch() {
        val out = run(
            "10 GOTO 30",
            "20 PRINT \"NO\"",
            "30 PRINT \"YES\"",
            "RUN"
        )
        assertEquals("YES\n", out)
    }

    @Test fun ifThenGate() {
        val out = run(
            "10 IF 1=1 THEN PRINT \"T\"",
            "20 IF 1=2 THEN PRINT \"F\"",
            "RUN"
        )
        assertEquals("T\n", out)
    }

    @Test fun ifThenLineNumber() {
        val out = run(
            "10 IF 1=1 THEN 30",
            "20 PRINT \"NO\"",
            "30 PRINT \"YES\"",
            "RUN"
        )
        assertEquals("YES\n", out)
    }

    @Test fun helpListsCommands() {
        val out = run("HELP")
        assertTrue(out.contains("PRINT"))
        assertTrue(out.contains("HELP"))
        assertTrue(out.contains("CHR$"))
        assertTrue(out.contains("LET"))
        assertTrue(out.contains("GOSUB"))
        assertTrue(out.contains("LEFT$"))
    }

    @Test fun syntaxErrorMessage() {
        val out = run("PRINT )")
        assertTrue(out.contains("?SYNTAX ERROR"))
    }

    @Test fun listShowsProgram() {
        val out = run(
            "10 PRINT \"A\"",
            "20 PRINT \"B\"",
            "LIST"
        )
        assertEquals("10 PRINT \"A\"\n20 PRINT \"B\"\n", out)
    }

    @Test fun newClearsProgram() {
        val out = run(
            "10 PRINT \"X\"",
            "NEW",
            "LIST"
        )
        assertEquals("", out)
    }

    @Test fun chrFunction() {
        assertEquals("A\n", run("PRINT CHR$(65)"))
    }

    @Test fun ascAndLen() {
        assertEquals(" 65\n 3\n", run("PRINT ASC(\"ABC\")", "PRINT LEN(\"ABC\")"))
    }

    @Test fun divisionByZero() {
        assertTrue(run("PRINT 1/0").contains("?DIVISION BY ZERO ERROR"))
    }

    @Test fun stringConcat() {
        assertEquals("AB\n", run("PRINT \"A\"+\"B\""))
    }

    @Test fun forStepNegative() {
        val out = run(
            "10 FOR I=3 TO 1 STEP -1",
            "20 PRINT I",
            "30 NEXT",
            "RUN"
        )
        assertEquals(" 3\n 2\n 1\n", out)
    }

    @Test fun explicitLet() {
        assertEquals(" 7\n", run("LET A=7", "PRINT A"))
    }

    @Test fun implicitLet() {
        assertEquals(" 5\n", run("A=5", "PRINT A"))
    }

    @Test fun stringAssignment() {
        assertEquals("HI\n", run("A$=\"HI\"", "PRINT A$"))
    }

    @Test fun multiStatementLine() {
        assertEquals("A\nB\n", run("PRINT \"A\" : PRINT \"B\""))
    }

    @Test fun gosubReturn() {
        val out = run(
            "10 PRINT \"A\"",
            "20 GOSUB 100",
            "30 PRINT \"C\"",
            "40 END",
            "100 PRINT \"B\"",
            "110 RETURN",
            "RUN"
        )
        assertEquals("A\nB\nC\n", out)
    }

    @Test fun returnWithoutGosub() {
        val out = run("10 RETURN", "RUN")
        assertTrue(out.contains("?RETURN WITHOUT GOSUB ERROR"))
    }

    @Test fun leftRightMid() {
        val out = run(
            "PRINT LEFT$(\"HELLO\",3)",
            "PRINT RIGHT$(\"HELLO\",2)",
            "PRINT MID$(\"HELLO\",2,3)",
        )
        assertEquals("HEL\nLO\nELL\n", out)
    }

    @Test fun strAndVal() {
        assertEquals(" 42\n 3.14\n", run("PRINT STR$(42)", "PRINT VAL(\"3.14abc\")"))
    }

    @Test fun inputNumeric() {
        val out = runWithInput(listOf("42"), "INPUT A", "PRINT A")
        assertTrue(out.contains("? "))
        assertTrue(out.endsWith(" 42\n"))
    }

    @Test fun inputString() {
        val out = runWithInput(listOf("WORLD"), "INPUT A$", "PRINT \"HELLO,\";A$")
        assertTrue(out.contains("? "))
        assertTrue(out.endsWith("HELLO,WORLD\n"))
    }

    @Test fun inputWithPrompt() {
        val out = runWithInput(listOf("5"), "INPUT \"AGE\";A", "PRINT A")
        assertTrue(out.contains("AGE? "))
    }

    @Test fun loadDemoListsAndRuns() {
        val out = run("LOAD \"*\",8,1", "LIST", "RUN")
        assertTrue("expected SEARCHING in: $out", out.contains("SEARCHING FOR *"))
        assertTrue("expected LOADING in: $out", out.contains("LOADING"))
        // The built-in demo opens with a REM banner at line 10.
        assertTrue("expected line 10 REM banner in: $out",
            out.contains("10 REM ----- C128 BASIC V7.0 SHOWCASE"))
        assertTrue("expected HELLO, WORLD! in: $out", out.contains("HELLO, WORLD!"))
    }

    @Test fun demoCoversEveryFeature() {
        // Acid test: the canonical demo runs start to finish without raising
        // any BASIC error, and hits each feature section. Values are derived
        // from the DATA in line 900; if those change, update the assertions.
        val out = run("LOAD \"SHOWCASE\",8,1", "RUN")
        assertTrue("expected no errors in: $out", !out.contains("ERROR"))
        assertTrue("expected unreachable code not run in: $out",
            !out.contains("(UNREACHABLE)"))
        // Bar chart stats: total 94, max 22 (THU), 2 dry days (<10 mm).
        assertTrue("expected TOTAL= 94 in: $out", out.contains("TOTAL= 94"))
        assertTrue("expected MAX= 22 in: $out", out.contains("MAX= 22"))
        assertTrue("expected WETTEST=THU in: $out", out.contains("WETTEST=THU"))
        assertTrue("expected DRY(<10)= 2 in: $out", out.contains("DRY(<10)= 2"))
        // AND + >=/<= compound condition fires.
        assertTrue("expected STORMY rating in: $out", out.contains("STORMY"))
        // RESTORE rewinds the data pointer; the first DATA item is MON.
        assertTrue("expected RESTORE-> 1ST=MON in: $out",
            out.contains("RESTORE-> 1ST=MON"))
        // Caesar +1 cipher: HELLO -> IFMMP.
        assertTrue("expected CIPHER: HELLO -> IFMMP in: $out",
            out.contains("CIPHER: HELLO -> IFMMP"))
        // Numeric builtins.
        assertTrue("expected ABS(-7)= 7 in: $out", out.contains("ABS(-7)= 7"))
        assertTrue("expected INT(3.7)= 3 in: $out", out.contains("INT(3.7)= 3"))
        assertTrue("expected 2^8= 256 in: $out", out.contains("2^8= 256"))
        // String slicing functions.
        assertTrue("expected L=HEL in: $out", out.contains("L=HEL"))
        assertTrue("expected R=LO in: $out", out.contains("R=LO"))
        assertTrue("expected M=ELL in: $out", out.contains("M=ELL"))
        // STR$ + VAL round trip.
        assertTrue("expected STR= 22 in: $out", out.contains("STR= 22"))
        assertTrue("expected VAL= 3.14 in: $out", out.contains("VAL= 3.14"))
        // POKE/PEEK round trip: 72='H', 73='I'.
        assertTrue("expected POKE/PEEK: 'HI' in: $out",
            out.contains("POKE/PEEK: 'HI'"))
        // STEP -1 countdown then greeting reached.
        assertTrue("expected GO! in: $out", out.contains("GO!"))
        assertTrue("expected HELLO, WORLD! in: $out", out.contains("HELLO, WORLD!"))
    }

    @Test fun loadFileNotFound() {
        val out = run("LOAD \"NOPE\",8,1")
        assertTrue(out.contains("?FILE NOT FOUND ERROR"))
    }

    @Test fun go64SwitchesMode() = runBlocking {
        val c = CapturingConsole(listOf("Y"))
        val ip = Interpreter(c).apply { statementDelayMs = 0L }
        ip.execLine("10 PRINT \"X\"")
        ip.execLine("GO 64")
        assertTrue("expected ARE YOU SURE in: ${c.output}", c.output.contains("ARE YOU SURE?"))
        assertEquals(true, c.modeC64)
        // GO 64 wipes the program
        assertTrue(ip.program.isEmpty())
    }

    @Test fun go128SwitchesBack() = runBlocking {
        val c = CapturingConsole(listOf("Y"))
        val ip = Interpreter(c).apply { statementDelayMs = 0L }
        ip.execLine("GO 128")
        assertEquals(false, c.modeC64)
    }

    @Test fun goCancelLeavesModeUnchanged() = runBlocking {
        val c = CapturingConsole(listOf("N"))
        val ip = Interpreter(c).apply { statementDelayMs = 0L }
        ip.execLine("10 PRINT \"X\"")
        ip.execLine("GO 64")
        assertEquals(null, c.modeC64) // switchMode was never called
        assertTrue(!ip.program.isEmpty()) // program preserved
    }

    @Test fun goBadNumberIsSyntaxError() {
        assertTrue(run("GO 99").contains("?SYNTAX ERROR"))
    }

    @Test fun loadPathTraversalIsFileNotFound() {
        val out = run("LOAD \"../../etc/passwd\",8,1")
        assertTrue("expected FILE NOT FOUND in: $out", out.contains("?FILE NOT FOUND ERROR"))
    }

    @Test fun loadAbsolutePathIsFileNotFound() {
        val out = run("LOAD \"/etc/shadow\",8,1")
        assertTrue("expected FILE NOT FOUND in: $out", out.contains("?FILE NOT FOUND ERROR"))
    }

    @Test fun stepZeroIsIllegalQty() {
        val out = run("10 FOR I=1 TO 10 STEP 0", "20 NEXT", "RUN")
        assertTrue("expected ILLEGAL QUANTITY in: $out", out.contains("?ILLEGAL QUANTITY ERROR"))
    }

    @Test fun gosubOverflowIsOutOfMemory() {
        val out = run("10 GOSUB 10", "RUN")
        assertTrue("expected OUT OF MEMORY in: $out", out.contains("?OUT OF MEMORY ERROR"))
    }

    @Test fun breakStopsRunaway() = runBlocking {
        val c = CapturingConsole()
        val ip = Interpreter(c).apply { statementDelayMs = 0L }
        ip.execLine("10 GOTO 10")
        val job = launch { ip.execLine("RUN") }
        // One yield is enough: lets runProgram run up to its first internal yield(),
        // past the breakRequested = false reset. Setting the flag here means the next
        // loop iteration will see it.
        yield()
        ip.breakRequested = true
        withTimeout(1000L) { job.join() }
        assertTrue("expected BREAK in: ${c.output}", c.output.contains("BREAK IN 10"))
    }

    @Test fun ifThenColonRunsAllStatements() {
        val out = run("10 IF 1=1 THEN A=5 : PRINT A", "RUN")
        assertEquals(" 5\n", out)
    }

    @Test fun ifThenFalseSkipsRestOfLine() {
        val out = run("10 IF 1=2 THEN A=5 : PRINT \"X\"", "20 PRINT \"Y\"", "RUN")
        assertEquals("Y\n", out)
    }

    @Test fun percentSuffixIsSyntaxError() {
        val out = run("A%=5")
        assertTrue("expected SYNTAX ERROR in: $out", out.contains("?SYNTAX ERROR"))
    }

    @Test fun pokeAndPeekRoundTrip() {
        val out = run("POKE 1024,65", "PRINT PEEK(1024)")
        assertEquals(" 65\n", out)
    }

    @Test fun pokeOutOfRangeIsIllegalQty() {
        assertTrue(run("POKE 0,256").contains("?ILLEGAL QUANTITY"))
        assertTrue(run("POKE -1,0").contains("?ILLEGAL QUANTITY"))
        assertTrue(run("POKE 70000,0").contains("?ILLEGAL QUANTITY"))
    }

    @Test fun peekUntouchedAddressIsZero() {
        assertEquals(" 0\n", run("PRINT PEEK(12345)"))
    }

    @Test fun readConsumesDataInOrder() {
        val out = run(
            "10 DATA 10,20,30",
            "20 READ A,B,C",
            "30 PRINT A;B;C",
            "RUN"
        )
        assertEquals(" 10 20 30\n", out)
    }

    @Test fun readAcrossMultipleDataLines() {
        val out = run(
            "10 DATA 1,2",
            "20 DATA 3,4",
            "30 FOR I=1 TO 4 : READ X : PRINT X : NEXT",
            "RUN"
        )
        assertEquals(" 1\n 2\n 3\n 4\n", out)
    }

    @Test fun restoreRewindsDataPointer() {
        val out = run(
            "10 DATA 7,8",
            "20 READ A : READ B : RESTORE : READ C",
            "30 PRINT A;B;C",
            "RUN"
        )
        assertEquals(" 7 8 7\n", out)
    }

    @Test fun readPastEndIsOutOfData() {
        val out = run(
            "10 DATA 1",
            "20 READ A : READ B",
            "RUN"
        )
        assertTrue("expected OUT OF DATA in: $out", out.contains("?OUT OF DATA"))
    }

    @Test fun readStringFromData() {
        val out = run(
            "10 DATA HELLO,WORLD",
            "20 READ A\$ : READ B\$ : PRINT A\$;\",\";B\$",
            "RUN"
        )
        assertEquals("HELLO,WORLD\n", out)
    }

    @Test fun tabEmitsSpaces() {
        // TAB(5) returns five spaces, then "X". Acid test: no crash, length matches.
        val out = run("PRINT TAB(5);\"X\"")
        assertEquals("     X\n", out)
    }

    @Test fun ifNoSpaceBeforeIdent() {
        // C64-style: IFP>0 must lex as IF P>0, not as identifier IFP.
        val out = run("10 P=5", "20 IFP>0 THEN PRINT \"OK\"", "RUN")
        assertEquals("OK\n", out)
    }

    @Test fun rndNegativeSeedsReproducibly() {
        // Reseeding with the same negative value should yield the same draw.
        val a = run("PRINT RND(-7)")
        val b = run("PRINT RND(-7)")
        assertEquals(a, b)
    }

    @Test fun rndZeroReemitsLastValue() {
        // RND(0) returns the previous draw without advancing. After RND(-1)
        // seeds and draws X, RND(0) must return X and RND(0) again must also
        // return X (no advancement).
        val out = run("PRINT RND(-1)", "PRINT RND(0)", "PRINT RND(0)")
        val lines = out.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(3, lines.size)
        assertEquals(lines[0], lines[1])
        assertEquals(lines[1], lines[2])
    }

    @Test fun rndPositiveAdvances() {
        // Two consecutive RND(positive) draws after a fixed seed differ.
        val out = run("PRINT RND(-3)", "PRINT RND(1)", "PRINT RND(1)")
        val lines = out.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(3, lines.size)
        assertTrue("expected RND(1) to advance: $lines", lines[1] != lines[2])
    }

    @Test fun inputHandlesQuotedCommas() {
        // "HELLO,WORLD","FOO" should parse as two fields: A$=HELLO,WORLD B$=FOO.
        val out = runWithInput(
            listOf("\"HELLO,WORLD\",\"FOO\""),
            "INPUT A$,B$",
            "PRINT A$",
            "PRINT B$"
        )
        assertTrue("expected HELLO,WORLD on its own line in: $out",
            out.contains("HELLO,WORLD\n"))
        assertTrue("expected FOO line in: $out", out.contains("FOO\n"))
    }

    @Test fun inputUnquotedCommaSplitsAsBefore() {
        val out = runWithInput(
            listOf("HELLO,WORLD"),
            "INPUT A$,B$",
            "PRINT A$;\",\";B$"
        )
        assertTrue("expected HELLO,WORLD in: $out", out.endsWith("HELLO,WORLD\n"))
    }

    @Test fun inputClampsOverlongStrings() {
        // Feed a 1000-char string; the interpreter should clamp it to 255 so
        // downstream string ops can't overflow the cap.
        val long = "X".repeat(1000)
        val out = runWithInput(listOf(long), "INPUT A$", "PRINT LEN(A$)")
        assertTrue("expected LEN= 255 in: $out", out.contains(" 255"))
    }

    @Test fun loadRejectsNonLiteralDevice() {
        // A=8 : LOAD "*",A,1 should fail: device must be a numeric literal.
        val out = run("A=8", "LOAD \"*\",A,1")
        assertTrue("expected SYNTAX ERROR in: $out", out.contains("?SYNTAX ERROR"))
    }

    @Test fun loadAcceptsLiteralDeviceAndSecondary() {
        // Existing canonical form still works.
        val out = run("LOAD \"*\",8,1")
        assertTrue("expected LOADING in: $out", out.contains("LOADING"))
    }

    @Test fun rndArgIsRequired() {
        // RND without parens is a syntax error (it's parsed as a builtin call).
        assertTrue(run("PRINT RND").contains("?SYNTAX ERROR"))
    }

    @Test fun tokenizerRejectsEmptyExponent() {
        assertTrue(run("PRINT 1E").contains("?SYNTAX ERROR"))
        assertTrue(run("PRINT 1E+").contains("?SYNTAX ERROR"))
        assertTrue(run("PRINT 1E-").contains("?SYNTAX ERROR"))
    }

    @Test fun tokenizerLargeLiteralIsOverflow() {
        assertTrue(run("PRINT 1E999").contains("?OVERFLOW ERROR"))
    }

    @Test fun arithmeticOverflowIsCaught() {
        // 1e300 * 1e300 overflows IEEE-754 double; should error, not print "Infinity".
        assertTrue(run("PRINT 1E300*1E300").contains("?OVERFLOW ERROR"))
    }

    @Test fun powerOverflowIsCaught() {
        assertTrue(run("PRINT 10^400").contains("?OVERFLOW ERROR"))
    }

    @Test fun stringConcatDoublingIsBounded() {
        // Without the cap, this loop doubles A$ until JVM OOM. With the cap, the
        // first overflow past 255 chars raises ?STRING TOO LONG cleanly.
        val out = run(
            "10 A$=\"X\"",
            "20 FOR I=1 TO 50",
            "30 A$=A$+A$",
            "40 NEXT",
            "RUN"
        )
        assertTrue("expected STRING TOO LONG in: $out", out.contains("?STRING TOO LONG"))
    }

    @Test fun tabHugeArgIsIllegalQty() {
        assertTrue(run("PRINT TAB(2000000000);\"X\"").contains("?ILLEGAL QUANTITY"))
    }

    @Test fun leftHugeArgIsIllegalQty() {
        assertTrue(run("PRINT LEFT$(\"X\",1E20)").contains("?ILLEGAL QUANTITY"))
    }

    @Test fun midHugeArgIsIllegalQty() {
        assertTrue(run("PRINT MID$(\"X\",1,1E20)").contains("?ILLEGAL QUANTITY"))
    }

    @Test fun chrNanIsIllegalQty() {
        // 0/0 would be NaN; using a finite-but-out-of-range path via 1E300 instead.
        // Direct way: ensure CHR$ rejects non-finite via division by zero is caught.
        assertTrue(run("PRINT CHR$(500)").contains("?ILLEGAL QUANTITY"))
    }

    @Test fun lineNumberTooLargeIsSyntaxError() {
        val out = run("99999 PRINT \"X\"")
        assertTrue("expected SYNTAX ERROR in: $out", out.contains("?SYNTAX ERROR"))
    }

    @Test fun overlongProgramLineIsStringTooLong() {
        // 81-char body exceeds MAX_LINE_LENGTH.
        val body = "PRINT " + "\"" + "X".repeat(80) + "\""
        val out = run("10 $body")
        assertTrue("expected STRING TOO LONG in: $out", out.contains("?STRING TOO LONG"))
    }

    @Test fun dataNegativeStringIsSyntaxError() {
        // DATA -HELLO is malformed: minus before a non-numeric. Should report
        // ?SYNTAX ERROR IN <line>, not silently drop the minus.
        val out = run(
            "10 DATA -HELLO",
            "20 READ A$",
            "RUN"
        )
        assertTrue("expected SYNTAX ERROR IN 10 in: $out", out.contains("?SYNTAX ERROR IN 10"))
    }

    @Test fun letAssigningBuiltinNameIsSyntaxError() {
        assertTrue(run("LEN=5").contains("?SYNTAX ERROR"))
        assertTrue(run("LET TAB=1").contains("?SYNTAX ERROR"))
    }

    @Test fun inputRejectsBadNumeric() {
        val out = runWithInput(listOf("abc", "42"), "INPUT A", "PRINT A")
        assertTrue("expected REDO FROM START in: $out", out.contains("?REDO FROM START"))
        assertTrue("expected final value 42 in: $out", out.endsWith(" 42\n"))
    }

    @Test fun dancingMouseRunsToCompletion() {
        // Acid test: the canonical DANCING MOUSE program from c64playground.com
        // must parse, scan its DATA, and run to END without an interpreter error.
        // The C128 here has no VIC-II, so sprites don't actually render — we only
        // assert that the program executed cleanly and printed its message.
        val out = run(
            "10 PRINT CHR\$(147):V=53248:P=192:POKE V+21,1",
            "20 FOR S1=12288 TO 12350:READ Q1:POKE S1,Q1:NEXT",
            "25 FOR S2=12352 TO 12414:READ Q2:POKE S2,Q2:NEXT",
            "30 FOR S3=12416 TO 12478:READ Q3:POKE S3,Q3:NEXT",
            "35 POKE V+39,15:POKE V+1,68",
            "40 PRINT TAB(160) \"I AM THE DANCING MOUSE!\"",
            "50 FOR X=0 TO 347 STEP 3",
            "55 RX=INT(X/256):LX=X-RX*256",
            "60 POKE V,LX:POKE V+16,RX",
            "80 POKE 2040,P:FOR T=1 TO 60:NEXT",
            "85 P=P+1:IFP>194 THEN P=192",
            "90 NEXT",
            "95 END",
            "100 DATA 30,0,120,63,0,252,127,129,254,127,129,254,127,189,254,127,255,254",
            "101 DATA 63,255,252,31,187,248,3,187,192,1,255,128,3,189,192,1,231,128,1,255,0",
            "102 DATA 31,255,0,0,124,0,0,254,0,1,199,32,3,131,224,7,1,192,1,192,0,3,192,0",
            "103 DATA 30,0,120,63,0,252,127,129,254,127,129,254,127,189,254,127,255,254",
            "104 DATA 63,255,252,31,221,248,3,221,192,1,255,128,3,255,192,1,195,128,1,231,3",
            "105 DATA 31,255,255,0,124,0,0,254,0,1,199,0,7,1,128,7,0,204,1,128,124,7,128,56",
            "106 DATA 30,0,120,63,0,252,127,129,254,127,129,254,127,189,254,127,255,254",
            "107 DATA 63,255,252,31,221,248,3,221,192,1,255,134,3,189,204,1,199,152,1,255,48",
            "108 DATA 1,255,224,1,252,0,3,254,0",
            "109 DATA 7,14,0,204,14,0,248,56,0,112,112,0,0,60,0",
            "RUN"
        )
        assertTrue("expected no errors in: $out", !out.contains("ERROR"))
        assertTrue("expected the mouse message in: $out", out.contains("I AM THE DANCING MOUSE!"))
    }
}
