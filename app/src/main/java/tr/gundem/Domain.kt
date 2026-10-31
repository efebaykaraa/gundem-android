package tr.gundem

import kotlinx.serialization.json.*
import java.net.URI
import java.security.MessageDigest
import java.time.*
import java.util.Locale
import kotlin.math.pow

val TOPICS = listOf("TKP", "EMEK", "TURKIYE", "ULUSLARARASI", "KUBA", "EKONOMI", "EYLEM", "DIGER")
val TYPES = listOf("ACIKLAMA", "CAGRI", "SOMUT_GELISME", "YORUM")
val FIELDS = listOf("etiketler", "tur", "etki", "ciddiyet", "aciliyet")
const val PROMPT_VERSION = "tr-5-minimal"
fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
fun canonicalUrl(s: String): String {
    val u = URI(s.trim())
    require(u.scheme == "https" && !u.host.isNullOrBlank()) { "Yalnız HTTPS bağlantıları desteklenir" }
    val query = u.rawQuery?.split('&')?.filterNot { it.substringBefore('=').lowercase().let { k -> k.startsWith("utm_") || k in listOf("fbclid", "gclid") } }?.sorted()?.joinToString("&")?.ifBlank { null }
    val base=URI("https", u.authority.lowercase(), u.path.ifBlank { "/" }, null, null).toASCIIString()
    return base + (query?.let { "?$it" } ?: "")
}

data class Analysis(val tags: List<String>? = null, val type: String? = null, val impact: Int? = null, val seriousness: Int? = null, val urgency: Int? = null) {
    val complete get() = tags != null && type != null && impact != null && seriousness != null && urgency != null
    val invalid get() = FIELDS.filter { when(it) { "etiketler" -> tags == null; "tur" -> type == null; "etki" -> impact == null; "ciddiyet" -> seriousness == null; else -> urgency == null } }
    fun replace(field: String, other: Analysis) = when(field) {
        "etiketler" -> copy(tags = other.tags); "tur" -> copy(type = other.type); "etki" -> copy(impact = other.impact); "ciddiyet" -> copy(seriousness = other.seriousness); else -> copy(urgency = other.urgency)
    }
    fun json() = buildJsonObject {
        tags?.let { put("etiketler", JsonArray(it.map(::JsonPrimitive))) }; type?.let { put("tur", it) }
        impact?.let { put("etki", it) }; seriousness?.let { put("ciddiyet", it) }; urgency?.let { put("aciliyet", it) }
    }.toString()
}
object OutputValidator {
    fun analysis(raw: String, completed: Boolean = true): Analysis {
        if (!completed) return Analysis()
        val o = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return Analysis()
        if (o.keys.any { it !in FIELDS }) return Analysis()
        fun score(k: String): Int? = (o[k] as? JsonPrimitive)?.takeIf { !it.isString && it.content.matches(Regex("[0-3]")) }?.intOrNull
        val tags = (o["etiketler"] as? JsonArray)?.takeIf { it.size in 1..TOPICS.size && it.all { v -> v is JsonPrimitive && v.isString && v.content in TOPICS } }?.map { it.jsonPrimitive.content }?.takeIf { it.distinct().size == it.size && ("DIGER" !in it || it.size == 1) }
        return Analysis(tags, (o["tur"] as? JsonPrimitive)?.takeIf { it.isString && it.content in TYPES }?.content, score("etki"), score("ciddiyet"), score("aciliyet"))
    }
    fun partition(raw: String, size: Int, completed: Boolean = true): List<List<Int>>? = runCatching {
        require(completed)
        val arr = Json.parseToJsonElement(raw) as JsonArray
        val groups = arr.map { v -> (v as JsonArray).map { p -> val n = p as JsonPrimitive; require(!n.isString && n.content.matches(Regex("[1-9][0-9]*"))); n.int } .also { require(it.isNotEmpty()) } }
        require(groups.flatten().sorted() == (1..size).toList()); groups
    }.getOrNull()
}
data class Generation(val text: String, val completed: Boolean)
/** Each invalid field gets exactly one repair. Failed/incomplete generations never contribute fields. */
suspend fun analyzeWithRepair(generate: suspend (String?) -> Generation): Analysis {
    val first = generate(null)
    var result = OutputValidator.analysis(first.text, first.completed)
    for (field in result.invalid) {
        val retry = try { generate(field) } catch(e:kotlinx.coroutines.CancellationException) { throw e } catch(e:Exception) { Generation("",false) }
        val candidate = OutputValidator.analysis(retry.text, retry.completed)
        result = result.replace(field, candidate)
    }
    return result
}

data class Weights(
    val topics: Map<String, Double> = TOPICS.associateWith { 1.0 },
    val types: Map<String, Double> = TYPES.associateWith { 1.0 },
    val impact: Double = 1.0, val urgency: Double = 1.0,
    val seriousness: List<Double> = listOf(0.75, 1.0, 1.25, 1.5),
    val halfLifeHours: Double = 48.0, val freshnessFloor: Double = 0.25,
    val dailyPenalty: Double = 0.5
) {
    fun validated(): Weights {
        require(topics.keys == TOPICS.toSet() && types.keys == TYPES.toSet())
        require((topics.values + types.values).all { it.isFinite() && it in 0.5..1.5 })
        require(impact.isFinite() && impact in 0.25..3.0 && urgency.isFinite() && urgency in 0.25..3.0)
        require(seriousness.size == 4 && seriousness.all { it.isFinite() && it in 0.5..2.0 })
        require(halfLifeHours in 6.0..168.0 && freshnessFloor in 0.1..0.5)
        require(dailyPenalty.isFinite() && dailyPenalty in 0.0..5.0); return this
    }
}
data class Score(val value: Double, val activeTag: String?, val freshness: Double, val explanation: String, val agePenalty:Double=0.0, val ageDays:Long?=null)
fun score(a: Analysis, published: Long?, source: Double, w: Weights, now: Long): Score {
    val freshness = if (published == null) w.freshnessFloor else w.freshnessFloor + (1 - w.freshnessFloor) * 0.5.pow(((now - published).coerceAtLeast(0) / 3600000.0) / w.halfLifeHours)
    val active = a.tags?.sortedWith(compareByDescending<String> { w.topics[it] ?: 1.0 }.thenBy { it })?.firstOrNull()
    val days=published?.let { ((now-it).coerceAtLeast(0)/86_400_000L) }
    val penalty=(days ?: 0)*w.dailyPenalty
    val ageText=if(days==null) "Tarih bilinmiyor: günlük kesinti yok" else "$days gün × ${w.dailyPenalty} = $penalty puan kesinti"
    if (!a.complete) {
        val value=(0.5*freshness-penalty).coerceAtLeast(0.0)
        return Score(value,active,freshness,"Eksik analiz: max(0, 0,5 × güncellik($freshness) − $penalty) = $value. $ageText. Eksik puanlar sıfır sayılmaz.",penalty,days)
    }
    val base = w.impact * a.impact!! + w.urgency * a.urgency!! * w.seriousness[a.seriousness!!]
    val topic = w.topics[active] ?: 1.0; val type = w.types[a.type] ?: 1.0
    val value = (base * topic * type * source.coerceIn(0.5, 1.5) * freshness-penalty).coerceAtLeast(0.0)
    return Score(value, active, freshness, "max(0, (${w.impact} × ${a.impact} + ${w.urgency} × ${a.urgency} × ${w.seriousness[a.seriousness]}) × konu $topic ($active) × tür $type × kaynak $source × güncellik ${"%.3f".format(Locale.US, freshness)} − $penalty) = ${"%.3f".format(Locale.US, value)}. $ageText",penalty,days)

}

data class TimeHint(val time: String, val relation: String)
object TimeDetector {
    private val time = Regex("(?<![\\d.,:])(\\d{1,2})[.:](\\d{2})(?![\\d.,:]|\\s*[%₺])")
    fun detect(text: String): List<TimeHint> {
        val lower = text.lowercase(Locale.forLanguageTag("tr"))
        return time.findAll(lower).mapNotNull { m ->
            val h=m.groupValues[1].toInt(); val min=m.groupValues[2].toInt(); if(h !in 0..23 || min !in 0..59) return@mapNotNull null
            val context=lower.substring((m.range.first-60).coerceAtLeast(0),(m.range.last+90).coerceAtMost(lower.length))
            val clock=Regex("saat|itibarıyla|sularında").containsMatchIn(context)
            val action=Regex("eylem|çağrı|çağır|toplan|buluş|miting|yürüyüş|grev|basın açıklama").containsMatchIn(context)
            val money=Regex("yüzde|oran|fiyat|lira|milyon|katsayı|puan").containsMatchIn(context)
            if ((!clock && !action) || (money && !clock)) return@mapNotNull null
            val past=Regex("dün|yapıldı|toplandı|gerçekleşti|düzenlendi|başladı").containsMatchIn(context)
            val future=Regex("yarın|toplanacak|yapılacak|davet|çağrı|çağır|buluşalım").containsMatchIn(context)
            TimeHint("%02d:%02d".format(h,min), when { past && !future -> "Geçmiş olay ifadesi"; future && !past -> "Gelecek çağrısı ifadesi; tarih doğrulanmadı"; else -> "Tarih/yön belirsiz" })
        }.distinct().toList()
    }
}

data class GroupItem(val id: String, val published: Long?, val tags: List<String>)
data class PairVote(val left: String, val right: String, val same: Boolean)
fun pairKey(a: String, b: String) = listOf(a,b).sorted().joinToString("|")
/** Complete-link reconciliation: A=B and B=C cannot imply A=C. Explicit splits win. */
fun resolveGroups(ids: List<String>, automatic: List<PairVote>, manual: List<PairVote>): List<List<String>> {
    val known = ids.toSet(); val auto = automatic.associateBy { pairKey(it.left,it.right) }; val edits=manual.associateBy { pairKey(it.left,it.right) }
    val groups=ids.sorted().map { mutableListOf(it) }.toMutableList()
    fun merge(manualPass: Boolean) {
        var changed=true
        while(changed) { changed=false
            outer@ for(i in groups.indices) for(j in i+1 until groups.size) {
                val pairs=groups[i].flatMap { a -> groups[j].map { b -> pairKey(a,b) } }
                if(pairs.any { edits[it]?.same == false }) continue
                val ok=if(manualPass) pairs.any { edits[it]?.same == true } else pairs.all { edits[it]?.same ?: (auto[it]?.same == true) }
                if(ok) { groups[i].addAll(groups[j]); groups.removeAt(j); changed=true; break@outer }
            }
        }
    }
    merge(true); merge(false); return groups.map { it.filter { id -> id in known }.sorted() }
}
fun candidates(a: GroupItem,b: GroupItem): Boolean = a.published != null && b.published != null && kotlin.math.abs(a.published-b.published) <= 36*3600000L && a.tags.intersect(b.tags.toSet()).isNotEmpty()

fun java.io.InputStream.readLimited(limit:Int):ByteArray {
    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var total=0
    while(total<limit) { val n=read(buffer,0,minOf(buffer.size,limit-total));if(n<0) break;out.write(buffer,0,n);total+=n }
    return out.toByteArray()
}

/** Keep feed text from becoming tokenizer-level chat delimiters. Natural-language instructions remain untrusted data. */
fun escapeControlTokens(text:String)=text.replace('<','＜').replace('>','＞').replace("[INST]","［INST］").replace("[/INST]","［/INST］")
