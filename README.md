# Cricket Scorer — Android App (Kotlin + Jetpack Compose)

A native Android app for the Cricket Scoring Application (SRS v1.0). Three
things are fully wired end-to-end against the real backend:

1. **Dashboard** (the post-login landing screen): latest match-summary/announcement
   feed with banner images, a head-to-head "battle count" widget, role-based
   action buttons, and top-5 run-scorer/wicket-taker leaderboards.
2. **Online scoring**: log in, browse/create a match, run the toss, pick openers,
   score ball-by-ball — every action is a live API call, and the UI renders
   exactly what the backend returns.
3. **Offline demo mode**: the original fully-local scoring engine, reachable
   via a link on the login screen. No account, no backend, no network.

## Theme

Primary color is the requested `#289C85`, used as the "arc reactor" accent in
an original **hero command-center** design — deep graphite backgrounds, glowing
teal accents, gold highlights for top performers, angular "cut-corner" panels
(`ui/theme/HeroShapes.kt`) as the app's one recurring shape signature. This is
an original aesthetic built from color/geometry/typography — **not** actual
Marvel characters, logos, or franchise artwork, which can't be reproduced here.
See `ui/theme/Color.kt` for the full palette and `HeroShapes.kt` for the panel
shape used throughout.

## What's implemented

### Dashboard (new)
- **Feed carousel** (`ui/dashboard/DashboardScreen.kt`): latest 5 items from
  `GET /api/feeds/recent`, combining auto-generated match-summary posts and
  Super-Admin-authored announcements, each with an optional banner image
  (loaded via Coil). Tapping a match-summary card opens History Detail.
- **Head-to-head "battle count"** (top-right widget): wins/ties between two
  teams. **Assumption**: since a generic dashboard has no inherent "current"
  pair of teams, this defaults to the two teams from the most recent completed
  match (`DashboardViewModel.loadHeadToHeadForMostRecentMatch`). A more
  deliberate design would let the user pick which rivalry to feature.
- **Role-based actions, shown as enabled/disabled rather than hidden**: every
  action (Matches, Teams, Join Team, Post Feed) is always visible, so the full
  feature set is discoverable — a role-gated one you don't have access to
  renders disabled with a "Requires X role" caption instead of disappearing.
  `ui/dashboard/DashboardScreen.kt`'s `ActionChip` is the reusable piece; see
  `data/CurrentUser.kt` for the role checks it reads.
- **Top 5 batsmen / bowlers**: `GET /api/dashboard/top-batsmen` /
  `top-bowlers`, ranked by total runs / total wickets across every recorded
  innings (see backend README for why this metric, not average/economy).
- **History Detail** (`ui/dashboard/HistoryDetailScreen.kt`): full scorecard
  for any match, reached by tapping a match-summary feed card.
- **Feed posting** (`ui/dashboard/FeedPostScreen.kt`): Super-Admin-only form
  (message + optional image URL) — `deleteFeed` exists in `ApiService` but
  has no UI yet.

### Team & Player management (new — Modules 3–4)
- **Team Management** (`ui/team/TeamManagementScreen.kt` + `TeamManagementViewModel.kt`):
  tournament → team list → team detail. Add/remove teams; add/remove players
  on a team's roster (name, jersey number, age, role, batting style, bowling
  style — all of Module 4's Player Details). Gated behind Team Manager (or
  higher) on the Dashboard's action row.
- **Invite Players**: "Generate Invite Code" on a team's detail screen calls
  `POST /api/teams/{id}/invite` and displays the resulting code — there's no
  email/SMS provider anywhere in this stack, so sharing it is manual (text,
  WhatsApp, whatever). Redeeming it is a separate, always-available action —
  **"Join Team"** (`ui/team/JoinTeamScreen.kt`) — since redeeming an invite is
  something the invitee does for themselves, not something that should need
  Team Manager access.
- **Player Statistics**: `ApiService.getPlayerStats()` exists (matches, runs,
  wickets, strike rate, economy, highest score) but **has no UI screen yet** —
  the backend endpoint is ready and tested via the API reference doc; wiring a
  player-detail screen to show it is a natural next step.

Note: an earlier pass of this README said "tournament/team/player creation
intentionally stays out of scope for this app" — that's now only true for
*tournaments*. Team and player management got a real mobile surface in this
pass, once the backend actually supported captain/manager fields and the
Invite Players feature.

### Online scoring mode
- **Login** (`ui/auth/`): real `POST /api/auth/login`, session persisted via
  DataStore (`data/TokenStore.kt`), auto-skips the login screen on restart.
  Also now populates `data/CurrentUser.kt` (role + name), which the Dashboard
  reads for role-based UI without needing it passed through navigation.
- **Match browsing & creation** (`ui/match/`): tournaments → matches, plus a
  minimal "+ New Match" form. Tournament *creation* still stays out of scope
  for this app — assumed to happen via the backend directly, or a future admin tool.
- **Live scoring** (`ui/scoring/OnlineLiveScorerScreen.kt` + `OnlineScorerViewModel.kt`):
  toss → openers → ball-by-ball → result, entirely driven by the backend's
  `MatchScorecardDto`. Resumable — reopening a match picks up wherever it left off.

### Offline demo mode (unchanged from the original scaffold)
- **Scoring engine** (`data/MatchModels.kt`): pure Kotlin, unit-tested, no
  Android dependencies — see "Tests" below.
- **Offline queue** (`data/OfflineQueue.kt`): built and tested, not yet wired
  into online mode (see "Known gaps").

## Known gaps and rough edges (read before relying on this)

- **Head-to-head defaults to the most recent match's teams** rather than a
  deliberately chosen rivalry — see above. Fine for a first cut, not ideal UX.
- **The client doesn't pre-filter eligible bowlers** in the next-bowler picker
  — shows the entire bowling roster; the backend still enforces eligibility
  correctly and returns a clear error on a bad pick, but the UI doesn't gray
  out ineligible names the way the offline demo's locally-simulated picker does.
- **Online mode doesn't use `OfflineQueue`.** Every action is a synchronous,
  awaited API call — a dropped connection mid-over fails visibly rather than
  queuing for retry. Wiring true offline-then-sync into online mode is left
  as a next step (it also needs a conflict-resolution story if the same match
  is scored from two devices).
- **`deleteFeed` has no UI** — the endpoint exists and works, nothing calls it yet.
- **New accounts default to Viewer** — someone needs Scorer/Tournament Admin/
  Super Admin to use most of this app. Promoting a user currently requires a
  direct database update (see backend README).
- The `tossWinnerTeamId` gap from an earlier pass **is now fixed** — the
  backend's `MatchResponse` exposes it directly, and `OnlineScorerViewModel`
  no longer needs the old name-matching workaround.
- Push notifications, QR team registration, and multi-language are still not implemented.

## First-time setup

This project does **not** include the binary `gradle-wrapper.jar` (can't be
generated without network access in the environment that produced it):

1. **Easiest — open in Android Studio.** It detects the missing wrapper jar
   and offers to regenerate it.
2. **Command line**, if you have Gradle installed locally:
   ```bash
   gradle wrapper --gradle-version 8.7
   ```

## Running against the backend

1. Start the backend (`cricket-backend/README.md`) — by default on `localhost:8080`.
   Make sure all four migrations have been applied (Flyway does this
   automatically on startup — see the backend README; `V4__team_invite.sql`
   is needed for the Invite Players feature below).
2. Emulator reaches the host at `10.0.2.2`, already set as `API_BASE_URL` in
   `app/build.gradle.kts`. On a physical device, use your machine's LAN IP instead.
3. You'll need a user promoted past Viewer — see the backend README's curl
   walkthrough for registering and promoting via direct DB update.
4. Build & run: `./gradlew installDebug`, or the Run button in Android Studio.
5. First launch: **Sign in** → land on the **Dashboard**. Tap **Matches** to
   score, **Teams** (if Team Manager+) to manage rosters and generate invite
   codes, **Join Team** to redeem one, or (Super Admin) **Post Feed** to
   publish an announcement. Or tap **"Try the offline demo instead"** on the
   login screen to skip all of that.

## Project layout

```
app/src/main/java/com/cricketapp/mobile/
  MainActivity.kt                 — entry point, hosts AppNavHost
  data/
    MatchModels.kt                — offline-demo data classes + ScoringEngine (pure Kotlin, unit-tested)
    OfflineQueue.kt                — DataStore-backed offline queue (built, not wired into online mode)
    TokenStore.kt                  — persistent login session (DataStore) + populates CurrentUser
    CurrentUser.kt                  — in-memory current-role holder, read by Dashboard for role-based UI
  network/
    ApiService.kt                  — full Retrofit interface matching the real backend
    BackendDtos.kt                  — response DTOs mirroring the backend's dto/ records field-for-field
    RetrofitClient.kt              — HTTP client, JWT header injection
  ui/
    nav/AppNav.kt                   — Login → Dashboard → {Matches, Scoring, History, Teams, Join Team, Post Feed}, + offline demo
    auth/                           — LoginScreen, LoginViewModel
    dashboard/                      — DashboardScreen/ViewModel, HistoryDetailScreen/ViewModel, FeedPostScreen
    team/                           — TeamManagementScreen/ViewModel, JoinTeamScreen
    match/                          — MatchBrowserScreen, MatchBrowserViewModel
    scoring/
      OnlineScorerViewModel.kt, OnlineLiveScorerScreen.kt  — backend-driven online scoring
      ScorerViewModel.kt, LiveScorerScreen.kt              — offline demo (unchanged)
    theme/
      Color.kt        — hero command-center palette (primary #289C85)
      Theme.kt        — Material3 dark color scheme
      Type.kt         — bold/wide-tracked "HUD" typography
      HeroShapes.kt   — angular cut-corner panel shape, glow border, banner scrim
```

## Tests

`app/src/test/java/com/cricketapp/mobile/data/ScoringEngineTest.kt` covers every
scoring flow with JUnit4 for the **offline demo's** local engine — dynamic
bowler cap, strike rotation, wide/no-ball/bye/leg-bye, every dismissal type's
bowler-credit rule, over completion + maiden detection, innings completion
(all three routes), and two full end-to-end matches.

```bash
./gradlew test
```

This suite was written against logic already verified via a standalone Java
port run outside Gradle before it existed as a Kotlin suite — 51/51 assertions
passed there. **Everything else in this app (Dashboard, Feed, Team Management,
the new theme, online-mode DTOs/ViewModels/screens) has not been executed at
all** — no working Gradle build exists in the environment that produced this
project (the container's filesystem also reset mid-project at one point,
recovered from the last delivered zips — noted here since it's the kind of
thing that could silently lose work if not caught). It's been reviewed by hand
instead: brace/paren balance and package/directory consistency checked
programmatically across all 30 Kotlin files (clean), and the logic traced
through manually. Real bugs caught this way across the project's history
include: a logout that navigated to the login screen without actually clearing
the stored session; an unclickable tournament list; `28.sp()` written where
`28.sp` was meant (a property, not a function call) in the invite-code
display; and a missing `import com.cricketapp.dto.PlayerDtos` on the backend
that would have failed to compile. Treat this as logic-reviewed, not
test-verified — running it on a real emulator is the natural next step, and
I'd want to know what breaks.

## Suggested next steps

- Actually run this on an emulator/device — see "Tests" above for exactly
  what has and hasn't been verified.
- Let the user choose which two teams to feature in the head-to-head widget,
  rather than defaulting to the most recent match.
- Add `previousBowlerId` to the backend's `InningsScorecardDto` so the bowler
  picker can pre-filter eligible bowlers instead of relying on the backend to
  reject a bad pick after the fact.
- Wire `OfflineQueue` into online mode for real offline-then-sync scoring.
- A delete-feed UI (Super Admin) — the endpoint already exists.
- A player-detail screen showing `GET /api/players/{id}/stats` — the backend
  endpoint is ready, nothing in the app calls it yet.
- Instrumented/Compose UI tests, and unit tests for the ViewModels (would need
  Robolectric or a fake `ApiService` — neither set up yet).
