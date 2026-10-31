package uk.me.michaellloydlee.recruiter.application.evaluation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailAttachment
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailApiClient
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailMessagesApi
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.OAuth2Token
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.RefreshingAccessTokenProvider
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.TestOAuth2Client
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.temporaryTokenStore
import uk.me.michaellloydlee.recruiter.infrastructure.logging.LogRecord
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import uk.me.michaellloydlee.recruiter.infrastructure.parser.TestDocuments
import uk.me.michaellloydlee.recruiter.infrastructure.parser.TestWordDocuments
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttachmentTextReaderTest {
    @Test
    fun readsTheTextOfAPdfAttachment() =
        runTest {
            val engine = engine(TestDocuments.pdfWith(listOf("Hello recruiter")))

            val text = reader(engine).textOf(message(attachment("cv.pdf", PDF_MIME_TYPE)))

            assertTrue(text.contains("Hello recruiter"))
        }

    @Test
    fun readsTheTextOfAWordAttachment() =
        runTest {
            val engine = engine(TestWordDocuments.docxWith(listOf("Hello recruiter")))

            val text = reader(engine).textOf(message(attachment("cv.docx", WORD_MIME_TYPE)))

            assertTrue(text.contains("Hello recruiter"))
        }

    @Test
    fun skipsAnAttachmentOfAnUnsupportedType() =
        runTest {
            val engine = engine()

            val text = reader(engine).textOf(message(attachment("logo.png", "image/png")))

            assertEquals("", text)
        }

    @Test
    fun skipsAnAttachmentThatCannotBeRead() =
        runTest {
            val engine = engine("not a document".toByteArray())

            val text = reader(engine).textOf(message(attachment("cv.pdf", PDF_MIME_TYPE)))

            assertEquals("", text)
        }

    @Test
    fun reportsAnAttachmentThatCannotBeRead() =
        runTest {
            val sink = StringBuilder()
            val engine = engine("not a document".toByteArray())

            reader(engine, sink).textOf(message(attachment("cv.pdf", PDF_MIME_TYPE)))

            val record = record(sink)
            assertEquals(AttachmentTextReader.EVENT_ATTACHMENT_FAILED, record.event)
            assertEquals("message-id", record.attributes["id"])
            assertEquals("cv.pdf", record.attributes["file"])
            assertTrue(record.attributes["reason"]!!.isNotBlank())
        }

    @Test
    fun readsTheTextOfTheOtherAttachmentsWhenOneCannotBeRead() =
        runTest {
            val sink = StringBuilder()
            val engine = engine("not a document".toByteArray())

            val text =
                reader(engine, sink).textOf(
                    message(
                        attachment("cv.pdf", PDF_MIME_TYPE),
                        attachment("cv.docx", WORD_MIME_TYPE),
                    ),
                )

            assertTrue(text.contains("Hello recruiter"))
            val record = record(sink)
            assertEquals(AttachmentTextReader.EVENT_ATTACHMENT_FAILED, record.event)
            assertEquals("cv.pdf", record.attributes["file"])
        }

    @Test
    fun joinsTheTextOfTheAttachments() =
        runTest {
            val engine = engine(TestDocuments.pdfWith(listOf("Hello recruiter")))

            val text =
                reader(engine).textOf(
                    message(
                        attachment("cv.pdf", PDF_MIME_TYPE),
                        attachment("cv.docx", WORD_MIME_TYPE),
                    ),
                )

            assertTrue(text.contains("Hello recruiter"))
            assertTrue(text.contains("\n\n"))
        }

    private suspend fun reader(
        engine: MockEngine,
        sink: StringBuilder = StringBuilder(),
    ): AttachmentTextReader {
        val api =
            GmailApiClient(
                httpClient = HttpClient(engine) { install(ContentNegotiation) { json(JSON) } },
                tokens =
                    RefreshingAccessTokenProvider(
                        refresher = TestOAuth2Client().client,
                        tokenStore = temporaryTokenStore(token()),
                    ),
                baseUrl = "https://gmail.example/v1",
            )
        return AttachmentTextReader(GmailMessagesApi(api), StructuredLogger(sink))
    }

    private fun record(sink: StringBuilder): LogRecord = Json.decodeFromString(sink.toString().trim())

    private fun engine(content: ByteArray = TestDocuments.pdfWith(listOf("Hello recruiter"))): MockEngine =
        MockEngine { request ->
            val document =
                if (request.url.encodedPath.endsWith(PDF_ATTACHMENT_ID)) {
                    content
                } else {
                    TestWordDocuments.docxWith(listOf("Hello recruiter"))
                }
            val data = Base64.getUrlEncoder().withoutPadding().encodeToString(document)
            respond(
                content = """{"data":"$data"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    private fun message(vararg attachments: EmailAttachment): EmailMessage =
        EmailMessage(
            id = "message-id",
            threadId = "thread-id",
            sender = "recruiter@example.com",
            subject = "Your CV",
            body = "the body of the mail",
            labelIds = listOf("INBOX"),
            attachments = attachments.toList(),
        )

    private fun attachment(
        fileName: String,
        mimeType: String,
    ): EmailAttachment =
        EmailAttachment(
            id = if (mimeType == PDF_MIME_TYPE) PDF_ATTACHMENT_ID else WORD_ATTACHMENT_ID,
            fileName = fileName,
            mimeType = mimeType,
            size = 100,
        )

    private fun token(): OAuth2Token =
        OAuth2Token(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = Instant.now().plusSeconds(600),
        )

    companion object {
        private const val PDF_MIME_TYPE = "application/pdf"

        private const val PDF_ATTACHMENT_ID = "pdf-attachment"

        private const val WORD_ATTACHMENT_ID = "word-attachment"

        private const val WORD_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        private val JSON: Json = Json { ignoreUnknownKeys = true }
    }
}
