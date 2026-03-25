package com.example.shutthebox

object GameEngine {

    /**
     * Finds all subsets of [openTiles] that sum to [target].
     * Returns each combination as a sorted list.
     */
    fun findCombinations(openTiles: List<Int>, target: Int): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        backtrack(openTiles.sorted(), target, startIndex = 0, current = mutableListOf(), result)
        return result
    }

    private fun backtrack(
        tiles: List<Int>,
        remaining: Int,
        startIndex: Int,
        current: MutableList<Int>,
        result: MutableList<List<Int>>,
    ) {
        if (remaining == 0) {
            result.add(current.toList())
            return
        }
        for (i in startIndex until tiles.size) {
            val tile = tiles[i]
            if (tile > remaining) break  // tiles are sorted – no need to continue
            current.add(tile)
            backtrack(tiles, remaining - tile, i + 1, current, result)
            current.removeAt(current.lastIndex)
        }
    }
}
