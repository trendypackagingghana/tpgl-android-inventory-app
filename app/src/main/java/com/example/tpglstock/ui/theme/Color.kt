package com.example.tpglstock.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Colours for one stock status: text, tinted background, level bar and the tone used on dark cards. */
@Immutable
data class StatusTone(val fg: Color, val bg: Color, val bar: Color, val onHero: Color)

/** Warm paper palette from the TPGL Stock redesign. */
@Immutable
data class TpglColors(
    val paper: Color,
    val surface: Color,
    /** Pressed rows and input fields. */
    val surfaceSoft: Color,
    val ink: Color,
    val onInk: Color,
    val muted: Color,
    val faint: Color,
    val placeholder: Color,
    /** Row dividers inside cards. */
    val line: Color,
    /** Swatch and input outlines. */
    val border: Color,
    /** Segmented-control tracks and neutral icon tiles. */
    val track: Color,
    val chipBorder: Color,
    val handle: Color,
    val accent: Color,
    val onAccent: Color,
    val disabledBg: Color,
    val disabledFg: Color,
    /** The dark raw-materials card. */
    val hero: Color,
    val onHero: Color,
    val heroMuted: Color,
    val heroTrack: Color,
    val ok: StatusTone,
    val low: StatusTone,
    val out: StatusTone,
    val inFg: Color,
    val inBg: Color,
    val outFg: Color,
    val outBg: Color,
    val chartUp: Color,
    val chartDown: Color,
    val scrim: Color,
)

val LightTpglColors = TpglColors(
    paper = Color(0xFFF3EEE6),
    surface = Color(0xFFFFFFFF),
    surfaceSoft = Color(0xFFFAF7F2),
    ink = Color(0xFF1B1712),
    onInk = Color(0xFFF3EEE6),
    muted = Color(0xFF6B6358),
    faint = Color(0xFF9A9186),
    placeholder = Color(0xFFC7BEB1),
    line = Color(0xFFF0EAE0),
    border = Color(0xFFE6DFD3),
    track = Color(0xFFEAE3D7),
    chipBorder = Color(0xFFD8CFC1),
    handle = Color(0xFFCFC6B8),
    accent = Color(0xFFE2572B),
    onAccent = Color(0xFF1B1712),
    disabledBg = Color(0xFFE2DACD),
    disabledFg = Color(0xFF8F8678),
    hero = Color(0xFF1B1712),
    onHero = Color(0xFFF3EEE6),
    heroMuted = Color(0xFFB5AC9F),
    heroTrack = Color(0xFF3A332B),
    ok = StatusTone(Color(0xFF1F7A4A), Color(0xFFDCEFE2), Color(0xFF2F8F5A), Color(0xFF7FD39E)),
    low = StatusTone(Color(0xFF9A5A06), Color(0xFFFBEACB), Color(0xFFE0A030), Color(0xFFF2C230)),
    out = StatusTone(Color(0xFFB3321F), Color(0xFFF8DCD5), Color(0xFFC2412B), Color(0xFFF08A70)),
    inFg = Color(0xFF1F7A4A),
    inBg = Color(0xFFDCEFE2),
    outFg = Color(0xFFB3401F),
    outBg = Color(0xFFFADBCB),
    chartUp = Color(0xFF2F8F5A),
    chartDown = Color(0xFFE2572B),
    scrim = Color(0x731B1712),
)

val DarkTpglColors = TpglColors(
    paper = Color(0xFF14110D),
    surface = Color(0xFF1F1A15),
    surfaceSoft = Color(0xFF28221B),
    ink = Color(0xFFF3EEE6),
    onInk = Color(0xFF1B1712),
    muted = Color(0xFFB5AC9F),
    faint = Color(0xFF81786D),
    placeholder = Color(0xFF5E564C),
    line = Color(0xFF2B251F),
    border = Color(0xFF3A332B),
    track = Color(0xFF2B251F),
    chipBorder = Color(0xFF4A4238),
    handle = Color(0xFF4A4238),
    accent = Color(0xFFE8683E),
    onAccent = Color(0xFF1B1712),
    disabledBg = Color(0xFF2B251F),
    disabledFg = Color(0xFF81786D),
    hero = Color(0xFF2A231C),
    onHero = Color(0xFFF3EEE6),
    heroMuted = Color(0xFFB5AC9F),
    heroTrack = Color(0xFF3F372E),
    ok = StatusTone(Color(0xFF7FD39E), Color(0xFF1E3A2A), Color(0xFF3FA66B), Color(0xFF7FD39E)),
    low = StatusTone(Color(0xFFF2C230), Color(0xFF3D2F12), Color(0xFFE0A030), Color(0xFFF2C230)),
    out = StatusTone(Color(0xFFF08A70), Color(0xFF45211A), Color(0xFFD9573F), Color(0xFFF08A70)),
    inFg = Color(0xFF7FD39E),
    inBg = Color(0xFF1E3A2A),
    outFg = Color(0xFFF59A76),
    outBg = Color(0xFF45251A),
    chartUp = Color(0xFF3FA66B),
    chartDown = Color(0xFFE8683E),
    scrim = Color(0x99000000),
)
