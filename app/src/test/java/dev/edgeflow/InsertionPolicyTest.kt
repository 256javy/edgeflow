package dev.edgeflow

import org.junit.Assert.*
import org.junit.Test

class InsertionPolicyTest {
    @Test fun preservesBothSidesOfCursor() {
        val edit = InsertionPolicy.insert("hola mundo", 4, 4, "desde Paraguay")!!
        assertEquals("hola desde Paraguay mundo", edit.text)
        assertEquals(19, edit.cursor)
    }
    @Test fun refusesSelectedTextRatherThanDeletingIt() {
        assertNull(InsertionPolicy.insert("importante", 0, 10, "dictado"))
    }
    @Test fun refusesUnknownOrStaleSelection() {
        assertNull(InsertionPolicy.insert("abc", -1, -1, "dictado"))
        assertNull(InsertionPolicy.insert("abc", 4, 4, "dictado"))
    }
    @Test fun emptyFieldAndUnicodeRemainIntact() {
        assertEquals("ñandutí", InsertionPolicy.insert("", 0, 0, " ñandutí ")!!.text)
        assertNull(InsertionPolicy.insert("\uD83D\uDE00", 1, 1, "texto"))
    }
    @Test fun rejectsBlankRecognition() { assertNull(InsertionPolicy.insert("existente", 0, 0, "   ")) }
    @Test fun blocksPasswordVariations() {
        listOf(0x81, 0x91, 0xe1, 0x12).forEach { assertTrue(InsertionPolicy.sensitive(it, false)) }
        assertTrue(InsertionPolicy.sensitive(1, true))
        assertFalse(InsertionPolicy.sensitive(0x20001, false))
    }
}
