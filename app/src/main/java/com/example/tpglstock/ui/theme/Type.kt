package com.example.tpglstock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.tpglstock.R

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int, vararg extra: FontVariation.Setting) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight), *extra))

/** Headings: Bricolage Grotesque. */
val DisplayFamily = FontFamily(
    variable(R.font.bricolage_grotesque, 500, FontVariation.Setting("opsz", 36f)),
    variable(R.font.bricolage_grotesque, 600, FontVariation.Setting("opsz", 36f)),
    variable(R.font.bricolage_grotesque, 700, FontVariation.Setting("opsz", 36f)),
)

/** Body text: Geist. */
val BodyFamily = FontFamily(
    variable(R.font.geist, 400),
    variable(R.font.geist, 500),
    variable(R.font.geist, 600),
)

/** Quantities and deltas: Geist Mono. */
val MonoFamily = FontFamily(
    variable(R.font.geist_mono, 400),
    variable(R.font.geist_mono, 500),
    variable(R.font.geist_mono, 600),
)

fun mono(size: TextUnit, weight: FontWeight = FontWeight.Medium, letterSpacing: TextUnit = 0.sp) =
    TextStyle(fontFamily = MonoFamily, fontSize = size, fontWeight = weight, letterSpacing = letterSpacing)

private fun display(size: Int, lineHeight: Float = 1.1f, tracking: Float = 0f) = TextStyle(
    fontFamily = DisplayFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking.em,
)

private fun body(size: Int, weight: FontWeight = FontWeight.Normal, lineHeight: Float = 1.4f) = TextStyle(
    fontFamily = BodyFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
)

val Typography = Typography(
    displaySmall = display(34, 1.05f, -0.02f),
    headlineMedium = display(30, 1.05f, -0.02f),
    headlineSmall = display(22),
    titleLarge = display(20),
    titleMedium = display(18),
    titleSmall = body(14, FontWeight.SemiBold),
    bodyLarge = body(16),
    bodyMedium = body(14, lineHeight = 1.45f),
    bodySmall = body(12, lineHeight = 1.35f),
    labelLarge = body(14, FontWeight.Medium),
    labelMedium = body(12, FontWeight.Medium),
    labelSmall = body(11, FontWeight.Medium),
)
