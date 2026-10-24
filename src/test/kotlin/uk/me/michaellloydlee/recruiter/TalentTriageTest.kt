package uk.me.michaellloydlee.recruiter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TalentTriageTest {
    @Test
    fun exposesTheProjectName() {
        assertEquals("TalentTriage", TalentTriage.NAME)
    }

    @Test
    fun exposesTheRootPackage() {
        assertEquals("uk.me.michaellloydlee.recruiter", TalentTriage.ROOT_PACKAGE)
    }

    @Test
    fun describesTheProject() {
        val description = TalentTriage.describe()

        assertTrue(description.contains(TalentTriage.NAME))
        assertTrue(description.contains(TalentTriage.ROOT_PACKAGE))
    }
}
