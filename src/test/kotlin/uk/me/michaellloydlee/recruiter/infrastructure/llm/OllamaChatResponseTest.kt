package uk.me.michaellloydlee.recruiter.infrastructure.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OllamaChatResponseTest {
    @Test
    fun aResponseWithoutFieldsFallsBackToTheDefaults() {
        val response = OllamaChatResponse()

        assertEquals("", response.model)
        assertEquals("", response.createdAt)
        assertNull(response.message)
        assertTrue(response.done)
        assertNull(response.totalDuration)
        assertNull(response.evalCount)
        assertNull(response.promptEvalCount)
        assertEquals("", response.content)
    }

    @Test
    fun theContentIsTheContentOfTheMessage() {
        val response =
            OllamaChatResponse(
                message = OllamaMessage(role = OllamaMessage.ROLE_ASSISTANT, content = "Hi there"),
            )

        assertEquals("Hi there", response.content)
    }

    @Test
    fun theOptionsAreEmptyByDefault() {
        val options = OllamaOptions()

        assertNull(options.temperature)
        assertNull(options.topP)
        assertNull(options.numCtx)
        assertNull(options.numPredict)
        assertNull(options.seed)
    }

    @Test
    fun theRolesAreTheOnesOfTheApi() {
        assertEquals("system", OllamaMessage.ROLE_SYSTEM)
        assertEquals("user", OllamaMessage.ROLE_USER)
        assertEquals("assistant", OllamaMessage.ROLE_ASSISTANT)
    }
}
