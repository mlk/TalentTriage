package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * Stores the OAuth2 token in a local JSON file.
 *
 * The file holds a secret, it is never committed - the token file is part of
 * the git ignore list.
 *
 * @param path location of the token file.
 * @param json encoder used to write the file.
 */
class FileOAuth2TokenStore(
    private val path: Path,
    private val json: Json =
        Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
        },
) : OAuth2TokenStore {
    override suspend fun load(): OAuth2Token? =
        withContext(Dispatchers.IO) {
            if (!Files.exists(path)) {
                null
            } else {
                json.decodeFromString(OAuth2Token.serializer(), Files.readString(path))
            }
        }

    override suspend fun save(token: OAuth2Token) {
        withContext(Dispatchers.IO) {
            path.parent?.let { Files.createDirectories(it) }
            Files.writeString(path, json.encodeToString(OAuth2Token.serializer(), token))
        }
    }
}
