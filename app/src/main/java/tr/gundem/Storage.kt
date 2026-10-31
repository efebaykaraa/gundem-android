package tr.gundem

import android.content.Context
import androidx.room.*
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

@Entity(tableName="articles", indices=[Index(value=["url"], unique=true),Index(value=["sourceId","remoteId"],unique=true)])
data class Article(
    @PrimaryKey val id: String, val sourceId: String, val remoteId: String, val url: String,
    val title: String, val intro: String, val published: Long?, val image: String?, val contentHash: String,
    val fetched: Long = System.currentTimeMillis(), val analysisJson: String = "{}", val analysisKey: String = "",
    val state: String = "BEKLIYOR", val error: String? = null, val titleOnly: Boolean = intro.isBlank(),
    val read: Boolean = false, val saved: Boolean = false, val hidden: Boolean = false,
    val manualJson: String? = null
) { fun analysis() = OutputValidator.analysis(manualJson ?: analysisJson) }
@Entity(tableName="sources")
data class Source(@PrimaryKey val id: String, val name: String, val endpoint: String, val enabled: Boolean = true, val priority: Double = 1.0, val lastAttempt: Long?=null, val lastSuccess: Long?=null, val error: String?=null, val kind: String="RSS")
@Entity(tableName="analysis_cache")
data class CachedAnalysis(@PrimaryKey val key: String, val json: String, val complete: Boolean)
@Entity(tableName="pairs")
data class StoredPair(@PrimaryKey val key: String, val left: String, val right: String, val same: Boolean, val manual: Boolean, val version: String = "")

@Dao
interface NewsDao {
    @Query("SELECT * FROM articles ORDER BY fetched DESC") fun observeArticles(): Flow<List<Article>>
    @Query("SELECT * FROM articles ORDER BY fetched DESC") suspend fun articles(): List<Article>
    @Query("SELECT * FROM articles WHERE id=:id") suspend fun article(id: String): Article?
    @Query("SELECT * FROM sources ORDER BY id") fun observeSources(): Flow<List<Source>>
    @Query("SELECT * FROM sources ORDER BY id") suspend fun sources(): List<Source>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertSource(s: Source)
    @Upsert suspend fun putSource(s: Source)
    @Query("DELETE FROM sources WHERE id=:id AND kind='X_ACCOUNT'") suspend fun deleteXSource(id:String)
    @Transaction suspend fun restoreSources(sources:List<Source>) {
        val restored=sources.map { it.id }.toSet()
        sources().filter { it.kind=="X_ACCOUNT" && it.id !in restored }.forEach { deleteXSource(it.id) }
        sources.forEach { source -> val old=sources().firstOrNull { it.id==source.id };if(old==null) insertSource(source) else configureSource(source.id,source.enabled,source.priority,source.endpoint) }
    }
    @Query("UPDATE sources SET enabled=:enabled, priority=:priority, endpoint=:endpoint WHERE id=:id") suspend fun configureSource(id: String,enabled:Boolean,priority:Double,endpoint:String)
    @Query("UPDATE sources SET lastAttempt=:at,lastSuccess=CASE WHEN :error IS NULL THEN :at ELSE lastSuccess END,error=:error WHERE id=:id") suspend fun sourceResult(id: String,at:Long,error:String?)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(a: Article): Long
    @Query("SELECT * FROM articles WHERE url=:url OR (sourceId=:source AND remoteId=:remote) LIMIT 1") suspend fun existing(url:String,source:String,remote:String): Article?
    @Query("UPDATE articles SET title=:title,intro=:intro,published=:published,image=:image,contentHash=:hash,titleOnly=:titleOnly,error=CASE WHEN contentHash!=:hash THEN NULL ELSE error END,analysisJson=CASE WHEN contentHash!=:hash THEN '{}' ELSE analysisJson END,analysisKey=CASE WHEN contentHash!=:hash THEN '' ELSE analysisKey END,state=CASE WHEN contentHash!=:hash THEN 'BEKLIYOR' ELSE state END WHERE id=:id")
    suspend fun updateContent(id:String,title:String,intro:String,published:Long?,image:String?,hash:String,titleOnly:Boolean)
    @Transaction suspend fun ingest(a: Article) {
        val old=existing(a.url,a.sourceId,a.remoteId)
        if(old==null) insert(a) else updateContent(old.id,a.title,a.intro,a.published,a.image,a.contentHash,a.titleOnly)
    }
    @Query("UPDATE articles SET analysisJson=:json,analysisKey=:key,state=:state,error=:error WHERE id=:id AND contentHash=:contentHash") suspend fun analysisResult(id:String,contentHash:String,json:String,key:String,state:String,error:String?)
    @Query("UPDATE articles SET error=:error WHERE id=:id") suspend fun notice(id:String,error:String?)
    @Query("UPDATE articles SET state=:state,error=:error WHERE id=:id") suspend fun status(id:String,state:String,error:String?=null)
    @Query("UPDATE articles SET read=:value WHERE id=:id") suspend fun read(id:String,value:Boolean)
    @Query("UPDATE articles SET saved=:value WHERE id=:id") suspend fun save(id:String,value:Boolean)
    @Query("UPDATE articles SET hidden=:value WHERE id=:id") suspend fun hide(id:String,value:Boolean)
    @Query("UPDATE articles SET manualJson=:json WHERE id=:id") suspend fun correct(id:String,json:String?)
    @Query("UPDATE articles SET state='BEKLIYOR',error=NULL WHERE state='CALISIYOR'") suspend fun recover()
    @Query("UPDATE articles SET state='BEKLIYOR',error=NULL WHERE state IN ('HATA','EKSIK')") suspend fun retryFailed()
    @Query("SELECT * FROM analysis_cache WHERE `key`=:key") suspend fun cache(key:String): CachedAnalysis?
    @Query("DELETE FROM analysis_cache WHERE `key`=:key") suspend fun deleteCache(key:String)
    @Upsert suspend fun cache(c:CachedAnalysis)
    @Query("SELECT * FROM pairs") fun observePairs(): Flow<List<StoredPair>>
    @Query("SELECT * FROM pairs") suspend fun pairs(): List<StoredPair>
    @Upsert suspend fun pair(p:StoredPair)
}
@Database(entities=[Article::class,Source::class,CachedAnalysis::class,StoredPair::class],version=1,exportSchema=true)
abstract class NewsDatabase: RoomDatabase() { abstract fun news(): NewsDao }

private val Context.dataStore by preferencesDataStore("gundem_settings")
data class Settings(val weights:Weights=Weights(),val theme:String="SISTEM",val paused:Boolean=true,val modelPath:String="",val modelHash:String="",val modelName:String="Model seçilmedi",val nitterBase:String="",val xDefaultsSeeded:Boolean=false,val fontScale:Float=1f)
class SettingsStore(private val context:Context) {
    private val key=stringPreferencesKey("settings")
    val flow=context.dataStore.data.map { decode(it[key] ?: "{}") }.catch { emit(Settings()) }
    suspend fun current()=flow.first()
    suspend fun update(block:(Settings)->Settings) { context.dataStore.edit { p -> p[key]=encode(block(decode(p[key]?:"{}"))) } }
    fun encode(s:Settings,portable:Boolean=false)=buildJsonObject {
        put("fontScale",s.fontScale);put("version",1); put("theme",s.theme); put("paused",s.paused);put("nitterBase",s.nitterBase)
        if(!portable) put("xDefaultsSeeded",s.xDefaultsSeeded)
        if(!portable) { put("modelPath",s.modelPath);put("modelHash",s.modelHash);put("modelName",s.modelName) }
        put("weights",buildJsonObject {
            put("topics",buildJsonObject { s.weights.topics.forEach { (k,v)->put(k,v) } });put("types",buildJsonObject { s.weights.types.forEach { (k,v)->put(k,v) } })
            put("impact",s.weights.impact);put("urgency",s.weights.urgency);put("seriousness",JsonArray(s.weights.seriousness.map(::JsonPrimitive)))
            put("dailyPenalty",s.weights.dailyPenalty);put("halfLifeHours",s.weights.halfLifeHours);put("freshnessFloor",s.weights.freshnessFloor)
        })
    }.toString()
    fun decode(text:String):Settings {
        val o=Json.parseToJsonElement(text).jsonObject
        require(o["version"]==null || o["version"]?.jsonPrimitive?.int==1) { "Ayar sürümü desteklenmiyor" }
        val w=o["weights"]?.jsonObject
        val weights=if(w==null) Weights() else Weights(
            w.getValue("topics").jsonObject.mapValues { it.value.jsonPrimitive.double },w.getValue("types").jsonObject.mapValues { it.value.jsonPrimitive.double },
            w.getValue("impact").jsonPrimitive.double,w.getValue("urgency").jsonPrimitive.double,w.getValue("seriousness").jsonArray.map { it.jsonPrimitive.double },
            w.getValue("halfLifeHours").jsonPrimitive.double,w.getValue("freshnessFloor").jsonPrimitive.double,
            w["dailyPenalty"]?.jsonPrimitive?.double ?: 0.5
        ).validated()
        val theme=o["theme"]?.jsonPrimitive?.content?:"SISTEM";require(theme in listOf("SISTEM","ACIK","KOYU"))
        return Settings(weights,theme,o["paused"]?.jsonPrimitive?.boolean?:true,o["modelPath"]?.jsonPrimitive?.content?:"",o["modelHash"]?.jsonPrimitive?.content?:"",o["modelName"]?.jsonPrimitive?.content?:"Model seçilmedi",o["nitterBase"]?.jsonPrimitive?.content?:"",o["xDefaultsSeeded"]?.jsonPrimitive?.boolean?:false,(o["fontScale"]?.jsonPrimitive?.float?:1f).coerceIn(0.85f,1.4f))
    }
}

/** Historical automatic pair votes are ignored; only explicit user choices group cards. */
fun articleGroups(articles:List<Article>,pairs:List<StoredPair>):List<List<Article>> {
    val all=articles.associateBy { it.id }
    val manual=pairs.filter { it.manual }.map { PairVote(it.left,it.right,it.same) }
    return resolveGroups(all.keys.toList(),emptyList(),manual).map { ids->ids.mapNotNull { all[it] } }
}
