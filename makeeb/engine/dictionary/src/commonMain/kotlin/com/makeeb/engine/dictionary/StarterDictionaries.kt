package com.makeeb.engine.dictionary

/**
 * A tiny built-in English list so the scaffold has something to suggest. Real dictionaries are
 * loaded from language packs (board: APP-37); this list is a placeholder.
 */
object StarterDictionaries {
    fun english(): Dictionary = TrieDictionary("en", parse(ENGLISH))

    private fun parse(source: String): List<WordEntry> =
        source.trim().lineSequence().flatMap { line ->
            val (frequency, words) = line.split(':', limit = 2)
            words.trim().split(' ').map { WordEntry(it, frequency.trim().toInt()) }
        }.toList()

    private const val ENGLISH = """
        255: the be to of and a in that have I it for not on with he as you do at
        240: this but his by from they we say her she or an will my one all would there their what
        225: so up out if about who get which go me when make can like time no just him know take
        210: people into year your good some could them see other than then now look only come its over think also
        195: back after use two how our work first well way even new want because any these give day most us
        180: is are was were been has had did does said made went got thing things really very much many more
        165: hello thanks thank please sorry yes ok okay great today tomorrow tonight morning night week weekend
        150: love home call meet later soon maybe sure right left keyboard typing message phone email
        135: where why here should must might need feel try ask let keep start seem help talk turn show hear
        120: world life hand part place case point government company number group problem fact
        105: London Monday Tuesday Wednesday Thursday Friday Saturday Sunday January February March April
        90: definitely probably actually basically literally especially recommend receive separate
    """
}
