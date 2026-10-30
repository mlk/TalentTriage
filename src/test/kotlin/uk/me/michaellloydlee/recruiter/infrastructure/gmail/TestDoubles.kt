package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Duration

/**
 * A token store that keeps the token in memory.
 *
 * @param token the token the store starts with.
 */
class FakeTokenStore(
    private var token: OAuth2Token?,
) : OAuth2TokenStore {
    /** The token of the last [save]. */
    var saved: OAuth2Token? = null
        private set

    override suspend fun load(): OAuth2Token? = token

    override suspend fun save(token: OAuth2Token) {
        this.token = token
        saved = token
    }
}

/**
 * The OAuth2 client of a test, with a mocked token endpoint.
 *
 * The client is the real one, only the endpoint it talks to is a mock.
 *
 * @property engine the mocked token endpoint, its request history counts the refreshes.
 * @property client the OAuth2 client that talks to the mocked endpoint.
 */
class TestOAuth2Client(
    private val accessToken: String = "refreshed-access-token",
    private val answerDelay: Duration = Duration.ZERO,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** The mocked token endpoint. */
    val engine: MockEngine =
        MockEngine {
            delay(answerDelay.toMillis())
            respond(
                content = """{"access_token":"$accessToken","expires_in":3600}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    /** The OAuth2 client that talks to the mocked endpoint. */
    val client: GmailOAuth2Client =
        GmailOAuth2Client(
            httpClient = HttpClient(engine),
            credentials =
                GoogleClientCredentials(
                    clientId = "client-id",
                    clientSecret = "client-secret",
                ),
            clock = clock,
        )

    /** How often the token endpoint was asked for a token. */
    val refreshCount: Int get() = engine.requestHistory.size
}
