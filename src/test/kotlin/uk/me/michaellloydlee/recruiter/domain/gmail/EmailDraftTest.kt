package uk.me.michaellloydlee.recruiter.domain.gmail

import kotlin.test.Test
import kotlin.test.assertEquals

class EmailDraftTest {
    @Test
    fun theSubjectOfAReplyStartsWithRe() {
        assertEquals("Re: Your CV", replySubject("Your CV"))
    }

    @Test
    fun theSubjectOfAReplyIsNotPrefixedTwice() {
        assertEquals("Re: Your CV", replySubject("Re: Your CV"))
    }

    @Test
    fun theSubjectOfAReplyKeepsThePrefixItAlreadyHas() {
        assertEquals("re: Your CV", replySubject("re: Your CV"))
    }

    @Test
    fun theSubjectOfAReplyOfAMailWithoutASubjectIsEmpty() {
        assertEquals("Re: ", replySubject(""))
    }
}
