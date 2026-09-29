package com.makeeb.engine.dictionary

import com.makeeb.platform.storage.PackFile
import com.makeeb.platform.storage.PackFiles

/**
 * A downloaded dictionary pack as it lies in [PackFiles]: `<language>-<version>.mkd`, where the
 * version is the start of the pack's SHA-256 ("hu-2adf8e4a98690983.mkd"). The name alone says
 * which language a pack serves and which build it is, so the keyboard lists packs without opening
 * them, the companion app sees when the catalogue has a newer one, and a new version never reuses
 * an old one's name (what [PackFiles] promises a keyboard that has the old one mapped).
 */
data class InstalledPack(val language: String, val version: String, val size: Long) {
    val fileName: String get() = "$language-$version$EXTENSION"

    val baseLanguage: String get() = language.substringBefore('-')

    /** Whether this is the pack whose SHA-256 is [sha256]. */
    fun isVersion(sha256: String): Boolean = sha256.startsWith(version, ignoreCase = true)

    companion object {
        private const val EXTENSION = ".mkd"
        private const val VERSION_LENGTH = 16

        fun fileName(language: String, sha256: String): String = "$language-${sha256.take(VERSION_LENGTH).lowercase()}$EXTENSION"

        /** The pack [file] holds, or null for anything else in the directory. */
        fun parse(file: PackFile): InstalledPack? {
            if (!file.name.endsWith(EXTENSION)) return null
            val stem = file.name.removeSuffix(EXTENSION)
            val dash = stem.lastIndexOf('-')
            if (dash <= 0) return null
            val version = stem.substring(dash + 1)
            if (version.length != VERSION_LENGTH || !version.all { it in '0'..'9' || it in 'a'..'f' }) return null
            return InstalledPack(stem.substring(0, dash), version, file.size)
        }

        /**
         * The packs in [files], one per language. Between installing a new version and deleting
         * the old there can be two; either is a complete pack, and the choice is stable.
         */
        fun list(files: PackFiles): List<InstalledPack> =
            files.list().mapNotNull(::parse)
                .groupBy { it.language }
                .map { (_, versions) -> versions.maxBy { it.version } }
                .sortedBy { it.language }
    }
}

/** The pack serving [languageTag]: its own ("pt-BR"), else one for its language ("pt" → "pt-BR"). */
fun List<InstalledPack>.forLanguage(languageTag: String): InstalledPack? =
    firstOrNull { it.language.equals(languageTag, ignoreCase = true) }
        ?: firstOrNull { it.baseLanguage.equals(languageTag.substringBefore('-'), ignoreCase = true) }
