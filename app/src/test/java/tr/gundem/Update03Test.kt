package tr.gundem

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class Update03Test {
    @Test fun paragraphBreaksListsAndMarkdownSurvive() {
        val body=RssAdapter.clean("<p>İlk paragraf.</p><p>İkinci<br>satır.</p><ul><li>Bir</li><li>İki</li></ul>")
        assertTrue(body,body.contains("İlk paragraf.\n\nİkinci\nsatır."));assertTrue(body.contains("• Bir"))
        val formatted=formattedBody("# Başlık\n\n**Önemli**\nyeni satır\n\n<script>x</script>")
        assertTrue(formatted.contains("<b>Başlık</b>"));assertTrue(formatted.contains("<b>Önemli</b><br>yeni satır"));assertFalse(formatted.contains("<script>"))
    }
    @Test fun logPersistsAcrossInstancesAndExportsTiming() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val log=EventLog(context);log.clear();log.add("AI","Çağrı tamamlandı","girdi_token=42",123)
        val second=EventLog(context);assertEquals(1,second.rows().size);assertEquals(123L,second.rows().single().getLong("elapsedMs"));assertTrue(second.text().contains("girdi_token=42"));second.clear()
    }
    @Test fun catalogIncludesEveryMeasuredPairAndScores() {
        val list=modelCatalog(ApplicationProvider.getApplicationContext())
        assertEquals(20,list.size)
        assertTrue("Every original/fine-tuned variant must be downloadable",list.all { it.downloadable })
        assertEquals(18,list.count { it.format=="gguf" })
        assertEquals(2,list.count { it.format=="onnx" })
        assertEquals(20,list.map { it.id }.distinct().size)
        assertEquals(10,list.groupBy { it.family }.size)
        list.groupBy { it.family }.values.forEach { pair ->
            assertEquals(setOf("original","distilled"),pair.map { it.variant }.toSet())
            assertTrue(pair.all { it.overallScore!=null && it.overallScore in 0.0..100.0 })
            assertEquals(0.0,pair.single { it.variant=="original" }.overallScore!!,0.0)
        }
        assertEquals(RECOMMENDED_MODEL,list.first())
        assertTrue(list.first().downloadable)
        list.filter { it.downloadable }.forEach {
            assertEquals("gundem-score-v1",it.scoreVersion)
            assertTrue(it.url.matches(Regex("https://huggingface\\.co/efebaykara/[^/]+/resolve/[a-f0-9]{40}/"+(if(it.format=="onnx") "onnx/[^/]+\\.zip" else "gguf/[^/]+\\.gguf"))))
            assertTrue(it.size>50_000_000)
        }
        list.filterNot { it.downloadable }.forEach { assertTrue(it.availabilityReason.isNotBlank());assertTrue(it.url.isEmpty()) }
    }
    @Test fun zeroScoreIsDisplayedAsMeasuredAndUnavailableVariantsRemainDistinct() {
        val original=ModelSpec("",0,"","Original",id="gpt:original",variant="original",overallScore=0.0)
        val tuned=original.copy(id="gpt:distilled",variant="distilled",overallScore=69.6)
        assertFalse(original.downloadable)
        assertEquals("Gündem Score · 0,0 / 100",original.scoreLabel)
        assertEquals("Fine-tuned",tuned.variantLabel)
        assertNotEquals(original.id,tuned.id)
        assertTrue(original.copy(overallScore=null).scoreLabel.contains("Ölçülmedi"))
    }
    @Test fun disabledSearchFallsBackToVerifiedProfileInsideApp() {
        val directory=XDirectory("https://example.org") { url ->
            if(url.contains("/search?")) "<div class='timeline'>No items found</div>" else """<div class='profile-card'><a class='profile-card-username'>@OkuyanKemal</a><a class='profile-card-fullname'>Kemal Okuyan</a><a class='profile-card-avatar'><img src='/pic/avatar.jpg'></a></div>"""
        }
        val profiles=directory.search("@OkuyanKemal")
        assertEquals(1,profiles.size);assertEquals("okuyankemal",profiles.single().handle);assertEquals("https://example.org/pic/avatar.jpg",profiles.single().avatar)
    }
    @Test fun appearanceRoundTripsWithExistingSettings()=runBlocking {
        val store=SettingsStore(ApplicationProvider.getApplicationContext())
        val s=Settings(theme="KOYU",fontScale=1.2f);assertEquals(s,store.decode(store.encode(s)))
        assertEquals(1f,store.decode("{}").fontScale)
    }
}
