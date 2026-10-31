package tr.gundem

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File

/** Bounded persistent JSONL journal; never records signed URLs, credentials or full article bodies. */
class EventLog(context:Context) {
    private val file=File(context.filesDir,"events.jsonl")
    val revision=MutableStateFlow(0L)
    @Synchronized fun add(category:String,event:String,detail:String="",elapsedMs:Long?=null) {
        val row=JSONObject().put("time",System.currentTimeMillis()).put("category",category).put("event",event).put("detail",detail.take(2000))
        elapsedMs?.let { row.put("elapsedMs",it) }
        if(file.length()>2_000_000) { File(file.parentFile,"events.previous.jsonl").delete();file.renameTo(File(file.parentFile,"events.previous.jsonl")) }
        file.appendText(row.toString()+"\n");revision.value++
    }
    @Synchronized fun text():String=listOf(File(file.parentFile,"events.previous.jsonl"),file).filter { it.exists() }.joinToString("") { it.readText() }
    fun rows():List<JSONObject> = text().lineSequence().filter { it.isNotBlank() }.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }.toList().asReversed()
    @Synchronized fun clear() { file.delete();File(file.parentFile,"events.previous.jsonl").delete();revision.value++ }
}
