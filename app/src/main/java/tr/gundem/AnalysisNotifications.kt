package tr.gundem

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*

private const val CHANNEL="analysis"
private const val NOTICE=41
fun analysisNotification(context:Context,done:Int,total:Int,label:String="Haber analizi",paused:Boolean=false):android.app.Notification {
    val manager=context.getSystemService(NotificationManager::class.java)
    if(!paused) manager.cancel(43)
    manager.createNotificationChannel(NotificationChannel(CHANNEL,"Yapay zekâ",NotificationManager.IMPORTANCE_LOW))
    val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val action=if(paused) "resume" else "pause"
    val toggle=PendingIntent.getBroadcast(context,if(paused) 1 else 2,Intent(context,AnalysisActions::class.java).setAction(action),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    return NotificationCompat.Builder(context,CHANNEL).setSmallIcon(tr.gundem.R.drawable.ic_gundem).setContentTitle(if(paused) "Yapay zekâ duraklatıldı" else label).setContentText("$done / $total haber").setContentIntent(open).setOngoing(!paused).setOnlyAlertOnce(true).setProgress(total.coerceAtLeast(1),done,false).addAction(0,if(paused) "Devam et" else "Durdur",toggle).build()
}
fun analysisForeground(context:Context,done:Int,total:Int,label:String="Haber analizi"):ForegroundInfo {
    val n=analysisNotification(context,done,total,label)
    return if(Build.VERSION.SDK_INT>=34) ForegroundInfo(NOTICE,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else ForegroundInfo(NOTICE,n)
}
class AnalysisActions:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val result=goAsync();val app=context.applicationContext as GundemApp
        CoroutineScope(Dispatchers.IO).launch { try {
            val pause=intent.action!="resume"
            app.logs.add("AI",if(pause) "Bildirimden duraklatıldı" else "Bildirimden devam ettirildi")
            if(pause) pauseAnalysis(app)
            else resumeAnalysis(app)
        } finally { result.finish() } }
    }
}

fun downloadForeground(context:Context,id:java.util.UUID,bytes:Long,total:Long):ForegroundInfo {
    val manager=context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel("downloads","Model indirme",NotificationManager.IMPORTANCE_LOW))
    val notification=NotificationCompat.Builder(context,"downloads").setSmallIcon(tr.gundem.R.drawable.ic_gundem).setContentTitle("Model indiriliyor").setContentText("${bytes/1_000_000} / ${total/1_000_000} MB").setProgress(100,(bytes*100/total.coerceAtLeast(1)).toInt(),false).setOngoing(true).setOnlyAlertOnce(true).addAction(0,"Durdur",WorkManager.getInstance(context).createCancelPendingIntent(id)).build()
    return if(Build.VERSION.SDK_INT>=34) ForegroundInfo(42,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(42,notification)
}

suspend fun pauseAnalysis(app:GundemApp) {
    val manager=WorkManager.getInstance(app)
    val work=manager.getWorkInfosForUniqueWork("analysis").get().firstOrNull { !it.state.isFinished }
    val done=work?.progress?.getInt("done",0)?:0;val total=work?.progress?.getInt("total",0)?:0
    app.settings.update { it.copy(paused=true) };app.model.cancel()
    manager.cancelUniqueWork("analysis").result.get()
    if(Build.VERSION.SDK_INT<33 || androidx.core.content.ContextCompat.checkSelfPermission(app,Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED) {
        app.getSystemService(NotificationManager::class.java).notify(43,analysisNotification(app,done,total,paused=true))
    }
}

/** Serializes repeated taps/broadcasts. KEEP never cancels an already running analysis. */
private val analysisControl=kotlinx.coroutines.sync.Mutex()
suspend fun resumeAnalysis(app:GundemApp) {
    analysisControl.lock()
    try {
        val manager=WorkManager.getInstance(app)
        val active=manager.getWorkInfosForUniqueWork("analysis").get().any { !it.state.isFinished }
        if(!app.settings.current().paused && active) { app.logs.add("AI","Yinelenen başlatma yok sayıldı");return }
        app.settings.update { it.copy(paused=false) }
        app.logs.add("AI","Kullanıcı devam ettirdi")
        enqueueAnalysis(app)
    } finally { analysisControl.unlock() }
}
