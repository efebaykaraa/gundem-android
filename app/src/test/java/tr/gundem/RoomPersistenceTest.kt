package tr.gundem
import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class RoomPersistenceTest {
    private lateinit var db:NewsDatabase
    @Before fun setup() { db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),NewsDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() { db.close() }
    private fun item()=Article("a","sol","remote","https://example.org/a","İşçiler grevde","Ücret için",100,null,"v1")
    @Test fun duplicateIngestAndRemoteIdentityPreserveManualData()=runBlocking {
        val d=db.news();val a=item();d.ingest(a);d.read("a",true);d.save("a",true);d.hide("a",true)
        val manual=Analysis(listOf("EMEK"),"CAGRI",3,3,2).json();d.correct("a",manual)
        d.ingest(a);d.ingest(a.copy(id="b",url="https://example.org/new",title="Değişti",contentHash="v2"))
        assertEquals(1,d.articles().size);val actual=d.article("a")!!
        assertEquals("Değişti",actual.title);assertTrue(actual.read);assertTrue(actual.saved);assertTrue(actual.hidden);assertEquals(manual,actual.manualJson)
        d.analysisResult("a","v2",Analysis(listOf("DIGER"),"YORUM",0,0,0).json(),"model","TAMAM",null)
        assertEquals(listOf("EMEK"),d.article("a")!!.analysis().tags)
    }
    @Test fun sameUrlAcrossSourcesCannotDuplicateAndOldAnalysisCannotOverwriteNewContent()=runBlocking {
        val d=db.news();val a=item();d.ingest(a);d.ingest(a.copy(id="b",sourceId="tkp",remoteId="another"));assertEquals(1,d.articles().size)
        d.analysisResult("a","v1",Analysis(listOf("EMEK"),"CAGRI",1,1,1).json(),"old","TAMAM",null)
        d.ingest(a.copy(contentHash="v2"));d.analysisResult("a","v1","{\"etki\":3}","old","TAMAM",null)
        val actual=d.article("a")!!;assertEquals("BEKLIYOR",actual.state);assertEquals("{}",actual.analysisJson)
    }
    @Test fun interruptedQueueAndManualPairSurviveRefresh()=runBlocking {
        val d=db.news();d.ingest(item());d.status("a","CALISIYOR");d.recover();assertEquals("BEKLIYOR",d.article("a")!!.state)
        d.pair(StoredPair("a|b","a","b",false,true));d.ingest(item().copy(contentHash="v2"));assertEquals(false,d.pairs().single().same);assertTrue(d.pairs().single().manual)
    }
    @Test fun settingsBoundsRoundTripAndPortableModelExclusion() {
        val store=SettingsStore(ApplicationProvider.getApplicationContext())
        val s=Settings(Weights(impact=2.0),theme="KOYU",modelPath="/private/model.gguf",modelHash="abc")
        assertEquals(s,store.decode(store.encode(s)));assertEquals("",store.decode(store.encode(s,true)).modelPath)
        assertThrows(IllegalArgumentException::class.java) { store.decode(store.encode(s).replace("\"impact\":2.0","\"impact\":200.0")) }
    }
    @Test fun xAccountRemovalIsNotUndoneByNextStartup()=runBlocking {
        val store=SettingsStore(ApplicationProvider.getApplicationContext())
        store.update { it.copy(xDefaultsSeeded=false) }
        seedSources(db.news(),store)
        assertTrue(db.news().sources().any { it.id=="x:tkpninsesi" })
        db.news().deleteXSource("x:tkpninsesi")
        seedSources(db.news(),store)
        assertFalse(db.news().sources().any { it.id=="x:tkpninsesi" })
        val added=xSource("@new_account","Yeni hesap");db.news().insertSource(added);db.news().insertSource(added)
        assertEquals(1,db.news().sources().count { it.id==added.id })
    }
    @Test fun portableSettingsAndAccountListRestore()=runBlocking {
        val store=SettingsStore(ApplicationProvider.getApplicationContext())
        val original=Settings(nitterBase="https://nitter.example.org",xDefaultsSeeded=true)
        val portable=store.decode(store.encode(original,true));assertEquals(original.nitterBase,portable.nitterBase)
        db.news().insertSource(xSource("old_account"))
        db.news().restoreSources(listOf(xSource("new_account")))
        assertEquals(listOf("x:new_account"),db.news().sources().map { it.id })
    }

}
