package uk.me.michaellloydlee.recruiter.application.requirements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SalaryAmountsTest {
    @Test
    fun findsThePayWithTheCurrencyInFront() {
        val pays = SalaryAmounts.of("The median salary is £75,000 a year.", "EUR")

        assertEquals(listOf(SalaryAmount(75_000.0, "GBP")), pays)
    }

    @Test
    fun findsThePayWithTheCurrencyBehind() {
        val pays = SalaryAmounts.of("The median salary is 72.500 EUR a year.", "GBP")

        assertEquals(listOf(SalaryAmount(72_500.0, "EUR")), pays)
    }

    @Test
    fun findsThePayThatIsWrittenWithAThousand() {
        val pays = SalaryAmounts.of("Engineers earn $85k a year.", "GBP")

        assertEquals(listOf(SalaryAmount(85_000.0, "USD")), pays)
    }

    @Test
    fun findsThePayThatIsWrittenWithTheWordOfThePay() {
        val pays = SalaryAmounts.of("The base salary of the role is 120000 a year.", "GBP")

        assertEquals(listOf(SalaryAmount(120_000.0, "GBP")), pays)
    }

    @Test
    fun ignoresTheAmountsThatAreNoPays() {
        val pays = SalaryAmounts.of("10 years of experience, 2026, 42 applications.", "EUR")

        assertTrue(pays.isEmpty())
    }

    @Test
    fun findsEveryPayOfTheText() {
        val pays =
            SalaryAmounts.of(
                "The salary is between £60,000 and £80,000 a year.",
                "EUR",
            )

        assertEquals(
            listOf(SalaryAmount(60_000.0, "GBP"), SalaryAmount(80_000.0, "GBP")),
            pays,
        )
    }
}
