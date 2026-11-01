package tr.gundem

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);setContent { GundemScreen() } }
}
private fun date(value:Long?)=value?.let { DateTimeFormatter.ofPattern("d MMM HH:mm",Locale.forLanguageTag("tr")).withZone(ZoneId.of("Europe/Istanbul")).format(Instant.ofEpochMilli(it)) }?:"Yayın tarihi bilinmiyor"
private val Red=Color(0xFFA22332)
@Composable fun GundemScreen(vm:NewsViewModel=viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var detail by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val screens=rememberSaveableStateHolder()
    var page by rememberSaveable { mutableIntStateOf(0) }
    val downloadState by vm.modelDownload.collectAsStateWithLifecycle()
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(state.settings.paused,downloadState?.state) {
        if((!state.settings.paused || downloadState?.state?.isFinished==false) && android.os.Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(vm.app,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    val dark=state.settings.theme=="KOYU" || state.settings.theme=="SISTEM" && isSystemInDarkTheme()
    val scheme=if(dark) darkColorScheme(primary=Color(0xFFFFB2B7),secondary=Color(0xFF9BD2C5)) else lightColorScheme(primary=Red,secondary=Color(0xFF286A5B),background=Color(0xFFFAF8F4),surface=Color(0xFFFFFCF8),surfaceVariant=Color(0xFFF0EAE3))
    MaterialTheme(colorScheme=scheme) {
        Scaffold(bottomBar={ if(detail==null) NavigationBar { listOf("Akış","Kaynaklar","Ayarlar","Model").forEachIndexed { i,title->NavigationBarItem(selected=page==i,onClick={page=i},icon={Text(listOf("▤","◉","≡","◈")[i],fontSize=22.sp)},label={Text(title)}) } } }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if(message!=null) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Text(message!!,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);TextButton(onClick={vm.message.value=null}) { Text("Kapat") } }
                if(detail!=null) {
                    BackHandler { detail=null }
                    ArticleDetail(detail!!.mapNotNull { id->state.articles.firstOrNull { it.id==id } },state,vm) { detail=null }
                } else {
                    AnalysisBanner(state,vm)
                    screens.SaveableStateProvider(page) { when(page) { 0->Feed(state,vm) { detail=it.map(Article::id) };1->Sources(state,vm);2->AppSettings(state,vm);else->ModelSettings(state,vm,busy) } }
                }
            }
        }
    }
}
@Composable private fun FilterChoice(label:String,values:List<String>,selected:String,onSelect:(String)->Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick={open=true},contentPadding=PaddingValues(horizontal=12.dp,vertical=5.dp)) { Text(if(selected.isBlank()) label else selected,style=MaterialTheme.typography.labelMedium) };DropdownMenu(expanded=open,onDismissRequest={open=false}) { DropdownMenuItem(text={Text("Tümü · $label")},onClick={onSelect("");open=false});values.forEach { value-> DropdownMenuItem(text={Text(value)},onClick={onSelect(value);open=false}) } } }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun Feed(state:ScreenState,vm:NewsViewModel,onOpen:(List<Article>)->Unit) {
    var search by rememberSaveable { mutableStateOf("") };var tag by rememberSaveable { mutableStateOf("") };var type by rememberSaveable { mutableStateOf("") };var source by rememberSaveable { mutableStateOf("") }
    var newest by rememberSaveable { mutableStateOf(false) };var subset by rememberSaveable { mutableStateOf("Tümü") }
    var edit by remember { mutableStateOf<Article?>(null) };var mergeTarget by remember { mutableStateOf<List<Article>?>(null) }
    val clock by produceState(System.currentTimeMillis()) { while(true) { kotlinx.coroutines.delay(60000);value=System.currentTimeMillis() } }
    val sourceMap=state.sources.associateBy { it.id }
    val scores=remember(state.articles,state.sources,state.settings.weights,clock) { state.articles.associate { a->a.id to score(a.analysis(),a.published,sourceMap[a.sourceId]?.priority?:1.0,state.settings.weights,clock) } }
    val groups=remember(state.articles,state.pairs) { articleGroups(state.articles,state.pairs) }
    val filtered=groups.mapNotNull { group->
        val visible=group.filter { a->
            val analysis=a.analysis()
            (if(subset=="Gizlenen") a.hidden else !a.hidden) && (subset!="Kaydedilen" || a.saved) && (subset!="Okunmamış" || !a.read) &&
                (tag.isBlank() || tag in analysis.tags.orEmpty()) && (type.isBlank() || type==analysis.type) && (source.isBlank() || sourceMap[a.sourceId]?.name==source) &&
                (search.isBlank() || "${a.title} ${a.intro}".lowercase(Locale.forLanguageTag("tr")).contains(search.lowercase(Locale.forLanguageTag("tr"))))
        }
        if(visible.isEmpty()) null else visible.sortedWith(compareByDescending<Article> { scores[it.id]?.value?:0.0 }.thenByDescending { it.published?:Long.MIN_VALUE }.thenBy { it.id })
    }.sortedWith(if(newest) compareByDescending<List<Article>> { g->g.mapNotNull { it.published }.maxOrNull()?:Long.MIN_VALUE }.thenBy { it.first().id } else compareByDescending<List<Article>> { scores[it.first().id]?.value?:0.0 }.thenBy { it.first().id })
    CollapsingFeed(header={
      Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal=16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) { Text("GÜNDEM",Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);FilledTonalButton(onClick=vm::refresh) { Text("Yenile") } }
        OutlinedTextField(search,{search=it},label={Text("Başlık ve girişte ara")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp),shape=RoundedCornerShape(14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChoice("Konu",TOPICS,tag){tag=it};FilterChoice("Tür",TYPES,type){type=it};FilterChoice("Kaynak",state.sources.map { it.name },source){source=it}
            FilterChoice("Görünüm",listOf("Tümü","Kaydedilen","Okunmamış","Gizlenen"),subset){subset=it.ifBlank { "Tümü" }}
        }
        Row(Modifier.padding(horizontal=20.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("${filtered.size} olay",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={newest=!newest}) { Text(if(newest) "En yeni ↓" else "Önem sırası ↓") }
        }
        if(mergeTarget!=null) Row(Modifier.background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Text("Birleştirilecek ikinci kartı seçin",Modifier.weight(1f));TextButton(onClick={mergeTarget=null}) { Text("Vazgeç") } }
      }
    }) {
      LazyColumn(state=rememberLazyListState(),modifier=Modifier.weight(1f),contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
      if(state.articles.isEmpty()) item { Text("Henüz haber yok. Yenile ile haberleri getirin.",Modifier.padding(20.dp)) }
            items(filtered,key={ it.map(Article::id).sorted().joinToString() }) { group->
                NewsCard(group,scores,sourceMap,vm,onEdit={edit=it},onMerge={ if(mergeTarget==null) mergeTarget=group else {vm.merge(mergeTarget!!,group);mergeTarget=null} },selecting=mergeTarget!=null,onOpen={onOpen(group)})
            }
            if(filtered.isEmpty()) item { Text("Bu filtrelerle eşleşen haber yok.",Modifier.padding(20.dp)) }
    }
    }
    edit?.let { a-> CorrectionDialog(a,onDismiss={edit=null},onSave={vm.correct(a,it);edit=null},onReset={vm.clearCorrection(a);edit=null}) }
}
@Composable private fun NewsCard(group:List<Article>,scores:Map<String,Score>,sources:Map<String,Source>,vm:NewsViewModel,onEdit:(Article)->Unit,onMerge:()->Unit,selecting:Boolean,onOpen:()->Unit) {
    val a=group.first();val analysis=a.analysis();val context=LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().clickable { if(selecting) onMerge() else onOpen() }.padding(17.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Text(sources[a.sourceId]?.name?:a.sourceId,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);Text("%.2f".format(Locale.forLanguageTag("tr"),scores[a.id]?.value?:0.0),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.secondary) }
            Spacer(Modifier.height(9.dp));Text(a.title,style=MaterialTheme.typography.titleLarge,fontWeight=if(a.read) FontWeight.Normal else FontWeight.Bold,lineHeight=27.sp)
            Spacer(Modifier.height(8.dp));Text(date(a.published),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp));Text(when { a.manualJson!=null->"Elle düzeltildi";analysis.complete->"${analysis.tags?.joinToString(" · ")} / ${analysis.type}";else->"AI: ${when(a.state) { "CALISIYOR"->"Analiz sürüyor";"EKSIK"->"Eksik analiz";"HATA"->"Hata; yeniden deneme gerekli";else->"Analiz bekliyor" }}" },style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
            if(a.intro.isNotBlank()) { Spacer(Modifier.height(10.dp));Text(a.intro,maxLines=if(expanded) Int.MAX_VALUE else 3,style=MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(6.dp));Text("${group.map { it.sourceId }.distinct().size} kaynak · ${group.size} haber  ${if(expanded) "▴" else "▾"}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            TextButton(onClick={expanded=!expanded}) {Text(if(expanded) "İşlemleri kapat" else "İşlemler")}
            if(expanded) {
                HorizontalDivider(Modifier.padding(vertical=12.dp))
                Text(scores[a.id]?.explanation.orEmpty(),style=MaterialTheme.typography.bodySmall)
                Text("Etki ${analysis.impact?:"BILINMIYOR"} · Ciddiyet ${analysis.seriousness?:"BILINMIYOR"} · Aciliyet ${analysis.urgency?:"BILINMIYOR"}",style=MaterialTheme.typography.bodySmall)
                Text("Tüm konular: ${analysis.tags?.joinToString()?:"SINIFLANDIRILAMADI"}",style=MaterialTheme.typography.bodySmall)
                if(a.titleOnly) Text("Giriş eksik: yalnız başlıkla analiz",style=MaterialTheme.typography.bodySmall)
                a.error?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error) }
                TimeDetector.detect("${a.title} ${a.intro}").forEach { Text("Saat ifadesi: ${it.time} · ${it.relation}",style=MaterialTheme.typography.bodySmall) }
                Text("Kart puanı: en yüksek puanlı haber. Eşitlikte yayın tarihi, ardından sabit kimlik; kaynak sayısı bonus vermez.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                group.forEach { item ->
                    Spacer(Modifier.height(8.dp));Text("${sources[item.sourceId]?.name}: ${item.title}",fontWeight=FontWeight.Medium,style=MaterialTheme.typography.bodyMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick={runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(item.url)));if(!item.read) vm.read(item) }.onFailure { vm.message.value="Bağlantıyı açacak uygulama bulunamadı" }}) { Text("Kaynağında aç") }
                        TextButton(onClick={runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain";putExtra(Intent.EXTRA_TEXT,"${item.title}\n${item.url}") },"Haberi paylaş")) }.onFailure { vm.message.value="Paylaşım uygulaması bulunamadı" }}) { Text("Paylaş") }
                        TextButton(onClick={onEdit(item)}) { Text("Düzelt") }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick={vm.markRead(group,!a.read)}) { Text(if(a.read) "Okunmadı" else "Okundu") }
                    TextButton(onClick={vm.save(a)}) { Text(if(a.saved) "Kaydı kaldır" else "Kaydet") }
                    TextButton(onClick={group.forEach { vm.hide(it) }}) { Text(if(a.hidden) "Geri getir" else "Gizle") }
                }
                Row { TextButton(onClick=onMerge) { Text("Elle birleştir") };if(group.size>1) TextButton(onClick={vm.split(group)}) { Text("Kartı ayır") } }
            }
        }
    }
}
@Composable private fun CorrectionDialog(a:Article,onDismiss:()->Unit,onSave:(Analysis)->Unit,onReset:()->Unit) {
    val original=remember { a.analysis() }
    var tags by remember { mutableStateOf(original.tags.orEmpty()) };var type by remember { mutableStateOf(original.type?:TYPES.first()) }
    var impact by remember { mutableIntStateOf(original.impact?:0) };var serious by remember { mutableIntStateOf(original.seriousness?:0) };var urgency by remember { mutableIntStateOf(original.urgency?:0) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Analizi düzelt")},text={ Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Bu düzeltme yenilemede korunur.")
        TOPICS.chunked(2).forEach { row-> Row { row.forEach { tag->Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically) { Checkbox(tag in tags,{ checked->tags=if(checked) { if(tag=="DIGER") listOf(tag) else (tags-tag).filterNot { it=="DIGER" }+tag } else tags-tag });Text(tag,style=MaterialTheme.typography.labelSmall) } } } }
        FilterChoice("Tür",TYPES,type){if(it.isNotBlank()) type=it}
        IntegerSlider("Etki",impact){impact=it};IntegerSlider("Ciddiyet",serious){serious=it};IntegerSlider("Aciliyet",urgency){urgency=it}
        if(a.manualJson!=null) TextButton(onClick=onReset) { Text("Elle düzeltmeyi kaldır") }
    } },confirmButton={Button(enabled=tags.isNotEmpty(),onClick={onSave(Analysis(tags,type,impact,serious,urgency))}) {Text("Kaydet")}},dismissButton={TextButton(onClick=onDismiss){Text("Vazgeç")}})
}
@Composable private fun IntegerSlider(label:String,value:Int,onChange:(Int)->Unit) { Text("$label: $value");Slider(value.toFloat(),{onChange(it.toInt())},valueRange=0f..3f,steps=2) }
@Composable private fun Sources(state:ScreenState,vm:NewsViewModel) {
    var xTab by remember { mutableStateOf(false) };var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Source?>(null) }
    val results by vm.xResults.collectAsStateWithLifecycle();val searching by vm.xSearching.collectAsStateWithLifecycle()
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Kaynaklar",style=MaterialTheme.typography.headlineMedium);Row { FilterChip(!xTab,{xTab=false},label={Text("Haber / RSS")});Spacer(Modifier.width(8.dp));FilterChip(xTab,{xTab=true},label={Text("X hesapları")}) } }
        if(xTab) {
            item { OutlinedTextField(search,{search=it},label={Text("İsim veya @kullanıcı_adı")},singleLine=true,modifier=Modifier.fillMaxWidth());Row { Button(enabled=search.isNotBlank() && !searching,onClick={vm.searchX(search)}) { Text("Hesap ara") };TextButton(enabled=runCatching { xHandle(search) }.isSuccess,onClick={vm.addX(search,"")}) { Text("Kullanıcı adıyla ekle") } };if(searching) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(results,key={"result:${it.handle}"}) { result -> Card { Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) { ProfileAvatar(result.avatar,result.name);Column(Modifier.weight(1f).padding(horizontal=12.dp)) { Text(result.name,fontWeight=FontWeight.Bold);Text("@${result.handle}") };TextButton(enabled=state.sources.none { it.id=="x:${result.handle}" },onClick={vm.addX(result.handle,result.name+" · @"+result.handle)}) {Text("Ekle")} } } }
        }
        items(state.sources.filter { if(xTab) it.kind=="X_ACCOUNT" else it.kind!="X_ACCOUNT" },key={it.id}) { source ->
            Card(onClick={editing=source}) { Row(Modifier.padding(16.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(source.name,fontWeight=FontWeight.Bold);Text(if(source.kind=="X_ACCOUNT") "x.com/${source.id.removePrefix("x:")}" else source.endpoint,style=MaterialTheme.typography.bodySmall,maxLines=2) }
                StatusDot(if(source.lastAttempt==null || !source.enabled) Color.Gray else if(source.error==null) Color(0xFF288954) else Color(0xFFD33A42),source.error?:if(source.lastSuccess==null) "Henüz kontrol edilmedi" else "Çalışıyor")
            } }
        }
        item { TextButton(onClick=vm::refresh) {Text("Kaynakları yenile")} }
    }
    editing?.let { source ->
        var enabled by remember { mutableStateOf(source.enabled) };var endpoint by remember { mutableStateOf(source.endpoint) };var priority by remember { mutableDoubleStateOf(source.priority) }
        AlertDialog(onDismissRequest={editing=null},title={Text(source.name)},text={Column { Row(verticalAlignment=Alignment.CenterVertically) { Text("Etkin",Modifier.weight(1f));Switch(enabled,{enabled=it}) };SettingSlider("Kaynak katsayısı",priority,0.5f..1.5f){priority=it};OutlinedTextField(endpoint,{endpoint=it},label={Text(if(source.kind=="X_ACCOUNT") "Özel RSS (isteğe bağlı)" else "RSS adresi")});source.error?.let { Text(it,color=MaterialTheme.colorScheme.error) };if(source.kind=="X_ACCOUNT") TextButton(onClick={vm.removeX(source);editing=null}) {Text("Hesabı kaldır")} }},confirmButton={TextButton(onClick={vm.source(source.copy(endpoint=endpoint,enabled=enabled,priority=priority));editing=null}) {Text("Kaydet")}},dismissButton={TextButton(onClick={editing=null}) {Text("Kapat")}})
    }
}
@Composable private fun WeightSettings(state:ScreenState,vm:NewsViewModel) {
    var draft by remember(state.settings.weights) { mutableStateOf(state.settings.weights) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportSettings) }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importSettings) }
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Text("Katsayılar",style=MaterialTheme.typography.headlineSmall) }
        item { Row { Button(onClick={vm.settings(state.settings.copy(weights=draft));vm.message.value="Katsayılar kaydedildi"}) {Text("Uygula")};TextButton(onClick={draft=Weights()}) {Text("Varsayılanlar") } } }
        item { Text("Temel = etki ağırlığı × etki + aciliyet ağırlığı × aciliyet × ciddiyet çarpanı\nSonuç = max(0, temel × konu × tür × kaynak × güncellik − günlük kesinti)",style=MaterialTheme.typography.bodySmall) }
        item { SettingSlider("Etki ağırlığı",draft.impact,0.25f..3f){draft=draft.copy(impact=it)};SettingSlider("Aciliyet ağırlığı",draft.urgency,0.25f..3f){draft=draft.copy(urgency=it)} }
        item { Text("Konu katsayıları",style=MaterialTheme.typography.titleMedium) }
        items(TOPICS) { t->SettingSlider(t,draft.topics.getValue(t),0.5f..1.5f){draft=draft.copy(topics=draft.topics+(t to it))} }
        item { Text("Tür katsayıları",style=MaterialTheme.typography.titleMedium) }
        items(TYPES) { t->SettingSlider(t,draft.types.getValue(t),0.5f..1.5f){draft=draft.copy(types=draft.types+(t to it))} }
        items((0..3).toList()) { i->SettingSlider("Ciddiyet $i çarpanı",draft.seriousness[i],0.5f..2f){v->draft=draft.copy(seriousness=draft.seriousness.mapIndexed { index,x->if(index==i) v else x })} }
        item { SettingSlider("Her 24 saatte düşülecek puan",draft.dailyPenalty,0f..5f){draft=draft.copy(dailyPenalty=it)};SettingSlider("Güncellik yarı ömrü (saat)",draft.halfLifeHours,6f..168f){draft=draft.copy(halfLifeHours=it)};SettingSlider("Güncellik tabanı / eksik tarih",draft.freshnessFloor,0.1f..0.5f){draft=draft.copy(freshnessFloor=it)};Text("Her tamamlanan 24 saatte puan düşer; sonuç sıfırın altına inmez. Eksik tarihte yalnız taban güncellik uygulanır, günlük kesinti yapılmaz.",style=MaterialTheme.typography.bodySmall) }
        item { Row {TextButton(onClick={export.launch("gundem-ayarlar.json")}) {Text("Ayarları dışa aktar")};TextButton(onClick={import.launch(arrayOf("application/json","text/plain"))}) {Text("İçe aktar")} } }
    }
}
@Composable private fun SettingSlider(label:String,value:Double,range:ClosedFloatingPointRange<Float>,change:(Double)->Unit) { Column { Text("$label · %.2f".format(value),style=MaterialTheme.typography.labelLarge);Slider(value.toFloat(),{change(it.toDouble())},valueRange=range) } }
@Composable private fun ModelSettings(state:ScreenState,vm:NewsViewModel,busy:Boolean) {
    val status by vm.modelStatus.collectAsStateWithLifecycle();val download by vm.modelDownload.collectAsStateWithLifecycle()
    val catalog=remember { modelCatalog(vm.app) };var selected by remember(state.settings.modelHash) { mutableStateOf(catalog.firstOrNull { it.downloadable && it.sha256==state.settings.modelHash } ?: catalog.first { it.downloadable }) };var choose by remember { mutableStateOf(false) };var info by remember { mutableStateOf(false) }
    val downloading=download?.state?.isFinished==false
    var query by remember { mutableStateOf("") }
    var changeModel by rememberSaveable { mutableStateOf(false) }
    val installed=state.settings.modelPath.isNotBlank()
    LaunchedEffect(state.settings.modelHash,download?.state) { if(installed && download?.state?.isFinished!=false) changeModel=false }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importModel) }
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text("Model",style=MaterialTheme.typography.headlineMedium) }
        if(!installed || changeModel || downloading) item { Card { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(selected.name,style=MaterialTheme.typography.titleLarge)
            Text(selected.scoreLabel,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
            Text(if(selected.downloadable) "${selected.size/1_000_000} MB · ${selected.formatLabel}" else "Mobil sürüm kullanılamıyor",style=MaterialTheme.typography.bodyMedium)
            if(selected.availabilityReason.isNotBlank()) Text(selected.availabilityReason,style=MaterialTheme.typography.bodySmall)
            Row { TextButton(enabled=!downloading && !busy,onClick={choose=true}) {Text("Model seç")};TextButton(onClick={info=true}) {Text("Bilgi")} }
            Button(enabled=selected.downloadable && !downloading && !busy && selected.sha256!=state.settings.modelHash,onClick={vm.download(selected)}) {Text(if(selected.downloadable && selected.sha256==state.settings.modelHash) "Kullanılıyor" else "İndir ve kullan")}
            if(installed && !downloading) TextButton(onClick={changeModel=false}) { Text("Vazgeç") }
        } } }
        download?.let { d -> item {
            if(downloading) { val total=d.progress.getLong("total",selected.size);val bytes=d.progress.getLong("bytes",0);Text("${d.progress.getString("label")?:"Sırada"} · ${bytes/1_000_000} / ${total/1_000_000} MB");LinearProgressIndicator(progress={(bytes.toFloat()/total.coerceAtLeast(1)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth());TextButton(onClick=vm::cancelDownload) {Text("İndirmeyi durdur")} }
            else if(d.state==androidx.work.WorkInfo.State.FAILED) Text(d.outputData.getString("error").orEmpty(),color=MaterialTheme.colorScheme.error)
        } }
        item { Text("Kullanılan: ${state.settings.modelName}",fontWeight=FontWeight.Bold);catalog.firstOrNull { it.downloadable && it.sha256==state.settings.modelHash }?.let { Text(it.scoreLabel,color=MaterialTheme.colorScheme.primary) };Text(status);if(installed && !changeModel && !downloading) TextButton(enabled=!busy,onClick={changeModel=true;choose=true}) { Text("Model değiştir") };if(busy) LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(enabled=!busy && !downloading,onClick={picker.launch(arrayOf("*/*"))}) {Text("Model dosyası seç")} }
        item { Button(enabled=state.settings.modelPath.isNotBlank() && !busy && !downloading,onClick={if(state.settings.paused) vm.resume() else vm.pause()}) {Text(if(state.settings.paused) "Devam et" else "Durdur")};TextButton(enabled=state.settings.paused,onClick=vm::retry) {Text("Eksik / hatalı olanları yeniden dene")} }
        item { QueueDots(state) }
    }
    if(choose) AlertDialog(onDismissRequest={choose=false},title={Text("Model seç · ${catalog.size} sürüm")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value=query,onValueChange={query=it},label={Text("Model ara")},singleLine=true,modifier=Modifier.fillMaxWidth())
        LazyColumn(Modifier.heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(catalog.filter { it.name.contains(query,ignoreCase=true) || it.family.contains(query,ignoreCase=true) },key={it.id}) { spec ->
                OutlinedCard(Modifier.fillMaxWidth().clickable { selected=spec;choose=false }) {
                    Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected.id==spec.id,{selected=spec;choose=false})
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text(spec.family,style=MaterialTheme.typography.titleSmall)
                            Text(spec.variantLabel,style=MaterialTheme.typography.labelMedium)
                            Text(spec.scoreLabel,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodyMedium)
                            Text(if(spec.downloadable) "${spec.size/1_000_000} MB · ${spec.formatLabel}" else "Mobil sürüm kullanılamıyor",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }},confirmButton={TextButton(onClick={choose=false}) {Text("Kapat")}})
    if(info) AlertDialog(onDismissRequest={info=false},title={Text(selected.name)},text={Text("${selected.scoreLabel}\n\n101 başlıkta öğretmen etiketleriyle uyum ölçümü. Puan, HF/BF16 kıyasından alınır; mobil model yeniden puanlanmadı. Telefon hızı veya genel zekâ puanı değildir.\n\n2 CPU iş parçacığı\n${selected.ram}\n2.048 token bağlam\nBellek kullanımı cihaz ve girdiye göre değişir.")},confirmButton={TextButton(onClick={info=false}) {Text("Tamam")}})
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun StatusDot(color:Color,label:String,count:Int?=null) {
    var show by remember { mutableStateOf(false) }
    Box(Modifier.size(if(count==null) 16.dp else 42.dp).background(color,androidx.compose.foundation.shape.CircleShape).combinedClickable(onClick={show=true},onLongClick={show=true}).semantics { contentDescription=if(count==null) label else "$label: $count" },contentAlignment=Alignment.Center) { count?.let { Text(it.toString(),color=Color.White,fontWeight=FontWeight.Bold) } }
    if(show) AlertDialog(onDismissRequest={show=false},text={Text(if(count==null) label else "$label: $count")},confirmButton={TextButton(onClick={show=false}) {Text("Tamam")}})
}
@Composable private fun QueueDots(state:ScreenState) {
    val counts=queueCounts(state.articles,state.settings)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
        listOf(Triple("BEKLIYOR","Bekleyen",Color(0xFFA97800)),Triple("TAMAM","Tamamlanan",Color(0xFF288954)),Triple("EKSIK","Eksik",Color(0xFFC06B16)),Triple("HATA","Hata",Color(0xFFCC3846))).forEach { (key,label,color)->StatusDot(color,label,counts[key]?:0) }
    }
}
@Composable private fun AnalysisBanner(state:ScreenState,vm:NewsViewModel) {
    val work by vm.analysisProgress.collectAsStateWithLifecycle()
    if(work!=null && !state.settings.paused) {
        val done=work!!.progress.getInt("done",0);val total=work!!.progress.getInt("total",0)
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal=16.dp,vertical=4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Text("${work!!.progress.getString("label")?:"Yapay zekâ hazırlanıyor"} · $done / $total",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium);TextButton(onClick=vm::pause) {Text("Durdur")} }
            LinearProgressIndicator(progress={if(total==0) 0f else done.toFloat()/total},modifier=Modifier.fillMaxWidth())
        }
    } else if(state.settings.paused && state.settings.modelPath.isNotBlank()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) { Text("Yapay zekâ duraklatıldı",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium);TextButton(onClick=vm::resume) {Text("Devam et")} }
    }
}
@Composable private fun ProfileAvatar(url:String?,name:String) {
    val bitmap by produceState<android.graphics.Bitmap?>(null,url) {
        if(!url.isNullOrBlank()) value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching {
            val c=(java.net.URL(canonicalUrl(url)).openConnection() as java.net.HttpURLConnection).apply {connectTimeout=8000;readTimeout=8000}
            try { if(c.responseCode==200) c.inputStream.use { val bytes=it.readLimited(512*1024);android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size) } else null } finally {c.disconnect()}
        }.getOrNull() }
    }
    if(bitmap!=null) Image(bitmap!!.asImageBitmap(),name,Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
    else Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer,androidx.compose.foundation.shape.CircleShape),contentAlignment=Alignment.Center) {Text(name.take(1))}
}
@Composable private fun ArticleDetail(group:List<Article>,state:ScreenState,vm:NewsViewModel,back:()->Unit) {
    val context=LocalContext.current;var correction by remember { mutableStateOf<Article?>(null) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { TextButton(onClick=back) {Text("‹ Akışa dön")} }
        items(group,key={it.id}) { article ->
            Column {
                Text(state.sources.firstOrNull { it.id==article.sourceId }?.name.orEmpty(),color=MaterialTheme.colorScheme.primary)
                Text(article.title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                Text(date(article.published),Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.labelMedium)
                RichBody(article.intro.ifBlank { "Bu haberin giriş metni kaynakta bulunmuyor." },state.settings.fontScale)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick={vm.save(article)}) {Text(if(article.saved) "Kaydı kaldır" else "Kaydet")}
                    TextButton(onClick={correction=article}) {Text("Analizi düzelt")}
                    TextButton(onClick={runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(article.url)))}.onFailure {vm.message.value="Bağlantı açılamadı"}}) {Text("Kaynağında aç")}
                    TextButton(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,"${article.title}\n${article.url}")},"Paylaş"))}) {Text("Paylaş")}
                }
            }
        }
    }
    LaunchedEffect(group.map { it.id }) { vm.markRead(group,true) }
    correction?.let { a->CorrectionDialog(a,{correction=null},{vm.correct(a,it);correction=null},{vm.clearCorrection(a);correction=null}) }
}
@Composable private fun RichBody(text:String,scale:Float) {
    val color=MaterialTheme.colorScheme.onSurface.toArgb()
    val rendered=remember(text) { formattedBody(text) }
    androidx.compose.ui.viewinterop.AndroidView(factory={android.widget.TextView(it).apply {setTextIsSelectable(true);setLineSpacing(8f,1.15f);movementMethod=android.text.method.LinkMovementMethod.getInstance()}},update={it.text=android.text.Html.fromHtml(rendered,android.text.Html.FROM_HTML_MODE_LEGACY);it.setTextColor(color);it.textSize=17f*scale},modifier=Modifier.fillMaxWidth())
}
internal fun formattedBody(text:String):String {
    return text.split(Regex("\n\\s*\n")).joinToString("") { paragraph ->
        var html=android.text.TextUtils.htmlEncode(paragraph)
        html=html.replace(Regex("\\*\\*(.+?)\\*\\*"),"<b>$1</b>").replace(Regex("__(.+?)__"),"<b>$1</b>").replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)"),"<i>$1</i>")
        html=html.lines().joinToString("<br>") { line -> if(line.startsWith("#")) "<b>"+line.trimStart('#',' ')+"</b>" else line }
        "<p>$html</p>"
    }
}
@Composable private fun AppSettings(state:ScreenState,vm:NewsViewModel) {
    var section by remember { mutableStateOf("") }
    BackHandler(section.isNotBlank()) {section=""}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) { if(section.isNotBlank()) TextButton(onClick={section=""}) {Text("‹")};Text(if(section.isBlank()) "Ayarlar" else section,style=MaterialTheme.typography.headlineMedium) }
        when(section) {
            "Katsayılar"->WeightSettings(state,vm)
            "Görünüm"->Column(Modifier.padding(20.dp)) { FilterChoice("Tema",listOf("SISTEM","ACIK","KOYU"),state.settings.theme) { if(it.isNotBlank()) vm.settings(state.settings.copy(theme=it)) };Text("Haber metni boyutu");Slider(state.settings.fontScale,{vm.settings(state.settings.copy(fontScale=it))},valueRange=0.85f..1.4f);Text("Örnek haber metni",fontSize=(17*state.settings.fontScale).sp) }
            "Loglar"->LogScreen(vm)
            else->Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { listOf("Katsayılar","Görünüm","Loglar").forEach { title -> OutlinedCard(onClick={section=title},modifier=Modifier.fillMaxWidth()) {Text(title,Modifier.padding(20.dp),style=MaterialTheme.typography.titleMedium)} } }
        }
    }
}
@Composable private fun LogScreen(vm:NewsViewModel) {
    val revision by vm.app.logs.revision.collectAsStateWithLifecycle();var category by remember { mutableStateOf("") }
    val rows by produceState<List<org.json.JSONObject>>(emptyList(),revision,category) { value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {vm.app.logs.rows().filter { category.isBlank() || it.optString("category")==category }.take(500)} }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {it?.let(vm::exportLogs)}
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=20.dp)) {FilterChoice("Tüm loglar",listOf("AI","MODEL","KAYNAK","X"),category){category=it};TextButton(onClick={export.launch("gundem-loglar.jsonl")}) {Text("Dışa aktar")};TextButton(onClick=vm::shareLogs) {Text("Paylaş")}}
        LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(rows.isEmpty()) item {Text("Henüz kayıt yok.")}
            items(rows) { row -> Column { Text("${java.time.Instant.ofEpochMilli(row.optLong("time")).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm:ss.SSS"))} · ${row.optString("category")} · ${row.optString("event")}",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodySmall);Text(row.optString("detail"),style=MaterialTheme.typography.bodySmall);if(row.has("elapsedMs")) Text("Süre: ${row.optLong("elapsedMs")} ms",color=MaterialTheme.colorScheme.primary);HorizontalDivider(Modifier.padding(top=8.dp)) } }
        }
    }
}
