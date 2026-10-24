package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GmailHttpClientFactoryTest {
    @Test
    fun createsAClientThatCanBeClosed() {
        val client = GmailHttpClientFactory.create()

        assertNotNull(client)
        client.close()
    }

    @Test
    fun usesAPositiveTimeout() {
        assertTrue(GmailHttpClientFactory.DEFAULT_TIMEOUT_MILLIS > 0)
    }
}
