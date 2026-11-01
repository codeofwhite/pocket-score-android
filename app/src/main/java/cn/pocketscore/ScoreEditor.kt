package cn.pocketscore

class ScoreEditor(initial: Score) {
    var score: Score = initial; private set
    private val history = ArrayDeque<Score>()
    val canUndo get() = history.isNotEmpty()
    fun replace(next: Score) {
        next.validate()
        if (next == score) return
        history.addLast(score)
        if (history.size > 100) history.removeFirst()
        score = next
    }
    fun undo() { if(history.isNotEmpty()) score = history.removeLast() }
    fun place(measure: Int, tick: Int, duration: Int, lane: Int, tone: Tone?, rest: Boolean) {
        require(tick % duration == 0) { "请把光标移到时值对齐的位置（如四分音符从每拍起点输入）" }
        require(tick + duration <= score.capacity) { "本小节剩余时值不足，请换小节或缩短时值" }
        val events = score.measures[measure].events
        val old = events.find { it.tick == tick && it.lane == lane }
        val tones = if(rest) emptyList() else {
            requireNotNull(tone)
            (if(old != null && !old.rest && old.duration == duration) old.tones.filterNot { it.row == tone.row } else emptyList()) + tone
        }
        val next = Event(tick,duration,lane,rest,tones)
        require(events.none { it != old && it.lane == lane && tick < it.tick + it.duration && it.tick < tick + duration }) { "这里与已有音符重叠，请先删除原音符" }
        update(measure, events.filterNot { it == old } + next)
    }
    fun delete(measure: Int, tick: Int, lane: Int) {
        update(measure,score.measures[measure].events.filterNot { it.lane == lane && tick >= it.tick && tick < it.tick + it.duration })
    }
    private fun update(index: Int, events: List<Event>) {
        replace(score.copy(measures=score.measures.mapIndexed { i,m -> if(i==index) Measure(events.sortedWith(compareBy({it.lane},{it.tick}))) else m }))
    }
    fun addMeasure() = replace(score.copy(measures=score.measures + Measure()))
    fun deleteMeasure(index: Int) {
        require(score.measures.size > 1) { "至少保留一个小节" }
        replace(score.copy(measures=score.measures.filterIndexed { i,_ -> i != index }))
    }
}

object PdfLayout {
    fun pages(count: Int, piano: Boolean): List<IntRange> {
        require(count in 1..256)
        val perPage = if(piano) 4 else 6
        return (0 until count step perPage).map { it..minOf(it+perPage-1,count-1) }
    }
}
