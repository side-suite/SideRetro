package fi.palonkorpi.sideretro.emu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSystemTest {

    @Test
    fun `every declared extension resolves to its owning system case insensitively`() {
        for (system in GameSystem.entries) {
            for (extension in system.extensions) {
                assertEquals(system, GameSystem.forExtension(extension))
                assertEquals(system, GameSystem.forExtension(extension.uppercase()))
            }
        }
    }

    @Test
    fun `unsupported malformed and dot-prefixed extensions are rejected`() {
        assertNull(GameSystem.forExtension("zip"))
        assertNull(GameSystem.forExtension(".gba"))
        assertNull(GameSystem.forExtension(""))
        assertNull(GameSystem.forExtension("gba "))
    }

    @Test
    fun `all extensions are unique and equal the union advertised by systems`() {
        val declared = GameSystem.entries.flatMap { it.extensions }

        assertEquals(declared.toSet(), GameSystem.allExtensions)
        assertEquals(declared.size, GameSystem.allExtensions.size)
    }

    @Test
    fun `system capability flags capture the input differences`() {
        assertTrue(GameSystem.GBA.hasShoulders)
        assertFalse(GameSystem.GB.hasShoulders)
        assertFalse(GameSystem.GENESIS.hasShoulders)
        assertFalse(GameSystem.GENESIS.hasSelect)
        assertTrue(GameSystem.NES.hasSelect)
        assertTrue(GameSystem.GENESIS.isGenesis)
    }
}
