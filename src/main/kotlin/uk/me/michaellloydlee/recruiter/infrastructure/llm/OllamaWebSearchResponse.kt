package uk.me.michaellloydlee.recruiter.infrastructure.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The answer of the web search of Ollama.
 *
 * @property results the pages the search found.
 */
@Serializable
data class OllamaWebSearchResponse(
    val results: List<OllamaWebSearchResult> = emptyList(),
)

/**
 * A page of the web the search found.
 *
 * @property title the title of the page.
 * @property url the address of the page.
 * @property content the part of the page the search holds.
 */
@Serializable
data class OllamaWebSearchResult(
    val title: String = "",
    val url: String = "",
    val content: String = "",
)

/**
 * The outcome of a web search.
 *
 * A search that cannot be made is reported, it never throws: the caller
 * decides what it does without the pages of the web.
 */
sealed interface WebSearchOutcome {
    /**
     * The search found pages.
     *
     * @property results the pages the search found.
     */
    data class Found(
        val results: List<OllamaWebSearchResult>,
    ) : WebSearchOutcome

    /**
     * The search could not be made.
     *
     * @property reason why the search failed.
     */
    data class Failed(
        val reason: String,
    ) : WebSearchOutcome
}

/**
 * The request to the web search of Ollama.
 *
 * @property query what is searched for.
 * @property maxResults how many pages the search returns.
 */
@Serializable
data class OllamaWebSearchRequest(
    val query: String,
    @SerialName("max_results")
    val maxResults: Int,
)
