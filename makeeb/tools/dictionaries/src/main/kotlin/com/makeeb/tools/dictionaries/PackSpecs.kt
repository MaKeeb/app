package com.makeeb.tools.dictionaries

import com.makeeb.engine.packs.resolveUrl

/**
 * Every pack the builder makes: English ships inside the apps ([bundled]); the other languages
 * are downloaded by the companion app ([downloadable], published with `packRelease`). Each source
 * is pinned by SHA-256, whichever mirror serves it.
 */
object PackSpecs {
    /** AOSP LatinIME, the commit that last changed the word lists (2014-10-31, "possibly_offensive flag"). */
    const val AOSP_COMMIT = "8dd31a28ae774c0f5cd43404ad4b78bf46e5aeb6"

    private const val LEIPZIG_CITATION = "Goldhahn, Eckart & Quasthoff (2012): Building Large Monolingual Dictionaries at the " +
        "Leipzig Corpora Collection: From 100 to 200 Languages. LREC 2012."

    private fun leipzig(name: String, sha256: String) = LeipzigCorpus(
        name,
        PinnedSource("leipzig-$name.tar.gz", sha256, listOf(PinnedSource.Mirror("https://downloads.wortschatz-leipzig.de/corpora/$name.tar.gz"))),
    )

    private fun aospList(locale: String, sha256: String): PinnedSource {
        val file = "dictionaries/${locale}_wordlist.combined.gz"
        return PinnedSource(
            fileName = "aosp-$AOSP_COMMIT-${locale}_wordlist.combined.gz",
            sha256 = sha256,
            mirrors = listOf(
                PinnedSource.Mirror(
                    "https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/$AOSP_COMMIT/$file?format=TEXT",
                    base64 = true,
                ),
                // LineageOS mirrors AOSP with the same commits; the hash pins the bytes either way.
                PinnedSource.Mirror("https://raw.githubusercontent.com/LineageOS/android_packages_inputmethods_LatinIME/$AOSP_COMMIT/$file"),
            ),
        )
    }

    /** A pack built on an AOSP word list, with next-word statistics from [corpora]. */
    private fun aosp(
        locale: String,
        displayName: String,
        sha256: String,
        corpora: List<LeipzigCorpus>,
        checks: PackChecks,
    ) = PackSpec(
        fileName = "$locale.mkd",
        displayName = displayName,
        lexicon = AospLexicon(aospList(locale, sha256), "AOSP LatinIME dictionaries/${locale}_wordlist.combined.gz at $AOSP_COMMIT"),
        licence = "Apache-2.0",
        attribution = "Word list from the Android Open Source Project (LatinIME), Copyright (C) The Android Open Source Project, " +
            "licensed under the Apache License 2.0. Converted to MKD by MaKeeb.",
        ngramCorpora = corpora,
        ngramLicence = "CC-BY-4.0",
        ngramAttribution = "Next-word statistics counted from the Leipzig Corpora Collection (${corpora.joinToString { it.name }}), " +
            "Wortschatz Leipzig, Leipzig University, licensed under CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/). " +
            "Counted over the word list, pruned and quantised by MaKeeb. $LEIPZIG_CITATION",
        checks = checks,
    )

    // English news (2024) and .com web pages (2018), 1M sentences each: about 42M words.
    val englishUs = aosp(
        "en_US", "English (US)", "0f78dd455b532be169a23f233227b811fabced4b5bd7fc9c40cc05839793bcbd",
        listOf(
            leipzig("eng_news_2024_1M", "8f1d4d07b9771f8a7fc219ad587d5382eabf5009ef563f1bc4c12a467a8e3a97"),
            leipzig("eng-com_web-public_2018_1M", "de8849fe30c7d5bf3502f620093232f4dceca10a6ac19a9ad49f474b62df0a5f"),
        ),
        PackChecks(
            words = listOf("the", "cat", "kitchen", "London", "don't"),
            completion = "kitch" to "kitchen",
            // AOSP's not-a-word "im" is dropped; its folded key finds the real word. Keys fold apostrophes.
            folds = listOf("im" to "I'm", "dont" to "don't"),
            predictions = listOf(listOf("thank") to "you", listOf("one", "of") to "the"),
        ),
    )

    // The other languages: one Leipzig news corpus each (1M sentences, 2024 where there is one).
    val german = aosp(
        "de", "Deutsch", "38996899f92e386541677a5b9b3f8677f17d3c05116475ac71e22bcf5037022f",
        listOf(leipzig("deu_news_2024_1M", "203d98de187f6d87e7f97c1f516a64a9d19016d0dc81c935a72e769ef184fa64")),
        PackChecks(listOf("der", "und", "Straße", "Mädchen", "Berlin"), "Mädch" to "Mädchen", listOf("madchen" to "Mädchen", "fur" to "für", "uber" to "über")),
    )
    val spanish = aosp(
        "es", "Español", "889ad52bce2933e2a30a0560b8d5a76f5334500e776a7db7c7cb3e9e79fc2652",
        listOf(leipzig("spa_news_2024_1M", "4666b54caad54cd46cdfea80e7cb727663b220c2ead871b4ccf6285df0599090")),
        PackChecks(listOf("de", "que", "año", "también", "Madrid"), "tambi" to "también", listOf("tambien" to "también", "espanol" to "español", "manana" to "mañana")),
    )
    val french = aosp(
        "fr", "Français", "cc917a0a81acab0ea12089c2fb06b5c6be98c75f23a7605bfdbbeb698abfd65e",
        listOf(leipzig("fra_news_2024_1M", "907eed297ab7b5fbe0ea87105084899158f660890e87f121b2a24eb9853a6467")),
        PackChecks(listOf("de", "et", "être", "français", "Paris"), "franç" to "français", listOf("etre" to "être", "francais" to "français", "tres" to "très")),
    )
    val italian = aosp(
        "it", "Italiano", "4ac1fa3b112130416843f5abc2a61fdbd2395a41fb6a719790cd7c45934218ec",
        listOf(leipzig("ita_news_2024_1M", "90ed9f839c50de9a58a7f1772516891d6721c92f4b863560bd26a480d7717e30")),
        PackChecks(listOf("di", "che", "perché", "città", "Roma"), "perch" to "perché", listOf("citta" to "città", "piu" to "più")),
    )
    val dutch = aosp(
        "nl", "Nederlands", "a647ed5fdd846d3240572c9cb10fb8c30880df2ebefea6184a378adb713cd0cd",
        listOf(leipzig("nld_news_2024_1M", "1badc58eb5227580c03a75f5253ded10b78f1a0f67ba8f87d679891e88fbe394")),
        PackChecks(listOf("de", "het", "een", "Amsterdam"), "Amsterd" to "Amsterdam", listOf("ideeen" to "ideeën", "cafe" to "café")),
    )
    val polish = aosp(
        "pl", "Polski", "75a7a488e014ec3b9dbdb2527f09bca6bb28c250232d9ba50cb0ee1f8738ea45",
        listOf(leipzig("pol_news_2024_1M", "60f79d00b23afbeb48e9350937cb9c748b73d9a9ff6e7a06ea5197adca58762d")),
        PackChecks(listOf("w", "się", "że", "Warszawa"), "Warsz" to "Warszawie", listOf("sie" to "się", "jezyk" to "język")),
    )

    // Brazilian Portuguese is the keyboard's "pt": far more people type it than European
    // Portuguese, whose AOSP list could become a second pack for the same language.
    val portuguese = aosp(
        "pt_BR", "Português (Brasil)", "f9b7c2f610eacebc782c7d24e64d8ad2620b449defbac326507dfb5702c6c48b",
        listOf(leipzig("por_news_2024_1M", "cc8560f85eeea797ed9ddd5e8d3b8a3db5daea694f4b8272e20be8622c6ed865")),
        PackChecks(listOf("de", "que", "não", "você", "Brasil"), "voc" to "você", listOf("nao" to "não", "voce" to "você", "tambem" to "também")),
    )
    val swedish = aosp(
        "sv", "Svenska", "a8c8aa7d1c8dd65b331fcfb4baf739dddb87b146a5fe737ee0ac218686e379bb",
        listOf(leipzig("swe_news_2023_1M", "899447b8d42d2acca48d0c193a571678cac814e761329895d0e55ac37ad075c9")),
        PackChecks(listOf("och", "att", "för", "Stockholm"), "Stockh" to "Stockholm", listOf("hjalp" to "hjälp", "ocksa" to "också")),
    )

    /**
     * Hungarian has no AOSP list, so the words come from the corpus itself (the next-word
     * statistics too): letters of the Hungarian alphabet (and inner hyphens), seen at least
     * [CorpusWordRecipe.minCount] times, at most [CorpusWordRecipe.maxWords] words.
     */
    val hungarian = run {
        val corpus = leipzig("hun_news_2024_1M", "1bea06e08a763633fada636476ba7b81049c357b305ae6304722a5c713495b11")
        val attribution = "Words and next-word statistics counted from the Leipzig Corpora Collection (${corpus.name}), " +
            "Wortschatz Leipzig, Leipzig University, licensed under CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/). " +
            "Selected, cased, ranked, pruned and quantised by MaKeeb. $LEIPZIG_CITATION"
        PackSpec(
            fileName = "hu.mkd",
            displayName = "Magyar",
            lexicon = CorpusLexicon(
                corpus,
                CorpusWordRecipe(
                    languageTag = "hu",
                    alphabet = "aábcdeéfghiíjklmnoóöőpqrstuúüűvwxyz",
                    offensive = HungarianOffensiveWords,
                ),
            ),
            licence = "CC-BY-4.0",
            attribution = attribution,
            ngramCorpora = listOf(corpus),
            ngramLicence = "CC-BY-4.0",
            ngramAttribution = attribution,
            checks = PackChecks(
                words = listOf("a", "és", "hogy", "magyar", "Budapest", "szeretnék"),
                completion = "szeret" to "szeretnék",
                folds = listOf("hogyan" to "hogyan", "kerdes" to "kérdés"),
            ),
            // Agglutinative: many more word forms per lemma (docs/research/dictionaries-autocorrect.md §10.5).
            maxBytes = 16_000_000,
        )
    }

    /**
     * The published en_US.mkd (`packRelease` puts it beside the other packs): what
     * `dictionaryPacks` downloads instead of counting 517 MB of corpora, once the catalogue URL is
     * set. It is the bytes this builder makes (the writer is reproducible), pinned like any
     * source: after changing how English is built, publish the new folder and pin its hash here.
     */
    const val ENGLISH_US_RELEASE_SHA256 = "9f80ed21568253055533e625dcae82e4c077b72baadf521d21653b4b3545c884"

    /** [ENGLISH_US_RELEASE_SHA256], next to the catalogue at [catalogueUrl]. */
    fun englishUsRelease(catalogueUrl: String) = PinnedSource(
        fileName = "release-en_US-${ENGLISH_US_RELEASE_SHA256.take(16)}.mkd",
        sha256 = ENGLISH_US_RELEASE_SHA256,
        mirrors = listOf(PinnedSource.Mirror(resolveUrl(catalogueUrl, englishUs.fileName))),
    )

    val bundled = listOf(englishUs)
    val downloadable = listOf(german, spanish, french, italian, dutch, polish, portuguese, swedish, hungarian)
    val all = bundled + downloadable
}

/**
 * Words each pack must answer as expected, or the build fails: [words] look up as themselves,
 * [completion] completes, each of [folds] (typed → found) finds the accented or apostrophised
 * spelling, and each of [predictions] (context → word) is among the top three next words.
 */
class PackChecks(
    val words: List<String>,
    val completion: Pair<String, String>,
    val folds: List<Pair<String, String>> = emptyList(),
    val predictions: List<Pair<List<String>, String>> = emptyList(),
)

class PackSpec(
    val fileName: String,
    val displayName: String,
    val lexicon: LexiconSource,
    val licence: String,
    val attribution: String,
    val ngramCorpora: List<LeipzigCorpus> = emptyList(),
    val ngramLicence: String = "",
    val ngramAttribution: String = "",
    val pruning: NgramPruning = NgramPruning(),
    val checks: PackChecks,
    /** The size budget (docs/research/dictionaries-autocorrect.md §10.5). */
    val maxBytes: Int = 10_000_000,
) {
    /** "hu.mkd" → "hu", "pt_BR.mkd" → "pt_BR": held-out text and cache files are named by it. */
    val stem: String get() = fileName.substringBeforeLast('.')
}

/** Where a pack's words come from. */
sealed interface LexiconSource {
    /** The pinned file the words are read from. */
    val source: PinnedSource

    /** For the pack's META `source`. */
    val description: String
}

/** An AOSP LatinIME `.combined` word list ([AospWordList]). */
class AospLexicon(override val source: PinnedSource, override val description: String) : LexiconSource

/** Words counted in a corpus ([CorpusWordList]), for languages AOSP has no list for. */
class CorpusLexicon(val corpus: LeipzigCorpus, val recipe: CorpusWordRecipe) : LexiconSource {
    override val source: PinnedSource get() = corpus.source
    override val description: String
        get() = "Leipzig Corpora Collection ${corpus.name}: words seen at least ${recipe.minCount} times, " +
            "at most ${recipe.maxWords} (1 in $HELD_OUT_EVERY sentences held out)"
}
