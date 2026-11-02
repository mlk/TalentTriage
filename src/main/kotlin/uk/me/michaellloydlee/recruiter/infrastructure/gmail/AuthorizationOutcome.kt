package uk.me.michaellloydlee.recruiter.infrastructure.gmail

/**
 * What the authorisation of the mailbox did.
 */
sealed interface AuthorizationOutcome {
    /**
     * The application is authorised, the token is stored.
     *
     * @property token the token that was stored.
     */
    data class Authorised(
        val token: OAuth2Token,
    ) : AuthorizationOutcome

    /**
     * The application is not authorised.
     *
     * @property reason why the authorisation did not happen, it goes into the
     *   log and into the message of the failure.
     */
    data class Failed(
        val reason: String,
    ) : AuthorizationOutcome
}
