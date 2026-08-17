package fi.palonkorpi.sideretro.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenFilterTest {

    @Test
    fun `only reviewed filter choices are exposed`() {
        assertEquals(listOf(ScreenFilter.OFF, ScreenFilter.CRT), ScreenFilter.entries.toList())
    }

    @Test
    fun `legacy shader preferences migrate to CRT`() {
        listOf("CRISP", "SHARP", "LCD", null, "UNKNOWN").forEach { stored ->
            assertEquals(ScreenFilter.CRT, ScreenFilter.fromStoredName(stored))
        }
        assertEquals(ScreenFilter.OFF, ScreenFilter.fromStoredName("OFF"))
        assertEquals(ScreenFilter.CRT, ScreenFilter.fromStoredName("CRT"))
    }
}
