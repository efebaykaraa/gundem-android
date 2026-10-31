package tr.gundem

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AnalysisControlTest {
    @Test fun repeatedResumeDoesNotReplaceAnExistingQueue()=runBlocking<Unit> {
        val app=ApplicationProvider.getApplicationContext<GundemApp>();val manager=WorkManager.getInstance(app)
        val original=app.settings.current()
        manager.cancelUniqueWork("analysis").result.get()
        val held=OneTimeWorkRequestBuilder<AnalysisWorker>().setInitialDelay(1,TimeUnit.DAYS).build()
        try {
            app.settings.update {it.copy(paused=false)}
            manager.enqueueUniqueWork("analysis",ExistingWorkPolicy.KEEP,held).result.get()
            coroutineScope { repeat(10) {launch(Dispatchers.IO) {resumeAnalysis(app)}} }
            val active=manager.getWorkInfosForUniqueWork("analysis").get().filterNot {it.state.isFinished}
            assertEquals(1,active.size);assertEquals(held.id,active.single().id)
            assertEquals(WorkInfo.State.ENQUEUED,active.single().state)
        } finally {manager.cancelUniqueWork("analysis").result.get();app.settings.update {original.copy(paused=true)}}
    }
}
