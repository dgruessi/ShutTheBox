# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run all unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.example.shutthebox.GameEngineTest"

# Build debug APK
./gradlew assembleDebug

# Lint
./gradlew lint
```

## Architecture

**Package:** `com.example.shutthebox`

### Core files

| File | Role |
|------|------|
| `GameEngine.kt` | Pure Kotlin object – subset-sum backtracking. No Android deps. |
| `GameViewModel.kt` | `ViewModel` holding `StateFlow<GameState>`. Single source of truth. |
| `MainActivity.kt` | Thin host – collects state, passes lambdas down to `GameScreen`. |

### Data flow

```
GameEngine.findCombinations(openTiles, diceRoll)
        ↓
GameViewModel._uiState: MutableStateFlow<GameState>
        ↓  (collectAsState)
GameScreen(state, onRollDice, onSelectCombination, onReset)   ← stateless
```

`GameScreen` is stateless – it only renders `GameState` and fires callbacks. All mutations go through `GameViewModel`.

### GameState lifecycle

1. Initial: tiles 1–9 open, `hasRolled = false`
2. `rollDice()` → `diceResult` set, `availableCombinations` computed, `hasRolled = true`
3. `selectCombination(tiles)` → tiles removed from `openTiles`, `hasRolled = false`
4. `isGameOver = true` when no combinations exist OR `openTiles` is empty

### Key constraints

- `GameEngine` must remain free of Android/Compose imports (testable with plain JVM).
- Tiles are always in range 1–9.
- `score = openTiles.sum()` – lower is better; 0 is a perfect game.
