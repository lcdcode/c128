package com.lcdcode.c128.basic

import java.util.TreeMap

class Program {
    private val lines = TreeMap<Int, String>()

    fun put(line: Int, source: String) { lines[line] = source }
    fun remove(line: Int) { lines.remove(line) }
    fun clear() { lines.clear() }
    fun isEmpty() = lines.isEmpty()
    fun get(line: Int): String? = lines[line]
    fun firstLine(): Int? = lines.firstEntry()?.key
    fun nextLineAfter(line: Int): Int? = lines.higherKey(line)
    fun list(start: Int? = null, end: Int? = null): List<Pair<Int, String>> {
        val from = start ?: lines.firstEntry()?.key ?: return emptyList()
        val to = end ?: lines.lastEntry()?.key ?: return emptyList()
        return lines.subMap(from, true, to, true).map { it.key to it.value }
    }
}
