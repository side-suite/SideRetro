package fi.palonkorpi.sideretro.ui

/** Small pure rules used by the game list; Android views remain a single target per game row. */
object LibraryNavigation {
    /**
     * After deleting item [deletedIndex] from a [gameCount]-item list, focus stays at that index
     * if there is a following item, otherwise moves to the preceding item. With no games left,
     * index zero selects the first utility action (Add games).
     */
    fun selectionAfterDelete(deletedIndex: Int, gameCount: Int): Int = when {
        gameCount <= 1 -> 0
        else -> deletedIndex.coerceIn(0, gameCount - 2)
    }
}
