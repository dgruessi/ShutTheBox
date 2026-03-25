package com.example.shutthebox

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.shutthebox.ui.theme.ShutTheBoxTheme

// ---------------------------------------------------------------------------
// Design tokens
// ---------------------------------------------------------------------------
private val BgCream      = Color(0xFFF5E6D0)
private val TileOpen     = Color(0xFF7B4120)
private val TileSelected = Color(0xFFF59E0B)
private val TileClosed   = Color(0xFFCCBCAA)
private val PillBg       = Color(0xFFEDD9BC)
private val PillBgActive = Color(0xFFB5743A)
private val PillText     = Color(0xFF3B1A05)
private val DotInk       = Color(0xFF2B1600)
private val OkayActive   = Color(0xFF2E7D32)
private val OkayDisabled = Color(0xFFBDBDBD)

// ---------------------------------------------------------------------------
// GameSoundManager
//
// All sounds live in app/src/main/res/raw/ (WAV/MP3/OGG only – AIFF is not
// supported by Android's SoundPool).
//
// Loaded files:
//   dice_roll_1.wav / dice_roll_3.wav  – rolled dice (picked randomly)
//   single_drop.wav                    – 1 tile selected (soft tap feedback)
//   single_drop / two_drops /
//   three_drops / four_drops .wav      – tiles confirmed & shut (by count)
//   setup_board.wav                    – board reset after game over
//
// Note: dice_roll_2.aiff / dice_roll_3.aiff are present but NOT loaded –
//       Android does not support the AIFF format.
// ---------------------------------------------------------------------------
private class GameSoundManager(context: Context) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loadedIds = mutableSetOf<Int>()

    // Dice sounds – three variants, picked randomly on each roll
    private val diceIds = listOf(
        soundPool.load(context, R.raw.dice_roll_1, 1),
        soundPool.load(context, R.raw.dice_roll_2, 1),
        soundPool.load(context, R.raw.dice_roll_3, 1),
    )

    // Drop sounds indexed by tile count (1–4)
    private val dropIds = mapOf(
        1 to soundPool.load(context, R.raw.single_drop,  1),
        2 to soundPool.load(context, R.raw.two_drops,    1),
        3 to soundPool.load(context, R.raw.three_drops,  1),
        4 to soundPool.load(context, R.raw.four_drops,   1),
    )

    private val setupId = soundPool.load(context, R.raw.setup_board, 1)

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedIds.add(sampleId)
        }
    }

    /** Called when the dice are rolled. */
    fun playDiceRoll() = play(diceIds.random())

    /** Subtle tap feedback when a tile is selected or deselected. */
    fun playTileSelect() = play(dropIds[1]!!, volume = 0.35f)

    /** Full drop sound when tiles are actually shut. [count] = 1..4+. */
    fun playTilesClose(count: Int) = play(dropIds[count.coerceIn(1, 4)]!!)

    /** Played when the board is reset for a new game. */
    fun playBoardSetup() = play(setupId)

    private fun play(id: Int, volume: Float = 1f) {
        if (id in loadedIds) soundPool.play(id, volume, volume, 0, 0, 1f)
    }

    fun release() = soundPool.release()
}

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------

class MainActivity : ComponentActivity() {

    private lateinit var soundManager: GameSoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundManager = GameSoundManager(this)
        enableEdgeToEdge()
        setContent {
            ShutTheBoxTheme {
                val vm: GameViewModel = viewModel()
                val state by vm.uiState.collectAsState()
                GameScreen(
                    state                = state,
                    onRollDice           = {
                        soundManager.playDiceRoll()
                        vm.rollDice()
                    },
                    onToggleTile         = { tile ->
                        soundManager.playTileSelect()
                        vm.toggleTile(tile)
                    },
                    onPreviewCombination = vm::previewCombination,
                    onConfirm            = {
                        // Capture count BEFORE the state is mutated by the ViewModel
                        val count = state.selectedTiles.size
                        soundManager.playTilesClose(count)
                        vm.confirmSelection()
                    },
                    onReset              = {
                        soundManager.playBoardSetup()
                        vm.reset()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }
}

// ---------------------------------------------------------------------------
// GameScreen – stateless
// ---------------------------------------------------------------------------

@Composable
fun GameScreen(
    state: GameState,
    onRollDice: () -> Unit,
    onToggleTile: (Int) -> Unit,
    onPreviewCombination: (List<Int>) -> Unit,
    onConfirm: () -> Unit,
    onReset: () -> Unit,
) {
    Scaffold(containerColor = BgCream) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text  = "Shut the Box",
                style = MaterialTheme.typography.headlineLarge,
                color = TileOpen,
            )

            // Tile row 1..9
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (tile in 1..9) {
                    TileCell(
                        number     = tile,
                        isOpen     = tile in state.openTiles,
                        isSelected = tile in state.selectedTiles,
                        onClick    = { onToggleTile(tile) },
                    )
                }
            }

            // Two graphical dice
            if (state.dice != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    DieFace(value = state.dice.first)
                    DieFace(value = state.dice.second)
                }
            }

            // Combination pills + Okay button
            if (state.availableCombinations.isNotEmpty()) {
                Row(
                    modifier             = Modifier.fillMaxWidth(),
                    verticalAlignment    = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier              = Modifier.weight(1f),
                        verticalArrangement   = Arrangement.spacedBy(8.dp),
                    ) {
                        state.availableCombinations.forEach { combo ->
                            CombinationPill(
                                combo    = combo,
                                isActive = combo.toSet() == state.selectedTiles,
                                onClick  = { onPreviewCombination(combo) },
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    OkayButton(enabled = state.canConfirm, onClick = onConfirm)
                }
            }

            // Roll button
            Button(
                onClick  = onRollDice,
                enabled  = !state.hasRolled && !state.isGameOver,
                colors   = ButtonDefaults.buttonColors(containerColor = TileOpen),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Würfeln", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            // Game Over card
            if (state.isGameOver) {
                Card(
                    colors   = CardDefaults.cardColors(containerColor = Color(0xFFFFDACC)),
                    shape    = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier              = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment   = Alignment.CenterHorizontally,
                        verticalArrangement   = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text  = if (state.openTiles.isEmpty()) "Perfekt!" else "Spiel vorbei",
                            style = MaterialTheme.typography.headlineSmall,
                            color = PillText,
                        )
                        Text("Punkte: ${state.score}", fontSize = 22.sp, color = PillText)
                        Button(
                            onClick = onReset,
                            colors  = ButtonDefaults.buttonColors(containerColor = TileOpen),
                        ) { Text("Neu starten") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// TileCell – haptic on every tap
// ---------------------------------------------------------------------------

@Composable
private fun TileCell(
    number: Int,
    isOpen: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val bg = when {
        !isOpen    -> TileClosed
        isSelected -> TileSelected
        else       -> TileOpen
    }
    val textColor = if (!isOpen) Color(0xFF6B6059) else Color.White

    Box(
        modifier = Modifier
            .size(34.dp)
            .shadow(if (isOpen) 3.dp else 0.dp, RoundedCornerShape(8.dp))
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(enabled = isOpen) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = if (isOpen) number.toString() else "✓",
            color      = textColor,
            fontWeight = FontWeight.Bold,
            fontSize   = 15.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// DieFace – Canvas dots on white rounded card
// ---------------------------------------------------------------------------

private val dieDots = mapOf(
    1 to listOf(0.50f to 0.50f),
    2 to listOf(0.75f to 0.25f, 0.25f to 0.75f),
    3 to listOf(0.75f to 0.25f, 0.50f to 0.50f, 0.25f to 0.75f),
    4 to listOf(0.25f to 0.25f, 0.75f to 0.25f, 0.25f to 0.75f, 0.75f to 0.75f),
    5 to listOf(0.25f to 0.25f, 0.75f to 0.25f, 0.50f to 0.50f, 0.25f to 0.75f, 0.75f to 0.75f),
    6 to listOf(0.25f to 0.25f, 0.75f to 0.25f, 0.25f to 0.50f, 0.75f to 0.50f, 0.25f to 0.75f, 0.75f to 0.75f),
)

@Composable
private fun DieFace(value: Int) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .background(Color.White, RoundedCornerShape(14.dp))
            .padding(10.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.12f
            dieDots[value]?.forEach { (rx, ry) ->
                drawCircle(DotInk, radius = r, center = Offset(size.width * rx, size.height * ry))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// CombinationPill
// ---------------------------------------------------------------------------

@Composable
private fun CombinationPill(combo: List<Int>, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isActive) PillBgActive else PillBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text       = combo.joinToString(" + "),
            color      = if (isActive) Color.White else PillText,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 15.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// OkayButton – Confirm haptic on successful press
// ---------------------------------------------------------------------------

@Composable
private fun OkayButton(enabled: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val tint   = if (enabled) OkayActive else OkayDisabled

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(if (enabled) 4.dp else 0.dp, CircleShape)
                .clip(CircleShape)
                .background(tint)
                .clickable(enabled = enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Text("Okay", color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "Mid-game – Wurf 7, 3+4 gewählt")
@Composable
private fun PreviewMidGame() {
    ShutTheBoxTheme {
        GameScreen(
            state = GameState(
                openTiles            = setOf(1, 2, 3, 4, 5, 6, 7),
                selectedTiles        = setOf(3, 4),
                dice                 = Pair(3, 4),
                availableCombinations = listOf(listOf(7), listOf(3, 4), listOf(2, 5), listOf(1, 2, 4)),
                hasRolled            = true,
            ),
            onRollDice           = {},
            onToggleTile         = {},
            onPreviewCombination = {},
            onConfirm            = {},
            onReset              = {},
        )
    }
}

@Preview(showBackground = true, name = "Game Over")
@Composable
private fun PreviewGameOver() {
    ShutTheBoxTheme {
        GameScreen(
            state = GameState(
                openTiles            = setOf(3, 5),
                dice                 = null,
                availableCombinations = emptyList(),
                hasRolled            = true,
                isGameOver           = true,
            ),
            onRollDice           = {},
            onToggleTile         = {},
            onPreviewCombination = {},
            onConfirm            = {},
            onReset              = {},
        )
    }
}
