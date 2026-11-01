package tr.gundem

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest

object NativeLlama {
    init { System.loadLibrary("gundem") }
    external fun load(path:String)
    external fun generate(system:String,user:String,grammar:String,maxTokens:Int):String
    external fun stats():String
    external fun architecture():String
    external fun loadProgress():Int
    external fun close()
    external fun cancel()
}
class LocalModel(private val context:Context) {
    private val mutex=Mutex()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var idle:Job?=null
    private var loaded=""
    private var qwen=false
    @Volatile private var onnx:GptOnnxRuntime?=null
    val status=MutableStateFlow("Model bellekte değil")
    private val logStore by lazy { (context.applicationContext as? GundemApp)?.logs ?: EventLog(context) }
    private fun log(event:String,detail:String="",ms:Long?=null)=logStore.add("AI",event,detail,ms)
    private fun asset(name:String)=context.assets.open("prompts/$name").bufferedReader().use { it.readText() }
    suspend fun <T> session(settings:Settings,block:suspend ()->T):T=withContext(Dispatchers.IO) {
        mutex.withLock {
            idle?.cancel()
            try {
                require(settings.modelPath.isNotBlank() && File(settings.modelPath).isFile) { "Model seçilmedi" }
                if(loaded!=settings.modelHash) {
                    loadIntoMemory(settings.modelPath,settings.modelHash,settings.modelName)
                } else log("Bellekteki model kullanılıyor",settings.modelName)
                status.value="Yerel analiz çalışıyor";block()
            } finally {
                if(loaded.isNotBlank()) {
                    status.value="Model bellekte hazır"
                    idle=scope.launch { delay(30*60*1000L);release() }
                }
            }
        }
    }
    suspend fun release()=mutex.withLock { onnx?.close();onnx=null;NativeLlama.close();loaded="";status.value="Model bellekte değil" }
    fun cancel() { onnx?.cancelled=true;NativeLlama.cancel() }
    private fun generate(system:String,user:String,grammar:String,max:Int=160):Generation {
        val start=System.nanoTime();log("Çağrı başladı","girdi=${user.length}, sistem=${system.length}, sınır=$max")
        val result=try { onnx?.generate(system,escapeControlTokens(user),grammar,max) ?: NativeLlama.generate(system,escapeControlTokens(user),grammar,max) } catch(e:Exception) { log("Çağrı hatası",e.message.orEmpty(),(System.nanoTime()-start)/1_000_000);throw e }
        log("Çağrı tamamlandı",(onnx?.stats?:NativeLlama.stats())+"\n"+result.take(1000),(System.nanoTime()-start)/1_000_000)
        return Generation(result.substringAfter('\n'),result.startsWith("OK\n"))
    }
    suspend fun classify(article:Article):Analysis {
        val system=asset(if(qwen) "classify-qwen.txt" else "classify-json.txt")
        log("Girdi hazırlandı","${article.id}: yalnız başlık, karakter=${article.title.length}")
        val input=classificationInput(article)
        return analyzeWithRepair { field ->
            currentCoroutineContext().ensureActive()
            if(field!=null) log("Alan yeniden deneniyor",field)
            val grammar=if(field==null) asset("classify.gbnf") else repairGrammar(field)
            val instruction=if(field==null) "" else "Recheck only '$field'. Return one-key JSON.\n"
            generate(system,instruction+input,grammar)
        }
    }
    private fun repairGrammar(field:String):String {
        require(field in FIELDS)
        val rules=asset("classify.gbnf").lineSequence().filterNot { it.startsWith("root ::=") }.joinToString("\n")
        val rule=when(field) { "etiketler"->"tags";"tur"->"kind";else->"score" }
        return "root ::= \"{\" ws \"\\\"$field\\\"\" ws \":\" ws $rule ws \"}\" ws\n$rules"
    }
    suspend fun import(uri:Uri):Triple<String,String,String> = withContext(Dispatchers.IO) {
        release()
        val directory=File(context.filesDir,"models").apply { mkdirs() }
        val temp=File.createTempFile("import-",".part",directory)
        try {
            var format="gguf"
            val digest=MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { out ->
                val header=ByteArray(4);require(input.read(header)==4) { "Model dosyası eksik" }
                format=when {header.contentEquals("GGUF".toByteArray())->"gguf";header.contentEquals(byteArrayOf(0x50,0x4b,3,4))->"onnx";else->error("GGUF veya desteklenen ONNX paketi seçin")}
                digest.update(header);out.write(header)
                val buffer=ByteArray(1024*1024);var total=4L
                while(true) { currentCoroutineContext().ensureActive();val count=input.read(buffer);if(count<0) break;total+=count;require(total<=3L*1024*1024*1024) { "Bu cihaz profili için en fazla 3 GiB model içe aktarılır" };out.write(buffer,0,count);digest.update(buffer,0,count) }
            } }
            val fingerprint=digest.digest().joinToString("") { "%02x".format(it) }
            val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null }?:"GGUF"
            install(temp,fingerprint,name,format)
        } finally { temp.delete() }
    }
    suspend fun install(file:File,fingerprint:String,name:String,format:String="gguf"):Triple<String,String,String> = withContext(Dispatchers.IO) {
        require(file.parentFile?.canonicalFile==File(context.filesDir,"models").canonicalFile) { "Model özel model dizininde olmalı" }
        mutex.withLock {
            idle?.cancel()
            require(format in setOf("gguf","onnx")) { "Model biçimi desteklenmiyor" }
            val target=File(file.parentFile,"$fingerprint.${if(format=="onnx") "onnx.zip" else "gguf"}")
            if(file.canonicalFile!=target.canonicalFile) {
                if(target.exists()) file.delete() else check(file.renameTo(target)) { "Model dosyası taşınamadı" }
            }
            if(loaded!=fingerprint) loadIntoMemory(target.path,fingerprint,name)
            idle=scope.launch { delay(30*60*1000L);release() }
            Triple(target.path,fingerprint,name)
        }
    }
    private suspend fun loadIntoMemory(path:String,fingerprint:String,name:String)=coroutineScope {
        loaded="";status.value="Model yükleniyor…"
        val start=System.nanoTime()
        val memory=android.app.ActivityManager.MemoryInfo()
        context.getSystemService(android.app.ActivityManager::class.java)?.getMemoryInfo(memory)
        log("Yükleme başladı","$name, dosya_MB=${File(path).length()/1_000_000}, boş_RAM_MB=${memory.availMem/1_000_000}, düşük_bellek=${memory.lowMemory}")
        val finished=java.util.concurrent.atomic.AtomicBoolean(false)
        val monitor=launch(Dispatchers.IO,start=CoroutineStart.UNDISPATCHED) {
            var previous=Int.MIN_VALUE
            var lastLog=0L
            try {
                while(true) {
                    delay(500)
                    val progress=NativeLlama.loadProgress()
                    val label=when(progress) { -4 -> "CPU motoru hazırlanıyor…"; -2 -> "Model bağlamı hazırlanıyor…"; in 0..100 -> "Model belleğe yükleniyor: %$progress"; else -> "Model dosyası açılıyor…" }
                    status.value=label
                    val elapsed=(System.nanoTime()-start)/1_000_000
                    if(progress!=previous || elapsed-lastLog>=10_000) {
                        log("Yükleme aşaması",label,elapsed);previous=progress;lastLog=elapsed
                    }
                    if(elapsed>=180_000) {
                        status.value="Model yükleme süresi aşıldı; durduruluyor…"
                        log("Yükleme zaman aşımı",label,elapsed)
                        cancel()
                        break
                    }
                }
            } finally { if(!finished.get()) cancel() }
        }
        var terminalStatus="Model bellekte değil"
        try {
            onnx?.close();onnx=null;NativeLlama.close()
            val zipped=File(path).inputStream().use { it.read()==0x50 && it.read()==0x4b }
            if(zipped) onnx=GptOnnxRuntime(context,File(path),fingerprint) else NativeLlama.load(path)
            currentCoroutineContext().ensureActive()
            loaded=fingerprint;qwen=onnx==null && NativeLlama.architecture().startsWith("qwen")
            log("Yükleme tamamlandı",name+" · "+(if(onnx!=null) "ONNX CPU" else NativeLlama.stats()),(System.nanoTime()-start)/1_000_000)
            terminalStatus="Model bellekte hazır"
        } catch(e:Exception) {
            onnx?.close();onnx=null;NativeLlama.close();loaded=""
            terminalStatus=if(e is CancellationException) "Model yüklemesi durduruldu" else "Model yüklenemedi: ${e.message}"
            log("Yükleme başarısız",e.message.orEmpty(),(System.nanoTime()-start)/1_000_000)
            throw e
        } finally {
            finished.set(true)
            withContext(NonCancellable) { monitor.cancelAndJoin() }
            status.value=terminalStatus
        }
    }
}
