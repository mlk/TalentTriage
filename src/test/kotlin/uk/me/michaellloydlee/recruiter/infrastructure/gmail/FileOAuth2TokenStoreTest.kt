package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileOAuth2TokenStoreTest {
    @Test
    fun savesAndLoadsTheToken() =
        runTest {
            val file = tempFile()
            val store = FileOAuth2TokenStore(file)
            val token =
                OAuth2Token(
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                    expiresAt = Instant.parse("2026-10-24T09:00:00Z"),
                    scope = "scope-a",
                )

            store.save(token)

            assertEquals(token, store.load())
            assertTrue(file.readText().contains("access-token"))
        }

    @Test
    fun returnsNullWhenNothingIsStored() =
        runTest {
            val store = FileOAuth2TokenStore(tempDirectory().resolve("missing.json"))

            assertNull(store.load())
        }

    @Test
    fun createsTheDirectoryOfTheTokenFile() =
        runTest {
            val file = tempDirectory().resolve("nested").resolve("token.json")
            val store = FileOAuth2TokenStore(file)

            store.save(OAuth2Token(accessToken = "access-token"))

            assertTrue(file.toFile().exists())
        }

    @Test
    fun replacesTheStoredToken() =
        runTest {
            val file = tempFile()
            val store = FileOAuth2TokenStore(file)

            store.save(OAuth2Token(accessToken = "first-token"))
            store.save(OAuth2Token(accessToken = "second-token"))

            assertEquals("second-token", store.load()?.accessToken)
        }

    @Test
    fun leavesNoTemporaryFilesBehind() =
        runTest {
            val directory = tempDirectory()
            val store = FileOAuth2TokenStore(directory.resolve("token.json"))

            store.save(OAuth2Token(accessToken = "access-token"))

            assertEquals(listOf("token.json"), directory.toFile().list()!!.toList())
        }

    private fun tempDirectory(): Path = createTempDirectory("token-store")

    private fun tempFile(): Path = tempDirectory().resolve("token.json")
}
