package uk.me.michaellloydlee.recruiter.infrastructure.requirements

import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.domain.requirements.PayRequirement
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequirementsFileTest {
    @Test
    fun loadsTheRequirements() {
        val file =
            requirementsFile(
                """
                {
                  "minimum_pay": {"amount": 120000, "currency": "EUR", "period": "year"},
                  "maximum_office_days": 2,
                  "profile": "Senior Kotlin engineer"
                }
                """.trimIndent(),
            )

        val requirements = RequirementsFile.load(file)

        assertEquals(120000.0, requirements.minimumPay?.amount)
        assertEquals("EUR", requirements.minimumPay?.currency)
        assertEquals("year", requirements.minimumPay?.period)
        assertEquals(2, requirements.maximumOfficeDays)
        assertEquals("Senior Kotlin engineer", requirements.profile)
    }

    @Test
    fun loadsRequirementsWithoutAPay() {
        val file =
            requirementsFile(
                """
                {
                  "maximum_office_days": 0,
                  "profile": "Senior Kotlin engineer"
                }
                """.trimIndent(),
            )

        val requirements = RequirementsFile.load(file)

        assertNull(requirements.minimumPay)
        assertEquals(false, requirements.caresAboutPay)
    }

    @Test
    fun failsWhenTheFileIsMissing() {
        val missing = Path.of("/does/not/exist/requirements.json")

        val error = assertFailsWith<IllegalArgumentException> { RequirementsFile.load(missing) }

        assertTrue(error.message!!.contains("not found"))
    }

    @Test
    fun failsWhenTheFileIsNotValidJson() {
        val file = requirementsFile("not json at all")

        val error = assertFailsWith<IllegalArgumentException> { RequirementsFile.load(file) }

        assertTrue(error.message!!.contains("could not be read"))
    }

    @Test
    fun failsWhenTheOfficeDaysAreNegative() {
        val file =
            requirementsFile(
                """
                {
                  "maximum_office_days": -1,
                  "profile": "Senior Kotlin engineer"
                }
                """.trimIndent(),
            )

        assertFailsWith<IllegalArgumentException> { RequirementsFile.load(file) }
    }

    @Test
    fun failsWhenTheProfileIsEmpty() {
        val file =
            requirementsFile(
                """
                {
                  "maximum_office_days": 2,
                  "profile": "  "
                }
                """.trimIndent(),
            )

        assertFailsWith<IllegalArgumentException> { RequirementsFile.load(file) }
    }

    @Test
    fun savesTheRequirements() {
        val file = createTempDirectory("requirements").resolve("requirements.json")

        RequirementsFile.save(
            file,
            JobRequirements(
                minimumPay = PayRequirement(120_000.0, "EUR"),
                maximumOfficeDays = 2,
                profile = "Senior Kotlin engineer",
            ),
        )

        val requirements = RequirementsFile.load(file)
        assertEquals(120_000.0, requirements.minimumPay?.amount)
        assertEquals("EUR", requirements.minimumPay?.currency)
        assertEquals("year", requirements.minimumPay?.period)
        assertEquals(2, requirements.maximumOfficeDays)
        assertEquals("Senior Kotlin engineer", requirements.profile)
    }

    @Test
    fun savesRequirementsWithoutAPay() {
        val file = createTempDirectory("requirements").resolve("requirements.json")

        RequirementsFile.save(
            file,
            JobRequirements(
                minimumPay = null,
                maximumOfficeDays = 3,
                profile = "Senior Kotlin engineer",
            ),
        )

        val requirements = RequirementsFile.load(file)
        assertNull(requirements.minimumPay)
        assertEquals(3, requirements.maximumOfficeDays)
    }

    @Test
    fun failsWhenTheFileCannotBeWritten() {
        val file = Path.of("/does/not/exist/requirements.json")

        val error =
            assertFailsWith<IllegalArgumentException> {
                RequirementsFile.save(
                    file,
                    JobRequirements(minimumPay = null, maximumOfficeDays = 2, profile = "Senior Kotlin engineer"),
                )
            }

        assertTrue(error.message!!.contains("could not be written"))
    }

    private fun requirementsFile(content: String): Path {
        val file = createTempDirectory("requirements").resolve("requirements.json")
        file.writeText(content)
        return file
    }
}
