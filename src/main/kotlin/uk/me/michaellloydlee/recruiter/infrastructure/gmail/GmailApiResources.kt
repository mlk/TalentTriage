package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A message of the Gmail REST API.
 */
@Serializable
internal data class GmailMessageResource(
    val id: String,
    val threadId: String = "",
    val labelIds: List<String> = emptyList(),
    val snippet: String = "",
    val payload: GmailPayloadResource? = null,
)

/**
 * The payload of a message of the Gmail REST API.
 */
@Serializable
internal data class GmailPayloadResource(
    val mimeType: String = "",
    val headers: List<GmailHeaderResource> = emptyList(),
    val body: GmailBodyResource = GmailBodyResource(),
    val parts: List<GmailPayloadResource> = emptyList(),
)

/**
 * A header of a message of the Gmail REST API.
 */
@Serializable
internal data class GmailHeaderResource(
    val name: String = "",
    val value: String = "",
)

/**
 * The body of a payload of the Gmail REST API, the data is base64url encoded.
 */
@Serializable
internal data class GmailBodyResource(
    val data: String? = null,
    val size: Long = 0,
)

/**
 * The answer of the endpoint that lists the messages.
 */
@Serializable
internal data class GmailListMessagesResource(
    val messages: List<GmailMessageSummaryResource> = emptyList(),
    val nextPageToken: String? = null,
    @SerialName("resultSizeEstimate")
    val resultSizeEstimate: Int = 0,
)

/**
 * A message of the list of the Gmail REST API.
 */
@Serializable
internal data class GmailMessageSummaryResource(
    val id: String,
    val threadId: String = "",
)

/**
 * The answer of the endpoint that lists the labels.
 */
@Serializable
internal data class GmailListLabelsResource(
    val labels: List<GmailLabelResource> = emptyList(),
)

/**
 * A label of the Gmail REST API.
 */
@Serializable
internal data class GmailLabelResource(
    val id: String,
    val name: String = "",
    val type: String = "",
)

/**
 * The body of the endpoint that creates a label.
 */
@Serializable
internal data class GmailCreateLabelResource(
    val name: String,
)

/**
 * The body of the endpoint that modifies the labels of a message.
 */
@Serializable
internal data class GmailModifyLabelsResource(
    @SerialName("addLabelIds")
    val addLabelIds: List<String> = emptyList(),
    @SerialName("removeLabelIds")
    val removeLabelIds: List<String> = emptyList(),
)
