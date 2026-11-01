package cn.pocketscore

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.roundToInt

object ScoreRenderer {
    const val WIDTH = 760f
    const val START = 100f
    const val END = 730f
    fun height(s: Score) = if(s.instrument==Instrument.PIANO) 340f else 250f
    private fun paint(size: Float=16f, color: Int=Color.rgb(35,45,43)) = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=size; this.color=color; strokeWidth=1.2f }
    fun tickAt(x: Float,s: Score): Int = (((x-START)/(END-START)*s.capacity)-0.5f).roundToInt().coerceIn(0,s.capacity-1)
    fun x(tick: Int,s: Score) = START+(tick+0.5f)*(END-START)/s.capacity
    fun rowAt(y: Float, piano: Boolean, lane: Int, strings: Int): Int = if(piano) {
        val bottom=if(lane==0) 130f else 250f
        ((bottom-y)/5f).roundToInt().plus(if(lane==0)30 else 18).coerceIn(7,49)
    } else ((y-72f)/20f).roundToInt().coerceIn(0,strings-1)
    fun draw(canvas: Canvas,s: Score,index: Int,selectedTick: Int?=null,selectedLane: Int=0) {
        val p=paint()
        val piano=s.instrument==Instrument.PIANO
        val h=height(s)
        canvas.drawRect(0f,0f,WIDTH,h,paint(color=Color.WHITE))
        canvas.drawText("${index+1}",18f,24f,paint(17f,Color.GRAY))
        if(selectedTick!=null) {
            val cx=x(selectedTick,s); val step=(END-START)/s.capacity
            val highlight=paint(color=Color.rgb(218,239,231))
            canvas.drawRect(cx-step/2,40f,cx+step/2,h-25,highlight)
        }
        if(piano) {
            for(top in listOf(90f,210f)) for(i in 0..4) canvas.drawLine(START-55,top+i*10,END,top+i*10,p)
            canvas.drawLine(START-55,90f,START-55,250f,p)
            clef(canvas,START-36,90f,false);clef(canvas,START-36,210f,true)
            canvas.drawText("${s.numerator}",START-13,106f,paint(15f));canvas.drawText("${s.denominator}",START-13,124f,paint(15f))
            canvas.drawText("${s.numerator}",START-13,226f,paint(15f));canvas.drawText("${s.denominator}",START-13,244f,paint(15f))
            canvas.drawLine(END,90f,END,250f,p)
        } else {
            val names=if(s.instrument==Instrument.GUITAR) listOf("e","B","G","D","A","E") else listOf("A","E","C","G")
            names.forEachIndexed { i,name -> val y=72f+i*20;canvas.drawLine(START-18,y,END,y,p);canvas.drawText(name,42f,y+5,paint(17f)) }
            canvas.drawText("${s.numerator}/${s.denominator}",14f,h-30,paint(16f))
            canvas.drawLine(END,72f,END,72f+(s.instrument.strings-1)*20,p)
        }
        if(selectedTick!=null) for(t in 0 until s.capacity) {
            val xx=x(t,s)
            canvas.drawCircle(xx,h-36,if(t % (16/s.denominator)==0) 2f else 1f,paint(color=Color.LTGRAY))
        }
        val activeAccidentals=mutableMapOf<Pair<Int,Int>,Int>()
        s.measures[index].events.sortedBy { it.tick }.forEach { e ->
            val xx=x(e.tick,s)
            if(e.rest) { rest(canvas,xx,if(piano) (if(e.lane==0)112f else 232f) else 105f,e.duration) }
            else if(piano) {
                val bottom=if(e.lane==0)130f else 250f
                val ys=e.tones.map { bottom-(it.row-(if(e.lane==0)30 else 18))*5f }
                e.tones.sortedBy { it.row }.forEachIndexed { n,t ->
                    val yy=bottom-(t.row-(if(e.lane==0)30 else 18))*5f
                    val top=bottom-40
                    var ledger=bottom+10
                    while(ledger<=yy) { canvas.drawLine(xx-11,ledger,xx+11,ledger,p);ledger+=10 }
                    ledger=top-10
                    while(ledger>=yy) { canvas.drawLine(xx-11,ledger,xx+11,ledger,p);ledger-=10 }
                    val displaced=n>0 && e.tones.sortedBy { it.row }[n-1].row==t.row-1
                    val hx=xx+if(displaced)11f else 0f
                    val head=paint().apply { style=if(e.duration>=8) Paint.Style.STROKE else Paint.Style.FILL;strokeWidth=1.8f }
                    canvas.save();canvas.rotate(-18f,hx,yy);canvas.drawOval(hx-6,yy-4,hx+6,yy+4,head);canvas.restore()
                    val key=e.lane to t.row
                    val previous=activeAccidentals[key] ?: 0
                    if(t.value!=0 || previous!=0) canvas.drawText(when(t.value){1->"♯";-1->"♭";else->"♮"},xx-23,yy+5,paint(18f))
                    activeAccidentals[key]=t.value
                }
                if(e.duration<16) {
                    val low=ys.maxOrNull()!!; val high=ys.minOrNull()!!
                    val stemTop=high-30
                    canvas.drawLine(xx+6,low,xx+6,stemTop,p)
                    flags(canvas,xx+6,stemTop,e.duration)
                }
            } else {
                e.tones.forEach { t ->
                    val yy=72f+t.row*20
                    val text=t.value.toString();val tp=paint(19f);val width=tp.measureText(text)
                    canvas.drawRect(xx-width/2-3,yy-12,xx+width/2+3,yy+8,paint(color=Color.WHITE))
                    canvas.drawText(text,xx-width/2,yy+6,tp)
                }
                val baseline=72f+(s.instrument.strings-1)*20+25
                if(e.duration<16) {canvas.drawLine(xx,baseline,xx,baseline+23,p);flags(canvas,xx,baseline,e.duration)}
                if(e.duration>=8) canvas.drawOval(xx-4,baseline+19,xx+4,baseline+24,paint().apply { style=Paint.Style.STROKE })
            }
        }
        if(selectedTick!=null) canvas.drawText("${selectedTick+1}/${s.capacity} · ${if(piano) if(selectedLane==0) "右手" else "左手" else "TAB"}",START,h-8,paint(13f,Color.GRAY))
    }
    private fun flags(c: Canvas,x: Float,y: Float,d: Int) {
        val count=when(d){2->1;1->2;else->0}
        repeat(count){ i -> val yy=y+i*8;val path=Path().apply {moveTo(x,yy);cubicTo(x+16,yy+8,x+17,yy+14,x+8,yy+20)};c.drawPath(path,paint().apply {style=Paint.Style.STROKE;strokeWidth=2f}) }
    }
    private fun rest(c: Canvas,x: Float,y: Float,d: Int) {
        val p=paint()
        when(d){
            16,8 -> {c.drawLine(x-9,y,x+9,y,p);c.drawRect(x-6,if(d==16)y else y-5,x+6,if(d==16)y+5 else y,p)}
            4 -> {val path=Path().apply {moveTo(x-3,y-13);lineTo(x+4,y-5);lineTo(x-4,y+3);lineTo(x+4,y+10);quadTo(x-10,y+10,x-3,y+18)};c.drawPath(path,p.apply{style=Paint.Style.STROKE;strokeWidth=3f})}
            else -> {c.drawLine(x+5,y-9,x-3,y+14,p);c.drawCircle(x-2,y-6,4f,p);if(d==1)c.drawCircle(x-4,y+2,4f,p)}
        }
    }
    private fun clef(c: Canvas,x: Float,y: Float,bass: Boolean) {
        val p=paint().apply {style=Paint.Style.STROKE;strokeWidth=2.3f}
        val path=Path()
        if(bass){path.moveTo(x-10,y+13);path.cubicTo(x-13,y-4,x+17,y-5,x+7,y+17);path.quadTo(x+2,y+28,x-12,y+33);c.drawPath(path,p);c.drawCircle(x-9,y+11,3f,paint());c.drawCircle(x+16,y+5,2f,paint());c.drawCircle(x+16,y+15,2f,paint())}
        else {path.moveTo(x+2,y+45);path.cubicTo(x-17,y+52,x-12,y+18,x+2,y+25);path.cubicTo(x+17,y+29,x+9,y+45,x-3,y+37);path.moveTo(x,y+54);path.lineTo(x-4,y-12);path.cubicTo(x+15,y-12,x+10,y+4,x-6,y+17);path.cubicTo(x-25,y+37,x-9,y+47,x+2,y+45);c.drawPath(path,p)}
    }
}
