package tr.gundem

import java.net.URI
import java.util.Locale

fun xHandle(input:String):String {
    val text=input.trim()
    val handle=if(text.startsWith("https://") || text.startsWith("http://")) {
        val uri=URI(text);require(uri.host?.lowercase() in listOf("x.com","www.x.com","twitter.com","www.twitter.com")) { "X/Twitter profil bağlantısı girin" }
        val parts=uri.path.trim('/').split('/');require(parts.size==1) { "Gönderi yerine profil bağlantısı girin" };parts.single()
    } else text.removePrefix("@")
    require(handle.matches(Regex("[A-Za-z0-9_]{1,15}")) && handle.lowercase() !in listOf("home","search","explore","settings","intent","i","share")) { "Geçerli bir @kullanıcı_adı veya X profil bağlantısı girin" }
    return handle.lowercase(Locale.ROOT)
}
fun xSource(input:String,name:String=""):Source {
    val handle=xHandle(input)
    return Source("x:$handle",name.trim().take(100).ifBlank { "@$handle" },"",true,kind="X_ACCOUNT")
}
val DEFAULT_X_ACCOUNTS=listOf(
    xSource("tkpninsesi","Türkiye Komünist Partisi (TKP) · @tkpninsesi"),
    xSource("OkuyanKemal","Kemal Okuyan · @OkuyanKemal"),
    xSource("pensendeyiz","Patronların Ensesindeyiz · @pensendeyiz")
)
fun nitterFeed(base:String,handle:String):String {
    require(base.isNotBlank()) { "X hesabı seçili; içerik bağlantısı kurulmadı. Nitter sunucusu veya hesaba özel RSS adresi ekleyin." }
    val uri=URI(base.trim());require(uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.rawQuery==null && uri.rawFragment==null && uri.userInfo==null) { "Nitter için sorgu içermeyen HTTPS sunucu adresi girin" }
    return "${base.trim().trimEnd('/')}/${xHandle(handle)}/rss"
}
fun resolveXSource(source:Source,base:String):Source {
    if(source.endpoint.isNotBlank()) { canonicalUrl(source.endpoint);return source }
    require(source.kind=="X_ACCOUNT") { "X RSS adresi yapılandırılmadı" }
    return source.copy(endpoint=nitterFeed(base.ifBlank { DEFAULT_X_SERVER },source.id.removePrefix("x:")))
}
