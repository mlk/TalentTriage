package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.test.runTest
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalAuthorizationServiceTest {
    @Test
    fun catchesTheCodeAndStoresTheToken() =
        runTest {
            val oauth = TestOAuth2Client()
            val store = temporaryTokenStore()
            val service = service(oauth.client, store)

            service.start()
            try {
                val page = redirect(service, "code=the-code&state=${service.state()}")

                assertTrue(page.contains("The application is authorised"))
                val outcome = service.awaitAuthorization()
                assertTrue(outcome is AuthorizationOutcome.Authorised)
                assertEquals("refreshed-access-token", store.load()?.accessToken)
            } finally {
                service.stop()
            }
        }

    @Test
    fun refusesARedirectWithAnotherState() =
        runTest {
            val oauth = TestOAuth2Client()
            val store = temporaryTokenStore()
            val service = service(oauth.client, store)

            service.start()
            try {
                val page = redirect(service, "code=the-code&state=another-state")

                assertTrue(page.contains("The authorisation failed"))
                val outcome = service.awaitAuthorization()
                assertEquals(
                    AuthorizationOutcome.Failed("The redirect does not carry the state of the authorisation"),
                    outcome,
                )
                assertEquals(null, store.load())
            } finally {
                service.stop()
            }
        }

    @Test
    fun refusesARedirectWithoutACode() =
        runTest {
            val oauth = TestOAuth2Client()
            val store = temporaryTokenStore()
            val service = service(oauth.client, store)

            service.start()
            try {
                val page = redirect(service, "state=${service.state()}")

                assertTrue(page.contains("The authorisation failed"))
                val outcome = service.awaitAuthorization()
                assertEquals(
                    AuthorizationOutcome.Failed("The redirect of the authorisation carries no code"),
                    outcome,
                )
            } finally {
                service.stop()
            }
        }

    @Test
    fun refusesARedirectThatGoogleRefused() =
        runTest {
            val oauth = TestOAuth2Client()
            val store = temporaryTokenStore()
            val service = service(oauth.client, store)

            service.start()
            try {
                redirect(service, "error=access_denied&state=${service.state()}")

                val outcome = service.awaitAuthorization()
                assertEquals(
                    AuthorizationOutcome.Failed("The authorisation was refused: access_denied"),
                    outcome,
                )
            } finally {
                service.stop()
            }
        }

    @Test
    fun failsWhenTheAuthorisationDoesNotArrive() =
        runTest {
            val oauth = TestOAuth2Client()
            val store = temporaryTokenStore()
            val service = service(oauth.client, store, timeout = Duration.ofMillis(50))

            service.start()
            try {
                val outcome = service.awaitAuthorization()

                assertEquals(
                    AuthorizationOutcome.Failed("The authorisation did not arrive within PT0.05S"),
                    outcome,
                )
            } finally {
                service.stop()
            }
        }

    @Test
    fun listensOnAFreePortOfTheLoopbackInterface() =
        runTest {
            val oauth = TestOAuth2Client()
            val store = temporaryTokenStore()
            val service = service(oauth.client, store)

            service.start()
            try {
                assertTrue(service.redirectUri().startsWith("http://localhost:"))
                assertTrue(service.redirectUri() != "http://localhost:0")
            } finally {
                service.stop()
            }
        }

    private suspend fun service(
        oauth2Client: GmailOAuth2Client,
        store: FileOAuth2TokenStore,
        timeout: Duration = LocalAuthorizationService.DEFAULT_TIMEOUT,
    ): LocalAuthorizationService =
        LocalAuthorizationService(
            authenticator = GmailAuthenticator(oauth2Client = oauth2Client, tokenStore = store),
            logger = StructuredLogger(StringBuilder()),
            timeout = timeout,
        )

    /**
     * The redirect of the browser to the local service.
     */
    private suspend fun redirect(
        service: LocalAuthorizationService,
        query: String,
    ): String {
        val client = HttpClient(CIO)
        return try {
            client.get("${service.redirectUri()}/?$query").bodyAsText()
        } finally {
            client.close()
        }
    }
}
