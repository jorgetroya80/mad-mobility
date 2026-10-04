package io.github.jorgetroya80.madmobility.shared.emt

import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException

/** Maps low-level HTTP client failures to [EmtException]s. */
internal object EmtErrors {
    fun <T> readBody(read: () -> T): T =
        try {
            read()
        } catch (e: RestClientException) {
            // An I/O error while reading the body (dropped connection, read timeout) is a transport
            // failure; anything else (malformed JSON) is a protocol error
            if (generateSequence<Throwable>(e) { it.cause }.any { it is IOException }) {
                throw EmtUnavailable(EmtUnavailable.Reason.SERVER_ERROR, "EMT connection failed while reading the response", e)
            }
            throw EmtProtocolError(null, "Unreadable EMT response: ${e.mostSpecificCause.message}", e)
        }

    fun unavailable(e: ResourceAccessException): EmtUnavailable {
        val timedOut = generateSequence<Throwable>(e) { it.cause }.any { it is HttpTimeoutException || it is SocketTimeoutException }
        return if (timedOut) {
            EmtUnavailable(EmtUnavailable.Reason.TIMEOUT, "EMT did not answer in time", e)
        } else {
            EmtUnavailable(EmtUnavailable.Reason.SERVER_ERROR, "EMT could not be reached", e)
        }
    }
}
