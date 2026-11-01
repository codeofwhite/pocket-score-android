package cn.pocketscore

import java.util.UUID

enum class Instrument(val label: String, val strings: Int) { GUITAR("吉他六线谱", 6), UKULELE("尤克里里四线谱", 4), PIANO("钢琴双行五线谱", 0) }
data class Tone(val row: Int, val value: Int = 0)
data class Event(val tick: Int, val duration: Int, val lane: Int = 0, val rest: Boolean = false, val tones: List<Tone> = emptyList())
data class Measure(val events: List<Event> = emptyList())
data class Score(
    val id: String = UUID.randomUUID().toString(), val title: String = "未命名乐谱", val author: String = "",
    val instrument: Instrument = Instrument.GUITAR, val numerator: Int = 4, val denominator: Int = 4,
    val tempo: Int = 80, val measures: List<Measure> = listOf(Measure())
) {
    val capacity: Int get() = numerator * 16 / denominator
    fun validate() {
        require(title.length <= 120 && author.length <= 80) { "标题或作者过长" }
        require(numerator in 1..12 && denominator in listOf(2,4,8,16)) { "不支持的拍号" }
        require(tempo in 30..240 && measures.size in 1..256) { "速度或小节数量超出范围" }
        measures.forEach { measure ->
            require(measure.events.size <= capacity * 2) { "小节事件过多" }
            measure.events.forEach { e ->
                require(e.duration in listOf(1,2,4,8,16) && e.tick >= 0 && e.tick + e.duration <= capacity) { "音符超出小节" }
                require(e.tick % e.duration == 0) { "音符起点必须对齐所选时值" }
                require(e.lane in (if (instrument == Instrument.PIANO) 0..1 else 0..0)) { "声部无效" }
                require(if(e.rest) e.tones.isEmpty() else e.tones.isNotEmpty()) { "音符内容无效" }
                require(e.tones.distinctBy { it.row }.size == e.tones.size) { "同一位置不能重复输入" }
                e.tones.forEach { t ->
                    require(if(instrument == Instrument.PIANO) t.row in 7..49 && t.value in -1..1 else t.row in 0 until instrument.strings && t.value in 0..24) { "音高或品数无效" }
                }
                require(measure.events.none { other -> other !== e && other.lane == e.lane && e.tick < other.tick + other.duration && other.tick < e.tick + e.duration }) { "同一声部的音符时值重叠" }
            }
        }
    }
}
