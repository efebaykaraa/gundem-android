package tr.gundem

import org.junit.Assert.*
import org.junit.Test

class CollapsingFeedTest {
    @Test fun headerTracksDragAndOnlyConsumesAvailableTravel() {
        assertEquals(-10f,headerOffset(0f,-10f,200f))
        val old=-180f
        val next=headerOffset(old,-50f,200f)
        assertEquals(-200f,next)
        assertEquals(-20f,next-old) // The remaining -30 px belong to the list.
        assertEquals(-185f,headerOffset(next,15f,200f))
        assertEquals(0f,headerOffset(-5f,40f,200f))
        assertEquals(0f,headerOffset(0f,-10f,0f),0.001f)
    }
}
