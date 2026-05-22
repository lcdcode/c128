package com.lcdcode.c128.basic

import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import java.util.Random
import kotlin.math.pow

private const val MAX_STACK_DEPTH = 256

// Returned when the cursor advances past the end of the token list. The
// tokenizer normally appends an EOL terminator, but defending here means a
// malformed statement can't escape the BasicError channel as an AIOOBE.
private val EOL_TOKEN = Token(TokenType.EOL, "")

interface BasicConsole {
    fun print(s: String)
    fun newline()
    fun println(s: String) { print(s); newline() }
    suspend fun readLine(): String
    fun switchMode(c64: Boolean) {}
    /** Current cursor column (0-based) on the active row. Used by PRINT for `,` zoning. */
    val column: Int get() = 0
}

private data class ForFrame(
    val varName: String,
    val limit: Double,
    val step: Double,
    val returnLine: Int,
    val returnIdx: Int,
    val returnTokens: List<Token>,
)

private data class GosubFrame(
    val returnLine: Int,
    val returnIdx: Int,
    val returnTokens: List<Token>,
)

class Interpreter(private val out: BasicConsole) {
    internal val program = Program()
    private val numVars = HashMap<String, Double>()
    private val strVars = HashMap<String, String>()
    private val forStack = ArrayDeque<ForFrame>()
    private val gosubStack = ArrayDeque<GosubFrame>()

    // 64K of simulated RAM for PEEK/POKE. No banking, no I/O side effects —
    // writes to VIC-II / SID / etc. registers are accepted but produce no output.
    private val ram = ByteArray(65536)

    // DATA values are collected once at RUN time (a flat queue across all DATA
    // statements in program-line order). READ pulls from here; RESTORE rewinds.
    private val dataItems = mutableListOf<Value>()
    private var dataPointer = 0

    private var tokens: List<Token> = emptyList()
    private var pos: Int = 0
    private var currentLine: Int = -1 // -1 = direct mode

    // Set from another thread (RUN/STOP gesture) to halt a running program at the
    // next yield point. Volatile because it's written off-thread.
    @Volatile var breakRequested: Boolean = false

    // Per-statement pacing. Real C128 BASIC 7.0 ran ~500-1000 stmts/sec; 2ms keeps
    // us in that ballpark and, because delay() suspends to the Main dispatcher,
    // lets Choreographer redraw between iterations so tight PRINT loops are visible.
    // Tests set this to 0 to keep the suite fast.
    var statementDelayMs: Long = 2L

    // RND state. Real BASIC: RND(positive) draws a new sample, RND(0) re-emits
    // the last one, RND(negative) reseeds with the integer part of the arg.
    private var random = Random()
    private var lastRnd = 0.0

    private fun rnd(x: Double): Value.Num {
        when {
            x < 0.0 -> {
                random = Random(x.toRawBits())
                lastRnd = random.nextDouble()
            }
            x == 0.0 -> { /* return previous value */ }
            else -> lastRnd = random.nextDouble()
        }
        return Value.Num(lastRnd)
    }

    /** Execute one line of source. */
    suspend fun execLine(source: String) {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return
        val firstSpace = trimmed.indexOfFirst { it == ' ' || it == '\t' }
        val head = if (firstSpace >= 0) trimmed.substring(0, firstSpace) else trimmed
        val lineNum = head.toIntOrNull()
        if (lineNum != null) {
            val rest = if (firstSpace >= 0) trimmed.substring(firstSpace + 1) else ""
            try {
                if (rest.isBlank()) program.remove(lineNum)
                else program.put(lineNum, rest)
            } catch (e: BasicError) {
                out.println(e.displayLine)
            }
            return
        }
        runDirect(trimmed)
    }

    private suspend fun runDirect(src: String) {
        currentLine = -1
        try {
            tokens = Tokenizer.tokenize(src)
            pos = 0
            executeStatements()
        } catch (e: BasicError) {
            printError(e)
            currentLine = -1
        }
    }

    private suspend fun runProgram(startLine: Int? = null) {
        val first = startLine ?: program.firstLine()
        if (first == null) return
        currentLine = first
        breakRequested = false
        try {
            while (currentLine != -1) {
                if (statementDelayMs > 0) delay(statementDelayMs) else yield()
                if (breakRequested) {
                    out.println("BREAK IN $currentLine")
                    breakRequested = false
                    currentLine = -1
                    return
                }
                tokens = program.tokens(currentLine) ?: Err.undef()
                pos = 0
                val cont = executeStatements()
                currentLine = when (cont) {
                    is Continuation.Goto -> cont.line
                    is Continuation.Next -> program.nextLineAfter(currentLine) ?: -1
                    is Continuation.End -> -1
                }
            }
        } catch (e: BasicError) {
            printError(e)
            currentLine = -1
        }
    }

    private fun printError(e: BasicError) {
        if (currentLine >= 0) out.println(e.displayLine + " IN " + currentLine)
        else out.println(e.displayLine)
    }

    private sealed class Continuation {
        data class Goto(val line: Int) : Continuation()
        object Next : Continuation()
        object End : Continuation()
    }

    private suspend fun executeStatements(): Continuation {
        while (true) {
            when (peek().type) {
                TokenType.EOL -> return Continuation.Next
                TokenType.COLON -> { advance(); continue }
                else -> {
                    val c = executeOneStatement() ?: continue
                    return c
                }
            }
        }
    }

    private suspend fun executeOneStatement(): Continuation? {
        val t = peek()
        return when (t.type) {
            TokenType.PRINT -> { advance(); doPrint(); null }
            TokenType.LIST -> { advance(); doList(); null }
            TokenType.NEW -> { advance(); doNew(); null }
            TokenType.RUN -> { advance(); doRun() }
            TokenType.END -> { advance(); Continuation.End }
            TokenType.REM -> { while (peek().type != TokenType.EOL) advance(); null }
            TokenType.HELP -> { advance(); doHelp(); null }
            TokenType.GOTO -> { advance(); Continuation.Goto(readLineNum()) }
            TokenType.IF -> { advance(); doIf() }
            TokenType.FOR -> { advance(); doFor(); null }
            TokenType.NEXT -> { advance(); doNext() }
            TokenType.LET -> { advance(); doLet(); null }
            TokenType.INPUT -> { advance(); doInput(); null }
            TokenType.GOSUB -> { advance(); doGosub() }
            TokenType.RETURN -> { advance(); doReturn() }
            TokenType.LOAD -> { advance(); doLoad(); null }
            TokenType.GO -> { advance(); doGo() }
            TokenType.DATA -> { advance(); doData(); null }
            TokenType.READ -> { advance(); doRead(); null }
            TokenType.RESTORE -> { advance(); doRestore(); null }
            TokenType.POKE -> { advance(); doPoke(); null }
            TokenType.IDENT -> { doLet(); null } // implicit LET
            else -> Err.syntax()
        }
    }

    private fun doPrint() {
        var lastSep: TokenType? = null
        while (true) {
            val t = peek().type
            if (t == TokenType.EOL || t == TokenType.COLON) break
            if (t == TokenType.SEMI) { advance(); lastSep = TokenType.SEMI; continue }
            if (t == TokenType.COMMA) {
                advance()
                lastSep = TokenType.COMMA
                val target = ((out.column / 10) + 1) * 10
                while (out.column < target) out.print(" ")
                continue
            }
            val v = evalExpr()
            out.print(formatValue(v))
            lastSep = null
        }
        if (lastSep == null) out.newline()
    }

    private fun formatValue(v: Value): String = when (v) {
        is Value.Str -> v.v
        is Value.Num -> basicFormatNumber(v.v)
    }

    private fun doList() {
        var start: Int? = null
        var end: Int? = null
        if (peek().type == TokenType.NUMBER) {
            start = consume(TokenType.NUMBER).number.toInt()
            if (peek().type == TokenType.MINUS) {
                advance()
                if (peek().type == TokenType.NUMBER) end = consume(TokenType.NUMBER).number.toInt()
            } else {
                end = start
            }
        }
        program.list(start, end).forEach { (n, src) ->
            out.println("$n $src")
        }
    }

    private fun doNew() {
        program.clear()
        numVars.clear(); strVars.clear()
        forStack.clear(); gosubStack.clear()
        ram.fill(0)
        dataItems.clear(); dataPointer = 0
    }

    private suspend fun doRun(): Continuation {
        var line: Int? = null
        if (peek().type == TokenType.NUMBER) line = consume(TokenType.NUMBER).number.toInt()
        numVars.clear(); strVars.clear()
        forStack.clear(); gosubStack.clear()
        scanDataItems()
        runProgram(line)
        return Continuation.End
    }

    /** Walk every program line, collect DATA literals into a flat queue. */
    private fun scanDataItems() {
        dataItems.clear()
        dataPointer = 0
        for ((line, _) in program.list()) {
            // Set currentLine so a malformed DATA statement reports the offending
            // line via the shared error printer rather than a bare ?SYNTAX ERROR.
            currentLine = line
            val ts = program.tokens(line) ?: continue
            var i = 0
            while (i < ts.size) {
                if (ts[i].type != TokenType.DATA) { i++; continue }
                i++ // past DATA
                while (i < ts.size && ts[i].type != TokenType.EOL && ts[i].type != TokenType.COLON) {
                    var negate = false
                    if (ts[i].type == TokenType.MINUS) { negate = true; i++ }
                    if (i >= ts.size) Err.syntax()
                    val tk = ts[i]
                    when (tk.type) {
                        TokenType.NUMBER -> dataItems.add(Value.Num(if (negate) -tk.number else tk.number))
                        TokenType.STRING -> {
                            if (negate) Err.syntax()
                            dataItems.add(Value.Str(tk.text))
                        }
                        TokenType.IDENT -> {
                            if (negate) Err.syntax()
                            dataItems.add(Value.Str(tk.text))
                        }
                        else -> Err.syntax()
                    }
                    i++
                    if (i < ts.size && ts[i].type == TokenType.COMMA) i++
                }
            }
        }
    }

    private fun doData() {
        // Runtime no-op: values were already collected by scanDataItems().
        // Just consume tokens until end of statement.
        while (peek().type != TokenType.EOL && peek().type != TokenType.COLON) advance()
    }

    private fun doRead() {
        while (true) {
            val nameTok = consume(TokenType.IDENT)
            if (dataPointer >= dataItems.size) Err.outOfData()
            val item = dataItems[dataPointer++]
            if (nameTok.text.endsWith("$")) {
                val s = when (item) {
                    is Value.Str -> item.v
                    is Value.Num -> basicFormatNumber(item.v).trim()
                }
                strVars[nameTok.text] = s
            } else {
                val n = when (item) {
                    is Value.Num -> item.v
                    is Value.Str -> item.v.toDoubleOrNull() ?: Err.typeMismatch()
                }
                numVars[nameTok.text] = n
            }
            if (peek().type != TokenType.COMMA) break
            advance()
        }
    }

    private fun doRestore() {
        // Optional line-number argument is accepted but ignored; we always
        // rewind to the start of the data queue, which matches the common case.
        if (peek().type == TokenType.NUMBER) advance()
        dataPointer = 0
    }

    private fun doPoke() {
        val addr = numOf(evalExpr()).toInt()
        if (peek().type != TokenType.COMMA) Err.syntax()
        advance()
        val value = numOf(evalExpr()).toInt()
        if (addr !in 0..65535) Err.illegalQty()
        if (value !in 0..255) Err.illegalQty()
        ram[addr] = value.toByte()
    }

    internal fun peekByte(addr: Int): Int {
        if (addr !in 0..65535) Err.illegalQty()
        return ram[addr].toInt() and 0xFF
    }

    private fun doHelp() {
        out.println("C128 BASIC V7.0 SUBSET")
        out.println("STATEMENTS:")
        out.println(" PRINT LIST NEW RUN END REM")
        out.println(" GOTO IF/THEN FOR/TO/STEP NEXT")
        out.println(" LET INPUT GOSUB RETURN")
        out.println(" LOAD HELP GO")
        out.println(" DATA READ RESTORE POKE")
        out.println("FUNCTIONS:")
        out.println(" ABS INT RND LEN CHR$ ASC")
        out.println(" LEFT$ RIGHT$ MID$ STR$ VAL")
        out.println(" PEEK TAB")
        out.println("OPERATORS: + - * / ^ = <> < > <= >=")
        out.println(" AND OR NOT")
    }

    private suspend fun doIf(): Continuation? {
        val cond = evalExpr()
        if (peek().type != TokenType.THEN) Err.syntax()
        advance() // THEN
        val truthy = when (cond) {
            is Value.Num -> cond.v != 0.0
            is Value.Str -> Err.typeMismatch()
        }
        if (!truthy) {
            while (peek().type != TokenType.EOL) advance()
            return null
        }
        if (peek().type == TokenType.NUMBER) {
            return Continuation.Goto(consume(TokenType.NUMBER).number.toInt())
        }
        // Run the entire remainder of the line; THEN A=1 : PRINT A must execute both.
        return executeStatements()
    }

    private fun doFor() {
        val varTok = consume(TokenType.IDENT)
        if (varTok.text.endsWith("$")) Err.typeMismatch()
        consume(TokenType.EQ)
        val initial = numOf(evalExpr())
        consume(TokenType.TO)
        val limit = numOf(evalExpr())
        val step = if (peek().type == TokenType.STEP) { advance(); numOf(evalExpr()) } else 1.0
        if (step == 0.0) Err.illegalQty()
        if (forStack.size >= MAX_STACK_DEPTH) Err.outOfMemory()
        numVars[varTok.text] = initial
        forStack.addLast(
            ForFrame(varTok.text, limit, step, currentLine, pos, tokens)
        )
    }

    private fun doNext(): Continuation? {
        // C128 BASIC: NEXT with a name unwinds nested FORs until the matching var is found.
        val varName = if (peek().type == TokenType.IDENT) consume(TokenType.IDENT).text else null
        if (forStack.isEmpty()) Err.nextWithoutFor()
        val frame = if (varName != null) {
            while (forStack.isNotEmpty() && forStack.last().varName != varName) forStack.removeLast()
            if (forStack.isEmpty()) Err.nextWithoutFor()
            forStack.last()
        } else {
            forStack.last()
        }
        val cur = (numVars[frame.varName] ?: 0.0) + frame.step
        numVars[frame.varName] = cur
        val done = if (frame.step >= 0) cur > frame.limit else cur < frame.limit
        if (done) {
            forStack.removeLast()
            return null
        }
        tokens = frame.returnTokens
        pos = frame.returnIdx
        currentLine = frame.returnLine
        return null
    }

    private fun doLet() {
        val nameTok = consume(TokenType.IDENT)
        // Don't let `LEN = 5` or `CHR$ = "X"` silently shadow a builtin.
        if (Builtins.isBuiltin(nameTok.text) || nameTok.text == "PEEK") Err.syntax()
        if (peek().type != TokenType.EQ) Err.syntax()
        advance()
        val v = evalExpr()
        if (nameTok.text.endsWith("$")) {
            val s = (v as? Value.Str)?.v ?: Err.typeMismatch()
            strVars[nameTok.text] = s
        } else {
            val n = (v as? Value.Num)?.v ?: Err.typeMismatch()
            numVars[nameTok.text] = n
        }
    }

    private suspend fun doInput() {
        var prompt: String? = null
        if (peek().type == TokenType.STRING) {
            prompt = consume(TokenType.STRING).text
            val sep = peek().type
            if (sep != TokenType.SEMI && sep != TokenType.COMMA) Err.syntax()
            advance()
        }
        val vars = mutableListOf<Token>()
        vars += consume(TokenType.IDENT)
        while (peek().type == TokenType.COMMA) { advance(); vars += consume(TokenType.IDENT) }
        while (true) {
            if (prompt != null) out.print(prompt)
            out.print("? ")
            val line = out.readLine()
            val parts = if (vars.size > 1) splitInputFields(line) else listOf(line.trim())
            val parsed = arrayOfNulls<Any?>(vars.size)
            var redo = false
            for ((i, vt) in vars.withIndex()) {
                val raw = parts.getOrNull(i) ?: ""
                if (vt.text.endsWith("$")) {
                    // Clamp here so a future console (or a test fixture) can't
                    // feed gigabyte strings that defeat the string-length cap.
                    parsed[i] = if (raw.length > MAX_STRING_LENGTH)
                        raw.substring(0, MAX_STRING_LENGTH) else raw
                } else {
                    val n = raw.toDoubleOrNull()
                    if (n == null) { redo = true; break }
                    parsed[i] = n
                }
            }
            if (redo) { out.println("?REDO FROM START"); continue }
            for ((i, vt) in vars.withIndex()) {
                if (vt.text.endsWith("$")) strVars[vt.text] = parsed[i] as String
                else numVars[vt.text] = parsed[i] as Double
            }
            return
        }
    }

    // Split INPUT response on commas, but keep commas that fall inside a pair
    // of double quotes. Surrounding quotes are stripped; whitespace is trimmed.
    // Matches the canonical BASIC behaviour where `"A,B","C"` parses as two
    // fields: `A,B` and `C`.
    private fun splitInputFields(s: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (c in s) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    out += current.toString().trim()
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        out += current.toString().trim()
        return out
    }

    private fun doGosub(): Continuation {
        val target = readLineNum()
        if (gosubStack.size >= MAX_STACK_DEPTH) Err.outOfMemory()
        gosubStack.addLast(GosubFrame(currentLine, pos, tokens))
        return Continuation.Goto(target)
    }

    private fun doReturn(): Continuation? {
        if (gosubStack.isEmpty()) Err.returnWithoutGosub()
        val frame = gosubStack.removeLast()
        tokens = frame.returnTokens
        pos = frame.returnIdx
        currentLine = frame.returnLine
        return null
    }

    private suspend fun doGo(): Continuation {
        val n = readLineNum()
        if (n != 64 && n != 128) Err.syntax()
        out.print("ARE YOU SURE? ")
        val ans = out.readLine().trim().uppercase()
        if (ans.firstOrNull() != 'Y') return Continuation.End
        when (n) {
            64 -> { out.switchMode(true); doNew() }
            128 -> { out.switchMode(false); doNew() }
        }
        return Continuation.End
    }

    private fun doLoad() {
        val nameVal = evalExpr()
        val name = (nameVal as? Value.Str)?.v ?: Err.typeMismatch()
        // Optional ,device,secondary: real BASIC requires these to be numeric
        // literals. Restricting to NUMBER tokens (rather than expressions) means
        // a malicious program can't trigger side effects via an evaluated arg.
        if (peek().type == TokenType.COMMA) {
            advance()
            if (peek().type != TokenType.NUMBER) Err.syntax()
            advance()
            if (peek().type == TokenType.COMMA) {
                advance()
                if (peek().type != TokenType.NUMBER) Err.syntax()
                advance()
            }
        }
        val prog = BuiltInPrograms.lookup(name) ?: Err.fileNotFound()
        out.println("SEARCHING FOR $name")
        out.println("LOADING")
        program.clear()
        for ((line, src) in prog) program.put(line, src)
        numVars.clear(); strVars.clear()
        forStack.clear(); gosubStack.clear()
    }

    private fun readLineNum(): Int {
        val t = peek()
        if (t.type != TokenType.NUMBER) Err.syntax()
        advance()
        return t.number.toInt()
    }

    // ---- Expressions: recursive descent ----
    private fun evalExpr(): Value = evalOr()
    private fun evalOr(): Value {
        var left = evalAnd()
        while (peek().type == TokenType.OR) {
            advance(); val r = evalAnd()
            left = Value.Num(if ((numOf(left) != 0.0) || (numOf(r) != 0.0)) -1.0 else 0.0)
        }
        return left
    }
    private fun evalAnd(): Value {
        var left = evalNot()
        while (peek().type == TokenType.AND) {
            advance(); val r = evalNot()
            left = Value.Num(if ((numOf(left) != 0.0) && (numOf(r) != 0.0)) -1.0 else 0.0)
        }
        return left
    }
    private fun evalNot(): Value {
        if (peek().type == TokenType.NOT) {
            advance()
            val v = evalNot()
            return Value.Num(if (numOf(v) == 0.0) -1.0 else 0.0)
        }
        return evalCompare()
    }
    private fun evalCompare(): Value {
        val left = evalAdd()
        val t = peek().type
        if (t in setOf(
                TokenType.EQ, TokenType.NEQ, TokenType.LT,
                TokenType.GT, TokenType.LTE, TokenType.GTE
            )) {
            advance(); val r = evalAdd()
            return Value.Num(compare(t, left, r))
        }
        return left
    }
    private fun compare(op: TokenType, a: Value, b: Value): Double {
        val result = when {
            a is Value.Num && b is Value.Num -> when (op) {
                TokenType.EQ -> a.v == b.v
                TokenType.NEQ -> a.v != b.v
                TokenType.LT -> a.v < b.v
                TokenType.GT -> a.v > b.v
                TokenType.LTE -> a.v <= b.v
                TokenType.GTE -> a.v >= b.v
                else -> Err.syntax()
            }
            a is Value.Str && b is Value.Str -> when (op) {
                TokenType.EQ -> a.v == b.v
                TokenType.NEQ -> a.v != b.v
                TokenType.LT -> a.v < b.v
                TokenType.GT -> a.v > b.v
                TokenType.LTE -> a.v <= b.v
                TokenType.GTE -> a.v >= b.v
                else -> Err.syntax()
            }
            else -> Err.typeMismatch()
        }
        return if (result) -1.0 else 0.0
    }
    private fun evalAdd(): Value {
        var left = evalMul()
        while (peek().type == TokenType.PLUS || peek().type == TokenType.MINUS) {
            val op = advance().type
            val r = evalMul()
            left = when {
                left is Value.Str && r is Value.Str && op == TokenType.PLUS ->
                    Value.Str(concatStrings(left.v, r.v))
                left is Value.Num && r is Value.Num ->
                    finite(if (op == TokenType.PLUS) left.v + r.v else left.v - r.v)
                else -> Err.typeMismatch()
            }
        }
        return left
    }
    private fun evalMul(): Value {
        var left = evalPow()
        while (peek().type == TokenType.STAR || peek().type == TokenType.SLASH) {
            val op = advance().type
            val r = numOf(evalPow())
            val l = numOf(left)
            left = when (op) {
                TokenType.STAR -> finite(l * r)
                TokenType.SLASH -> {
                    if (r == 0.0) Err.divZero()
                    finite(l / r)
                }
                else -> Err.syntax()
            }
        }
        return left
    }
    private fun evalPow(): Value {
        val base = evalUnary()
        if (peek().type == TokenType.CARET) {
            advance()
            val exp = evalUnary()
            return finite(numOf(base).pow(numOf(exp)))
        }
        return base
    }

    private fun finite(d: Double): Value.Num {
        if (!d.isFinite()) Err.overflow()
        return Value.Num(d)
    }

    private fun concatStrings(a: String, b: String): String {
        if (a.length + b.length > MAX_STRING_LENGTH) Err.stringTooLong()
        return a + b
    }
    private fun evalUnary(): Value {
        if (peek().type == TokenType.MINUS) {
            advance()
            return Value.Num(-numOf(evalUnary()))
        }
        if (peek().type == TokenType.PLUS) { advance(); return evalUnary() }
        return evalAtom()
    }
    private fun evalAtom(): Value {
        val t = peek()
        return when (t.type) {
            TokenType.NUMBER -> { advance(); Value.Num(t.number) }
            TokenType.STRING -> { advance(); Value.Str(t.text) }
            TokenType.LPAREN -> {
                advance()
                val v = evalExpr()
                if (peek().type != TokenType.RPAREN) Err.syntax()
                advance(); v
            }
            TokenType.IDENT -> {
                advance()
                if (t.text == "PEEK") {
                    if (peek().type != TokenType.LPAREN) Err.syntax()
                    advance()
                    val addr = numOf(evalExpr()).toInt()
                    if (peek().type != TokenType.RPAREN) Err.syntax()
                    advance()
                    return Value.Num(peekByte(addr).toDouble())
                }
                if (Builtins.isBuiltin(t.text)) {
                    if (peek().type != TokenType.LPAREN) Err.syntax()
                    advance()
                    val args = mutableListOf<Value>()
                    if (peek().type != TokenType.RPAREN) {
                        args += evalExpr()
                        while (peek().type == TokenType.COMMA) { advance(); args += evalExpr() }
                    }
                    if (peek().type != TokenType.RPAREN) Err.syntax()
                    advance()
                    // RND owns interpreter-local state; route it through rnd().
                    if (t.text == "RND") {
                        if (args.size != 1) Err.syntax()
                        return rnd(numOf(args[0]))
                    }
                    return Builtins.call(t.text, args)
                }
                if (t.text.endsWith("$")) Value.Str(strVars[t.text] ?: "")
                else Value.Num(numVars[t.text] ?: 0.0)
            }
            else -> Err.syntax()
        }
    }

    private fun numOf(v: Value): Double = (v as? Value.Num)?.v ?: Err.typeMismatch()

    private fun peek(): Token = if (pos < tokens.size) tokens[pos] else EOL_TOKEN
    private fun advance(): Token =
        if (pos < tokens.size) tokens[pos++] else EOL_TOKEN
    private fun consume(t: TokenType): Token {
        if (peek().type != t) Err.syntax()
        return advance()
    }
}
