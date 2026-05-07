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

    @Test fun loadHelloWorld() {
        val out = run("LOAD \"*\",8,1", "LIST", "RUN")
        assertTrue("expected SEARCHING in: $out", out.contains("SEARCHING FOR *"))
        assertTrue("expected LOADING in: $out", out.contains("LOADING"))
        assertTrue("expected REM HELLO WORLD in: $out", out.contains("10 REM HELLO WORLD"))
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
}
