package com.lcdcode.c128.basic

import kotlin.math.absoluteValue

sealed class Value {
    data class Num(val v: Double) : Value()
    data class Str(val v: String) : Value()
}

internal fun basicFormatNumber(d: Double): String {
    val sign = if (d < 0) "-" else " "
    val abs = kotlin.math.abs(d)
    val text = if (abs == kotlin.math.floor(abs) && abs < 1e16)
        abs.toLong().toString()
    else
        abs.toString()
    return sign + text
}

object Builtins {
    val names = listOf(
        "ABS", "INT", "RND", "LEN", "CHR$", "ASC",
        "LEFT$", "RIGHT$", "MID$", "STR$", "VAL",
    )

    fun isBuiltin(name: String) = name in names

    fun call(name: String, args: List<Value>): Value = when (name) {
        "ABS" -> num(args, 1) { Value.Num(it[0].absoluteValue) }
        "INT" -> num(args, 1) { Value.Num(kotlin.math.floor(it[0])) }
        "RND" -> num(args, 1) { Value.Num(Math.random()) }
        "LEN" -> {
            require1(args); val s = strArg(args[0])
            Value.Num(s.length.toDouble())
        }
        "CHR$" -> num(args, 1) {
            val code = it[0].toInt()
            if (code !in 0..255) Err.illegalQty()
            Value.Str(code.toChar().toString())
        }
        "ASC" -> {
            require1(args); val s = strArg(args[0])
            if (s.isEmpty()) Err.illegalQty()
            Value.Num(s[0].code.toDouble())
        }
        "LEFT$" -> {
            if (args.size != 2) Err.syntax()
            val s = strArg(args[0]); val n = intArg(args[1])
            if (n < 0) Err.illegalQty()
            Value.Str(s.take(n))
        }
        "RIGHT$" -> {
            if (args.size != 2) Err.syntax()
            val s = strArg(args[0]); val n = intArg(args[1])
            if (n < 0) Err.illegalQty()
            Value.Str(s.takeLast(n.coerceAtMost(s.length)))
        }
        "MID$" -> {
            if (args.size !in 2..3) Err.syntax()
            val s = strArg(args[0]); val start = intArg(args[1])
            if (start < 1) Err.illegalQty()
            val from = (start - 1).coerceAtMost(s.length)
            val len = if (args.size == 3) intArg(args[2]).also { if (it < 0) Err.illegalQty() }
                      else s.length - from
            val end = (from + len).coerceAtMost(s.length)
            Value.Str(s.substring(from, end))
        }
        "STR$" -> {
            require1(args); val n = numArg(args[0])
            Value.Str(basicFormatNumber(n))
        }
        "VAL" -> {
            require1(args); val s = strArg(args[0]).trim()
            if (s.isEmpty()) Value.Num(0.0)
            else {
                var i = 0
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i].isDigit()) i++
                if (i < s.length && s[i] == '.') {
                    i++
                    while (i < s.length && s[i].isDigit()) i++
                }
                if (i < s.length && (s[i] == 'E' || s[i] == 'e')) {
                    i++
                    if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                    while (i < s.length && s[i].isDigit()) i++
                }
                Value.Num(s.substring(0, i).toDoubleOrNull() ?: 0.0)
            }
        }
        else -> Err.syntax()
    }

    private fun require1(args: List<Value>) { if (args.size != 1) Err.syntax() }
    private fun strArg(v: Value): String = (v as? Value.Str)?.v ?: Err.typeMismatch()
    private fun numArg(v: Value): Double = (v as? Value.Num)?.v ?: Err.typeMismatch()
    private fun intArg(v: Value): Int = numArg(v).toInt()

    private fun num(args: List<Value>, n: Int, f: (DoubleArray) -> Value): Value {
        if (args.size != n) Err.syntax()
        val ds = DoubleArray(n) { i -> numArg(args[i]) }
        return f(ds)
    }
}
