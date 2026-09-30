package com.tekadi.kvvs.league.util

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tekadi.kvvs.league.ui.theme.CardSurface
import com.tekadi.kvvs.league.ui.theme.TekadiGreen
import com.tekadi.kvvs.league.ui.theme.TekadiGreenDim
import com.tekadi.kvvs.league.ui.theme.TextMuted
import com.tekadi.kvvs.league.ui.theme.TextOnDark
import com.tekadi.kvvs.league.ui.theme.TextPrimary
import com.tekadi.kvvs.league.ui.theme.WicketRed
import kotlinx.coroutines.delay

/**
 * App-wide feedback convention (feature request: "add toast dialog for every info or exceptions
 * etc."): every screen surfaces its ViewModel's transient state through exactly two calls near
 * the top of its top-level composable —
 * ```
 * ErrorDialog(vm.error) { vm.error = null }
 * InfoToast(vm.info) { vm.info = null }
 * ```
 * - [InfoToast]: a quick, non-blocking confirmation for something that succeeded (saved, posted,
 *   submitted, deleted, ...). Auto-dismisses; a screen never needs to hold its own visibility
 *   state.
 * - [ErrorDialog]: an exception/failure the user should actually notice and acknowledge before
 *   continuing (a network error, a rejected request, a validation failure). Stays up until
 *   explicitly closed — a Toast can be missed entirely on a noisy ground with the phone in a
 *   pocket; this can't.
 *
 * Adapted from a design reference (a standalone `LiveScoreToast` sample) into this app's actual
 * convention, with three real fixes on top of it:
 *  1. **Centralized, not per-screen.** The reference required every call site to own its own
 *     `visible`/`currentEvent` state and remember to render the card in its layout. These two
 *     functions plug directly into the `error`/`info` strings already on every ViewModel — a
 *     screen adds two lines, not a state machine.
 *  2. **The exit animation actually plays.** The reference's `AnimatedVisibility` was gated by an
 *     `if (event == null)` a layer up in a typical call site, which unmounts the whole subtree the
 *     instant the message clears — the fade-out never gets a frame to run. This uses
 *     [MutableTransitionState] and only unmounts the [Popup] once `currentState` (not just
 *     `targetState`) has caught up, which is the pattern Compose's own animation docs recommend
 *     for exactly this case.
 *  3. **Themed, not hardcoded.** The reference's cards used literal hex colors (`Color(0xFF1E1E1E)`
 *     etc.) that don't exist anywhere else in this app. These use the actual Tekadi Cricket
 *     palette (`TekadiGreen`/`WicketRed`/`CardSurface`/...) so a toast looks like it belongs next
 *     to everything else instead of a different app bolted on.
 *
 * [LiveEventToast] keeps the reference's original idea for the screen it was clearly designed
 * for — a live scoring/viewing screen calling out a six, four, or wicket the instant it lands —
 * see [MatchEvent] and its use in `OnlineLiveScorerScreen`.
 */

private fun autoDismissMillis(message: String) = (1800 + message.length * 30L).coerceAtMost(4500)
private const val EXIT_FADE_MS = 250

@Composable
fun InfoToast(message: String?, onShown: () -> Unit) {
    var shown by remember { mutableStateOf(message) }
    val transitionState = remember { MutableTransitionState(false) }

    LaunchedEffect(message) {
        if (message != null) {
            shown = message
            transitionState.targetState = true
            delay(autoDismissMillis(message))
            transitionState.targetState = false
            onShown()
        }
    }

    ToastPopup(transitionState) {
        FeedbackCard(
            message = shown.orEmpty(),
            accent = TekadiGreen,
            containerColor = TekadiGreenDim,
            contentColor = TextOnDark,
            icon = null,
            onDismiss = null,
        )
    }
}

@Composable
fun ErrorDialog(message: String?, onDismiss: () -> Unit) {
    var shown by remember { mutableStateOf(message) }
    val transitionState = remember { MutableTransitionState(false) }

    LaunchedEffect(message) {
        if (message != null) {
            shown = message
            transitionState.targetState = true
        }
    }

    ToastPopup(transitionState) {
        FeedbackCard(
            message = shown.orEmpty(),
            accent = WicketRed,
            containerColor = CardSurface,
            contentColor = TextPrimary,
            icon = Icons.Filled.Warning,
            outlined = true,
            onDismiss = {
                transitionState.targetState = false
                onDismiss()
            },
        )
    }
}

/** The three live-match moments worth calling out on their own, distinct from a plain info toast. */
enum class MatchEventType(val accent: Color, val emoji: String) {
    WICKET(WicketRed, "🎯"),
    SIX(TekadiGreen, "🔥"),
    FOUR(TekadiGreenDim, "⚡"),
}

data class MatchEvent(val type: MatchEventType, val message: String)

/** Same shape as [InfoToast] but with the event's own accent color/emoji instead of the generic info styling. */
@Composable
fun LiveEventToast(event: MatchEvent?, onShown: () -> Unit) {
    var shown by remember { mutableStateOf(event) }
    val transitionState = remember { MutableTransitionState(false) }

    LaunchedEffect(event) {
        if (event != null) {
            shown = event
            transitionState.targetState = true
            delay(autoDismissMillis(event.message))
            transitionState.targetState = false
            onShown()
        }
    }

    ToastPopup(transitionState) {
        val current = shown
        if (current != null) {
            FeedbackCard(
                message = current.message,
                accent = current.type.accent,
                containerColor = TekadiGreenDim,
                contentColor = TextOnDark,
                icon = null,
                emoji = current.type.emoji,
                onDismiss = null,
            )
        }
    }
}

@Composable
private fun ToastPopup(transitionState: MutableTransitionState<Boolean>, content: @Composable () -> Unit) {
    // Keeps the Popup mounted for exactly as long as it's either showing or still animating out —
    // currentState only catches up to targetState once AnimatedVisibility's exit transition
    // finishes, so this is what makes the fade-out actually visible instead of an instant cut.
    if (!transitionState.currentState && !transitionState.targetState) return
    Popup(alignment = Alignment.BottomCenter, properties = PopupProperties(focusable = false)) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            ),
            exit = fadeOut(animationSpec = tween(EXIT_FADE_MS)),
        ) {
            Box(Modifier.fillMaxWidth().padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun FeedbackCard(
    message: String,
    accent: Color,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    emoji: String? = null,
    outlined: Boolean = false,
    onDismiss: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = if (outlined) BorderStroke(1.dp, accent.copy(alpha = 0.5f)) else null,
        tonalElevation = if (outlined) 3.dp else 6.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null || emoji != null) {
                Box(
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (emoji != null) {
                        Text(emoji, fontSize = 16.sp)
                    } else if (icon != null) {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
            }

            Text(
                text = message,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )

            if (onDismiss != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp).clickable(onClick = onDismiss),
                )
            }
        }
    }
}
