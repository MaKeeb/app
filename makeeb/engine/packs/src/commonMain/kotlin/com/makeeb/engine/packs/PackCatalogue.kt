package com.makeeb.engine.packs

/**
 * The downloadable dictionary packs: a small JSON file published beside them (GitHub release
 * assets), which the companion app fetches to offer them. `:tools:dictionaries` writes it with
 * [toJson] and the app reads it with [parse], so both sides share one definition. The format is
 * documented in docs/dictionaries/pack-catalogue.md.
 */
class PackCatalogue(val packs: List<PackEntry>) {
    /** The pack for [languageTag]: its own ("pt-BR"), else the first listed for its language ("pt" → "pt-BR"). */
    fun forLanguage(languageTag: String): PackEntry? =
        packs.firstOrNull { it.language.equals(languageTag, ignoreCase = true) }
            ?: packs.firstOrNull { it.baseLanguage.equals(languageTag.substringBefore('-'), ignoreCase = true) }

    fun toJson(): String = Json.write(
        linkedMapOf(
            "format" to FORMAT,
            "packs" to packs.map { pack ->
                linkedMapOf(
                    "language" to pack.language,
                    "name" to pack.name,
                    "file" to pack.file,
                    "url" to pack.url,
                    "size" to pack.size,
                    "sha256" to pack.sha256,
                    "mkdVersion" to pack.mkdVersion,
                    "words" to pack.words,
                    "nextWords" to pack.nextWords,
                    "licence" to pack.licence,
                    "attribution" to pack.attribution,
                )
            },
        ),
    )

    companion object {
        /** Bumped only for changes old apps would misread; they then offer no downloads. */
        const val FORMAT = 1

        /** MKD major version this app reads; packs of another major version are left out. */
        const val MKD_MAJOR = 1

        /** No pack comes close; a larger size means a bad entry, not a big pack. */
        const val MAX_PACK_BYTES = 64L * 1024 * 1024

        /**
         * Reads a catalogue. Unknown fields are ignored and packs this app can't read (another MKD
         * major version) are left out, so a newer catalogue still works in an older app.
         */
        @Throws(CatalogueFormatException::class)
        fun parse(json: String): PackCatalogue {
            val root = Json.parse(json) as? Map<*, *> ?: throw CatalogueFormatException("not an object")
            val format = (root["format"] as? Double)?.toInt() ?: throw CatalogueFormatException("no format")
            if (format != FORMAT) throw CatalogueFormatException("format $format; this app reads $FORMAT")
            val packs = root["packs"] as? List<*> ?: throw CatalogueFormatException("no packs")
            return PackCatalogue(
                packs.map { item ->
                    val entry = item as? Map<*, *> ?: throw CatalogueFormatException("a pack isn't an object")
                    fun text(key: String): String = entry[key] as? String ?: throw CatalogueFormatException("pack without $key")
                    fun number(key: String): Double = entry[key] as? Double ?: throw CatalogueFormatException("pack without $key")
                    PackEntry(
                        language = text("language"),
                        name = text("name"),
                        file = text("file"),
                        url = text("url"),
                        size = number("size").toLong(),
                        sha256 = text("sha256").lowercase(),
                        mkdVersion = text("mkdVersion"),
                        words = number("words").toInt(),
                        nextWords = entry["nextWords"] as? Boolean ?: false,
                        licence = text("licence"),
                        attribution = text("attribution"),
                    ).also(::validate)
                }.filter { it.mkdVersion.substringBefore('.').toIntOrNull() == MKD_MAJOR },
            )
        }

        /**
         * The language becomes part of a file name on the device, and the size and hash decide
         * what is accepted: anything unexpected there rejects the whole catalogue.
         */
        private fun validate(pack: PackEntry) {
            if (!LANGUAGE_TAG.matches(pack.language)) throw CatalogueFormatException("bad language tag")
            if (!SHA256.matches(pack.sha256)) throw CatalogueFormatException("bad sha256 for ${pack.language}")
            if (pack.size !in 1..MAX_PACK_BYTES) throw CatalogueFormatException("bad size for ${pack.language}")
            if (pack.url.isBlank()) throw CatalogueFormatException("no url for ${pack.language}")
        }

        private val LANGUAGE_TAG = Regex("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*")
        private val SHA256 = Regex("[0-9a-f]{64}")
    }
}

/** One downloadable pack. */
data class PackEntry(
    /** BCP 47, as in the pack's own META: "de", "pt-BR", "hu". */
    val language: String,
    /** The language's own name for itself, for the app to show: "Deutsch", "Português (Brasil)". */
    val name: String,
    /** The release asset's name. */
    val file: String,
    /** Where to download it: absolute, or relative to the catalogue's own URL. */
    val url: String,
    /** Bytes. */
    val size: Long,
    /** Lower-case hex; a download that doesn't hash to it is thrown away. */
    val sha256: String,
    /** The MKD format version, "1.1" (docs/dictionaries/mkd-format.md). */
    val mkdVersion: String,
    val words: Int,
    /** Whether it has next-word statistics (an NGRM section). */
    val nextWords: Boolean,
    /** SPDX expression for the whole pack. */
    val licence: String,
    val attribution: String,
) {
    val baseLanguage: String get() = language.substringBefore('-')

    /** [url] resolved against [catalogueUrl] (RFC 3986 for the forms a catalogue uses). */
    fun downloadUrl(catalogueUrl: String): String = resolveUrl(catalogueUrl, url)
}

/** The catalogue isn't one this app can read. */
class CatalogueFormatException(message: String) : Exception(message)

/** Resolves [reference] against [base]: absolute URLs as they are, "/path" against the host, anything else against the base's directory. */
fun resolveUrl(base: String, reference: String): String {
    if ("://" in reference) return reference
    val schemeEnd = base.indexOf("://")
    if (reference.startsWith("/")) {
        val hostEnd = base.indexOf('/', if (schemeEnd < 0) 0 else schemeEnd + 3)
        return (if (hostEnd < 0) base else base.substring(0, hostEnd)) + reference
    }
    val path = base.substringBefore('?').substringBefore('#')
    val directoryEnd = path.lastIndexOf('/')
    return if (directoryEnd <= schemeEnd + 2) "$path/$reference" else path.substring(0, directoryEnd + 1) + reference
}
