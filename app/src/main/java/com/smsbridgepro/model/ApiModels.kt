package com.smsbridgepro.model

import kotlinx.serialization.Serializable

// ── Inbound ──────────────────────────────────────────────────
/**
 * JSON body posted to POST /api/v1/dispatch
 * phone : comma-separated E.164 numbers  "+9779870293027, +918303730172"
 * text  : the SMS body
 */
@Serializable
data class SmsDispatchRequest(val phone: String, val text: String)

// ── Outbound ─────────────────────────────────────────────────
@Serializable
data class ApiResponse(
    val success: Boolean,
    val message: String,
    val results: List<SmsResult>? = null
)

@Serializable
data class SmsResult(val phone: String, val sent: Boolean, val error: String? = null)

@Serializable
data class SmsLogItem(
    val id: Long = System.currentTimeMillis(),
    val phone: String,
    val text: String,
    val timestamp: Long,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

// ── Session ──────────────────────────────────────────────────
/**
 * Generated once per server start by SecureIdentityGenerator.
 * All three fields must be present and matching for the 3-Point Interlock to pass.
 */
data class SessionCredentials(
    val username: String,      // e.g. "node_742"
    val password: String,      // 12-char alphanumeric
    val xAuthHeader: String    // 32-char hex — sent as X-SMS-Auth-Key header
)

enum class ServerStatus { STOPPED, STARTING, RUNNING }
enum class GatewayMode  { LOCAL, GLOBAL }
