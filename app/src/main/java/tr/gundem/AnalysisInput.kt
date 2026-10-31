package tr.gundem

import kotlinx.serialization.json.*

/** News content sent to the model is deliberately limited to the headline. */
fun classificationInput(article:Article):String = article.title
/** Model's wire format omits repeated JSON keys; public storage/export schema stays unchanged. */
fun compactAnalysis(raw:String,completed:Boolean=true):Analysis {
    if(!completed) return Analysis()
    val a=runCatching { Json.parseToJsonElement(raw) as? JsonArray }.getOrNull()?.takeIf {it.size==5} ?: return Analysis()
    val flags=when(val value=a[0]) {
        is JsonArray -> value
        is JsonObject -> {
            if(value.keys!=TOPICS.take(7).toSet()) return Analysis()
            JsonArray(TOPICS.take(7).map {value.getValue(it)})
        }
        else -> return Analysis()
    }
    val tags=if(flags.size==7 && flags.all { it is JsonPrimitive && !it.isString && it.content in listOf("0","1") }) {
        TOPICS.take(7).filterIndexed { index,_ -> flags[index].jsonPrimitive.int==1 }.ifEmpty {listOf("DIGER")}.let {JsonArray(it.map(::JsonPrimitive))}
    } else if(flags.all {it is JsonPrimitive && it.isString}) flags else return Analysis()
    return OutputValidator.analysis(buildJsonObject {
        put("etiketler",tags);put("tur",a[1]);put("etki",a[2]);put("ciddiyet",a[3]);put("aciliyet",a[4])
    }.toString()).let { it.copy(tags=it.tags?.sortedBy(TOPICS::indexOf)) }
}

fun analysisCacheKey(a:Article,s:Settings)=hash("${hash(a.title)}|${s.modelHash}|$PROMPT_VERSION")
fun queueCounts(articles:List<Article>,settings:Settings):Map<String,Int> = articles.filterNot { it.hidden }.groupingBy { a ->
    when {
        a.manualJson!=null -> if(a.analysis().complete) "TAMAM" else "EKSIK"
        a.state=="CALISIYOR" -> "BEKLIYOR"
        a.state in listOf("TAMAM","EKSIK") && a.analysisKey!=analysisCacheKey(a,settings) -> "BEKLIYOR"
        else -> a.state
    }
}.eachCount()

/** Known publication dates first; fetched time only breaks ties and orders undated news. */
fun newestFirst(articles:List<Article>):List<Article> = articles.sortedWith(
    compareByDescending<Article> { it.published ?: Long.MIN_VALUE }.thenByDescending { it.fetched }.thenBy { it.id }
)
