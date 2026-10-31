package tr.gundem

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
class Update03DeviceTest {
    @Test fun realSourceAndPersistentJournal()=runBlocking<Unit> {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveFeeds")=="1")
        val app=ApplicationProvider.getApplicationContext<GundemApp>()
        withContext(Dispatchers.IO) {
            val dao=app.db.news()
            for(source in INITIAL_SOURCES+DEFAULT_X_ACCOUNTS) {
                val configured=if(source.kind=="X_ACCOUNT") resolveXSource(source,"") else source
                val start=System.nanoTime()
                try {
                    val articles=RssAdapter().fetch(configured);assertTrue(source.name,articles.isNotEmpty())
                    dao.insertSource(source);articles.forEach {dao.ingest(it)};dao.sourceResult(source.id,System.currentTimeMillis(),null)
                    app.logs.add("KAYNAK","Cihaz doğrulaması","${source.name}: ${articles.size}",(System.nanoTime()-start)/1_000_000)
                } catch(e:Exception) {app.logs.add("KAYNAK","Cihaz erişim hatası","${source.name}: ${e.message}");throw e}
            }
            assertTrue(dao.articles().isNotEmpty());assertTrue(EventLog(app).text().contains("Cihaz doğrulaması"))
        }
    }
    @Test fun notificationAndLogExportFileAreValid()=runBlocking<Unit> {
        val app=ApplicationProvider.getApplicationContext<GundemApp>()
        val info=analysisForeground(app,2,5)
        assertEquals(41,info.notificationId);assertEquals(1,info.notification.actions.size)
        app.logs.add("AI","Bildirim doğrulaması","2 / 5")
        val log=EventLog(app).rows().first {it.optString("event")=="Bildirim doğrulaması"}
        assertEquals("2 / 5",log.optString("detail"))
    }
}
