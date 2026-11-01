package cn.pocketscore

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF126B5E),secondary=Color(0xFF667B70),background=Color(0xFFF5F6F0),surface=Color(0xFFFFFEF9))) {
                PocketScore()
            }
        }
    }
    private fun message(text: String) = Toast.makeText(this,text,Toast.LENGTH_LONG).show()

    @Composable private fun PocketScore() {
        val storage=remember { ScoreStorage(this) }
        var editor by remember { mutableStateOf<ScoreEditor?>(null) }
        var revision by remember { mutableIntStateOf(0) }
        var measure by remember { mutableIntStateOf(0) }
        var tick by remember { mutableIntStateOf(0) }
        var lane by remember { mutableIntStateOf(0) }
        var row by remember { mutableIntStateOf(0) }
        var fret by remember { mutableIntStateOf(0) }
        var accidental by remember { mutableIntStateOf(0) }
        var duration by remember { mutableIntStateOf(4) }
        var autoNext by remember { mutableStateOf(false) }
        var dialog by remember { mutableStateOf<String?>(null) }
        var deleteTarget by remember { mutableStateOf<Score?>(null) }
        var busy by remember { mutableStateOf(false) }
        var snapshot by remember { mutableStateOf<Score?>(null) }
        val scope=rememberCoroutineScope()
        val score=remember(editor,revision) { editor?.score }
        fun open(s: Score) { editor=ScoreEditor(s); measure=0;tick=0;lane=0;row=if(s.instrument==Instrument.PIANO)28 else 0;duration=4;revision++ }
        fun change(action: () -> Unit) {
            try { action();revision++;editor?.let { storage.save(it.score) } }
            catch(e: Exception) { message(e.message ?: "操作失败") }
        }
        fun startExport(kind: String,uri: android.net.Uri) {
            val captured=snapshot ?: return
            busy=true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri,"wt")?.use { out ->
                            if(kind=="pdf") PdfExporter.write(captured,out) else out.write(ScoreCodec.encode(captured).toByteArray(Charsets.UTF_8))
                        } ?: error("无法打开保存位置")
                    }
                    message(if(kind=="pdf") "PDF 已保存" else "工程备份已保存")
                } catch(e: Exception) {message("导出失败：${e.message}")} finally {busy=false}
            }
        }
        val pdfPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> if(uri!=null) startExport("pdf",uri) }
        val backupPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri!=null) startExport("json",uri) }
        val importPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if(uri!=null) {
                busy=true
                scope.launch {
                    try {
                        val imported=withContext(Dispatchers.IO) {
                            val bytes=contentResolver.openInputStream(uri)?.use { input ->
                                val output=java.io.ByteArrayOutputStream()
                                val buffer=ByteArray(8192)
                                while(output.size()<=2_000_000) {
                                    val n=input.read(buffer,0,minOf(buffer.size,2_000_001-output.size()))
                                    if(n<0)break
                                    output.write(buffer,0,n)
                                }
                                output.toByteArray()
                            } ?: error("文件无法读取")
                            require(bytes.size<=2_000_000) {"工程文件超过 2 MB"}
                            ScoreCodec.decode(bytes.toString(Charsets.UTF_8)).copy(id=UUID.randomUUID().toString())
                        }
                        storage.save(imported);open(imported);message("已恢复为新的乐谱")
                    } catch(e: Exception) {message("恢复失败：${e.message}")} finally {busy=false}
                }
            }
        }
        BackHandler(enabled=editor!=null && !busy) {editor=null;revision++}
        Scaffold(containerColor=MaterialTheme.colorScheme.background,
            bottomBar={ if(score!=null) Surface(shadowElevation=8.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=12.dp,vertical=6.dp)) {
                    ScrollRow {
                        listOf(16 to "全音",8 to "二分",4 to "四分",2 to "八分",1 to "十六分").forEach { (d,label) ->
                            FilterChip(selected=duration==d,onClick={duration=d;tick=tick/d*d},label={Text(label)})
                        }
                    }
                    if(score.instrument==Instrument.PIANO) {
                        ScrollRow {
                            FilterChip(lane==0,{lane=0;row=28},{Text("右手 · 高音")})
                            FilterChip(lane==1,{lane=1;row=14},{Text("左手 · 低音")})
                            OutlinedButton(onClick={row=(row-7).coerceAtLeast(7)}){Text("低八度")}
                            OutlinedButton(onClick={row=(row+7).coerceAtMost(49)}){Text("高八度")}
                        }
                        ScrollRow {
                            (0..6).forEach { n -> val pitch=row/7*7+n
                                FilterChip(row%7==n,{row=pitch.coerceIn(7,49)},{Text(listOf("C","D","E","F","G","A","B")[n]+(pitch/7))})
                            }
                            listOf(-1 to "♭",0 to "♮",1 to "♯").forEach { (v,t) -> FilterChip(accidental==v,{accidental=v},{Text(t)}) }
                        }
                    } else {
                        ScrollRow {
                            (0 until score.instrument.strings).forEach { n -> FilterChip(row==n,{row=n},{Text("${n+1}弦")}) }
                        }
                        ScrollRow {
                            OutlinedButton(onClick={fret=(fret-1).coerceAtLeast(0)}){Text("−")}
                            Text("$fret 品",Modifier.padding(top=14.dp),fontWeight=FontWeight.Bold)
                            OutlinedButton(onClick={fret=(fret+1).coerceAtMost(24)}){Text("＋")}
                            (0..12).forEach { f -> FilterChip(fret==f,{fret=f},{Text("$f")}) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        Button(onClick={ change {
                            editor!!.place(measure,tick,duration,lane,Tone(row,if(score.instrument==Instrument.PIANO)accidental else fret),false)
                            if(autoNext && tick+duration<score.capacity)tick+=duration
                        } },modifier=Modifier.weight(1f)){Text("添加音符")}
                        OutlinedButton(onClick={change {editor!!.place(measure,tick,duration,lane,null,true);if(autoNext && tick+duration<score.capacity)tick+=duration}}){Text("休止")}
                        OutlinedButton(onClick={change {editor!!.delete(measure,tick,lane)}}){Text("删除")}
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        TextButton(onClick={tick=(tick-duration).coerceAtLeast(0)}){Text("← 上一拍")}
                        TextButton(onClick={autoNext=!autoNext}){Text(if(autoNext)"输入后前进 ✓" else "和弦输入 · 留在原拍")}
                        TextButton(onClick={tick=(tick+duration).coerceAtMost(score.capacity-1)}){Text("下一拍 →")}
                    }
                }
            } }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick={if(score!=null){editor=null;revision++}else dialog="help"}){Text(if(score!=null)"‹ 乐谱库" else "使用帮助")}
                    Text("口袋乐谱",Modifier.padding(top=12.dp),fontWeight=FontWeight.Bold,fontSize=20.sp)
                    TextButton(onClick={if(score!=null)dialog="settings" else dialog="new"}){Text(if(score!=null)"设置" else "＋ 新建")}
                }
                if(score==null) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Text("把旋律，\n写在手边。",fontSize=34.sp,lineHeight=43.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                        Text("吉他 · 尤克里里 · 钢琴\n离线创作，随时导出 PDF。",lineHeight=24.sp,color=MaterialTheme.colorScheme.secondary)
                        Button(onClick={dialog="new"},modifier=Modifier.fillMaxWidth().height(52.dp)){Text("＋ 创建一份乐谱")}
                        OutlinedButton(onClick={importPicker.launch(arrayOf("application/json","application/octet-stream","text/plain"))},modifier=Modifier.fillMaxWidth()){Text("从工程文件恢复")}
                        Text("我的乐谱",fontWeight=FontWeight.Bold,fontSize=20.sp)
                        val entries=remember(revision,score) {storage.list()}
                        if(entries.isEmpty()) Text("还没有乐谱。先选一种乐器，写下第一个音吧。",color=MaterialTheme.colorScheme.secondary)
                        entries.forEach { file ->
                            val loaded=remember(file,file.lastModified()){runCatching {storage.read(file)}}
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(loaded.getOrNull()?.title ?: "无法读取：${file.name}",fontWeight=FontWeight.Bold,fontSize=18.sp)
                                    Text(loaded.getOrNull()?.let{"${it.instrument.label} · ${it.measures.size} 小节 · ${it.numerator}/${it.denominator}"} ?: "工程可能损坏，原文件已保留",fontSize=13.sp)
                                    Row {
                                        TextButton(onClick={loaded.fold({open(it)},{message(it.message ?: "读取失败")})}){Text("打开编辑")}
                                        if(loaded.isSuccess)TextButton(onClick={deleteTarget=loaded.getOrThrow()}){Text("删除")}
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(score.title,fontSize=25.sp,fontWeight=FontWeight.Bold)
                        Text("${score.instrument.label}  ·  ${score.numerator}/${score.denominator}  ·  ♩ ${score.tempo}",color=MaterialTheme.colorScheme.secondary,fontSize=13.sp)
                        ScrollRow {
                            Button(onClick={snapshot=score;pdfPicker.launch(safeName(score.title)+".pdf")}){Text("导出 PDF")}
                            OutlinedButton(onClick={snapshot=score;backupPicker.launch(safeName(score.title)+".pocketscore.json")}){Text("备份工程")}
                            OutlinedButton(onClick={
                                val captured=score;busy=true
                                scope.launch {
                                    try {
                                        val file=withContext(Dispatchers.IO) {
                                            val dir=File(cacheDir,"exports").apply{mkdirs()};val f=File(dir,safeName(captured.title)+".pdf")
                                            f.outputStream().use {PdfExporter.write(captured,it)};f
                                        }
                                        val uri=FileProvider.getUriForFile(this@MainActivity,"$packageName.files",file)
                                        val send=Intent(Intent.ACTION_SEND).apply {type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);clipData=android.content.ClipData.newRawUri("乐谱 PDF",uri)}
                                        startActivity(Intent.createChooser(send,"分享乐谱 PDF"))
                                    } catch(e: Exception){message("分享失败：${e.message}")}finally{busy=false}
                                }
                            }){Text("分享 PDF")}
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            TextButton(onClick={measure=(measure-1).coerceAtLeast(0);tick=0},enabled=measure>0){Text("‹ 上一节")}
                            Text("第 ${measure+1} / ${score.measures.size} 小节",Modifier.padding(top=14.dp),fontWeight=FontWeight.Bold)
                            TextButton(onClick={measure++;tick=0},enabled=measure<score.measures.lastIndex){Text("下一节 ›")}
                        }
                        Surface(shape=RoundedCornerShape(16.dp),color=Color.White,tonalElevation=1.dp) {
                            Box(Modifier.horizontalScroll(rememberScrollState())) {
                                Canvas(Modifier.width(600.dp).height(if(score.instrument==Instrument.PIANO)268.dp else 198.dp)
                                    .pointerInput(score,measure,lane) { detectTapGestures { pos ->
                                        val x=pos.x/size.width*ScoreRenderer.WIDTH
                                        val y=pos.y/size.height*ScoreRenderer.height(score)
                                        tick=ScoreRenderer.tickAt(x,score)
                                        if(score.instrument==Instrument.PIANO)lane=if(y>170)1 else 0
                                        row=ScoreRenderer.rowAt(y,score.instrument==Instrument.PIANO,lane,score.instrument.strings)
                                    } }) {
                                    val c=drawContext.canvas.nativeCanvas
                                    c.save();c.scale(size.width/ScoreRenderer.WIDTH,size.height/ScoreRenderer.height(score))
                                    ScoreRenderer.draw(c,score,measure,tick,lane);c.restore()
                                }
                            }
                        }
                        Text("左右滑动查看谱面 · 点击选位置，再按“添加音符”",fontSize=12.sp,color=MaterialTheme.colorScheme.secondary)
                        Text("当前位置：${tick+1}/${score.capacity} 格（每格十六分音符）",fontSize=12.sp)
                        ScrollRow {
                            OutlinedButton(onClick={change{editor!!.undo();measure=measure.coerceAtMost(editor!!.score.measures.lastIndex);tick=tick.coerceAtMost(editor!!.score.capacity-1)}},enabled=editor!!.canUndo){Text("撤销")}
                            OutlinedButton(onClick={change{editor!!.addMeasure();measure=editor!!.score.measures.lastIndex;tick=0}}){Text("＋ 小节")}
                            TextButton(onClick={dialog="deleteMeasure"}){Text("删除本小节")}
                            TextButton(onClick={dialog="help"}){Text("帮助")}
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
        if(dialog=="new" || (dialog=="settings" && score!=null)) {
            val isNew=dialog=="new"
            SettingsDialog(if(isNew)Score() else score!!,isNew,onDismiss={dialog=null}) { next ->
                try {
                    next.validate()
                    if(isNew){storage.save(next);open(next)} else change {editor!!.replace(next);tick=tick.coerceAtMost(next.capacity-1)}
                    dialog=null
                }catch(e: Exception){message(e.message ?: "保存失败")}
            }
        }
        if(dialog=="deleteMeasure") AlertDialog(onDismissRequest={dialog=null},title={Text("删除当前小节？")},text={Text("该小节的所有音符都会删除，可使用撤销恢复。")},confirmButton={TextButton(onClick={change{editor!!.deleteMeasure(measure);measure=measure.coerceAtMost(editor!!.score.measures.lastIndex);tick=0};dialog=null}){Text("删除")}},dismissButton={TextButton(onClick={dialog=null}){Text("取消")}})
        if(deleteTarget!=null) AlertDialog(onDismissRequest={deleteTarget=null},title={Text("删除这份乐谱？")},text={Text("请先备份需要保留的工程。乐谱库删除后不能撤销。")},confirmButton={TextButton(onClick={try{storage.delete(deleteTarget!!);revision++}catch(e: Exception){message(e.message ?: "删除失败")};deleteTarget=null}){Text("删除")}},dismissButton={TextButton(onClick={deleteTarget=null}){Text("取消")}})
        if(dialog=="help") AlertDialog(onDismissRequest={dialog=null},title={Text("写下第一段旋律")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("1. 选乐器并新建。拍号默认 4/4，每格代表一个十六分音符。")
            Text("2. 点击谱面选择位置。吉他和尤克里里选弦、选品；钢琴选左右手、音名、八度和升降号。")
            Text("3. 选时值，按添加音符。同一位置添加不同弦或不同音高，即组成和弦。选中同弦再次添加会替换品数。")
            Text("4. 默认光标留在原拍，方便写和弦；点击“输入后前进”切换连续输入。箭头按当前时值移动。")
            Text("5. 同一声部音符不能重叠。修改时值需先删除旧音符。空白位置是未填写区域，正式乐谱请补休止符。")
            Text("6. 修改自动保存到本机。导出 PDF 用于打印或分享；备份工程才能继续编辑。卸载会删除本地乐谱，请先备份。")
            Text("第一版：C 大调 / A 小调五线谱，可逐音升降；支持全音至十六分音符。暂不支持附点、连音线、连音组、跨小节延音、播放及 PDF 反向导入。尤克里里按标准 G-C-E-A 定弦标注。")
        }},confirmButton={TextButton(onClick={dialog=null}){Text("知道了")}})
        if(busy) AlertDialog(onDismissRequest={},title={Text("正在处理文件")},text={LinearProgressIndicator(Modifier.fillMaxWidth())},confirmButton={})
    }
    private fun safeName(text: String)=text.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"),"_").take(60).ifBlank{"乐谱"}
}

@Composable private fun ScrollRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp),content=content)
}

@Composable private fun SettingsDialog(initial: Score,isNew: Boolean,onDismiss: () -> Unit,onSave: (Score) -> Unit) {
    var title by remember {mutableStateOf(if(isNew)"" else initial.title)}
    var author by remember {mutableStateOf(initial.author)}
    var instrument by remember {mutableStateOf(initial.instrument)}
    var numerator by remember {mutableStateOf(initial.numerator)}
    var denominator by remember {mutableStateOf(initial.denominator)}
    var tempo by remember {mutableStateOf(initial.tempo.toString())}
    var error by remember {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(isNew)"创建乐谱" else "乐谱设置")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title,{title=it.take(120)},label={Text("曲名")},singleLine=true)
            OutlinedTextField(author,{author=it.take(80)},label={Text("作者（可选）")},singleLine=true)
            if(isNew) Instrument.entries.forEach { i -> FilterChip(instrument==i,{instrument=i},{Text(i.label)}) }
            Text("拍号分子")
            ScrollRow { (1..12).forEach { n -> FilterChip(numerator==n,{numerator=n},{Text("$n")}) } }
            Text("拍号分母")
            ScrollRow { listOf(2,4,8,16).forEach { d -> FilterChip(denominator==d,{denominator=d},{Text("$d")}) } }
            OutlinedTextField(tempo,{tempo=it.filter {c->c.isDigit()}.take(3)},label={Text("速度（四分音符 / 分钟，30–240）")},singleLine=true)
            if(!isNew)Text("缩短拍号时，已有音符必须仍能放入小节；否则请先编辑音符。",fontSize=12.sp)
            if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(onClick={
        val bpm=tempo.toIntOrNull()
        if(bpm==null || bpm !in 30..240)error="请输入 30–240 的速度"
        else onSave(initial.copy(title=title.trim().ifBlank{"未命名乐谱"},author=author.trim(),instrument=instrument,numerator=numerator,denominator=denominator,tempo=bpm))
    }){Text(if(isNew)"开始创作" else "保存")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}
