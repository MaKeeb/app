package com.makeeb.engine.layout

import com.makeeb.engine.layout.data.BundledFile
import com.makeeb.engine.layout.data.JsonArray
import com.makeeb.engine.layout.data.JsonBoolean
import com.makeeb.engine.layout.data.JsonDataException
import com.makeeb.engine.layout.data.JsonNull
import com.makeeb.engine.layout.data.JsonNumber
import com.makeeb.engine.layout.data.JsonObject
import com.makeeb.engine.layout.data.JsonReader
import com.makeeb.engine.layout.data.JsonString
import com.makeeb.engine.layout.data.LayoutDataParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The strict JSON reader under the layout data (docs/layouts/schema.md). */
class JsonReaderTest {
    private fun parse(text: String) = JsonReader.parse(text)

    private fun assertRejected(text: String, fragment: String, path: String = "$") {
        val e = assertFailsWith<JsonDataException>(text) { parse(text) }
        assertTrue(fragment in (e.message ?: ""), "'$text': expected '$fragment' in '${e.message}'")
        assertEquals(path, e.path, "'$text'")
    }

    @Test
    fun readsEveryKindOfValue() {
        val root = parse(""" { "s": "a\"b\\c\/d\n\t\u00e9\ud83d\ude00", "n": -1.5e2, "i": 42, "t": true, "f": false, "z": null, "a": [1, "x", []], "o": {} } """)
        root as JsonObject
        assertEquals(listOf("s", "n", "i", "t", "f", "z", "a", "o"), root.fields.keys.toList(), "file order")
        assertEquals("a\"b\\c/d\n\té😀", (root.fields["s"] as JsonString).value)
        assertEquals(-150.0, (root.fields["n"] as JsonNumber).value)
        assertEquals(false, (root.fields["n"] as JsonNumber).isInteger)
        assertEquals(true, (root.fields["i"] as JsonNumber).isInteger)
        assertEquals(true, (root.fields["t"] as JsonBoolean).value)
        assertEquals(false, (root.fields["f"] as JsonBoolean).value)
        assertEquals(JsonNull, root.fields["z"])
        assertEquals(3, (root.fields["a"] as JsonArray).items.size)
        assertEquals(emptyMap(), (root.fields["o"] as JsonObject).fields)
    }

    @Test
    fun numbersMatchTheStandardParser() {
        listOf("0", "-0", "1", "2", "42", "0.5", "0.7", "0.75", "1.5", "1.25", "0.05", "-3.25", "1e3", "1E-2", "2.5e+1",
            "123456789", "0.000001", "9007199254740993").forEach { text ->
            assertEquals(text.toDouble(), (parse(text) as JsonNumber).value, text)
        }
        assertEquals(1.5f, (parse("1.5") as JsonNumber).value.toFloat())
        assertEquals(true, (parse("-12") as JsonNumber).isInteger)
        assertEquals(false, (parse("1.0") as JsonNumber).isInteger)
    }

    @Test
    fun badEscapesAreRejected() {
        assertRejected(""""\x"""", "bad escape '\\x'")
        assertRejected(""""\'"""", "bad escape '\\''")
        assertRejected(""""\u12"""", "bad escape '\\u12\"'")
        assertRejected(""""\u12G4"""", "needs four hex digits")
        assertRejected("\"a\nb\"", "control character")
        assertRejected("\"abc", "unterminated string")
        assertRejected("\"abc\\", "unterminated string")
    }

    @Test
    fun trailingCommasAreRejected() {
        assertRejected("""{"a": 1,}""", "trailing comma")
        assertRejected("""[1, 2,]""", "trailing comma")
        assertRejected("""{"rows": ["q", "a",]}""", "trailing comma", "$.rows")
        assertRejected("""[,]""", "unexpected ','", "$[0]")
    }

    @Test
    fun duplicateKeysAreRejected() {
        assertRejected("""{"a": 1, "a": 2}""", "duplicate key 'a'", "$.a")
        assertRejected("""{"alternates": {"a": "ä", "a": "à"}}""", "duplicate key 'a'", "$.alternates.a")
        assertRejected("""{"keys": {"'": {}, "'": {}}}""", "duplicate key", "$.keys[\"'\"]")
    }

    @Test
    fun otherSyntaxErrorsAreRejectedWithTheirPosition() {
        assertRejected("", "unexpected end of input")
        assertRejected("""{"a": 1} x""", "after the value")
        assertRejected("""// comment
            {}""", "line 1, column 1: unexpected '/'")
        assertRejected("""{'a': 1}""", "field name in double quotes")
        assertRejected("""{a: 1}""", "field name in double quotes")
        assertRejected("""{"a" 1}""", "expected ':'", "$.a")
        assertRejected("""{"a": 1 "b": 2}""", "expected ',' or '}'")
        assertRejected("""[1 2]""", "expected ',' or ']'")
        assertRejected("""[tru]""", "unexpected 't'", "$[0]")
        assertRejected("""{
            "a": 01}""", "line 2, column 19: bad number: no leading zeros", "$.a")
        listOf("1.", "-", ".5", "1e", "+1").forEach { assertRejected(it, "") }
        val deep = JsonReader.MAX_DEPTH + 1
        assertRejected("[".repeat(deep) + "]".repeat(deep), "nested deeper than ${JsonReader.MAX_DEPTH}", "$" + "[0]".repeat(deep - 1))
    }

    // region Through the schema: unknown fields and values of the wrong type

    private fun layoutProblems(json: String): List<String> =
        LayoutDataParser.layout(BundledFile("test", json)).problems.map { "${it.path}: ${it.message}" }

    private fun languageProblems(json: String): List<String> =
        LayoutDataParser.language(BundledFile("xx", json), setOf("qwerty")).problems.map { "${it.path}: ${it.message}" }

    private fun layout(schema: String = "1", name: String = "\"Test\"", rows: String = """["q w e", "a s d", "z x c"]""", extra: String = "") =
        """{"schema": $schema, "id": "test", "name": $name, "rows": $rows, $extra "sources": ["test"]}"""

    private fun language(alternates: String = """{"a": "ä"}""", layouts: String = """["qwerty"]""", extra: String = "") =
        """{"schema": 1, "language": "xx", "name": "X", "autonym": "X", "layouts": $layouts, "alternates": $alternates, $extra "sources": ["test"]}"""

    private fun assertOnlyProblem(problems: List<String>, expected: String) = assertEquals(listOf(expected), problems)

    @Test
    fun unknownFieldsAreRejectedWhereTheyAre() {
        assertOnlyProblem(layoutProblems(layout(extra = """"frame": "shift",""")), "$.frame: unknown field 'frame'")
        assertOnlyProblem(layoutProblems(layout(extra = """"keys": {"q": {"colour": "red"}},""")), "$.keys.q.colour: unknown field 'colour'")
        assertOnlyProblem(languageProblems(language(extra = """"bottomRow": [],""")), "$.bottomRow: unknown field 'bottomRow'")
    }

    @Test
    fun valuesOfTheWrongTypeAreRejected() {
        assertOnlyProblem(layoutProblems(layout(name = "5")), "$.name: expected a string")
        assertOnlyProblem(layoutProblems(layout(name = "null")), "$.name: expected a string")
        assertOnlyProblem(layoutProblems(layout(rows = "[1, 2, 3]")), "$.rows[0]: expected a string")
        assertOnlyProblem(layoutProblems(layout(rows = "\"q w e\"")), "$.rows: expected an array of strings")
        assertOnlyProblem(layoutProblems(layout(schema = "\"1\"")), "$.schema: expected a whole number")
        assertOnlyProblem(layoutProblems(layout(schema = "1.5")), "$.schema: expected a whole number")
        assertOnlyProblem(layoutProblems(layout(extra = """"keys": {"q": {"width": "wide"}},""")), "$.keys.q.width: expected a number")
        assertOnlyProblem(layoutProblems(layout(extra = """"keys": {"q": ["1"]},""")), "$.keys.q: expected an object")
        assertOnlyProblem(languageProblems(language(alternates = """{"a": ["ä", "à"]}""")), "$.alternates.a: expected a string")
        assertOnlyProblem(languageProblems(language(alternates = "\"a: ä\"")), "$.alternates: expected an object")
        assertOnlyProblem(languageProblems(language(layouts = """[true]""")), "$.layouts[0]: expected a string")
        assertOnlyProblem(layoutProblems("[]"), "$: expected an object")
        assertOnlyProblem(layoutProblems("""{"schema": 1, "id": "test", "name": "Test", "sources": ["test"]}"""), "$: missing field 'rows'")
    }

    // endregion
}
