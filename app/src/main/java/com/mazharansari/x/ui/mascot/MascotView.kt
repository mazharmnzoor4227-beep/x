package com.mazharansari.x.ui.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mazharansari.x.R
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The X mascot logo, rendered as still frames from the real 3D GLB
 * (~/workspace/grok-workspace/public/mascot.glb, also bundled at
 * assets/mascot.glb for future use). Frames are 512x512 transparent PNGs
 * in res/drawable-nodpi, rendered with the mock's camera/lighting
 * (FluffyMascot.jsx: fov 29, warm key + cool fill + rim).
 *
 * Behaviour (mirrors the mock):
 * - Gentle idle bob (mock: turntable y = sin(t*1.45) * 0.008, roll 0.012 rad).
 * - Blink every ~3-5s for 170ms (mock blink curve).
 * - Drag horizontally to rotate through the yaw frames (-60..+60 deg).
 * - Eyes follow the pointer (gaze frames) when not dragging.
 * - Slow auto-rotate (ping-pong through the yaw frames) while [thinking].
 *
 * No 3D runtime is used: plain [Image] + [painterResource], no new deps.
 */
@Composable
fun MascotLogo(size: Dp, thinking: Boolean, modifier: Modifier = Modifier, onTap: (() -> Unit)? = null) {
    // Yaw frame indices: 0 = -60, 1 = -30, 2 = front, 3 = +30, 4 = +60.
    val yawFrames = remember {
        listOf(
            R.drawable.mascot_yaw_m60,
            R.drawable.mascot_yaw_m30,
            R.drawable.mascot_yaw_0,
            R.drawable.mascot_yaw_p30,
            R.drawable.mascot_yaw_p60,
        )
    }
    // Continuous yaw position (2f = front). Drag writes it; thinking animates it.
    val yaw = remember { Animatable(2f) }
    var dragging by remember { mutableStateOf(false) }
    var pointerDown by remember { mutableStateOf(false) }
    var blinking by remember { mutableStateOf(false) }

    // Gaze target in -1..1 (x: left/right, y: down/up), smoothed for display.
    var gazeTargetX by remember { mutableStateOf(0f) }
    var gazeTargetY by remember { mutableStateOf(0f) }
    val gazeX by animateFloatAsState(gazeTargetX, tween(150), label = "gazeX")
    val gazeY by animateFloatAsState(gazeTargetY, tween(150), label = "gazeY")

    // Blink loop: every 3-5s, shut for 170ms (mock: next blink 3.0-5.7s, 0.17s long).
    // Still in the test runtime: a repeating timer keeps the test rule from ever
    // reaching idle, and the eyes would be shut in half the screenshots.
    if (!com.mazharansari.x.ui.AnimationGate.inTestRuntime) LaunchedEffect(Unit) {
        while (true) {
            delay(3000L + Random.nextLong(2000L))
            blinking = true
            delay(170L)
            blinking = false
        }
    }

    // Thinking: slow auto-rotate through the yaw frames, ping-pong.
    // Mock spins at 0.36 rad/s (~21 deg/s); 120 deg of travel takes ~5.8s a leg.
    // Dragging pauses it; when thinking stops the mascot eases back to front.
    // Still in the test runtime (same AnimationGate rule as the bob below).
    if (!com.mazharansari.x.ui.AnimationGate.inTestRuntime) LaunchedEffect(thinking, dragging) {
        if (thinking && !dragging) {
            while (true) {
                yaw.animateTo(4f, tween(5800, easing = LinearEasing))
                yaw.animateTo(0f, tween(5800, easing = LinearEasing))
            }
        } else if (!thinking) {
            yaw.animateTo(2f, tween(350))
        }
    }

    // Idle bob: mock period is 2*PI/1.45 ~= 4.33s. Still (bobPhase = 0) in the test
    // runtime: a continuous frame loop keeps recomposing forever there, so the Compose
    // test rule never reaches idle and screen tests die with AppNotIdle — the same
    // rule as the orb, the thinking ribbon and shimmer/pulse (AnimationGate).
    // The transition lives on a local val (same shape as MessageViews' shimmer/pulse).
    // NOTE: InfiniteTransition.animateFloat is an extension in
    // androidx.compose.animation.core — it needs its own import (CI: unresolved
    // without it; MessageViews compiles only because it imports the package .*).
    val bobPhase = if (com.mazharansari.x.ui.AnimationGate.inTestRuntime) {
        0f
    } else {
        val bobTransition = rememberInfiniteTransition(label = "mascotBob")
        bobTransition.animateFloat(
            0f,
            (2f * Math.PI).toFloat(),
            infiniteRepeatable(tween(4330, easing = LinearEasing)),
            label = "bobPhase",
        ).value
    }
    val density = LocalDensity.current
    val bobYPx = with(density) { sin(bobPhase) * 1.5f.dp.toPx() }
    val bobRollDeg = sin(bobPhase * 0.5f) * 0.7f

    val touchSlop = LocalViewConfiguration.current.touchSlop
    val scope = rememberCoroutineScope()

    // Frame selection. Gaze/blink frames are front-only renders, so they only
    // apply near the front yaw; at an angle the yaw frame is shown instead.
    val yawValue = yaw.value
    val nearFront = abs(yawValue - 2f) < 0.5f
    val frameRes = when {
        blinking && nearFront -> R.drawable.mascot_blink
        nearFront && !dragging -> when {
            abs(gazeX) > abs(gazeY) && gazeX < -0.25f -> R.drawable.mascot_gaze_left
            abs(gazeX) > abs(gazeY) && gazeX > 0.25f -> R.drawable.mascot_gaze_right
            abs(gazeY) >= abs(gazeX) && gazeY > 0.25f -> R.drawable.mascot_gaze_up
            abs(gazeY) >= abs(gazeX) && gazeY < -0.25f -> R.drawable.mascot_gaze_down
            else -> R.drawable.mascot_yaw_0
        }
        else -> yawFrames[yawValue.roundToInt().coerceIn(0, 4)]
    }

    Image(
        painter = painterResource(frameRes),
        contentDescription = "X mascot",
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationY = bobYPx
                rotationZ = bobRollDeg
            }
            .semantics {
                this.contentDescription = "X mascot"
                role = Role.Image
                if (onTap != null) {
                    onClick(action = { onTap(); true })
                }
            }
            // Drag to rotate + press-move gaze + tap, in one pointer loop so
            // the gestures never fight each other. Events are not consumed,
            // so the hover loop below still sees them.
            .pointerInput(Unit) {
                // Pixel size of the (square) mascot, from the composable's
                // `size: Dp` via the local density. PointerInputScope.size is
                // shadowed by the `size` parameter here (CI: pxSize did
                // not resolve), so derive it from the Dp instead.
                val pxSize = with(density) { size.toPx() }
                awaitPointerEventScope {
                    var downX = 0f
                    var downY = 0f
                    var downTime = 0L
                    var totalDx = 0f
                    var isDragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> {
                                val pos = event.changes.firstOrNull()?.position ?: continue
                                pointerDown = true
                                isDragging = false
                                totalDx = 0f
                                downX = pos.x
                                downY = pos.y
                                downTime = event.changes.firstOrNull()?.uptimeMillis ?: 0L
                            }
                            PointerEventType.Move -> {
                                if (!pointerDown) continue
                                val change = event.changes.firstOrNull() ?: continue
                                val pos = change.position
                                val dx = change.positionChange().x
                                totalDx += dx
                                if (!isDragging && abs(totalDx) > touchSlop) {
                                    isDragging = true
                                    dragging = true
                                }
                                if (isDragging) {
                                    // A full-width swipe covers all 5 yaw frames.
                                    // snapTo is suspending and the awaitPointerEventScope block is
                                    // restricted — launch it on the composition scope instead.
                                    scope.launch { yaw.snapTo((yaw.value + dx * 4f / pxSize).coerceIn(0f, 4f)) }
                                } else {
                                    // Finger held still: eyes follow it (mock gaze).
                                    gazeTargetX = ((pos.x / pxSize) * 2f - 1f).coerceIn(-1f, 1f)
                                    gazeTargetY = (1f - (pos.y / pxSize) * 2f).coerceIn(-1f, 1f)
                                }
                            }
                            PointerEventType.Release -> {
                                val up = event.changes.firstOrNull()
                                val moved = if (up != null) {
                                    abs(up.position.x - downX) + abs(up.position.y - downY)
                                } else {
                                    Float.MAX_VALUE
                                }
                                val heldMs = (up?.uptimeMillis ?: 0L) - downTime
                                if (!isDragging && moved < touchSlop && heldMs in 0..350L) {
                                    onTap?.invoke()
                                }
                                pointerDown = false
                                isDragging = false
                                dragging = false
                                gazeTargetX = 0f
                                gazeTargetY = 0f
                            }
                            else -> Unit
                        }
                    }
                }
            }
            // Mouse hover (desktop): eyes follow the pointer without pressing.
            // On touch devices there is no hover; the press-move path above covers it.
            .pointerInput(Unit) {
                val pxSize = with(density) { size.toPx() }
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Move -> {
                                if (pointerDown) continue
                                val pos = event.changes.firstOrNull()?.position ?: continue
                                gazeTargetX = ((pos.x / pxSize) * 2f - 1f).coerceIn(-1f, 1f)
                                gazeTargetY = (1f - (pos.y / pxSize) * 2f).coerceIn(-1f, 1f)
                            }
                            PointerEventType.Exit -> {
                                gazeTargetX = 0f
                                gazeTargetY = 0f
                            }
                            else -> Unit
                        }
                    }
                }
            },
    )
}
