package tr.gundem

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

data class ScreenState(val articles:List<Article> = emptyList(),val sources:List<Source> = emptyList(),val pairs:List<StoredPair> = emptyList(),val settings:Settings=Settings())
class NewsViewModel(application:Application):AndroidViewModel(application) {
    val app=application as GundemApp
    val dao=app.db.news()
    val state=combine(dao.observeArticles(),dao.observeSources(),dao.observePairs(),app.settings.flow) { a,s,p,w-> ScreenState(a,s,p,w) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),ScreenState())
    val message=MutableStateFlow<String?>(null)
    val busy=MutableStateFlow(false)
    val xResults=MutableStateFlow<List<XProfile>>(emptyList())
    val xSearching=MutableStateFlow(false)
    private var searchJob:Job?=null
    fun searchX(query:String) {
        searchJob?.cancel();searchJob=viewModelScope.launch(Dispatchers.IO) {
            xSearching.value=true;xResults.value=emptyList()
            try {
                val base=app.settings.current().nitterBase.ifBlank { DEFAULT_X_SERVER }
                val found=XDirectory(base).search(query.trim());ensureActive();xResults.value=found
                if(found.isEmpty()) message.value="Arama servisi sonuç döndürmedi. Tam @kullanıcı_adı ile deneyin."
                app.logs.add("X","Hesap araması","sorgu=$query, sonuç=${found.size}")
            } catch(e:CancellationException) { throw e } catch(e:Exception) { app.logs.add("X","Arama hatası",e.message.orEmpty());message.value=e.message } finally { xSearching.value=false }
        }
    }
    fun exportLogs(uri:Uri)=task { app.contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { it.write(app.logs.text()) };message.value="Loglar dışa aktarıldı" }
    fun shareLogs()=task {
        val dir=java.io.File(app.cacheDir,"shared").apply { mkdirs() };val file=java.io.File(dir,"gundem-loglar.jsonl");file.writeText(app.logs.text())
        val uri=androidx.core.content.FileProvider.getUriForFile(app,"${app.packageName}.files",file)
        val intent=android.content.Intent(android.content.Intent.ACTION_SEND).apply { type="text/plain";putExtra(android.content.Intent.EXTRA_STREAM,uri);addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        withContext(Dispatchers.Main) { app.startActivity(android.content.Intent.createChooser(intent,"Logları paylaş").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    val analysisProgress=WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow("analysis").map { infos -> infos.firstOrNull { !it.state.isFinished } }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    val modelStatus=app.model.status
    val modelDownload=WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(MODEL_DOWNLOAD_WORK).map { infos -> infos.firstOrNull { !it.state.isFinished } ?: infos.firstOrNull() }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    fun download(spec:ModelSpec=RECOMMENDED_MODEL) { downloadModel(app,spec) }
    fun cancelDownload() { WorkManager.getInstance(app).cancelUniqueWork(MODEL_DOWNLOAD_WORK) }
    fun addX(input:String,name:String)=task { val source=xSource(input,name);require(dao.sources().none { it.id==source.id }) { "Bu hesap zaten listede" };dao.insertSource(source);message.value="Hesap eklendi";refresh(app) }
    fun removeX(source:Source)=task { dao.deleteXSource(source.id) }
    fun nitter(base:String)=task { if(base.isNotBlank()) nitterFeed(base,"test");app.settings.update { it.copy(nitterBase=base.trim().trimEnd('/')) };message.value="X bağlantı ayarı kaydedildi" }

    fun task(block:suspend ()->Unit) { viewModelScope.launch(Dispatchers.IO) { try { block() } catch(e:CancellationException) { throw e } catch(e:Exception) { message.value=e.message ?: "İşlem tamamlanamadı" } } }
    fun refresh() { refresh(app);message.value="Yenileme kuyruğa alındı" }
    fun resume()=task { resumeAnalysis(app) }
    fun pause()=task { pauseAnalysis(app);app.logs.add("AI","Kullanıcı duraklattı") }
    fun retry()=task {
        // Explicit retry invalidates incomplete cache entries by removing through REPLACE with fresh key below.
        dao.articles().filter { it.state in listOf("HATA","EKSIK") }.forEach { a -> dao.deleteCache(analysisCacheKey(a,app.settings.current())) }
        dao.retryFailed();app.settings.update { it.copy(paused=false) };enqueueAnalysis(app)
    }
    fun source(source:Source)=task { require(source.priority in 0.5..1.5);if(source.endpoint.isNotBlank()) canonicalUrl(source.endpoint);dao.configureSource(source.id,source.enabled,source.priority,source.endpoint) }
    fun read(a:Article)=task { dao.read(a.id,!a.read) }
    fun markRead(group:List<Article>,value:Boolean)=task { group.forEach { dao.read(it.id,value) } }
    fun save(a:Article)=task { dao.save(a.id,!a.saved) }
    fun hide(a:Article)=task { dao.hide(a.id,!a.hidden) }
    fun correct(a:Article,value:Analysis)=task { require(value.complete);dao.correct(a.id,value.json()) }
    fun clearCorrection(a:Article)=task { dao.correct(a.id,null) }
    fun split(group:List<Article>)=task { for(i in group.indices) for(j in i+1 until group.size) pair(group[i].id,group[j].id,false) }
    fun merge(a:List<Article>,b:List<Article>)=task { for(x in a) for(y in b) if(x.id!=y.id) pair(x.id,y.id,true) }
    private suspend fun pair(a:String,b:String,same:Boolean)=dao.pair(StoredPair(pairKey(a,b),a,b,same,true,"user"))
    fun settings(settings:Settings)=task { settings.weights.validated();app.settings.update { settings } }
    fun importModel(uri:Uri)=task {
        busy.value=true
        try {
            app.settings.update { it.copy(paused=true) };app.model.cancel();WorkManager.getInstance(app).cancelUniqueWork("analysis").result.get()
            val(path,fingerprint,name)=app.model.import(uri)
            app.settings.update { it.copy(modelPath=path,modelHash=fingerprint,modelName=name,paused=true) }
            message.value="Model doğrulandı. Analizi Başlat ile çalıştırabilirsiniz."
        } finally { busy.value=false }
    }
    fun exportSettings(uri:Uri)=task {
        val sources=dao.sources()
        val obj=buildJsonObject {
            put("settings",Json.parseToJsonElement(app.settings.encode(app.settings.current(),true)))
            put("sources",JsonArray(sources.map { s -> buildJsonObject { put("id",s.id);put("name",s.name);put("kind",s.kind);put("enabled",s.enabled);put("priority",s.priority);put("endpoint",s.endpoint) } }))
        }
        app.contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { it.write(obj.toString()) };message.value="Ayarlar dışa aktarıldı (model dosyası dahil değil)"
    }
    fun importSettings(uri:Uri)=task {
        val bytes=app.contentResolver.openInputStream(uri)!!.use { it.readLimited(256*1024+1) };require(bytes.size<=256*1024) { "Ayar dosyası çok büyük" }
        val obj=Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        val imported=app.settings.decode(obj.getValue("settings").toString());val existing=dao.sources().associateBy { it.id }
        val sources=obj.getValue("sources").jsonArray.map { x ->
            val o=x.jsonObject;val id=o.getValue("id").jsonPrimitive.content
            val base=existing[id] ?: if(id.startsWith("x:")) xSource(xHandle(id.removePrefix("x:")),o["name"]?.jsonPrimitive?.content?:"").also { require(it.id==id) } else error("Bilinmeyen kaynak")
            val weight=o.getValue("priority").jsonPrimitive.double;require(weight in 0.5..1.5)
            val endpoint=o.getValue("endpoint").jsonPrimitive.content;if(endpoint.isNotBlank()) canonicalUrl(endpoint)
            base.copy(priority=weight,enabled=o.getValue("enabled").jsonPrimitive.boolean,endpoint=endpoint)
        }
        if(imported.nitterBase.isNotBlank()) nitterFeed(imported.nitterBase,"test")
        require(sources.map { it.id }.distinct().size==sources.size)
        app.settings.update { current -> imported.copy(modelPath=current.modelPath,modelHash=current.modelHash,modelName=current.modelName,paused=true,xDefaultsSeeded=true) }
        dao.restoreSources(sources);message.value="Ayarlar içe aktarıldı; analiz duraklatıldı"
    }
}
