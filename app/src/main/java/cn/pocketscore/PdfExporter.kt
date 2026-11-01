package cn.pocketscore

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

object PdfExporter {
    fun write(score: Score,output: OutputStream) {
        score.validate()
        val document=PdfDocument()
        try {
            val pages=PdfLayout.pages(score.measures.size,score.instrument==Instrument.PIANO)
            pages.forEachIndexed { pageIndex,range ->
                val page=document.startPage(PdfDocument.PageInfo.Builder(595,842,pageIndex+1).create())
                val c=page.canvas
                val text=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(25,45,40);textSize=20f}
                fun fitted(value: String,y: Float,size: Float) {
                    text.textSize=size
                    if(text.measureText(value)>523) text.textSize=size*523/text.measureText(value)
                    c.drawText(value,36f,y,text)
                }
                fitted(score.title,43f,20f)
                fitted("${score.author}  ·  ${score.instrument.label}  ·  ${score.numerator}/${score.denominator}  ·  ♩ = ${score.tempo}",65f,10f)
                val scale=523f/ScoreRenderer.WIDTH
                val rowHeight=if(score.instrument==Instrument.PIANO) 174f else 116f
                range.forEachIndexed { row,index ->
                    c.save();c.translate(36f,85f+row*rowHeight)
                    c.scale(scale,rowHeight/ScoreRenderer.height(score))
                    ScoreRenderer.draw(c,score,index)
                    c.restore()
                }
                text.textSize=9f;c.drawText("口袋乐谱  ·  ${pageIndex+1} / ${pages.size}",36f,812f,text)
                document.finishPage(page)
            }
            document.writeTo(output)
        } finally { document.close() }
    }
}
