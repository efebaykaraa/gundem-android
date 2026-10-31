package tr.gundem

import org.jsoup.Jsoup
import java.net.URLEncoder

const val DEFAULT_X_SERVER="https://nitter.mizuki.st"
data class XProfile(val handle:String,val name:String,val avatar:String?)
class XDirectory(private val base:String=DEFAULT_X_SERVER,private val fetch:(String)->String=RssAdapter::download) {
    fun profile(handle:String):XProfile {
        val doc=Jsoup.parse(fetch("$base/${xHandle(handle)}"),base)
        val card=doc.selectFirst(".profile-card") ?: error("Profil bulunamadı")
        val user=xHandle(card.selectFirst(".profile-card-username")?.text() ?: error("Profil doğrulanamadı"))
        return XProfile(user,card.selectFirst(".profile-card-fullname")?.text().orEmpty(),card.selectFirst(".profile-card-avatar img")?.absUrl("src"))
    }
    fun search(query:String):List<XProfile> {
        val doc=Jsoup.parse(fetch("$base/search?f=users&q=${URLEncoder.encode(query,"UTF-8")}"),base)
        val results=doc.select(".timeline-item").mapNotNull { row -> runCatching {
            XProfile(xHandle(row.selectFirst(".username")!!.text()),row.selectFirst(".fullname")!!.text(),row.selectFirst("img.avatar")?.absUrl("src"))
        }.getOrNull() }.distinctBy { it.handle }.take(20)
        if(results.isNotEmpty()) return results
        // Some public instances disable user search. Exact username resolution still works.
        val exact=runCatching { profile(query) }.getOrNull()
        val known=DEFAULT_X_ACCOUNTS.filter { "${it.name} ${it.id}".contains(query,true) }.mapNotNull { runCatching { profile(it.id.removePrefix("x:")) }.getOrNull() }
        return (listOfNotNull(exact)+known).distinctBy { it.handle }
    }
}
