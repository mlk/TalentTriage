package uk.me.michaellloydlee.recruiter.infrastructure.requirements

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.domain.requirements.PayRequirement
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads and writes the requirements of the job seeker in a local JSON file.
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
            shareMinimumPay = file.shareMinimumPay,
        )
    }

    /**
     * Writes the requirements to the file.
     *
     * The file is written with the same format the loader reads, so the file
     * of the command can be used by the pipeline without a change.
     *
     * @param path location of the file.
     * @param requirements the requirements of the job seeker.
     * @param json encoder used to write the file.
     * @throws IllegalArgumentException when the file cannot be written.
     */
    fun save(
        path: Path,
        requirements: JobRequirements,
        json: Json = Json { prettyPrint = true },
    ) {
        val file =
            RequirementsFile(
                minimumPay =
                    requirements.minimumPay?.let {
                        PayFile(it.amount, it.currency, it.period)
                    },
                maximumOfficeDays = requirements.maximumOfficeDays,
                profile = requirements.profile,
                shareMinimumPay = requirements.shareMinimumPay,
            )
        try {
            Files.writeString(path, json.encodeToString(file))
        } catch (error: IOException) {
            throw IllegalArgumentException("Requirements file $path could not be written: ${error.message}", error)
        }
    }

    @Serializable
    private data class RequirementsFile(
        @SerialName("minimum_pay")
        val minimumPay: PayFile? = null,
        @SerialName("maximum_office_days")
        val maximumOfficeDays: Int,
        val profile: String,
        @SerialName("share_minimum_pay")
        val shareMinimumPay: Boolean = false,
    )

    @Serializable
    private data class PayFile(
        val amount: Double,
        val currency: String,
        val period: String = "year",
    )
}
