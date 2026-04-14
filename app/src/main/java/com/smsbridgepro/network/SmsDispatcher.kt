package com.smsbridgepro.network

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.smsbridgepro.data.SmsLogRepository
import com.smsbridgepro.model.SmsLogItem
import com.smsbridgepro.model.SmsResult

/**
 * SmsDispatcher
 * ════════════════════════════════════════════════════════════
 * Sends SMS messages via Android's SmsManager.
 *
 * Features:
 *   • Parses comma-separated phone lists (spec: "num1, num2")
 *   • Auto-splits messages > 160 chars into multipart SMS
 *   • E.164 format validation before sending
 *   • Returns per-recipient SmsResult for API response
 * ════════════════════════════════════════════════════════════
 */
class SmsDispatcher(private val ctx: Context) {

    companion object {
        private const val TAG = "SmsDispatcher"
        private const val MAX_SMS_LEN = 160
    }

    /**
     * dispatch()
     * Parse [phoneField] (comma-separated) and send [text] to each number.
     * Returns a list of [SmsResult], one per number.
     */
    fun dispatch(phoneField: String, text: String): List<SmsResult> {
        val numbers = phoneField.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        Log.d(TAG, "Dispatching to ${numbers.size} recipient(s)")
        return numbers.map { 
            val result = sendOne(it, text)
            // Record log
            SmsLogRepository.addLog(
                SmsLogItem(
                    phone = it,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    isSuccess = result.sent,
                    errorMessage = result.error
                )
            )
            result
        }
    }

    /** Sends to a single E.164 number. Handles single-part and multipart. */
    private fun sendOne(phone: String, text: String): SmsResult {
        if (!isValidE164(phone))
            return SmsResult(phone, false, "Invalid E.164 format (e.g. +1234567890)")
        return try {
            val mgr = smsManager()
            if (text.length > MAX_SMS_LEN) {
                // Long message: split into parts, device reassembles on recipient side
                val parts = mgr.divideMessage(text)
                mgr.sendMultipartTextMessage(phone, null, parts, null, null)
                Log.d(TAG, "Multipart SMS (${parts.size} parts) → $phone")
            } else {
                mgr.sendTextMessage(phone, null, text, null, null)
                Log.d(TAG, "SMS sent → $phone")
            }
            SmsResult(phone, true)
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed → $phone: ${e.message}")
            SmsResult(phone, false, e.message ?: "Unknown error")
        }
    }

    /** API-level-aware SmsManager getter (API 31 deprecated the static one) */
    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ctx.getSystemService(SmsManager::class.java)
        else
            SmsManager.getDefault()

    /** E.164 = '+' followed by 7–14 digits */
    private fun isValidE164(phone: String) = phone.matches(Regex("^\\+[1-9]\\d{6,14}$"))
}
