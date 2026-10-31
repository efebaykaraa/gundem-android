package tr.gundem

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class NativeClassificationTest {
    @Test fun failedLoadDoesNotClaimReadyAndValidInstallIsReused()=runBlocking {
        val path=System.getenv("GUNDEM_TEST_MODEL")
        Assume.assumeTrue(!path.isNullOrBlank() && !System.getenv("GUNDEM_NATIVE_LIB_DIR").isNullOrBlank())
        val context=ApplicationProvider.getApplicationContext<Context>()
        val model=LocalModel(context)
        val directory=java.io.File(context.filesDir,"models").apply { mkdirs() }
        val invalid=java.io.File(directory,"invalid-test.gguf").apply { writeText("broken") }
        val source=java.io.File(path!!)
        val valid=java.io.File(directory,"retained-test.gguf")
        try {
            try { model.session(Settings(modelPath=invalid.path,modelHash="invalid")) { fail("Invalid GGUF accepted") };fail("No load error") }
            catch(e:IllegalStateException) { assertTrue(model.status.value,model.status.value.startsWith("Model yüklenemedi:")) }
            source.copyTo(valid,overwrite=true)
            val installed=model.install(valid,"retained-test","Test model")
            assertEquals("Model bellekte hazır",model.status.value)
            assertEquals(101,NativeLlama.loadProgress())
            model.session(Settings(modelPath=installed.first,modelHash=installed.second)) {
                assertEquals("Yerel analiz çalışıyor",model.status.value)
            }
            val log=EventLog(context).text()
            assertTrue(log,log.contains("Bellekteki model kullanılıyor"))
            assertEquals("Model bellekte hazır",model.status.value)
        } finally { model.release();invalid.delete();valid.delete();java.io.File(directory,"retained-test.gguf").delete() }
    }
    @Test fun sameHeadlineUsesLessThanHalfPreviousInput()=runBlocking {
        val path=System.getenv("GUNDEM_TEST_MODEL")
        Assume.assumeTrue(!path.isNullOrBlank() && !System.getenv("GUNDEM_NATIVE_LIB_DIR").isNullOrBlank())
        val context=ApplicationProvider.getApplicationContext<Context>();val model=LocalModel(context)
        val article=RssAdapter.parse(javaClass.getResource("/feeds/sol.xml")!!.readText(),INITIAL_SOURCES.first()).first()
        val lfm=java.io.File(path!!).name.contains("LFM",ignoreCase=true)
        val baseline=javaClass.getResource(if(lfm) "/perf-v07/system-lfm.txt" else "/perf-v07/system-json.txt")!!.readText()
        val grammar=if(lfm) classificationGrammar() else context.assets.open("prompts/classify.gbnf").bufferedReader().use {it.readText()}
        try { model.session(Settings(modelPath=path!!,modelHash="native-benchmark-04")) {
            val input=buildJsonObject {put("baslik",article.title)}.toString()
            val start=System.nanoTime()
            val old=NativeLlama.generate(baseline,"Haber:\n"+input,grammar,if(lfm) 96 else 160)
            val before=NativeLlama.stats();val oldMs=(System.nanoTime()-start)/1_000_000
            assertTrue(old.startsWith("OK\n"))
            val result=model.classify(article);val after=NativeLlama.stats()
            println("SAME_ARTICLE_BEFORE: $before | elapsed_ms=$oldMs")
            println("SAME_ARTICLE_AFTER: $after | result=${result.json()}")
            fun tokens(stats:String)=Regex("girdi_token=(\\d+)").find(stats)!!.groupValues[1].toInt()
            assertTrue(result.complete);assertTrue("before=$before after=$after",tokens(after)<tokens(before)/2)
        } } finally {model.release()}
    }
    @Test fun realModelKeepsRelevantTopicsWithMinimalPrompt()=runBlocking {
        val path=System.getenv("GUNDEM_TEST_MODEL")
        Assume.assumeTrue(!path.isNullOrBlank() && !System.getenv("GUNDEM_NATIVE_LIB_DIR").isNullOrBlank())
        val context=ApplicationProvider.getApplicationContext<Context>();val model=LocalModel(context)
        val examples=listOf(
            Triple("İstanbul'da fabrika işçileri greve başladı","İşçiler ödenmeyen ücretlerini ve sendikal haklarını talep ediyor.","EMEK"),
            Triple("Küba'ya ABD ablukası Birleşmiş Milletler'de görüşüldü","Delegeler Küba halkına uygulanan ekonomik ablukayı kınadı.","KUBA"),
            Triple("TKP Ankara'daki mitinge katılım çağrısı yaptı","Türkiye Komünist Partisi yarınki yürüyüşe emekçileri davet etti.","TKP")
        )
        try { model.session(Settings(modelPath=path!!,modelHash="native-04")) {
            for((index,e) in examples.withIndex()) {
                val a=Article("quality-$index","test","remote-$index","https://example.org/$index",e.first,e.second,null,null,"test-$index")
                val start=System.nanoTime();val result=model.classify(a)
                println("SAMPLE $index: ${result.json()} | ${NativeLlama.stats()} | elapsed_ms=${(System.nanoTime()-start)/1_000_000}")
                assertTrue(result.json(),result.complete)
                assertTrue("${e.first}: ${result.json()}",e.third in result.tags.orEmpty())
                assertTrue("Bütün etiketler kopyalandı: ${result.json()}",result.tags!!.size<7)
            }
        } } finally {model.release()}
    }
}
