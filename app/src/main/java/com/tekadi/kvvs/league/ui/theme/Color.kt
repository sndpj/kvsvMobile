package com.tekadi.kvvs.league.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * "Tekadi cricket" theme, refined against the actual TEKADI CRICKET logo
 * (IMG-20260814-WA0008/0009) rather than the earlier illustrative dashboard
 * mockup. Same warm ground-and-scoreboard direction — green, cream, orange
 * — but every hex below is pulled from the logo art itself: the helmet
 * shell and "CRICKET" lettering for green, the ribbon's outer band and bat
 * blade for orange, the stumps and wordmark fill for cream, the banner
 * behind "TEKADI" for ink. The logo reads more saturated/vivid than the
 * earlier mockup-derived palette (e.g. green was #0F5132, now #1A8449) —
 * this is a deliberate correction toward brand accuracy, not a redesign.
 *
 * One contrast consequence of going brighter on the primary green: the
 * live-scoring hero panel puts vivid orange TEXT directly on the green
 * background, and the old very-dark green had contrast to spare for that;
 * this brighter green doesn't. That one spot now uses TekadiGreenDim
 * instead of TekadiGreen — see OnlineLiveScorerScreen's ScoreHeader.
 *
 * This file keeps every OLD token name as a documented alias so no screen
 * file needed a rewrite for the refresh to take effect — see the alias
 * section below for the handful of tokens that carry a real design note.
 */

// ---- Primary — Tekadi green, from the helmet + "CRICKET" lettering ----
val TekadiGreen = Color(0xFF1A8449)       // header, primary actions, active nav
val TekadiGreenBright = Color(0xFF2FA05E) // ribbon highlight — hover/highlight variant
val TekadiGreenDim = Color(0xFF0F5C33)    // ribbon shadow — pressed/border variant, and the hero panel bg (see note above)

// ---- Backgrounds & surfaces — from the stumps and wordmark fill ----
val AppBackground = Color(0xFFF6F2E4)     // cream page background
val CardSurface = Color(0xFFFFFFFF)       // white cards
val CardSurfaceAlt = Color(0xFFEFE9D8)    // placeholder/alt panel surface (e.g. missing banner image)
val HeaderOverlay = Color(0x14FFFFFF)     // rgba(255,255,255,0.08) — the live-score panel on the green header

// ---- Borders ----
val HairlineBorder = Color(0xFFDCD6C4)

// ---- Accent — saffron orange, from the ribbon + bat blade ----
// Vivid form: safe on the dark green header or as a chip/badge fill (see
// the logo's ribbon). NOT safe as small text on the cream/white surfaces
// most screens actually use — see the Amber alias note below for why the
// widely-used alias is a different, darker shade.
val AccentOrange = Color(0xFFE17A2E)
val AccentOrangeDim = Color(0xFF9C4E1A)   // readable-on-light variant, used for most text/icon accents
val AccentOrangeDeep = Color(0xFF6B3B10)  // darkest variant, for button containers that need light content text on top

// ---- Wood + ball — new, from the bat/gloves and the cricket ball art ----
// Not wired into any screen yet; available for a future tertiary accent
// (e.g. a "coach"/"equipment" chip) without inventing an off-brand color.
val WoodBrown = Color(0xFF6B4423)
val BallGrey = Color(0xFF8C8A85)

// ---- Status chips (Home/Away style pills) ----
val ChipGreenBg = Color(0xFFEAF3DE)
val ChipGreenText = Color(0xFF173404)
val ChipAmberBg = Color(0xFFFAEEDA)
val ChipAmberText = Color(0xFF412402)

// ---- Danger / wickets ----
val DangerRed = Color(0xFFA32D2D)         // legible as text on white/cream
val DangerRedVivid = Color(0xFFD85A30)    // fine as a bg fill, not as small text

// ---- Trophy / top-performer gold ----
val TrophyGold = Color(0xFFD9A441)

// ---- Informational blue ----
val InfoBlueColor = Color(0xFF185FA5)

// ---- Text — ink, from the banner behind "TEKADI" ----
val TextPrimaryColor = Color(0xFF221E1B)    // body text on light surfaces
val TextSecondary = Color(0xFF5F5E5A)
val TextMutedColor = Color(0xFF888780)
val TextDisabledColor = Color(0xFFB4B2A9)
val TextOnDark = Color(0xFFF6F2E4)          // light text for the green header / image scrims

// ============================================================================
// Legacy aliases — every name a screen file already references. Values are
// chosen so each alias's SINGLE real-world role (mostly one-to-one with the
// tokens above) reads correctly under the refined palette.
// ============================================================================

val ArcTeal = TekadiGreen
val ArcTealBright = TekadiGreen          // was used for links/highlight numbers; TekadiGreen reads as a link on the cream/white surfaces
val ArcTealDim = TekadiGreenDim

// VoidBlack: was the page-background color everywhere (Surface(color =
// VoidBlack, ...) in ~8 screens) — that role maps cleanly to the cream
// background. The ONE place VoidBlack meant something else (dark text on a
// gold rank-1 badge in DashboardScreen's leaderboard) was changed at the
// call site to TextPrimary instead of forced through this alias — a light
// cream color there would have been unreadable on a gold chip.
val VoidBlack = AppBackground
val GraphitePanel = CardSurface
val GraphitePanelAlt = CardSurfaceAlt
val HudLine = HairlineBorder
val RepulsorGold = TrophyGold
val InfinityRed = DangerRed
val PulseBlue = InfoBlueColor

// TextPrimary: same one-exception story as VoidBlack — every usage is body
// text on a light card except the feed-banner overlay message in
// DashboardScreen (text sitting on a dark image scrim), which was pointed
// at TextOnDark directly instead.
val TextPrimary = TextPrimaryColor
val TextMuted = TextMutedColor
val TextDisabled = TextDisabledColor

val PitchBg = AppBackground
val PanelGreen = CardSurface
val PanelAlt = CardSurfaceAlt
val LineGreen = HairlineBorder
val Chalk = TextSecondary
val Muted = TextMutedColor

// Amber: the single most-used alias (headlines, the live score digits,
// prompts, the live-connection dot) — almost always small/medium text on a
// white or cream surface, where the vivid accent orange fails contrast
// (it's a bright, high-luminance hue — weak against a light background
// regardless of size). AccentOrangeDim is dark enough to read clearly while
// still unmistakably "the orange accent." The one spot that legitimately
// wants the vivid, logo-accurate orange — the live score panel itself —
// uses AccentOrange directly against TekadiGreenDim, not through this
// alias; see OnlineLiveScorerScreen's ScoreHeader.
val Amber = AccentOrangeDim
val AmberDim = AccentOrangeDeep
val WicketRed = DangerRed
val InfoBlue = InfoBlueColor

// ============================================================================
// Direction C · Seam — tinted canvas home screen (Tekadi Cricket App.dc.html)
// ============================================================================
val TintedCanvasBg = ChipGreenBg          // #EAF3DE — reuses the existing chip-green token, same hex
val TintedHairline = Color(0xFFCBDDB4)

// ============================================================================
// Role accent colors — the badge/chip that marks which mode you're in.
// Feature request: "role based theme" — resolved as an accent/badge layer rather than a full
// distinct palette per role (see the design-brief conversation): one brand identity everywhere,
// but a small always-visible color+label showing which role's capabilities are active. Colors
// chosen to read as "authority level" without inventing off-brand hues — reuses existing tokens
// where the role's real-world urgency already matches one (e.g. Scorer = the same red used for
// wickets/danger, since scoring actions are live and consequential).
// ============================================================================
val RoleColorSuperAdmin = TrophyGold
val RoleColorTournamentAdmin = InfoBlueColor
val RoleColorScorer = DangerRedVivid
val RoleColorTeamManager = TekadiGreen
val RoleColorUmpire = WoodBrown
// VIEWER intentionally has no badge at all — see CurrentUser.roleAccent — the unmarked default
// state, so a badge never has to say "you can't do anything here."
