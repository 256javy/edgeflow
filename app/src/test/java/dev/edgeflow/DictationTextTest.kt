package dev.edgeflow

import org.junit.Assert.*
import org.junit.Test

class DictationTextTest {
    @Test fun exactCorrectionsPreserveWholeWordsAndDoNotCascade() {
        val dictionary = "PostgreSQL\nposgres => PostgreSQL\nfoo => bar\nbar => baz\nSQL => SQL"
        assertEquals("PostgreSQL y posgresita bar baz", PersonalDictionary.correct("POSGRES y posgresita foo bar", dictionary))
    }
    @Test fun vocabularyDoesNotInventPhoneticAliases() {
        assertEquals("un beso", PersonalDictionary.correct("un beso", "PoC"))
    }
    @Test fun conflictingAliasesAreNotGuessed() {
        assertEquals("api", PersonalDictionary.correct("api", "api => API\napi => api-rest"))
    }
    @Test fun unicodeBoundariesPreventSubstringEdits() {
        assertEquals("mysql SQL ñsql SQL", PersonalDictionary.correct("mysql sql ñsql SQL", "sql => SQL"))
    }
    @Test fun termsUseCanonicalValuesAndAreBounded() {
        assertEquals(listOf("PostgreSQL", "DNIT"), DictationText.terms("posgres => PostgreSQL\nDNIT\nDNIT"))
        assertEquals(80, DictationText.terms((1..100).joinToString("\n") { "Term$it" }).size)
    }
    @Test fun overlapMergesOnlySharedBoundaryWords() {
        assertEquals("Vamos a revisar el PR mañana.", DictationText.merge("Vamos a revisar el PR", "el PR mañana."))
        assertEquals("hola hola mundo", DictationText.merge("hola", "hola mundo"))
    }
    @Test fun wavRoundTripPreservesAudioContract() {
        val input = FloatArray(16000) { .25f }
        val decoded = WavReader.read(DictationText.wav(input))
        assertEquals(input.size, decoded.size)
        assertEquals(.25f, decoded[0], .0001f)
    }
    @Test fun reviewRejectsAddedOrChangedNumbers() {
        assertNotNull(ReviewPolicy.rejection("Versión 12", "Versión 13."))
        assertNotNull(ReviewPolicy.rejection("Actualizar PostgreSQL", "Actualizar PostgreSQL 17."))
        assertNull(ReviewPolicy.rejection("versión 12 no desplegar", "Versión 12. No desplegar."))
    }
    @Test fun reviewRejectsLostNegation() {
        assertNotNull(ReviewPolicy.rejection("No enviar el informe", "Enviar el informe."))
    }
    @Test fun reviewRejectsEmptyOrOversizedOutput() {
        assertNotNull(ReviewPolicy.rejection("Hola", ""))
        assertNotNull(ReviewPolicy.rejection("Hola", "x".repeat(300)))
    }
}
