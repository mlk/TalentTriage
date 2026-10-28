package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import uk.me.michaellloydlee.recruiter.domain.gmail.RecruiterLabel
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GmailLabelStateManagerTest {
    @Test
    fun appliesTheStateLabelAndRemovesTheOtherStateLabels() =
        runTest {
            val engine = engine()

            stateManager(engine).apply("message-id", RecruiterLabel.WORTH_FOLLOW_UP)

            val body = bodyOf(engine)
            assertTrue(body.contains("Label_worthfollowup"))
            assertTrue(body.contains("Label_filteredout"))
            assertTrue(body.contains("Label_needspaycheck"))
            assertTrue(body.contains("Label_trash"))
            assertFalse(body.contains("INBOX"))
        }

    @Test
    fun archivesTheMessageWhenTheStateArchivesIt() =
        runTest {
            val engine = engine()

            stateManager(engine).apply("message-id", RecruiterLabel.TRASH)

            val body = bodyOf(engine)
            assertTrue(body.contains("Label_trash"))
            assertTrue(body.contains("INBOX"))
        }

    @Test
    fun keepsTheMessageInTheInboxWhenTheStateDoesNotArchiveIt() =
        runTest {
            val engine = engine()

            stateManager(engine).apply("message-id", RecruiterLabel.FILTERED_OUT)

            val body = bodyOf(engine)
            assertTrue(body.contains("Label_filteredout"))
            assertFalse(body.contains("INBOX"))
        }

    @Test
    fun createsTheStateLabelsThatDoNotExistYet() =
        runTest {
            val engine = engine(existingLabels = listOf("Recruiters/Trash"))

            stateManager(engine).apply("message-id", RecruiterLabel.TRASH)

            val created =
                engine.requestHistory.filter {
                    it.method.value == "POST" && it.url.encodedPath.endsWith("/users/me/labels")
                }
            assertEquals(4, created.size)
            val body = (created.first().body as TextContent).text
            assertTrue(body.contains("Recruiters/FilteredOut"))
        }

    @Test
    fun reusesTheStateLabelsItKnowsAlready() =
        runTest {
            val engine = engine()
            val manager = stateManager(engine)

            manager.apply("message-id", RecruiterLabel.TRASH)
            manager.apply("message-id", RecruiterLabel.FILTERED_OUT)

            val listed =
                engine.requestHistory.count {
                    it.method.value == "GET" && it.url.encodedPath.endsWith("/users/me/labels")
                }
            assertEquals(1, listed)
        }

    private fun bodyOf(engine: MockEngine): String = (engine.requestHistory.last().body as TextContent).text

    private fun engine(existingLabels: List<String> = ALL_LABELS): MockEngine =
        MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/users/me/labels") && request.method.value == "GET" -> {
                    respond(
                        content = """{"labels":[${existingLabels.joinToString(
                            ",",
                        ) { label -> """{"id":"${idOf(label)}","name":"$label"}""" }}]}""",
                        status = HttpStatusCode.OK,
                    )
                }

                request.url.encodedPath.endsWith("/users/me/labels") -> {
                    respond(
                        content = """{"id":"Label_new","name":"new"}""",
                        status = HttpStatusCode.OK,
                    )
                }

                else -> {
                    respond(content = """{"id":"message-id"}""", status = HttpStatusCode.OK)
                }
            }
        }

    private fun idOf(label: String): String = "Label_${label.substringAfterLast('/').lowercase()}"

    private fun stateManager(engine: MockEngine): GmailLabelStateManager {
        val client =
            GmailApiClient(
                httpClient = HttpClient(engine),
                tokens =
                    RefreshingAccessTokenProvider(
                        refresher = FakeRefresher(token("refreshed-access-token")),
                        tokenStore = FakeTokenStore(token("access-token")),
                        clock = Clock.fixed(NOW, ZoneOffset.UTC),
                    ),
                baseUrl = "https://gmail.example/v1",
            )
        val messages = GmailMessagesApi(client)
        return GmailLabelStateManager(messages, GmailLabelsApi(client))
    }

    private fun token(accessToken: String): OAuth2Token =
        OAuth2Token(
            accessToken = accessToken,
            refreshToken = "refresh-token",
            expiresAt = NOW.plusSeconds(600),
        )

    companion object {
        private val NOW: Instant = Instant.parse("2026-10-24T08:00:00Z")

        private val ALL_LABELS =
            listOf(
                "Recruiters/FilteredOut",
                "Recruiters/WorthFollowUp",
                "Recruiters/NeedsPayCheck",
                "Recruiters/NeedsWorkFromHomeCheck",
                "Recruiters/Trash",
            )
    }
}
