package com.smsbridgepro.security

import com.smsbridgepro.model.SessionCredentials
import java.security.SecureRandom

/**
 * SecureIdentityGenerator
 * ════════════════════════════════════════════════════════════
 * Generates fresh 3-part session credentials every time the
 * server starts, as specified in the architectural blueprint:
 *   • Username  — random alias like "node_742"
 *   • Password  — cryptographically secure 12-char alphanumeric
 *   • X-Header  — 32-char hex token for X-SMS-Auth-Key
 *
 * Uses Java's SecureRandom (backed by /dev/urandom on Android)
 * instead of kotlin.random.Random for cryptographic strength.
 * ════════════════════════════════════════════════════════════
 */
object SecureIdentityGenerator {

    private val rng = SecureRandom()

    /** Alphanumeric pool — ambiguous chars (0/O/l/1) removed */
    private val ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"

    /** Hex chars for the X-Auth token */
    private val HEX = "0123456789abcdef"

    /** Node-style prefixes that make usernames look like network IDs */
    private val PREFIXES = listOf("node","relay","gate","bridge","proxy","hub","link","peer","edge","core")

    /**
     * generate() — call on every server start.
     * Returns a new [SessionCredentials] with random values.
     */
    fun generate() = SessionCredentials(
        username    = "${PREFIXES[rng.nextInt(PREFIXES.size)]}_${rng.nextInt(9000) + 100}",
        password    = buildString(12) { repeat(12) { append(ALPHA[rng.nextInt(ALPHA.length)]) } },
        xAuthHeader = buildString(32) { repeat(32) { append(HEX[rng.nextInt(HEX.length)]) } }
    )
}
