package cn.pocketscore

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ScoreCodec {
    fun encode(s: Score): String = JSONObject().apply {
        put("version",1); put("id",s.id); put("title",s.title); put("author",s.author); put("instrument",s.instrument.name)
        put("numerator",s.numerator); put("denominator",s.denominator); put("tempo",s.tempo)
        put("measures",JSONArray().apply { s.measures.forEach { m -> put(JSONArray().apply { m.events.forEach { e -> put(JSONObject().apply {
            put("tick",e.tick); put("duration",e.duration); put("lane",e.lane); put("rest",e.rest)
            put("tones",JSONArray().apply { e.tones.forEach { t -> put(JSONObject().put("row",t.row).put("value",t.value)) } })
        }) } }) } })
    }.toString(2)
    fun decode(text: String): Score {
        require(text.length <= 2_000_000) { "工程文件过大" }
        val j = JSONObject(text)
        require(j.getInt("version") == 1) { "不支持的工程版本" }
        val ms = j.getJSONArray("measures")
        require(ms.length() in 1..256) { "小节数量无效" }
        return Score(id=j.getString("id"),title=j.getString("title"),author=j.getString("author"),instrument=Instrument.valueOf(j.getString("instrument")),numerator=j.getInt("numerator"),denominator=j.getInt("denominator"),tempo=j.getInt("tempo"),measures=(0 until ms.length()).map { i ->
            val es=ms.getJSONArray(i)
            require(es.length() <= 384) { "事件过多" }
            Measure((0 until es.length()).map { k -> val e=es.getJSONObject(k); val ts=e.getJSONArray("tones")
                require(ts.length() <= 43) { "和弦音符过多" }
                Event(e.getInt("tick"),e.getInt("duration"),e.getInt("lane"),e.getBoolean("rest"),(0 until ts.length()).map { t -> Tone(ts.getJSONObject(t).getInt("row"),ts.getJSONObject(t).getInt("value")) })
        }) }).also { require(it.id.matches(Regex("[a-zA-Z0-9-]{1,80}"))) { "工程编号无效" }; it.validate() }
    }
}
class ScoreStorage(context: Context) {
    private val dir = File(context.filesDir,"scores").apply { mkdirs() }
    fun list(): List<File> = dir.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    fun read(file: File): Score = ScoreCodec.decode(file.readText())
    fun save(score: Score) {
        score.validate()
        require(score.id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val atomic=AtomicFile(File(dir,"${score.id}.json"))
        val stream=atomic.startWrite()
        try { stream.write(ScoreCodec.encode(score).toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch(e: Exception) { atomic.failWrite(stream); throw e }
    }
    fun delete(score: Score) { require(File(dir,"${score.id}.json").delete()) { "删除失败" } }
}
