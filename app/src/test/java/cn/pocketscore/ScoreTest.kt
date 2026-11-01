package cn.pocketscore

import org.junit.Assert.*
import org.junit.Test

class ScoreTest {
    @Test fun chordInputPreservesOtherStringsAndReplacesSameString() {
        val e=ScoreEditor(Score())
        e.place(0,0,4,0,Tone(0,3),false);e.place(0,0,4,0,Tone(1,2),false);e.place(0,0,4,0,Tone(0,5),false)
        assertEquals(setOf(Tone(0,5),Tone(1,2)),e.score.measures[0].events.single().tones.toSet())
    }
    @Test fun pianoHandsHaveIndependentTimelines() {
        val e=ScoreEditor(Score(instrument=Instrument.PIANO))
        e.place(0,0,16,0,Tone(28),false);e.place(0,0,8,1,Tone(14),false)
        assertEquals(2,e.score.measures[0].events.size)
    }
    @Test fun overlappingNotesAreRejectedWithoutChangingScore() {
        val e=ScoreEditor(Score());e.place(0,0,8,0,Tone(0,2),false);val before=e.score
        assertThrows(IllegalArgumentException::class.java){e.place(0,4,4,0,Tone(0,3),false)}
        assertEquals(before,e.score)
    }
    @Test fun measureOverflowIsRejected() {
        val e=ScoreEditor(Score(numerator=3))
        assertThrows(IllegalArgumentException::class.java){e.place(0,8,8,0,Tone(0,3),false)}
    }
    @Test fun undoRestoresDeletedChord() {
        val e=ScoreEditor(Score());e.place(0,0,4,0,Tone(0,3),false);val before=e.score
        e.delete(0,0,0);e.undo();assertEquals(before,e.score)
    }
    @Test fun codecRoundTripPreservesChineseAndPianoAccidentals() {
        val e=ScoreEditor(Score(title="月光练习",author="小林",instrument=Instrument.PIANO))
        e.place(0,0,4,0,Tone(28,1),false);e.place(0,0,8,1,null,true)
        assertEquals(e.score,ScoreCodec.decode(ScoreCodec.encode(e.score)))
    }
    @Test fun importedInvalidFretIsRejected() {
        val bad=Score(measures=listOf(Measure(listOf(Event(0,4,tones=listOf(Tone(0,25)))))))
        assertThrows(IllegalArgumentException::class.java){ScoreCodec.decode(ScoreCodec.encode(bad))}
    }
    @Test fun pdfPaginationCoversEveryMeasureExactlyOnce() {
        for(piano in listOf(true,false)) for(count in listOf(1,4,6,7,12,256)) {
            val pages=PdfLayout.pages(count,piano)
            assertEquals((0 until count).toList(),pages.flatMap { it.toList() })
            assertTrue(pages.all { it.count() <= if(piano)4 else 6 })
        }
    }
    @Test fun changingTimeSignatureCannotSilentlyLoseNotes() {
        val e=ScoreEditor(Score());e.place(0,12,4,0,Tone(0,0),false)
        assertThrows(IllegalArgumentException::class.java){e.replace(e.score.copy(numerator=3))}
        assertEquals(4,e.score.numerator)
    }
}
