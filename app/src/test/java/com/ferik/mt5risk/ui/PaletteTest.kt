package com.ferik.mt5risk.ui

import com.ferik.mt5risk.pass.MessageSeverity
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(rgb: Int): Double =
        0.2126 * channel((rgb shr 16) and 0xFF) + 0.7152 * channel((rgb shr 8) and 0xFF) + 0.0722 * channel(rgb and 0xFF)

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    /** [foreground] drawn at [alpha] over [behind]: what the eye sees when the system thins an overlay. */
    private fun blend(foreground: Int, alpha: Double, behind: Int): Int {
        fun mix(shift: Int): Int {
            val f = (foreground shr shift) and 0xFF
            val b = (behind shr shift) and 0xFF
            return Math.round(f * alpha + b * (1 - alpha)).toInt()
        }
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private val white = 0xFFFFFF
    private val lightGrey = 0xC8CCD2
    private val black = 0x000000

    private fun assertReadable(name: String, foreground: Int, background: Int, minimum: Double = 4.5) {
        val ratio = contrast(foreground, background)
        assertTrue("$name is only ${"%.2f".format(ratio)} : 1, needs $minimum", ratio >= minimum)
    }

    @Test
    fun `the contrast formula matches known values`() {
        assertEquals(21.0, contrast(white, black), 0.01)
        assertEquals(1.0, contrast(white, white), 0.001)
    }

    @Test
    fun `the old picker title colour would fail the check`() {
        // Black default text on the old card #22262E measured 1.38 : 1.
        assertFalse(contrast(0x000000, 0x22262E) >= 4.5)
        assertFalse(contrast(0x381E72, 0x2E9E5B) >= 4.5) // old highlighted preset
    }

    @Test
    fun `picker and minimum-lot text on the card`() {
        assertReadable("title and body", Palette.TEXT, Palette.CARD)
        assertReadable("secondary", Palette.TEXT_SECONDARY, Palette.CARD)
        assertReadable("link", Palette.LINK, Palette.CARD)
        assertReadable("text field border", Palette.OUTLINE, Palette.CARD, 3.0)
        assertReadable("error text", Palette.STATUS_MISSING, Palette.CARD)
    }

    @Test
    fun `chips and buttons`() {
        assertReadable("preset chip", Palette.TEXT, Palette.CHIP)
        assertReadable("selected chip", Palette.ON_SELECTED, Palette.SELECTED)
        assertReadable("primary action", Palette.ON_PRIMARY, Palette.PRIMARY)
        assertReadable("disabled button", Palette.ON_DISABLED, Palette.DISABLED)
    }

    @Test
    fun `toasts stay readable at full opacity and at 80 percent over white chart text`() {
        for ((name, text, background) in listOf(
            Triple("success", Palette.ON_SUCCESS, Palette.SUCCESS),
            Triple("warning", Palette.ON_WARNING, Palette.WARNING),
            Triple("error", Palette.ON_ERROR, Palette.ERROR),
        )) {
            assertReadable("$name toast", text, background)
            assertReadable("$name toast at 80 % over white", text, blend(background, 0.8, white))
        }
    }

    @Test
    fun `each severity maps to its own toast colours`() {
        assertEquals(Palette.SUCCESS to Palette.ON_SUCCESS, Palette.toast(MessageSeverity.SUCCESS))
        assertEquals(Palette.WARNING to Palette.ON_WARNING, Palette.toast(MessageSeverity.WARNING))
        assertEquals(Palette.ERROR to Palette.ON_ERROR, Palette.toast(MessageSeverity.ERROR))
    }

    @Test
    fun `the three toast severities are different colours`() {
        assertEquals(3, setOf(Palette.SUCCESS, Palette.WARNING, Palette.ERROR).size)
    }

    @Test
    fun `bubble label stays readable over the chart`() {
        for (behind in listOf(white, lightGrey, black)) {
            assertReadable(
                "bubble over ${"%06X".format(behind)}",
                Palette.ON_BUBBLE,
                blend(Palette.BUBBLE, Palette.BUBBLE_ALPHA.toDouble(), behind),
            )
        }
    }

    @Test
    fun `in-app screens`() {
        for (background in listOf(Palette.SCREEN, Palette.SCREEN_CARD)) {
            assertReadable("text", Palette.TEXT, background)
            assertReadable("secondary", Palette.TEXT_SECONDARY, background)
            assertReadable("link", Palette.LINK, background)
            assertReadable("Ready", Palette.STATUS_READY, background)
            assertReadable("Missing", Palette.STATUS_MISSING, background)
            assertReadable("Check yourself", Palette.STATUS_CHECK, background)
        }
        assertReadable("disabled button on a card", Palette.ON_DISABLED, Palette.DISABLED)
    }

    @Test
    fun `the title is larger than the body text`() {
        assertTrue(Palette.titleSize.value > Palette.bodySize.value)
        assertTrue(Palette.bodySize.value > Palette.secondarySize.value)
    }
}
