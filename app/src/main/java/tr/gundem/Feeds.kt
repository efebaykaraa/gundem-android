package tr.gundem

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.HttpURLConnection
import java.net.URL
import java.time.*
import java.time.format.DateTimeFormatter

val INITIAL_SOURCES=listOf(
    Source("sol","soL Haber","https://haber.sol.org.tr/rss.xml"),
    Source("tkp","Türkiye Komünist Partisi","https://www.tkp.org.tr/feed/"),
    Source("pe","Patronların Ensesindeyiz","https://patronlarinensesindeyiz.org/feed/")
)
interface SourceAdapter { fun fetch(source:Source):List<Article> }
class RssAdapter:SourceAdapter {
    override fun fetch(source:Source):List<Article> {
        require(source.endpoint.startsWith("https://")) { "HTTPS RSS adresi gerekli; X için çalışan sunucu ve hesap beslemesi sağlayın" }
        val body=download(source.endpoint)
        return parse(body,source).also { require(it.isNotEmpty()) { "Beslemede kullanılabilir haber bulunamadı (HTML/erişim engeli olabilir)" } }
    }
    companion object {
        const val MAX_BYTES=12*1024*1024
        fun download(endpoint:String):String {
            var target=canonicalUrl(endpoint)
            repeat(5) {
                val c=(URL(target).openConnection() as HttpURLConnection).apply { connectTimeout=15000;readTimeout=20000;instanceFollowRedirects=false;setRequestProperty("User-Agent","Gundem/0.1 (personal RSS reader)");setRequestProperty("Accept","application/rss+xml, application/atom+xml, application/xml, text/xml") }
                try {
                    if(c.responseCode in listOf(301,302,303,307,308)) { target=canonicalUrl(URL(URL(target),c.getHeaderField("Location") ?: error("Yönlendirme eksik")).toString());return@repeat }
                    require(c.responseCode==200) { "HTTP ${c.responseCode}" }
                    val bytes=c.inputStream.use { it.readLimited(MAX_BYTES+1) };require(bytes.size<=MAX_BYTES) { "Besleme 12 MiB sınırını aşıyor" }
                    return bytes.toString(Charsets.UTF_8)
                } finally { c.disconnect() }
            };error("Çok fazla yönlendirme")
        }
        fun clean(html:String):String {
            val doc=Jsoup.parse(html);doc.select("script,style,iframe").remove()
            doc.select("p,div,h1,h2,h3,h4,blockquote,ul,ol").forEach { it.before("\n\n");it.after("\n\n") }
            doc.select("li").forEach { it.before("\n• ") }
            return doc.wholeText().replace(Regex("[ \t\u000B\u000C\r]+")," ").replace(Regex(" *\n *"),"\n").replace(Regex("\n{3,}"),"\n\n").trim()
        }
        fun date(raw:String):Long? {
            if(raw.isBlank()) return null
            return runCatching { ZonedDateTime.parse(raw.trim(),DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
                ?:runCatching { Instant.parse(raw.trim()).toEpochMilli() }.getOrNull()
                ?:runCatching { OffsetDateTime.parse(raw.trim()).toInstant().toEpochMilli() }.getOrNull()
        }
        fun parse(xml:String,source:Source):List<Article> {
            require(!xml.contains("<!DOCTYPE",true) && !xml.contains("<!ENTITY",true)) { "DTD/Entity içeren besleme reddedildi" }
            val doc=Jsoup.parse(xml,source.endpoint,Parser.xmlParser())
            require(doc.selectFirst("rss,feed")!=null) { "Geçerli RSS/Atom değil" }
            return doc.select("item,entry").take(40).mapNotNull { item -> runCatching {
                fun value(vararg names:String)=names.firstNotNullOfOrNull { name -> item.children().firstOrNull { it.tagName().equals(name,true) }?.text()?.takeIf(String::isNotBlank) }?:""
                val link=item.children().firstOrNull { it.tagName()=="link" && (it.attr("rel").isBlank() || it.attr("rel")=="alternate") }
                val url=canonicalUrl(URL(URL(source.endpoint),link?.attr("href")?.ifBlank { link.text() } ?: error("Bağlantı yok")).toString())
                val title=clean(value("title")).take(600);require(title.isNotBlank())
                val body=item.children().firstOrNull { it.tagName().lowercase() in listOf("description","summary","content","content:encoded") }
                val html=if(body?.children()?.isNotEmpty()==true) body.html() else body?.text().orEmpty()
                val intro=clean(html).take(5000)
                val image=item.selectFirst("enclosure[type^=image],media|thumbnail,media|content")?.attr("url")?.ifBlank { null }
                    ?:Jsoup.parse(html).selectFirst("img[src]")?.attr("src")
                val remote=value("guid","id").ifBlank { url }
                Article(hash(url),source.id,remote,url,title,intro,date(value("pubDate","published","updated","dc:date")),image?.let { runCatching { canonicalUrl(URL(URL(url),it).toString()) }.getOrNull() },hash("$title\n$intro"))
            }.getOrNull() }
        }
    }
}
/** X is a pluggable feed adapter. No public Nitter instance or API credentials are assumed. */
class XFeedAdapter:SourceAdapter {
    override fun fetch(source:Source):List<Article> {
        require(source.endpoint.isNotBlank()) { "X bağlı değil. Hesaba ait çalışan HTTPS Nitter/RSS beslemesini ayarlayın." }
        return RssAdapter().fetch(source)
    }
}
