package io.github.duddmeister.weather

import io.github.duddmeister.weather.ui.update.NoteLine
import io.github.duddmeister.weather.ui.update.noteLines
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    @Test
    fun showsTheChangesAndLeavesOutTheInstallSteps() {
        // What the release workflow writes.
        val notes = """
            ## What's new

            - A sharper forecast chart
            - **Smoother** page transitions

            ## Install

            Download **Nimbus-v1.0.42.apk** below and open it on your phone.
        """.trimIndent()
        assertEquals(
            listOf(NoteLine.Bullet("A sharper forecast chart"), NoteLine.Bullet("Smoother page transitions")),
            noteLines(notes),
        )
    }

    @Test
    fun keepsOtherHeadingsAndText() {
        val notes = "# Fixes\n* Widgets on small screens\nThanks for the reports.\n\n**Full Changelog**: https://example.invalid/compare"
        assertEquals(
            listOf(NoteLine.Heading("Fixes"), NoteLine.Bullet("Widgets on small screens"), NoteLine.Plain("Thanks for the reports.")),
            noteLines(notes),
        )
    }

    @Test
    fun emptyNotesStillSaySomething() {
        assertEquals(listOf(NoteLine.Plain("Bug fixes and improvements.")), noteLines("  \n"))
    }
}
