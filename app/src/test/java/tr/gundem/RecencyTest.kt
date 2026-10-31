package tr.gundem

import org.junit.Assert.*
import org.junit.Test
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class RecencyTest {
    private val day=86_400_000L
    private val analysis=Analysis(listOf("EMEK"),"SOMUT_GELISME",3,2,3)
    private fun article(id:String,published:Long?,fetched:Long)=Article(id,"s",id,"https://example.org/$id",id,"body",published,null,id,fetched=fetched)

    @Test fun dailyPenaltyUsesCompleted24HoursAndNeverChangesAnalysis() {
        val w=Weights(dailyPenalty=0.7)
        val before=analysis.json()
        val old=score(analysis,10*day,1.0,w,12*day)
        val unpenalized=score(analysis,10*day,1.0,w.copy(dailyPenalty=0.0),12*day)
        assertEquals(2L,old.ageDays);assertEquals(1.4,old.agePenalty,0.00001)
        assertEquals(unpenalized.value-1.4,old.value,0.00001)
        assertEquals(0.0,score(analysis,day,1.0,w,2*day-1).agePenalty,0.0)
        assertEquals(0.7,score(analysis,day,1.0,w,2*day).agePenalty,0.00001)
        assertEquals(before,analysis.json())
    }
    @Test fun unknownAndFutureDatesDoNotInventAgeAndScoreCannotBeNegative() {
        assertNull(score(analysis,null,1.0,Weights(),day).ageDays)
        assertEquals(0.0,score(analysis,null,1.0,Weights(),day).agePenalty,0.0)
        assertEquals(0L,score(analysis,2*day,1.0,Weights(),day).ageDays)
        assertEquals(0.0,score(analysis,0,1.0,Weights(),100*day).value,0.0)
        assertEquals(0.0,score(Analysis(),0,1.0,Weights(),100*day).value,0.0)
    }
    @Test fun publishedDateWinsOverFetchTimeAndNewArrivalsJumpQueue() {
        val a=article("old",day,20*day);val b=article("new",3*day,2*day);val c=article("unknown",null,50*day)
        assertEquals(listOf("new","old","unknown"),newestFirst(listOf(c,a,b)).map {it.id})
        val arriving=article("arriving",4*day,4*day)
        assertEquals("arriving",newestFirst(listOf(a,c,arriving)).first().id)
        assertEquals(listOf("a","b"),newestFirst(listOf(article("b",day,day),article("a",day,day))).map {it.id})
    }
    @Test fun datesAndPenaltyNeverEnterModelInputOrCacheKey() {
        val a=article("a",day,day)
        val b=a.copy(published=20*day)
        assertEquals(classificationInput(a),classificationInput(b))
        assertEquals(analysisCacheKey(a,Settings()),analysisCacheKey(b,Settings(weights=Weights(dailyPenalty=5.0))))
    }
    @Test fun penaltyPersistsAndOldSettingsGetDefault() {
        val store=SettingsStore(ApplicationProvider.getApplicationContext())
        val settings=Settings(weights=Weights(dailyPenalty=1.25))
        assertEquals(settings,store.decode(store.encode(settings)))
        val legacy=store.encode(settings).replace("\"dailyPenalty\":1.25,", "")
        assertEquals(0.5,store.decode(legacy).weights.dailyPenalty,0.0)
        assertThrows(IllegalArgumentException::class.java) {Weights(dailyPenalty=-1.0).validated()}
        assertThrows(IllegalArgumentException::class.java) {Weights(dailyPenalty=Double.NaN).validated()}
    }
}
