package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class GmailServicesTest {
    @Test
    fun assemblesTheComponents() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"messages":[{"id":"message-id","threadId":"thread-id"}]}""",
                        status = HttpStatusCode.OK,
                    )
                }

            val services =
                GmailServices.create(
                    credentials = GoogleClientCredentials(clientId = "client-id", clientSecret = "client-secret"),
                    tokenStore = FakeTokenStore(token()),
                    httpClient = HttpClient(engine),
                )

            val summaries = services.messages.listUnprocessed()
            assertEquals(listOf("message-id"), summaries.map { it.id })
            services.close()
        }

    private fun token(): OAuth2Token =
        OAuth2Token(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = Instant.now().plusSeconds(600),
        )
}
