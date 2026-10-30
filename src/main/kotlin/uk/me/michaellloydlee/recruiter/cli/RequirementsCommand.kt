package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import io.ktor.client.HttpClient
import uk.me.michaellloydlee.recruiter.application.requirements.CvProfileWriter
import uk.me.michaellloydlee.recruiter.application.requirements.PayChooser
import uk.me.michaellloydlee.recruiter.application.requirements.RequirementsGenerator
import uk.me.michaellloydlee.recruiter.application.requirements.RequirementsOutcome
import uk.me.michaellloydlee.recruiter.application.requirements.SalaryScaleLookup
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailHttpClientFactory
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaWebSearchClient
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import uk.me.michaellloydlee.recruiter.infrastructure.parser.DocumentTextExtractor
import uk.me.michaellloydlee.recruiter.infrastructure.parser.ExtractionResult
import uk.me.michaellloydlee.recruiter.infrastructure.parser.documentTextExtractorFor
import uk.me.michaellloydlee.recruiter.infrastructure.requirements.RequirementsFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Generates the requirements of the job seeker from the CV.
 *
 * The command reads the CV, a PDF or a Word document, and writes the
 * requirements of the job seeker to a file: the profile the job descriptions
 * are compared with, the minimum pay and the number of days of office a job
 * may demand. The file of the command is the file of the pipeline, the run of
 * the inbox reads it.
 *
 * @param logger sink for the structured log output.
 * @param httpClientFactory creates the client used to talk to the models and
 *   to the web. It is called once per invocation and the client is closed
 *   when the command is done, so the factory must return a fresh client every
 *   time.
 * @param environment the environment of the command, it holds the API key of
 *   the web search of Ollama.
 */
class RequirementsCommand(
    private val logger: StructuredLogger,
    private val httpClientFactory: () -> HttpClient = { GmailHttpClientFactory.create() },
    private val environment: Map<String, String> = System.getenv(),
) : SuspendingCliktCommand(name = COMMAND_NAME) {
    private val cvFile: String by
        argument(
            "CV",
            help = "The CV of the job seeker, a PDF or a Word document",
        )

    private val requirementsFile: String by
        option(
            "--requirements",
            "-q",
            help = "The file the requirements of the job seeker are written to",
        ).default(DEFAULT_REQUIREMENTS_FILE)

    private val force: Boolean by
        option(
            "--force",
            "-f",
            help = "Overwrite the file of the requirements when it exists",
        ).flag()

    private val maxOfficeDays: Int by
        option(
            "--max-office-days",
            help = "The number of days of office a job may demand",
        ).int().default(DEFAULT_MAX_OFFICE_DAYS)

    private val model: String by
        option(
            "--model",
            "-m",
            help =
                "The chat model that writes the profile of the job seeker. The models with the " +
                    "suffix :cloud run in the cloud of Ollama and need a paid plan or usage credits",
        ).default(CvProfileWriter.DEFAULT_MODEL)

    private val decisionModel: String by
        option(
            "--decision-model",
            "-d",
            help =
                "The decision model that names the minimum pay: the decision models are nimble, " +
                    "tev1, clef and clef:cloud",
        ).default(PayChooser.DEFAULT_MODEL)

    private val endpoint: String by
        option(
            "--endpoint",
            help = "The endpoint of the models",
        ).default(OllamaChatClient.DEFAULT_BASE_URL)

    private val timeout: Long by
        option(
            "--timeout",
            help = "How long a call to a model may take, in milliseconds",
        ).long().default(DEFAULT_TIMEOUT_MILLIS)

    override suspend fun run() {
        val target = Path.of(requirementsFile)
        if (Files.exists(target) && !force) {
            throw CommandFailedException("The file of the requirements $target exists, --force overwrites it")
        }
        if (maxOfficeDays < 0) {
            throw CommandFailedException("The number of days of office cannot be negative")
        }
        val cv = cvText()
        logger.info(
            event = "$COMMAND_NAME.started",
            message = "The requirements of the job seeker are generated",
            attributes = mapOf("cv" to cvFile),
        )
        httpClientFactory().use { client ->
            val outcome = generator(client).generate(cv, maxOfficeDays)
            when (outcome) {
                is RequirementsOutcome.Failed -> {
                    throw CommandFailedException(outcome.reason)
                }

                is RequirementsOutcome.Generated -> {
                    save(target, outcome.requirements)
                    logger.info(
                        event = "$COMMAND_NAME.completed",
                        message = "The requirements of the job seeker are written",
                        attributes = attributes(target, outcome),
                    )
                    printSuggestion(target, outcome)
                }
            }
        }
    }

    /**
     * The generator of the requirements.
     *
     * @param client the client of the models and of the web.
     * @return the generator of the requirements.
     */
    private fun generator(client: HttpClient): RequirementsGenerator {
        val endpoint = modelEndpoint()
        val timeout = timeout
        return RequirementsGenerator(
            profileWriter =
                CvProfileWriter(
                    client = OllamaChatClient(httpClient = client, baseUrl = endpoint, timeoutMillis = timeout),
                    model = model,
                ),
            salaryLookup =
                SalaryScaleLookup(
                    search =
                        OllamaWebSearchClient(
                            httpClient = client,
                            apiKey = environment[API_KEY_VARIABLE],
                        ),
                ),
            payChooser =
                PayChooser(
                    client = OllamaDecisionClient(httpClient = client, baseUrl = endpoint, timeoutMillis = timeout),
                    model = decisionModel,
                ),
        )
    }

    /**
     * The endpoint of a model, it has to be a URL the client can talk to.
     */
    private fun modelEndpoint(): String =
        try {
            require(endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
                "'$endpoint' is not an endpoint, it needs a scheme like http:// or https://"
            }
            endpoint
        } catch (error: IllegalArgumentException) {
            throw CommandFailedException(error.message.orEmpty(), error)
        }

    /**
     * The text of the CV.
     *
     * @return the text of the CV of the job seeker.
     */
    private fun cvText(): String =
        when (val outcome = readCv(Path.of(cvFile))) {
            is CvReading.Unreadable -> throw CommandFailedException(outcome.reason)
            is CvReading.Read -> outcome.text
        }

    /**
     * The text of the CV of the job seeker.
     *
     * @param path the file of the CV.
     * @return the text of the CV, the reason why it could not be read.
     */
    private fun readCv(path: Path): CvReading {
        if (!Files.exists(path)) {
            return CvReading.Unreadable("The CV $path does not exist")
        }
        val extractor =
            documentTextExtractorFor(path.fileName.toString())
                ?: return CvReading.Unreadable("The CV $path is not a PDF or a Word document")
        return readCv(path, extractor)
    }

    /**
     * The text of the document of the CV.
     *
     * @param path the file of the CV.
     * @param extractor the extractor of the type of the CV.
     * @return the text of the CV, the reason why it could not be read.
     */
    private fun readCv(
        path: Path,
        extractor: DocumentTextExtractor,
    ): CvReading {
        val content =
            try {
                Files.readAllBytes(path)
            } catch (error: IOException) {
                return CvReading.Unreadable("The CV $path could not be read: ${error.message}")
            }
        val text =
            when (val result = extractor.extract(content)) {
                is ExtractionResult.Extracted -> {
                    result.text
                }

                is ExtractionResult.Failed -> {
                    return CvReading.Unreadable("The CV $path could not be read: ${result.reason}")
                }
            }
        return if (text.isBlank()) {
            CvReading.Unreadable("The CV $path holds no text")
        } else {
            CvReading.Read(text)
        }
    }

    /**
     * The requirements are written to the file.
     *
     * @param target the file of the requirements.
     * @param requirements the requirements of the job seeker.
     */
    private fun save(
        target: Path,
        requirements: JobRequirements,
    ) {
        try {
            RequirementsFile.save(target, requirements)
        } catch (error: IllegalArgumentException) {
            throw CommandFailedException(error.message.orEmpty(), error)
        }
    }

    /**
     * The attributes of the run, they go into the log.
     *
     * @param target the file of the requirements.
     * @param outcome the outcome of the generation.
     * @return the attributes of the run.
     */
    private fun attributes(
        target: Path,
        outcome: RequirementsOutcome.Generated,
    ): Map<String, String> {
        val requirements = outcome.requirements
        return mapOf(
            "file" to target.toString(),
            "minimum_pay" to (requirements.minimumPay?.description ?: NO_PAY),
            "maximum_office_days" to requirements.maximumOfficeDays.toString(),
            "notes" to outcome.notes.joinToString(separator = "; "),
        )
    }

    /**
     * The command prints what it suggests.
     *
     * @param target the file of the requirements.
     * @param outcome the outcome of the generation.
     */
    private fun printSuggestion(
        target: Path,
        outcome: RequirementsOutcome.Generated,
    ) {
        val requirements = outcome.requirements
        echo("The requirements of the job seeker:", err = true)
        echo("  profile: ${requirements.profile}", err = true)
        echo("  minimum pay: ${requirements.minimumPay?.description ?: NO_PAY}", err = true)
        echo("  maximum office days: ${requirements.maximumOfficeDays}", err = true)
        echo("  file: $target", err = true)
        outcome.notes.forEach { note -> echo("  note: $note", err = true) }
    }

    override fun help(context: Context): String =
        "Write the requirements of the job seeker from the CV of the job seeker."

    companion object {
        /** Name of the subcommand. */
        const val COMMAND_NAME: String = "requirements"

        /** The file the requirements are written to by default. */
        const val DEFAULT_REQUIREMENTS_FILE: String = "requirements.json"

        /** The number of days of office a job may demand by default. */
        const val DEFAULT_MAX_OFFICE_DAYS: Int = 2

        /** How long a call to a model may take, in milliseconds. */
        const val DEFAULT_TIMEOUT_MILLIS: Long = 600_000

        /** The variable of the environment that holds the API key of Ollama. */
        const val API_KEY_VARIABLE: String = "OLLAMA_API_KEY"

        /** The pay of a requirements file without one. */
        const val NO_PAY: String = "not set"
    }
}

/**
 * The outcome of the reading of the CV.
 */
private sealed interface CvReading {
    /**
     * The CV was read.
     *
     * @property text the text of the CV.
     */
    data class Read(
        val text: String,
    ) : CvReading

    /**
     * The CV could not be read.
     *
     * @property reason why the CV could not be read.
     */
    data class Unreadable(
        val reason: String,
    ) : CvReading
}
