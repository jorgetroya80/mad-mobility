package io.github.jorgetroya80.madmobility.shared.emt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

/** How the API authenticates against EMT MobilityLabs. */
sealed interface EmtCredentials {
    data class EmailPassword(
        val email: String,
        val password: String,
    ) : EmtCredentials {
        override fun toString() = "EmailPassword(email=***, password=***)"
    }

    data class ClientKey(
        val clientId: String,
        val passKey: String,
    ) : EmtCredentials {
        override fun toString() = "ClientKey(clientId=***, passKey=***)"
    }
}

/**
 * EMT connection settings. Fails at startup, naming the missing environment variable,
 * unless EMT_EMAIL + EMT_PASSWORD or EMT_CLIENT_ID + EMT_PASS_KEY are set
 * (the client key pair wins when both are present).
 */
@ConfigurationProperties("mad-mobility.emt")
class EmtProperties(
    val baseUrl: URI = URI.create("https://openapi.emtmadrid.es"),
    email: String? = null,
    password: String? = null,
    clientId: String? = null,
    passKey: String? = null,
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val readTimeout: Duration = Duration.ofSeconds(5),
) {
    val credentials: EmtCredentials = resolveCredentials(email, password, clientId, passKey)

    override fun toString() =
        "EmtProperties(baseUrl=$baseUrl, credentials=$credentials, " +
            "connectTimeout=$connectTimeout, readTimeout=$readTimeout)"

    private companion object {
        fun resolveCredentials(
            email: String?,
            password: String?,
            clientId: String?,
            passKey: String?,
        ): EmtCredentials {
            if (!clientId.isNullOrBlank() || !passKey.isNullOrBlank()) {
                require(!clientId.isNullOrBlank()) { "EMT_CLIENT_ID is missing (EMT_PASS_KEY is set)" }
                require(!passKey.isNullOrBlank()) { "EMT_PASS_KEY is missing (EMT_CLIENT_ID is set)" }
                return EmtCredentials.ClientKey(clientId, passKey)
            }
            if (!email.isNullOrBlank() || !password.isNullOrBlank()) {
                require(!email.isNullOrBlank()) { "EMT_EMAIL is missing (EMT_PASSWORD is set)" }
                require(!password.isNullOrBlank()) { "EMT_PASSWORD is missing (EMT_EMAIL is set)" }
                return EmtCredentials.EmailPassword(email, password)
            }
            throw IllegalArgumentException(
                "EMT credentials are missing: set EMT_EMAIL and EMT_PASSWORD, or EMT_CLIENT_ID and EMT_PASS_KEY",
            )
        }
    }
}
