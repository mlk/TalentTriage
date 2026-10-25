package uk.me.michaellloydlee.recruiter.infrastructure.gmail

/**
 * Authorises the application against the Gmail API.
 *
 * The authenticator owns the two steps of the authorisation: building the URL
 * the user has to open and exchanging the authorisation code Google sends
 * back for a token.
 *
 * @param oauth2Client client that performs the OAuth2 flows.
 * @param tokenStore store the token is written to.
 */
class GmailAuthenticator(
    private val oauth2Client: GmailOAuth2Client,
    private val tokenStore: OAuth2TokenStore,
) {
    /**
     * Builds the URL the user has to open in order to authorise the application.
     *
     * @param redirectUri the redirect URI registered for the application.
     * @param scopes the scopes to request.
     * @param state an opaque value that is echoed back by the authorisation server.
     */
    fun authorizationUrl(
        redirectUri: String,
        scopes: List<String>,
        state: String,
    ): String = oauth2Client.authorizationUrl(redirectUri, scopes, state)

    /**
     * Exchanges an authorisation code for a token and stores it.
     *
     * @param code the authorisation code the user received from Google.
     * @param redirectUri the redirect URI the authorisation was started with.
     * @return the token that was stored.
     */
    suspend fun authenticate(
        code: String,
        redirectUri: String,
    ): OAuth2Token {
        val token = oauth2Client.exchangeAuthorizationCode(code, redirectUri)
        tokenStore.save(token)
        return token
    }
}
