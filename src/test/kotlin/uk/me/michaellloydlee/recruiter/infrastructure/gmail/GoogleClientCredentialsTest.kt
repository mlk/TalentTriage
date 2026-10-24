package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GoogleClientCredentialsTest {
    @Test
    fun loadsTheFlatFormat() {
        val file =
            credentialsFile(
                """
                {
                  "client_id": "client-id",
                  "client_secret": "client-secret"
                }
                """.trimIndent(),
            )

        val credentials = GoogleClientCredentials.load(file)

        assertEquals("client-id", credentials.clientId)
        assertEquals("client-secret", credentials.clientSecret)
        assertEquals(GoogleClientCredentials.DEFAULT_AUTH_URI, credentials.authUri)
        assertEquals(GoogleClientCredentials.DEFAULT_TOKEN_URI, credentials.tokenUri)
    }

    @Test
    fun loadsTheInstalledFormatOfGoogle() {
        val file =
            credentialsFile(
                """
                {
                  "installed": {
                    "client_id": "client-id",
                    "client_secret": "client-secret",
                    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                    "token_uri": "https://oauth2.googleapis.com/token",
                    "redirect_uris": ["http://localhost"]
                  }
                }
                """.trimIndent(),
            )

        val credentials = GoogleClientCredentials.load(file)

        assertEquals("client-id", credentials.clientId)
        assertEquals("client-secret", credentials.clientSecret)
        assertEquals(listOf("http://localhost"), credentials.redirectUris)
    }

    @Test
    fun loadsTheWebFormatOfGoogle() {
        val file =
            credentialsFile(
                """
                {
                  "web": {
                    "client_id": "web-client-id",
                    "client_secret": "web-client-secret"
                  }
                }
                """.trimIndent(),
            )

        val credentials = GoogleClientCredentials.load(file)

        assertEquals("web-client-id", credentials.clientId)
        assertEquals("web-client-secret", credentials.clientSecret)
    }

    @Test
    fun failsWhenTheFileIsMissing() {
        val missing = Path.of("/does/not/exist/credentials.json")

        val error = assertFailsWith<GmailAuthenticationException> { GoogleClientCredentials.load(missing) }

        assertTrue(error.message!!.contains("Credentials file not found"))
    }

    @Test
    fun failsWhenTheFileIsNotValidJson() {
        val file = credentialsFile("not json at all")

        val error = assertFailsWith<GmailAuthenticationException> { GoogleClientCredentials.load(file) }

        assertTrue(error.message!!.contains("could not be parsed"))
    }

    @Test
    fun failsWhenTheFileHasNoClientId() {
        val file = credentialsFile("""{"client_secret": "client-secret"}""")

        assertFailsWith<GmailAuthenticationException> { GoogleClientCredentials.load(file) }
    }

    private fun credentialsFile(content: String): Path {
        val directory = createTempDirectory("credentials")
        val file = directory.resolve("credentials.json")
        file.writeText(content)
        return file
    }
}
