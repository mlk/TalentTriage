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
import uk.me.michaellloydlee.recruiter.application.draft.DraftWriter
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
import uk.me.michaellloydlee.recruiter.domain.gmail.MessageWindow
import uk.me.michaellloydlee.recruiter.domain.gmail.messageWindow
import uk.me.michaellloydlee.recruiter.domain.gmail.runWindow
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.domain.screening.JobListingHeuristics
import uk.me.michaellloydlee.recruiter.domain.screening.RecruiterHeuristics
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.FileOAuth2TokenStore
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailApiException
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailAuthenticationException
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailDraftsApi
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailHttpClientFactory
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailServices
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GoogleClientCredentials
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaException
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import uk.me.michaellloydlee.recruiter.infrastructure.requirements.RequirementsFile
import uk.me.michaellloydlee.recruiter.infrastructure.run.FileLastRunStore
import java.nio.file.Path
import java.time.Clock
import java.time.Instant

/**
 * Runs a single inbox triage pass.
 *
 * The command fetches the messages of the inbox that were not processed yet
 * and screens them: the ones that are not recruiter mails are reported and
 * stay unlabelled, the window of the run is what keeps the next run from
 * looking at them again. The recruiter mails are evaluated against the
 * requirements of the job seeker and end in one of the states of the
 * pipeline.
 *
 * @param logger sink for the structured log output of the run.
 * @param httpClientFactory creates the client used to talk to Google and to
 *   the models of the pipeline. It is called once per invocation and the client is closed
 *   when the command is done, so the factory must return a fresh client every
 *   time.
 * @param clock source of the current time, it decides how far back the run
 *   looks for messages.
 */
class ProcessInboxCommand(
    private val logger: StructuredLogger,
    private val httpClientFactory: () -> HttpClient = { GmailHttpClientFactory.create() },
    private val clock: Clock = Clock.systemUTC(),
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

    private val since: String? by
        option(
            "--since",
            help =
                "How far back the messages are looked for, an ISO-8601 duration like PT24H or P1D. " +
                    "The default is the gap since the last successful run",
        )

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

    private val draftModel: String by
        option(
            "--draft-model",
            "-d",
            help =
                "The chat model that writes the draft that asks the recruiter for the missing " +
                    "information. The models with the suffix :cloud run in the cloud of Ollama and " +
                    "need a paid plan or usage credits",
        ).default(DraftWriter.DEFAULT_MODEL)

    private val draftEndpoint: String by
        option(
            "--draft-endpoint",
            help = "The endpoint of the model that writes the drafts",
        ).default(OllamaChatClient.DEFAULT_BASE_URL)

    private val draftTimeout: Long by
        option(
            "--draft-timeout",
            help = "How long the writing of a draft may take, in milliseconds",
        ).long().default(OllamaChatClient.DEFAULT_TIMEOUT_MILLIS)

    override suspend fun run() {
        logger.info(
            event = "$COMMAND_NAME.started",
            message = "Inbox triage run started",
            attributes = mapOf(TRIGGER_ATTRIBUTE to TRIGGER_CLI),
        )
        val startedAt = clock.instant()
        val window = window(startedAt)
        val since = startedAt.minus(window.duration)
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
                        since = since,
                        screening =
                            ScreeningService(
                                heuristics = RecruiterHeuristics(),
                                jobListings = JobListingHeuristics(),
                                verifier = verifier(client),
                                stateManager = gmail.stateManager,
                            ),
                        evaluation =
                            EvaluationService(
                                evaluator = evaluator(client),
                                attachments = AttachmentTextReader(gmail.messages, logger),
                                stateManager = gmail.stateManager,
                            ),
                        drafts = drafts(client, gmail.drafts),
                        requirements = requirements,
                        logger = logger,
                    ).run()
                } catch (error: GmailApiException) {
                    throw CommandFailedException(error.message.orEmpty(), error)
                } catch (error: OllamaException) {
                    throw CommandFailedException(error.message.orEmpty(), error)
                }
            // The run succeeded: the next run looks back to this moment, the
            // mail that was screened out is not looked at again.
            lastRunStore().save(startedAt)
            logger.info(
                event = "$COMMAND_NAME.completed",
                message = "Inbox triage run completed",
                attributes = summary.attributes(window),
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
     * The window of the run, the messages that arrived before it are not
     * looked at.
     *
     * The window is the gap since the last successful run, a window on the
     * command line wins over it.
     *
     * @param now the moment of the run.
     */
    private suspend fun window(now: Instant): MessageWindow {
        val lastRun = lastRunStore().load()
        if (lastRun != null && !lastRun.isBefore(now)) {
            logger.warn(
                event = "$COMMAND_NAME.window_fallback",
                message =
                    "The moment of the last run is not in the past, " +
                        "the run looks back as far as the default window",
                attributes = mapOf("last_run" to lastRun.toString()),
            )
        }
        return try {
            runWindow(
                lastRun = lastRun,
                now = now,
                given = since?.let { messageWindow(it) },
            )
        } catch (error: IllegalArgumentException) {
            throw CommandFailedException(error.message.orEmpty(), error)
        }
    }

    /**
     * The store of the moment of the last successful run, next to the token.
     */
    private fun lastRunStore(): FileLastRunStore = FileLastRunStore.nextTo(Path.of(tokenFile))

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
     *
     * @param window how far back the run looked for messages.
     */
    private fun ProcessInboxSummary.attributes(window: MessageWindow): Map<String, String> =
        mapOf(
            "screened" to screened.toString(),
            "recruiters" to recruiters.toString(),
            "filtered_out" to filteredOut.toString(),
            "job_listings" to jobListings.toString(),
            "worth_follow_up" to worthFollowUp.toString(),
            "needs_more_information" to needsMoreInformation.toString(),
            "trashed" to trashed.toString(),
            "drafts" to drafts.toString(),
            "window" to window.duration.toString(),
            "since" to since.toString(),
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

    /**
     * The writer of the draft that asks the recruiter for the missing
     * information.
     *
     * @param client the client the models of the pipeline are reached with.
     * @param drafts the draft endpoints of the Gmail API.
     */
    private fun drafts(
        client: HttpClient,
        drafts: GmailDraftsApi,
    ): DraftWriter =
        DraftWriter(
            client =
                OllamaChatClient(
                    httpClient = client,
                    baseUrl = endpoint(draftEndpoint),
                    timeoutMillis = draftTimeout,
                ),
            drafts = drafts,
            model = draftModel,
        )

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
