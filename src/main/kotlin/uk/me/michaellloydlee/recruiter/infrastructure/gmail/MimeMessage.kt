package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailDraft
import java.util.Base64

/**
 * The mail of a draft in the format the Gmail API takes it.
 *
 * The API reads a mail as it is written on the wire: the headers of the mail,
 * an empty line and the body. The mail is encoded with base64url, the encoding
 * the API expects.
 */
internal object MimeMessage {
    /**
     * The mail of a draft, encoded for the API.
     *
     * @param draft the draft to write.
     * @return the mail of the draft, encoded with base64url.
     */
    fun encode(draft: EmailDraft): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(build(draft).toByteArray(Charsets.UTF_8))

    /**
     * The mail of a draft, in the format of RFC 2822.
     *
     * @param draft the draft to write.
     * @return the mail of the draft.
     */
    fun build(draft: EmailDraft): String =
        buildString {
            append(header("To", draft.to))
            append(header("Subject", draft.subject))
            append(header("In-Reply-To", "<${draft.inReplyTo}>"))
            append(header("References", "<${draft.inReplyTo}>"))
            append(header("Content-Type", "text/plain; charset=\"UTF-8\""))
            append(LINE_BREAK)
            append(draft.body)
        }

    /**
     * A header of the mail.
     *
     * The value of a header cannot break the mail apart: a line break in it
     * would end the header and start a new one.
     *
     * @param name the name of the header.
     * @param value the value of the header.
     * @return the header of the mail.
     */
    private fun header(
        name: String,
        value: String,
    ): String = "$name: ${value.replace("\r\n", " ").replace("\r", " ").replace("\n", " ")}$LINE_BREAK"

    /** The line break of a mail, the format of RFC 2822 asks for CRLF. */
    private const val LINE_BREAK: String = "\r\n"
}
