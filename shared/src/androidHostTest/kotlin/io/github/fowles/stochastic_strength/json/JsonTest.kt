package io.github.fowles.stochastic_strength.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shim must round-trip with the real org.json in both directions for the subset BackupJson
 * uses. Byte-level output may differ (org.json prints 5.0 as 5); parsed values must not.
 */
class JsonTest {

    private fun shimSample(): JSONObject = JSONObject()
        .put("str", "hello")
        .put("long", 1234567890123L)
        .put("int", 42)
        .put("double", 2.5)
        .put("whole", 100.0)
        .put("bool", true)
        .put("nil", JSONObject.NULL)
        .put("nested", JSONObject().put("k", "v"))
        .put("strings", JSONArray().put("A").put("B"))
        .put("objects", JSONArray().put(JSONObject().put("n", 1)).put(JSONObject().put("n", 2)))

    @Test
    fun shimOutputParsesWithOrgJson() {
        for (text in listOf(shimSample().toString(), shimSample().toString(2))) {
            val o = org.json.JSONObject(text)
            assertEquals("hello", o.getString("str"))
            assertEquals(1234567890123L, o.getLong("long"))
            assertEquals(42, o.getInt("int"))
            assertEquals(2.5, o.getDouble("double"), 0.0)
            assertEquals(100.0, o.getDouble("whole"), 0.0)
            assertTrue(o.getBoolean("bool"))
            assertTrue(o.isNull("nil"))
            assertEquals("v", o.getJSONObject("nested").getString("k"))
            assertEquals(listOf("A", "B"), (0 until o.getJSONArray("strings").length()).map { o.getJSONArray("strings").getString(it) })
            assertEquals(2, o.getJSONArray("objects").getJSONObject(1).getInt("n"))
        }
    }

    @Test
    fun orgJsonOutputParsesWithShim() {
        val text = org.json.JSONObject()
            .put("str", "hello").put("long", 1234567890123L).put("int", 42)
            .put("double", 2.5).put("whole", 100.0).put("bool", true)
            .put("nil", org.json.JSONObject.NULL)
            .put("nested", org.json.JSONObject().put("k", "v"))
            .put("strings", org.json.JSONArray().put("A").put("B"))
            .put("objects", org.json.JSONArray().put(org.json.JSONObject().put("n", 1)).put(org.json.JSONObject().put("n", 2)))
            .toString(2)
        val o = JSONObject(text)
        assertEquals("hello", o.getString("str"))
        assertEquals(1234567890123L, o.getLong("long"))
        assertEquals(42, o.getInt("int"))
        assertEquals(2.5, o.getDouble("double"), 0.0)
        assertEquals(100.0, o.getDouble("whole"), 0.0)   // org.json wrote this as `100`
        assertTrue(o.getBoolean("bool"))
        assertTrue(o.isNull("nil"))
        assertEquals("v", o.getJSONObject("nested").getString("k"))
        val strings = o.getJSONArray("strings")
        assertEquals(listOf("A", "B"), (0 until strings.length()).map { strings.getString(it) })
        assertEquals(2, o.getJSONArray("objects").getJSONObject(1).getInt("n"))
    }

    @Test
    fun optAndIsNullSemanticsMatchOrgJson() {
        val o = JSONObject("""{"present":"x","nil":null,"num":7,"flag":false,"arr":[1]}""")
        assertTrue(o.isNull("nil"))
        assertTrue(o.isNull("missing"))
        assertFalse(o.isNull("present"))
        assertEquals("x", o.optString("present"))
        assertEquals("", o.optString("missing"))
        assertEquals("", o.optString("nil"))
        assertEquals(7, o.optInt("num", -1))
        assertEquals(-1, o.optInt("missing", -1))
        assertEquals(false, o.optBoolean("flag", true))
        assertEquals(true, o.optBoolean("missing", true))
        assertEquals(1, o.optJSONArray("arr")!!.length())
        assertNull(o.optJSONArray("missing"))
        assertTrue(o.has("present"))
        assertFalse(o.has("missing"))
    }

    @Test
    fun putNullRemovesKeyLikeOrgJson() {
        val o = JSONObject().put("a", 1).put("a", null)
        assertFalse(o.has("a"))
        assertEquals("{}", o.toString())
    }

    @Test
    fun getOnMissingOrWrongTypeThrowsJSONException() {
        val o = JSONObject("""{"s":"text","n":null}""")
        assertThrows(JSONException::class.java) { o.getLong("missing") }
        assertThrows(JSONException::class.java) { o.getLong("s") }
        assertThrows(JSONException::class.java) { o.getString("n") }
        assertThrows(JSONException::class.java) { o.getJSONArray("s") }
        assertThrows(JSONException::class.java) { o.getJSONObject("missing") }
    }

    @Test
    fun malformedInputThrowsJSONException() {
        assertThrows(JSONException::class.java) { JSONObject("not json") }
        assertThrows(JSONException::class.java) { JSONObject("[1,2]") }
    }

    @Test
    fun prettyPrintUsesTwoSpaceIndentAndCompactHasNone() {
        val o = JSONObject().put("a", JSONObject().put("b", 1))
        assertEquals("""{"a":{"b":1}}""", o.toString())
        assertEquals("{\n  \"a\": {\n    \"b\": 1\n  }\n}", o.toString(2))
    }
}
