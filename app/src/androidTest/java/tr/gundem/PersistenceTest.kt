package tr.gundem

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private lateinit var db:NewsDatabase
    @Before fun setup() { db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),NewsDatabase::class.java).build() }
    @After fun close() { db.close() }
    private fun article()=Article("a","sol","remote1","https://example.org/one","İşçiler grevde","Ücret için grev",100,null,"h1")
    @Test fun repeatedIngestDoesNotDuplicateOrEraseCorrections()=runBlocking<Unit> {
        val dao=db.news();val a=article();dao.ingest(a);dao.save("a",true);dao.read("a",true)
        val manual=Analysis(listOf("EMEK"),"CAGRI",2,3,2).json();dao.correct("a",manual)
        dao.ingest(a);dao.ingest(a.copy(id="different",url="https://example.org/new",title="Yeni başlık",contentHash="h2"))
        assertEquals(1,dao.articles().size);val current=dao.article("a")!!
        assertEquals(manual,current.manualJson);assertTrue(current.saved);assertTrue(current.read);assertEquals("Yeni başlık",current.title)
        dao.analysisResult("a","h2",Analysis(listOf("DIGER"),"YORUM",0,0,0).json(),"key","TAMAM",null)
        assertEquals(listOf("EMEK"),dao.article("a")!!.analysis().tags)
    }
    @Test fun processRecoveryAndManualPairsSurviveFeedUpdates()=runBlocking<Unit> {
        val dao=db.news();dao.ingest(article());dao.status("a","CALISIYOR");dao.recover();assertEquals("BEKLIYOR",dao.article("a")!!.state)
        dao.pair(StoredPair(pairKey("a","b"),"a","b",false,true));dao.ingest(article().copy(title="Değişti",contentHash="h2"));assertTrue(dao.pairs().single().manual)
    }
    @Test fun urlUniquenessAcrossSourcesAndStaleAnalysisGuard()=runBlocking<Unit> {
        val dao=db.news();val a=article();dao.ingest(a);dao.ingest(a.copy(id="b",sourceId="tkp",remoteId="other"));assertEquals(1,dao.articles().size)
        dao.ingest(a.copy(contentHash="new"));dao.analysisResult("a","h1","{\"etki\":3}","old","TAMAM",null);assertEquals("BEKLIYOR",dao.article("a")!!.state)
    }
    @Test fun settingsRoundTripAndBounds()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Context>();val store=SettingsStore(context)
        val s=Settings(weights=Weights(impact=2.0),theme="KOYU");assertEquals(s,store.decode(store.encode(s)))
        assertThrows(IllegalArgumentException::class.java) { store.decode(store.encode(s).replace("\"impact\":2.0","\"impact\":200.0")) }
    }
    @Test fun liveRssToRoomAndRepeatDownload()=runBlocking<Unit> {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveFeeds")=="1")
        val context=ApplicationProvider.getApplicationContext<GundemApp>()
        withContext(Dispatchers.IO) {
            val articles=INITIAL_SOURCES.filter { it.kind=="RSS" }.flatMap { source ->
                RssAdapter().fetch(source).also { assertTrue(source.name,it.isNotEmpty()) }
            }
            articles.forEach { context.db.news().ingest(it) }
            val count=context.db.news().articles().size
            articles.forEach { context.db.news().ingest(it) }
            assertEquals(count,context.db.news().articles().size)
            assertTrue(context.db.news().articles().all { it.analysis().tags==null || it.analysis().complete })
        }
    }
    @Test fun nativeConstrainedInferenceWithOptionalTestModel()=runBlocking<Unit> {
        val path=InstrumentationRegistry.getArguments().getString("modelPath")
        Assume.assumeTrue("modelPath verilmedi",!path.isNullOrBlank())
        val context=ApplicationProvider.getApplicationContext<Context>()
        val model=(context.applicationContext as GundemApp).model;val s=Settings(modelPath=path!!,modelHash="instrumentation")
        try { model.session(s) {
            val result=model.classify(article().copy(title="İşçiler ödenmeyen maaşları için bugün greve başladı",intro="İstanbul'da fabrikada ücretlerin ödenmemesi üzerine işçiler grev başlattı."))
            assertTrue("Gerçek native model çıktısı: ${result.json()}",result.complete)
        } } finally { model.release() }
    }
}
