package tr.gundem

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class GundemApp:Application() {
    val db by lazy { Room.databaseBuilder(this,NewsDatabase::class.java,"gundem.db").build() }
    val settings by lazy { SettingsStore(this) }
    val logs by lazy { EventLog(this) }
    val model by lazy { LocalModel(this) }
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            seedSources(this@GundemApp)
            schedule(this@GundemApp)
        }
    }
    override fun onTrimMemory(level:Int) { super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_CRITICAL && level!=TRIM_MEMORY_UI_HIDDEN) CoroutineScope(Dispatchers.IO).launch { model.release() } }
}
fun schedule(context:Context) {
    val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
    val request=PeriodicWorkRequestBuilder<RefreshWorker>(2,TimeUnit.HOURS).setConstraints(constraints).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("rss-periodic",ExistingPeriodicWorkPolicy.KEEP,request)
}
fun refresh(context:Context) { WorkManager.getInstance(context).enqueueUniqueWork("rss-manual",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()) }
fun enqueueAnalysis(context:Context) {
    WorkManager.getInstance(context).enqueueUniqueWork("analysis",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<AnalysisWorker>().setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).setBackoffCriteria(BackoffPolicy.LINEAR,10,TimeUnit.SECONDS).build())
}
class RefreshWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        val app=applicationContext as GundemApp;val dao=app.db.news()
        seedSources(app)
        for(source in dao.sources().filter { it.enabled }) {
            ensureActive()
            try {
                val isX=source.kind in listOf("X_RSS","X_ACCOUNT")
                val adapter=if(isX) XFeedAdapter() else RssAdapter()
                val configured=if(isX) resolveXSource(source,app.settings.current().nitterBase) else source
                val start=System.nanoTime();app.logs.add("KAYNAK","İstek başladı","${source.name} · ${java.net.URI(configured.endpoint).host}")
                val fetched=adapter.fetch(configured);fetched.forEach { dao.ingest(it) }
                app.logs.add("KAYNAK","İstek tamamlandı","${source.name}: ${fetched.size} haber",(System.nanoTime()-start)/1_000_000)
                dao.sourceResult(source.id,System.currentTimeMillis(),null)
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { app.logs.add("KAYNAK","İstek hatası","${source.name}: ${e.message}");dao.sourceResult(source.id,System.currentTimeMillis(),e.message?.take(300) ?: "Kaynak alınamadı") }
        }
        if(!app.settings.current().paused) enqueueAnalysis(app)
        Result.success()
    }
}
class AnalysisWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        val app=applicationContext as GundemApp;val dao=app.db.news();val settings=app.settings.current()
        if(settings.paused || settings.modelPath.isBlank()) return@withContext Result.success()
        setForeground(analysisForeground(app,0,1))
        app.logs.add("AI","Kuyruk başladı","deneme=$runAttemptCount, model=${settings.modelName}, sha=${settings.modelHash.take(12)}, prompt=$PROMPT_VERSION")
        val thermal=if(android.os.Build.VERSION.SDK_INT>=29) app.getSystemService(android.os.PowerManager::class.java).currentThermalStatus.toString() else "bilinmiyor"
        app.logs.add("AI","Çalışma ortamı","sürüm=${BuildConfig.VERSION_NAME}, cihaz=${android.os.Build.MODEL}, ABI=${android.os.Build.SUPPORTED_ABIS.joinToString()}, CPU=${Runtime.getRuntime().availableProcessors()}, termal=$thermal")
        dao.recover()
        val pending=newestFirst(dao.articles().filter { needsAnalysis(it,settings) })
        if(pending.isEmpty()) {
            app.logs.add("AI","Kuyruk boş","Başlık analizi bekleyen haber yok")
            return@withContext Result.success()
        }
        val completed=java.util.concurrent.atomic.AtomicBoolean(false)
        val abortWatcher=launch { try { awaitCancellation() } finally { if(!completed.get()) app.model.cancel() } }
        var queueFailure:String?=null
        try {
            app.model.session(settings) {
                var done=0
                val attempted=mutableSetOf<String>()
                while(true) {
                    ensureActive();if(app.settings.current().paused) return@session
                    val remaining=newestFirst(dao.articles().filter { needsAnalysis(it,settings) && it.id !in attempted })
                    val a=remaining.firstOrNull() ?: break
                    attempted+=a.id
                    val total=done+remaining.size
                    setForeground(analysisForeground(app,done,total))
                    setProgress(workDataOf("done" to done,"total" to total,"label" to "Sınıflandırma"))
                    app.logs.add("AI","Haber işleniyor",a.id)
                    ensureActive();if(app.settings.current().paused) return@session
                    val key=cacheKey(a,settings);dao.status(a.id,"CALISIYOR")
                    try {
                        val cached=dao.cache(key)
                        if(cached!=null) app.logs.add("AI","Önbellek isabeti",a.id)
                        val analysis=if(cached!=null) OutputValidator.analysis(cached.json) else app.model.classify(a)
                        ensureActive()
                        app.logs.add("AI","Analiz sonucu","${a.id}: ${analysis.json()}")
                        dao.cache(CachedAnalysis(key,analysis.json(),analysis.complete))
                        dao.analysisResult(a.id,a.contentHash,analysis.json(),key,if(analysis.complete) "TAMAM" else "EKSIK",if(analysis.complete) null else "SINIFLANDIRILAMADI / BILINMIYOR: geçersiz alanlar bir kez yeniden denendi")
                    } catch(e:CancellationException) { throw e }
                    catch(e:Exception) { ensureActive();if(app.settings.current().paused) { dao.status(a.id,"BEKLIYOR");return@session };app.logs.add("AI","Haber hatası","${a.id}: ${e.message}");dao.status(a.id,"HATA",e.message?.take(300)) }
                    done++
                }
                setProgress(workDataOf("done" to done,"total" to done,"label" to "Tamamlandı"))
            }
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) {
            ensureActive();queueFailure=e.message ?: "Model yüklenemedi"
            app.logs.add("AI","Kuyruk hatası",queueFailure!!)
            app.settings.update { it.copy(paused=true) }
        }
        finally { completed.set(true);abortWatcher.cancel();withContext(NonCancellable) { dao.recover() } }
        val more=dao.articles().any { needsAnalysis(it,settings) }
        app.logs.add("AI","Kuyruk turu tamamlandı","kalan=$more")
        if(queueFailure!=null) return@withContext Result.failure(workDataOf("error" to queueFailure))
        if(more && !app.settings.current().paused) Result.retry() else Result.success()
    }
    private fun needsAnalysis(a:Article,s:Settings)=!a.hidden && a.manualJson==null && (a.state=="BEKLIYOR" || a.state in listOf("TAMAM","EKSIK") && a.analysisKey!=cacheKey(a,s))
    private fun cacheKey(a:Article,s:Settings)=analysisCacheKey(a,s)

}

suspend fun seedSources(app:GundemApp)=seedSources(app.db.news(),app.settings)
suspend fun seedSources(dao:NewsDao,settings:SettingsStore) {
    INITIAL_SOURCES.forEach { dao.insertSource(it) }
    if(!settings.current().xDefaultsSeeded) {
        DEFAULT_X_ACCOUNTS.forEach { dao.insertSource(it) }
        settings.update { it.copy(xDefaultsSeeded=true) }
    }
}
