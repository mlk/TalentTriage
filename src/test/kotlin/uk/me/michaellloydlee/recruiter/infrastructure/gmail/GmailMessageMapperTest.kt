package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GmailMessageMapperTest {
    @Test
    fun readsTheHeadersAndTheBody() {
        val message =
            GmailMessageMapper.toDomain(
                GmailMessageResource(
                    id = "message-id",
                    threadId = "thread-id",
                    labelIds = listOf("INBOX", "UNREAD"),
                    payload =
                        GmailPayloadResource(
                            mimeType = "text/plain",
                            headers =
                                listOf(
                                    GmailHeaderResource(name = "From", value = "recruiter@example.com"),
                                    GmailHeaderResource(name = "Subject", value = "Your CV"),
                                ),
                            body = GmailBodyResource(data = "SGVsbG8gcmVjcnVpdGVyIQ=="),
                        ),
                ),
            )

        assertEquals("message-id", message.id)
        assertEquals("thread-id", message.threadId)
        assertEquals("recruiter@example.com", message.sender)
        assertEquals("Your CV", message.subject)
        assertEquals("Hello recruiter!", message.body)
        assertEquals(listOf("INBOX", "UNREAD"), message.labelIds)
        assertTrue(message.attachments.isEmpty())
    }

    @Test
    fun readsTheAttachmentsOfTheNestedParts() {
        val message =
            GmailMessageMapper.toDomain(
                GmailMessageResource(
                    id = "message-id",
                    payload =
                        GmailPayloadResource(
                            mimeType = "multipart/mixed",
                            parts =
                                listOf(
                                    GmailPayloadResource(
                                        mimeType = "multipart/alternative",
                                        parts =
                                            listOf(
                                                GmailPayloadResource(
                                                    mimeType = "text/plain",
                                                    body = GmailBodyResource(data = "SGVsbG8h"),
                                                ),
                                                GmailPayloadResource(mimeType = "text/html"),
                                            ),
                                    ),
                                    GmailPayloadResource(
                                        mimeType = "application/pdf",
                                        fileName = "cv.pdf",
                                        body = GmailBodyResource(attachmentId = "attachment-1", size = 2048),
                                    ),
                                    GmailPayloadResource(
                                        mimeType =
                                            "application/vnd.openxmlformats-officedocument" +
                                                ".wordprocessingml.document",
                                        fileName = "cv.docx",
                                        body = GmailBodyResource(attachmentId = "attachment-2", size = 1024),
                                    ),
                                ),
                        ),
                ),
            )

        assertEquals("Hello!", message.body)
        assertEquals(2, message.attachments.size)
        assertEquals("attachment-1", message.attachments[0].id)
        assertEquals("cv.pdf", message.attachments[0].fileName)
        assertEquals("application/pdf", message.attachments[0].mimeType)
        assertEquals(2048, message.attachments[0].size)
        assertEquals("attachment-2", message.attachments[1].id)
        assertEquals("cv.docx", message.attachments[1].fileName)
    }

    @Test
    fun fallsBackToTheSnippetWhenThereIsNoText() {
        val message =
            GmailMessageMapper.toDomain(
                GmailMessageResource(
                    id = "message-id",
                    snippet = "the snippet",
                    payload = GmailPayloadResource(mimeType = "text/html"),
                ),
            )

        assertEquals("the snippet", message.body)
    }
}
