package com.makeeb.engine.layout.data

/** A parsed JSON value. Objects keep their fields in file order. */
internal sealed interface JsonValue

internal class JsonObject(val fields: Map<String, JsonValue>) : JsonValue

internal class JsonArray(val items: List<JsonValue>) : JsonValue

internal class JsonString(val value: String) : JsonValue

/** [isInteger]: written without a fraction or exponent. */
internal class JsonNumber(val value: Double, val isInteger: Boolean) : JsonValue

internal class JsonBoolean(val value: Boolean) : JsonValue

internal data object JsonNull : JsonValue

/** Malformed JSON, or a value the schema doesn't allow; [path] is where (`$.keys["'"].width`). */
internal class JsonDataException(val path: String, message: String) : IllegalArgumentException(message)

/**
 * A strict JSON reader (RFC 8259) for the layout data. The files are small and their schemas flat,
 * so a tree is enough; kotlinx.serialization cost the iOS keyboard framework about 1 MB for this.
 *
 * Strict means: no comments, trailing commas, single quotes or unquoted keys; only the standard
 * escapes; no raw control characters in strings; no duplicate object keys; nothing after the
 * value; at most [MAX_DEPTH] levels of nesting, so a hostile import can't exhaust the stack.
 */
internal class JsonReader private constructor(private val text: String) {
    private var index = 0

    companion object {
        const val MAX_DEPTH = 8

        /** Digits kept in the mantissa; more would overflow a Long and add nothing a Float keeps. */
        private const val MAX_MANTISSA_DIGITS = 18

        /** Beyond this a Double is infinity or zero anyway. */
        private const val MAX_EXPONENT = 400

        fun parse(text: String): JsonValue = JsonReader(text).run {
            val value = value("$", depth = 0)
            skipWhitespace()
            if (index < text.length) fail("$", "unexpected '${text[index]}' after the value")
            value
        }

        /** The path of [key] under [parent]: `$.rows`, or `$.keys["'"]` for keys that aren't names. */
        fun childPath(parent: String, key: String): String =
            if (key.isNotEmpty() && key.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }) "$parent.$key"
            else "$parent[\"$key\"]"
    }

    private fun value(path: String, depth: Int): JsonValue {
        skipWhitespace()
        if (index >= text.length) fail(path, "unexpected end of input")
        return when (val char = text[index]) {
            '{' -> jsonObject(path, depth + 1)
            '[' -> jsonArray(path, depth + 1)
            '"' -> JsonString(string(path))
            't' -> literal(path, "true", JsonBoolean(true))
            'f' -> literal(path, "false", JsonBoolean(false))
            'n' -> literal(path, "null", JsonNull)
            else -> if (char == '-' || char in '0'..'9') number(path) else fail(path, "unexpected '$char'")
        }
    }

    private fun jsonObject(path: String, depth: Int): JsonObject {
        if (depth > MAX_DEPTH) fail(path, "nested deeper than $MAX_DEPTH levels")
        index++ // {
        val fields = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (peek() == '}') {
            index++
            return JsonObject(fields)
        }
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail(path, if (peek() == '}') "trailing comma" else "expected a field name in double quotes")
            val key = string(path)
            val fieldPath = childPath(path, key)
            if (key in fields) fail(fieldPath, "duplicate key '$key'")
            skipWhitespace()
            if (peek() != ':') fail(fieldPath, "expected ':' after the field name")
            index++
            fields[key] = value(fieldPath, depth)
            skipWhitespace()
            when (peek()) {
                ',' -> index++
                '}' -> {
                    index++
                    return JsonObject(fields)
                }
                else -> fail(path, "expected ',' or '}'")
            }
        }
    }

    private fun jsonArray(path: String, depth: Int): JsonArray {
        if (depth > MAX_DEPTH) fail(path, "nested deeper than $MAX_DEPTH levels")
        index++ // [
        val items = ArrayList<JsonValue>()
        skipWhitespace()
        if (peek() == ']') {
            index++
            return JsonArray(items)
        }
        while (true) {
            skipWhitespace()
            if (peek() == ']') fail(path, "trailing comma")
            items += value("$path[${items.size}]", depth)
            skipWhitespace()
            when (peek()) {
                ',' -> index++
                ']' -> {
                    index++
                    return JsonArray(items)
                }
                else -> fail(path, "expected ',' or ']'")
            }
        }
    }

    private fun string(path: String): String {
        index++ // "
        val out = StringBuilder()
        while (true) {
            if (index >= text.length) fail(path, "unterminated string")
            when (val char = text[index++]) {
                '"' -> return out.toString()
                '\\' -> {
                    if (index >= text.length) fail(path, "unterminated string")
                    when (val escape = text[index++]) {
                        '"', '\\', '/' -> out.append(escape)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            val hex = text.substring(index, minOf(index + 4, text.length))
                            val code = if (hex.length == 4 && hex.all { it.isHexDigit() }) hex.toInt(16) else -1
                            if (code < 0) fail(path, "bad escape '\\u$hex': needs four hex digits")
                            out.append(code.toChar())
                            index += 4
                        }
                        else -> fail(path, "bad escape '\\$escape'")
                    }
                }
                else -> {
                    if (char < ' ') fail(path, "control character in a string; escape it")
                    out.append(char)
                }
            }
        }
    }

    /**
     * `-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?`, computed here rather than with
     * `String.toDouble()`: on Kotlin/Native that links a hex-float parser built on Regex, about
     * 200 KB of the keyboard extension. The layout numbers (schema, key widths) are short decimals.
     */
    private fun number(path: String): JsonNumber {
        val negative = peek() == '-'
        if (negative) index++
        var mantissa = 0L
        var digits = 0
        var scale = 0
        fun digit(countsAfterPoint: Boolean) {
            if (digits < MAX_MANTISSA_DIGITS) {
                mantissa = mantissa * 10 + (text[index] - '0')
                if (mantissa != 0L) digits++
                if (countsAfterPoint) scale--
            } else if (!countsAfterPoint) {
                scale++
            }
            index++
        }
        when {
            peek() == '0' -> index++
            peek() in '1'..'9' -> while (peek() in '0'..'9') digit(countsAfterPoint = false)
            else -> fail(path, "bad number")
        }
        var isInteger = true
        if (peek() == '.') {
            isInteger = false
            index++
            if (peek() !in '0'..'9') fail(path, "bad number: digits must follow '.'")
            while (peek() in '0'..'9') digit(countsAfterPoint = true)
        }
        if (peek() == 'e' || peek() == 'E') {
            isInteger = false
            index++
            val negativeExponent = peek() == '-'
            if (peek() == '+' || peek() == '-') index++
            if (peek() !in '0'..'9') fail(path, "bad number: digits must follow the exponent")
            var exponent = 0
            while (peek() in '0'..'9') {
                if (exponent < MAX_EXPONENT) exponent = exponent * 10 + (text[index] - '0')
                index++
            }
            scale += if (negativeExponent) -exponent else exponent
        }
        if (peek() in '0'..'9') fail(path, "bad number: no leading zeros")
        val magnitude = when {
            mantissa == 0L -> 0.0
            scale >= 0 -> mantissa.toDouble() * powerOfTen(scale)
            else -> mantissa.toDouble() / powerOfTen(-scale)
        }
        return JsonNumber(if (negative) -magnitude else magnitude, isInteger)
    }

    /** Exact up to 10^22, which covers every value the schema allows. */
    private fun powerOfTen(exponent: Int): Double {
        var result = 1.0
        repeat(exponent.coerceAtMost(MAX_EXPONENT)) { result *= 10.0 }
        return result
    }

    private fun literal(path: String, word: String, value: JsonValue): JsonValue {
        if (!text.startsWith(word, index)) fail(path, "unexpected '${text[index]}'")
        index += word.length
        return value
    }

    private fun skipWhitespace() {
        while (index < text.length && (text[index] == ' ' || text[index] == '\n' || text[index] == '\r' || text[index] == '\t')) index++
    }

    /** The next character, or a NUL past the end (which no grammar rule accepts). */
    private fun peek(): Char = if (index < text.length) text[index] else '\u0000'

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun fail(path: String, message: String): Nothing {
        val before = text.substring(0, index.coerceAtMost(text.length))
        val line = before.count { it == '\n' } + 1
        val column = before.length - (before.lastIndexOf('\n') + 1) + 1
        throw JsonDataException(path, "line $line, column $column: $message")
    }
}

/**
 * The fields of one JSON object, read against a schema: unknown fields, missing required fields and
 * values of the wrong type are errors, reported with their path.
 */
internal class JsonFields private constructor(private val fields: Map<String, JsonValue>, private val path: String) {
    companion object {
        fun of(value: JsonValue, path: String, allowed: Set<String>): JsonFields {
            if (value !is JsonObject) throw JsonDataException(path, "expected an object")
            value.fields.keys.firstOrNull { it !in allowed }?.let { unknown ->
                throw JsonDataException(JsonReader.childPath(path, unknown), "unknown field '$unknown'")
            }
            return JsonFields(value.fields, path)
        }
    }

    fun string(name: String): String = stringOrNull(name) ?: missing(name)

    fun stringOrNull(name: String): String? = fields[name]?.let { asString(it, at(name)) }

    fun int(name: String): Int {
        val value = fields[name] ?: missing(name)
        if (value !is JsonNumber || !value.isInteger || value.value !in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            throw JsonDataException(at(name), "expected a whole number")
        }
        return value.value.toInt()
    }

    fun floatOrNull(name: String): Float? = fields[name]?.let { value ->
        if (value !is JsonNumber) throw JsonDataException(at(name), "expected a number")
        value.value.toFloat()
    }

    fun strings(name: String): List<String> {
        val value = fields[name] ?: missing(name)
        if (value !is JsonArray) throw JsonDataException(at(name), "expected an array of strings")
        return value.items.mapIndexed { index, item -> asString(item, "${at(name)}[$index]") }
    }

    /** An optional object of strings, such as `alternates`; absent is empty. */
    fun stringMap(name: String): Map<String, String> =
        objectFields(name).mapValues { (key, value) -> asString(value, JsonReader.childPath(at(name), key)) }

    /** An optional object of objects, such as `keys`, each read against [allowed]; absent is empty. */
    fun objectMap(name: String, allowed: Set<String>): Map<String, JsonFields> =
        objectFields(name).mapValues { (key, value) -> of(value, JsonReader.childPath(at(name), key), allowed) }

    private fun objectFields(name: String): Map<String, JsonValue> {
        val value = fields[name] ?: return emptyMap()
        if (value !is JsonObject) throw JsonDataException(at(name), "expected an object")
        return value.fields
    }

    private fun asString(value: JsonValue, path: String): String =
        (value as? JsonString)?.value ?: throw JsonDataException(path, "expected a string")

    private fun at(name: String) = JsonReader.childPath(path, name)

    private fun missing(name: String): Nothing = throw JsonDataException(path, "missing field '$name'")
}
