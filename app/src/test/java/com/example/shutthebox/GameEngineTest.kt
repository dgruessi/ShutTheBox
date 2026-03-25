package com.example.shutthebox

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameEngineTest {

    private val allTiles = (1..9).toList()

    @Test
    fun `roll of 7 contains combinations 7, 1+6, 2+5, 3+4`() {
        val result = GameEngine.findCombinations(allTiles, 7)

        assertContains(result, listOf(7))
        assertContains(result, listOf(1, 6))
        assertContains(result, listOf(2, 5))
        assertContains(result, listOf(3, 4))
    }

    @Test
    fun `roll of 7 also contains multi-tile combinations like 1+2+4`() {
        val result = GameEngine.findCombinations(allTiles, 7)
        assertContains(result, listOf(1, 2, 4))
    }

    @Test
    fun `every returned combination sums to the target`() {
        val target = 7
        val result = GameEngine.findCombinations(allTiles, target)
        assertTrue(result.isNotEmpty())
        result.forEach { combo ->
            assertEquals(target, combo.sum(), "Combination $combo does not sum to $target")
        }
    }

    @Test
    fun `no duplicates within a combination`() {
        val result = GameEngine.findCombinations(allTiles, 7)
        result.forEach { combo ->
            assertEquals(combo.size, combo.distinct().size, "Combination $combo contains duplicates")
        }
    }

    @Test
    fun `only open tiles are used in combinations`() {
        val openTiles = listOf(3, 4, 7, 8, 9)
        val result = GameEngine.findCombinations(openTiles, 7)
        result.forEach { combo ->
            combo.forEach { tile ->
                assertContains(openTiles, tile)
            }
        }
    }

    @Test
    fun `impossible target returns empty list`() {
        val result = GameEngine.findCombinations(listOf(2, 4, 6), 7)
        assertEquals(emptyList(), result)
    }

    @Test
    fun `empty tile list returns empty list`() {
        val result = GameEngine.findCombinations(emptyList(), 7)
        assertEquals(emptyList(), result)
    }

    @Test
    fun `all tiles shut is a perfect game - score is zero`() {
        val state = GameState(openTiles = emptySet())
        assertEquals(0, state.score)
    }
}
