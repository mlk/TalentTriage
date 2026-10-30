package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Stores the OAuth2 token in a local JSON file.
 *
 * The file holds a secret, it is never committed - the token file is part of
 * the git ignore list.
 *
 * The token is written to a temporary file first and moved over the target
 * afterwards, so a reader never sees a half written file. The move is atomic
 * when the file system supports it.
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
) {
    suspend fun load(): OAuth2Token? =
        withContext(Dispatchers.IO) {
            if (!Files.exists(path)) {
                null
            } else {
                json.decodeFromString(OAuth2Token.serializer(), Files.readString(path))
            }
        }

    suspend fun save(token: OAuth2Token) {
        withContext(Dispatchers.IO) {
            val directory = path.toAbsolutePath().parent
            Files.createDirectories(directory)
            val temporary = Files.createTempFile(directory, TEMP_PREFIX, TEMP_SUFFIX)
            try {
                Files.writeString(temporary, json.encodeToString(OAuth2Token.serializer(), token))
                move(temporary, path)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }

    private fun move(
        source: Path,
        target: Path,
    ) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: AtomicMoveNotSupportedException) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            } catch (failure: IOException) {
                failure.addSuppressed(error)
                throw IOException("Could not replace the token file $target", failure)
            }
        }
    }

    companion object {
        private const val TEMP_PREFIX = ".token-"
        private const val TEMP_SUFFIX = ".tmp"
    }
}
