package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailDraft
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MimeMessageTest {
    @Test
    fun writesTheHeadersOfTheDraft() {
        val mime = MimeMessage.build(draft())

        assertTrue(mime.startsWith("To: recruiter@example.com\r\n"))
        assertTrue(mime.contains("Subject: Re: Your CV\r\n"))
        assertTrue(mime.contains("In-Reply-To: <message-id>\r\n"))
        assertTrue(mime.contains("References: <message-id>\r\n"))
        assertTrue(mime.contains("Content-Type: text/plain; charset=\"UTF-8\"\r\n"))
    }

    @Test
    fun writesTheBodyOfTheDraftAfterTheHeaders() {
        val mime = MimeMessage.build(draft())

        assertTrue(mime.endsWith("\r\n\r\nthe reply of the model"))
    }

    @Test
    fun aLineBreakInTheSubjectCannotBreakTheMailApart() {
        val mime = MimeMessage.build(draft(subject = "Your CV\r\nBcc: someone@example.com"))

        assertFalse(mime.contains("\r\nBcc:"))
        assertTrue(mime.contains("Subject: Your CV Bcc: someone@example.com"))
    }

    @Test
    fun encodesTheMailWithBase64Url() {
        val encoded = MimeMessage.encode(draft())

        assertFalse(encoded.contains("+"))
        assertFalse(encoded.contains("/"))
        assertEquals(
            "To: recruiter@example.com\r\n" +
                "Subject: Re: Your CV\r\n" +
                "In-Reply-To: <message-id>\r\n" +
                "References: <message-id>\r\n" +
                "Content-Type: text/plain; charset=\"UTF-8\"\r\n" +
                "\r\n" +
                "the reply of the model",
            String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8),
        )
    }

    private fun draft(subject: String = "Re: Your CV"): EmailDraft =
        EmailDraft(
            to = "recruiter@example.com",
            subject = subject,
            body = "the reply of the model",
            inReplyTo = "message-id",
            threadId = "thread-id",
        )
}
