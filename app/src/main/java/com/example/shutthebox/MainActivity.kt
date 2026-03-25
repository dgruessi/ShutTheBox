package com.example.shutthebox

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.shutthebox.ui.theme.ShutTheBoxTheme

// ---------------------------------------------------------------------------
// Design tokens
// ---------------------------------------------------------------------------
private val BgCream       = Color(0xFFF5E6D0)
private val WoodDark      = Color(0xFF4E2B0E)
private val TileOpen      = Color(0xFF7B4120)
private val TileSelected  = Color(0xFFF59E0B)
private val TileClosed    = Color(0xFFCCBCAA)
private val WoodContainer = Color(0xFFEDD9BC)
private val PillBg        = Color(0xFFEDD9BC)
private val PillBgActive  = Color(0xFFB5743A)
private val PillText      = Color(0xFF3B1A05)
private val DotInk        = Color(0xFF2B1600)
private val CedarRed      = Color(0xFF8B2500)
private val WinGold       = Color(0xFFFFD700)
private val OkayActive    = Color(0xFF2E7D32)
private val OkayDisabled  = Color(0xFFBDBDBD)

// ---------------------------------------------------------------------------
// GameSoundManager
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

    private val diceIds = listOf(
        soundPool.load(context, R.raw.dice_roll_1, 1),
        soundPool.load(context, R.raw.dice_roll_2, 1),
        soundPool.load(context, R.raw.dice_roll_3, 1),
    )

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

    fun playDiceRoll()              = play(diceIds.random())
    fun playTileSelect()            = play(dropIds[1]!!, volume = 0.35f)
    fun playTilesClose(count: Int)  = play(dropIds[count.coerceIn(1, 4)]!!)
    fun playBoardSetup()            = play(setupId)

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
                    onRollDice           = { soundManager.playDiceRoll(); vm.rollDice() },
                    onToggleTile         = { tile -> soundManager.playTileSelect(); vm.toggleTile(tile) },
                    onPreviewCombination = vm::previewCombination,
                    onConfirm            = {
                        val count = state.selectedTiles.size
                        soundManager.playTilesClose(count)
                        vm.confirmSelection()
                    },
                    onReset              = { soundManager.playBoardSetup(); vm.reset() },
                    onToggleHighscores   = vm::toggleHighscores,
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
// GameScreen – stateless root
// ---------------------------------------------------------------------------

@Composable
fun GameScreen(
    state: GameState,
    onRollDice: () -> Unit,
    onToggleTile: (Int) -> Unit,
    onPreviewCombination: (List<Int>) -> Unit,
    onConfirm: () -> Unit,
    onReset: () -> Unit,
    onToggleHighscores: () -> Unit,
) {
    // Win-glow animation: 0 → 1 over 1.2 s when all tiles are shut
    val winProgress by animateFloatAsState(
        targetValue      = if (state.isWinner) 1f else 0f,
        animationSpec    = tween(1200, easing = FastOutSlowInEasing),
        label            = "winProgress",
    )

    if (state.showHighscores) {
        HighscoreScreen(highscores = state.highscores, onBack = onToggleHighscores)
        return
    }

    Scaffold(containerColor = BgCream) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ── Header: logo + highscore button ──────────────────────────────
            Row(
                modifier             = Modifier.fillMaxWidth(),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                GameLogo()
                HighscoreIconButton(onClick = onToggleHighscores)
            }

            // ── Tile row 1–9 (weight-based, scales with screen) ─────────────
            Row(modifier = Modifier.fillMaxWidth()) {
                for (tile in 1..9) {
                    TileCell(
                        modifier    = Modifier.weight(1f),
                        number      = tile,
                        isOpen      = tile in state.openTiles,
                        isSelected  = tile in state.selectedTiles,
                        winProgress = winProgress,
                        onClick     = { onToggleTile(tile) },
                    )
                }
            }

            // ── Two dice ────────────────────────────────────────────────────
            if (state.dice != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    DieFace(value = state.dice.first,  tileSize = 90.dp)
                    DieFace(value = state.dice.second, tileSize = 90.dp)
                }
            }

            // ── Combination pills + Okay button ─────────────────────────────
            if (state.availableCombinations.isNotEmpty()) {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier            = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
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

            // ── Roll button ──────────────────────────────────────────────────
            Button(
                onClick  = onRollDice,
                enabled  = !state.hasRolled && !state.isGameOver,
                colors   = ButtonDefaults.buttonColors(containerColor = TileOpen),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    "Würfeln",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp,
                    fontFamily = FontFamily.Serif,
                )
            }

            // ── Loss card (no dialog – only shown when game ends without win) ─
            if (state.isGameOver && !state.isWinner) {
                Card(
                    colors   = CardDefaults.cardColors(containerColor = Color(0xFFFFDACC)),
                    shape    = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier            = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Spiel vorbei",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 24.sp,
                            color      = CedarRed,
                        )
                        Text(
                            "${state.score} Punkte offen",
                            fontSize = 20.sp,
                            color    = PillText,
                        )
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

    // ── Win dialog – rendered as a Dialog overlay by Compose ────────────────
    if (state.isWinner) {
        WinDialog(
            turnCount         = state.turnCount,
            onNewGame         = onReset,
            onShowHighscores  = onToggleHighscores,
        )
    }
}

// ---------------------------------------------------------------------------
// GameLogo
// ---------------------------------------------------------------------------

@Composable
private fun GameLogo() {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text       = "SHUT THE",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize   = 14.sp,
            letterSpacing = 4.sp,
            color      = WoodDark,
        )
        Text(
            text       = "BOX",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.ExtraBold,
            fontSize   = 42.sp,
            letterSpacing = 4.sp,
            color      = TileOpen,
        )
    }
}

// ---------------------------------------------------------------------------
// HighscoreIconButton
// ---------------------------------------------------------------------------

@Composable
private fun HighscoreIconButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(TileOpen.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("🏆", fontSize = 24.sp)
    }
}

// ---------------------------------------------------------------------------
// TileCell – weight-based, CedarRed border when selected, gold glow on win
// ---------------------------------------------------------------------------

@Composable
private fun TileCell(
    modifier: Modifier = Modifier,
    number: Int,
    isOpen: Boolean,
    isSelected: Boolean,
    winProgress: Float,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    // Gold blends in for closed tiles as the win animation progresses
    val bg = when {
        !isOpen    -> lerp(TileClosed, WinGold, winProgress)
        isSelected -> TileSelected
        else       -> TileOpen
    }
    // Closed tiles fade up to full opacity as they turn gold
    val tileAlpha = if (!isOpen) 0.6f + 0.4f * winProgress else 1f

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .alpha(tileAlpha)
            .then(
                if (isSelected)
                    Modifier.border(3.dp, CedarRed, RoundedCornerShape(8.dp))
                else
                    Modifier
            )
            .shadow(if (isOpen) 4.dp else 0.dp, RoundedCornerShape(8.dp))
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(enabled = isOpen) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = number.toString(),      // always show the number
            color      = if (!isOpen)
                lerp(Color(0xFF6B6059), WoodDark, winProgress)
                         else Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize   = 14.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// DieFace – Canvas dots on white card, parameterised size
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
private fun DieFace(value: Int, tileSize: Dp = 90.dp) {
    Box(
        modifier = Modifier
            .size(tileSize)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dotRadius = size.minDimension * 0.12f
            dieDots[value]?.forEach { (rx, ry) ->
                drawCircle(DotInk, radius = dotRadius, center = Offset(size.width * rx, size.height * ry))
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
// OkayButton
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
// WinDialog – AlertDialog overlay, shown when all 9 tiles are shut
// ---------------------------------------------------------------------------

@Composable
private fun WinDialog(
    turnCount: Int,
    onNewGame: () -> Unit,
    onShowHighscores: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},   // intentional: player must press a button
        containerColor   = WoodContainer,
        title = {
            Text(
                "🎊 Perfekt!",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 26.sp,
                color      = TileOpen,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Alle 9 Klappen geschlossen!",
                    fontWeight = FontWeight.SemiBold,
                    color      = PillText,
                )
                Text("Züge: $turnCount", color = PillText)
                HorizontalDivider(color = PillBgActive, modifier = Modifier.padding(vertical = 4.dp))
                Text("Score gespeichert ✓", color = OkayActive, fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onNewGame,
                colors  = ButtonDefaults.buttonColors(containerColor = TileOpen),
            ) { Text("Neues Spiel") }
        },
        dismissButton = {
            TextButton(onClick = onShowHighscores) {
                Text("Bestenliste", color = TileOpen, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

// ---------------------------------------------------------------------------
// HighscoreScreen – full-screen overlay
// ---------------------------------------------------------------------------

@Composable
private fun HighscoreScreen(highscores: List<Int>, onBack: () -> Unit) {
    Scaffold(containerColor = BgCream) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Back button + title
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TileOpen)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Bestenliste",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 28.sp,
                    color      = TileOpen,
                )
            }

            HorizontalDivider(color = PillBgActive)

            if (highscores.isEmpty()) {
                Text(
                    "Noch keine abgeschlossenen Spiele.\nSpiel ein Spiel, um deinen ersten Score zu speichern!",
                    color      = PillText,
                    lineHeight = 24.sp,
                )
            } else {
                highscores.forEachIndexed { index, score ->
                    Card(
                        colors   = CardDefaults.cardColors(containerColor = PillBg),
                        shape    = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier              = Modifier
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                                .fillMaxWidth(),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text     = when (index) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "#${index + 1}" },
                                    fontSize = 22.sp,
                                )
                                Text(
                                    text       = if (score == 0) "Alle geschlossen!" else "$score Punkte",
                                    fontWeight = FontWeight.SemiBold,
                                    color      = PillText,
                                )
                            }
                            if (score == 0) Text("🏆", fontSize = 20.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "Niedrigerer Score = besser  ·  0 = alle Klappen zu",
                style = MaterialTheme.typography.bodySmall,
                color = PillText.copy(alpha = 0.55f),
            )
        }
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
                openTiles             = setOf(1, 2, 3, 4, 5, 6, 7),
                selectedTiles         = setOf(3, 4),
                dice                  = Pair(3, 4),
                availableCombinations = listOf(listOf(7), listOf(3, 4), listOf(2, 5), listOf(1, 2, 4)),
                hasRolled             = true,
            ),
            onRollDice           = {},
            onToggleTile         = {},
            onPreviewCombination = {},
            onConfirm            = {},
            onReset              = {},
            onToggleHighscores   = {},
        )
    }
}

@Preview(showBackground = true, name = "Spiel vorbei (Niederlage)")
@Composable
private fun PreviewGameOver() {
    ShutTheBoxTheme {
        GameScreen(
            state = GameState(
                openTiles             = setOf(3, 5),
                dice                  = null,
                availableCombinations = emptyList(),
                hasRolled             = true,
                isGameOver            = true,
            ),
            onRollDice           = {},
            onToggleTile         = {},
            onPreviewCombination = {},
            onConfirm            = {},
            onReset              = {},
            onToggleHighscores   = {},
        )
    }
}

@Preview(showBackground = true, name = "Bestenliste")
@Composable
private fun PreviewHighscores() {
    ShutTheBoxTheme {
        HighscoreScreen(
            highscores = listOf(0, 3, 7, 12, 21),
            onBack     = {},
        )
    }
}
