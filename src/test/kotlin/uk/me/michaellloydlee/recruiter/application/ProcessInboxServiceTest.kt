package uk.me.michaellloydlee.recruiter.application

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import uk.me.michaellloydlee.recruiter.domain.gmail.RunTarget
import uk.me.michaellloydlee.recruiter.infrastructure.llm.ModelUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessInboxServiceTest {
    @Test
    fun saysWhatTheCallsToTheModelsTook() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            val summary = service(gmail, model).run(WINDOW)

            // Two messages, a screening and an evaluation for each of them.
            assertEquals(ModelUsage(calls = 4, inputTokens = 40, outputTokens = 4), summary.models)
            assertTrue(summary.durationMillis >= 0)
        }

    @Test
    fun screensEveryUnprocessedMessage() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.screened)
            assertEquals(2, summary.recruiters)
            assertEquals(0, summary.filteredOut)
            assertEquals(0, summary.unknown)
        }

    @Test
    fun countsAJobListingAndDoesNotEvaluateIt() =
        runTest {
            val gmail = gmailEngine(listing = true)
            val model = modelEngine()

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.screened)
            assertEquals(2, summary.jobListings)
            assertEquals(0, summary.recruiters)
            assertEquals(0, model.requestHistory.size)
            val labels = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertEquals(2, labels.size)
            labels.forEach { label ->
                val body = (label.body as TextContent).text
                assertTrue(body.contains("Label_joblisting"))
            }
        }

    @Test
    fun countsTheStateOfTheRecruiterMails() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.screened)
            assertEquals(2, summary.recruiters)
            assertEquals(2, summary.worthFollowUp)
            assertEquals(0, summary.needsMoreInformation)
            assertEquals(0, summary.trashed)
        }

    @Test
    fun countsTheMessagesItCouldNotDecide() =
        runTest {
            val gmail = gmailEngine()
            val model =
                MockEngine {
                    respond(content = """{"error":"model not found"}""", status = HttpStatusCode.NotFound)
                }

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.screened)
            assertEquals(0, summary.recruiters)
            assertEquals(0, summary.filteredOut)
            assertEquals(2, summary.unknown)
        }

    @Test
    fun labelsEveryMailItDecidedSoItIsNotScreenedAgain() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            service(gmail, model).run(WINDOW)

            val labels = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertEquals(2, labels.size)
            labels.forEach { label ->
                val body = (label.body as TextContent).text
                assertTrue(body.contains("Label_worthfollowup"))
            }
        }

    @Test
    fun tagsAMailWithUnclearOfficeDaysForMoreInformation() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(officeDays = "unclear")

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.needsMoreInformation)
            assertEquals(0, summary.worthFollowUp)
            val labels = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertEquals(2, labels.size)
            labels.forEach { label ->
                val body = (label.body as TextContent).text
                assertTrue(body.contains("Label_needsmoreinformation"))
            }
        }

    @Test
    fun theLogSaysWhatIsMissing() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(officeDays = "unclear")
            val log = StringBuilder()

            service(gmail, model, log).run(WINDOW)

            assertTrue(log.toString().contains("does not say how many days of office"))
        }

    @Test
    fun tagsAMailTheModelCouldNotEvaluateForMoreInformation() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(answered = false)

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.screened)
            assertEquals(2, summary.recruiters)
            assertEquals(2, summary.needsMoreInformation)
            assertEquals(0, summary.unknown)
            val labels = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertEquals(2, labels.size)
            labels.forEach { label ->
                val body = (label.body as TextContent).text
                assertTrue(body.contains("Label_needsmoreinformation"))
            }
        }

    @Test
    fun theLogSaysThatTheModelCouldNotEvaluateTheJobDescription() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(answered = false)
            val log = StringBuilder()

            service(gmail, model, log).run(WINDOW)

            assertTrue(log.toString().contains("could not evaluate the job description"))
        }

    @Test
    fun looksAtTheMessagesOfTheWindowOnly() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            service(gmail, model).run(RunTarget.Window(NOW.minusSeconds(6 * 3600)))

            val search =
                gmail.requestHistory
                    .first { it.url.encodedPath.endsWith("/users/me/messages") }
                    .url.parameters["q"]
            assertTrue(search!!.contains("after:${NOW.minusSeconds(6 * 3600).epochSecond}"))
        }

    @Test
    fun theSummaryCarriesTheMomentTheRunLookedBackTo() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()
            val since = NOW.minusSeconds(6 * 3600)

            val summary = service(gmail, model).run(RunTarget.Window(since))

            assertEquals(RunTarget.Window(since), summary.target)
        }

    @Test
    fun leavesTheMailItCouldNotDecideWithoutALabel() =
        runTest {
            val gmail = gmailEngine()
            val model =
                MockEngine {
                    respond(content = """{"error":"model not found"}""", status = HttpStatusCode.NotFound)
                }

            service(gmail, model).run(WINDOW)

            val labels = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertTrue(labels.isEmpty())
        }

    @Test
    fun countsAMailThatDoesNotMatchAsTrashed() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(jdMatches = "does not match")

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.trashed)
            assertEquals(0, summary.drafts)
        }

    @Test
    fun trashesAMailWhosePayIsBelowTheTarget() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            val summary = service(gmail, model, requirements = REQUIREMENTS_WITH_A_TARGET).run(WINDOW)

            assertEquals(2, summary.trashed)
            assertEquals(0, summary.needsMoreInformation)
            assertEquals(0, summary.drafts)
            val labels = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertEquals(2, labels.size)
            labels.forEach { label ->
                val body = (label.body as TextContent).text
                assertTrue(body.contains("Label_trash"))
            }
            val drafts = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/users/me/drafts") }
            assertTrue(drafts.isEmpty())
        }

    @Test
    fun trashesAMailWhosePayIsBelowTheTargetEvenWhenTheModelIsNotSureAboutTheProfile() =
        runTest {
            // The mail of a recruiter that states a pay of 135000 GBP: the
            // model is sure that the pay is below the target of the job
            // seeker and not sure about the job description. The pay decides
            // the mail on its own.
            val gmail = gmailEngine()
            val model = modelEngine(jdMatches = "does not match", confidenceOfTheProfile = 0.0496)

            val summary =
                service(gmail, model, requirements = REQUIREMENTS_WITH_A_TARGET).run(WINDOW)

            assertEquals(2, summary.trashed)
            assertEquals(0, summary.needsMoreInformation)
            assertEquals(0, summary.drafts)
            val labels = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            labels.forEach { label ->
                val body = (label.body as TextContent).text
                assertTrue(body.contains("Label_trash"))
            }
        }

    @Test
    fun writesNoDraftForAMailTheModelWasNotSureAbout() =
        runTest {
            // The model was not sure enough about the job description and the
            // mail misses nothing else: the recruiter is not asked about what
            // the model does not know.
            val gmail = gmailEngine()
            val model = modelEngine(confidenceOfTheProfile = 0.2)

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.needsMoreInformation)
            assertEquals(0, summary.drafts)
        }

    @Test
    fun asksTheRecruiterAboutTheOfficeDaysWhenTheModelIsNotSureAboutTheProfile() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(officeDays = "unclear", confidenceOfTheProfile = 0.2)

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.needsMoreInformation)
            assertEquals(2, summary.drafts)
        }

    @Test
    fun writesADraftForAMailThatNeedsMoreInformation() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(officeDays = "unclear")

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.needsMoreInformation)
            assertEquals(2, summary.drafts)
            val drafts = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/users/me/drafts") }
            assertEquals(2, drafts.size)
        }

    @Test
    fun writesNoDraftForAMailTheModelCouldNotEvaluate() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(answered = false)

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.needsMoreInformation)
            assertEquals(0, summary.drafts)
            val drafts = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/users/me/drafts") }
            assertTrue(drafts.isEmpty())
        }

    @Test
    fun writesNoDraftForAMailThatIsWorthAFollowUp() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.worthFollowUp)
            assertEquals(0, summary.drafts)
            val drafts = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/users/me/drafts") }
            assertTrue(drafts.isEmpty())
        }

    @Test
    fun theLogSaysThatADraftWasWritten() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(officeDays = "unclear")
            val log = StringBuilder()

            service(gmail, model, log).run(WINDOW)

            assertTrue(log.toString().contains("process-inbox.draft_written"))
        }

    @Test
    fun reportsADraftThatCouldNotBeWritten() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(officeDays = "unclear", chatAnswers = false)
            val log = StringBuilder()

            val summary = service(gmail, model, log).run(WINDOW)

            assertEquals(2, summary.failed)
            assertEquals(0, summary.needsMoreInformation)
            assertEquals(0, summary.drafts)
            assertTrue(log.toString().contains("process-inbox.draft_failed"))
        }

    @Test
    fun leavesTheMailWithoutALabelWhenTheDraftCouldNotBeWritten() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(officeDays = "unclear", chatAnswers = false)

            service(gmail, model).run(WINDOW)

            val labels = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertTrue(labels.isEmpty())
        }

    @Test
    fun labelsTheMailThatNeedsMoreInformation() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(officeDays = "unclear")

            service(gmail, model).run(WINDOW)

            val labels = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertEquals(2, labels.size)
            labels.forEach { label ->
                val body = (label.body as TextContent).text
                assertTrue(body.contains("Label_needsmoreinformation"))
            }
        }

    @Test
    fun writesTheDraftBeforeTheStateLabelIsApplied() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(officeDays = "unclear")

            service(gmail, model).run(WINDOW)

            val requests = gmail.requestHistory
            val drafts = mutableMapOf<String, Int>()
            val labels = mutableMapOf<String, Int>()
            requests.forEachIndexed { index, request ->
                val path = request.url.encodedPath
                if (path.endsWith("/users/me/drafts")) {
                    drafts[mailOfTheDraft(request)] = index
                }
                if (path.endsWith("/modify")) {
                    labels[path.substringAfterLast("/users/me/messages/").substringBefore("/")] = index
                }
            }
            assertEquals(setOf("first", "second"), drafts.keys)
            assertEquals(setOf("first", "second"), labels.keys)
            drafts.forEach { (mail, draft) ->
                assertTrue(
                    draft < labels.getValue(mail),
                    "The draft of the mail $mail is written before its label",
                )
            }
        }

    @Test
    fun countsAMailWhoseLabelCouldNotBeAppliedAsFailed() =
        runTest {
            val gmail = gmailEngine(modifyFails = true)
            val model = modelEngine()

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.failed)
            assertEquals(0, summary.worthFollowUp)
        }

    @Test
    fun goesOnWithTheOtherMailsWhenALabelCouldNotBeApplied() =
        runTest {
            val gmail = gmailEngine(modifyFails = true)
            val model = modelEngine()

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.screened)
            assertEquals(2, summary.recruiters)
        }

    @Test
    fun theLogSaysThatTheLabelCouldNotBeApplied() =
        runTest {
            val gmail = gmailEngine(modifyFails = true)
            val model = modelEngine()
            val log = StringBuilder()

            service(gmail, model, log).run(WINDOW)

            assertTrue(log.toString().contains("process-inbox.label_failed"))
        }

    @Test
    fun countsAMailWhoseDraftWasWrittenAndWhoseLabelFailedAsFailed() =
        runTest {
            // The draft is written, the label cannot be applied: the mail keeps
            // its draft and no label, the next run writes a second draft.
            val gmail = gmailEngine(modifyFails = true)
            val model = modelEngine(officeDays = "unclear")

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.drafts)
            assertEquals(2, summary.failed)
            assertEquals(0, summary.needsMoreInformation)
            val labels = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertEquals(2, labels.size)
        }

    @Test
    fun theLogIncludesSubjectWhenDecisionIsUnknown() =
        runTest {
            val log = StringBuilder()
            val gmail = gmailEngine()
            val model =
                MockEngine {
                    respond(content = """{"error":"model not found"}""", status = HttpStatusCode.NotFound)
                }

            service(gmail, model, log = log).run(WINDOW)

            assertTrue(log.toString().contains("process-inbox.screened"))
            assertTrue(log.toString().contains("Your CV"), "Log should contain email subject when decision is UNKNOWN")
        }

    @Test
    fun processesTheMessageWithTheGivenIdOnly() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            val summary = service(gmail, model).run(RunTarget.Message("specific-id"))

            assertEquals(1, summary.screened)
            assertEquals(1, summary.recruiters)
            assertEquals(1, summary.worthFollowUp)
        }

    @Test
    fun doesNotLookAtTheInboxWhenAMessageIsGiven() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            service(gmail, model).run(RunTarget.Message("specific-id"))

            val list = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/users/me/messages") }
            assertTrue(list.isEmpty())
        }

    @Test
    fun removesTheStateOfTheMessageItLooksAtAgain() =
        runTest {
            val gmail = gmailEngine(labels = listOf("INBOX", "Label_trash"))
            val model = modelEngine()

            service(gmail, model).run(RunTarget.Message("specific-id"))

            val modifies = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertEquals(2, modifies.size)
            val cleared = (modifies.first().body as TextContent).text
            assertTrue(cleared.contains("Label_trash"))
            assertTrue(cleared.contains("INBOX"))
        }

    @Test
    fun leavesAMessageWithoutAStateAlone() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            service(gmail, model).run(RunTarget.Message("specific-id"))

            val modifies = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertEquals(1, modifies.size)
        }

    @Test
    fun theLogSaysWhichMessageWasScreened() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()
            val log = StringBuilder()

            service(gmail, model, log).run(RunTarget.Message("specific-id"))

            assertTrue(log.toString().contains("process-inbox.screened"))
            assertTrue(log.toString().contains("specific-id"))
        }

    @Test
    fun theSummaryOfAMessageHasNoWindow() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            val summary = service(gmail, model).run(RunTarget.Message("specific-id"))

            assertEquals(RunTarget.Message("specific-id"), summary.target)
        }
}
