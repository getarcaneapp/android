package app.getarcane.android.ui.theme

import androidx.compose.ui.graphics.Color

// iOS system palette — used as accent options and semantic/status colors,
// matching AppearanceSettingsView.AccentColorOption in the iOS app.
val ArcaneBlue = Color(0xFF007AFF)
val ArcaneIndigo = Color(0xFF5856D6)
val ArcanePurple = Color(0xFFAF52DE)
val ArcanePink = Color(0xFFFF2D55)
val ArcaneRed = Color(0xFFFF3B30)
val ArcaneOrange = Color(0xFFFF9500)
val ArcaneYellow = Color(0xFFFFCC00)
val ArcaneGreen = Color(0xFF34C759)
val ArcaneTeal = Color(0xFF5AC8FA)
val ArcaneMint = Color(0xFF00C7BE)
val ArcaneCyan = Color(0xFF32D2F0)
val ArcaneGray = Color(0xFF8E8E93)

/** Semantic status colors, mirroring the iOS StatusBadge / status-dot conventions. */
val StatusRunning = ArcaneGreen
val StatusStopped = ArcaneRed
val StatusPaused = ArcaneOrange
val StatusUnknown = ArcaneGray
