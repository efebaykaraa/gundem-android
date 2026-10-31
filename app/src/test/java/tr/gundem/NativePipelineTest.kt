package tr.gundem
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Optional real JNI integration on the host. This is not an Android ABI/device test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class NativePipelineTest {
    @Test fun realFeedFixtureToRoomNativeAnalysisCacheAndRanking()=runBlocking {
        val modelPath=System.getenv("GUNDEM_TEST_MODEL")
        Assume.assumeTrue("Gerçek GGUF ve host JNI kütüphanesi sağlanmadı",!modelPath.isNullOrBlank() && !System.getenv("GUNDEM_NATIVE_LIB_DIR").isNullOrBlank())
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,NewsDatabase::class.java).allowMainThreadQueries().build()
        val engine=LocalModel(context)
        try {
            val article=RssAdapter.parse(javaClass.getResource("/feeds/sol.xml")!!.readText(),INITIAL_SOURCES.first()).first()
            db.news().ingest(article)
            val settings=Settings(modelPath=modelPath!!,modelHash="real-host-model")
            engine.session(settings) {
                val analysis=engine.classify(article)
                assertTrue("Gerçek JNI çıktısı: ${analysis.json()}",analysis.complete)
                val key=analysisCacheKey(article,settings)
                db.news().cache(CachedAnalysis(key,analysis.json(),true))
                db.news().analysisResult(article.id,article.contentHash,analysis.json(),key,"TAMAM",null)
                val stored=db.news().article(article.id)!!
                assertEquals(analysis,stored.analysis())
                assertTrue(score(stored.analysis(),stored.published,1.0,Weights(),System.currentTimeMillis()).value.isFinite())
                assertEquals(analysis.json(),db.news().cache(key)!!.json)
                val cut=NativeLlama.generate("Haber sınıflandır.","İşçiler grevde",context.assets.open("prompts/classify.gbnf").bufferedReader().use { it.readText() },1)
                assertTrue(cut.startsWith("CUT\n"));assertFalse(OutputValidator.analysis(cut.substringAfter('\n'),false).complete)
            }
        } finally { engine.release();db.close() }
    }
}
