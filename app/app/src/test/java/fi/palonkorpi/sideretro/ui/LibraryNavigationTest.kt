package fi.palonkorpi.sideretro.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryNavigationTest {

    @Test
    fun `deleting a game keeps the following row selected`() {
        assertEquals(1, LibraryNavigation.selectionAfterDelete(deletedIndex = 1, gameCount = 4))
    }

    @Test
    fun `deleting final game selects the preceding row`() {
        assertEquals(2, LibraryNavigation.selectionAfterDelete(deletedIndex = 3, gameCount = 4))
    }

    @Test
    fun `deleting only game selects add games`() {
        assertEquals(0, LibraryNavigation.selectionAfterDelete(deletedIndex = 0, gameCount = 1))
    }
}
