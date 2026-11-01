package tr.gundem

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.LongBuffer
import java.util.zip.ZipFile
import java.security.MessageDigest

object NativeTokens {
    init { System.loadLibrary("gundem") }
    external fun load(path:String)
    external fun close()
    external fun encode(bytes:ByteArray):IntArray
    external fun decode(tokens:IntArray):ByteArray
    external fun grammar(source:String)
    external fun sample(logits:FloatArray):Int
    external fun end(token:Int):Boolean
}

/** Explicit GDN2/attention state; CPU-only ONNX runtime for the GPT-X3 pair. */
class GptOnnxRuntime(context:Context,bundle:File,fingerprint:String):AutoCloseable {
    @Volatile var cancelled=false
    var stats="";private set
    private val environment=OrtEnvironment.getEnvironment()
    private val session:OrtSession
    private val stateShapes=linkedMapOf<String,LongArray>()

    init {
        require(fingerprint.matches(Regex("[a-f0-9]{64}"))) { "Geçersiz model kimliği" }
        val folder=File(context.filesDir,"models/$fingerprint-runtime")
        ZipFile(bundle).use { zip ->
            val descriptor=zip.getEntry("bundle.json")?:error("ONNX model tanımı eksik")
            require(descriptor.size in 1..131072) { "Geçersiz ONNX model tanımı" }
            val info=Json.parseToJsonElement(zip.getInputStream(descriptor).bufferedReader().use { it.readText() }).jsonObject
            require(info["backend"]?.jsonPrimitive?.content=="gptx3-onnx-v1" && info["version"]?.jsonPrimitive?.int==1) { "ONNX model biçimi desteklenmiyor" }
            val config=info.getValue("config").jsonObject
            require(config.getValue("hidden_size").jsonPrimitive.int==576 && config.getValue("num_hidden_layers").jsonPrimitive.int==24 &&
                config.getValue("gdn_head_dim").jsonPrimitive.int==64 && config.getValue("num_key_value_heads").jsonPrimitive.int==3 &&
                config.getValue("head_dim").jsonPrimitive.int==64 && config.getValue("conv_size").jsonPrimitive.int==4 &&
                config.getValue("gdn_layers_per_gqa").jsonPrimitive.int==3 && config.getValue("num_attention_heads").jsonPrimitive.int==9 &&
                config.getValue("vocab_size").jsonPrimitive.int==65536 && config.getValue("bos_token_id").jsonPrimitive.int==1 &&
                config.getValue("eos_token_id").jsonPrimitive.int==2) { "ONNX model boyutları desteklenmiyor" }
            folder.mkdirs()
            for(name in listOf("model.onnx","tokenizer.gguf")) {
                val entry=zip.getEntry(name)?:error("ONNX paket dosyası eksik: $name")
                require(entry.size in 1..(1024L*1024*1024)) { "ONNX paket boyutu geçersiz" }
                val target=File(folder,name)
                if(target.length()!=entry.size) {
                    val temp=File(folder,"$name.part")
                    try {
                        zip.getInputStream(entry).use { input -> temp.outputStream().use { output ->
                            val buffer=ByteArray(1024*1024);var count=0L
                            while(true) {val n=input.read(buffer);if(n<0)break;count+=n;require(count<=entry.size);output.write(buffer,0,n)}
                            require(count==entry.size)
                        } }
                        check(temp.renameTo(target)) { "ONNX dosyası hazırlanamadı" }
                    } finally { temp.delete() }
                }
                val expected=info.getValue(if(name=="model.onnx") "model_sha256" else "tokenizer_sha256").jsonPrimitive.content
                val digest=MessageDigest.getInstance("SHA-256")
                target.inputStream().use { input -> val buffer=ByteArray(1024*1024);while(true) {val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)} }
                if(digest.digest().joinToString("") { "%02x".format(it) }!=expected) {target.delete();error("ONNX paket dosyasının SHA-256 doğrulaması başarısız")}
            }
        }
        for(layer in 0 until 24) {
            if((layer+1)%4==0) {
                stateShapes["$layer.key"]=longArrayOf(1,3,0,64)
                stateShapes["$layer.value"]=longArrayOf(1,3,0,64)
            } else {
                stateShapes["$layer.recurrent"]=longArrayOf(1,9,64,64)
                for(channel in listOf("q","k","v")) stateShapes["$layer.conv_$channel"]=longArrayOf(1,576,3)
            }
        }
        try {
            NativeTokens.load(File(folder,"tokenizer.gguf").path)
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1)
                session=environment.createSession(File(folder,"model.onnx").path,options)
            }
            val expected=stateShapes.keys.map { "state.$it" }.toSet()+setOf("input_ids","position")
            if(session.inputNames!=expected) {session.close();error("ONNX girdileri beklenen modelle uyuşmuyor")}
        } catch(e:Exception) { NativeTokens.close();throw e }
    }

    fun generate(system:String,user:String,grammar:String,maxTokens:Int):String {
        cancelled=false
        val started=System.nanoTime()
        val prompt="<|startoftext|>System: $system\nUser: $user\nAssistant: "
        val ids=NativeTokens.encode(prompt.toByteArray(Charsets.UTF_8))
        require(ids.size+maxTokens+4<=2048) { "Girdi model bağlam sınırını aşıyor" }
        var states=stateShapes.mapValues { (_,shape) ->
            val count=shape.fold(1L) { a,b->a*b }.toInt()
            OnnxTensor.createTensor(environment,ByteBuffer.allocateDirect(count*4).order(ByteOrder.nativeOrder()).asFloatBuffer(),shape)
        }
        val initial=states.values.toList()
        var owner:OrtSession.Result?=null
        val generated=mutableListOf<Int>()
        var finished=false
        try {
            fun step(token:Int,position:Int,readLogits:Boolean):FloatArray? {
                if(cancelled) throw CancellationException("Model çalışması durduruldu")
                OnnxTensor.createTensor(environment,LongBuffer.wrap(longArrayOf(token.toLong())),longArrayOf(1,1)).use { input ->
                    OnnxTensor.createTensor(environment,LongBuffer.wrap(longArrayOf(position.toLong())),longArrayOf(1)).use { pos ->
                        val feeds=states.mapKeys { "state.${it.key}" }.toMutableMap()
                        feeds["input_ids"]=input;feeds["position"]=pos
                        val result=session.run(feeds)
                        try {
                            val next=stateShapes.keys.associateWith { result.get("new_state.$it").get() as OnnxTensor }
                            owner?.close();owner=result;states=next
                            return if(readLogits) {
                                val logits=FloatArray(65536)
                                (result.get("logits").get() as OnnxTensor).floatBuffer.get(logits)
                                logits
                            } else null
                        } catch(e:Exception) {result.close();throw e}
                    }
                }
            }
            var logits:FloatArray?=null
            for((position,token) in ids.withIndex()) logits=step(token,position,position==ids.lastIndex)
            NativeTokens.grammar(grammar)
            for(index in 0 until maxTokens) {
                if(cancelled) throw CancellationException("Model çalışması durduruldu")
                val token=NativeTokens.sample(requireNotNull(logits))
                if(NativeTokens.end(token)) {finished=true;break}
                generated+=token
                if(index+1<maxTokens) logits=step(token,ids.size+index,true)
            }
            stats="girdi_token=${ids.size}, çıktı_token=${generated.size}, toplam_ms=${(System.nanoTime()-started)/1_000_000}"
            return (if(finished) "OK\n" else "CUT\n")+String(NativeTokens.decode(generated.toIntArray()),Charsets.UTF_8)
        } finally {
            owner?.close();initial.forEach { it.close() }
        }
    }

    override fun close() { session.close();NativeTokens.close() }
}
