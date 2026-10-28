package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.long
import io.ktor.client.HttpClient
import uk.me.michaellloydlee.recruiter.application.ProcessInboxService
import uk.me.michaellloydlee.recruiter.application.ProcessInboxSummary
import uk.me.michaellloydlee.recruiter.application.evaluation.AttachmentTextReader
import uk.me.michaellloydlee.recruiter.application.evaluation.ChatModelEvaluator
import uk.me.michaellloydlee.recruiter.application.evaluation.DecisionModelEvaluator
import uk.me.michaellloydlee.recruiter.application.evaluation.EvaluationService
import uk.me.michaellloydlee.recruiter.application.evaluation.JobDescriptionEvaluationMode
import uk.me.michaellloydlee.recruiter.application.evaluation.JobDescriptionEvaluator
import uk.me.michaellloydlee.recruiter.application.screening.ChatModelVerifier
import uk.me.michaellloydlee.recruiter.application.screening.DecisionModelVerifier
import uk.me.michaellloydlee.recruiter.application.screening.RecruiterVerificationMode
import uk.me.michaellloydlee.recruiter.application.screening.RecruiterVerifier
import uk.me.michaellloydlee.recruiter.application.screening.ScreeningService
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.domain.screening.RecruiterHeuristics
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.FileOAuth2TokenStore
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailApiException
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailAuthenticationException
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailHttpClientFactory
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailServices
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GoogleClientCredentials
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaException
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import uk.me.michaellloydlee.recruiter.infrastructure.requirements.RequirementsFile
import java.nio.file.Path

/**
 * Runs a single inbox triage pass.
 *
 * The command fetches the messages of the inbox that were not processed yet
 * and screens them: the ones that are not recruiter mails are filtered out
 * with the label `Recruiters/FilteredOut`. The deep evaluation of the
 * attachments of the recruiter mails comes with its own story.
 *
 * @param logger sink for the structured log output of the run.
 * @param httpClientFactory creates the client used to talk to Google and to
 *   the models of the pipeline. It is called once per invocation and the client is closed
 *   when the command is done, so the factory must return a fresh client every
 *   time.
 */
class ProcessInboxCommand(
    private val logger: StructuredLogger,
    private val httpClientFactory: () -> HttpClient = { GmailHttpClientFactory.create() },
) : SuspendingCliktCommand(name = COMMAND_NAME) {
    private val credentialsFile: String by
        option(
            "--credentials",
            "-c",
            help = "The file with the OAuth2 client credentials",
        ).default(AuthenticateCommand.DEFAULT_CREDENTIALS_FILE)

    private val tokenFile: String by
        option(
            "--token",
            "-t",
            help = "The file the OAuth2 token is stored in",
        ).default(AuthenticateCommand.DEFAULT_TOKEN_FILE)

    private val recruiterVerification: RecruiterVerificationMode by
        option(
            "--recruiter-verification",
            "-r",
            help = "How the messages are verified: a decision model of Ollama or a chat model",
        ).enum<RecruiterVerificationMode>().default(RecruiterVerificationMode.DECISION, defaultForHelp = "decision")

    private val jobDescriptionEvaluation: JobDescriptionEvaluationMode by
        option(
            "--job-description-evaluation",
            "-e",
            help = "How the job descriptions are evaluated: a decision model of Ollama or a chat model",
        ).enum<JobDescriptionEvaluationMode>().default(
            JobDescriptionEvaluationMode.DECISION,
            defaultForHelp = "decision",
        )

    private val jobDescriptionEvaluationModel: String by
        option(
            "--job-description-evaluation-model",
            "-j",
            help =
                "The model that evaluates the job descriptions: the decision models are nimble, tev1, " +
                    "clef and clef:cloud, a chat model is needed for --job-description-evaluation=chat. " +
                    "The models with the suffix :cloud run in the cloud of Ollama and need a paid plan " +
                    "or usage credits",
        ).default(DecisionModelEvaluator.DEFAULT_MODEL)

    private val requirementsFile: String by
        option(
            "--requirements",
            "-q",
            help = "The file with the requirements of the job seeker",
        ).default(DEFAULT_REQUIREMENTS_FILE)

    private val recruiterVerificationEndpoint: String by
        option(
            "--recruiter-verification-endpoint",
            help = "The endpoint of the model that verifies the recruiter mails",
        ).default(OllamaChatClient.DEFAULT_BASE_URL)

    private val recruiterVerificationTimeout: Long by
        option(
            "--recruiter-verification-timeout",
            help = "How long the verification of a recruiter mail may take, in milliseconds",
        ).long().default(OllamaChatClient.DEFAULT_TIMEOUT_MILLIS)

    private val jobDescriptionEvaluationEndpoint: String by
        option(
            "--job-description-evaluation-endpoint",
            help = "The endpoint of the model that evaluates the job descriptions",
        ).default(OllamaChatClient.DEFAULT_BASE_URL)

    private val jobDescriptionEvaluationTimeout: Long by
        option(
            "--job-description-evaluation-timeout",
            help = "How long the evaluation of a job description may take, in milliseconds",
        ).long().default(DEFAULT_EVALUATION_TIMEOUT_MILLIS)

    private val recruiterVerificationModel: String by
        option(
            "--recruiter-verification-model",
            "-m",
            help =
                "The model that verifies the messages: the decision models are nimble, tev1, " +
                    "clef and clef:cloud, a chat model is needed for --recruiter-verification=llm. " +
                    "The models with the suffix :cloud run in the cloud of Ollama and need a paid plan " +
                    "or usage credits",
        ).default(DecisionModelVerifier.DEFAULT_MODEL)

    override suspend fun run() {
        logger.info(
            event = "$COMMAND_NAME.started",
            message = "Inbox triage run started",
            attributes = mapOf(TRIGGER_ATTRIBUTE to TRIGGER_CLI),
        )
        val credentials = credentials()
        val requirements = requirements()
        httpClientFactory().use { client ->
            val gmail =
                GmailServices.create(
                    credentials = credentials,
                    tokenStore = FileOAuth2TokenStore(Path.of(tokenFile)),
                    httpClient = client,
                )
            val summary =
                try {
                    ProcessInboxService(
                        messages = gmail.messages,
                        screening =
                            ScreeningService(
                                heuristics = RecruiterHeuristics(),
                                verifier = verifier(client),
                                stateManager = gmail.stateManager,
                            ),
                        evaluation =
                            EvaluationService(
                                evaluator = evaluator(client),
                                stateManager = gmail.stateManager,
                            ),
                        attachments = AttachmentTextReader(gmail.messages),
                        requirements = requirements,
                        logger = logger,
                    ).run()
                } catch (error: GmailApiException) {
                    throw CommandFailedException(error.message.orEmpty(), error)
                } catch (error: OllamaException) {
                    throw CommandFailedException(error.message.orEmpty(), error)
                }
            logger.info(
                event = "$COMMAND_NAME.completed",
                message = "Inbox triage run completed",
                attributes = summary.attributes(),
            )
        }
    }

    /**
     * The verifier of the recruiter verification, the decision model of Ollama
     * or a chat model.
     */
    private fun verifier(client: HttpClient): RecruiterVerifier {
        val endpoint = endpoint(recruiterVerificationEndpoint)
        val timeout = recruiterVerificationTimeout
        return when (recruiterVerification) {
            RecruiterVerificationMode.DECISION -> {
                DecisionModelVerifier(
                    client =
                        OllamaDecisionClient(
                            httpClient = client,
                            baseUrl = endpoint,
                            timeoutMillis = timeout,
                        ),
                    model = recruiterVerificationModel,
                )
            }

            else -> {
                ChatModelVerifier(
                    client =
                        OllamaChatClient(
                            httpClient = client,
                            baseUrl = endpoint,
                            timeoutMillis = timeout,
                        ),
                    model = recruiterVerificationModel,
                )
            }
        }
    }

    /**
     * The endpoint of a model, it has to be a URL the client can talk to.
     */
    private fun endpoint(value: String): String =
        try {
            require(value.startsWith("http://") || value.startsWith("https://")) {
                "'$value' is not an endpoint, it needs a scheme like http:// or https://"
            }
            value
        } catch (error: IllegalArgumentException) {
            throw CommandFailedException(error.message.orEmpty(), error)
        }

    /**
     * The credentials of the Google application.
     */
    private fun credentials(): GoogleClientCredentials =
        try {
            GoogleClientCredentials.load(Path.of(credentialsFile))
        } catch (error: GmailAuthenticationException) {
            throw CommandFailedException(error.message.orEmpty(), error)
        }

    /**
     * The requirements of the job seeker, they are read from the file of the
     * working directory.
     */
    private fun requirements(): JobRequirements =
        try {
            RequirementsFile.load(Path.of(requirementsFile))
        } catch (error: IllegalArgumentException) {
            throw CommandFailedException(error.message.orEmpty(), error)
        }

    /**
     * The counters of the run, they go into the log.
     */
    private fun ProcessInboxSummary.attributes(): Map<String, String> =
        mapOf(
            "screened" to screened.toString(),
            "recruiters" to recruiters.toString(),
            "filtered_out" to filteredOut.toString(),
            "worth_follow_up" to worthFollowUp.toString(),
            "needs_pay_check" to needsPayCheck.toString(),
            "trashed" to trashed.toString(),
            "unknown" to unknown.toString(),
        )

    /**
     * The evaluator of the job descriptions, the decision model of Ollama or a
     * chat model.
     */
    private fun evaluator(client: HttpClient): JobDescriptionEvaluator {
        val endpoint = endpoint(jobDescriptionEvaluationEndpoint)
        val timeout = jobDescriptionEvaluationTimeout
        return when (jobDescriptionEvaluation) {
            JobDescriptionEvaluationMode.DECISION -> {
                DecisionModelEvaluator(
                    client =
                        OllamaDecisionClient(
                            httpClient = client,
                            baseUrl = endpoint,
                            timeoutMillis = timeout,
                        ),
                    model = jobDescriptionEvaluationModel,
                )
            }

            JobDescriptionEvaluationMode.CHAT -> {
                ChatModelEvaluator(
                    client =
                        OllamaChatClient(
                            httpClient = client,
                            baseUrl = endpoint,
                            timeoutMillis = timeout,
                        ),
                    model = jobDescriptionEvaluationModel,
                )
            }
        }
    }

    override fun help(context: Context): String = "Fetch, triage and label the unprocessed messages of the inbox."

    companion object {
        /** Name of the subcommand. */
        const val COMMAND_NAME: String = "process-inbox"

        /** Name of the attribute that records what triggered the run. */
        const val TRIGGER_ATTRIBUTE: String = "trigger"

        /** Value of the trigger attribute for a run started from the command line. */
        const val TRIGGER_CLI: String = "cli"

        /** The file with the requirements of the job seeker. */
        const val DEFAULT_REQUIREMENTS_FILE: String = "requirements.json"

        /** How long the evaluation of a job description may take, in milliseconds. */
        const val DEFAULT_EVALUATION_TIMEOUT_MILLIS: Long = 600_000
    }
}
