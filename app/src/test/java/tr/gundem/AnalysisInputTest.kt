package tr.gundem
import org.junit.Test
import org.junit.Assert.*

class AnalysisInputTest {
    @Test fun onlyHeadlineIsSentAndBodyChangesDoNotInvalidateAnalysis() {
        val a=Article("a","s","r","https://example.org/a","İşçiler greve çıktı","Gizli giriş metni",null,null,"h")
        assertEquals("İşçiler greve çıktı",classificationInput(a))
        val changed=a.copy(intro="Başka metin",contentHash="changed")
        assertEquals(classificationInput(a),classificationInput(changed))
        assertEquals(analysisCacheKey(a,Settings()),analysisCacheKey(changed,Settings()))
        assertNotEquals(analysisCacheKey(a,Settings()),analysisCacheKey(a.copy(title="Başka başlık"),Settings()))
    }
    @Test fun compactWireFormatKeepsPublicValues() {
        val value=compactAnalysis("""[["EMEK","TURKIYE","EYLEM"],"SOMUT_GELISME",2,3,1]""")
        assertTrue(value.complete);assertEquals(listOf("EMEK","TURKIYE","EYLEM"),value.tags);assertEquals(3,value.seriousness)
        assertEquals(value,OutputValidator.analysis(value.json()))
    }
    @Test fun binaryTopicsAreIndependentAndZerosMeanOther() {
        assertEquals(listOf("EMEK","TURKIYE","EYLEM"),compactAnalysis("""[[0,1,1,0,0,0,1],"SOMUT_GELISME",1,2,2]""").tags)
        assertEquals(listOf("DIGER"),compactAnalysis("""[[0,0,0,0,0,0,0],"YORUM",0,0,0]""").tags)
        assertFalse(compactAnalysis("""[[0,2,1,0,0,0,1],"SOMUT_GELISME",1,2,2]""").complete)
    }
    @Test fun countersExcludeActiveCategoryAndOldSuccesses() {
        val settings=Settings(modelHash="model")
        val a=Article("a","s","r","https://example.org/a","Title","Intro",null,null,"h")
        val current=a.copy(state="TAMAM",analysisKey=analysisCacheKey(a,settings))
        val counts=queueCounts(listOf(current,a.copy(id="b",state="CALISIYOR"),a.copy(id="c",state="TAMAM",analysisKey="old"),a.copy(id="d",hidden=true)),settings)
        assertEquals(mapOf("TAMAM" to 1,"BEKLIYOR" to 2),counts);assertFalse("CALISIYOR" in counts)
    }
    @Test fun malformedCutAndOutOfRangeNeverBecomeSuccessful() {
        assertFalse(compactAnalysis("""[["DIGER"],"YORUM",0,0,0]""",false).complete)
        assertFalse(compactAnalysis("""[["DIGER","TKP"],"YORUM",0,0,0]""").complete)
        assertNull(compactAnalysis("""[["DIGER"],"YORUM",9,0,0]""").impact)
        assertFalse(compactAnalysis("[]").complete)
    }
}
