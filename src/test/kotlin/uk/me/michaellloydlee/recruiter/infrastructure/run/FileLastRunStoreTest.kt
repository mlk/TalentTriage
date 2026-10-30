package uk.me.michaellloydlee.recruiter.infrastructure.run

import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileLastRunStoreTest {
    @Test
    fun savesAndLoadsTheMoment() =
        runTest {
            val store = FileLastRunStore(tempFile())

            store.save(MOMENT)

            assertEquals(MOMENT, store.load())
        }

    @Test
    fun returnsNullWhenNoRunSucceededYet() =
        runTest {
            val store = FileLastRunStore(tempFile())

            assertNull(store.load())
        }

    @Test
    fun returnsNullWhenTheFileDoesNotHoldAMoment() =
        runTest {
            val file = tempFile()
            file.writeText("not a moment")
            val store = FileLastRunStore(file)

            assertNull(store.load())
        }

    @Test
    fun keepsTheMomentNextToTheToken() =
        runTest {
            val token = tempDirectory().resolve("token.json")
            val store = FileLastRunStore.nextTo(token)

            store.save(MOMENT)

            assertTrue(token.resolveSibling("token.json.last-run").toFile().exists())
        }

    @Test
    fun replacesTheMomentOfTheLastRun() =
        runTest {
            val store = FileLastRunStore(tempFile())
            store.save(MOMENT)

            store.save(MOMENT.plusSeconds(60))

            assertEquals(MOMENT.plusSeconds(60), store.load())
        }

    private fun tempDirectory(): Path = createTempDirectory("last-run")

    private fun tempFile(): Path = tempDirectory().resolve("token.json.last-run")

    companion object {
        private val MOMENT: Instant = Instant.parse("2026-10-30T18:00:00Z")
    }
}
