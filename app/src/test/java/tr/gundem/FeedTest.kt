package tr.gundem
import org.junit.Test
import org.junit.Assert.*
class FeedTest {
    private val source=Source("test","Test","https://example.org/feed")
    @Test fun rssCleansHtmlExtractsImageAndKeepsMissingDate() {
        val result=RssAdapter.parse("""<rss><channel><item><title>Başlık</title><link>https://example.org/a</link><guid>a1</guid><description><![CDATA[<script>ignore me</script><img src="https://example.org/p.jpg"><p>İşçiler mücadelede.</p>]]></description></item></channel></rss>""",source).single()
        assertEquals("İşçiler mücadelede.",result.intro);assertEquals("https://example.org/p.jpg",result.image);assertNull(result.published);assertEquals("a1",result.remoteId)
    }
    @Test fun structuredXmlParagraphsRemainSeparate() {
        val a=RssAdapter.parse("""<rss><channel><item><title>Başlık</title><link>https://example.org/a</link><description><p>İlk paragraf.</p><p>İkinci paragraf.</p></description></item></channel></rss>""",source).single()
        assertEquals("İlk paragraf.\n\nİkinci paragraf.",a.intro)
    }
    @Test fun atomDateAndAlternateLink() {
        val result=RssAdapter.parse("""<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Haber</title><link rel="alternate" href="https://example.org/b"/><id>atom1</id><published>2026-10-28T09:00:00Z</published><summary>Özet</summary></entry></feed>""",source).single()
        assertEquals(1793178000000L,result.published);assertEquals("atom1",result.remoteId)
    }
    @Test fun invalidDatesStayUnknownAndHtmlIsRejected() {
        assertNull(RssAdapter.date("geçen gün"));assertThrows(IllegalArgumentException::class.java) { RssAdapter.parse("<html>error</html>",source) }
        assertThrows(IllegalArgumentException::class.java) { RssAdapter.parse("<!DOCTYPE rss><rss/>",source) }
    }
    @Test fun repeatDownloadsHaveStableIdentity() {
        val xml="""<rss><channel><item><title>Haber</title><link>https://example.org/a</link></item></channel></rss>"""
        val a=RssAdapter.parse(xml,source).single();val b=RssAdapter.parse(xml,source).single();assertEquals(a.id,b.id);assertEquals(a.contentHash,b.contentHash)
    }
    @Test fun actualVerifiedFeedsParse() {
        listOf("sol","tkp","pe").forEach { id->
            val xml=javaClass.getResource("/feeds/$id.xml")!!.readText()
            val articles=RssAdapter.parse(xml,INITIAL_SOURCES.first { it.id==id })
            assertTrue(id,articles.isNotEmpty());assertTrue(articles.all { it.title.isNotBlank() && it.url.startsWith("https://") });assertTrue(articles.size<=40)
        }
    }
}
