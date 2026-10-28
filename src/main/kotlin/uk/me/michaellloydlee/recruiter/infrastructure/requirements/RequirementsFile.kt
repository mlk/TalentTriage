package uk.me.michaellloydlee.recruiter.infrastructure.requirements

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.domain.requirements.PayRequirement
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads the requirements of the job seeker from a local JSON file.
 *
 * The file holds personal data, it is git-ignored like the credentials and the
 * token.
 */
object RequirementsFile {
    /**
     * Loads the requirements.
     *
     * @param path location of the file.
     * @param json parser used to read the file.
     * @return the requirements of the job seeker.
     * @throws IllegalArgumentException when the file is missing or cannot be read.
     */
    fun load(
        path: Path,
        json: Json = Json { ignoreUnknownKeys = true },
    ): JobRequirements {
        require(Files.exists(path)) {
            "Requirements file not found: $path"
        }
        val file =
            try {
                json.decodeFromString(RequirementsFile.serializer(), Files.readString(path))
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("Requirements file $path could not be read: ${error.message}", error)
            }
        return JobRequirements(
            minimumPay = file.minimumPay?.let { PayRequirement(it.amount, it.currency, it.period) },
            maximumOfficeDays = file.maximumOfficeDays,
            profile = file.profile,
        )
    }

    @Serializable
    private data class RequirementsFile(
        @SerialName("minimum_pay")
        val minimumPay: PayFile? = null,
        @SerialName("maximum_office_days")
        val maximumOfficeDays: Int,
        val profile: String,
    )

    @Serializable
    private data class PayFile(
        val amount: Double,
        val currency: String,
        val period: String = "year",
    )
}
