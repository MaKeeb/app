package com.makeeb.engine.prediction

/**
 * The only words autocorrect rewrites until MaKeeb has a real lexicon: common English typos and
 * missing apostrophes. With a 250-word starter list, "not in the list" can't mean "misspelt", and
 * correcting every near miss turned real words into listed ones ("cat" → "at"). Near misses are
 * still offered as suggestions. Stage 0 of docs/research/dictionaries-autocorrect.md §10.6.
 */
internal object KnownTypos {
    fun correctionFor(word: String): String? = TYPOS[word.lowercase()]

    // Not here on purpose: words that are also real words ("ill", "id", "whit", "lets", "were").
    private val TYPOS = mapOf(
        "im" to "I'm", "ive" to "I've",
        "teh" to "the", "hte" to "the", "tge" to "the", "adn" to "and", "nad" to "and", "taht" to "that",
        "thta" to "that", "waht" to "what", "wiht" to "with",
        "thier" to "their", "recieve" to "receive", "recieved" to "received", "becuase" to "because",
        "beacuse" to "because", "definately" to "definitely", "seperate" to "separate", "occured" to "occurred",
        "untill" to "until", "tommorow" to "tomorrow", "tommorrow" to "tomorrow", "wierd" to "weird",
        "freind" to "friend", "beleive" to "believe", "alot" to "a lot", "acheive" to "achieve",
        "adress" to "address", "begining" to "beginning", "calender" to "calendar", "goverment" to "government",
        "neccessary" to "necessary", "necesary" to "necessary", "occassion" to "occasion", "reccomend" to "recommend",
        "remeber" to "remember", "sucess" to "success", "suprise" to "surprise", "truely" to "truly",
        "knwo" to "know", "konw" to "know", "jsut" to "just", "yuo" to "you", "yoru" to "your",
        "dont" to "don't", "cant" to "can't", "wont" to "won't", "didnt" to "didn't", "doesnt" to "doesn't",
        "isnt" to "isn't", "wasnt" to "wasn't", "werent" to "weren't", "arent" to "aren't", "hasnt" to "hasn't",
        "havent" to "haven't", "hadnt" to "hadn't", "couldnt" to "couldn't", "wouldnt" to "wouldn't",
        "shouldnt" to "shouldn't", "youre" to "you're", "theyre" to "they're", "thats" to "that's",
        "whats" to "what's", "theres" to "there's", "heres" to "here's", "youve" to "you've", "theyve" to "they've",
        "weve" to "we've", "youll" to "you'll", "theyll" to "they'll", "itll" to "it'll",
    )
}
