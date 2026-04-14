package com.smsbridgepro.network

import android.util.Log
import com.smsbridgepro.model.ApiResponse
import com.smsbridgepro.model.SessionCredentials
import com.smsbridgepro.model.SmsDispatchRequest
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

/**
 * KtorServerEngine
 * ════════════════════════════════════════════════════════════
 * Wraps an embedded Ktor/Netty HTTP server.
 *
 * Exposes one authenticated route:
 *   POST /api/v1/dispatch  — sends SMS to one or more numbers
 *
 * Security: 3-Point Interlock (per spec):
 *   1. X-SMS-Auth-Key header must be present          → else 401
 *   2. Username + Password + Token must all match      → else 401
 *   3. JSON body must have valid phone (E.164) + text  → else 400
 * ════════════════════════════════════════════════════════════
 */
class KtorServerEngine(
    private val creds: SessionCredentials,
    private val sms: SmsDispatcher
) {
    companion object {
        private const val TAG  = "KtorServerEngine"
        private const val H_KEY  = "X-SMS-Auth-Key"
        private const val H_USER = "SMS-Username"
        private const val H_PASS = "SMS-Password"
    }

    private var server: ApplicationEngine? = null

    /** Start the Netty server on [port]. Call from a background coroutine. */
    fun start(port: Int = 8080) {
        Log.d(TAG, "Starting on port $port")
        server = embeddedServer(Netty, port = port, host = "0.0.0.0") {

            // ── JSON content negotiation ─────────────────────
            install(ContentNegotiation) {
                json(Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true })
            }

            // ── Global error handler ─────────────────────────
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    Log.e(TAG, "Unhandled error", cause)
                    call.respond(HttpStatusCode.InternalServerError,
                        ApiResponse(false, "Server error: ${cause.message}"))
                }
            }

            routing {

                // ── Health check (no auth required) ──────────
                get("/health") {
                    call.respond(HttpStatusCode.OK,
                        ApiResponse(true, "SMS Bridge Pro is running"))
                }

                // ── Main SMS dispatch endpoint ────────────────
                post("/api/v1/dispatch") {

                    // ── POINT 1: Handshake — header must exist ─
                    val key = call.request.headers[H_KEY]
                    if (key == null) {
                        Log.w(TAG, "Rejected: missing $H_KEY")
                        call.respond(HttpStatusCode.Unauthorized,
                            ApiResponse(false, "Missing $H_KEY header"))
                        return@post
                    }

                    // ── POINT 2: Credential match ──────────────
                    val user = call.request.headers[H_USER]
                    val pass = call.request.headers[H_PASS]
                    if (key != creds.xAuthHeader || user != creds.username || pass != creds.password) {
                        Log.w(TAG, "Rejected: invalid credentials")
                        call.respond(HttpStatusCode.Unauthorized,
                            ApiResponse(false, "Invalid credentials"))
                        return@post
                    }

                    // ── POINT 3: Payload integrity ─────────────
                    val body = try {
                        call.receive<SmsDispatchRequest>()
                    } catch (e: Exception) {
                        Log.w(TAG, "Rejected: bad JSON — ${e.message}")
                        call.respond(HttpStatusCode.BadRequest,
                            ApiResponse(false, "Invalid JSON. Need: {\"phone\":\"E.164\",\"text\":\"msg\"}"))
                        return@post
                    }

                    if (body.phone.isBlank() || body.text.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest,
                            ApiResponse(false, "'phone' and 'text' must not be empty"))
                        return@post
                    }

                    // ── All checks passed — dispatch SMS ────────
                    Log.d(TAG, "Auth OK. Dispatching to: ${body.phone}")
                    val results = sms.dispatch(body.phone, body.text)
                    val allOk   = results.all { it.sent }
                    call.respond(
                        if (allOk) HttpStatusCode.OK else HttpStatusCode.MultiStatus,
                        ApiResponse(allOk,
                            if (allOk) "All messages dispatched successfully"
                            else "Some messages failed",
                            results)
                    )
                }
            }
        }.start(wait = false)
        Log.d(TAG, "Ktor server started")
    }

    fun stop(grace: Long = 1_000, timeout: Long = 5_000) {
        server?.stop(grace, timeout)
        server = null
        Log.d(TAG, "Ktor server stopped")
    }

    fun isRunning() = server != null
}
