package tr.gundem

import android.content.Context
import kotlinx.serialization.json.*
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

const val MODEL_DOWNLOAD_WORK="model-download"
private val modelDownloadLock=Mutex()
data class ModelSpec(
    val url:String,val size:Long,val sha256:String,val name:String,val ram:String="≈ 1–2 GB RAM",
    val id:String=sha256,val family:String="",val variant:String="",val overallScore:Double?=null,
    val scoreVersion:String="",val availabilityReason:String="",val format:String="gguf"
) {
    val downloadable:Boolean get()=format in setOf("gguf","onnx") && url.startsWith("https://") && size>0 && sha256.matches(Regex("[a-f0-9]{64}"))
    val formatLabel:String get()=if(format=="onnx") "ONNX INT8" else "Q4_K_M"
    val fileExtension:String get()=if(format=="onnx") "onnx.zip" else "gguf"
    val variantLabel:String get()=when(variant) { "distilled"->"Fine-tuned";"original"->"Orijinal";else->"" }
    val scoreLabel:String get()=overallScore?.let { "Gündem Score · %.1f / 100".format(java.util.Locale.forLanguageTag("tr"),it) } ?: "Gündem Score · Ölçülmedi"
}
// The bundled JSON is the editable catalog. First entry is the default.
fun modelCatalog(context:Context):List<ModelSpec> = kotlinx.serialization.json.Json.parseToJsonElement(context.assets.open("models.json").bufferedReader().use { it.readText() }).jsonArray.map { row ->
    val o=row.jsonObject
    val score=o["overallScore"]?.jsonPrimitive?.doubleOrNull
    require(score==null || (score.isFinite() && score in 0.0..100.0)) { "Geçersiz model puanı" }
    ModelSpec(o["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),o["size"]?.jsonPrimitive?.longOrNull?:0L,
        o["sha256"]?.jsonPrimitive?.contentOrNull.orEmpty(),o.getValue("name").jsonPrimitive.content,
        o["ram"]?.jsonPrimitive?.contentOrNull.orEmpty(),o.getValue("id").jsonPrimitive.content,
        o.getValue("family").jsonPrimitive.content,o.getValue("variant").jsonPrimitive.content,score,
        o["scoreVersion"]?.jsonPrimitive?.contentOrNull.orEmpty(),o["availabilityReason"]?.jsonPrimitive?.contentOrNull.orEmpty(),o["format"]?.jsonPrimitive?.contentOrNull?:"gguf")
}
val RECOMMENDED_MODEL=ModelSpec("https://huggingface.co/efebaykara/LFM3-1.7B-Base-Gundem-LoRA/resolve/0d3b796d033f01e4ecf33a7cc0545889647c0443/gguf/lfm3-base-distilled-Q4_K_M.gguf",1099626912L,"7f159f876a991ef5de009030de8848cba7c41914065321a4964510af891aa0e3","Gündem LFM3 1.7B Base · Fine-tuned","Bellek ihtiyacı cihaz ve bağlama göre değişir.","lfm3-base:distilled","LFM3 1.7B Base","distilled",78.13429130523672,"gundem-score-v1")
/** Streaming, bounded transfer. A stopped process keeps .part; identity is pinned by size + SHA. */
object ModelTransfer {
    suspend fun download(spec:ModelSpec,file:File,progress:suspend (Long)->Unit,onResponse:(Int,Long)->Unit={_,_->},open:(URL)->HttpURLConnection={ it.openConnection() as HttpURLConnection }):File {
        require(spec.downloadable) { spec.availabilityReason.ifBlank { "Bu model için indirilebilir mobil dosya yok" } }
        if(file.length()>spec.size) file.delete()
        if(file.length()<spec.size) {
            var target=spec.url;var finished=false
            for(redirect in 0..5) {
                val uri=URI(target);require(uri.scheme=="https" && !uri.host.isNullOrBlank()) { "Model indirmesi HTTPS gerektirir" }
                val offset=file.length()
                val connection=open(URL(target)).apply { connectTimeout=15000;readTimeout=20000;instanceFollowRedirects=false;setRequestProperty("User-Agent","Gundem/0.2");setRequestProperty("Accept-Encoding","identity");if(offset>0) setRequestProperty("Range","bytes=$offset-") }
                try {
                    val code=connection.responseCode
                    if(code in listOf(301,302,303,307,308)) { target=URL(URL(target),connection.getHeaderField("Location")?:error("Model yönlendirmesi eksik")).toString();continue }
                    require(code==200 || code==206) { "Model indirilemedi (HTTP $code). Bağlantıyı kontrol edip tekrar deneyin." }
                    val append=code==206 && offset>0
                    if(code==206) {
                        val expected=if(append) offset else 0L
                        val range=Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(connection.getHeaderField("Content-Range")?:"")
                        require(range!=null && range.groupValues[1].toLong()==expected && range.groupValues[3].toLong()==spec.size) { "Sunucu geçersiz devam aralığı döndürdü" }
                    }
                    onResponse(code,if(append) offset else 0L)
                    var count=if(append) offset else 0L
                    progress(count)
                    connection.inputStream.use { input -> FileOutputStream(file,append).use { output ->
                        val buffer=ByteArray(128*1024);var last=0L
                        while(true) {
                            currentCoroutineContext().ensureActive()
                            val read=input.read(buffer);if(read<0) break
                            currentCoroutineContext().ensureActive()
                            count+=read
                            if(count>spec.size) { file.delete();error("Model beklenen boyuttan büyük; dosya reddedildi") }
                            output.write(buffer,0,read)
                            val now=System.nanoTime();if(now-last>500_000_000L) {progress(count);last=now}
                        }
                    } }
                    progress(count);require(count==spec.size) { "İndirme yarım kaldı. Tekrar indir ile kaldığı yerden devam edebilirsiniz." }
                    finished=true;break
                } finally { connection.disconnect() }
            }
            check(finished) { "Model indirmesinde çok fazla yönlendirme" }
        }
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer=ByteArray(1024*1024);while(true) {currentCoroutineContext().ensureActive();val read=input.read(buffer);if(read<0) break;digest.update(buffer,0,read)} }
        val actual=digest.digest().joinToString("") { "%02x".format(it) }
        if(file.length()!=spec.size || actual!=spec.sha256) { file.delete();error("Model SHA-256 doğrulaması başarısız; dosya silindi. Tekrar indirin.") }
        return file
    }
}
class ModelDownloadWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    @Volatile private var installing=false
    override suspend fun doWork():Result=withContext(Dispatchers.IO) { modelDownloadLock.withLock {
        val app=applicationContext as GundemApp;val catalog=modelCatalog(app);val spec=catalog.firstOrNull { it.downloadable && it.sha256==inputData.getString("model") } ?: catalog.first { it.downloadable }
        val directory=File(app.filesDir,"models").apply { mkdirs() }
        val finalFile=File(directory,"${spec.sha256}.${spec.fileExtension}")
        val part=if(finalFile.isFile) finalFile else File(directory,"${spec.sha256}.part")
        var resumedFrom=0L;var httpStatus=0
        val start=System.nanoTime();app.logs.add("MODEL","İndirme başladı",spec.name)
        val watcher=launch(start=CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { if(installing) app.model.cancel() } }
        try {
            setForeground(downloadForeground(app,id,part.length(),spec.size))
            require(directory.usableSpace+part.length()>spec.size*(if(spec.format=="onnx") 3 else 1)+100L*1024*1024) { "Model için yeterli boş alan yok" }
            setProgress(workDataOf("label" to "İndiriliyor", "bytes" to part.length(),"total" to spec.size))
            ModelTransfer.download(spec,part,progress={ count -> setForeground(downloadForeground(app,id,count,spec.size));setProgress(workDataOf("label" to "İndiriliyor", "bytes" to count,"total" to spec.size)) },onResponse={code,offset->httpStatus=code;resumedFrom=offset;app.logs.add("MODEL","HTTP yanıtı","kod=$code, devam=$offset")})
            currentCoroutineContext().ensureActive()
            app.settings.update { it.copy(paused=true) };app.model.cancel();WorkManager.getInstance(app).cancelUniqueWork("analysis").result.get()
            setProgress(workDataOf("label" to "Model yüklenerek doğrulanıyor", "bytes" to spec.size,"total" to spec.size))
            installing=true
            val(path,fingerprint,name)=app.model.install(part,spec.sha256,spec.name,spec.format)
            currentCoroutineContext().ensureActive()
            app.settings.update { it.copy(modelPath=path,modelHash=fingerprint,modelName=name,paused=true) }
            app.logs.add("MODEL","Model hazır",spec.name,(System.nanoTime()-start)/1_000_000)
            Result.success(workDataOf("label" to "Model indirildi ve hazır. Başlat ile analizi açın.","resumedFrom" to resumedFrom,"httpStatus" to httpStatus))
        } catch(e:CancellationException) { app.logs.add("MODEL","İndirme durduruldu",spec.name);throw e }
        catch(e:Exception) { app.logs.add("MODEL","İndirme hatası",e.message.orEmpty());Result.failure(workDataOf("error" to (e.message?:"Model indirilemedi"))) }
        finally { installing=false;watcher.cancel() }
    } }
}
fun downloadModel(context:Context,spec:ModelSpec=RECOMMENDED_MODEL) {
    require(spec.downloadable) { spec.availabilityReason.ifBlank { "Bu model için indirilebilir mobil dosya yok" } }
    WorkManager.getInstance(context).enqueueUniqueWork(MODEL_DOWNLOAD_WORK,ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<ModelDownloadWorker>().setInputData(workDataOf("model" to spec.sha256)).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
}
