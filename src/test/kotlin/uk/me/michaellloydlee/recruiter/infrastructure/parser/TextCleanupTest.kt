package uk.me.michaellloydlee.recruiter.infrastructure.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class TextCleanupTest {
    @Test
    fun keepsTheTextAsItIs() {
        assertEquals("Hello recruiter", TextCleanup.clean("Hello recruiter"))
    }

    @Test
    fun collapsesTheSpaces() {
        assertEquals("Hello recruiter", TextCleanup.clean("Hello     recruiter"))
    }

    @Test
    fun replacesTheTabsWithSpaces() {
        assertEquals("Hello recruiter", TextCleanup.clean("Hello\trecruiter"))
    }

    @Test
    fun trimsTheLines() {
        assertEquals("Hello\nrecruiter", TextCleanup.clean("  Hello  \n  recruiter  "))
    }

    @Test
    fun collapsesTheEmptyLines() {
        assertEquals("Hello\n\nrecruiter", TextCleanup.clean("Hello\n\n\n\nrecruiter"))
    }

    @Test
    fun normalisesTheLineEndings() {
        assertEquals("Hello\nrecruiter", TextCleanup.clean("Hello\r\nrecruiter"))
        assertEquals("Hello\nrecruiter", TextCleanup.clean("Hello\rrecruiter"))
    }

    @Test
    fun removesTheControlCharacters() {
        assertEquals("Hello recruiter", TextCleanup.clean("Hello\u0000 recruiter"))
    }

    @Test
    fun removesTheInvisibleCharacters() {
        assertEquals("Helloworld", TextCleanup.clean("Hello\u00ADworld"))
        assertEquals("Helloworld", TextCleanup.clean("Hello\uFEFFworld"))
    }

    @Test
    fun removesTheZeroWidthCharacters() {
        assertEquals("Helloworld", TextCleanup.clean("Hello\u200Bworld"))
        assertEquals("Helloworld", TextCleanup.clean("Hello\u200Cworld"))
        assertEquals("Helloworld", TextCleanup.clean("Hello\u200Dworld"))
        assertEquals("Helloworld", TextCleanup.clean("Hello\u2060world"))
    }

    @Test
    fun removesTheSurroundingEmptyLines() {
        assertEquals("Hello", TextCleanup.clean("\n\n  Hello  \n\n"))
    }

    @Test
    fun keepsTheParagraphsApart() {
        assertEquals("Hello\n\nrecruiter", TextCleanup.clean("Hello\n\nrecruiter"))
    }
}
