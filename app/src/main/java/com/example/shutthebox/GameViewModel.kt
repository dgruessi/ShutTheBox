package com.example.shutthebox

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class GameState(
    val openTiles: Set<Int> = (1..9).toSet(),
    val selectedTiles: Set<Int> = emptySet(),
    val dice: Pair<Int, Int>? = null,
    val availableCombinations: List<List<Int>> = emptyList(),
    val hasRolled: Boolean = false,
    val isGameOver: Boolean = false,
    val turnCount: Int = 0,
    val highscores: List<Int> = emptyList(),    // top 5, ascending (lower = better)
    val showHighscores: Boolean = false,
) {
    val diceResult: Int? get() = dice?.let { it.first + it.second }
    val score: Int get() = openTiles.sum()
    val isWinner: Boolean get() = isGameOver && openTiles.isEmpty()
    val canConfirm: Boolean
        get() = selectedTiles.isNotEmpty() &&
                diceResult != null &&
                selectedTiles.sum() == diceResult
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("stb_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(GameState(highscores = loadHighscores()))
    val uiState: StateFlow<GameState> = _uiState.asStateFlow()

    // -------------------------------------------------------------------------
    // Highscore persistence
    // -------------------------------------------------------------------------

    private fun loadHighscores(): List<Int> {
        val raw = prefs.getString("highscores", "") ?: ""
        return if (raw.isEmpty()) emptyList()
        else raw.split(",").mapNotNull { it.toIntOrNull() }
    }

    private fun persistHighscore(score: Int) {
        val updated = (loadHighscores() + score).sorted().take(5)
        prefs.edit().putString("highscores", updated.joinToString(",")).apply()
        _uiState.update { it.copy(highscores = updated) }
    }

    // -------------------------------------------------------------------------
    // Game actions
    // -------------------------------------------------------------------------

    fun rollDice() {
        val d1 = (1..6).random()
        val d2 = (1..6).random()
        val state = _uiState.value
        val combinations = GameEngine.findCombinations(state.openTiles.toList(), d1 + d2)
        val gameOver = combinations.isEmpty()
        _uiState.update {
            it.copy(
                dice = Pair(d1, d2),
                selectedTiles = emptySet(),
                availableCombinations = combinations,
                hasRolled = true,
                isGameOver = gameOver,
            )
        }
        if (gameOver) persistHighscore(_uiState.value.score)
    }

    /** Toggle a single open tile in/out of the current selection. */
    fun toggleTile(tile: Int) {
        _uiState.update { state ->
            if (tile !in state.openTiles) return@update state
            val newSelected = if (tile in state.selectedTiles) {
                state.selectedTiles - tile
            } else {
                state.selectedTiles + tile
            }
            state.copy(selectedTiles = newSelected)
        }
    }

    /** Select an entire combination via a pill shortcut. */
    fun previewCombination(tiles: List<Int>) {
        _uiState.update { it.copy(selectedTiles = tiles.toSet()) }
    }

    /** Apply the current selection, close those tiles, save score if game ends. */
    fun confirmSelection() {
        _uiState.update { state ->
            if (!state.canConfirm) return@update state
            val newOpen = state.openTiles - state.selectedTiles
            val gameOver = newOpen.isEmpty()
            state.copy(
                openTiles = newOpen,
                selectedTiles = emptySet(),
                dice = null,
                availableCombinations = emptyList(),
                hasRolled = false,
                isGameOver = gameOver,
                turnCount = state.turnCount + 1,
            )
        }
        if (_uiState.value.isGameOver) persistHighscore(_uiState.value.score)
    }

    fun toggleHighscores() {
        _uiState.update { it.copy(showHighscores = !it.showHighscores) }
    }

    fun reset() {
        _uiState.update { current -> GameState(highscores = current.highscores) }
    }
}
