package com.lcdcode.c128.basic

import java.util.TreeMap

// Historical Commodore BASIC valid line range.
const val MAX_LINE_NUMBER = 63999

// 80 characters matches the canonical C128 logical-line limit; the on-screen
// editor only allows entering this much per line.
const val MAX_LINE_LENGTH = 80

// 64 KiB matches the real C128 BASIC program RAM budget. Going over should
// surface as ?OUT OF MEMORY, not a JVM OutOfMemoryError.
const val MAX_PROGRAM_BYTES = 65536

class Program {
    private val lines = TreeMap<Int, String>()
    // Lazy per-line token cache. Populated on first tokens() call, invalidated
    // whenever the line is edited. Keeps tight FOR/NEXT loops from re-tokenizing
    // the same source on every iteration.
    private val tokenCache = HashMap<Int, List<Token>>()
    private var totalBytes: Int = 0

    fun put(line: Int, source: String) {
        if (line < 0 || line > MAX_LINE_NUMBER) Err.syntax()
        if (source.length > MAX_LINE_LENGTH) Err.stringTooLong()
        val delta = source.length - (lines[line]?.length ?: 0)
        if (totalBytes + delta > MAX_PROGRAM_BYTES) Err.outOfMemory()
        lines[line] = source
        tokenCache.remove(line)
        totalBytes += delta
    }
    fun remove(line: Int) {
        val prev = lines.remove(line)
        if (prev != null) {
            totalBytes -= prev.length
            tokenCache.remove(line)
        }
    }
    fun clear() {
        lines.clear()
        tokenCache.clear()
        totalBytes = 0
    }
    fun isEmpty() = lines.isEmpty()
    fun get(line: Int): String? = lines[line]
    fun tokens(line: Int): List<Token>? {
        val src = lines[line] ?: return null
        return tokenCache.getOrPut(line) { Tokenizer.tokenize(src) }
    }
    fun firstLine(): Int? = lines.firstEntry()?.key
    fun nextLineAfter(line: Int): Int? = lines.higherKey(line)
    fun list(start: Int? = null, end: Int? = null): List<Pair<Int, String>> {
        val from = start ?: lines.firstEntry()?.key ?: return emptyList()
        val to = end ?: lines.lastEntry()?.key ?: return emptyList()
        return lines.subMap(from, true, to, true).map { it.key to it.value }
    }
}
