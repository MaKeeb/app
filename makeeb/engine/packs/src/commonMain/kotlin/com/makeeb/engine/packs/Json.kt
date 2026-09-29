package com.makeeb.engine.packs

/**
 * Just enough JSON for the pack catalogue: objects become maps, arrays lists, numbers `Double`s
 * (sizes stay exact below 2^53), plus strings, booleans and null. The catalogue is a few KB that
 * the companion app reads once, so a small reader beats a serialization library and its compiler
 * plugin in every module.
 */
internal object Json {
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.skipSpace()
        if (!reader.atEnd) reader.fail("trailing characters")
        return value
    }

    /** Writes [value] (maps with string keys, lists, strings, numbers, booleans, null) with two-space indents. */
    fun write(value: Any?): String = StringBuilder().also { write(value, it, 0) }.append('\n').toString()

    private fun write(value: Any?, out: StringBuilder, indent: Int) {
        when (value) {
            null -> out.append("null")
            is String -> quote(value, out)
            is Boolean, is Int, is Long -> out.append(value.toString())
            is Map<*, *> -> block(value.entries.toList(), '{', '}', out, indent) { (key, item) ->
                quote(key as String, out)
                out.append(": ")
                write(item, out, indent + 1)
            }
            is List<*> -> block(value, '[', ']', out, indent) { write(it, out, indent + 1) }
            else -> throw IllegalArgumentException("can't write ${value::class.simpleName}")
        }
    }

    private inline fun <T> block(items: List<T>, open: Char, close: Char, out: StringBuilder, indent: Int, item: (T) -> Unit) {
        out.append(open)
        if (items.isEmpty()) {
            out.append(close)
            return
        }
        items.forEachIndexed { i, it ->
            out.append(if (i == 0) "\n" else ",\n")
            repeat(indent + 1) { out.append("  ") }
            item(it)
        }
        out.append('\n')
        repeat(indent) { out.append("  ") }
        out.append(close)
    }

    private fun quote(text: String, out: StringBuilder) {
        out.append('"')
        for (char in text) {
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' -> out.append("\\\\")
                char == '\n' -> out.append("\\n")
                char == '\r' -> out.append("\\r")
                char == '\t' -> out.append("\\t")
                char < ' ' -> out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                else -> out.append(char)
            }
        }
        out.append('"')
    }

    private class Reader(private val text: String) {
        private var at = 0
        val atEnd: Boolean get() = at >= text.length

        fun fail(message: String): Nothing = throw CatalogueFormatException("$message at $at")

        fun skipSpace() {
            while (at < text.length && text[at] in " \t\r\n") at++
        }

        fun value(): Any? {
            skipSpace()
            if (atEnd) fail("unexpected end")
            return when (text[at]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }

        private fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            at++
            skipSpace()
            if (peek('}')) return out
            while (true) {
                skipSpace()
                if (!peek('"', consume = false)) fail("expected a key")
                val key = string()
                skipSpace()
                if (!peek(':')) fail("expected ':'")
                out[key] = value()
                skipSpace()
                if (peek('}')) return out
                if (!peek(',')) fail("expected ',' or '}'")
            }
        }

        private fun array(): List<Any?> {
            val out = ArrayList<Any?>()
            at++
            skipSpace()
            if (peek(']')) return out
            while (true) {
                out += value()
                skipSpace()
                if (peek(']')) return out
                if (!peek(',')) fail("expected ',' or ']'")
            }
        }

        private fun string(): String {
            val out = StringBuilder()
            at++
            while (true) {
                if (atEnd) fail("unterminated string")
                val char = text[at++]
                when (char) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd) fail("unterminated escape")
                        when (val escaped = text[at++]) {
                            '"', '\\', '/' -> out.append(escaped)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (at + 4 > text.length) fail("short \\u escape")
                                out.append(text.substring(at, at + 4).toIntOrNull(16)?.toChar() ?: fail("bad \\u escape"))
                                at += 4
                            }
                            else -> fail("bad escape")
                        }
                    }
                    else -> out.append(char)
                }
            }
        }

        private fun number(): Double {
            val start = at
            while (at < text.length && (text[at].isDigit() || text[at] in "+-.eE")) at++
            return text.substring(start, at).toDoubleOrNull() ?: fail("expected a value")
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!text.startsWith(word, at)) fail("expected $word")
            at += word.length
            return value
        }

        private fun peek(char: Char, consume: Boolean = true): Boolean {
            if (at < text.length && text[at] == char) {
                if (consume) at++
                return true
            }
            return false
        }
    }
}
