package uk.me.michaellloydlee.recruiter.domain.screening

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage

/**
 * The rules that decide without a model whether a message can be a recruiter
 * mail.
 *
 * They are the first stage of the screening: a message that matches none of
 * them is not a recruiter mail and is filtered out without asking a model.
 * The rules are deliberately generous - a message that passes them is only a
 * candidate, the model has the last word - because a recruiter mail
 * that is filtered out here never comes back.
 *
 * The words are matched as a part of a word, so "cv" also matches "revolve".
 * The keywords are the stems of the words: "hire" matches "hires" and
 * "hired", "recruit" matches "recruiting" and "recruited". That costs a
 * call to the model for the mails that talk about hiring without being a
 * recruiter mail, and it keeps the mails that use another form of the word.
 * A stem that is not a part of the word is listed with its form: "hiring"
 * is not a part of "hire".
 *
 * @param keywords the words a recruiter mail talks about, they are looked for
 *   in the subject and in the body of the message.
 * @param senderHints the parts of the sender address of a recruiter mail.
 */
class RecruiterHeuristics(
    private val keywords: Collection<String> = DEFAULT_KEYWORDS,
    private val senderHints: Collection<String> = DEFAULT_SENDER_HINTS,
) {
    /**
     * Tells whether the message can be a recruiter mail.
     *
     * @param message the message to look at.
     */
    fun isCandidate(message: EmailMessage): Boolean {
        if (senderHints.any { message.sender.contains(it, ignoreCase = true) }) {
            return true
        }
        return keywords.any { keyword ->
            message.subject.contains(keyword, ignoreCase = true) ||
                message.body.contains(keyword, ignoreCase = true)
        }
    }

    companion object {
        /**
         * The words a recruiter mail talks about.
         *
         * They are the stems of the words: the words are matched as a part of
         * a word, so "hire" catches "hires" and "hired". A word whose stem
         * is not a part of the word is listed with its form: "hiring" is not
         * a part of "hire", "opportunities" is not a part of "opportunity".
         */
        val DEFAULT_KEYWORDS: Set<String> =
            setOf(
                "applicant",
                "application",
                "apply",
                "candidate",
                "career",
                "curriculum vitae",
                "cv",
                "hire",
                "hiring",
                "interview",
                "job",
                "jobs",
                "offer",
                "opportunities",
                "opportunity",
                "position",
                "recruit",
                "referral",
                "resume",
                "talent",
                "vacancies",
                "vacancy",
                "looking for",
                "your experience",
                "your profile",
            )

        /**
         * The parts of the sender address of a recruiter mail.
         */
        val DEFAULT_SENDER_HINTS: Set<String> =
            setOf(
                "career",
                "headhunter",
                "hiring",
                "hr@",
                "jobs",
                "recruit",
                "staffing",
                "talent",
            )
    }
}
