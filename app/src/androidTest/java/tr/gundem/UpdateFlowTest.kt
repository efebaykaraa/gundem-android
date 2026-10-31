package tr.gundem
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.WorkInfo
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpdateFlowTest {
    @Test fun oneTouchDownloadInstallsRealVerifiedRecommendedModel()=runBlocking<Unit> {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveModelDownload")=="1")
        val app=ApplicationProvider.getApplicationContext<GundemApp>()
        val manager=WorkManager.getInstance(app)
        downloadModel(app)
        var terminal:WorkInfo?=null
        for(attempt in 1..600) {
            delay(1000)
            val info=manager.getWorkInfosForUniqueWork(MODEL_DOWNLOAD_WORK).get().firstOrNull()
            if(info?.state?.isFinished==true) {terminal=info;break}
        }
        assertNotNull("İndirme süresi doldu",terminal)
        assertEquals(terminal!!.outputData.toString(),WorkInfo.State.SUCCEEDED,terminal!!.state)
        InstrumentationRegistry.getArguments().getString("expectedResumeOffset")?.toLong()?.let { expected ->
            assertEquals(206,terminal!!.outputData.getInt("httpStatus",0))
            assertEquals(expected,terminal!!.outputData.getLong("resumedFrom",-1))
        }
        val settings=app.settings.current()
        assertEquals(RECOMMENDED_MODEL.sha256,settings.modelHash)
        assertEquals(RECOMMENDED_MODEL.size,File(settings.modelPath).length())
        assertTrue(settings.paused)
    }
}
