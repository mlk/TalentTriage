package uk.me.michaellloydlee.recruiter.domain.gmail

import kotlin.test.Test
import kotlin.test.assertEquals

class EmailAddressTest {
    @Test
    fun theReplyGoesToTheAddressOfTheReplyToHeader() {
        val address =
            replyAddress(
                from = "no-reply@linkedin.com",
                replyTo = "Jane Doe <jane@example.com>",
            )

        assertEquals("jane@example.com", address)
    }

    @Test
    fun theReplyGoesToTheAddressOfTheSenderWhenTheMailHasNoReplyTo() {
        val address =
            replyAddress(
                from = "Jane Doe <jane@example.com>",
                replyTo = "",
            )

        assertEquals("jane@example.com", address)
    }

    @Test
    fun theNameOfTheSenderIsNotPartOfTheAddress() {
        val address =
            replyAddress(
                from = "Jane Doe <jane@example.com>",
                replyTo = "",
            )

        assertEquals("jane@example.com", address)
    }

    @Test
    fun anAddressWithoutANameIsTheAddressItself() {
        assertEquals("jane@example.com", replyAddress("jane@example.com", ""))
    }

    @Test
    fun theFirstAddressOfAHeaderIsTheAddressOfTheReply() {
        assertEquals(
            "jane@example.com",
            replyAddress("jane@example.com, other@example.com", ""),
        )
    }

    @Test
    fun aHeaderWithoutAnAddressIsEmpty() {
        assertEquals("", replyAddress("Jane Doe", ""))
    }
}
