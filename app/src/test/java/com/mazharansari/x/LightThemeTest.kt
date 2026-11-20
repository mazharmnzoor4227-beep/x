package com.mazharansari.x

import androidx.compose.ui.graphics.Color
import com.mazharansari.x.ui.theme.DarkPalette
import com.mazharansari.x.ui.theme.LightPalette
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

/**
 * Light ("Day") theme contrast — the 2026-11-20 white-on-white fix.
 *
 * The bug: LightPalette had bg #F7F8FA with white surfaces/bubbles and a
 * #E2E5EA hairline, so chat bubbles and the canvas were indistinguishable
 * (bubble-vs-bg ≈ 1.05:1). The fix gives the canvas a real grey (#DEE1E6),
 * keeps bubbles/cards white, and gives them a visible border (#AEB4BE) +
 * 2dp shadow (MessageViews.kt). These tests pin the numbers with the WCAG
 * relative-luminance formula, so the palette can never silently slide back
 * to white-on-white. Thresholds are deliberately a little below the measured
 * values (measured in parentheses) so a one-step token tweak does not flake,
 * but a return to the old tokens fails hard:
 *   bubble-vs-bg 1.31 (was 1.08), border-vs-bg 1.59 (was 1.19),
 *   text-vs-bg 13.55, text-vs-bubble 17.76.
 * Pure JVM test — no Robolectric, no Compose runtime: [Color] is a value
 * class and the palette is plain data.
 */
class LightThemeTest {

    /** WCAG 2.x relative luminance of an sRGB colour. */
    private fun luminance(c: Color): Double {
        fun lin(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.04045) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }

    /** WCAG contrast ratio, 1.0 (identical) to 21.0 (black on white). */
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private val p get() = LightPalette

    @Test fun userBubbleReadsAgainstTheCanvas() {
        val r = contrast(p.userBubble, p.bg)
        println("LIGHT-THEME userBubble vs bg = %.3f (need >= 1.25)".format(r))
        assertTrue("user bubble vs bg is $r — bubbles melt into the canvas", r >= 1.25)
    }

    @Test fun assistantBubbleAndCardsReadAgainstTheCanvas() {
        // The assistant bubble is p.card; the composer/sheets are p.surface.
        // All three are white cards on the same grey canvas.
        listOf("card" to p.card, "surface" to p.surface, "sheet" to p.sheet).forEach { (name, c) ->
            val r = contrast(c, p.bg)
            println("LIGHT-THEME $name vs bg = %.3f (need >= 1.25)".format(r))
            assertTrue("$name vs bg is $r — white card on near-white canvas", r >= 1.25)
        }
    }

    @Test fun borderReadsAgainstTheCanvasAndOnWhiteCards() {
        val onBg = contrast(p.border, p.bg)
        val onCard = contrast(p.border, p.surface)
        println("LIGHT-THEME border vs bg = %.3f (need >= 1.5), vs card = %.3f (need >= 2.0)".format(onBg, onCard))
        assertTrue("border vs bg is $onBg — hairline invisible on the canvas", onBg >= 1.5)
        assertTrue("border vs card is $onCard — hairline invisible on white cards", onCard >= 2.0)
        assertEquals("cardBorder must be the same visible border", p.border, p.cardBorder)
    }

    @Test fun bodyTextIsDarkAndReadableEverywhere() {
        val onBg = contrast(p.text, p.bg)
        val onBubble = contrast(p.text, p.userBubble)
        val onCard = contrast(p.text, p.card)
        println("LIGHT-THEME text vs bg = %.2f, vs bubble = %.2f, vs card = %.2f (need >= 7)".format(onBg, onBubble, onCard))
        assertTrue("text vs bg is $onBg", onBg >= 7.0)
        assertTrue("text vs bubble is $onBubble", onBubble >= 7.0)
        assertTrue("text vs card is $onCard", onCard >= 7.0)
    }

    @Test fun secondaryAndAccentTextStayReadable() {
        val secondary = contrast(p.textSecondary, p.bg)
        val accentOnCard = contrast(p.accent, p.card)
        val accentOnBg = contrast(p.accent, p.bg)
        println("LIGHT-THEME textSecondary vs bg = %.2f (need >= 4.5); accent vs card = %.2f, vs bg = %.2f (need >= 4.5)".format(secondary, accentOnCard, accentOnBg))
        assertTrue("textSecondary vs bg is $secondary", secondary >= 4.5)
        assertTrue("accent vs card is $accentOnCard", accentOnCard >= 4.5)
        assertTrue("accent vs bg is $accentOnBg", accentOnBg >= 4.5)
    }

    @Test fun mutedCaptionsAreNotInvisible() {
        // Muted is timestamps/placeholders only, so the bar is lower than body
        // text. The token was darkened (#8A919C -> #7C838E) so it keeps about
        // the same readability on the darker canvas (2.92:1, was 2.99:1).
        val r = contrast(p.muted, p.bg)
        println("LIGHT-THEME muted vs bg = %.2f (need >= 2.5)".format(r))
        assertTrue("muted vs bg is $r", r >= 2.5)
    }

    @Test fun adjacentLayersAreDistinctValues() {
        // No two layers that touch may share a value — that is the bug.
        assertNotEquals("bg must differ from surface", p.bg, p.surface)
        assertNotEquals("bg must differ from userBubble", p.bg, p.userBubble)
        assertNotEquals("bg must differ from card", p.bg, p.card)
        assertNotEquals("bg must differ from sheet", p.bg, p.sheet)
        assertNotEquals("surface must differ from surfaceSunken", p.surface, p.surfaceSunken)
        assertNotEquals("surface must differ from chip", p.surface, p.chip)
        assertNotEquals("border must differ from bg", p.border, p.bg)
        // One canvas: every screen's page background is the same grey.
        assertEquals("page2 must equal bg (one canvas on every screen)", p.bg, p.page2)
    }

    @Test fun canvasIsGreyNotWhiteAndBubblesStayWhite() {
        assertTrue("bg luminance ${luminance(p.bg)} — canvas slid back to white", luminance(p.bg) < 0.80)
        assertNotEquals("bg must not be pure white", Color.White, p.bg)
        assertEquals("bubbles stay white by design (border + shadow carry the edge)", Color.White, p.userBubble)
        assertEquals("cards stay white by design", Color.White, p.card)
    }

    @Test fun darkPaletteIsByteIdentical() {
        // The fix is light-only. If any dark token moves, this fails loudly.
        val d = DarkPalette
        assertEquals(Color(0xFF14161A), d.bg)
        assertEquals(Color(0xFF1D2026), d.surface)
        assertEquals(Color(0xFF262A31), d.surface2)
        assertEquals(Color(0xFF1D2026), d.sheet)
        assertEquals(Color(0xFF2E333D), d.border)
        assertEquals(Color(0xFFF2F3F5), d.text)
        assertEquals(Color(0xFFA6ADB8), d.textSecondary)
        assertEquals(Color(0xFF6E7683), d.muted)
        assertEquals(Color(0xFFE0A63F), d.accent)
        assertEquals(Color(0xFF14161A), d.onAccent)
        assertEquals(Color(0xFF2A2F38), d.userBubble)
        assertEquals(Color(0xFF1D2026), d.card)
        assertEquals(Color(0xFF14161A), d.page2)
        assertEquals(Color(0xFF262A31), d.surfaceRaised)
        assertEquals(Color(0xFF0F1114), d.surfaceSunken)
        assertTrue("dark palette must still be dark", d.isDark)
    }
}
