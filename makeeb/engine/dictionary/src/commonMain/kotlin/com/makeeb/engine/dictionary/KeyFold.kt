package com.makeeb.engine.dictionary

/**
 * How dictionary keys are folded, so what people type finds what they meant: lower case, no
 * diacritics ("naive" finds "naïve", "cafe" finds "café"), the usual letter expansions ("strasse"
 * finds "Straße"), and no apostrophes ("dont" finds "don't"). A folded key can hold several
 * spellings; the spelling is what gets suggested. Latin script only so far; other scripts pass
 * through lower-cased.
 */
object KeyFold {
    /** The `keyFold` a pack declares for this fold. */
    const val SCHEME = "fold-v2"

    fun fold(word: String): String {
        val out = StringBuilder(word.length)
        for (char in word.lowercase()) {
            when {
                char in APOSTROPHES -> Unit
                char.category == CharCategory.NON_SPACING_MARK -> Unit
                else -> {
                    val single = SOURCE.indexOf(char)
                    when {
                        single >= 0 -> out.append(TARGET[single])
                        else -> out.append(MULTI[char] ?: char.toString())
                    }
                }
            }
        }
        return out.toString()
    }

    private const val APOSTROPHES = "'\u2019\u02BC"

    // Generated from Unicode decompositions of U+00C0–U+024F and U+1E00–U+1EFF.
    private const val SOURCE = "àáâãäåçèéêëìíîïðñòóôõöøùúûüýÿāăąćĉċčďđēĕėęěĝğġģĥħĩīĭįıĵķĺļľŀłńņňŋōŏőŕŗřśŝşšţťŧũūŭůűųŵŷźżžſƒơưǎǐǒǔǖǘǚǜǟǡǧǩǫǭǰǵǹǻȁȃȅȇȉȋȍȏȑȓȕȗșțȟȧȩȫȭȯȱȳḁḃḅḇḉḋḍḏḑḓḕḗḙḛḝḟḡḣḥḧḩḫḭḯḱḳḵḷḹḻḽḿṁṃṅṇṉṋṍṏṑṓṕṗṙṛṝṟṡṣṥṧṩṫṭṯṱṳṵṷṹṻṽṿẁẃẅẇẉẋẍẏẑẓẕẖẗẘẙạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ"
    private const val TARGET = "aaaaaaceeeeiiiidnoooooouuuuyyaaaccccddeeeeegggghhiiiiijklllllnnnnooorrrsssstttuuuuuuwyzzzsfouaiouuuuuaagkoojgnaaaeeiioorruusthaeooooyabbbcdddddeeeeefghhhhhiikkkllllmmmnnnnoooopprrrrsssssttttuuuuuvvwwwwwxxyzzzhtwyaaaaaaaaaaaaeeeeeeeeiioooooooooooouuuuuuuyyyy"
    private val MULTI = mapOf('ß' to "ss", 'æ' to "ae", 'þ' to "th", 'ĳ' to "ij", 'œ' to "oe")
}
