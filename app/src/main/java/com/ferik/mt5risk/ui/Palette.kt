package com.ferik.mt5risk.ui

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.ferik.mt5risk.pass.MessageSeverity

/**
 * Every colour and text size the app draws with. The RGB values are plain ints so the contrast test
 * can check each foreground/background pair without any Android classes; PaletteTest holds the
 * minimum contrast for each pair.
 */
object Palette {
    // Overlay card (picker, minimum-lot question)
    const val CARD = 0x1A1E26
    const val TEXT = 0xF4F6FA
    const val TEXT_SECONDARY = 0xC3C9D4
    const val LINK = 0x8AB4FF
    const val OUTLINE = 0x8D95A3

    // Chips and buttons
    const val CHIP = 0x2C3342
    const val SELECTED = 0x3DDC84
    const val ON_SELECTED = 0x04210F
    const val PRIMARY = 0x1456B8
    const val ON_PRIMARY = 0xFFFFFF
    const val DISABLED = 0x262B36
    const val ON_DISABLED = 0x98A0AE

    // Toasts
    const val SUCCESS = 0x0B5A32
    const val WARNING = 0xFFC53D
    const val ERROR = 0x8E1B1B
    const val ON_SUCCESS = 0xFFFFFF
    const val ON_WARNING = 0x1A1200
    const val ON_ERROR = 0xFFFFFF

    // Bubble: drawn at BUBBLE_ALPHA so the chart shows through a little
    const val BUBBLE = PRIMARY
    const val ON_BUBBLE = 0xFFFFFF
    const val BUBBLE_ALPHA = 0.9f

    // In-app screens
    const val SCREEN = 0x1C1B1F
    const val SCREEN_CARD = 0x262B36
    const val STATUS_READY = 0x4ADE80
    const val STATUS_MISSING = 0xFF7A7A
    const val STATUS_CHECK = 0xFFC53D

    // Text sizes (sp)
    val titleSize = 20.sp
    val bodySize = 16.sp
    val secondarySize = 14.sp
    val bubbleSize = 14.sp

    fun color(rgb: Int, alpha: Float = 1f): Color = Color(
        red = ((rgb shr 16) and 0xFF) / 255f,
        green = ((rgb shr 8) and 0xFF) / 255f,
        blue = (rgb and 0xFF) / 255f,
        alpha = alpha,
    )

    /** One scheme for the overlay and the app, so anything that does not set a colour is still readable. */
    val colorScheme = darkColorScheme(
        primary = color(PRIMARY),
        onPrimary = color(ON_PRIMARY),
        secondary = color(CHIP),
        onSecondary = color(TEXT),
        background = color(SCREEN),
        onBackground = color(TEXT),
        surface = color(SCREEN),
        onSurface = color(TEXT),
        surfaceVariant = color(SCREEN_CARD),
        onSurfaceVariant = color(TEXT_SECONDARY),
        outline = color(OUTLINE),
        error = color(STATUS_MISSING),
        onError = color(ON_WARNING),
    )

    /** Background and text colour (RGB) for a result message of the given [severity]. */
    fun toast(severity: MessageSeverity): Pair<Int, Int> = when (severity) {
        MessageSeverity.SUCCESS -> SUCCESS to ON_SUCCESS
        MessageSeverity.WARNING -> WARNING to ON_WARNING
        MessageSeverity.ERROR -> ERROR to ON_ERROR
    }

    /** Blue "Go", "Save" and similar main actions. */
    @Composable
    fun primaryButton(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = color(PRIMARY),
        contentColor = color(ON_PRIMARY),
        disabledContainerColor = color(DISABLED),
        disabledContentColor = color(ON_DISABLED),
    )

    /** Neutral preset chips. */
    @Composable
    fun chipButton(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = color(CHIP),
        contentColor = color(TEXT),
        disabledContainerColor = color(DISABLED),
        disabledContentColor = color(ON_DISABLED),
    )

    /** The last used preset. */
    @Composable
    fun selectedChipButton(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = color(SELECTED),
        contentColor = color(ON_SELECTED),
    )

    @Composable
    fun outlinedButton(): ButtonColors = ButtonDefaults.outlinedButtonColors(
        contentColor = color(TEXT),
        disabledContentColor = color(ON_DISABLED),
    )

    @Composable
    fun textButton(): ButtonColors = ButtonDefaults.textButtonColors(
        contentColor = color(LINK),
        disabledContentColor = color(ON_DISABLED),
    )

    @Composable
    fun overlayCard(): CardColors = CardDefaults.cardColors(containerColor = color(CARD), contentColor = color(TEXT))

    @Composable
    fun screenCard(): CardColors = CardDefaults.cardColors(containerColor = color(SCREEN_CARD), contentColor = color(TEXT))

    @Composable
    fun textField(): TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = color(TEXT),
        unfocusedTextColor = color(TEXT),
        focusedLabelColor = color(LINK),
        unfocusedLabelColor = color(TEXT_SECONDARY),
        focusedBorderColor = color(LINK),
        unfocusedBorderColor = color(OUTLINE),
        cursorColor = color(LINK),
        errorTextColor = color(TEXT),
        errorLabelColor = color(STATUS_MISSING),
        errorBorderColor = color(STATUS_MISSING),
        errorCursorColor = color(STATUS_MISSING),
    )
}
