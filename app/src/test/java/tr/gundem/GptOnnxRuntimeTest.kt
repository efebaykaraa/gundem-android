package tr.gundem

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class GptOnnxRuntimeTest {
    @Test fun localModelInstallsAndClassifiesWithTheOnnxBackend()=runBlocking {
        val bundle=File(System.getProperty("gundem.onnx.bundle","/no-model"))
        assumeTrue("Requires the exported ONNX bundle and host JNI",bundle.isFile)
        val digest=MessageDigest.getInstance("SHA-256")
        bundle.inputStream().use { input -> val buffer=ByteArray(1024*1024);while(true) {val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)} }
        val hash=digest.digest().joinToString("") { "%02x".format(it) }
        val context=ApplicationProvider.getApplicationContext<Application>()
        val folder=File(context.filesDir,"models").apply { mkdirs() }
        val part=File(folder,"onnx-integration.part");bundle.copyTo(part,overwrite=true)
        val model=LocalModel(context)
        try {
            val(path,fingerprint,name)=model.install(part,hash,"GPT-X3 ONNX","onnx")
            assertTrue(path.endsWith(".onnx.zip"))
            val settings=Settings(modelPath=path,modelHash=fingerprint,modelName=name)
            model.session(settings) {
                val article=Article("onnx","test","onnx","https://example.org/news","İşçiler ücret artışı için greve başladı","",null,null,"test")
                val result=model.classify(article)
                assertTrue(result.json(),result.complete)
            }
        } finally { model.release() }
    }
    @Test fun actualBundleGeneratesConstrainedJsonAndResetsAfterCancellation() {
        val bundle=File(System.getProperty("gundem.onnx.bundle","/no-model"))
        assumeTrue("Requires the exported ONNX bundle and host JNI",bundle.isFile)
        val digest=MessageDigest.getInstance("SHA-256")
        bundle.inputStream().use { input -> val buffer=ByteArray(1024*1024);while(true) {val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)} }
        val hash=digest.digest().joinToString("") { "%02x".format(it) }
        val context=ApplicationProvider.getApplicationContext<Application>()
        val system=context.assets.open("prompts/classify-json.txt").bufferedReader().use { it.readText() }
        val grammar=context.assets.open("prompts/classify.gbnf").bufferedReader().use { it.readText() }
        GptOnnxRuntime(context,bundle,hash).use { runtime ->
            val result=runtime.generate(system,"İşçiler ücret artışı için greve başladı",grammar,160)
            assertTrue(result,result.startsWith("OK\n"))
            val fields=Json.parseToJsonElement(result.substringAfter('\n')).jsonObject
            assertEquals(setOf("etiketler","tur","etki","ciddiyet","aciliyet"),fields.keys)
            assertTrue(runtime.stats.contains("girdi_token="))
            val cancel=Thread { Thread.sleep(30);runtime.cancelled=true }
            cancel.start()
            try {runtime.generate(system,"İşçiler ücret artışı için greve başladı",grammar,160);fail("Cancellation ignored")}
            catch(_:CancellationException) { }
            finally {cancel.join()}
            assertEquals(result,runtime.generate(system,"İşçiler ücret artışı için greve başladı",grammar,160))
        }
    }
}
