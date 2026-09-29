package com.makeeb.engine.layout.data

/** One bundled data file: its layout id or language tag, and its JSON text. */
internal class BundledFile(val name: String, val json: String)

/**
 * The letter rows of one arrangement, independent of language. Characters only: the engine adds
 * shift, backspace, the bottom row and the digit hints (docs/layouts/schema.md).
 */
internal data class LetterLayoutSpec(
    val id: String,
    val name: String,
    /** Three rows of key texts, top to bottom, left to right. */
    val rows: List<List<String>>,
    /** Key widths in key units, where not 1. */
    val widths: Map<String, Float> = emptyMap(),
    /** Long-press keys the arrangement itself adds, before the language's (AZERTY's apostrophe). */
    val alternates: Map<String, List<String>> = emptyMap(),
)

/** What a language adds to any layout. Alternates follow the languages, not the layout. */
internal data class LanguageSpec(
    val tag: String,
    val name: String,
    val autonym: String,
    /** Bundled layouts the language uses, most usual first. */
    val layouts: List<String>,
    /** Long-press keys per base key, most likely first. */
    val alternates: Map<String, List<String>> = emptyMap(),
    /** Shifted forms where `uppercase()` is wrong for the language (`ß` → `ẞ`). Not applied yet. */
    val shifted: Map<String, String> = emptyMap(),
)

/**
 * The bundled layouts and languages, each parsed and validated the first time it is needed. A file
 * that fails validation counts as missing, so the keyboard never throws on bad data: an unknown
 * layout falls back to QWERTY, an unknown language to its base language, and a set of languages
 * with none known to English.
 */
internal class LayoutData(
    private val layoutFiles: List<BundledFile> = bundledLayoutFiles,
    private val languageFiles: List<BundledFile> = bundledLanguageFiles,
) {
    private val layoutIds = layoutFiles.map { it.name }.toSet()
    private val parsedLayouts = mutableMapOf<String, LetterLayoutSpec?>()
    private val parsedLanguages = mutableMapOf<String, LanguageSpec?>()
    private val mergedAccents = mutableMapOf<List<String>, Map<String, List<String>>>()

    /** Every valid layout, in bundle order. Parses them all: for settings, not the keyboard. */
    val layouts: List<LetterLayoutSpec> get() = layoutFiles.mapNotNull { layoutOrNull(it.name) }

    /** Every valid language, in bundle order. Parses them all: for settings, not the keyboard. */
    val languages: List<LanguageSpec> get() = languageFiles.mapNotNull { languageOrNull(it.name) }

    fun layout(id: String): LetterLayoutSpec = layoutOrNull(id) ?: layoutOrNull(DEFAULT_LAYOUT) ?: FALLBACK_LAYOUT

    /** The language for [tag], else its language subtag (`de-CH` → `de`), else English. */
    fun language(tag: String): LanguageSpec? =
        languageOrNull(tag) ?: languageOrNull(tag.substringBefore('-')) ?: languageOrNull(DEFAULT_LANGUAGE)

    /**
     * The languages for [tags] in their order, each resolved like [language] but skipped when
     * unknown (a phone language without data adds nothing), English when none is known.
     */
    fun languages(tags: List<String>): List<LanguageSpec> =
        tags.mapNotNull { languageOrNull(it) ?: languageOrNull(it.substringBefore('-')) }.distinctBy { it.tag }
            .ifEmpty { listOfNotNull(languageOrNull(DEFAULT_LANGUAGE)) }

    /**
     * Every base key's long-press accents across [tags]: the first language's, then each next
     * language's that aren't there yet. Cached, since the keyboard asks on every page build.
     */
    fun accents(tags: List<String>): Map<String, List<String>> = mergedAccents.getOrPut(tags) {
        val merged = LinkedHashMap<String, MutableList<String>>()
        languages(tags).forEach { language ->
            language.alternates.forEach { (key, alternates) ->
                val list = merged.getOrPut(key) { mutableListOf() }
                alternates.filterTo(list) { it !in list }
            }
        }
        merged
    }

    private fun layoutOrNull(id: String): LetterLayoutSpec? {
        if (id in parsedLayouts) return parsedLayouts[id]
        return layoutFiles.firstOrNull { it.name == id }?.let { LayoutDataParser.layout(it).value }
            .also { parsedLayouts[id] = it }
    }

    private fun languageOrNull(tag: String): LanguageSpec? {
        if (tag in parsedLanguages) return parsedLanguages[tag]
        return languageFiles.firstOrNull { it.name == tag }?.let { LayoutDataParser.language(it, layoutIds).value }
            .also { parsedLanguages[tag] = it }
    }

    companion object {
        const val DEFAULT_LAYOUT = "qwerty"
        const val DEFAULT_LANGUAGE = "en"

        /** Only if the bundled QWERTY file were invalid, which the tests rule out. */
        val FALLBACK_LAYOUT = LetterLayoutSpec(
            DEFAULT_LAYOUT,
            "QWERTY",
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").map { row -> row.map(Char::toString) },
        )
    }
}

/** A problem in a layout or language file: the file, the JSON path and what is wrong. */
internal data class LayoutDataProblem(val file: String, val path: String, val message: String) {
    override fun toString(): String = "$file: $path: $message"
}

/** A parsed file, or null and the problems that stopped it. */
internal class Parsed<T : Any>(val value: T?, val problems: List<LayoutDataProblem>)

/**
 * Reads layout and language files (docs/layouts/schema.md) and checks them. The same checks run
 * at load and in the tests over every bundled file; later, on files users import.
 */
internal object LayoutDataParser {
    const val SCHEMA = 1
    const val MAX_FILE_CHARS = 64 * 1024
    const val ROWS = 3
    const val MAX_KEYS_PER_ROW = 12
    const val MAX_ROW_UNITS = 12f

    /** With shift and backspace (3 units) beside it, letters stay 0.7 units or wider. */
    const val MAX_LAST_ROW_UNITS = 10f
    const val MAX_ALTERNATES = 16
    const val MAX_KEY_CODE_POINTS = 8
    const val MIN_KEY_WIDTH = 0.5f
    const val MAX_KEY_WIDTH = 2f
    const val MAX_NAME_LENGTH = 40

    /**
     * `[a-z0-9][a-z0-9_-]{0,31}`. Checked by hand, as is the language tag: Regex would link
     * Kotlin/Native's regex engine into the keyboard extension for two patterns.
     */
    private fun isLayoutId(id: String): Boolean =
        id.length in 1..32 && id.first().let { it in 'a'..'z' || it in '0'..'9' } &&
            id.all { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }

    /** Language, optional script, optional region: `de`, `sr-Latn`, `pt-BR`, `es-419`. */
    private fun isLanguageTag(tag: String): Boolean {
        val parts = tag.split('-')
        if (parts.first().length !in 2..3 || !parts.first().all { it in 'a'..'z' }) return false
        var next = 1
        val script = parts.getOrNull(next)
        if (script != null && script.length == 4 && script.first() in 'A'..'Z' && script.drop(1).all { it in 'a'..'z' }) next++
        val region = parts.getOrNull(next)
        if (region != null && (region.length == 2 && region.all { it in 'A'..'Z' } || region.length == 3 && region.all { it in '0'..'9' })) next++
        return next == parts.size
    }

    fun layout(file: BundledFile): Parsed<LetterLayoutSpec> {
        val problems = Problems("layouts/${file.name}.json")
        val parsed = problems.decode(file.json, ::layoutFile) ?: return problems.result(null)
        with(problems) {
            checkHeader(parsed.schema, parsed.sources)
            if (!isLayoutId(parsed.id)) add("$.id", "must be 1–32 of a-z, 0-9, _ and -, starting with a letter or digit")
            if (parsed.id != file.name) add("$.id", "'${parsed.id}' is not the file's name '${file.name}'")
            checkName("$.name", parsed.name)
            if (parsed.rows.size != ROWS) add("$.rows", "needs exactly $ROWS rows, has ${parsed.rows.size}")
            val rows = parsed.rows.mapIndexed { index, row -> keys("$.rows[$index]", row) }
            val seen = mutableSetOf<String>()
            rows.forEachIndexed { index, keys ->
                val path = "$.rows[$index]"
                if (keys.size !in 1..MAX_KEYS_PER_ROW) add(path, "needs 1–$MAX_KEYS_PER_ROW keys, has ${keys.size}")
                keys.forEach { key ->
                    checkKey(path, key)
                    if (!seen.add(key)) add(path, "'$key' is on the layout twice")
                }
                val units = keys.sumOf { (parsed.keys[it]?.width ?: 1f).toDouble() }.toFloat()
                val max = if (index == ROWS - 1) MAX_LAST_ROW_UNITS else MAX_ROW_UNITS
                if (units > max) add(path, "is $units units wide, at most $max")
            }
            parsed.keys.forEach { (key, options) ->
                val path = "$.keys[\"$key\"]"
                if (key !in seen) add(path, "is not a key in the rows")
                options.width?.let { if (it !in MIN_KEY_WIDTH..MAX_KEY_WIDTH) add("$path.width", "must be $MIN_KEY_WIDTH–$MAX_KEY_WIDTH") }
                options.alternates?.let { text ->
                    val alternates = alternates("$path.alternates", key, text)
                    // The selected languages decide which letters can be typed; a layout only places them.
                    alternates.filter { alternate -> alternate.any(Char::isLetter) }.forEach {
                        add("$path.alternates", "'$it' is a letter: accents come from the languages, not the layout")
                    }
                }
            }
            return result(
                LetterLayoutSpec(
                    id = parsed.id,
                    name = parsed.name,
                    rows = rows,
                    widths = parsed.keys.mapNotNull { (key, options) -> options.width?.let { key to it } }.toMap(),
                    alternates = parsed.keys.mapNotNull { (key, options) -> options.alternates?.let { key to it.split(' ') } }.toMap(),
                ),
            )
        }
    }

    /** [layoutIds]: the layouts a language may name. */
    fun language(file: BundledFile, layoutIds: Set<String>): Parsed<LanguageSpec> {
        val problems = Problems("languages/${file.name}.json")
        val parsed = problems.decode(file.json, ::languageFile) ?: return problems.result(null)
        with(problems) {
            checkHeader(parsed.schema, parsed.sources)
            if (!isLanguageTag(parsed.language)) add("$.language", "'${parsed.language}' is not a language tag like de, pt-BR or sr-Latn")
            if (parsed.language != file.name) add("$.language", "'${parsed.language}' is not the file's name '${file.name}'")
            checkName("$.name", parsed.name)
            checkName("$.autonym", parsed.autonym)
            if (parsed.layouts.isEmpty()) add("$.layouts", "needs at least one layout")
            parsed.layouts.forEachIndexed { index, id ->
                if (id !in layoutIds) add("$.layouts[$index]", "'$id' is not a bundled layout")
            }
            if (parsed.layouts.toSet().size != parsed.layouts.size) add("$.layouts", "names a layout twice")
            val alternateLists = parsed.alternates.mapValues { (key, keys) ->
                checkKey("$.alternates", key)
                alternates("$.alternates[\"$key\"]", key, keys)
            }
            val reachable = alternateLists.keys + alternateLists.values.flatten()
            parsed.shifted.forEach { (key, shifted) ->
                val path = "$.shifted[\"$key\"]"
                if (key !in reachable) add(path, "'$key' is neither a base key nor an alternate")
                checkKey(path, shifted)
            }
            return result(
                LanguageSpec(
                    tag = parsed.language,
                    name = parsed.name,
                    autonym = parsed.autonym,
                    layouts = parsed.layouts,
                    alternates = alternateLists,
                    shifted = parsed.shifted,
                ),
            )
        }
    }

    private class Problems(private val file: String) {
        private val found = mutableListOf<LayoutDataProblem>()

        fun add(path: String, message: String) {
            found += LayoutDataProblem(file, path, message)
        }

        fun <T : Any> result(value: T?): Parsed<T> = Parsed(if (found.isEmpty()) value else null, found.toList())

        /** [text] as strict JSON, read by [reader] against the schema; null after a problem. */
        fun <T : Any> decode(text: String, reader: (JsonValue) -> T): T? {
            if (text.length > MAX_FILE_CHARS) {
                add("$", "is ${text.length} characters, at most $MAX_FILE_CHARS")
                return null
            }
            return try {
                reader(JsonReader.parse(text))
            } catch (e: JsonDataException) {
                add(e.path, e.message ?: "is not valid JSON")
                null
            }
        }

        fun checkHeader(schema: Int, sources: List<String>) {
            if (schema > SCHEMA) add("$.schema", "schema $schema needs a newer MaKeeb")
            if (schema < SCHEMA) add("$.schema", "unknown schema $schema")
            if (sources.isEmpty() || sources.any { it.isBlank() }) add("$.sources", "must say where the data comes from")
        }

        fun checkName(path: String, name: String) {
            if (name.isBlank() || name.length > MAX_NAME_LENGTH) add(path, "must be 1–$MAX_NAME_LENGTH characters")
        }

        /** The space-separated keys of [text]; a stray space would hide an empty key. */
        fun keys(path: String, text: String): List<String> {
            val keys = text.split(' ')
            if (keys.any { it.isEmpty() }) add(path, "has a leading, trailing or double space")
            return keys.filter { it.isNotEmpty() }
        }

        fun checkKey(path: String, key: String) {
            val codePoints = codePointCount(key)
            when {
                codePoints == null -> add(path, "'$key' has a lone surrogate")
                codePoints !in 1..MAX_KEY_CODE_POINTS -> add(path, "'$key' must be 1–$MAX_KEY_CODE_POINTS characters")
                key.any { it.isWhitespace() || it.isISOControl() } -> add(path, "'$key' contains whitespace or a control character")
                key.startsWith('$') -> add(path, "'$key': keys starting with \$ are reserved")
            }
        }

        fun alternates(path: String, key: String, text: String): List<String> {
            val keys = keys(path, text)
            if (keys.size !in 1..MAX_ALTERNATES) add(path, "needs 1–$MAX_ALTERNATES alternates, has ${keys.size}")
            keys.forEach { checkKey(path, it) }
            if (keys.toSet().size != keys.size) add(path, "lists an alternate twice")
            if (key in keys) add(path, "lists the key itself")
            return keys
        }

        /** Code points in [text], or null if it has a lone surrogate. */
        private fun codePointCount(text: String): Int? {
            var count = 0
            var index = 0
            while (index < text.length) {
                val char = text[index]
                when {
                    char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate() -> index += 2
                    char.isSurrogate() -> return null
                    else -> index++
                }
                count++
            }
            return count
        }
    }
}

/** A layout file as written, before the checks. */
internal class LayoutFile(
    val schema: Int,
    val id: String,
    val name: String,
    val rows: List<String>,
    val keys: Map<String, KeyOptions>,
    val sources: List<String>,
)

internal class KeyOptions(val width: Float?, val alternates: String?)

/** A language file as written, before the checks. */
internal class LanguageFile(
    val schema: Int,
    val language: String,
    val name: String,
    val autonym: String,
    val layouts: List<String>,
    val alternates: Map<String, String>,
    val shifted: Map<String, String>,
    val sources: List<String>,
)

private val LAYOUT_FIELDS = setOf("schema", "id", "name", "rows", "keys", "sources")
private val KEY_FIELDS = setOf("width", "alternates")
private val LANGUAGE_FIELDS = setOf("schema", "language", "name", "autonym", "layouts", "alternates", "shifted", "sources")

private fun layoutFile(root: JsonValue): LayoutFile {
    val fields = JsonFields.of(root, "$", LAYOUT_FIELDS)
    return LayoutFile(
        schema = fields.int("schema"),
        id = fields.string("id"),
        name = fields.string("name"),
        rows = fields.strings("rows"),
        keys = fields.objectMap("keys", KEY_FIELDS).mapValues { (_, key) -> KeyOptions(key.floatOrNull("width"), key.stringOrNull("alternates")) },
        sources = fields.strings("sources"),
    )
}

private fun languageFile(root: JsonValue): LanguageFile {
    val fields = JsonFields.of(root, "$", LANGUAGE_FIELDS)
    return LanguageFile(
        schema = fields.int("schema"),
        language = fields.string("language"),
        name = fields.string("name"),
        autonym = fields.string("autonym"),
        layouts = fields.strings("layouts"),
        alternates = fields.stringMap("alternates"),
        shifted = fields.stringMap("shifted"),
        sources = fields.strings("sources"),
    )
}
