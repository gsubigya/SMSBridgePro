package com.smsbridgepro.data

import com.smsbridgepro.model.SmsLogItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * SmsLogRepository
 * ════════════════════════════════════════════════════════════
 * Simple singleton repository to hold the history of sent SMS.
 * In a production app, this would be backed by Room/SQLite.
 * ════════════════════════════════════════════════════════════
 */
object SmsLogRepository {
    private val _logs = MutableStateFlow<List<SmsLogItem>>(emptyList())
    val logs: StateFlow<List<SmsLogItem>> = _logs

    fun addLog(log: SmsLogItem) {
        val current = _logs.value.toMutableList()
        current.add(0, log) // Add to top
        // Keep last 100 logs
        if (current.size > 100) {
            current.removeAt(current.size - 1)
        }
        _logs.value = current
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
